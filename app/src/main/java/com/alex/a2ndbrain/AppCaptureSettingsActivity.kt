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
import com.alex.a2ndbrain.core.capture.HomeSummaryConfig
import com.alex.a2ndbrain.core.capture.HomeDefaultMode
import com.alex.a2ndbrain.core.memory.HabitEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
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
                        themePreference = themePreference,
                        onThemeChange = { newTheme ->
                            settingsManager.saveThemePreference(newTheme)
                            themePreference = newTheme
                        },
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
    themePreference: String,
    onThemeChange: (String) -> Unit,
    onBack: () -> Unit,
    onRestartService: () -> Unit,
    activeHabits: List<HabitEntity> = emptyList(),
    onAddCustomHabit: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onDeleteHabit: (String) -> Unit = {},
    onToggleHabitActive: (String) -> Unit = {},
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

    var homeSummaryConfig by remember { mutableStateOf(settingsManager.getHomeSummaryConfig()) }

    var appSearchQuery by remember { mutableStateOf("") }
    val filteredApps = if (appSearchQuery.isEmpty()) {
        allApps
    } else {
        allApps.filter { 
            packageManager.getApplicationLabel(it.applicationInfo ?: return@filter false).toString()
                .contains(appSearchQuery, ignoreCase = true) || it.packageName.contains(appSearchQuery, ignoreCase = true)
        }
    }

    var isListenerEnabled by remember { mutableStateOf(false) }
    var showDebugLogs by remember { mutableStateOf(false) }
    
    // Check listener status periodically
    LaunchedEffect(Unit) {
        while(true) {
            val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            isListenerEnabled = enabledListeners?.contains(context.packageName) == true
            kotlinx.coroutines.delay(2000)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Capture Settings", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        text = "v${com.alex.a2ndbrain.BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
                Button(onClick = onBack) { Text("Done") }
            }
        }
        
        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Backup & Restore",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Save your monitored apps and habits/medications to a JSON file. Restore after a database reset.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                createBackupLauncher.launch("2ndbrain-settings-backup.json")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = {
                                openBackupLauncher.launch(arrayOf("application/json"))
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Restore")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Home Summary Settings
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Home Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "Configure what appears on the home screen summary card.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Default mode
                    Text("Default mode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        HomeDefaultMode.entries.forEach { mode ->
                            val label = when (mode) {
                                HomeDefaultMode.SUMMARY_ONLY    -> "Summary only"
                                HomeDefaultMode.REMEMBER_LAST   -> "Remember last state"
                                HomeDefaultMode.ALWAYS_EXPANDED -> "Always expanded"
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val updated = homeSummaryConfig.copy(defaultMode = mode)
                                        homeSummaryConfig = updated
                                        settingsManager.saveHomeSummaryConfig(updated)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = homeSummaryConfig.defaultMode == mode,
                                    onClick = {
                                        val updated = homeSummaryConfig.copy(defaultMode = mode)
                                        homeSummaryConfig = updated
                                        settingsManager.saveHomeSummaryConfig(updated)
                                    }
                                )
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Text("Summary card sections", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

                    // Toggle rows
                    listOf(
                        "Show Sense of Day explanation"  to homeSummaryConfig.showSenseOfDayText,
                        "Show alerts"                    to homeSummaryConfig.showAlerts,
                        "Show habits progress pill"      to homeSummaryConfig.showHabitPill,
                        "Show next event pill"           to homeSummaryConfig.showNextEventPill,
                        "Show steps pill"                to homeSummaryConfig.showStepsPill,
                        "Show sleep / meditation pill"   to homeSummaryConfig.showSleepMeditationPill
                    ).forEach { (label, value) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = value,
                                onCheckedChange = { checked ->
                                    val updated = when (label) {
                                        "Show Sense of Day explanation"  -> homeSummaryConfig.copy(showSenseOfDayText     = checked)
                                        "Show alerts"                    -> homeSummaryConfig.copy(showAlerts              = checked)
                                        "Show habits progress pill"      -> homeSummaryConfig.copy(showHabitPill           = checked)
                                        "Show next event pill"           -> homeSummaryConfig.copy(showNextEventPill       = checked)
                                        "Show steps pill"                -> homeSummaryConfig.copy(showStepsPill           = checked)
                                        "Show sleep / meditation pill"   -> homeSummaryConfig.copy(showSleepMeditationPill = checked)
                                        else -> homeSummaryConfig
                                    }
                                    homeSummaryConfig = updated
                                    settingsManager.saveHomeSummaryConfig(updated)
                                }
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(4.dp)) }

        // System Permissions Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isListenerEnabled) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("System Access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { showDebugLogs = !showDebugLogs }) {
                            Text(if (showDebugLogs) "Hide Logs" else "Show Logs", fontSize = 12.sp)
                        }
                    }
                    Text(
                        if (isListenerEnabled) "✓ Notification access granted" else "✗ Notification access required",
                        style = MaterialTheme.typography.bodySmall
                    )
                    
                    if (showDebugLogs) {
                        Spacer(modifier = Modifier.height(8.dp))
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

                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Button(onClick = {
                            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                        }) {
                            Text("System")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }) {
                            Text("Usage Access")
                        }
                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = onRestartService,
                            modifier = Modifier
                                .width(130.dp)
                                .height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary,
                                containerColor = Color.Transparent
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Text(
                                text = "Refresh",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        if (!isListenerEnabled) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "⚠️ Notification Access is Restricted",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "To fix this:\n1. Click 'OPEN APP INFO' below.\n2. In that screen, if you don't see (⋮), SCROLL DOWN and tap 'Notifications' or 'Advanced' first.\n3. If still not there, return to 'System' and try to toggle the greyed-out switch again to 'trigger' the menu.\n4. Once unblocked, the switch in 'System' will work.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = android.net.Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("OPEN APP INFO")
                        }
                    }
                }
            }
        }

        // Nearby Sync Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "NEARBY SYNC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val statusText = when (syncStatus) {
                            NearbySyncManager.SyncStatus.Idle -> "Sync screen time and meditations with nearby devices."
                            NearbySyncManager.SyncStatus.Scanning -> "Searching for nearby devices..."
                            is NearbySyncManager.SyncStatus.Connecting -> "Connecting to ${(syncStatus as NearbySyncManager.SyncStatus.Connecting).deviceName}..."
                            is NearbySyncManager.SyncStatus.Syncing -> "Syncing data..."
                            is NearbySyncManager.SyncStatus.Success -> "Successfully synchronized!"
                            is NearbySyncManager.SyncStatus.Failed -> "Sync failed: ${(syncStatus as NearbySyncManager.SyncStatus.Failed).reason}"
                        }
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    val buttonText = when (syncStatus) {
                        NearbySyncManager.SyncStatus.Idle -> "Sync"
                        NearbySyncManager.SyncStatus.Scanning -> "Stop"
                        is NearbySyncManager.SyncStatus.Connecting -> "Stop"
                        is NearbySyncManager.SyncStatus.Syncing -> "Syncing"
                        is NearbySyncManager.SyncStatus.Success -> "Sync"
                        is NearbySyncManager.SyncStatus.Failed -> "Retry"
                    }
                    
                    val isScanningOrConnecting = syncStatus is NearbySyncManager.SyncStatus.Scanning || syncStatus is NearbySyncManager.SyncStatus.Connecting
                    
                    Button(
                        onClick = {
                            if (isScanningOrConnecting || syncStatus is NearbySyncManager.SyncStatus.Syncing) {
                                onStopSync()
                            } else {
                                val hasPermissions = permissionsToRequest.all {
                                    androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                }
                                if (hasPermissions) {
                                    onStartSync(true)
                                } else {
                                    syncPermissionLauncher.launch(permissionsToRequest)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isScanningOrConnecting) MaterialTheme.colorScheme.error.copy(alpha = 0.1f) else MaterialTheme.colorScheme.primary,
                            contentColor = if (isScanningOrConnecting) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        if (syncStatus is NearbySyncManager.SyncStatus.Syncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(buttonText, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "🎨 Theme Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Choose your preferred appearance for 2ndBrain.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            "SYSTEM" to "System",
                            "LIGHT" to "Light",
                            "DARK" to "Dark"
                        ).forEach { (value, label) ->
                            val isSelected = themePreference == value
                            Button(
                                onClick = { onThemeChange(value) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                                    },
                                    contentColor = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                ),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Daily Habits & Routines Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "💊 Daily Routines & Medications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Configure daily checklists, medications, and custom alarms.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // List of existing habits
                    if (activeHabits.isEmpty()) {
                        Text(
                            text = "No routines configured.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            activeHabits.forEach { habit ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = if (habit.isMedication) "💊" else "🏃",
                                            modifier = Modifier.padding(end = 8.dp),
                                            fontSize = 18.sp
                                        )
                                        Column {
                                            Text(
                                                text = habit.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "Alarm: ${habit.timeString}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Switch(
                                            checked = habit.isActive,
                                            onCheckedChange = { onToggleHabitActive(habit.id) }
                                        )
                                        
                                        // Allow deleting custom habits (anything that is not prepopulated)
                                        if (habit.id != "default_meds" && habit.id != "default_walk" && habit.id != "default_reflection") {
                                            IconButton(
                                                onClick = { onDeleteHabit(habit.id) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Delete,
                                                    contentDescription = "Delete Habit",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Add Custom Habit Form
                    Text(
                        text = "Add Custom Habit / Medication",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    var newHabitName by remember { mutableStateOf("") }
                    var newHabitTime by remember { mutableStateOf("") }
                    var newHabitIsMedication by remember { mutableStateOf(false) }
                    var validationError by remember { mutableStateOf<String?>(null) }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newHabitName,
                            onValueChange = { newHabitName = it },
                            label = { Text("Name (e.g. Vitamin D)") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium
                        )

                        OutlinedTextField(
                            value = newHabitTime,
                            onValueChange = { newHabitTime = it },
                            label = { Text("Time (e.g. 08:30)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            placeholder = { Text("HH:mm") },
                            textStyle = MaterialTheme.typography.bodyMedium
                        )
                    }

                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = newHabitIsMedication,
                                onCheckedChange = { newHabitIsMedication = it }
                            )
                            Text(
                                text = "Is Medication Alarm",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Button(
                            onClick = {
                                if (newHabitName.isBlank()) {
                                    validationError = "Please enter habit name."
                                    return@Button
                                }
                                if (!newHabitTime.matches(Regex("^([01]?[0-9]|2[0-3]):[0-5][0-9]$"))) {
                                    validationError = "Use HH:mm format (e.g. 08:30)."
                                    return@Button
                                }
                                validationError = null
                                onAddCustomHabit(newHabitName.trim(), newHabitTime.trim(), newHabitIsMedication)
                                newHabitName = ""
                                newHabitTime = ""
                                newHabitIsMedication = false
                            }
                        ) {
                            Text("Add Alarm")
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
        }

        item {
            OutlinedTextField(
                value = appSearchQuery,
                onValueChange = { appSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps (e.g. Gmail)") },
                singleLine = true,
                trailingIcon = {
                    if (appSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { appSearchQuery = "" }) {
                            Text("✕")
                        }
                    }
                }
            )
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Monitor Apps", style = MaterialTheme.typography.titleMedium)
                Row {
                    TextButton(onClick = {
                        val allPackageNames = allApps.map { it.packageName }.toSet()
                        settingsManager.saveMonitoredApps(allPackageNames)
                        monitoredApps = allPackageNames
                    }) {
                        Text("Select All", fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        val removedApps = monitoredApps.toList()
                        settingsManager.saveMonitoredApps(emptySet())
                        monitoredApps = emptySet()
                        removedApps.forEach { onUnmonitoredAppRemoved(it) }
                    }) {
                        Text("Deselect All", fontSize = 12.sp)
                    }
                }
            }
        }
        
        items(filteredApps) { pkg ->
            val appName = packageManager.getApplicationLabel(pkg.applicationInfo ?: return@items).toString()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = monitoredApps.contains(pkg.packageName),
                    onCheckedChange = { checked ->
                        val current = monitoredApps.toMutableSet()
                        if (checked) {
                            current.add(pkg.packageName)
                        } else {
                            current.remove(pkg.packageName)
                            onUnmonitoredAppRemoved(pkg.packageName)
                        }
                        settingsManager.saveMonitoredApps(current)
                        monitoredApps = current
                    }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(appName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = pkg.packageName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
