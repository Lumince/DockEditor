package com.lumi.dockeditor

enum class ModuleState(val label: String) {
    ACTIVE("Active"),
    PATCH_NOT_APPLIED("Patch not applied"),
    NOT_INSTALLED("Not installed"),
    DISABLED("Disabled in Vector"),
    NOT_SCOPED("Not scoped in Vector"),
    NOT_LOADED("Not loaded in Vector"),
    CHECK_FAILED("Check failed")
}

data class DockLimit(
    val max: Int,
    val source: String,
    val state: ModuleState = ModuleState.NOT_LOADED,
    val detail: String = "" ) {
    val extended: Boolean get() = max > STOCK_LIMIT

    companion object {
        const val STOCK_LIMIT = 5
        const val STATUS_FILE_NAME = "uxpatcher_dock_status"
        const val MODULE_PACKAGE = "com.lumi.uxpatcher"
        const val SYSTEMUX_PACKAGE = "com.oculus.systemux"
        private val LSPOSED_DB_PATHS = listOf(
            "/data/adb/lspd/config/modules_config.db",
            "/data/adb/modules/zygisk_lsposed/config/modules_config.db"
        )
        const val HARD_MAX = 10

        @Volatile
        var current: DockLimit = DockLimit(STOCK_LIMIT, "default (not checked yet)", ModuleState.NOT_LOADED, "Not checked yet.")

        fun detect(userId: Int): DockLimit {
            val result = try {
                detectInternal(userId)
            } catch (e: Exception) {
                DockLimit(STOCK_LIMIT, "UX Patcher check failed", ModuleState.CHECK_FAILED, "UX Patcher check failed. Limited to $STOCK_LIMIT.")
            }
            current = result
            return result
        }

        private class Status(val limit: Int, val build: String?)

        private fun detectInternal(userId: Int): DockLimit {
            val status = readLiveStatus(userId)
            if (status != null) {
                return if (status.limit >= STOCK_LIMIT + 1) {
                    val max = minOf(status.limit, HARD_MAX)
                    DockLimit(max, "UX Patcher", ModuleState.ACTIVE, "UX Patcher raised the dock limit to $max.")
                } else {
                    DockLimit(STOCK_LIMIT, "UX Patcher patch not applied", ModuleState.PATCH_NOT_APPLIED,
                        "UX Patcher is loaded, but the pin patch didn't apply. Limited to $STOCK_LIMIT.")
                }
            }
            return diagnoseNotLoaded(userId)
        }

        private fun readLiveStatus(userId: Int): Status? {
            val path = "/data/user/$userId/$SYSTEMUX_PACKAGE/files/$STATUS_FILE_NAME"
            val text = RootShell.getFileContent(path) ?: return null
            val kv = text.lineSequence()
                .mapNotNull { line -> line.split("=", limit = 2).takeIf { it.size == 2 }?.let { it[0].trim() to it[1].trim() } }
                .toMap()
            val limit = kv["limit"]?.toIntOrNull() ?: return null
            val pid = kv["pid"]?.toIntOrNull() ?: return null
            if (limit < 0) return null

            val bootNow = RootShell.executeCommand("cat /proc/sys/kernel/random/boot_id").trim()
            val bootFile = kv["boot"].orEmpty()
            if (bootFile.isNotEmpty() && bootNow.isNotEmpty() && bootFile != bootNow) return null

            // the writer must still be running as SystemUX, otherwise the module may have been turned off since
            val cmdline = RootShell.executeCommand("cat /proc/$pid/cmdline").replace("\u0000", "").trim()
            if (!cmdline.startsWith(SYSTEMUX_PACKAGE)) return null
            return Status(limit, kv["build"])
        }

        private fun diagnoseNotLoaded(userId: Int): DockLimit {
            val pm = RootShell.executeCommand("pm path --user $userId $MODULE_PACKAGE").trim()
            if (!pm.contains("package:")) {
                return DockLimit(STOCK_LIMIT, "UX Patcher not installed", ModuleState.NOT_INSTALLED,
                    "UX Patcher isn't installed. Limited to $STOCK_LIMIT.")
            }

            val enabled = vectorQuery("select enabled from modules where module_pkg_name='$MODULE_PACKAGE' limit 1")
            if (enabled != null) {
                if (enabled.isEmpty() || enabled.trim() != "1") {
                    return DockLimit(STOCK_LIMIT, "UX Patcher disabled in Vector", ModuleState.DISABLED,
                        "UX Patcher isn't enabled in Vector. Limited to $STOCK_LIMIT.")
                }
                val scoped = vectorQuery(
                    "select count(*) from scope join modules on scope.mid=modules.mid " +
                        "where modules.module_pkg_name='$MODULE_PACKAGE' and scope.app_pkg_name='$SYSTEMUX_PACKAGE'"
                )
                if (scoped != null && scoped.trim() == "0") {
                    return DockLimit(STOCK_LIMIT, "UX Patcher not scoped in Vector", ModuleState.NOT_SCOPED,
                        "UX Patcher isn't scoped to SystemUX in Vector. Limited to $STOCK_LIMIT.")
                }
            }
            return DockLimit(STOCK_LIMIT, "UX Patcher not loaded in Vector", ModuleState.NOT_LOADED,
                "UX Patcher isn't loaded in SystemUX by Vector. Restart SystemUX or reboot. Limited to $STOCK_LIMIT.")
        }

        private fun vectorQuery(sql: String): String? {
            val sqlite = RootShell.executeCommand("command -v sqlite3").trim()
            if (!sqlite.startsWith("/")) return null
            val db = LSPOSED_DB_PATHS.firstOrNull {
                RootShell.executeCommand("[ -f $it ] && echo yes").trim() == "yes"
            } ?: return null
            val out = RootShell.executeCommand("sqlite3 $db \"$sql\"").trim()
            // error text (locked db, unknown table ...) is not an answer
            if (out.contains("rror") || out.contains("denied") || out.contains("not found")) return null
            return out
        }

        fun coerceForList(detected: DockLimit, currentCount: Int): DockLimit =
            if (detected.max <= HARD_MAX) detected else detected.copy(max = HARD_MAX)

        fun canSave(detected: DockLimit, newCount: Int, originalCount: Int): Boolean =
            newCount <= minOf(detected.max, HARD_MAX) || newCount <= originalCount
    }
}
