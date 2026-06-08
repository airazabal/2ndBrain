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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.ui.theme.*
import com.alex.a2ndbrain.ConsolidatedUsage
import com.alex.a2ndbrain.ui.usage.UsageBarChart
import com.alex.a2ndbrain.TimelineEvent
import com.alex.a2ndbrain.core.reflection.TtsManager
import com.alex.a2ndbrain.TimelineConflict
import com.alex.a2ndbrain.ConflictType
import com.alex.a2ndbrain.ConflictSeverity
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VolumeMute
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.draw.rotate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Hub
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar
import java.util.Date
import com.alex.a2ndbrain.ui.home.GrandCentralResult
import com.alex.a2ndbrain.ui.home.NotificationCategory
import com.alex.a2ndbrain.ui.home.CategorizedNotification
import com.alex.a2ndbrain.core.todoist.TaskLatencyStats
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver



@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    memories: List<MemoryEntity>,
    latestReflection: DailySummaryEntity?,
    notes: List<DocumentFile>,
    consolidatedUsage: List<ConsolidatedUsage>,
    onNavigateToTab: (AppTab) -> Unit,
    onNavigateToFeedWithFilter: (String) -> Unit = {},
    healthMetrics: com.alex.a2ndbrain.core.health.HealthMetrics = com.alex.a2ndbrain.core.health.HealthMetrics(),
    healthPermissionGranted: Boolean = false,
    healthConnectAvailable: Boolean = false,
    onConnectHealth: () -> Unit = {},
    meditationSessions: List<com.alex.a2ndbrain.core.meditation.MeditationSession> = emptyList(),
    meditationStreaks: com.alex.a2ndbrain.core.meditation.StreakResult = com.alex.a2ndbrain.core.meditation.StreakResult(0, 0, 0),

    senseOfDayScore: Int = 0,
    senseOfDayContext: String = "Calibrating your day...",
    senseOfDayPillars: List<SenseOfDayPillar> = emptyList(),
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
    lastRefreshedAt: Long = System.currentTimeMillis(),
    refreshIntervalMinutes: Int = 30,
    unreadEmailCount: Int = 0,
    unreadMessageCount: Int = 0,
    meetingsTodayCount: Int = 0,
    grandCentralResult: GrandCentralResult = GrandCentralResult(),
    agendaItems: List<TodayAgendaItem> = emptyList(),
    agendaOverdueCount: Int = 0,
    taskLatencyStats: TaskLatencyStats = TaskLatencyStats(),
    agendaLoading: Boolean = false,
    onCompleteTask: (String) -> Unit = {},
    onToggleHabit: (String) -> Unit = {},
    onRefreshAgenda: () -> Unit = {},
    onRefreshIntervalChange: (Int) -> Unit = {},
    exerciseSessionsThisWeek: Int = 0,
    exerciseTotalMinutesThisWeek: Int = 0,
    onExerciseClick: () -> Unit = {},
    onPillarClick: (String) -> Unit = {},
    themePreference: String = "SYSTEM",
    onThemeToggle: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsManager = remember { TtsManager(context) }
    val isSpeaking by ttsManager.isSpeaking.collectAsState()

    var showAgendaSheet by remember { mutableStateOf(false) }
    var showQuickAddDialog by remember { mutableStateOf(false) }
    var isTimelineExpanded by remember { mutableStateOf(true) }
    var expandedTimelineEventId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        onRefreshHealth()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onRefreshAgenda()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
        // Dashboard header — greeting + stat cards
        item {
            DashboardHeader(
                lastRefreshedAt       = lastRefreshedAt,
                refreshIntervalMinutes = refreshIntervalMinutes,
                onRefreshIntervalChange = onRefreshIntervalChange,
                overdueCount          = agendaOverdueCount,
                unreadEmailCount      = unreadEmailCount,
                meetingsCount         = meetingsTodayCount,
                unreadMessageCount    = unreadMessageCount,
                steps                 = healthMetrics.steps,
                sleepMinutes          = healthMetrics.sleepMinutes,
                avgHeartRate          = healthMetrics.avgHeartRate,
                onOverdueClick        = { showAgendaSheet = true },
                onEmailClick          = { onNavigateToFeedWithFilter("Gmail") },
                onMeetingsClick       = { showAgendaSheet = true },
                onMessagesClick       = { onNavigateToFeedWithFilter("Messages") },
                onHealthClick         = { onNavigateToTab(AppTab.WELLNESS) },
                exerciseSessionsThisWeek = exerciseSessionsThisWeek,
                exerciseTotalMinutesThisWeek = exerciseTotalMinutesThisWeek,
                onExerciseClick       = onExerciseClick,
                onPillarClick         = onPillarClick,
                themePreference       = themePreference,
                onThemeToggle         = onThemeToggle,
                senseOfDayScore       = senseOfDayScore,
                senseOfDayContext     = senseOfDayContext,
                senseOfDayPillars     = senseOfDayPillars
            )
        }

        // Grand Central
        item {
            GrandCentralCard(
                grandCentralResult = grandCentralResult,
                todayEvents        = todayTimelineEvents,
                timelineConflicts  = timelineConflicts,
                healthMetrics      = healthMetrics,
                unreadEmailCount   = unreadEmailCount,
                unreadMessageCount = unreadMessageCount,
                onTasksClick       = {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_CALENDAR)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (e: Exception) { showAgendaSheet = true }
                },
                onEmailClick       = {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_EMAIL)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (e: Exception) { onNavigateToFeedWithFilter("Gmail+unread") }
                },
                onMessagesClick    = {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MESSAGING)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try { context.startActivity(intent) } catch (e: Exception) { onNavigateToFeedWithFilter("Messages+unread") }
                },
                onHealthClick      = { onNavigateToTab(AppTab.WELLNESS) },
                onFeedClick        = { onNavigateToFeedWithFilter("All") }
            )
        }

    }

    // ── Today's Agenda Sheet ─────────────────────────────────────────────────
    if (showAgendaSheet) {
        val agendaTaskTitles = agendaItems.filterIsInstance<TodayAgendaItem.Task>()
            .map { it.task.content.trim().lowercase() }.toSet()
        val calEvents = todayTimelineEvents
            .filter { it.sourcePackage == "calendar" }
            .filter { !it.appName.contains("todoist", ignoreCase = true) && it.title.trim().lowercase() !in agendaTaskTitles }
            .sortedBy { it.minutesFromMidnight }
        val overdueItems = agendaItems.filter { it.isOverdue && !it.isCompleted }
        val upcomingItems = agendaItems.filter { !it.isOverdue && !it.isCompleted }
        val completedItems = agendaItems.filter { it.isCompleted }

        ModalBottomSheet(onDismissRequest = { showAgendaSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Today's Agenda", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        val pending = agendaItems.count { !it.isCompleted }
                        Text(
                            if (pending == 0) "All done!" else "$pending remaining · ${completedItems.size} done",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (taskLatencyStats.completedCount > 0) {
                            Text(
                                "Task avg: ${"%.1f".format(taskLatencyStats.avgDays)}d overdue",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            )
                        }
                    }
                    if (agendaLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = onRefreshAgenda) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Overdue section
                if (overdueItems.isNotEmpty()) {
                    AgendaSectionLabel("OVERDUE", Color(0xFFE53935))
                    overdueItems.forEach { item -> AgendaItemRow(item, taskLatencyStats, onCompleteTask, onToggleHabit) }
                    Spacer(Modifier.height(12.dp))
                }

                // Upcoming: agenda items
                if (upcomingItems.isNotEmpty()) {
                    AgendaSectionLabel("TODAY", MaterialTheme.colorScheme.primary)
                    upcomingItems.forEach { item -> AgendaItemRow(item, taskLatencyStats, onCompleteTask, onToggleHabit) }
                    Spacer(Modifier.height(12.dp))
                }

                // Calendar events
                if (calEvents.isNotEmpty()) {
                    AgendaSectionLabel("CALENDAR", Color(0xFF1E88E5))
                    calEvents.forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(event.time, style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.width(48.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(event.appName, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        HorizontalDivider()
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // Completed
                if (completedItems.isNotEmpty()) {
                    AgendaSectionLabel("DONE", Color(0xFF43A047))
                    completedItems.forEach { item -> AgendaItemRow(item, taskLatencyStats, onCompleteTask, onToggleHabit) }
                }

                if (agendaItems.isEmpty() && calEvents.isEmpty() && !agendaLoading) {
                    Text("Nothing on the agenda today.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
private fun AgendaSectionLabel(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Box(modifier = Modifier.size(6.dp, 14.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            color = color, letterSpacing = 0.8.sp)
    }
}

@Composable
private fun AgendaItemRow(
    item: TodayAgendaItem,
    taskLatencyStats: com.alex.a2ndbrain.core.todoist.TaskLatencyStats,
    onCompleteTask: (String) -> Unit,
    onToggleHabit: (String) -> Unit
) {
    val overdueColor = Color(0xFFE53935)
    val doneColor = Color(0xFF43A047)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Check button
        when (item) {
            is TodayAgendaItem.Task -> {
                val priorityColor = when (item.task.priority) {
                    4 -> overdueColor; 3 -> Color(0xFFFF8F00); 2 -> Color(0xFF1E88E5)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                }
                IconButton(onClick = { onCompleteTask(item.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.RadioButtonUnchecked, contentDescription = "Complete",
                        tint = if (item.isOverdue) overdueColor else priorityColor,
                        modifier = Modifier.size(22.dp))
                }
            }
            is TodayAgendaItem.Habit -> {
                val tint = when {
                    item.isCompleted -> doneColor
                    item.isOverdue -> overdueColor
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                }
                IconButton(onClick = { onToggleHabit(item.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (item.isCompleted) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                        contentDescription = if (item.isCompleted) "Mark incomplete" else "Complete habit",
                        tint = tint, modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // Content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (item.isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        else MaterialTheme.colorScheme.onSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (item is TodayAgendaItem.Habit && item.habit.timeString.isNotBlank()) {
                    Text(item.habit.timeString, style = MaterialTheme.typography.labelSmall,
                        color = if (item.isOverdue) overdueColor else MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                }
                if (item is TodayAgendaItem.Task) {
                    val dueLabel = item.task.dueDateStr?.take(10) ?: item.task.deadlineDateStr?.take(10)
                    if (dueLabel != null && item.isOverdue) {
                        Text("Due $dueLabel", style = MaterialTheme.typography.labelSmall, color = overdueColor.copy(alpha = 0.8f))
                    }
                }
                if (item is TodayAgendaItem.Habit && !item.habit.repeatRule.isNullOrBlank()) {
                    Text("🔄 ${item.habit.repeatRule}", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }
        }

        // Type badge + stale badge
        if (item is TodayAgendaItem.Habit) {
            Text(item.habit.emoji, fontSize = 16.sp)
        }
        if (item is TodayAgendaItem.Task) {
            val staleDays = taskLatencyStats.staleDays[item.id] ?: 0
            if (staleDays >= 1) {
                Surface(shape = RoundedCornerShape(4.dp), color = overdueColor.copy(alpha = 0.1f)) {
                    Text("${staleDays}d", style = MaterialTheme.typography.labelSmall,
                        color = overdueColor, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
        }
    }
    HorizontalDivider(modifier = Modifier.padding(vertical = 1.dp))
}

@Composable
private fun GrandCentralCard(
    grandCentralResult: GrandCentralResult,
    todayEvents: List<TimelineEvent>,
    timelineConflicts: List<TimelineConflict>,
    healthMetrics: com.alex.a2ndbrain.core.health.HealthMetrics,
    unreadEmailCount: Int,
    unreadMessageCount: Int,
    latestReflection: DailySummaryEntity? = null,
    onTasksClick: () -> Unit,
    onEmailClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onHealthClick: () -> Unit,
    onFeedClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // ── Local time-sensitive alerts (computed without AI) ────────────────────
    data class LocalAlert(val label: String, val urgency: Int, val onClick: () -> Unit)
    val now = Calendar.getInstance()
    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val currentHour = now.get(Calendar.HOUR_OF_DAY)
    val calendarEvents = todayEvents.filter { it.sourcePackage == "calendar" }

    val localAlerts = buildList<LocalAlert> {
        val imminent = calendarEvents.filter { it.minutesFromMidnight in currentMinutes..(currentMinutes + 15) }.minByOrNull { it.minutesFromMidnight }
        if (imminent != null) {
            val mins = imminent.minutesFromMidnight - currentMinutes
            add(LocalAlert(if (mins <= 0) "⏰ Starting now: ${imminent.title}" else "⏰ In ${mins}m: ${imminent.title}", 0, onTasksClick))
        } else {
            val upcoming = calendarEvents.filter { it.minutesFromMidnight in (currentMinutes + 16)..(currentMinutes + 60) }.minByOrNull { it.minutesFromMidnight }
            if (upcoming != null) add(LocalAlert("⏰ In ${upcoming.minutesFromMidnight - currentMinutes}m: ${upcoming.title}", 1, onTasksClick))
        }
        timelineConflicts.forEach { add(LocalAlert("⚠ ${it.title}", if (it.severity == ConflictSeverity.ALERT) 0 else 1, onTasksClick)) }
        if (currentHour < 12 && healthMetrics.sleepMinutes in 1..359) {
            val h = healthMetrics.sleepMinutes / 60; val m = healthMetrics.sleepMinutes % 60
            add(LocalAlert("🛌 Short sleep: ${if (h > 0) "${h}h " else ""}${m}m last night", 1, onHealthClick))
        }
        if (healthMetrics.avgHeartRate > 100) add(LocalAlert("❤ Elevated HR: ${healthMetrics.avgHeartRate} bpm avg", 1, onHealthClick))
        if (currentHour >= 17 && healthMetrics.steps in 1..2499) add(LocalAlert("👟 Only ${healthMetrics.steps.toInt()} steps so far", 1, onHealthClick))
    }

    // ── AI category expand state ──────────────────────────────────────────────
    var expandedCategories by remember { mutableStateOf<Set<String>>(emptySet()) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Hub, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Text("GRAND CENTRAL", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                if (grandCentralResult.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary)
                } else if (grandCentralResult.categories.isNotEmpty()) {
                    Text("${grandCentralResult.categories.sumOf { it.items.size }} notifications",
                        fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                }
            }

            // ── Right Now (local alerts) ──────────────────────────────────────
            if (localAlerts.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("RIGHT NOW", fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp, color = Color(0xFFE65100).copy(alpha = 0.8f))
                Spacer(Modifier.height(6.dp))
                localAlerts.forEach { alert ->
                    val color = if (alert.urgency == 0) Color(0xFFD32F2F) else Color(0xFFE65100)
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                            .padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp))
                            .background(color.copy(alpha = 0.08f)).clickable { alert.onClick() },
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(color))
                        Text(alert.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = color)
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }

            // ── Loading placeholder ───────────────────────────────────────────
            if (grandCentralResult.isLoading) {
                Spacer(Modifier.height(12.dp))
                Text("Analyzing your notifications…", fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
            }

            // ── Suggested Actions ─────────────────────────────────────────────
            if (grandCentralResult.suggestedActions.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("SUGGESTED ACTIONS", fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp, color = Color(0xFF1E88E5).copy(alpha = 0.8f))
                Spacer(Modifier.height(6.dp))
                grandCentralResult.suggestedActions.forEach { action ->
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)
                            .padding(vertical = 2.dp).clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF1E88E5).copy(alpha = 0.06f))
                            .clickable(onClick = onFeedClick),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.width(3.dp).fillMaxHeight().background(Color(0xFF1E88E5)))
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 7.dp)) {
                            Text(action.summary, style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface)
                            Text(action.sourceApp, fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f))
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }

            // ── Topic Categories ──────────────────────────────────────────────
            if (grandCentralResult.categories.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text("CATEGORIES", fontSize = 9.sp, fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                Spacer(Modifier.height(6.dp))
                grandCentralResult.categories.forEach { category ->
                    val isExpanded = expandedCategories.contains(category.name)
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    expandedCategories = if (isExpanded)
                                        expandedCategories - category.name
                                    else expandedCategories + category.name
                                }
                                .padding(vertical = 7.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(category.emoji, fontSize = 16.sp)
                                Text(category.name, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("${category.items.size}", fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
                                Icon(
                                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = null, modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                )
                            }
                        }
                        if (isExpanded) {
                            category.items.forEach { item ->
                                Row(
                                    modifier = Modifier.fillMaxWidth()
                                        .padding(start = 28.dp, bottom = 4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable(onClick = onFeedClick)
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(item.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 16.sp)
                                    }
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(item.sourceApp, fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }

            // ── All clear ─────────────────────────────────────────────────────
            if (!grandCentralResult.isLoading && grandCentralResult.categories.isEmpty() && localAlerts.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = Color(0xFF388E3C), modifier = Modifier.size(16.dp))
                    Text("All clear — nothing to review right now.", fontSize = 13.sp,
                        color = Color(0xFF388E3C))
                }
            }

            val reflectionSnippet = remember(latestReflection) {
                latestReflection?.summary?.takeIf { it.isNotBlank() }?.let { raw ->
                    val lines = raw.lines().map { it.trim() }.filter { it.isNotBlank() }

                    // A true section header uses # markdown or is a standalone bold phrase
                    // (no sentence content after it). A bold item inside a list is NOT a header.
                    fun isSectionHeader(line: String): Boolean {
                        if (line.startsWith("#")) return true
                        if (!line.startsWith("**")) return false
                        val inner = line.removePrefix("**").removeSuffix("**").removeSuffix("**:").removeSuffix("**.").trim()
                        // Treat as header only if it's a short title-like phrase (≤ 8 words, no sentence punctuation mid-text)
                        val wordCount = inner.split(" ").size
                        return wordCount <= 8 && !inner.contains(". ")
                    }

                    fun stripMarkdown(line: String) = line
                        .replace(Regex("^[-*•]\\s+"), "")
                        .replace(Regex("^\\d+[.)>]\\s*"), "")
                        .replace(Regex("\\*\\*(.*?)\\*\\*"), "$1")
                        .replace(Regex("\\*(.*?)\\*"), "$1")
                        .trim()

                    // Matches explicit bullet/numbered lines AND standalone bold-title lines
                    // (e.g. "**Re-energize with Movement**" used as a list item without a marker)
                    fun isBulletLine(line: String) =
                        line.startsWith("- ") || line.startsWith("* ") ||
                        line.startsWith("• ") || line.matches(Regex("^\\d+[.)>].*")) ||
                        (line.startsWith("**") && line.length > 4)

                    fun extractBullets(src: List<String>): List<String> = src
                        .filter { isBulletLine(it) }
                        .map { stripMarkdown(it) }
                        .filter { it.isNotBlank() }

                    // Find the recommendations / advisory section header
                    val advisoryIndex = lines.indexOfFirst { line ->
                        val lower = line.lowercase()
                        isSectionHeader(line) && (
                            lower.contains("recommendation") ||
                            lower.contains("take away") || lower.contains("takeaway") ||
                            lower.contains("second brain") ||
                            lower.contains("advisory") ||
                            lower.contains("key insight") || lower.contains("key takeaway") ||
                            lower.contains("action item") || lower.contains("next step") ||
                            lower.contains("focus area") || lower.contains("suggestion")
                        )
                    }
                    // Collect lines until next # header (bold titles inside the section are content)
                    val sectionLines = if (advisoryIndex >= 0)
                        lines.drop(advisoryIndex + 1).takeWhile { !it.startsWith("#") }
                    else emptyList()

                    val bullets = extractBullets(sectionLines)
                        .ifEmpty { extractBullets(lines) }
                        .ifEmpty {
                            (if (sectionLines.isNotEmpty()) sectionLines else lines)
                                .filter { !isSectionHeader(it) }
                                .take(3)
                                .map { stripMarkdown(it) }
                                .filter { it.isNotBlank() }
                        }

                    bullets.mapIndexed { i, b -> "${i + 1}. $b" }.joinToString("\n")
                }
            }
            if (!reflectionSnippet.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Latest Reflection",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildAnnotatedString {
                        reflectionSnippet!!.split("\n").forEachIndexed { idx, line ->
                            if (idx > 0) append("\n")
                            // Split only on ": " or " - " / " — " (space-surrounded dash), never on a
                            // bare hyphen inside a word like "Re-energize".
                            val match = Regex("^(\\d+\\.\\s*.+?)(?:(?::\\s+|\\s[-—]\\s)(.+))?$").find(line)
                            if (match != null) {
                                withStyle(androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(match.groupValues[1].trim())
                                }
                                val description = match.groupValues[2].trim()
                                if (description.isNotBlank()) append(": $description")
                            } else {
                                append(line)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                    lineHeight = 18.sp
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
fun HealthStripCard(
    healthMetrics: com.alex.a2ndbrain.core.health.HealthMetrics,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = healthMetrics.steps.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("steps", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val h = healthMetrics.sleepMinutes / 60
                val m = healthMetrics.sleepMinutes % 60
                Text(
                    text = if (healthMetrics.sleepMinutes > 0) "${h}h ${m}m" else "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("sleep", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (healthMetrics.avgHeartRate > 0) "${healthMetrics.avgHeartRate} BPM" else "--",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("heart rate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
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

