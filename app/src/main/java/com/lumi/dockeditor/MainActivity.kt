package com.lumi.dockeditor

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.lumi.dockeditor.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        private const val PREFS_NAME = "DockEditorPrefs"
        private const val DARK_MODE_KEY = "darkModeEnabled"
        private const val TARGET_FILE = "/data/user/0/com.oculus.systemux/shared_prefs/AUI_PREFERENCES.xml"
        private const val BACKUP_SUBDIR = "backups"
        private const val MAX_BACKUPS = 3

        private const val DEFAULT_AUI_PREFERENCES = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="aui_bar_apps_pinned">[{&quot;packageName&quot;:&quot;com.oculus.explore&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;com.oculus.store&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;messenger_system_app&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;share_system_app&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;com.oculus.browser&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}]</string>
            	<string name="aui_bar_apps_history">[]</string>
            </map>
        """
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = sharedPreferences.getBoolean(DARK_MODE_KEY, false)
        
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupButtons()
        checkRootAccess()
    }

    override fun onDestroy() {
        super.onDestroy()
        RootShell.shutdown()
    }

    private fun setupButtons() {
        binding.loadButton.setOnClickListener { loadAndParseFile() }
        binding.backupButton.setOnClickListener { backupFile() }
        binding.restoreBackupButton.setOnClickListener { showRestoreBackupDialog() }
        binding.restoreDefaultButton.setOnClickListener { showRestoreDefaultDialog() }
        binding.restartSystemUIButton.setOnClickListener { showRestartSystemUIDialog() }

        binding.darkModeToggle.setOnClickListener {
            val isCurrentlyDarkMode = sharedPreferences.getBoolean(DARK_MODE_KEY, false)
            val newMode = !isCurrentlyDarkMode
            
            AppCompatDelegate.setDefaultNightMode(
                if (newMode) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            )
            sharedPreferences.edit().putBoolean(DARK_MODE_KEY, newMode).apply()
        }
    }

    private fun checkRootAccess() {
        logToUi("Checking for root access...")
        thread {
            val hasRoot = RootShell.initRootShell("su --mount-master")

            if (hasRoot) {
                runOnUiThread { checkForExistingBackups() }
            }

            runOnUiThread {
                if (hasRoot) {
                    binding.statusText.text = "Root Access ✓"
                    binding.backupButton.isEnabled = true
                    binding.restoreDefaultButton.isEnabled = true
                    binding.loadButton.isEnabled = true
                    binding.restartSystemUIButton.isEnabled = true
                    logToUi("Root access granted with --mount-master.")
                    checkSelinuxStatus()
                } else {
                    binding.statusText.text = "Root Access ✗"
                    Toast.makeText(this, "Root access is required for this app to function", Toast.LENGTH_LONG).show()
                    logToUi("Root access denied.")
                }
            }
        }
    }

    private fun showRestartSystemUIDialog() {
        AlertDialog.Builder(this)
            .setTitle("Restart SystemUX?")
            .setMessage("This will force restart SystemUX, applying the changes that you have made.")
            .setPositiveButton("Restart") { _, _ ->
                logToUi("Executing: am force-stop com.oculus.systemux")
                Toast.makeText(this, "Restarting SystemUI...", Toast.LENGTH_SHORT).show()
                thread {
                    RootShell.executeCommand("am force-stop com.oculus.systemux")
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadAndParseFile() {
        logToUi("Loading and parsing file...")
        thread {
            try {
                logToUi("Reading content from target file $TARGET_FILE...")

                val content = RootShell.getFileContent(TARGET_FILE)
                if (content == null) {
                    runOnUiThread { Toast.makeText(this, "Failed to read original file", Toast.LENGTH_SHORT).show() }
                    logToUi("Failed to read original file. Output: ${RootShell.lastCommandOutput}")
                    return@thread
                }

                logToUi("Parsing XML content...")
                val parsedAppList = parseAuiPreferences(content)

                runOnUiThread {
                    Toast.makeText(this, "File loaded successfully, opening editor...", Toast.LENGTH_SHORT).show()
                    logToUi("File loaded and parsed successfully. Launching editor.")
                    
                    val intent = Intent(this, EditPinnedActivity::class.java).apply {
                        putParcelableArrayListExtra("appList", parsedAppList)
                    }
                    startActivity(intent)
                }

            } catch (e: Exception) {
                logToUi("Load error: ${e.message}")
                runOnUiThread { Toast.makeText(this, "Load error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun parseAuiPreferences(xmlContent: String): ArrayList<AppInfo> {
        val localAppList = ArrayList<AppInfo>()
        try {
            val startTag = "<string name=\"aui_bar_apps_pinned\">"
            val startIndex = xmlContent.indexOf(startTag) + startTag.length
            val endIndex = xmlContent.indexOf("</string>", startIndex)

            if (startIndex < startTag.length || endIndex == -1) {
                throw Exception("Could not find pinned apps data in file")
            }

            val jsonString = xmlContent.substring(startIndex, endIndex)
                .replace("&quot;", "\"")
                .replace("&amp;", "&")

            val appsArray = JSONArray(jsonString)
            logToUi("Found ${appsArray.length()} pinned apps.")

            for (i in 0 until appsArray.length()) {
                val appObj = appsArray.getJSONObject(i)
                localAppList.add(AppInfo(appObj.toString()))
            }
            return localAppList

        } catch (e: JSONException) {
            logToUi("JSON parsing failed: ${e.message}")
            throw RuntimeException("Failed to parse app data", e)
        } catch (e: Exception) {
            logToUi("Parsing error: ${e.message}")
            throw RuntimeException(e)
        }
    }

    private fun checkSelinuxStatus() {
        thread {
            val output = RootShell.executeCommand("getenforce").trim()
            runOnUiThread {
                binding.selinuxStatusText.text = when {
                    output.equals("Enforcing", ignoreCase = true) -> "SELinux: Enforcing ⚠️"
                    output.equals("Permissive", ignoreCase = true) -> "SELinux: Permissive ✓"
                    else -> "SELinux: $output ?"
                }
            }
        }
    }

    private fun checkForExistingBackups() {
        logToUi("Checking for existing backups...")
        thread {
            val backupDir = File(cacheDir, BACKUP_SUBDIR)
            val backupFiles = if (backupDir.exists()) {
                backupDir.listFiles { _, name -> name.endsWith(".xml") }
            } else null

            val count = backupFiles?.size ?: 0
            
            runOnUiThread {
                if (count == 0) {
                    binding.restoreBackupButton.text = "No Backups Found"
                    binding.restoreBackupButton.isEnabled = false
                    logToUi("No backups found.")
                } else {
                    binding.restoreBackupButton.text = "Restore Backup ($count)"
                    binding.restoreBackupButton.isEnabled = true
                    logToUi("$count backups found.")
                }
            }
        }
    }

    private fun showRestoreBackupDialog() {
        logToUi("Showing restore backup dialog...")
        thread {
            val backupDir = File(cacheDir, BACKUP_SUBDIR)
            val backupFiles = if (backupDir.exists()) {
                backupDir.listFiles { _, name -> name.endsWith(".xml") }
            } else null

            if (backupFiles.isNullOrEmpty()) {
                runOnUiThread { Toast.makeText(this, "No backup files found", Toast.LENGTH_SHORT).show() }
                logToUi("Error: No backup files found.")
                return@thread
            }

            backupFiles.sortByDescending { it.lastModified() }

            val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            val backupNames = backupFiles.map { file ->
                "${file.name}\n${sdf.format(Date(file.lastModified()))}"
            }.toTypedArray()

            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("Select Backup to Restore")
                    .setItems(backupNames) { _, which ->
                        restoreFromBackup(backupFiles[which])
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun showRestoreDefaultDialog() {
        logToUi("Showing restore default dialog...")
        AlertDialog.Builder(this)
            .setTitle("Restore Default Configuration")
            .setMessage("This will restore the default Oculus dock configuration:\n\n" +
                    "• Oculus Explore\n" +
                    "• Oculus Store\n" +
                    "• Messenger\n" +
                    "• Share\n" +
                    "• Oculus Browser\n\n" +
                    "This will overwrite your current configuration. Continue?")
            .setPositiveButton("Restore Defaults") { _, _ -> restoreDefaults() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restoreFromBackup(backupFile: File) {
        logToUi("Restoring from backup: ${backupFile.name}")
        thread {
            try {
                val backupContent = backupFile.readText(Charsets.UTF_8)
                logToUi("Writing content to target file...")
                val success = RootShell.writeFileContent(TARGET_FILE, backupContent)

                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Backup restored successfully!\nRestart Oculus system to see changes.", Toast.LENGTH_LONG).show()
                        logToUi("Restore successful.")
                    } else {
                        Toast.makeText(this, "Restore failed. Check log for details.", Toast.LENGTH_SHORT).show()
                        logToUi("Restore failed. Last command output: ${RootShell.lastCommandOutput}")
                    }
                }
            } catch (e: Exception) {
                logToUi("Restore error: ${e.message}")
                runOnUiThread { Toast.makeText(this, "Restore error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun restoreDefaults() {
        logToUi("Restoring default configuration...")
        thread {
            try {
                logToUi("Writing default configuration to target file...")
                val success = RootShell.writeFileContent(TARGET_FILE, DEFAULT_AUI_PREFERENCES.trimIndent())

                runOnUiThread {
                    if (success) {
                        Toast.makeText(this, "Default configuration restored successfully!\nRestart Oculus system to see changes.", Toast.LENGTH_LONG).show()
                        logToUi("Default configuration restored successfully.")
                    } else {
                        Toast.makeText(this, "Restore failed. Check log for details.", Toast.LENGTH_SHORT).show()
                        logToUi("Restore failed. Last command output: ${RootShell.lastCommandOutput}")
                    }
                }
            } catch (e: Exception) {
                logToUi("Restore defaults error: ${e.message}")
                runOnUiThread { Toast.makeText(this, "Restore defaults error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun backupFile() {
        logToUi("Starting backup process...")
        thread {
            try {
                val content = RootShell.getFileContent(TARGET_FILE)
                if (content == null) {
                    runOnUiThread { Toast.makeText(this, "Backup failed: Could not read original file.", Toast.LENGTH_SHORT).show() }
                    logToUi("Backup failed: Could not read original file. Output: ${RootShell.lastCommandOutput}")
                    return@thread
                }

                val backupDir = File(cacheDir, BACKUP_SUBDIR).apply { if (!exists()) mkdirs() }
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                val backupFileName = "AUI_PREFERENCES_$timestamp.xml"
                val backupFile = File(backupDir, backupFileName)
                
                logToUi("Saving backup to ${backupFile.absolutePath}")
                backupFile.writeText(content, Charsets.UTF_8)

                pruneBackups()

                runOnUiThread {
                    Toast.makeText(this, "Backup created: $backupFileName", Toast.LENGTH_LONG).show()
                    checkForExistingBackups()
                    logToUi("Backup created successfully.")
                }

            } catch (e: Exception) {
                logToUi("Backup error: ${e.message}")
                runOnUiThread { Toast.makeText(this, "Backup error: ${e.message}", Toast.LENGTH_SHORT).show() }
            }
        }
    }

    private fun pruneBackups() {
        val backupDir = File(cacheDir, BACKUP_SUBDIR)
        if (!backupDir.exists()) return

        val backupFiles = backupDir.listFiles { _, name -> name.endsWith(".xml") } ?: return

        if (backupFiles.size > MAX_BACKUPS) {
            backupFiles.sortBy { it.lastModified() }
            val filesToDeleteCount = backupFiles.size - MAX_BACKUPS
            logToUi("Backup limit exceeded. Deleting $filesToDeleteCount oldest backup(s)...")

            for (i in 0 until filesToDeleteCount) {
                val oldestFile = backupFiles[i]
                if (oldestFile.delete()) {
                    logToUi("Deleted old backup: ${oldestFile.name}")
                } else {
                    logToUi("Failed to delete old backup: ${oldestFile.name}")
                }
            }
        }
    }

    private fun logToUi(message: String) {
        runOnUiThread {
            binding.logTextView.append("$message\n")
            binding.logScrollView.fullScroll(View.FOCUS_DOWN)
        }
    }
}