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
import com.alex.a2ndbrain.core.memory.HabitEntity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow


class AppCaptureSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settingsManager = CaptureSettingsManager(this)
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    AppCaptureSettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { finish() },
                        onRestartService = {
                            val componentName = android.content.ComponentName(this, com.alex.a2ndbrain.core.capture.NotificationCaptureService::class.java)
                            packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                            packageManager.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                        }
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
    activeHabits: List<HabitEntity> = emptyList(),
    onAddCustomHabit: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onDeleteHabit: (String) -> Unit = {},
    onToggleHabitActive: (String) -> Unit = {}
) {
    var monitoredApps by remember { mutableStateOf(settingsManager.getMonitoredApps()) }
    val debugEvents by com.alex.a2ndbrain.core.capture.CaptureDebugStore.events.collectAsState()
    val context = LocalContext.current
    val packageManager = context.packageManager
    
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
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
        
        Spacer(modifier = Modifier.height(16.dp))

        // System Permissions Card
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

        if (!isListenerEnabled) {
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

        Spacer(modifier = Modifier.height(16.dp))

        // Daily Habits & Routines Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
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
                    Box(modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(activeHabits) { habit ->
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
                            text = "Is Medication",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = {
                            if (newHabitName.isBlank()) {
                                validationError = "Name cannot be empty"
                                return@Button
                            }
                            val parts = newHabitTime.split(":")
                            if (parts.size != 2 || parts[0].toIntOrNull() == null || parts[1].toIntOrNull() == null || 
                                parts[0].toInt() !in 0..23 || parts[1].toInt() !in 0..59) {
                                validationError = "Use 24h format (e.g. 08:30 or 21:15)"
                                return@Button
                            }
                            
                            validationError = null
                            onAddCustomHabit(newHabitName.trim(), newHabitTime.trim(), newHabitIsMedication)
                            newHabitName = ""
                            newHabitTime = ""
                            newHabitIsMedication = false
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Add Routine", fontSize = 12.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(8.dp))
        
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
                    settingsManager.saveMonitoredApps(emptySet())
                    monitoredApps = emptySet()
                }) {
                    Text("Deselect All", fontSize = 12.sp)
                }
            }
        }
        
        LazyColumn {
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
                            if (checked) current.add(pkg.packageName) else current.remove(pkg.packageName)
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
        }
    }
}
