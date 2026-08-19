package com.lumi.dockeditor

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.core.content.IntentCompat
import java.util.Collections
import kotlin.concurrent.thread
import org.json.JSONArray
import org.json.JSONObject

class EditPinnedActivity : ComponentActivity() {

    companion object {
        private const val TARGET_FILE = "/data/user/0/com.oculus.systemux/shared_prefs/AUI_PREFERENCES.xml"
        private const val MAX_APPS = 5
        private const val EMPTY_PREFS_XML_HEADER = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n"
        private const val EMPTY_PREFS_XML = "$EMPTY_PREFS_XML_HEADER<map>\n</map>\n"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val initialList = IntentCompat.getParcelableArrayListExtra(
            intent,
            "appList",
            AppInfo::class.java
        )

        if (initialList == null) {
            Toast.makeText(this, "Error: Could not load app list.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    EditPinnedScreen(initialList = initialList)
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun EditPinnedScreen(initialList: List<AppInfo>) {
        val appList = remember { mutableStateListOf<AppInfo>().apply { addAll(initialList) } }
        var hasUnsavedChanges by remember { mutableStateOf(false) }
        
        // Drag-and-drop state
        val lazyListState = rememberLazyListState()
        var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
        var dragOffset by remember { mutableStateOf(0f) }

        // Activity Result and App Selection state
        val context = LocalContext.current
        var editingIndex by remember { mutableStateOf<Int?>(null) }
        
        val appSelectionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val packageName = result.data?.getStringExtra("packageName") ?: return@rememberLauncherForActivityResult
                val appName = result.data?.getStringExtra("appName") ?: return@rememberLauncherForActivityResult
                
                val selectedApp = InstalledAppInfo(packageName, appName, null)
                val existingAppInfo = editingIndex?.let { appList[it] }
                
                val dialog = ActivitySelectionDialog(context, selectedApp, object : ActivitySelectionDialog.OnActivitySelectedListener {
                    override fun onActivitySelected(activity: ActivityInfo) {
                        val newAppInfo = AppInfo(selectedApp.packageName, "APP", "ANDROID_6DOF", activity.name)
                        existingAppInfo?.let { newAppInfo.originalJsonString = it.originalJsonString }
                        
                        if (editingIndex != null) {
                            appList[editingIndex!!] = newAppInfo
                        } else {
                            appList.add(newAppInfo)
                        }
                        hasUnsavedChanges = true
                        editingIndex = null
                    }
                })
                dialog.show()
            } else {
                editingIndex = null
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Edit Pinned Apps") },
                    navigationIcon = {
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                BottomActionRow(
                    appListSize = appList.size,
                    hasUnsavedChanges = hasUnsavedChanges,
                    onAddClicked = {
                        editingIndex = null
                        appSelectionLauncher.launch(Intent(context, AppSelectionActivity::class.java))
                    },
                    onSaveClicked = {
                        saveChanges(appList.toList()) { success ->
                            if (success) {
                                hasUnsavedChanges = false
                                finish()
                            }
                        }
                    }
                )
            }
        ) { innerPadding ->
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(
                    items = appList,
                    key = { _, app -> app.instanceId }
                ) { index, app ->
                    val isDragging = draggedItemIndex == index
                    
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 12.dp else 2.dp, 
                        animationSpec = tween(durationMillis = 150),
                        label = "elevation"
                    )

                    val animatedTranslationY by animateFloatAsState(
                        targetValue = if (isDragging) dragOffset else 0f,
                        animationSpec = tween(durationMillis = 100),
                        label = "dragOffset"
                    )

                    AppCard(
                        appInfo = app,
                        elevation = elevation,
                        modifier = Modifier
                            .zIndex(if (isDragging) 10f else 1f)
                            .animateItemPlacement(
                                animationSpec = tween(durationMillis = 200)
                            )
                            .graphicsLayer {
                                this.translationY = animatedTranslationY
                                this.scaleX = if (isDragging) 1.02f else 1.0f
                                this.scaleY = if (isDragging) 1.02f else 1.0f
                            }
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggedItemIndex = index },
                                    onDragEnd = {
                                        draggedItemIndex = null
                                        dragOffset = 0f
                                    },
                                    onDragCancel = {
                                        draggedItemIndex = null
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffset += dragAmount.y
                                        
                                        val currentDraggedIndex = draggedItemIndex ?: return@detectDragGesturesAfterLongPress
                                        val threshold = 150f 
                                        
                                        val targetIndex = when {
                                            dragOffset > threshold && currentDraggedIndex < appList.size - 1 -> currentDraggedIndex + 1
                                            dragOffset < -threshold && currentDraggedIndex > 0 -> currentDraggedIndex - 1
                                            else -> null
                                        }

                                        if (targetIndex != null) {
                                            Collections.swap(appList, currentDraggedIndex, targetIndex)
                                            draggedItemIndex = targetIndex
                                            dragOffset = if (targetIndex > currentDraggedIndex) dragOffset - threshold else dragOffset + threshold
                                            hasUnsavedChanges = true
                                        }
                                    }
                                )
                            },
                        onEdit = {
                            editingIndex = index
                            appSelectionLauncher.launch(Intent(context, AppSelectionActivity::class.java))
                        },
                        onRemove = {
                            appList.removeAt(index)
                            hasUnsavedChanges = true
                        }
                    )
                }
            }
        }
    }

    @Composable
    fun AppCard(
        appInfo: AppInfo,
        elevation: androidx.compose.ui.unit.Dp,
        modifier: Modifier = Modifier,
        onEdit: () -> Unit,
        onRemove: () -> Unit
    ) {
        val context = LocalContext.current
        val packageManager = context.packageManager

        val appLabel = remember(appInfo.packageName) {
            try {
                val info = packageManager.getApplicationInfo(appInfo.packageName, 0)
                packageManager.getApplicationLabel(info).toString()
            } catch (e: PackageManager.NameNotFoundException) {
                // Fallback to the short package name if app is not found
                appInfo.packageName.substringAfterLast(".")
            }
        }

        val appIcon: Drawable? = remember(appInfo.packageName) {
            try {
                packageManager.getApplicationIcon(appInfo.packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                packageManager.defaultActivityIcon
            }
        }
    
        val componentDisplay = if (appInfo.componentName.isNotEmpty()) {
            appInfo.componentName.substringAfterLast(".")
        } else {
            "Main Activity"
        }
    
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = elevation)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DragHandle,
                    contentDescription = "Drag to reorder",
                    modifier = Modifier.padding(end = 12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
    
                AndroidView(
                    factory = { ctx ->
                        ImageView(ctx).apply {
                            layoutParams = android.view.ViewGroup.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setImageDrawable(appIcon)
                            scaleType = ImageView.ScaleType.FIT_CENTER
                        }
                    },
                    update = { imageView ->
                        imageView.setImageDrawable(appIcon)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 12.dp)
                )
    
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = appLabel, // <--- Actual App Name
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = appInfo.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Text(
                        text = "Activity: $componentDisplay",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
    
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                    }
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    @Composable
    fun BottomActionRow(
        appListSize: Int,
        hasUnsavedChanges: Boolean,
        onAddClicked: () -> Unit,
        onSaveClicked: () -> Unit
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 12.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onAddClicked,
                    modifier = Modifier.weight(1f),
                    enabled = appListSize < MAX_APPS
                ) {
                    Text(if (appListSize >= MAX_APPS) "Max (${appListSize}/$MAX_APPS)" else "Add App (${appListSize}/$MAX_APPS)")
                }

                Button(
                    onClick = onSaveClicked,
                    modifier = Modifier.weight(1f),
                    enabled = hasUnsavedChanges
                ) {
                    Text("Save Changes")
                }
            }
        }
    }

    private fun saveChanges(currentList: List<AppInfo>, onComplete: (Boolean) -> Unit) {
        thread {
            try {
                val newAppsArray = JSONArray()
                for (app in currentList) {
                    val appObj = try { JSONObject(app.originalJsonString) } catch (e: Exception) {
                        JSONObject().apply { put("type", app.type); put("platformName", app.platformName) }
                    }
                    appObj.apply {
                        put("packageName", app.packageName)
                        val appPanelData = optJSONObject("appPanelData") ?: JSONObject()
                        if (app.componentName.isNotEmpty()) appPanelData.put("componentName", app.componentName) else appPanelData.remove("componentName")
                        if (!appPanelData.has("volumetricWindowTokens")) {
                            appPanelData.put("volumetricWindowTokens", JSONArray())
                        }
                        put("appPanelData", appPanelData)
                        remove("activity")
                    }
                    newAppsArray.put(appObj)
                }
                val encodedPinnedJson = newAppsArray.toString().replace("\\/", "/").replace("\"", "&quot;")

                var auiXml = RootShell.getFileContent(TARGET_FILE) ?: EMPTY_PREFS_XML
                auiXml = upsertXmlString(auiXml, "aui_bar_apps_pinned", encodedPinnedJson)
                auiXml = upsertXmlString(auiXml, "aui_bar_apps_history", "[]")
                auiXml = upsertXmlBoolean(auiXml, "aui_bar_default_apps_pinned", false)
                val success = RootShell.writeFileContent(TARGET_FILE, auiXml)

                if (success) {
                    RootShell.executeCommand("am force-stop com.oculus.systemux")
                }

                runOnUiThread {
                    if (success) Toast.makeText(this@EditPinnedActivity, "Updated!", Toast.LENGTH_LONG).show()
                    else Toast.makeText(this@EditPinnedActivity, "Root access required to save.", Toast.LENGTH_SHORT).show()
                    onComplete(success)
                }
            } catch (e: Exception) {
                runOnUiThread { onComplete(false) }
            }
        }
    }

    private fun upsertXmlString(xmlContent: String, key: String, encodedValue: String): String {
        val entryRegex = Regex(
            """[ \t]*<string name="${Regex.escape(key)}">.*?</string>[ \t]*\r?\n?""",
            RegexOption.DOT_MATCHES_ALL
        )
        val stripped = xmlContent.replace(entryRegex, "")
        val newEntry = "<string name=\"$key\">$encodedValue</string>\n"
        return if (stripped.contains("</map>")) {
            stripped.replaceFirst("</map>", "$newEntry</map>")
        } else {
            "$EMPTY_PREFS_XML_HEADER<map>\n$newEntry</map>\n"
        }
    }

    private fun upsertXmlBoolean(xmlContent: String, key: String, value: Boolean): String {
        val entryRegex = Regex(
            """[ \t]*<boolean name="${Regex.escape(key)}" value="[^"]*"\s*/>[ \t]*\r?\n?"""
        )
        val stripped = xmlContent.replace(entryRegex, "")
        val newEntry = "<boolean name=\"$key\" value=\"$value\" />\n"
        return if (stripped.contains("</map>")) {
            stripped.replaceFirst("</map>", "$newEntry</map>")
        } else {
            "$EMPTY_PREFS_XML_HEADER<map>\n$newEntry</map>\n"
        }
    }
}