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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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



@OptIn(ExperimentalMaterial3Api::class)
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
    lastRefreshedAt: Long = System.currentTimeMillis(),
    refreshIntervalMinutes: Int = 30,
    unreadEmailCount: Int = 0,
    unreadMessageCount: Int = 0,
    meetingsTodayCount: Int = 0,
    overdueHabitsCount: Int = 0,
    onRefreshIntervalChange: (Int) -> Unit = {},
    onDeleteHabit: (String) -> Unit = {},
    onAddHabit: (String, String, Boolean, Long?) -> Unit = { _, _, _, _ -> },
    onUpdateHabit: (com.alex.a2ndbrain.core.memory.HabitEntity, String, String, Long?) -> Unit = { _, _, _, _ -> },
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val ttsManager = remember { TtsManager(context) }
    val isSpeaking by ttsManager.isSpeaking.collectAsState()

    var showOverdueSheet by remember { mutableStateOf(false) }
    var showHabitsSheet by remember { mutableStateOf(false) }
    var showMeetingsSheet by remember { mutableStateOf(false) }
    var showAddHabitDialog by remember { mutableStateOf(false) }
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
        // Dashboard header — greeting + stat cards
        item {
            DashboardHeader(
                lastRefreshedAt       = lastRefreshedAt,
                refreshIntervalMinutes = refreshIntervalMinutes,
                onRefreshIntervalChange = onRefreshIntervalChange,
                overdueCount          = overdueHabitsCount,
                unreadEmailCount      = unreadEmailCount,
                meetingsCount         = meetingsTodayCount,
                unreadMessageCount    = unreadMessageCount,
                completedHabits       = completedHabitIds.size,
                totalHabits           = activeHabits.count { it.isActive },
                steps                 = healthMetrics.steps, // Long
                sleepMinutes          = healthMetrics.sleepMinutes,
                avgHeartRate          = healthMetrics.avgHeartRate,
                onOverdueClick        = { showOverdueSheet = true },
                onEmailClick          = { onNavigateToTab(AppTab.FEED) },
                onMeetingsClick       = { showMeetingsSheet = true },
                onMessagesClick       = { onNavigateToTab(AppTab.FEED) },
                onHabitsClick         = { showHabitsSheet = true },
                onHealthClick         = { onNavigateToTab(AppTab.WELLNESS) }
            )
        }

        // Needs Attention Now section
        item {
            NeedsAttentionCard(
                activeHabits    = activeHabits,
                completedHabitIds = completedHabitIds,
                meetingsToday   = todayTimelineEvents.filter { it.sourcePackage == "calendar" },
                unreadEmailCount = unreadEmailCount,
                onHabitClick    = { showOverdueSheet = true },
                onMeetingsClick = { showMeetingsSheet = true },
                onEmailClick    = { onNavigateToTab(AppTab.FEED) }
            )
        }

    }

    // ── Shared edit dialog state ─────────────────────────────────────────────
    var editingHabit by remember { mutableStateOf<HabitEntity?>(null) }

    editingHabit?.let { habit ->
        var editName by remember(habit.id) { mutableStateOf(habit.name) }
        var editTime by remember(habit.id) { mutableStateOf(habit.timeString) }
        var repeatIndefinitely by remember(habit.id) { mutableStateOf(habit.repeatUntil == null) }
        var showDatePicker by remember { mutableStateOf(false) }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = habit.repeatUntil
                ?: (System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
        )

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        repeatIndefinitely = false
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        AlertDialog(
            onDismissRequest = { editingHabit = null },
            title = { Text("Edit Habit", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = editName,
                        onValueChange = { editName = it },
                        label = { Text("Name") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = editTime,
                        onValueChange = { editTime = it },
                        label = { Text("Time (HH:MM, 24h)") },
                        placeholder = { Text("e.g. 08:00") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Repeat section
                    Text(
                        text = "REPEAT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 0.8.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { repeatIndefinitely = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = repeatIndefinitely,
                            onClick = { repeatIndefinitely = true }
                        )
                        Text("Indefinitely", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { repeatIndefinitely = false; showDatePicker = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        RadioButton(
                            selected = !repeatIndefinitely,
                            onClick = { repeatIndefinitely = false; showDatePicker = true }
                        )
                        Text("Until date", style = MaterialTheme.typography.bodyMedium)
                        if (!repeatIndefinitely) {
                            Spacer(Modifier.width(8.dp))
                            val selectedMs = datePickerState.selectedDateMillis
                            val dateLabel = if (selectedMs != null)
                                java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(selectedMs))
                            else "Pick date"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable { showDatePicker = true }
                            ) {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (editName.isNotBlank() && editTime.isNotBlank()) {
                            val repeatUntil = if (repeatIndefinitely) null
                                else datePickerState.selectedDateMillis
                            onUpdateHabit(habit, editName, editTime, repeatUntil)
                            editingHabit = null
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingHabit = null }) { Text("Cancel") }
            }
        )
    }

    // ── Add New Habit Dialog ──────────────────────────────────────────────────
    if (showAddHabitDialog) {
        var newName by remember { mutableStateOf("") }
        var newTime by remember { mutableStateOf("") }
        var newIsMedication by remember { mutableStateOf(false) }
        var newRepeatIndefinitely by remember { mutableStateOf(true) }
        var showNewDatePicker by remember { mutableStateOf(false) }
        val newDatePickerState = rememberDatePickerState(
            initialSelectedDateMillis = System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000
        )

        if (showNewDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showNewDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        newRepeatIndefinitely = false
                        showNewDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showNewDatePicker = false }) { Text("Cancel") }
                }
            ) { DatePicker(state = newDatePickerState) }
        }

        AlertDialog(
            onDismissRequest = { showAddHabitDialog = false },
            title = { Text("New Habit", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Name") },
                        placeholder = { Text("e.g. Morning run") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newTime,
                        onValueChange = { newTime = it },
                        label = { Text("Time (HH:MM, 24h)") },
                        placeholder = { Text("e.g. 07:30") },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { newIsMedication = !newIsMedication }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(checked = newIsMedication, onCheckedChange = { newIsMedication = it })
                        Text("This is a medication", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = "REPEAT",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.outline,
                        letterSpacing = 0.8.sp
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { newRepeatIndefinitely = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = newRepeatIndefinitely, onClick = { newRepeatIndefinitely = true })
                        Text("Indefinitely", style = MaterialTheme.typography.bodyMedium)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { newRepeatIndefinitely = false; showNewDatePicker = true }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = !newRepeatIndefinitely,
                            onClick = { newRepeatIndefinitely = false; showNewDatePicker = true }
                        )
                        Text("Until date", style = MaterialTheme.typography.bodyMedium)
                        if (!newRepeatIndefinitely) {
                            Spacer(Modifier.width(8.dp))
                            val selectedMs = newDatePickerState.selectedDateMillis
                            val dateLabel = if (selectedMs != null)
                                java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                                    .format(java.util.Date(selectedMs))
                            else "Pick date"
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.clickable { showNewDatePicker = true }
                            ) {
                                Text(
                                    text = dateLabel,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newName.isNotBlank() && newTime.isNotBlank()) {
                            val repeatUntil = if (newRepeatIndefinitely) null
                                else newDatePickerState.selectedDateMillis
                            onAddHabit(newName, newTime, newIsMedication, repeatUntil)
                            showAddHabitDialog = false
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddHabitDialog = false }) { Text("Cancel") }
            }
        )
    }

    // ── Overdue Habits Sheet ──────────────────────────────────────────────────
    if (showOverdueSheet) {
        val cal = java.util.Calendar.getInstance()
        val currentMins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
        val overdueHabits = activeHabits.filter { habit ->
            val parts = habit.timeString.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            habit.isActive && (h * 60 + m) < currentMins && habit.id !in completedHabitIds
        }
        ModalBottomSheet(onDismissRequest = { showOverdueSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("Overdue Actions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${overdueHabits.size} habit(s) past their scheduled time",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))
                if (overdueHabits.isEmpty()) {
                    Text("All caught up! No overdue habits.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    overdueHabits.forEach { habit ->
                        HabitSheetRow(
                            habit = habit,
                            isCompleted = habit.id in completedHabitIds,
                            showOverdueBadge = true,
                            onToggle = { onToggleHabit(habit.id) },
                            onDelete = { onDeleteHabit(habit.id); showOverdueSheet = false },
                            onEdit = { editingHabit = it }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }

    // ── All Habits Sheet ──────────────────────────────────────────────────────
    if (showHabitsSheet) {
        ModalBottomSheet(onDismissRequest = { showHabitsSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Today's Habits", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("${completedHabitIds.size} / ${activeHabits.count { it.isActive }} completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    FilledTonalButton(
                        onClick = { showAddHabitDialog = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("New Habit")
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (activeHabits.isEmpty()) {
                    Text("No habits yet. Tap New Habit to add one.",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    activeHabits.filter { it.isActive }.sortedBy { habit ->
                        val parts = habit.timeString.split(":")
                        (parts.getOrNull(0)?.toIntOrNull() ?: 8) * 60 + (parts.getOrNull(1)?.toIntOrNull() ?: 0)
                    }.forEach { habit ->
                        val cal2 = java.util.Calendar.getInstance()
                        val currentMins2 = cal2.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal2.get(java.util.Calendar.MINUTE)
                        val parts = habit.timeString.split(":")
                        val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
                        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
                        val isOverdue = (h * 60 + m) < currentMins2 && habit.id !in completedHabitIds
                        HabitSheetRow(
                            habit = habit,
                            isCompleted = habit.id in completedHabitIds,
                            showOverdueBadge = isOverdue,
                            onToggle = { onToggleHabit(habit.id) },
                            onDelete = { onDeleteHabit(habit.id) },
                            onEdit = { editingHabit = it }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }

    // ── Meetings Sheet ────────────────────────────────────────────────────────
    if (showMeetingsSheet) {
        val meetings = todayTimelineEvents.filter { it.sourcePackage == "calendar" }
        ModalBottomSheet(onDismissRequest = { showMeetingsSheet = false }) {
            Column(modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp)) {
                Text("Today's Meetings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("${meetings.size} calendar event(s) today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(16.dp))
                if (meetings.isEmpty()) {
                    Text("No calendar events today.", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    meetings.sortedBy { it.minutesFromMidnight }.forEach { event ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(52.dp)
                            ) {
                                Text(event.time, style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(event.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(event.appName, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
                            }
                            Icon(Icons.Default.Schedule, contentDescription = null,
                                tint = Color(0xFF1E88E5), modifier = Modifier.size(18.dp))
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NeedsAttentionCard(
    activeHabits: List<HabitEntity>,
    completedHabitIds: Set<String>,
    meetingsToday: List<com.alex.a2ndbrain.TimelineEvent>,
    unreadEmailCount: Int,
    onHabitClick: () -> Unit,
    onMeetingsClick: () -> Unit,
    onEmailClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cal = java.util.Calendar.getInstance()
    val currentMins = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)

    data class AttentionItem(
        val label: String,
        val detail: String,
        val urgency: Int, // 0=urgent(red), 1=warning(amber), 2=healthy(green)
        val onClick: () -> Unit
    )

    val items = buildList {
        // Overdue medications → urgent/red
        activeHabits.filter { it.isActive && it.isMedication && it.id !in completedHabitIds }.forEach { habit ->
            val parts = habit.timeString.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if ((h * 60 + m) < currentMins) {
                add(AttentionItem("${habit.name} — OVERDUE.", "Medication scheduled for ${habit.timeString}, not yet taken.", 0, onHabitClick))
            }
        }
        // Overdue non-medication habits → warning/amber
        activeHabits.filter { it.isActive && !it.isMedication && it.id !in completedHabitIds }.forEach { habit ->
            val parts = habit.timeString.split(":")
            val h = parts.getOrNull(0)?.toIntOrNull() ?: 8
            val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
            if ((h * 60 + m) < currentMins) {
                add(AttentionItem("${habit.name} — past due.", "Scheduled for ${habit.timeString}. Mark done or reschedule.", 1, onHabitClick))
            }
        }
        // Meetings today → amber
        if (meetingsToday.isNotEmpty()) {
            val upcoming = meetingsToday.filter { it.minutesFromMidnight > currentMins }
            if (upcoming.isNotEmpty()) {
                val next = upcoming.first()
                add(AttentionItem("${upcoming.size} meeting(s) today.", "Next: ${next.title} at ${next.time}.", 1, onMeetingsClick))
            }
        }
        // Unread email → amber if >5
        if (unreadEmailCount > 5) {
            add(AttentionItem("$unreadEmailCount unread emails.", "Tap to review your inbox.", 1, onEmailClick))
        }
        // All caught up → green
        if (isEmpty()) {
            add(AttentionItem("All clear!", "No overdue actions or urgent items right now.", 2) {})
        }
    }

    val hasUrgent = items.any { it.urgency == 0 }
    val bgColor = when {
        hasUrgent -> Color(0xFFEF5350).copy(alpha = 0.08f)
        items.all { it.urgency == 2 } -> Color(0xFF66BB6A).copy(alpha = 0.08f)
        else -> Color(0xFFFF9800).copy(alpha = 0.08f)
    }
    val borderColor = when {
        hasUrgent -> Color(0xFFEF5350).copy(alpha = 0.25f)
        items.all { it.urgency == 2 } -> Color(0xFF66BB6A).copy(alpha = 0.25f)
        else -> Color(0xFFFF9800).copy(alpha = 0.25f)
    }
    val titleColor = when {
        hasUrgent -> Color(0xFFD32F2F)
        items.all { it.urgency == 2 } -> Color(0xFF388E3C)
        else -> Color(0xFFE65100)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.NotificationsActive, contentDescription = null,
                    tint = titleColor, modifier = Modifier.size(18.dp))
                Text(
                    text = if (items.all { it.urgency == 2 }) "All Clear" else "Needs Your Attention Now",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = titleColor
                )
            }
            Spacer(Modifier.height(10.dp))
            items.forEach { item ->
                val itemColor = when (item.urgency) {
                    0 -> Color(0xFFD32F2F)
                    1 -> Color(0xFFE65100)
                    else -> Color(0xFF388E3C)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                        .clickable { item.onClick() },
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(itemColor)
                    )
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = itemColor
                        )
                        if (item.detail.isNotBlank()) {
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
                if (item != items.last()) Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun HabitSheetRow(
    habit: HabitEntity,
    isCompleted: Boolean,
    showOverdueBadge: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (HabitEntity) -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isCompleted, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(habit.name, style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isCompleted) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.onSurface)
                if (showOverdueBadge && !isCompleted) {
                    Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp),
                        color = Color(0xFFEF5350).copy(alpha = 0.15f)) {
                        Text("past due", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            fontSize = 9.sp, color = Color(0xFFEF5350), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            val repeatLabel = if (habit.repeatUntil != null)
                "Until " + java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
                    .format(java.util.Date(habit.repeatUntil))
            else null
            Text(
                text = if (repeatLabel != null) "${habit.timeString}  ·  $repeatLabel"
                       else habit.timeString,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        IconButton(onClick = { onEdit(habit) }, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Edit, contentDescription = "Edit",
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Default.Delete, contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
        }
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

