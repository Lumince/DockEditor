package com.lumi.dockeditor

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import org.json.JSONArray

class MainActivity : ComponentActivity() {

    companion object {
        const val TARGET_FILE = "/data/user/0/com.oculus.systemux/shared_prefs/AUI_PREFERENCES.xml"
        const val BACKUP_SUBDIR = "backups"
        const val NAVIGATOR_PINNING_SERVICE_COMPONENT =
            "com.oculus.systemux/com.oculus.common.navigatoritempinningservice.NavigatorItemPinningService"
        const val MAX_BACKUPS = 3
        const val DEFAULT_AUI_PREFERENCES = """
            <?xml version='1.0' encoding='utf-8' standalone='yes' ?>
            <map>
                <string name="aui_bar_apps_pinned">[{&quot;packageName&quot;:&quot;com.oculus.explore&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;com.oculus.store&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;messenger_system_app&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;share_system_app&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}, {&quot;packageName&quot;:&quot;com.oculus.browser&quot;,&quot;type&quot;:&quot;APP&quot;,&quot;platformName&quot;:&quot;ANDROID_6DOF&quot;}]</string>
            	<string name="aui_bar_apps_history">[]</string>
            </map>
        """
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val dynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
            val colorScheme = if (dynamicColor) {
                if (isSystemInDarkTheme()) dynamicDarkColorScheme(LocalContext.current) 
                else dynamicLightColorScheme(LocalContext.current)
            } else {
                if (isSystemInDarkTheme()) darkColorScheme() 
                else lightColorScheme()
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    DockEditorScreen()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        RootShell.shutdown()
    }
}

fun isPeopleAppDisabledNative(context: Context): Boolean {
    return try {
        val pm = context.packageManager
        val component = ComponentName(
            "com.oculus.socialplatform",
            "com.oculus.panelapp.people.BlendedPeopleActivity"
        )
        pm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    } catch (e: Exception) {
        false
    }
}

private const val AUI_BACKUP_PREFIX = "AUI_PREFERENCES_"

fun listBackups(context: Context): List<File> {
    val backupDir = File(context.cacheDir, MainActivity.BACKUP_SUBDIR)
    val files = backupDir.listFiles { _, name -> name.startsWith(AUI_BACKUP_PREFIX) && name.endsWith(".xml") }
        ?: emptyArray()
    return files.sortedByDescending { it.lastModified() }
}

fun pruneBackups(backupDir: File) {
    val files = backupDir.listFiles { _, name -> name.startsWith(AUI_BACKUP_PREFIX) && name.endsWith(".xml") }
        ?.sortedBy { it.lastModified() }
    if (files != null && files.size > MainActivity.MAX_BACKUPS) {
        files.take(files.size - MainActivity.MAX_BACKUPS).forEach { it.delete() }
    }
}

@Composable
fun DockEditorScreen() {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val scrollState = rememberScrollState()
    
    // --- State ---
    var consoleText by remember { mutableStateOf("Initializing...\n") }
    var isRooted by remember { mutableStateOf(isPreview) } // Force true in preview to see UI
    var selinuxStatus by remember { mutableStateOf(if (isPreview) "Enforcing (Preview)" else "Checking...") }
    var backupCount by remember { mutableStateOf(if (isPreview) 2 else 0) }
    var pinningServiceStatus by remember { mutableStateOf(if (isPreview) "Disabled" else "Checking...") }
    
    // --- Prefs & Tweak State ---
    val sharedPrefs = remember { 
        if (isPreview) null else context.getSharedPreferences("dockeditor_prefs", Context.MODE_PRIVATE) 
    }
    val initialPeopleAppDisabled = sharedPrefs?.getBoolean("people_app_disabled", false) ?: false
    var isPeopleAppDisabled by remember { mutableStateOf(initialPeopleAppDisabled) }

    // --- Dialog States ---
    var showRestoreDefaultDialog by remember { mutableStateOf(false) }
    var showRestoreBackupDialog by remember { mutableStateOf(false) }

    fun log(message: String) {
        consoleText += "$message\n"
    }

    // Background sync for the true state of the People app
    LaunchedEffect(Unit) {
        if (!isPreview) {
            val realState = isPeopleAppDisabledNative(context)
            if (realState != isPeopleAppDisabled) {
                isPeopleAppDisabled = realState
                sharedPrefs?.edit()?.putBoolean("people_app_disabled", realState)?.apply()
            }
        }
    }

    // --- Logic Functions ---
    fun checkRoot() {
        if (isPreview) {
            return
        }
        log("Checking for root access...")
        thread {
            val hasRoot = RootShell.initRootShell("su --mount-master")
            isRooted = hasRoot
            if (hasRoot) {
                log("Root access granted.")
                val se = RootShell.executeCommand("getenforce").trim()
                selinuxStatus = se

                val disableResult = RootShell.executeCommand(
                    "pm disable ${MainActivity.NAVIGATOR_PINNING_SERVICE_COMPONENT}"
                ).trim()
                val disabled = disableResult.contains("new state: disable", ignoreCase = true)
                pinningServiceStatus = if (disabled) "Disabled" else "Unknown (see log)"
                log(
                    if (disabled) "Navigator pinning-reset service disabled (dock reverts fixed)."
                    else "Could not confirm pinning-reset service is disabled. Output: $disableResult"
                )

                backupCount = listBackups(context).size
            } else {
                log("Root access denied.")
            }
        }
    }

    fun handleBackup() {
        if (isPreview) {
            log("Mock Backup created.")
            backupCount++
            return
        }
        thread {
            log("Starting backup...")
            val auiContent = RootShell.getFileContent(MainActivity.TARGET_FILE)

            if (auiContent == null) {
                log("Backup failed: Could not read AUI_PREFERENCES.xml.")
                return@thread
            }

            val backupDir = File(context.cacheDir, MainActivity.BACKUP_SUBDIR).apply { if (!exists()) mkdirs() }
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())

            File(backupDir, "AUI_PREFERENCES_$timestamp.xml").writeText(auiContent)
            log("Backed up AUI_PREFERENCES.xml")

            pruneBackups(backupDir)

            backupCount = listBackups(context).size
            log("Backup created: $timestamp")
        }
    }

    fun loadAndParse() {
        if (isPreview) {
            log("Mock parsing pinned apps...")
            return
        }
        log("Loading pinned apps...")
        thread {
            try {
                val content = RootShell.getFileContent(MainActivity.TARGET_FILE)
                if (content == null) {
                    log("Failed to read file.")
                    return@thread
                }

                val startTag = "<string name=\"aui_bar_apps_pinned\">"
                val startIndex = content.indexOf(startTag) + startTag.length
                val endIndex = content.indexOf("</string>", startIndex)
                
                val jsonString = content.substring(startIndex, endIndex)
                    .replace("&quot;", "\"")
                    .replace("&amp;", "&")

                val appsArray = JSONArray(jsonString)
                val localAppList = ArrayList<AppInfo>()
                for (i in 0 until appsArray.length()) {
                    localAppList.add(AppInfo(appsArray.getJSONObject(i).toString()))
                }

                context.startActivity(Intent(context, EditPinnedActivity::class.java).apply {
                    putParcelableArrayListExtra("appList", localAppList)
                })
            } catch (e: Exception) {
                log("Parse error: ${e.message}")
            }
        }
    }

    LaunchedEffect(Unit) { checkRoot() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "dockeditor",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "customizer for the oculus systemux dock by Lumince",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Status Section
        ListItem(
            headlineContent = { Text("Root Status") },
            supportingContent = { Text(if (isRooted) "Access Granted" else "Access Denied") },
            leadingContent = {
                Icon(
                    imageVector = if (isRooted) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isRooted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        )
        ListItem(
            headlineContent = { Text("SELinux Status") },
            supportingContent = { Text(selinuxStatus) },
            leadingContent = { Icon(Icons.Default.Security, contentDescription = null) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Action Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { loadAndParse() }, modifier = Modifier.weight(1f), enabled = isRooted) {
                Text("Edit Pinned Apps")
            }
            OutlinedButton(onClick = { handleBackup() }, modifier = Modifier.weight(1f), enabled = isRooted) {
                Text("Backup")
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { showRestoreDefaultDialog = true }, modifier = Modifier.weight(1f), enabled = isRooted) {
                Text("Restore Default")
            }
            OutlinedButton(onClick = { showRestoreBackupDialog = true }, modifier = Modifier.weight(1f), enabled = isRooted && backupCount > 0 ) {
                Text("Restore Backup")
            }
        }
            
        Spacer(modifier = Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { 
                if (isPreview) {
                    log("Mock: Restarting SystemUX...")
                } else {
                    log("Restarting SystemUX...")
                    thread { RootShell.executeCommand("am force-stop com.oculus.systemux") } 
                }
            }, modifier = Modifier.weight(1f), enabled = isRooted) {
                Text("Restart UX")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // --- TWEAKS SECTION ---
        Text("Dock Tweaks", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        ListItem(
            headlineContent = { Text("Disable Force-Pinned People App") },
            trailingContent = {
                Switch(
                    checked = isPeopleAppDisabled,
                    onCheckedChange = { disableIt ->
                        isPeopleAppDisabled = disableIt
                        if (isPreview) {
                            log("Mock: Toggled People App to $disableIt")
                            return@Switch
                        }
                        sharedPrefs?.edit()?.putBoolean("people_app_disabled", disableIt)?.apply()

                        thread {
                            log("Setting People App disabled state to: $disableIt")
                            val component = "com.oculus.socialplatform/com.oculus.panelapp.people.BlendedPeopleActivity"
                            val command = if (disableIt) "pm disable $component" else "pm enable $component"
                            
                            val result = RootShell.executeCommand(command)
                            log("Result: ${result.trim()}")
                            
                            log("Restarting SystemUX to apply layout changes...")
                            RootShell.executeCommand("am force-stop com.oculus.systemux")
                        }
                    },
                    enabled = isRooted
                )
            }
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Text("Log Output:", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth().weight(1f)) {
            SelectionContainer {
                Text(
                    text = consoleText,
                    modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scrollState),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp
                )
            }
        }
        
        // Auto-scroll logic
        LaunchedEffect(consoleText) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // --- Dialogs ---
    if (showRestoreBackupDialog) {
        val backups = if (isPreview) {
            listOf(File("AUI_PREFERENCES_20260819_140000.xml"), File("AUI_PREFERENCES_20260818_090000.xml"))
        } else {
            listBackups(context)
        }

        AlertDialog(
            onDismissRequest = { showRestoreBackupDialog = false },
            title = { Text("Select Backup") },
            text = {
                if (backups.isEmpty()) {
                    Text("No backups found.")
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        val timestampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                        backups.forEach { file ->
                            val timestamp = file.name.removePrefix(AUI_BACKUP_PREFIX).removeSuffix(".xml")
                            val displayDate = if (isPreview) {
                                "Mock Date"
                            } else {
                                try {
                                    sdf.format(timestampFormat.parse(timestamp) ?: Date(file.lastModified()))
                                } catch (e: Exception) {
                                    timestamp
                                }
                            }
                            ListItem(
                                headlineContent = { Text(displayDate) },
                                modifier = Modifier.clickable {
                                    showRestoreBackupDialog = false
                                    if (isPreview) {
                                        log("Mock Restored: $displayDate")
                                    } else {
                                        thread {
                                            val success = RootShell.writeFileContent(MainActivity.TARGET_FILE, file.readText())
                                            log(if (success) "Restored AUI_PREFERENCES.xml from $displayDate" else "Failed to restore AUI_PREFERENCES.xml")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showRestoreBackupDialog = false }) { Text("Close") }
            }
        )
    }

    if (showRestoreDefaultDialog) {
        AlertDialog(
            onDismissRequest = { showRestoreDefaultDialog = false },
            title = { Text("Restore Defaults?") },
            text = { Text("This will overwrite your dock with the standard Oculus layout.") },
            confirmButton = {
                Button(onClick = {
                    showRestoreDefaultDialog = false
                    if (isPreview) {
                        log("Mock Defaults restored.")
                    } else {
                        thread { 
                            val success = RootShell.writeFileContent(MainActivity.TARGET_FILE, MainActivity.DEFAULT_AUI_PREFERENCES.trimIndent())
                            log(if (success) "Defaults restored." else "Restore failed.")
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { showRestoreDefaultDialog = false }) { Text("Cancel") } }
        )
    }
}

@Preview(showBackground = true, name = "Dock Editor Preview (Dark)")
@Composable
fun DockEditorScreenPreviewDark() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DockEditorScreen()
        }
    }
}

@Preview(showBackground = true, name = "Dock Editor Preview (Light)")
@Composable
fun DockEditorScreenPreviewLight() {
    MaterialTheme(colorScheme = lightColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            DockEditorScreen()
        }
    }
}