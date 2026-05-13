package com.alex.a2ndbrain.ui.usage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.BuildConfig
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class TimePeriod {
    TODAY, WEEK, MONTH
}

@Composable
fun DigitalTimeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }
    val digitalTimeManager = remember { DigitalTimeManager(context) }
    
    var selectedPeriod by remember { mutableStateOf(TimePeriod.TODAY) }
    var isPermissionGranted by remember { mutableStateOf(digitalTimeManager.isPermissionGranted()) }
    var isSyncing by remember { mutableStateOf(false) }

    val startDate = remember(selectedPeriod) {
        val cal = Calendar.getInstance()
        when (selectedPeriod) {
            TimePeriod.TODAY -> {} // Already today
            TimePeriod.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -7)
            TimePeriod.MONTH -> cal.add(Calendar.MONTH, -1)
        }
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
    }

    val usageStats by database.memoryDao().getUsageStatsSinceFlow(startDate).collectAsState(initial = emptyList())
    
    val consolidatedStats = remember(usageStats) {
        usageStats.groupBy { it.packageName }
            .map { (packageName, stats) ->
                val totalTime = stats.sumOf { it.totalTimeVisibleMs }
                val deviceBreakdown = stats.groupBy { it.deviceName }
                    .mapValues { entry -> entry.value.sumOf { it.totalTimeVisibleMs } }
                ConsolidatedUsage(
                    packageName = packageName,
                    totalTimeMs = totalTime,
                    deviceBreakdown = deviceBreakdown,
                    lastTimestamp = stats.maxOf { it.lastTimestamp }
                )
            }.sortedByDescending { it.totalTimeMs }
    }

    LaunchedEffect(Unit) {
        if (isPermissionGranted) {
            isSyncing = true
            digitalTimeManager.syncUsageStats()
            isSyncing = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Digital Time",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            if (isPermissionGranted) {
                IconButton(
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            digitalTimeManager.syncUsageStats()
                            isSyncing = false
                        }
                    },
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Period Selector
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            TimePeriod.entries.forEachIndexed { index, period ->
                SegmentedButton(
                    selected = selectedPeriod == period,
                    onClick = { selectedPeriod = period },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = TimePeriod.entries.size)
                ) {
                    Text(period.name.lowercase().replaceFirstChar { it.uppercase() })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isPermissionGranted) {
            PermissionRequiredView {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        } else {
            if (consolidatedStats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isSyncing) CircularProgressIndicator()
                    else Text("No usage data for this period.")
                }
            } else {
                val totalTime = consolidatedStats.sumOf { it.totalTimeMs }
                UsageSummaryCard(totalTime, selectedPeriod)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("App Totals Across Devices", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(consolidatedStats.take(30)) { stat ->
                        ConsolidatedUsageItem(stat)
                    }
                }
            }
        }
    }
}

data class ConsolidatedUsage(
    val packageName: String,
    val totalTimeMs: Long,
    val deviceBreakdown: Map<String, Long>,
    val lastTimestamp: Long
)

@Composable
fun ConsolidatedUsageItem(stat: ConsolidatedUsage) {
    val context = LocalContext.current
    val appName = remember(stat.packageName) {
        try {
            val appInfo = context.packageManager.getApplicationInfo(stat.packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            stat.packageName.substringAfterLast(".")
        }
    }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.ExtraBold)
                    Text(stat.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = formatDuration(stat.totalTimeMs),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            
            if (stat.deviceBreakdown.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                Spacer(modifier = Modifier.height(4.dp))
                stat.deviceBreakdown.forEach { (device, time) ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(device, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(formatDuration(time), style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else if (stat.deviceBreakdown.isNotEmpty()) {
                Text(
                    text = "On ${stat.deviceBreakdown.keys.first()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val mins = ms / 1000 / 60
    val hours = mins / 60
    return if (hours > 0) "${hours}h ${mins % 60}m" else "${mins}m"
}

@Composable
fun UsageSummaryCard(totalTimeMs: Long, period: TimePeriod) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Total Usage (${period.name.lowercase().replaceFirstChar { it.uppercase() }})", style = MaterialTheme.typography.labelMedium)
            Text(
                text = formatDuration(totalTimeMs),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun PermissionRequiredView(onGrantClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Usage Access Required", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "To sync digital time across devices, 2ndBrain needs permission to see how long apps are used.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGrantClick) {
                Text("Grant Permission")
            }
        }
    }
}
