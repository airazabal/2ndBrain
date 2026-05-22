package com.alex.a2ndbrain.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.alex.a2ndbrain.AppTab
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.memory.HabitEntity
import com.alex.a2ndbrain.ui.theme.*
import com.alex.a2ndbrain.ConsolidatedUsage
import com.alex.a2ndbrain.ui.usage.UsageBarChart
import com.alex.a2ndbrain.TimelineEvent
import com.alex.a2ndbrain.core.reflection.TtsManager
import com.alex.a2ndbrain.TimelineConflict
import com.alex.a2ndbrain.ConflictType
import com.alex.a2ndbrain.ConflictSeverity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.saveable.rememberSaveable
import com.alex.a2ndbrain.core.capture.HomeSummaryConfig
import com.alex.a2ndbrain.core.capture.HomeDefaultMode
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Spa
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Date



@Composable
fun HomeScreen(
    memories: List<MemoryEntity>,
    latestReflection: DailySummaryEntity?,
    notes: List<DocumentFile>,
    consolidatedUsage: List<ConsolidatedUsage>,
    onNavigateToTab: (AppTab) -> Unit,
    healthMetrics: com.alex.a2ndbrain.core.health.HealthMetrics = com.alex.a2ndbrain.core.health.HealthMetrics(),
    healthPermissionGranted: Boolean = false,
    healthConnectAvailable: Boolean = false,
    onConnectHealth: () -> Unit = {},
    meditationSessions: List<com.alex.a2ndbrain.core.meditation.MeditationSession> = emptyList(),
    meditationStreaks: com.alex.a2ndbrain.core.meditation.StreakResult = com.alex.a2ndbrain.core.meditation.StreakResult(0, 0, 0),
    
    // Dynamic Habits (Phase 2)
    activeHabits: List<HabitEntity> = emptyList(),
    completedHabitIds: Set<String> = emptySet(),
    onToggleHabit: (String) -> Unit = {},
    pastWeekHabitCompletions: List<Pair<String, Float>> = emptyList(),

    senseOfDayScore: Int = 75,
    senseOfDayContext: String = "🎯 Calibrating your day...",
    todayTimelineEvents: List<TimelineEvent> = emptyList(),
    tomorrowTimelineEvents: List<TimelineEvent> = emptyList(),
    timelineConflicts: List<TimelineConflict> = emptyList(),
    inlineCopilotResponses: Map<String, String> = emptyMap(),
    inlineCopilotLoading: Set<String> = emptySet(),
    onDismissConflict: (String) -> Unit = {},
    onDeepDiveCoPilotPrompt: (String) -> Unit = {},
    onResolveInline: (String, String) -> Unit = { _, _ -> },
    onAddManualEvent: (String, String) -> Unit = { _, _ -> },
    onDeepDiveCoPilot: (TimelineEvent) -> Unit = {},
    onDeleteManualEvent: (String) -> Unit = {},
    onRefreshHealth: () -> Unit = {},
    homeSummaryConfig: HomeSummaryConfig = HomeSummaryConfig(),
    lastDetailsExpanded: Boolean = false,
    onSaveDetailsExpanded: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsManager = remember { TtsManager(context) }
    val isSpeaking by ttsManager.isSpeaking.collectAsState()

    var showQuickAddDialog by remember { mutableStateOf(false) }
    var isTimelineExpanded by remember { mutableStateOf(true) }
    var expandedTimelineEventId by remember { mutableStateOf<String?>(null) }

    val initialExpanded = remember(homeSummaryConfig.defaultMode) {
        when (homeSummaryConfig.defaultMode) {
            HomeDefaultMode.ALWAYS_EXPANDED -> true
            HomeDefaultMode.SUMMARY_ONLY    -> false
            HomeDefaultMode.REMEMBER_LAST   -> lastDetailsExpanded
        }
    }
    var showDetails by rememberSaveable { mutableStateOf(initialExpanded) }
    LaunchedEffect(homeSummaryConfig.defaultMode) {
        when (homeSummaryConfig.defaultMode) {
            HomeDefaultMode.ALWAYS_EXPANDED -> showDetails = true
            HomeDefaultMode.SUMMARY_ONLY    -> showDetails = false
            HomeDefaultMode.REMEMBER_LAST   -> showDetails = lastDetailsExpanded
        }
    }

    LaunchedEffect(Unit) {
        onRefreshHealth()
    }
    
    DisposableEffect(Unit) {
        onDispose {
            ttsManager.stop()
        }
    }

    val unreadCount = remember(memories) { memories.count { !it.isRead } }
    val appCount = remember(memories) {
        memories.filter { it.source == "notification" }
            .mapNotNull { it.packageName }
            .distinct().size
    }



    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Smart summary card — always visible
        item {
            val currentMins = java.util.Calendar.getInstance().let {
                it.get(java.util.Calendar.HOUR_OF_DAY) * 60 + it.get(java.util.Calendar.MINUTE)
            }
            val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
            SmartSummaryCard(
                senseOfDayScore      = senseOfDayScore,
                senseOfDayContext    = senseOfDayContext,
                config               = homeSummaryConfig,
                topConflict          = timelineConflicts.maxByOrNull { if (it.severity == ConflictSeverity.ALERT) 1 else 0 },
                completedHabitCount  = completedHabitIds.size,
                totalHabitCount      = activeHabits.count { it.isActive },
                nextEvent            = todayTimelineEvents.firstOrNull { it.minutesFromMidnight >= currentMins }
                                        ?: todayTimelineEvents.firstOrNull(),
                healthMetrics        = healthMetrics,
                healthAvailable      = healthPermissionGranted,
                meditatedToday       = meditationSessions.any {
                    java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                        .format(java.util.Date(it.timestamp)) == todayStr
                },
                meditationWeekStreak = meditationStreaks.currentWeekStreak,
                showDetails          = showDetails,
                onToggleDetails      = {
                    val next = !showDetails
                    showDetails = next
                    if (homeSummaryConfig.defaultMode == HomeDefaultMode.REMEMBER_LAST) {
                        onSaveDetailsExpanded(next)
                    }
                },
                onDeepDiveConflict   = onDeepDiveCoPilotPrompt
            )
        }

        // All detail sections — hidden until the user taps "Show all details"
        item {
            AnimatedVisibility(
                visible = showDetails,
                enter = expandVertically(animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
                exit  = shrinkVertically(animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {

        // Daily Wellness & Habits Control Cockpit (Recommendation A & C)
        /* detail-section-start */
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onNavigateToTab(AppTab.SETTINGS) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(PastelBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ListAlt,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Daily Routine Cockpit",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Manage Habits",
                            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))

                    val ringColor = remember(senseOfDayScore) { Color.hsv(senseOfDayScore.toFloat() * 1.3f, 0.75f, 0.9f) }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(20.dp)
                    ) {
                        // Custom HSL Sense of Day Score Ring with radial glow
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .drawBehind {
                                    drawCircle(
                                        brush = Brush.radialGradient(
                                            colors = listOf(ringColor.copy(alpha = 0.22f), Color.Transparent),
                                            center = center,
                                            radius = size.width / 1.4f
                                        ),
                                        radius = size.width / 1.4f
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = Color.LightGray.copy(alpha = 0.2f),
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                                // Dynamic HSV color representation (Green/Cyan for balanced days)
                                val sweepAngle = (senseOfDayScore.toFloat() / 100f) * 360f
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "$senseOfDayScore%",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Sense of Day",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        // Habit checklist list
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            activeHabits.filter { it.isActive }.forEach { habit ->
                                val isCompleted = completedHabitIds.contains(habit.id)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onToggleHabit(habit.id) }
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val isPendingMedication = !isCompleted && habit.isMedication
                                    Icon(
                                        imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isCompleted) PastelGreen else if (isPendingMedication) Color.Red else MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        // Time on top
                                        Text(
                                            text = habit.timeString,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isPendingMedication) Color.Red else MaterialTheme.colorScheme.primary
                                        )
                                        
                                        Spacer(modifier = Modifier.height(2.dp))
                                        
                                        // Horizontal basicMarquee scrollable text widget under
                                        Text(
                                            text = habit.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(
                                                color = if (isPendingMedication) Color.Red else MaterialTheme.colorScheme.onSurface
                                            ),
                                            fontWeight = if (isCompleted) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .basicMarquee()
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Adaptive AI Alignment Context Box
                    Spacer(modifier = Modifier.height(12.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = ringColor.copy(alpha = 0.06f)
                        ),
                        border = BorderStroke(1.dp, ringColor.copy(alpha = 0.16f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "AI ALIGNMENT",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = ringColor,
                                modifier = Modifier
                                    .border(BorderStroke(1.dp, ringColor.copy(alpha = 0.35f)), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Text(
                                text = senseOfDayContext,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }


                    // Historical Wellness Streaks Dashboard (Phase 2, Step 3)
                    if (pastWeekHabitCompletions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.LightGray.copy(alpha = 0.2f)))
                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "Weekly Completion History",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.outline
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            pastWeekHabitCompletions.forEach { (dayLabel, completionRate) ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(36.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            drawCircle(
                                                color = Color.LightGray.copy(alpha = 0.15f),
                                                style = Stroke(width = 3.dp.toPx())
                                            )
                                            drawArc(
                                                color = if (completionRate >= 1.0f) PastelGreen else if (completionRate > 0f) PastelBlue else Color.LightGray.copy(alpha = 0.4f),
                                                startAngle = -90f,
                                                sweepAngle = completionRate * 360f,
                                                useCenter = false,
                                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                                            )
                                        }
                                        Text(
                                            text = "${(completionRate * 100).toInt()}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = dayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }  // end Column
    }  // end AnimatedVisibility
}  // end detail item

        if (showDetails && timelineConflicts.isNotEmpty()) {
            item {
                ActiveConflictsSection(
                    conflicts = timelineConflicts,
                    inlineCopilotResponses = inlineCopilotResponses,
                    inlineCopilotLoading = inlineCopilotLoading,
                    onDismissConflict = onDismissConflict,
                    onDeepDive = onDeepDiveCoPilotPrompt,
                    onResolveInline = onResolveInline
                )
            }
        }

        // Today's Calendar & Schedule Timeline (Recommendation B)
        if (showDetails) item {
            val chevronRotation by animateFloatAsState(
                targetValue = if (isTimelineExpanded) 180f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "timelineChevron"
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Clickable Header to Expand/Collapse
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isTimelineExpanded = !isTimelineExpanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PastelYellow),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Upcoming Schedule & Timeline",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            val totalEvents = todayTimelineEvents.size + tomorrowTimelineEvents.size
                            Text(
                                text = when {
                                    totalEvents == 0 -> "No events scheduled"
                                    tomorrowTimelineEvents.isEmpty() -> "${todayTimelineEvents.size} events today"
                                    todayTimelineEvents.isEmpty() -> "${tomorrowTimelineEvents.size} events tomorrow"
                                    else -> "${todayTimelineEvents.size} today · ${tomorrowTimelineEvents.size} tomorrow"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }

                        // Expand/Collapse Chevron
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expand or Collapse",
                            modifier = Modifier
                                .rotate(chevronRotation)
                                .size(24.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }

                    AnimatedVisibility(visible = isTimelineExpanded) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))

                            // Action Toolbar for Timeline
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 12.dp),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Speaking soundwaves
                                if (isSpeaking) {
                                    SpeakingSoundwave(
                                        isSpeaking = true,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }

                                // Refresh button
                                IconButton(
                                    onClick = { onRefreshHealth() },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = "Refresh Timeline",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))

                                // Voice Briefing button
                                IconButton(
                                    onClick = {
                                        if (isSpeaking) {
                                            ttsManager.stop()
                                        } else {
                                            val briefText = buildString {
                                                append("Here is your proactive schedule briefing. ")
                                                if (todayTimelineEvents.isEmpty()) {
                                                    append("You have no events scheduled for today.")
                                                } else {
                                                    append("You have ${todayTimelineEvents.size} events today. ")
                                                    todayTimelineEvents.forEach { event ->
                                                        append("At ${event.time}, ${event.title}. ")
                                                    }
                                                }
                                                if (tomorrowTimelineEvents.isNotEmpty()) {
                                                    append("Tomorrow you have ${tomorrowTimelineEvents.size} events. ")
                                                    tomorrowTimelineEvents.forEach { event ->
                                                        append("At ${event.time}, ${event.title}. ")
                                                    }
                                                }
                                                if (timelineConflicts.isNotEmpty()) {
                                                    append("However, I noticed ${timelineConflicts.size} proactive warnings. ")
                                                    timelineConflicts.forEach { conflict ->
                                                        append(conflict.description + " ")
                                                    }
                                                    append("Please address these when you have a moment.")
                                                }
                                            }
                                            ttsManager.speak(briefText, "timeline_briefing")
                                        }
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSpeaking) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                                        contentDescription = "Voice Briefing",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))

                                // Quick Add button
                                IconButton(
                                    onClick = { showQuickAddDialog = true },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Agenda Event",
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            if (todayTimelineEvents.isEmpty() && tomorrowTimelineEvents.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "No upcoming appointments found. Calendar events, routine habits, and parsed Obsidian logs will synchronize here automatically.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.fillMaxWidth(),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            } else {
                                // TODAY section
                                Text(
                                    text = "Today",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                if (todayTimelineEvents.isEmpty()) {
                                    Text(
                                        "No events captured yet for today.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                }
                            }
                            if (todayTimelineEvents.isNotEmpty()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState())
                                        .padding(vertical = 8.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    val infiniteTransition = rememberInfiniteTransition(label = "timePulse")
                                    val pulseScale by infiniteTransition.animateFloat(
                                        initialValue = 6f,
                                        targetValue = 12f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(1000, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "pulse"
                                    )

                                    todayTimelineEvents.forEachIndexed { index, event ->
                                        val conflict = timelineConflicts.find { it.relatedEventIds.contains(event.id) }
                                        val isConflicting = conflict != null
                                        
                                        // Colors mapping based on event category
                                        val (startColor, endColor, dotColor) = when {
                                            isConflicting ->
                                                Triple(Color(0xFFFFCDD2), Color(0xFFEF9A9A), Color(0xFFD32F2F))
                                            event.appName.contains("Calendar", ignoreCase = true) || event.sourcePackage == "calendar" -> 
                                                Triple(Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF1E88E5))
                                            event.appName.contains("Obsidian", ignoreCase = true) || event.sourcePackage == "obsidian" -> 
                                                Triple(Color(0xFFE0F2F1), Color(0xFFB2DFDB), Color(0xFF00897B))
                                            event.appName.contains("Routines", ignoreCase = true) || event.sourcePackage == "habit" -> 
                                                Triple(Color(0xFFFFF3E0), Color(0xFFFFE0B2), Color(0xFFF4511E))
                                            event.sourcePackage == "manual" -> 
                                                Triple(Color(0xFFF3E5F5), Color(0xFFE1BEE7), Color(0xFF8E24AA))
                                            else -> // Notification / Gmail / Messages / Slack etc.
                                                Triple(Color(0xFFF5F5F5), Color(0xFFE0E0E0), Color(0xFF9E9E9E))
                                        }
                                        
                                        val isExpanded = expandedTimelineEventId == event.id

                                        Column(
                                            modifier = Modifier
                                                .width(280.dp)
                                                .padding(vertical = 4.dp),
                                            horizontalAlignment = Alignment.Start
                                        ) {
                                            // Horizontal Connector Line & Node
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier.size(24.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                                        val isFirst = index == 0
                                                        val isLast = index == todayTimelineEvents.size - 1
                                                        
                                                        // Draw connection line
                                                        val startX = if (isFirst) size.width / 2 else 0f
                                                        val endX = if (isLast) size.width / 2 else size.width
                                                        drawLine(
                                                            color = Color.LightGray.copy(alpha = 0.5f),
                                                            start = androidx.compose.ui.geometry.Offset(startX, size.height / 2),
                                                            end = androidx.compose.ui.geometry.Offset(endX, size.height / 2),
                                                            strokeWidth = 3.dp.toPx()
                                                        )
                                                        
                                                        // Draw category point
                                                        drawCircle(
                                                            color = dotColor,
                                                            radius = 5.dp.toPx(),
                                                            center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                                                        )
                                                    }

                                                    // Pulsing active node
                                                    if ((event.sourcePackage == "habit" && !completedHabitIds.contains(event.id)) || isConflicting) {
                                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                                            drawCircle(
                                                                color = dotColor.copy(alpha = 0.3f),
                                                                radius = pulseScale.dp.toPx(),
                                                                center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
                                                            )
                                                        }
                                                    }
                                                }
                                                
                                                Spacer(modifier = Modifier.width(8.dp))
                                                
                                                Text(
                                                    text = event.time,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isConflicting) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Category HSL Card
                                            Card(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { expandedTimelineEventId = if (isExpanded) null else event.id },
                                                shape = RoundedCornerShape(16.dp),
                                                elevation = CardDefaults.cardElevation(defaultElevation = if (isExpanded || isConflicting) 4.dp else 2.dp),
                                                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                                                border = if (isConflicting) BorderStroke(1.5.dp, Color(0xFFD32F2F).copy(alpha = 0.6f)) else null
                                            ) {
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .background(
                                                            Brush.verticalGradient(listOf(startColor, endColor))
                                                        )
                                                        .padding(14.dp)
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = event.title,
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                color = Color.Black.copy(alpha = 0.8f)
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = if (isExpanded) event.description else event.description.take(45) + if (event.description.length > 45) "..." else "",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = Color.Black.copy(alpha = 0.6f)
                                                            )
                                                        }
                                                        
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        
                                                        // If manual insert, support deleting
                                                        if (event.sourcePackage == "manual") {
                                                            IconButton(
                                                                onClick = { onDeleteManualEvent(event.id) }
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Delete,
                                                                    contentDescription = "Delete Event",
                                                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                                    modifier = Modifier.size(20.dp)
                                                                )
                                                            }
                                                        } else {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(6.dp))
                                                                    .background(Color.White.copy(alpha = 0.4f))
                                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                                            ) {
                                                                Text(
                                                                    text = event.appName,
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = Color.Black.copy(alpha = 0.8f)
                                                                )
                                                            }
                                                        }
                                                    }
                                                    
                                                    AnimatedVisibility(visible = isExpanded) {
                                                        Column {
                                                            Spacer(modifier = Modifier.height(12.dp))
                                                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.Black.copy(alpha = 0.1f)))
                                                            Spacer(modifier = Modifier.height(8.dp))
                                                            
                                                            if (isConflicting && conflict != null) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD32F2F), modifier = Modifier.size(16.dp))
                                                                    Spacer(modifier = Modifier.width(8.dp))
                                                                    Text(text = conflict.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                                                }
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(text = conflict.description, style = MaterialTheme.typography.bodySmall, color = Color.Black.copy(alpha = 0.7f))
                                                                Spacer(modifier = Modifier.height(8.dp))
                                                                
                                                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                                                    Button(
                                                                        onClick = { onResolveInline(event.id, conflict.deepDivePrompt) },
                                                                        enabled = !inlineCopilotLoading.contains(event.id),
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.5f), contentColor = Color(0xFFD32F2F)),
                                                                        shape = RoundedCornerShape(8.dp),
                                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                                        modifier = Modifier.height(32.dp)
                                                                    ) {
                                                                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text("RESOLVE CONFLICT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            } else {
                                                                // No conflict, just standard action
                                                                val askPrompt = """
                                                                    Analyze my memories, notes, and habits for today's event: "${event.title}" scheduled at ${event.time} (${event.appName}). Please provide a cohesive context correlation summary to help me prepare.
                                                                """.trimIndent()
                                                                
                                                                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                                                                    Button(
                                                                        onClick = { onResolveInline(event.id, askPrompt) },
                                                                        enabled = !inlineCopilotLoading.contains(event.id),
                                                                        colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.5f), contentColor = Color.Black.copy(alpha = 0.7f)),
                                                                        shape = RoundedCornerShape(8.dp),
                                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                                                        modifier = Modifier.height(32.dp)
                                                                    ) {
                                                                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp))
                                                                        Spacer(modifier = Modifier.width(4.dp))
                                                                        Text("ASK CO-PILOT", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                                                    }
                                                                }
                                                            }
 
                                                            // Inline AI response container
                                                            val inlineResponse = inlineCopilotResponses[event.id]
                                                            val isLoading = inlineCopilotLoading.contains(event.id)
 
                                                            if (isLoading) {
                                                                Spacer(modifier = Modifier.height(12.dp))
                                                                Box(
                                                                    modifier = Modifier
                                                                        .fillMaxWidth()
                                                                        .padding(8.dp),
                                                                    contentAlignment = Alignment.Center
                                                                ) {
                                                                    CircularProgressIndicator(
                                                                        modifier = Modifier.size(24.dp),
                                                                        color = if (isConflicting) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                                                        strokeWidth = 2.5.dp
                                                                    )
                                                                }
                                                            }
 
                                                            if (inlineResponse != null) {
                                                                Spacer(modifier = Modifier.height(12.dp))
                                                                Card(
                                                                    modifier = Modifier.fillMaxWidth(),
                                                                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.6f)),
                                                                    shape = RoundedCornerShape(12.dp)
                                                                ) {
                                                                    Column(modifier = Modifier.padding(12.dp)) {
                                                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                                            Icon(
                                                                                imageVector = Icons.Default.AutoAwesome,
                                                                                contentDescription = null,
                                                                                tint = if (isConflicting) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary,
                                                                                modifier = Modifier.size(16.dp)
                                                                            )
                                                                            Spacer(modifier = Modifier.width(6.dp))
                                                                            Text(
                                                                                text = "Co-Pilot Strategy",
                                                                                style = MaterialTheme.typography.labelMedium,
                                                                                fontWeight = FontWeight.Bold,
                                                                                color = if (isConflicting) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                                                                            )
                                                                        }
                                                                        Spacer(modifier = Modifier.height(6.dp))
                                                                        Text(
                                                                            text = inlineResponse,
                                                                            style = MaterialTheme.typography.bodySmall,
                                                                            color = Color.Black.copy(alpha = 0.8f)
                                                                        )
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // TOMORROW section
                            if (todayTimelineEvents.isNotEmpty() || tomorrowTimelineEvents.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                val tomorrowLabel = run {
                                    val cal = java.util.Calendar.getInstance().apply {
                                        add(java.util.Calendar.DAY_OF_YEAR, 1)
                                    }
                                    "Tomorrow · " + java.text.SimpleDateFormat("EEE, MMM d", java.util.Locale.getDefault()).format(cal.time)
                                }
                                Text(
                                    text = tomorrowLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                                if (tomorrowTimelineEvents.isEmpty()) {
                                    Text(
                                        "No events found for tomorrow.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        tomorrowTimelineEvents.forEach { event ->
                                            val dotColor = when (event.sourcePackage) {
                                                "calendar" -> Color(0xFF1E88E5)
                                                "habit"    -> Color(0xFFF4511E)
                                                else       -> Color(0xFF9E9E9E)
                                            }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(10.dp))
                                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(dotColor)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Text(
                                                    text = event.time,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.outline,
                                                    modifier = Modifier.width(60.dp)
                                                )
                                                Text(
                                                    text = event.title,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Medium,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(dotColor.copy(alpha = 0.15f))
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = event.appName,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = dotColor
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        if (showDetails) item {
            SummaryGrid(
                totalFeed = memories.size,
                unreadFeed = unreadCount,
                appsCount = appCount,
                onCardClick = { onNavigateToTab(AppTab.FEED) }
            )
        }

        // Smartwatch Health Connect Card (Recommendation 6)
        if (showDetails && healthConnectAvailable) {
            item {
                HomeSectionCard(
                    title = "Smartwatch Wellness (Health Connect)",
                    icon = Icons.Default.Favorite,
                    iconColor = PastelGreen,
                    onClick = { if (!healthPermissionGranted) onConnectHealth() }
                ) {
                    if (healthPermissionGranted) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Steps Row
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Steps Today",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${healthMetrics.steps} / 10,000",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                val progress = (healthMetrics.steps.toFloat() / 10000f).coerceIn(0f, 1f)
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = PastelGreen,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }

                            // Sleep & Heart Rate Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                // Sleep Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Last Sleep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val h = healthMetrics.sleepMinutes / 60
                                        val m = healthMetrics.sleepMinutes % 60
                                        Text(
                                            text = if (healthMetrics.sleepMinutes > 0) "${h}h ${m}m" else "--",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Heart Rate Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text("Heart Rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (healthMetrics.avgHeartRate > 0) "${healthMetrics.avgHeartRate} BPM" else "--",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Synchronize physical health logs (Steps, Sleep, and Heart Rate) generated by your Zepp watch and other devices.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            Button(
                                onClick = onConnectHealth,
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            ) {
                                Icon(Icons.Default.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect Health Connect", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }

        // Zen Meditation Card
        if (showDetails) item {
            HomeSectionCard(
                title = "Zen Meditation (Zendence)",
                icon = Icons.Default.Spa,
                iconColor = MaterialTheme.colorScheme.primary,
                onClick = { onNavigateToTab(AppTab.MEDITATION) }
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Streaks Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("This Week", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${meditationStreaks.currentWeekStreak} days",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Overall Streak", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${meditationStreaks.maxOverallStreak} days",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Latest Session Details
                    val latestSession = meditationSessions.firstOrNull()
                    if (latestSession != null) {
                        Column {
                            Text(
                                text = "Last Session Details",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()).format(Date(latestSession.timestamp)),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${latestSession.durationMinutes} mins",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    if (latestSession.insight.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = "\"${latestSession.insight}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Text(
                            text = "No meditation sessions completed this week. Start a session in Zendence to log your first entry!",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        if (showDetails && latestReflection != null) {
            item {
                HomeSectionCard(
                    title = "Latest Intelligence",
                    icon = Icons.Default.AutoAwesome,
                    iconColor = PastelBlue,
                    onClick = { onNavigateToTab(AppTab.BRAIN) }
                ) {
                    Text(
                        text = latestReflection.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 20.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (showDetails) item {
            HomeSectionCard(
                title = "Recent Notes",
                icon = Icons.Default.Description,
                iconColor = PastelGreen,
                onClick = { onNavigateToTab(AppTab.NOTES) }
            ) {
                if (notes.isEmpty()) {
                    Text("No notes found.", style = MaterialTheme.typography.bodySmall)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        notes.take(3).forEach { note ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = note.name?.removeSuffix(".md") ?: "Untitled",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (notes.size > 3) {
                            Text(
                                text = "+ ${notes.size - 3} more",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }

        if (showDetails && consolidatedUsage.isNotEmpty()) {
            item {
                HomeSectionCard(
                    title = "Screen Time",
                    icon = Icons.Default.Schedule,
                    iconColor = PastelPurple,
                    onClick = { onNavigateToTab(AppTab.TIME) }
                ) {
                    UsageBarChart(consolidatedUsage.take(3))
                }
            }
        }
    }

    if (showQuickAddDialog) {
        var eventTitle by remember { mutableStateOf("") }
        var eventTime by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text(text = "➕ Quick-Add Agenda Event", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        label = { Text("Event Title") },
                        placeholder = { Text("e.g. Gym workout") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = eventTime,
                        onValueChange = { eventTime = it },
                        label = { Text("Scheduled Time") },
                        placeholder = { Text("e.g. 7:00 PM") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (eventTitle.isNotBlank() && eventTime.isNotBlank()) {
                            onAddManualEvent(eventTitle.trim(), eventTime.trim())
                            showQuickAddDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Add to Timeline")
                }
            },
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartSummaryCard(
    senseOfDayScore: Int,
    senseOfDayContext: String,
    config: HomeSummaryConfig,
    topConflict: TimelineConflict?,
    completedHabitCount: Int,
    totalHabitCount: Int,
    nextEvent: TimelineEvent?,
    healthMetrics: com.alex.a2ndbrain.core.health.HealthMetrics,
    healthAvailable: Boolean,
    meditatedToday: Boolean,
    meditationWeekStreak: Int,
    showDetails: Boolean,
    onToggleDetails: () -> Unit,
    onDeepDiveConflict: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val ringColor = remember(senseOfDayScore) {
        Color.hsv(senseOfDayScore.toFloat() * 1.3f, 0.75f, 0.9f)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ── Score ring + context text ─────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawCircle(color = Color.LightGray.copy(alpha = 0.2f), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
                        drawArc(color = ringColor, startAngle = -90f, sweepAngle = senseOfDayScore / 100f * 360f, useCenter = false, style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$senseOfDayScore%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Sense of Day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                    if (config.showSenseOfDayText) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = senseOfDayContext, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, lineHeight = 18.sp)
                    }
                }
            }

            // ── Top alert ─────────────────────────────────────────────────
            if (config.showAlerts && topConflict != null) {
                val accentColor = when (topConflict.type) {
                    ConflictType.OVERLAP          -> Color(0xFFE53935)
                    ConflictType.OVERDUE_HABIT    -> Color(0xFFFB8C00)
                    ConflictType.DISTRACTION_GAP  -> Color(0xFF8E24AA)
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDeepDiveConflict(topConflict.deepDivePrompt) },
                    colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.08f)),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Warning, contentDescription = null, tint = accentColor, modifier = Modifier.size(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = topConflict.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = accentColor)
                            Text(text = topConflict.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = accentColor.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                    }
                }
            }

            // ── Stats pills ───────────────────────────────────────────────
            val activePills = buildList {
                if (config.showHabitPill && totalHabitCount > 0) {
                    val label = "$completedHabitCount / $totalHabitCount habits"
                    val pillColor = when {
                        totalHabitCount == 0           -> Color.Gray
                        completedHabitCount == totalHabitCount -> PastelGreen
                        completedHabitCount.toFloat() / totalHabitCount >= 0.5f -> PastelBlue
                        else -> Color(0xFFFFCC80)
                    }
                    add(label to pillColor)
                }
                if (config.showNextEventPill) {
                    val label = nextEvent?.let { "${it.title.take(18)} ${it.time}" } ?: "No events today"
                    add(label to PastelYellow)
                }
                if (config.showStepsPill && healthAvailable) {
                    add("${healthMetrics.steps} steps" to PastelGreen)
                }
                if (config.showSleepMeditationPill) {
                    val label = when {
                        meditatedToday -> "Meditated ✓"
                        meditationWeekStreak > 0 -> "$meditationWeekStreak day streak"
                        healthAvailable && healthMetrics.sleepMinutes > 0 ->
                            "${healthMetrics.sleepMinutes / 60}h ${healthMetrics.sleepMinutes % 60}m sleep"
                        else -> "No sleep data"
                    }
                    add(label to MaterialTheme.colorScheme.surfaceVariant)
                }
            }

            if (activePills.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    activePills.forEach { (label, color) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(color.copy(alpha = 0.18f))
                                .border(BorderStroke(1.dp, color.copy(alpha = 0.35f)), RoundedCornerShape(20.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }

            // ── Toggle button ─────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleDetails() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (showDetails) "Show less" else "Show all details",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (showDetails) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun SummaryGrid(
    totalFeed: Int,
    unreadFeed: Int,
    appsCount: Int,
    onCardClick: () -> Unit
) {
    Card(
        onClick = onCardClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            SummaryItem(label = "Total", value = totalFeed.toString(), color = PastelBlue, textColor = PastelBlueText)
            SummaryItem(label = "Unread", value = unreadFeed.toString(), color = PastelRed, textColor = PastelRedText)
            SummaryItem(label = "Apps", value = appsCount.toString(), color = PastelGreen, textColor = PastelGreenText)
        }
    }
}

@Composable
fun SummaryItem(label: String, value: String, color: Color, textColor: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = textColor
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
fun HomeSectionCard(
    title: String,
    icon: ImageVector,
    iconColor: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val tintColor = remember(iconColor, primaryColor, onPrimaryColor) {
        when (iconColor) {
            PastelBlue -> PastelBlueText
            PastelGreen -> PastelGreenText
            PastelRed -> PastelRedText
            PastelYellow -> PastelYellowText
            PastelPurple -> PastelPurpleText
            primaryColor -> onPrimaryColor
            else -> onPrimaryColor
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = tintColor
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SpeakingSoundwave(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    color: Color = PastelPurple
) {
    val infiniteTransition = rememberInfiniteTransition(label = "soundwave")
    val scale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale1"
    )
    val scale2 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, delayMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale2"
    )
    val scale3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 350, delayMillis = 50, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale3"
    )
    val scale4 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, delayMillis = 150, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale4"
    )

    Row(
        modifier = modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val s1 = if (isSpeaking) scale1 else 0.2f
        val s2 = if (isSpeaking) scale2 else 0.3f
        val s3 = if (isSpeaking) scale3 else 0.2f
        val s4 = if (isSpeaking) scale4 else 0.3f

        Box(modifier = Modifier.size(width = 3.dp, height = (20.dp * s1)).clip(RoundedCornerShape(2.dp)).background(color))
        Box(modifier = Modifier.size(width = 3.dp, height = (20.dp * s2)).clip(RoundedCornerShape(2.dp)).background(color))
        Box(modifier = Modifier.size(width = 3.dp, height = (20.dp * s3)).clip(RoundedCornerShape(2.dp)).background(color))
        Box(modifier = Modifier.size(width = 3.dp, height = (20.dp * s4)).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
fun ActiveConflictsSection(
    conflicts: List<TimelineConflict>,
    inlineCopilotResponses: Map<String, String>,
    inlineCopilotLoading: Set<String>,
    onDismissConflict: (String) -> Unit,
    onDeepDive: (String) -> Unit,
    onResolveInline: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = "Active Conflicts & Constraints",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        conflicts.forEach { conflict ->
            key(conflict.id) {
                GlassmorphicConflictCard(
                    conflict = conflict,
                    inlineResponse = inlineCopilotResponses[conflict.id],
                    isLoading = inlineCopilotLoading.contains(conflict.id),
                    onDismiss = { onDismissConflict(conflict.id) },
                    onDeepDive = { onDeepDive(conflict.deepDivePrompt) },
                    onResolveInline = { onResolveInline(conflict.id, conflict.deepDivePrompt) }
                )
            }
        }
    }
}

@Composable
fun GlassmorphicConflictCard(
    conflict: TimelineConflict,
    inlineResponse: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onDeepDive: () -> Unit,
    onResolveInline: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Always use deep, premium dark hues for high-contrast visibility with white text
    val baseColor = when (conflict.type) {
        ConflictType.OVERLAP -> Color(0xFF421010) // Deep red
        ConflictType.OVERDUE_HABIT -> Color(0xFF3D2305) // Deep amber
        ConflictType.DISTRACTION_GAP -> Color(0xFF23103D) // Deep purple
    }
    
    val accentColor = when (conflict.type) {
        ConflictType.OVERLAP -> Color(0xFFE53935)
        ConflictType.OVERDUE_HABIT -> Color(0xFFFB8C00)
        ConflictType.DISTRACTION_GAP -> Color(0xFF8E24AA)
    }
    
    val glassGradient = Brush.verticalGradient(
        colors = listOf(
            baseColor.copy(alpha = 0.85f),
            Color(0xFF0F0F14).copy(alpha = 0.95f)
        )
    )
    
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.25f),
            Color.White.copy(alpha = 0.08f)
        )
    )
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, borderBrush, RoundedCornerShape(24.dp)),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .background(glassGradient)
                .padding(20.dp)
        ) {
            // Header Row: Badge & Dismiss
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Category badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val badgeIcon = when (conflict.type) {
                        ConflictType.OVERLAP -> Icons.Default.Schedule
                        ConflictType.OVERDUE_HABIT -> Icons.Default.Warning
                        ConflictType.DISTRACTION_GAP -> Icons.Default.Lock
                    }
                    val badgeLabel = when (conflict.type) {
                        ConflictType.OVERLAP -> "Schedule Crunch"
                        ConflictType.OVERDUE_HABIT -> "Overdue Habit"
                        ConflictType.DISTRACTION_GAP -> "Focus Strain"
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(accentColor.copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = badgeLabel.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                    }
                    
                    // Severity Badge
                    val severityLabel = conflict.severity.name
                    val severityBgColor = if (conflict.severity == ConflictSeverity.ALERT) {
                        Color.Red.copy(alpha = 0.15f)
                    } else {
                        Color.Gray.copy(alpha = 0.2f)
                    }
                    val severityTextColor = if (conflict.severity == ConflictSeverity.ALERT) {
                        Color(0xFFFF8A80)
                    } else {
                        Color.LightGray
                    }
                    
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(severityBgColor)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = severityLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = severityTextColor
                        )
                    }
                }
                
                // Dismiss Button
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Dismiss Alert",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Title & Description
            Text(
                text = conflict.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = conflict.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Inline Strategy Button
                Button(
                    onClick = onResolveInline,
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor.copy(alpha = 0.2f),
                        contentColor = accentColor
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(38.dp).weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Inline Strategy",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Deep Dive Button (switches to Co-Pilot screen)
                Button(
                    onClick = onDeepDive,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.height(38.dp).weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Deep Dive",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Collapsible strategy response drawer
            if (isLoading) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = accentColor,
                        strokeWidth = 2.dp
                    )
                }
            }
            
            if (inlineResponse != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.Black.copy(alpha = 0.35f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Co-Pilot Strategy",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = inlineResponse,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }
    }
}

