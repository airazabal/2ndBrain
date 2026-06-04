package com.alex.a2ndbrain

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.widget.Toast
import com.alex.a2ndbrain.core.sync.NearbySyncManager
import com.alex.a2ndbrain.ui.settings.SettingsViewModel
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

import com.alex.a2ndbrain.ui.theme.BrainTheme
import org.koin.android.ext.android.inject
import androidx.lifecycle.lifecycleScope
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.core.reflection.ModelDownloader
import com.alex.a2ndbrain.core.reflection.ModelPicker
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.Dispatchers

class AppCaptureSettingsActivity : ComponentActivity() {
    private val memoryRepository: MemoryRepository by inject()
    private val usageRepository: UsageRepository by inject()
    private val nearbySyncManager: NearbySyncManager by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsManager = CaptureSettingsManager(this)
        setContent {
            var themePreference by remember { mutableStateOf(settingsManager.getThemePreference()) }
            val isDark = when (themePreference) {
                "LIGHT" -> false
                "DARK" -> true
                else -> androidx.compose.foundation.isSystemInDarkTheme()
            }
            BrainTheme(darkTheme = isDark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val syncStatus by nearbySyncManager.syncStatus.collectAsState(NearbySyncManager.SyncStatus.Idle)
                    AppCaptureSettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { finish() },
                        onRestartService = {
                            val componentName = android.content.ComponentName(this, com.alex.a2ndbrain.core.capture.NotificationCaptureService::class.java)
                            packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                            packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                        },
                        onUnmonitoredAppRemoved = { packageName ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                memoryRepository.deleteMemoriesByPackage(packageName)
                                usageRepository.deleteUsageStatsByPackage(packageName)
                            }
                        },
                        syncStatus = syncStatus,
                        onStartSync = { force -> nearbySyncManager.startSync(force) },
                        onStopSync = { nearbySyncManager.stopSync() }
                    )
                }
            }
        }
    }
}

@Composable
fun AppCaptureSettingsScreen(
    settingsManager: CaptureSettingsManager,
    onBack: () -> Unit,
    onRestartService: () -> Unit,
    onUnmonitoredAppRemoved: (String) -> Unit = {},
    syncStatus: NearbySyncManager.SyncStatus = NearbySyncManager.SyncStatus.Idle,
    onStartSync: (Boolean) -> Unit = {},
    onStopSync: () -> Unit = {}
) {
    val settingsViewModel: SettingsViewModel = koinViewModel()
    var monitoredApps by remember { mutableStateOf(settingsManager.getMonitoredApps()) }
    val debugEvents by com.alex.a2ndbrain.core.capture.CaptureDebugStore.events.collectAsState()
    val context = LocalContext.current
    val packageManager = context.packageManager
    val scope = rememberCoroutineScope()
    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = settingsViewModel.exportBackupJson()
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                    }
                    Toast.makeText(context, "Settings backed up", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Backup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val openBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val json = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    } ?: return@launch
                    settingsViewModel.importFromJson(json)
                    monitoredApps = settingsManager.getMonitoredApps()
                    Toast.makeText(context, "Settings restored", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val permissionsToRequest = remember {
        val list = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            list.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            list.add(android.Manifest.permission.BLUETOOTH_SCAN)
            list.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        list.toTypedArray()
    }

    val syncPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            onStartSync(false)
        } else {
            Toast.makeText(context, "Nearby Sync requires Location and Bluetooth permissions.", Toast.LENGTH_SHORT).show()
        }
    }

    val allApps = remember {
        packageManager.getInstalledPackages(PackageManager.GET_META_DATA)
            .filter { (it.applicationInfo?.flags ?: 0) and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 ||
                     it.packageName == "com.google.android.gm" ||
                     it.packageName == "com.google.android.apps.messaging" ||
                     it.packageName == "com.microsoft.office.outlook" ||
                     it.packageName.contains("mail", ignoreCase = true) ||
                     it.packageName.contains("todoist", ignoreCase = true) }
            .sortedBy { packageManager.getApplicationLabel(it.applicationInfo ?: return@sortedBy "").toString().lowercase() }
    }

    var monitorAppsExpanded by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }
    val filteredApps = if (appSearchQuery.isEmpty()) allApps else allApps.filter {
        packageManager.getApplicationLabel(it.applicationInfo ?: return@filter false).toString()
            .contains(appSearchQuery, ignoreCase = true) || it.packageName.contains(appSearchQuery, ignoreCase = true)
    }

    var hasCalendarPermission by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_CALENDAR
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCalendarPermission = granted
        if (!granted) Toast.makeText(context, "Calendar permission denied", Toast.LENGTH_SHORT).show()
    }

    var isListenerEnabled by remember { mutableStateOf(false) }
    var showDebugLogs by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            isListenerEnabled = enabled?.contains(context.packageName) == true
            kotlinx.coroutines.delay(2000)
        }
    }

    // AI & Integrations state
    val modelPicker = remember { ModelPicker(context) }
    val modelDownloader = remember { ModelDownloader(context, scope) }
    var selectedModel by remember { mutableStateOf(settingsManager.getPreferredModelType()) }
    var availableModels by remember { mutableStateOf<List<ModelDownloader.LiteRTModel>>(emptyList()) }
    var isLoadingModels by remember { mutableStateOf(false) }
    var selectedLiteRTModel by remember { mutableStateOf(settingsManager.getSelectedLiteRTModel()) }
    val downloadProgress by modelDownloader.downloadProgress.collectAsState()
    val downloadingModelName by modelDownloader.downloadingModelName.collectAsState()
    val downloadError by modelDownloader.downloadError.collectAsState()
    var geminiApiKey by remember { mutableStateOf(settingsManager.getGeminiApiKey()) }
    var geminiModel by remember { mutableStateOf(settingsManager.getGeminiModel()) }
    var todoistApiToken by remember { mutableStateOf(settingsManager.getTodoistApiToken()) }

    // Daily Goals state
    var stepsGoalText by remember { mutableStateOf(settingsManager.getStepsGoal().toString()) }
    var sleepGoalText by remember { mutableStateOf(settingsManager.getSleepGoalHours().let {
        if (it == it.toInt().toFloat()) it.toInt().toString() else it.toString()
    }) }
    var exerciseGoalText by remember { mutableStateOf(settingsManager.getExerciseGoalMinutes().toString()) }
    var focusGoalText by remember { mutableStateOf(settingsManager.getDigitalFocusBaselineMinutes().toString()) }
    var showAddCustomModelDialog by remember { mutableStateOf(false) }
    var customModelName by remember { mutableStateOf("") }
    var customModelUrl by remember { mutableStateOf("") }
    var customModelDesc by remember { mutableStateOf("") }
    var customModelSize by remember { mutableStateOf("") }
    var showModelLibraryDialog by remember { mutableStateOf(false) }
    var remoteRegistryModels by remember { mutableStateOf<List<ModelDownloader.LiteRTModel>>(emptyList()) }
    var isLoadingRegistryModels by remember { mutableStateOf(false) }
    val aiModes = remember { listOf("AUTO", "GEMINI_CLOUD", "LITERT_LOCAL") }

    LaunchedEffect("models") {
        isLoadingModels = true
        availableModels = modelDownloader.fetchAvailableModels()
        isLoadingModels = false
    }

    LaunchedEffect(downloadingModelName) {
        if (downloadingModelName == null && downloadError == null && selectedLiteRTModel == "") {
            val downloaded = availableModels.find { model ->
                java.io.File(context.filesDir, "models/${model.name}.litertlm").exists()
            }
            if (downloaded != null) {
                selectedLiteRTModel = downloaded.name
                settingsManager.saveSelectedLiteRTModel(downloaded.name)
            }
        }
    }

    LaunchedEffect(showModelLibraryDialog) {
        if (showModelLibraryDialog) {
            isLoadingRegistryModels = true
            remoteRegistryModels = modelDownloader.fetchRemoteRegistry()
            isLoadingRegistryModels = false
        }
    }

    val cardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    val cardShape = RoundedCornerShape(16.dp)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    text = "v${com.alex.a2ndbrain.BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }

        // ── Permissions & Access ───────────────────────────────────────────────
        item {
            SettingsSectionLabel("Permissions & Access")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Notification Listener
                    PermissionRow(
                        label = "Notification Access",
                        subtitle = if (isListenerEnabled) "Granted — capturing notifications" else "Required for notification capture",
                        isGranted = isListenerEnabled,
                        actionLabel = "Open Settings",
                        onAction = { context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")) }
                    )
                    if (!isListenerEnabled) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Access is restricted. Open App Info below, scroll to Notifications or Advanced to unlock the toggle.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(
                                    onClick = {
                                        context.startActivity(
                                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = android.net.Uri.fromParts("package", context.packageName, null)
                                            }
                                        )
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) { Text("Open App Info") }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Usage Access
                    PermissionRow(
                        label = "Usage Access",
                        subtitle = "Required for screen time tracking",
                        isGranted = true,
                        actionLabel = "Open Settings",
                        onAction = { context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Calendar
                    PermissionRow(
                        label = "Calendar",
                        subtitle = if (hasCalendarPermission) "Granted — Google Calendar events shown on home timeline" else "Optional — shows Google Calendar events on home",
                        isGranted = hasCalendarPermission,
                        actionLabel = if (hasCalendarPermission) null else "Grant",
                        onAction = { calendarPermissionLauncher.launch(android.Manifest.permission.READ_CALENDAR) }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                    // Restart service + debug
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = onRestartService) { Text("Restart Capture Service") }
                        TextButton(onClick = { showDebugLogs = !showDebugLogs }) {
                            Text(if (showDebugLogs) "Hide Logs" else "Show Logs", fontSize = 12.sp)
                        }
                    }
                    if (showDebugLogs) {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 120.dp)
                        ) {
                            LazyColumn(modifier = Modifier.padding(8.dp)) {
                                items(debugEvents) { log ->
                                    Text(log, style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace))
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Nearby Sync ───────────────────────────────────────────────────────
        item { SettingsSectionLabel("Nearby Sync") }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val statusText = when (syncStatus) {
                            NearbySyncManager.SyncStatus.Idle -> "Sync screen time, health, and meditations with nearby devices."
                            NearbySyncManager.SyncStatus.Scanning -> "Searching for nearby devices..."
                            is NearbySyncManager.SyncStatus.Connecting -> "Connecting to ${syncStatus.deviceName}..."
                            is NearbySyncManager.SyncStatus.Syncing -> "Syncing data..."
                            is NearbySyncManager.SyncStatus.Success -> "Successfully synchronized!"
                            is NearbySyncManager.SyncStatus.Failed -> "Sync failed: ${syncStatus.reason}"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    val isBusy = syncStatus is NearbySyncManager.SyncStatus.Scanning ||
                            syncStatus is NearbySyncManager.SyncStatus.Connecting ||
                            syncStatus is NearbySyncManager.SyncStatus.Syncing
                    Button(
                        onClick = {
                            if (isBusy) {
                                onStopSync()
                            } else {
                                val hasPerms = permissionsToRequest.all {
                                    androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                                if (hasPerms) onStartSync(true) else syncPermissionLauncher.launch(permissionsToRequest)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isBusy) MaterialTheme.colorScheme.error.copy(alpha = 0.12f) else MaterialTheme.colorScheme.primary,
                            contentColor = if (isBusy) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (syncStatus is NearbySyncManager.SyncStatus.Syncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        } else {
                            val btnLabel = when (syncStatus) {
                                is NearbySyncManager.SyncStatus.Scanning,
                                is NearbySyncManager.SyncStatus.Connecting -> "Stop"
                                is NearbySyncManager.SyncStatus.Failed -> "Retry"
                                else -> "Sync Now"
                            }
                            Text(btnLabel, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        // ── AI & Integrations ─────────────────────────────────────────────────
        item { SettingsSectionLabel("AI & Integrations") }

        // Mode selector
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("AI Mode", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(aiModes) { mode ->
                            FilterChip(
                                selected = selectedModel == mode,
                                onClick = {
                                    selectedModel = mode
                                    settingsManager.savePreferredModelType(mode)
                                },
                                label = { Text(mode.replace("_", " ")) },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }
            }
        }

        // Gemini Cloud settings (shown only when GEMINI_CLOUD is selected)
        if (selectedModel == "GEMINI_CLOUD") {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Gemini Cloud Settings", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = geminiApiKey,
                            onValueChange = { geminiApiKey = it; settingsManager.saveGeminiApiKey(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API Key") },
                            placeholder = { Text("Paste your Gemini API key here") },
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation()
                        )
                        OutlinedTextField(
                            value = geminiModel,
                            onValueChange = { geminiModel = it; settingsManager.saveGeminiModel(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Model") },
                            placeholder = { Text("e.g. gemini-2.5-flash") },
                            singleLine = true
                        )
                    }
                }
            }
        }

        // LiteRT local models (shown when LITERT_LOCAL or AUTO selects local)
        if (selectedModel == "LITERT_LOCAL" || (selectedModel == "AUTO" && modelPicker.getBestModel() == ModelPicker.ModelType.LITERT_LOCAL)) {
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Local AI Models", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        if (isLoadingModels) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp).align(Alignment.CenterHorizontally))
                        } else {
                            availableModels.forEach { model ->
                                val isDownloaded = remember(model.name, downloadProgress) {
                                    java.io.File(context.filesDir, "models/${model.name}.litertlm").run { exists() && length() > 0 }
                                }
                                val isSelected = selectedLiteRTModel == model.name
                                val isCustom = remember(model.name) {
                                    modelDownloader.getCustomModels().any { it.name == model.name }
                                }
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp).let {
                                        if (isSelected) it.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)) else it
                                    },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                        else if (isDownloaded) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                    onClick = {
                                        if (isDownloaded) {
                                            selectedLiteRTModel = model.name
                                            settingsManager.saveSelectedLiteRTModel(model.name)
                                        }
                                    }
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = model.name,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(model.sizeLabel, style = MaterialTheme.typography.labelSmall)
                                                if (isCustom) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    IconButton(
                                                        onClick = {
                                                            modelDownloader.removeCustomModel(model.name)
                                                            scope.launch { availableModels = modelDownloader.fetchAvailableModels() }
                                                        },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                            }
                                        }
                                        Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                        if (isDownloaded) {
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text("✓ Ready", color = androidx.compose.ui.graphics.Color(0xFF2E7D32), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                if (isSelected) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(4.dp)) {
                                                        Text("ACTIVE", modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontSize = 8.sp)
                                                    }
                                                }
                                            }
                                        } else if (downloadingModelName == model.name) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            LinearProgressIndicator(progress = { downloadProgress ?: 0f }, modifier = Modifier.fillMaxWidth())
                                            Text("Downloading: ${((downloadProgress ?: 0f) * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                                        } else {
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(onClick = { modelDownloader.startDownload(model) }, modifier = Modifier.height(32.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp)) {
                                                Text("Download", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { showModelLibraryDialog = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Browse Library")
                                }
                                OutlinedButton(onClick = { showAddCustomModelDialog = true }, modifier = Modifier.weight(0.7f), shape = RoundedCornerShape(12.dp)) {
                                    Icon(Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Custom")
                                }
                            }
                        }
                        downloadError?.let { Text("Error: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    }
                }
            }
        }

        // Todoist integration (always visible)
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Todoist", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Get your API token from todoist.com → Settings → Integrations → Developer",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = todoistApiToken,
                        onValueChange = { todoistApiToken = it; settingsManager.saveTodoistApiToken(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("API Token") },
                        placeholder = { Text("Paste your Todoist API token here") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )
                }
            }
        }

        // ── Daily Goals (Sense of Day) ────────────────────────────────────────
        item { SettingsSectionLabel("Daily Goals") }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Set your daily targets to power the Sense of Day score.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    GoalTextField(
                        label = "Daily Steps Goal",
                        value = stepsGoalText,
                        unit = "steps",
                        onValueChange = {
                            stepsGoalText = it
                            it.toIntOrNull()?.let { v -> settingsManager.setStepsGoal(v) }
                        },
                        onDone = {
                            stepsGoalText.toIntOrNull()?.let { settingsManager.setStepsGoal(it) }
                        }
                    )
                    GoalTextField(
                        label = "Sleep Goal",
                        value = sleepGoalText,
                        unit = "hours",
                        onValueChange = {
                            sleepGoalText = it
                            it.toFloatOrNull()?.let { v -> settingsManager.setSleepGoalHours(v) }
                        },
                        onDone = {
                            sleepGoalText.toFloatOrNull()?.let { settingsManager.setSleepGoalHours(it) }
                        }
                    )
                    GoalTextField(
                        label = "Daily Exercise Goal",
                        value = exerciseGoalText,
                        unit = "min",
                        onValueChange = {
                            exerciseGoalText = it
                            it.toIntOrNull()?.let { v -> settingsManager.setExerciseGoalMinutes(v) }
                        },
                        onDone = {
                            exerciseGoalText.toIntOrNull()?.let { settingsManager.setExerciseGoalMinutes(it) }
                        }
                    )
                    GoalTextField(
                        label = "Digital Focus Baseline",
                        value = focusGoalText,
                        unit = "min/day",
                        onValueChange = {
                            focusGoalText = it
                            it.toIntOrNull()?.let { v -> settingsManager.setDigitalFocusBaselineMinutes(v) }
                        },
                        onDone = {
                            focusGoalText.toIntOrNull()?.let { settingsManager.setDigitalFocusBaselineMinutes(it) }
                        }
                    )
                }
            }
        }

        // ── Backup & Restore ──────────────────────────────────────────────────
        item { SettingsSectionLabel("Backup & Restore") }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Full export: monitored apps, captures (90 days), reflections, and health snapshots.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { createBackupLauncher.launch("2ndbrain-backup.json") }, modifier = Modifier.weight(1f)) {
                            Text("Export")
                        }
                        OutlinedButton(onClick = { openBackupLauncher.launch(arrayOf("application/json")) }, modifier = Modifier.weight(1f)) {
                            Text("Restore")
                        }
                    }
                }
            }
        }

        // ── Monitored Apps ────────────────────────────────────────────────────
        item { SettingsSectionLabel("Monitored Apps") }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { monitorAppsExpanded = !monitorAppsExpanded }.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            val count = monitoredApps.size
                            Text("App filter", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (count == 0) "All notifications captured" else "$count app${if (count == 1) "" else "s"} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(if (monitorAppsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
                    }

                    AnimatedVisibility(visible = monitorAppsExpanded) {
                        Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            OutlinedTextField(
                                value = appSearchQuery,
                                onValueChange = { appSearchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Search apps") },
                                singleLine = true,
                                trailingIcon = {
                                    if (appSearchQuery.isNotEmpty()) {
                                        IconButton(onClick = { appSearchQuery = "" }) { Text("✕") }
                                    }
                                }
                            )
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                TextButton(onClick = {
                                    val all = allApps.map { it.packageName }.toSet()
                                    settingsManager.saveMonitoredApps(all)
                                    monitoredApps = all
                                }) { Text("All", fontSize = 12.sp) }
                                TextButton(onClick = {
                                    settingsManager.saveMonitoredApps(emptySet())
                                    monitoredApps = emptySet()
                                }) { Text("None (show all)", fontSize = 12.sp) }
                            }
                            filteredApps.forEach { pkg ->
                                val appName = packageManager.getApplicationLabel(pkg.applicationInfo ?: return@forEach).toString()
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = monitoredApps.contains(pkg.packageName),
                                        onCheckedChange = { checked ->
                                            val current = monitoredApps.toMutableSet()
                                            if (checked) current.add(pkg.packageName)
                                            else { current.remove(pkg.packageName); onUnmonitoredAppRemoved(pkg.packageName) }
                                            settingsManager.saveMonitoredApps(current)
                                            monitoredApps = current
                                        }
                                    )
                                    Column(modifier = Modifier.padding(start = 8.dp)) {
                                        Text(appName, style = MaterialTheme.typography.bodyMedium)
                                        Text(pkg.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Add Custom Model dialog
    if (showAddCustomModelDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustomModelDialog = false },
            title = { Text("Add Custom LiteRT Model") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = customModelName, onValueChange = { customModelName = it }, label = { Text("Model Name") }, placeholder = { Text("e.g. DeepSeek-Qwen-1.5B") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = customModelUrl, onValueChange = { customModelUrl = it }, label = { Text("Download URL") }, placeholder = { Text("https://huggingface.co/...") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = customModelDesc, onValueChange = { customModelDesc = it }, label = { Text("Description") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = customModelSize, onValueChange = { customModelSize = it }, label = { Text("Size Label") }, placeholder = { Text("e.g. 1.2GB") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (customModelName.isNotBlank() && customModelUrl.isNotBlank()) {
                        modelDownloader.addCustomModel(ModelDownloader.LiteRTModel(name = customModelName.trim(), url = customModelUrl.trim(), description = customModelDesc.trim().ifBlank { "Custom registered LiteRT model." }, sizeLabel = customModelSize.trim().ifBlank { "Custom" }))
                        scope.launch { availableModels = modelDownloader.fetchAvailableModels() }
                        showAddCustomModelDialog = false
                        customModelName = ""; customModelUrl = ""; customModelDesc = ""; customModelSize = ""
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddCustomModelDialog = false }) { Text("Cancel") } }
        )
    }

    // Model Library dialog
    if (showModelLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showModelLibraryDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text("Compatible Model Library")
                }
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Browse and choose compatible local LiteRT models to add to your capture selection.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                    if (isLoadingRegistryModels) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (remoteRegistryModels.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { Text("No models found in registry.", color = MaterialTheme.colorScheme.error) }
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            items(remoteRegistryModels) { model ->
                                val isAlreadyAdded = availableModels.any { it.name == model.name }
                                Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = RoundedCornerShape(8.dp)) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(model.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(model.sizeLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                            }
                                            if (isAlreadyAdded) {
                                                Surface(color = androidx.compose.ui.graphics.Color(0xFFE8F5E9), shape = RoundedCornerShape(4.dp)) { Text("ADDED", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color(0xFF2E7D32), fontWeight = FontWeight.Bold) }
                                            } else {
                                                Button(onClick = { modelDownloader.registerModel(model); scope.launch { availableModels = modelDownloader.fetchAvailableModels() } }, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp), modifier = Modifier.height(30.dp), shape = RoundedCornerShape(6.dp)) { Text("Choose", fontSize = 11.sp) }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModelLibraryDialog = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun SettingsSectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
private fun GoalTextField(
    label: String,
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Text(unit, style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.width(110.dp),
            singleLine = true,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }),
            textStyle = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        )
    }
}

@Composable
private fun PermissionRow(
    label: String,
    subtitle: String,
    isGranted: Boolean,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isGranted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
