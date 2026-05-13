package com.alex.a2ndbrain.ui.usage

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.BuildConfig
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DigitalTimeScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val database = remember { AppDatabase.getDatabase(context) }
    val digitalTimeManager = remember { DigitalTimeManager(context) }
    
    val today = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
    val usageStats by database.memoryDao().getUsageStatsForDate(today).collectAsState(initial = emptyList())
    
    var isPermissionGranted by remember { mutableStateOf(digitalTimeManager.isPermissionGranted()) }

    // Refresh permission status when screen is opened
    LaunchedEffect(Unit) {
        digitalTimeManager.syncUsageStats()
        isPermissionGranted = digitalTimeManager.isPermissionGranted()
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
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!isPermissionGranted) {
            PermissionRequiredView {
                context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        } else {
            if (usageStats.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No usage data captured yet today.")
                }
            } else {
                val totalTime = usageStats.sumOf { it.totalTimeVisibleMs }
                UsageSummaryCard(totalTime)
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text("Top Apps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(usageStats.sortedByDescending { it.totalTimeVisibleMs }.take(15)) { stat ->
                        UsageItem(stat)
                    }
                }
            }
        }
    }
}

@Composable
fun UsageSummaryCard(totalTimeMs: Long) {
    val hours = totalTimeMs / 1000 / 3600
    val minutes = (totalTimeMs / 1000 / 60) % 60
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Total Screen Time Today", style = MaterialTheme.typography.labelMedium)
            Text(
                text = "${hours}h ${minutes}m",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun UsageItem(stat: UsageStatEntity) {
    val context = LocalContext.current
    val appName = remember(stat.packageName) {
        try {
            val appInfo = context.packageManager.getApplicationInfo(stat.packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            stat.packageName.substringAfterLast(".")
        }
    }
    
    val minutes = stat.totalTimeVisibleMs / 1000 / 60
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(appName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Text(stat.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Text(
                text = if (minutes > 60) "${minutes/60}h ${minutes%60}m" else "${minutes}m",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun PermissionRequiredView(onGrantClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Usage Access Required", style = MaterialTheme.typography.titleLarge)
            Text("2ndBrain needs permission to track app usage time.", style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onGrantClick) {
                Text("Grant Permission")
            }
        }
    }
}
