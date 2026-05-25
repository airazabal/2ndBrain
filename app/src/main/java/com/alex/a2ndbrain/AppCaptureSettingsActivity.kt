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
    var calendarSyncEnabled by remember { mutableStateOf(settingsManager.isCalendarSyncEnabled()) }

    val requestCalendarPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            settingsManager.setCalendarSyncEnabled(true)
            calendarSyncEnabled = true
        }
    }

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

        // ── Home Screen ───────────────────────────────────────────────────────
        item { SettingsSectionLabel("Home Screen") }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Default mode", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)
                    Column {
                        HomeDefaultMode.entries.forEach { mode ->
                            val label = when (mode) {
                                HomeDefaultMode.SUMMARY_ONLY    -> "Summary only"
                                HomeDefaultMode.REMEMBER_LAST   -> "Remember last state"
                                HomeDefaultMode.ALWAYS_EXPANDED -> "Always expanded"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    val updated = homeSummaryConfig.copy(defaultMode = mode)
                                    homeSummaryConfig = updated
                                    settingsManager.saveHomeSummaryConfig(updated)
                                }.padding(vertical = 2.dp),
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

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Text("Summary card", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.outline)

                    listOf(
                        "Sense of Day text"       to homeSummaryConfig.showSenseOfDayText,
                        "Alerts"                  to homeSummaryConfig.showAlerts,
                        "Next event pill"         to homeSummaryConfig.showNextEventPill,
                        "Steps pill"              to homeSummaryConfig.showStepsPill,
                        "Sleep / meditation pill" to homeSummaryConfig.showSleepMeditationPill
                    ).forEach { (label, value) ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(
                                checked = value,
                                onCheckedChange = { checked ->
                                    val updated = when (label) {
                                        "Sense of Day text"       -> homeSummaryConfig.copy(showSenseOfDayText     = checked)
                                        "Alerts"                  -> homeSummaryConfig.copy(showAlerts              = checked)
                                        "Next event pill"         -> homeSummaryConfig.copy(showNextEventPill       = checked)
                                        "Steps pill"              -> homeSummaryConfig.copy(showStepsPill           = checked)
                                        "Sleep / meditation pill" -> homeSummaryConfig.copy(showSleepMeditationPill = checked)
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

        // ── Appearance ────────────────────────────────────────────────────────
        item { SettingsSectionLabel("Appearance") }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("SYSTEM" to "System", "LIGHT" to "Light", "DARK" to "Dark").forEach { (value, label) ->
                        val isSelected = themePreference == value
                        Button(
                            onClick = { onThemeChange(value) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                        }
                    }
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

        // ── Calendar Sync ─────────────────────────────────────────────────────
        item { SettingsSectionLabel("Calendar") }
        item {
            Card(modifier = Modifier.fillMaxWidth(), colors = cardColors, shape = cardShape) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Write habits to calendar", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Adds a calendar event when you complete a habit.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = calendarSyncEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context, android.Manifest.permission.WRITE_CALENDAR
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (hasPermission) {
                                    settingsManager.setCalendarSyncEnabled(true)
                                    calendarSyncEnabled = true
                                } else {
                                    requestCalendarPermission.launch(android.Manifest.permission.WRITE_CALENDAR)
                                }
                            } else {
                                settingsManager.setCalendarSyncEnabled(false)
                                calendarSyncEnabled = false
                            }
                        }
                    )
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
