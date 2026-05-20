package com.alex.a2ndbrain.ui.usage

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.BuildConfig
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.ui.theme.*
import com.alex.a2ndbrain.ConsolidatedUsage
import org.koin.androidx.compose.koinViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

enum class TimePeriod {
    TODAY, WEEK, MONTH
}

@Composable
fun DigitalTimeScreen(
    viewModel: DigitalTimeViewModel = koinViewModel(),
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    
    val selectedPeriod by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val isPermissionGranted by viewModel.isPermissionGranted.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncing.collectAsStateWithLifecycle()
    val consolidatedStats by viewModel.consolidatedUsage.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.updatePermissionStatus()
        viewModel.syncUsageStats()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isPermissionGranted) {
                    IconButton(
                        onClick = { viewModel.syncUsageStats() },
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
        }

        item {
            // Period Selector
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                TimePeriod.entries.forEachIndexed { index, period ->
                    SegmentedButton(
                        selected = selectedPeriod == period,
                        onClick = { viewModel.setPeriod(period) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = TimePeriod.entries.size)
                    ) {
                        Text(period.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }
        }

        if (!isPermissionGranted) {
            item {
                PermissionRequiredView {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            }
        } else {
            if (consolidatedStats.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        if (isSyncing) CircularProgressIndicator()
                        else Text("No usage data for this period.", color = MaterialTheme.colorScheme.outline)
                    }
                }
            } else {
                val totalTime = consolidatedStats.sumOf { it.totalTimeMs }
                item {
                    UsageSummaryCard(totalTime, selectedPeriod)
                }

                // Digital Habits Correlation Insights (Recommendation 5)
                item {
                    val habitCorrelationText = remember(consolidatedStats) {
                        val socialMediaApps = listOf("com.instagram.android", "com.zhiliaoapp.musically", "com.twitter.android", "com.facebook.katana", "com.whatsapp", "com.snapchat.android")
                        val productivityApps = listOf("com.todoist", "com.microsoft.todos", "com.google.android.apps.tasks", "com.google.android.calendar", "com.alex.a2ndbrain")
                        
                        val socialTimeMs = consolidatedStats.filter { socialMediaApps.contains(it.packageName) }.sumOf { it.totalTimeMs }
                        val prodTimeMs = consolidatedStats.filter { productivityApps.contains(it.packageName) }.sumOf { it.totalTimeMs }
                        
                        val socialMins = socialTimeMs / 1000 / 60
                        val prodMins = prodTimeMs / 1000 / 60
                        
                        when {
                            socialMins > 60 && prodMins < 15 -> {
                                "⚠️ High distraction pattern detected. You've spent ${socialMins}m on social/chat apps, which is 4x more than your focused productivity apps today. Consider a quick digital detox to stay on track!"
                            }
                            prodMins > 30 -> {
                                "🎉 Fantastic focus today! Your productivity app usage is active at ${prodMins}m. This correlation indicates a strong alignment with your daily intentional schedule!"
                            }
                            socialMins > 0 -> {
                                "💡 Co-Pilot Insight: Moderate social media balance (${socialMins}m) observed today. Maintain this healthy distraction boundary for high mental energy!"
                            }
                            else -> {
                                "💡 Co-Pilot Insight: Safe, minimal distraction pattern logged today. Keep up the clean focus flow!"
                            }
                        }
                    }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "HABIT CORRELATIONS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = habitCorrelationText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 20.sp
                            )
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Visual Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                
                item {
                    UsageBarChart(consolidatedStats.take(5))
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Most Used", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }

                items(consolidatedStats.take(30)) { stat ->
                    ConsolidatedUsageItem(stat)
                }
            }
        }
        
        // Add a final spacer for better scrolling at the bottom
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun UsageBarChart(topApps: List<ConsolidatedUsage>) {
    val maxTime = topApps.firstOrNull()?.totalTimeMs ?: 1L
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        topApps.forEach { app ->
            val appName = remember(app.packageName) {
                try {
                    val appInfo = context.packageManager.getApplicationInfo(app.packageName, 0)
                    context.packageManager.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    app.packageName.substringAfterLast(".")
                }
            }
            
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(appName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    Text(formatDuration(app.totalTimeMs), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                }
                Spacer(modifier = Modifier.height(6.dp))
                val fraction = (app.totalTimeMs.toFloat() / maxTime).coerceIn(0.05f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
                )
            }
        }
    }
}

private fun getAppColors(appName: String): Pair<Color, Color> {
    val hash = appName.hashCode()
    return when (kotlin.math.abs(hash) % 5) {
        0 -> PastelBlue to PastelBlueText
        1 -> PastelRed to PastelRedText
        2 -> PastelGreen to PastelGreenText
        3 -> PastelYellow to PastelYellowText
        else -> PastelPurple to PastelPurpleText
    }
}

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
    
    val colors = remember(appName) { getAppColors(appName) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.first),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = appName.take(1).uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.second
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(appName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stat.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = formatDuration(stat.totalTimeMs),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (stat.deviceBreakdown.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                stat.deviceBreakdown.forEach { (device, time) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 44.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(device, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        Text(formatDuration(time), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
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
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = PastelBlue)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "Total Usage (${period.name.lowercase().replaceFirstChar { it.uppercase() }})", 
                style = MaterialTheme.typography.labelMedium,
                color = PastelBlueText.copy(alpha = 0.8f)
            )
            Text(
                text = formatDuration(totalTimeMs),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Black,
                color = PastelBlueText
            )
        }
    }
}

@Composable
fun PermissionRequiredView(onGrantClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            Spacer(modifier = Modifier.height(16.dp))
            Text("Access Required", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "2ndBrain needs permission to track app usage time.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onGrantClick, shape = RoundedCornerShape(12.dp)) {
                Text("Grant Permission")
            }
        }
    }
}
