package com.lumi.dockeditor

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AppSelectionActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background // Explicitly set background
                ) {
                    AppSelectionScreen(
                        onAppSelected = { app ->
                            val resultIntent = Intent().apply {
                                putExtra("packageName", app.packageName)
                                putExtra("appName", app.appName)
                            }
                            setResult(RESULT_OK, resultIntent)
                            finish()
                        },
                        onBack = {
                            setResult(RESULT_CANCELED)
                            finish()
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSelectionScreen(onAppSelected: (InstalledAppInfo) -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }
    var showUserApps by remember { mutableStateOf(true) }
    
    var allApps by remember { mutableStateOf<List<Pair<InstalledAppInfo, Boolean>>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val loadedApps = packages.mapNotNull { appInfo ->
                if (appInfo.packageName == context.packageName) return@mapNotNull null
                
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                val appName = pm.getApplicationLabel(appInfo).toString()
                
                Pair(
                    InstalledAppInfo(
                        packageName = appInfo.packageName,
                        appName = appName,
                        icon = pm.getApplicationIcon(appInfo.packageName)
                    ),
                    isSystem
                )
            }.sortedBy { it.first.appName.lowercase() }
            
            withContext(Dispatchers.Main) {
                allApps = loadedApps
                isLoading = false
            }
        }
    }

    val filteredApps = remember(searchQuery, showSystemApps, showUserApps, allApps) {
        allApps.filter { (app, isSystem) ->
            val matchesSearch = app.appName.contains(searchQuery, ignoreCase = true) || 
                                app.packageName.contains(searchQuery, ignoreCase = true)
            val matchesType = (isSystem && showSystemApps) || (!isSystem && showUserApps)
            matchesSearch && matchesType
        }.map { it.first }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select App") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showUserApps, onCheckedChange = { showUserApps = it })
                    Text("User Apps")
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = showSystemApps, onCheckedChange = { showSystemApps = it })
                    Text("System Apps")
                }
            }

            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppListItem(app = app, onClick = { onAppSelected(app) })
                    }
                }
            }
        }
    }
}

@Composable
fun AppListItem(app: InstalledAppInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    setImageDrawable(app.icon)
                }
            },
            modifier = Modifier.size(40.dp)
        )
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(text = app.appName, style = MaterialTheme.typography.bodyLarge)
            Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}