package com.alex.a2ndbrain.ui.trends

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewWeek
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alex.a2ndbrain.core.senseofday.CorrelationData
import com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshot
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

private val pillarColors = listOf(
    Color(0xFF1E88E5),
    Color(0xFF7E57C2),
    Color(0xFF43A047),
    Color(0xFFFF9800),
    Color(0xFFEC407A)
)
private val pillarLabels = listOf("Steps", "Sleep", "Exercise", "Focus", "Mood")

private fun scoreColor(score: Float): Color {
    val hue = (score / 100f * 120f).coerceIn(0f, 120f)
    return Color.hsv(hue, 0.72f, 0.88f)
}

@Composable
fun SenseOfDayTrendsScreen(
    uiState: TrendsUiState,
    modifier: Modifier = Modifier
) {
    if (uiState.last14Days.isEmpty() && uiState.weeklyAverages.all { it.second == 0f }) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "No history yet — check back after your first full day.",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontSize = 14.sp,
                modifier = Modifier.padding(32.dp)
            )
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { StatsRow(uiState.todayScore, uiState.weekAvg, uiState.monthAvg) }
        item { DailyBarsCard(uiState.last14Days) }
        item { WeeklyBarsCard(uiState.weeklyAverages) }
        item {
            PillarAveragesCard(
                stepsProgress = uiState.avgStepsProgress,
                sleepProgress = uiState.avgSleepProgress,
                exerciseProgress = uiState.avgExerciseProgress,
                focusProgress = uiState.avgFocusProgress,
                moodProgress = uiState.avgMoodProgress
            )
        }
        if (uiState.correlations.isNotEmpty()) {
            item { CorrelationsCard(uiState.correlations) }
        }
    }
}

// ─── Stats row ───────────────────────────────────────────────────────────────

@Composable
private fun StatsRow(todayScore: Int, weekAvg: Int, monthAvg: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScoreStatCard("Today", todayScore, Icons.Default.Today, Modifier.weight(1f))
        ScoreStatCard("7-Day Avg", weekAvg, Icons.Default.CalendarViewWeek, Modifier.weight(1f))
        ScoreStatCard("30-Day Avg", monthAvg, Icons.Default.CalendarMonth, Modifier.weight(1f))
    }
}

@Composable
private fun ScoreStatCard(title: String, score: Int, icon: ImageVector, modifier: Modifier) {
    val color = scoreColor(score.toFloat())
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (score == 0) "--" else "$score",
                fontSize = 20.sp,
                fontWeight = FontWeight.Black,
                color = color
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

// ─── 14-day daily bar chart ──────────────────────────────────────────────────

@Composable
private fun DailyBarsCard(snapshots: List<SenseOfDaySnapshot>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SectionLabel("LAST 14 DAYS")
            Spacer(Modifier.height(10.dp))

            // Fill in missing days so we always show 14 slots
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val dayLabelSdf = SimpleDateFormat("d", Locale.getDefault())
            val snapshotMap = snapshots.associateBy { it.date }
            val slots = (13 downTo 0).map { offset ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -offset)
                val dateStr = sdf.format(cal.time)
                val dayLabel = dayLabelSdf.format(cal.time)
                dateStr to (snapshotMap[dateStr]?.score ?: -1)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                slots.forEach { (_, score) ->
                    val ratio = if (score >= 0) score / 100f else 0f
                    val color = if (score >= 0) scoreColor(score.toFloat())
                                else Color.Gray.copy(alpha = 0.25f)
                    val animRatio by animateFloatAsState(ratio, tween(600), label = "bar")

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (score > 0) {
                            Text(
                                text = score.toString(),
                                fontSize = 8.sp,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Box(
                            modifier = Modifier.width(16.dp).height(64.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            )
                            if (animRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animRatio)
                                        .background(color, RoundedCornerShape(4.dp))
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            // Month markers below bars
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                val shortMonthSdf = SimpleDateFormat("MMM d", Locale.getDefault())
                val labelSdf = SimpleDateFormat("d", Locale.getDefault())
                val monthMarkSdf = SimpleDateFormat("MMM", Locale.getDefault())
                slots.forEachIndexed { i, (dateStr, _) ->
                    val cal = Calendar.getInstance()
                    cal.time = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(dateStr)!!
                    val isFirst = i == 0
                    val isFirstOfMonth = cal.get(Calendar.DAY_OF_MONTH) == 1
                    val text = when {
                        isFirst || isFirstOfMonth -> monthMarkSdf.format(cal.time)
                        i % 7 == 0 -> labelSdf.format(cal.time)
                        else -> ""
                    }
                    Text(
                        text = text,
                        fontSize = 8.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                        maxLines = 1,
                        overflow = TextOverflow.Visible,
                        modifier = Modifier.width(16.dp)
                    )
                }
            }
        }
    }
}

// ─── 8-week weekly averages bar chart ───────────────────────────────────────

@Composable
private fun WeeklyBarsCard(weeklyAverages: List<Pair<String, Float>>) {
    if (weeklyAverages.isEmpty()) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SectionLabel("WEEKLY AVERAGES")
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                weeklyAverages.forEach { (label, avg) ->
                    val ratio = avg / 100f
                    val color = if (avg > 0f) scoreColor(avg) else Color.Gray.copy(alpha = 0.25f)
                    val animRatio by animateFloatAsState(ratio, tween(600), label = "wbar")
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        if (avg > 0f) {
                            Text(
                                text = avg.toInt().toString(),
                                fontSize = 8.sp,
                                color = color,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Box(
                            modifier = Modifier.width(24.dp).height(64.dp),
                            contentAlignment = Alignment.BottomCenter
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                                    .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                            )
                            if (animRatio > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(animRatio)
                                        .background(color, RoundedCornerShape(4.dp))
                                )
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            label,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

// ─── Pillar averages (14-day) ─────────────────────────────────────────────────

@Composable
private fun PillarAveragesCard(
    stepsProgress: Float,
    sleepProgress: Float,
    exerciseProgress: Float,
    focusProgress: Float,
    moodProgress: Float
) {
    val progresses = listOf(stepsProgress, sleepProgress, exerciseProgress, focusProgress, moodProgress)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SectionLabel("14-DAY PILLAR AVERAGES")
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                pillarLabels.forEachIndexed { i, label ->
                    PillarRing(
                        label = label,
                        progress = progresses.getOrElse(i) { 0f },
                        color = pillarColors.getOrElse(i) { Color.Gray }
                    )
                }
            }
        }
    }
}

@Composable
private fun PillarRing(label: String, progress: Float, color: Color) {
    val animProg by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring_$label")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(60.dp)) {
            Canvas(modifier = Modifier.size(60.dp)) {
                val strokePx = 7.dp.toPx()
                val inset = strokePx / 2f
                val arcSize = Size(size.width - strokePx, size.height - strokePx)
                val topLeft = Offset(inset, inset)
                drawArc(
                    color = color.copy(alpha = 0.18f),
                    startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
                if (animProg > 0f) {
                    drawArc(
                        color = color,
                        startAngle = -90f, sweepAngle = animProg * 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 10.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
    }
}

// ─── Correlation overlay chart ────────────────────────────────────────────────

private val scoreLineColor = Color(0xFF607D8B)

@Composable
private fun CorrelationsCard(correlations: List<CorrelationData>) {
    var selectedIdx by remember { mutableStateOf(0) }
    val pair = correlations.getOrElse(selectedIdx) { correlations[0] }
    val colorA = if (pair.aPillarIdx < 0) scoreLineColor else pillarColors.getOrElse(pair.aPillarIdx) { Color.Gray }
    val colorB = if (pair.bPillarIdx < 0) scoreLineColor else pillarColors.getOrElse(pair.bPillarIdx) { Color.Gray }
    val rAbs = abs(pair.r)
    val rColor = when {
        rAbs >= 0.65f -> Color(0xFF4CAF50)
        rAbs >= 0.35f -> Color(0xFFFF9800)
        else          -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            SectionLabel("PILLAR CORRELATIONS")
            Spacer(Modifier.height(10.dp))

            // Pair selector chips
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(correlations.size) { i ->
                    FilterChip(
                        selected = i == selectedIdx,
                        onClick = { selectedIdx = i },
                        label = { Text(correlations[i].chipLabel, fontSize = 10.sp) }
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = pair.chartTitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(Modifier.height(8.dp))

            // Overlay line chart
            CorrelationLineChart(pair.aValues, colorA, pair.bValues, colorB)

            Spacer(Modifier.height(10.dp))

            // Legend row + r value
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CorrelationLegendDot(pair.aLabel, colorA)
                CorrelationLegendDot(pair.bLabel, colorB)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "r = ${"%.2f".format(pair.r)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = rColor
                )
            }

            Spacer(Modifier.height(6.dp))
            Text(
                text = pair.insight,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun CorrelationLineChart(
    valuesA: List<Float>,
    colorA: Color,
    valuesB: List<Float>,
    colorB: Color
) {
    val n = minOf(valuesA.size, valuesB.size)
    if (n < 2) return
    Canvas(modifier = Modifier.fillMaxWidth().height(90.dp)) {
        val xStep = if (n > 1) size.width / (n - 1) else size.width
        val strokePx = 2.dp.toPx()
        val dotRadius = 3.dp.toPx()

        listOf(valuesA to colorA, valuesB to colorB).forEach { (vals, color) ->
            for (i in 0 until n - 1) {
                val v1 = vals.getOrElse(i) { 0f }.coerceIn(0f, 1f)
                val v2 = vals.getOrElse(i + 1) { 0f }.coerceIn(0f, 1f)
                drawLine(
                    color = color.copy(alpha = 0.85f),
                    start = Offset(i * xStep, (1f - v1) * size.height),
                    end   = Offset((i + 1) * xStep, (1f - v2) * size.height),
                    strokeWidth = strokePx,
                    cap = StrokeCap.Round
                )
            }
            for (i in 0 until n) {
                val v = vals.getOrElse(i) { 0f }.coerceIn(0f, 1f)
                drawCircle(color = color, radius = dotRadius, center = Offset(i * xStep, (1f - v) * size.height))
            }
        }
    }
}

@Composable
private fun CorrelationLegendDot(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Canvas(modifier = Modifier.size(8.dp)) { drawCircle(color) }
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    )
}
