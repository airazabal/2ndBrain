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
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTab by remember { mutableStateOf("daily") }
    val ttsManager = remember { TtsManager(context) }
    var speakingText by remember { mutableStateOf<String?>(null) }
    val isSpeaking by ttsManager.isSpeaking.collectAsState()

    DisposableEffect(ttsManager) {
        onDispose {
            ttsManager.shutdown()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            TabRow(
                selectedTabIndex = if (activeTab == "daily") 0 else 1,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)),
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                contentColor = MaterialTheme.colorScheme.primary,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[if (activeTab == "daily") 0 else 1]),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            ) {
                Tab(
                    selected = activeTab == "daily",
                    onClick = { activeTab = "daily" },
                    text = { Text("Daily Briefings", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)) }
                )
                Tab(
                    selected = activeTab == "weekly",
                    onClick = { activeTab = "weekly" },
                    text = { Text("Weekly Cockpit", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 12.dp)) }
                )
            }
        }

        if (activeTab == "daily") {
            item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (summaries.isNotEmpty()) {
                    TextButton(
                        onClick = onClearAll,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Clear All")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                val reflectButtonWidth = (configuration.screenWidthDp * 0.25).coerceIn(90.0, 140.0).dp
                
                if (isGenerating) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                        Text(
                            "Thinking...", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = onCancelReflection,
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Cancel")
                        }
                    }
                } else {
                    Button(
                        onClick = onGenerateReflection,
                        modifier = Modifier.width(reflectButtonWidth),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("Reflect")
                    }
                }
            }
        }


        if (summaries.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Your daily synthesis will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        } else {
            items(summaries) { summary ->
                SummaryCard(summary, onDelete = { onDeleteSummary(summary.id) })
            }
        }
        
        } else {
            // ITEMS FOR THE WEEKLY COCKPIT TAB
            val weeklyReport = summaries.find { it.type == "weekly_correlation" }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Weekly AI Correlation Briefing",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Multi-Day Cognitive & Physical Analytics",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }

                            if (weeklyReport != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val currentPlaying = speakingText == weeklyReport.summary && isSpeaking
                                    if (currentPlaying) {
                                        SpeakingSoundwave(color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(
                                        onClick = {
                                            if (currentPlaying) {
                                                ttsManager.stop()
                                                speakingText = null
                                            } else {
                                                speakingText = weeklyReport.summary
                                                ttsManager.speak(weeklyReport.summary, "weekly_correlation")
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (currentPlaying) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                            contentDescription = if (currentPlaying) "Stop" else "Speak",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (weeklyReport != null) {
                            Text(
                                text = weeklyReport.summary,
                                style = MaterialTheme.typography.bodyLarge,
                                lineHeight = 24.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Analyze steps, sleep hours, routines checklist compliance, and digital screentime ratios from the past 7 days to trigger a smart AI correlation advisory card!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        if (isGeneratingWeeklyInsight) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyzing Weekly Trends...", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            Button(
                                onClick = onGenerateWeeklyInsight,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (weeklyReport != null) "Regenerate Weekly Insight" else "Generate Weekly Insight")
                            }
                        }
                    }
                }
            }

            // Steps Trend Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Weekly Physical Steps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (weeklyHealthTrends.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text("Enable Health Connect to track weekly steps", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                val maxSteps = (weeklyHealthTrends.maxOfOrNull { it.second.steps } ?: 0L).coerceAtLeast(10000L).toFloat()
                                val width = size.width
                                val height = size.height
                                val barCount = weeklyHealthTrends.size
                                if (barCount > 0) {
                                    val barWidth = (width / barCount) * 0.6f
                                    val spacing = (width / barCount) * 0.4f
                                    weeklyHealthTrends.forEachIndexed { index, pair ->
                                        val x = (index * (barWidth + spacing)) + (spacing / 2)
                                        val barHeight = (pair.second.steps.toFloat() / maxSteps) * height
                                        val y = height - barHeight
                                        drawRoundRect(
                                            brush = Brush.verticalGradient(listOf(Color(0xFF80C2FF), Color(0xFF3399FF))),
                                            topLeft = androidx.compose.ui.geometry.Offset(x, y),
                                            size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                weeklyHealthTrends.forEach { pair ->
                                    val label = try {
                                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(pair.first)
                                        SimpleDateFormat("E", Locale.getDefault()).format(date!!).take(1).uppercase()
                                    } catch (e: Exception) {
                                        pair.first.take(1)
                                    }
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }

            // Sleep Patterns Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Weekly Sleep Patterns", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        if (weeklyHealthTrends.isEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                                Text("Enable Health Connect to track weekly sleep duration", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                            }
                        } else {
                            Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                                val maxSleep = (weeklyHealthTrends.maxOfOrNull { it.second.sleepMinutes } ?: 0).coerceAtLeast(480).toFloat()
                                val width = size.width
                                val height = size.height
                                val pointCount = weeklyHealthTrends.size
                                if (pointCount > 1) {
                                    val stepX = width / (pointCount - 1)
                                    val points = weeklyHealthTrends.mapIndexed { index, pair ->
                                        val x = index * stepX
                                        val y = height - ((pair.second.sleepMinutes.toFloat() / maxSleep) * height)
                                        androidx.compose.ui.geometry.Offset(x, y)
                                    }
                                    val path = Path()
                                    path.moveTo(points[0].x, points[0].y)
                                    for (i in 1 until points.size) {
                                        path.lineTo(points[i].x, points[i].y)
                                    }
                                    drawPath(
                                        path = path,
                                        color = Color(0xFF81C784),
                                        style = Stroke(width = 4.dp.toPx())
                                    )
                                    points.forEach { point ->
                                        drawCircle(
                                            color = Color(0xFF4CAF50),
                                            radius = 6.dp.toPx(),
                                            center = point
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                weeklyHealthTrends.forEach { pair ->
                                    val label = try {
                                        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(pair.first)
                                        SimpleDateFormat("E", Locale.getDefault()).format(date!!).take(1).uppercase()
                                    } catch (e: Exception) {
                                        pair.first.take(1)
                                    }
                                    Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                }
                            }
                        }
                    }
                }
            }


            // Screen time Card
            item {
                val focusKeywords = listOf("gmail", "email", "slack", "teams", "doc", "sheet", "android", "github", "zoom", "meet", "drive")
                val socialKeywords = listOf("youtube", "tiktok", "instagram", "facebook", "twitter", "reddit", "social", "game")

                var focusMs = 0L
                var socialMs = 0L
                var otherMs = 0L
                
                weeklyUsageStats.forEach { stat ->
                    val name = stat.packageName.lowercase()
                    when {
                        focusKeywords.any { name.contains(it) } -> focusMs += stat.totalTimeVisibleMs
                        socialKeywords.any { name.contains(it) } -> socialMs += stat.totalTimeVisibleMs
                        else -> otherMs += stat.totalTimeVisibleMs
                    }
                }
                
                val totalMs = (focusMs + socialMs + otherMs).coerceAtLeast(1L)
                val focusPct = focusMs.toFloat() / totalMs
                val socialPct = socialMs.toFloat() / totalMs
                val otherPct = otherMs.toFloat() / totalMs

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("Weekly Focus vs. Social Split", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (focusPct > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(focusPct.coerceAtLeast(0.02f))
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(topStart = 10.dp, bottomStart = 10.dp))
                                        .background(Color(0xFF3F51B5))
                                )
                            }
                            if (otherPct > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(otherPct.coerceAtLeast(0.02f))
                                        .height(20.dp)
                                        .background(Color(0xFFE0E0E0))
                                )
                            }
                            if (socialPct > 0) {
                                Box(
                                    modifier = Modifier
                                        .weight(socialPct.coerceAtLeast(0.02f))
                                        .height(20.dp)
                                        .clip(RoundedCornerShape(topEnd = 10.dp, bottomEnd = 10.dp))
                                        .background(Color(0xFFFF4081))
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).background(Color(0xFF3F51B5)))
                                Text("Focus Work: ${(focusMs / 1000 / 60)} mins (${(focusPct * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).background(Color(0xFFFF4081)))
                                Text("Social & Games: ${(socialMs / 1000 / 60)} mins (${(socialPct * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(modifier = Modifier.size(10.dp).background(Color(0xFFE0E0E0)))
                                Text("General Utility: ${(otherMs / 1000 / 60)} mins (${(otherPct * 100).toInt()}%)", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
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
