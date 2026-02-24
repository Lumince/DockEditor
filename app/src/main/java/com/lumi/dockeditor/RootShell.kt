package com.lumi.dockeditor

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStreamReader

object RootShell {
    private var rootProcess: Process? = null
    private var rootOutput: DataOutputStream? = null
    private var rootStdout: BufferedReader? = null
    private var rootStderr: BufferedReader? = null

    var lastCommandOutput: String = ""
        private set

    fun initRootShell(suCommand: String): Boolean {
        if (rootProcess != null) return true
        return try {
            val process = Runtime.getRuntime().exec(suCommand)
            val output = DataOutputStream(process.outputStream)
            val stdout = BufferedReader(InputStreamReader(process.inputStream))
            val stderr = BufferedReader(InputStreamReader(process.errorStream))

            output.writeBytes("id\n")
            output.flush()
            val line = stdout.readLine()
            if (line?.contains("uid=0") == true) {
                rootProcess = process
                rootOutput = output
                rootStdout = stdout
                rootStderr = stderr
                true
            } else {
                shutdown()
                false
            }
        } catch (e: Exception) {
            lastCommandOutput = "Failed to initialize root shell: ${e.message}"
            shutdown()
            false
        }
    }

    fun shutdown() {
        try {
            rootOutput?.writeBytes("exit\n")
            rootOutput?.flush()
            rootOutput?.close()
            rootStdout?.close()
            rootStderr?.close()
            rootProcess?.waitFor()
        } catch (e: Exception) {
            if (e is InterruptedException) Thread.currentThread().interrupt()
        } finally {
            rootProcess?.destroy()
            rootProcess = null
        }
    }

    fun executeCommand(command: String): String = executeCommands(listOf(command))

    fun executeCommands(commands: List<String>): String {
        val outputStream = rootOutput ?: return "Root shell not initialized."
        val stdoutReader = rootStdout ?: return "Root shell not initialized."
        val output = StringBuilder()
        val marker = "--END_OF_COMMAND--"

        try {
            commands.forEach { outputStream.writeBytes("$it 2>&1\n") }
            outputStream.writeBytes("echo $marker\n")
            outputStream.flush()

            var line: String?
            while (stdoutReader.readLine().also { line = it } != null) {
                if (line?.contains(marker) == true) {
                    line?.indexOf(marker)?.takeIf { it > 0 }?.let { output.append(line?.substring(0, it)) }
                    break
                }
                output.append(line).append("\n")
            }
        } catch (e: IOException) {
            lastCommandOutput = "IO Error: ${e.message}"
            return lastCommandOutput
        }
        return output.toString().also { lastCommandOutput = it }
    }

    fun getFileContent(filePath: String): String? {
        val result = executeCommand("cat \"$filePath\"")
        return if (result.isBlank() || result.contains("Permission denied")) null else result
    }

    fun writeFileContent(filePath: String, content: String): Boolean {
        val contextResult = executeCommand("ls -Z \"$filePath\"")
        val selinuxContext = contextResult.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { !it.contains("No such") }

        val commands = mutableListOf(
            "printf '%s' '${content.replace("'", "'\"'\"'")}' > \"$filePath\"",
            "chmod 666 \"$filePath\"",
            "chown system:system \"$filePath\""
        )
        selinuxContext?.takeIf { !it.contains("?") }?.let { commands.add("chcon '$it' \"$filePath\"") }

        val result = executeCommands(commands)
        return !result.contains("Permission denied")
    }
}