package com.alex.a2ndbrain.ui.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Hotel
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alex.a2ndbrain.core.health.DailyHealthMetrics
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun HealthScreen(viewModel: HealthViewModel, modifier: Modifier = Modifier) {
    val period           by viewModel.selectedPeriod.collectAsStateWithLifecycle()
    val metrics          by viewModel.dailyMetrics.collectAsStateWithLifecycle()
    val isLoading        by viewModel.isLoading.collectAsStateWithLifecycle()
    val lastRefreshedMs  by viewModel.lastRefreshedMs.collectAsStateWithLifecycle()
    val stepsGoal        = viewModel.stepsGoal

    val lastRefreshedLabel = remember(lastRefreshedMs) {
        if (lastRefreshedMs == 0L) "Never synced"
        else "Synced " + SimpleDateFormat("h:mm a", Locale.getDefault())
            .format(java.util.Date(lastRefreshedMs))
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Top bar ──────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Health",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$lastRefreshedLabel · Zepp syncs to HC periodically",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            IconButton(onClick = { viewModel.refresh() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }

        // ── Period selector ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            HealthPeriod.entries.forEach { p ->
                FilterChip(
                    selected = period == p,
                    onClick  = { viewModel.selectPeriod(p) },
                    label    = {
                        Text(when (p) {
                            HealthPeriod.TODAY -> "Today"
                            HealthPeriod.WEEK  -> "This Week"
                            HealthPeriod.MONTH -> "30 Days"
                        })
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (metrics.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No health data available", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Connect Health Connect on your phone\nor sync with a paired device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            return@Column
        }

        if (period == HealthPeriod.TODAY) {
            TodayView(day = metrics.first(), stepsGoal = stepsGoal, modifier = Modifier.padding(horizontal = 16.dp))
        } else {
            PeriodView(metrics = metrics, period = period)
        }
    }
}

// ── Today ────────────────────────────────────────────────────────────────────

@Composable
private fun TodayView(day: DailyHealthMetrics, stepsGoal: Int, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { StepsCard(day, stepsGoal) }
        item { SleepCard(day) }
        item { HeartCard(day) }
    }
}

@Composable
private fun StepsCard(day: DailyHealthMetrics, stepsGoal: Int) {
    val goal = stepsGoal.toLong()
    val progress = if (goal > 0) (day.steps.toFloat() / goal).coerceIn(0f, 1f) else 0f
    MetricCard(icon = Icons.Default.DirectionsRun, iconTint = Color(0xFF42A5F5), title = "Steps") {
        if (day.hasSteps) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "%,d".format(day.steps),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "/ %,d goal".format(goal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                color = Color(0xFF42A5F5),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "${(progress * 100).toInt()}% of daily goal",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            Text("No step data", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun SleepCard(day: DailyHealthMetrics) {
    MetricCard(icon = Icons.Default.Hotel, iconTint = Color(0xFF7E57C2), title = "Sleep") {
        if (day.hasSleep) {
            Text(
                text = "${day.sleepHours}h ${day.sleepMins}m",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(4.dp))
            val quality = when {
                day.sleepMinutes >= 480 -> "Well rested"
                day.sleepMinutes >= 360 -> "Adequate"
                day.sleepMinutes >= 240 -> "Light sleep"
                else                   -> "Very little sleep"
            }
            Text(quality, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline)
        } else {
            Text("No sleep data", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun HeartCard(day: DailyHealthMetrics) {
    MetricCard(icon = Icons.Default.Favorite, iconTint = Color(0xFFEF5350), title = "Heart Rate") {
        if (day.hasHeart) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${day.avgHeartRate}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = "avg bpm",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                HRLabel("Min", day.minHeartRate, Color(0xFF66BB6A))
                HRLabel("Avg", day.avgHeartRate, Color(0xFFEF5350))
                HRLabel("Max", day.maxHeartRate, Color(0xFFFF7043))
            }
        } else {
            Text("No heart rate data", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun HRLabel(label: String, value: Int, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "$value", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = color)
        Text(text = label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun MetricCard(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = iconTint,
                    modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

// ── Week / Month ─────────────────────────────────────────────────────────────

@Composable
private fun PeriodView(metrics: List<DailyHealthMetrics>, period: HealthPeriod) {
    val avgSteps  = metrics.filter { it.hasSteps }.let { l -> if (l.isEmpty()) 0L else l.sumOf { it.steps } / l.size }
    val avgSleep  = metrics.filter { it.hasSleep }.let { l -> if (l.isEmpty()) 0  else l.sumOf { it.sleepMinutes } / l.size }
    val avgHR     = metrics.filter { it.hasHeart }.let { l -> if (l.isEmpty()) 0  else l.sumOf { it.avgHeartRate } / l.size }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Summary header
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = if (period == HealthPeriod.WEEK) "7-Day Averages" else "30-Day Averages",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SummaryStatCol(
                            icon = Icons.Default.DirectionsRun,
                            tint = Color(0xFF42A5F5),
                            value = if (avgSteps > 0) "%,d".format(avgSteps) else "—",
                            label = "avg steps"
                        )
                        SummaryStatCol(
                            icon = Icons.Default.Hotel,
                            tint = Color(0xFF7E57C2),
                            value = if (avgSleep > 0) "${avgSleep / 60}h ${avgSleep % 60}m" else "—",
                            label = "avg sleep"
                        )
                        SummaryStatCol(
                            icon = Icons.Default.Favorite,
                            tint = Color(0xFFEF5350),
                            value = if (avgHR > 0) "$avgHR bpm" else "—",
                            label = "avg HR"
                        )
                    }
                }
            }
        }

        // Per-day rows
        items(metrics, key = { it.date }) { day ->
            DayRow(day)
        }
    }
}

@Composable
private fun SummaryStatCol(icon: ImageVector, tint: Color, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
    }
}

@Composable
private fun DayRow(day: DailyHealthMetrics) {
    var expanded by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val inputFmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val displayDate = remember(day.date) {
        try { fmt.format(inputFmt.parse(day.date)!!) } catch (_: Exception) { day.date }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(displayDate, style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (day.hasSteps) DayChip(Icons.Default.DirectionsRun, "%,d".format(day.steps), Color(0xFF42A5F5))
                        if (day.hasSleep) DayChip(Icons.Default.Hotel, "${day.sleepHours}h ${day.sleepMins}m", Color(0xFF7E57C2))
                        if (day.hasHeart) DayChip(Icons.Default.Favorite, "${day.avgHeartRate} bpm", Color(0xFFEF5350))
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (day.hasSteps) {
                        DetailRow(Icons.Default.DirectionsRun, Color(0xFF42A5F5), "Steps",
                            "%,d steps".format(day.steps))
                    }
                    if (day.hasSleep) {
                        DetailRow(Icons.Default.Hotel, Color(0xFF7E57C2), "Sleep",
                            "${day.sleepHours}h ${day.sleepMins}m")
                    }
                    if (day.hasHeart) {
                        DetailRow(Icons.Default.Favorite, Color(0xFFEF5350), "Heart Rate",
                            "avg ${day.avgHeartRate} bpm  (${day.minHeartRate}–${day.maxHeartRate})")
                    }
                }
            }
        }
    }
}

@Composable
private fun DayChip(icon: ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun DetailRow(icon: ImageVector, tint: Color, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("$label: ", style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
        Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface)
    }
}
