package com.alex.a2ndbrain.ui.reflection

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import kotlinx.coroutines.launch
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.animation.core.*
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VolumeOff
import com.alex.a2ndbrain.core.reflection.TtsManager
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.health.HealthMetrics
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import kotlinx.coroutines.flow.collectLatest

@Composable
fun ReflectionScreen(
    summaries: List<DailySummaryEntity>,
    isGenerating: Boolean,
    onGenerateReflection: () -> Unit,
    onCancelReflection: () -> Unit,
    onClearAll: () -> Unit,
    onDeleteSummary: (Long) -> Unit,
    weeklyUsageStats: List<UsageStatEntity>,
    weeklyHealthTrends: List<Pair<String, HealthMetrics>>,
    isGeneratingWeeklyInsight: Boolean,
    onGenerateWeeklyInsight: () -> Unit,
    isGeneratingTomorrowForecast: Boolean = false,
    onGenerateTomorrowForecast: () -> Unit = {},
    isGeneratingCircadian: Boolean = false,
    onGenerateCircadianInsight: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val reflectionTabs = listOf("daily", "weekly", "rhythms")
    var activeTabIndex by remember { mutableIntStateOf(0) }
    val activeTab = reflectionTabs[activeTabIndex]
    val ttsManager = remember { TtsManager(context) }
    var speakingText by remember { mutableStateOf<String?>(null) }
    val isSpeaking by ttsManager.isSpeaking.collectAsState()

    DisposableEffect(ttsManager) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    // Tab row pinned outside any scrollable container — switching tabs replaces
    // the list entirely so item indices never shift and there's no shared recomposition scope.
    Column(modifier = modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = activeTabIndex,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp).clip(RoundedCornerShape(16.dp)),
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[activeTabIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            Tab(selected = activeTabIndex == 0, onClick = { activeTabIndex = 0 },
                text = { Text("Daily Briefings", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)) })
            Tab(selected = activeTabIndex == 1, onClick = { activeTabIndex = 1 },
                text = { Text("Weekly Cockpit", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)) })
            Tab(selected = activeTabIndex == 2, onClick = { activeTabIndex = 2 },
                text = { Text("Rhythms", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)) })
        }

        if (activeTab == "daily") {
            // ── Daily tab — independent LazyColumn ───────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                item(key = "daily_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (summaries.isNotEmpty()) {
                            TextButton(onClick = onClearAll, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                Text("Clear All")
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }
                        val reflectButtonWidth = (configuration.screenWidthDp * 0.25).coerceIn(90.0, 140.0).dp
                        if (isGenerating) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                Text("Thinking...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.width(4.dp))
                                TextButton(onClick = onCancelReflection, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                                    Text("Cancel")
                                }
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onGenerateReflection, modifier = Modifier.width(reflectButtonWidth), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(0.dp)) {
                                    Text("Reflect")
                                }
                                if (isGeneratingTomorrowForecast) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Text("Forecasting…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                } else {
                                    OutlinedButton(onClick = onGenerateTomorrowForecast, shape = RoundedCornerShape(12.dp)) {
                                        Text("Tomorrow")
                                    }
                                }
                            }
                        }
                    }
                }
                if (summaries.isEmpty()) {
                    item(key = "daily_empty") {
                        Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                            Text("Your daily synthesis will appear here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                        }
                    }
                } else {
                    items(summaries, key = { it.id }) { summary ->
                        SummaryCard(summary, onDelete = { onDeleteSummary(summary.id) })
                    }
                }
            }
        } else if (activeTab == "weekly") {
            // ── Weekly tab — independent LazyColumn ──────────────────────
            // Compute screen-time breakdown once; recomputes only when stats change.
            val weeklyReport = remember(summaries) { summaries.find { it.type == "weekly_correlation" } }
            val screenTimeSplit = remember(weeklyUsageStats) {
                val focusKw = listOf("gmail", "email", "slack", "teams", "doc", "sheet", "android", "github", "zoom", "meet", "drive")
                val socialKw = listOf("youtube", "tiktok", "instagram", "facebook", "twitter", "reddit", "social", "game")
                var fMs = 0L; var sMs = 0L; var oMs = 0L
                weeklyUsageStats.forEach { stat ->
                    val n = stat.packageName.lowercase()
                    when {
                        focusKw.any { n.contains(it) } -> fMs += stat.totalTimeVisibleMs
                        socialKw.any { n.contains(it) } -> sMs += stat.totalTimeVisibleMs
                        else -> oMs += stat.totalTimeVisibleMs
                    }
                }
                val total = (fMs + sMs + oMs).coerceAtLeast(1L)
                Triple(fMs, sMs, oMs) to Triple(fMs.toFloat() / total, sMs.toFloat() / total, oMs.toFloat() / total)
            }
            val (rawMs, pcts) = screenTimeSplit
            val (focusMs, socialMs, otherMs) = rawMs
            val (focusPct, socialPct, otherPct) = pcts

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                // Weekly AI Correlation Card
                item(key = "weekly_ai_card") {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Weekly AI Correlation Briefing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("Multi-Day Cognitive & Physical Analytics", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                                if (weeklyReport != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val currentPlaying = speakingText == weeklyReport.summary && isSpeaking
                                        if (currentPlaying) SpeakingSoundwave(color = MaterialTheme.colorScheme.primary)
                                        IconButton(onClick = {
                                            if (currentPlaying) { ttsManager.stop(); speakingText = null }
                                            else { speakingText = weeklyReport.summary; ttsManager.speak(weeklyReport.summary, "weekly_correlation") }
                                        }) {
                                            Icon(if (currentPlaying) Icons.Default.VolumeOff else Icons.Default.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            if (weeklyReport != null) {
                                Text(text = weeklyReport.summary, style = MaterialTheme.typography.bodyLarge, lineHeight = 24.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("Analyze steps, sleep hours, routines checklist compliance, and digital screentime ratios from the past 7 days to trigger a smart AI correlation advisory card!", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = onGenerateWeeklyInsight,
                                enabled = !isGeneratingWeeklyInsight,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isGeneratingWeeklyInsight) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = androidx.compose.ui.graphics.Color.White)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Analyzing Weekly Trends...")
                                } else {
                                    Text(if (weeklyReport != null) "Regenerate Weekly Insight" else "Generate Weekly Insight")
                                }
                            }
                        }
                    }
                }

                // Steps Trend Card
                item(key = "weekly_steps") {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Weekly Physical Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            if (weeklyHealthTrends.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text("Enable Health Connect to track weekly steps", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                }
                            } else {
                                val maxSteps = (weeklyHealthTrends.maxOfOrNull { it.second.steps } ?: 0L).coerceAtLeast(10000L)
                                val midSteps = maxSteps / 2
                                val stepsLabel: (Long) -> String = { v -> if (v >= 1000) "${v / 1000}k" else "$v" }
                                Row(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                    Column(modifier = Modifier.width(28.dp).fillMaxHeight().padding(bottom = 2.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                                        Text(stepsLabel(maxSteps), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                                        Text(stepsLabel(midSteps), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                                        Text("0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    val barBrush = remember { Brush.verticalGradient(listOf(Color(0xFF80C2FF), Color(0xFF3399FF))) }
                                    Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        val barCount = weeklyHealthTrends.size
                                        if (barCount > 0) {
                                            val barWidth = (size.width / barCount) * 0.6f
                                            val spacing = (size.width / barCount) * 0.4f
                                            weeklyHealthTrends.forEachIndexed { index, pair ->
                                                val x = (index * (barWidth + spacing)) + (spacing / 2)
                                                val barH = (pair.second.steps.toFloat() / maxSteps.toFloat()) * size.height
                                                drawRoundRect(brush = barBrush, topLeft = androidx.compose.ui.geometry.Offset(x, size.height - barH), size = androidx.compose.ui.geometry.Size(barWidth, barH), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f))
                                            }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    weeklyHealthTrends.forEach { pair ->
                                        val label = try { SimpleDateFormat("E", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(pair.first)!!).take(1).uppercase() } catch (e: Exception) { pair.first.take(1) }
                                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }

                // Sleep Patterns Card
                item(key = "weekly_sleep") {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Weekly Sleep Patterns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            if (weeklyHealthTrends.isEmpty()) {
                                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                    Text("Enable Health Connect to track weekly sleep duration", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                                }
                            } else {
                                val maxSleepMins = (weeklyHealthTrends.maxOfOrNull { it.second.sleepMinutes } ?: 0).coerceAtLeast(480)
                                val midSleepH = maxSleepMins / 2 / 60
                                val maxSleepH = maxSleepMins / 60
                                Row(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                    Column(modifier = Modifier.width(28.dp).fillMaxHeight().padding(bottom = 2.dp), verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End) {
                                        Text("${maxSleepH}h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                                        Text("${midSleepH}h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                                        Text("0h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline, fontSize = 9.sp)
                                    }
                                    Spacer(Modifier.width(4.dp))
                                    Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                        val pointCount = weeklyHealthTrends.size
                                        if (pointCount > 1) {
                                            val stepX = size.width / (pointCount - 1)
                                            val points = weeklyHealthTrends.mapIndexed { index, pair ->
                                                androidx.compose.ui.geometry.Offset(index * stepX, size.height - ((pair.second.sleepMinutes.toFloat() / maxSleepMins.toFloat()) * size.height))
                                            }
                                            val path = Path().apply { moveTo(points[0].x, points[0].y); for (i in 1 until points.size) lineTo(points[i].x, points[i].y) }
                                            drawPath(path = path, color = Color(0xFF81C784), style = Stroke(width = 4.dp.toPx()))
                                            points.forEach { drawCircle(color = Color(0xFF4CAF50), radius = 6.dp.toPx(), center = it) }
                                        }
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                Row(modifier = Modifier.fillMaxWidth().padding(start = 32.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    weeklyHealthTrends.forEach { pair ->
                                        val label = try { SimpleDateFormat("E", Locale.getDefault()).format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(pair.first)!!).take(1).uppercase() } catch (e: Exception) { pair.first.take(1) }
                                        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                    }
                                }
                            }
                        }
                    }
                }

                // Screen Time Card — breakdown pre-computed via remember()
                item(key = "weekly_screentime") {
                    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text("Weekly Focus vs. Social Split", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (focusPct > 0) Box(modifier = Modifier.weight(focusPct.coerceAtLeast(0.02f)).height(20.dp).clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp)).background(Color(0xFF3F51B5)))
                                if (otherPct > 0)  Box(modifier = Modifier.weight(otherPct.coerceAtLeast(0.02f)).height(20.dp).background(Color(0xFFE0E0E0)))
                                if (socialPct > 0) Box(modifier = Modifier.weight(socialPct.coerceAtLeast(0.02f)).height(20.dp).clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp)).background(Color(0xFFFF4081)))
                            }
                            Spacer(Modifier.height(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFF3F51B5)))
                                    Text("Focus Work: ${focusMs / 1000 / 60} mins (${(focusPct * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF4081)))
                                    Text("Social & Games: ${socialMs / 1000 / 60} mins (${(socialPct * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(10.dp).background(Color(0xFFE0E0E0)))
                                    Text("General Utility: ${otherMs / 1000 / 60} mins (${(otherPct * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // ── Rhythms tab — circadian pattern analysis ──────────────────
            val circadianReports = remember(summaries) {
                summaries.filter { it.type == "circadian_pattern" }.sortedByDescending { it.timestamp }
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp)
            ) {
                item(key = "rhythms_card") {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                "Circadian Pattern Analysis",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "28-day activity rhythm across habits, notifications & exercise",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(16.dp))
                            if (circadianReports.isNotEmpty()) {
                                Text(
                                    circadianReports.first().summary,
                                    style = MaterialTheme.typography.bodyLarge,
                                    lineHeight = 24.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "Last analyzed: ${SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(circadianReports.first().timestamp))}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            } else {
                                Text(
                                    "Analyze 28 days of your habits, notifications, and exercise timestamps to discover your natural energy peaks and focus windows.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Spacer(Modifier.height(20.dp))
                            Button(
                                onClick = onGenerateCircadianInsight,
                                enabled = !isGeneratingCircadian,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isGeneratingCircadian) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = androidx.compose.ui.graphics.Color.White
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("Mapping Your Rhythms…")
                                } else {
                                    Text(if (circadianReports.isNotEmpty()) "Refresh Analysis" else "Analyze My Rhythms")
                                }
                            }
                        }
                    }
                }

                if (circadianReports.size > 1) {
                    item(key = "rhythms_history_header") {
                        Text(
                            "HISTORY",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        )
                    }
                    items(circadianReports.drop(1), key = { it.id }) { report ->
                        SummaryCard(report, onDelete = { onDeleteSummary(report.id) })
                    }
                }
            }
        }
    }
}

@Composable
fun SpeakingSoundwave(color: Color, modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(600, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "h1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.5f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(animation = tween(450, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "h2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 1.2f,
        animationSpec = infiniteRepeatable(animation = tween(700, easing = LinearEasing), repeatMode = RepeatMode.Reverse), label = "h3"
    )
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(width = 3.dp, height = (20.dp * scale1)).clip(RoundedCornerShape(2.dp)).background(color))
        Box(modifier = Modifier.size(width = 3.dp, height = (20.dp * scale2)).clip(RoundedCornerShape(2.dp)).background(color))
        Box(modifier = Modifier.size(width = 3.dp, height = (20.dp * scale3)).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
private fun SummaryCard(summary: DailySummaryEntity, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (summary.type == "briefing") PastelGreen else PastelBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = if (summary.type == "briefing") PastelGreenText else PastelBlueText,
                    modifier = Modifier.size(20.dp)
                )
            }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = if (summary.type == "briefing") "Morning Briefing" else "Evening Reflection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(Date(summary.timestamp)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        summary.modelName?.let { name ->
                            Text(
                                text = name.removePrefix("gemini-"),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = summary.summary,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = 24.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
