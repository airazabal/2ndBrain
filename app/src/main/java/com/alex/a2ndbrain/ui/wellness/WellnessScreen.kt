package com.alex.a2ndbrain.ui.wellness

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alex.a2ndbrain.ui.exercise.ExerciseScreen
import com.alex.a2ndbrain.ui.exercise.ExerciseViewModel
import com.alex.a2ndbrain.ui.health.HealthScreen
import com.alex.a2ndbrain.ui.health.HealthViewModel
import com.alex.a2ndbrain.ui.home.WellnessViewModel
import com.alex.a2ndbrain.ui.meditation.MeditationScreen
import com.alex.a2ndbrain.ui.habits.HabitsScreen
import com.alex.a2ndbrain.ui.mood.MoodScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionViewModel
import com.alex.a2ndbrain.ui.goals.GoalsScreen
import com.alex.a2ndbrain.ui.goals.GoalsViewModel
import com.alex.a2ndbrain.ui.todoist.TodoistScreen
import com.alex.a2ndbrain.ui.todoist.TodoistViewModel
import com.alex.a2ndbrain.ui.trends.SenseOfDayTrendsScreen
import com.alex.a2ndbrain.ui.trends.SenseOfDayTrendsViewModel
import com.alex.a2ndbrain.ui.usage.DigitalTimeScreen
import org.koin.androidx.compose.koinViewModel

private enum class WellnessGroup(val label: String) {
    BODY("Body"),
    MIND("Mind"),
    HABITS("Habits"),
    TIME("Time"),
    INSIGHTS("Insights")
}

private enum class WellnessLeaf(val label: String, val group: WellnessGroup) {
    HEALTH("Health", WellnessGroup.BODY),
    EXERCISE("Exercise", WellnessGroup.BODY),
    MOOD("Mood", WellnessGroup.MIND),
    MEDITATION("Meditation", WellnessGroup.MIND),
    HABIT_LIST("Habits", WellnessGroup.HABITS),
    GOALS("Goals", WellnessGroup.HABITS),
    ONLINE("Online", WellnessGroup.TIME),
    TASKS("Tasks", WellnessGroup.TIME),
    TRENDS("Sense of Day", WellnessGroup.INSIGHTS),
    REFLECT("Reflect", WellnessGroup.INSIGHTS)
}

private val groupLeaves: Map<WellnessGroup, List<WellnessLeaf>> =
    WellnessLeaf.entries.groupBy { it.group }

private fun resolveInitialLeaf(initialTab: String): WellnessLeaf = when (initialTab) {
    "EXERCISE"   -> WellnessLeaf.EXERCISE
    "MOOD"       -> WellnessLeaf.MOOD
    "MEDITATION" -> WellnessLeaf.MEDITATION
    "HABITS"     -> WellnessLeaf.HABIT_LIST
    "GOALS"      -> WellnessLeaf.GOALS
    "TASKS"      -> WellnessLeaf.TASKS
    "ONLINE"     -> WellnessLeaf.ONLINE
    "TRENDS"     -> WellnessLeaf.TRENDS
    "REFLECT"    -> WellnessLeaf.REFLECT
    else         -> WellnessLeaf.HEALTH
}

@Composable
fun WellnessScreen(
    initialTab: String = "HEALTH",
    modifier: Modifier = Modifier
) {
    val healthViewModel: HealthViewModel = koinViewModel()
    val wellnessViewModel: WellnessViewModel = koinViewModel()
    val goalsViewModel: GoalsViewModel = koinViewModel()
    val reflectionViewModel: ReflectionViewModel = koinViewModel()
    val exerciseViewModel: ExerciseViewModel = koinViewModel()
    val todoistViewModel: TodoistViewModel = koinViewModel()
    val trendsViewModel: SenseOfDayTrendsViewModel = koinViewModel()

    LaunchedEffect(Unit) { healthViewModel.refresh() }

    val startLeaf = resolveInitialLeaf(initialTab)
    var selectedGroup by remember { mutableStateOf(startLeaf.group) }
    var selectedLeaf by remember { mutableStateOf(startLeaf) }

    LaunchedEffect(selectedLeaf) {
        if (selectedLeaf == WellnessLeaf.GOALS) goalsViewModel.refresh()
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Primary 5-group tab row
        TabRow(selectedTabIndex = WellnessGroup.entries.indexOf(selectedGroup)) {
            WellnessGroup.entries.forEach { group ->
                Tab(
                    selected = selectedGroup == group,
                    onClick = {
                        if (selectedGroup != group) {
                            selectedGroup = group
                            selectedLeaf = groupLeaves[group]!!.first()
                        }
                    },
                    text = { Text(group.label) }
                )
            }
        }

        // Secondary segmented picker — 2 choices per group
        val leaves = groupLeaves[selectedGroup] ?: emptyList()
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            leaves.forEachIndexed { index, leaf ->
                SegmentedButton(
                    selected = selectedLeaf == leaf,
                    onClick = { selectedLeaf = leaf },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = leaves.size),
                    label = { Text(leaf.label) }
                )
            }
        }

        // Content — each branch collects only its own state so changes in one leaf never recompose another
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedLeaf) {
                WellnessLeaf.HEALTH ->
                    HealthScreen(viewModel = healthViewModel, modifier = Modifier.fillMaxSize())

                WellnessLeaf.EXERCISE -> {
                    val exerciseUiState by exerciseViewModel.uiState.collectAsStateWithLifecycle()
                    ExerciseScreen(
                        sessions = exerciseUiState.sessions,
                        weeklyConsistency = exerciseUiState.weeklyConsistency,
                        todaySessionCount = exerciseUiState.todaySessionCount,
                        todayTotalMinutes = exerciseUiState.todayTotalMinutes,
                        weeklySessionCount = exerciseUiState.weeklySessionCount,
                        weeklyTotalMinutes = exerciseUiState.weeklyTotalMinutes,
                        totalSessionCount = exerciseUiState.totalSessionCount,
                        showLogSheet = exerciseUiState.showLogSheet,
                        selectedType = exerciseUiState.selectedType,
                        durationMinutes = exerciseUiState.durationMinutes,
                        notes = exerciseUiState.notes,
                        isLoading = exerciseUiState.isLoading,
                        editingSession = exerciseUiState.editingSession,
                        editSelectedType = exerciseUiState.editSelectedType,
                        editDurationMinutes = exerciseUiState.editDurationMinutes,
                        editNotes = exerciseUiState.editNotes,
                        onShowLogSheet = exerciseViewModel::showLogSheet,
                        onHideLogSheet = exerciseViewModel::hideLogSheet,
                        onSelectType = exerciseViewModel::selectType,
                        onSetDuration = exerciseViewModel::setDuration,
                        onSetNotes = exerciseViewModel::setNotes,
                        onLogSession = exerciseViewModel::logSession,
                        onDeleteSession = exerciseViewModel::deleteSession,
                        onEditSession = exerciseViewModel::showEditSheet,
                        onHideEditSheet = exerciseViewModel::hideEditSheet,
                        onSetEditType = exerciseViewModel::setEditType,
                        onSetEditDuration = exerciseViewModel::setEditDuration,
                        onSetEditNotes = exerciseViewModel::setEditNotes,
                        onSaveEdit = exerciseViewModel::saveEdit,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                WellnessLeaf.MOOD ->
                    MoodScreen(modifier = Modifier.fillMaxSize())

                WellnessLeaf.MEDITATION -> {
                    val sessions by wellnessViewModel.meditationSessions.collectAsStateWithLifecycle()
                    val streaks by wellnessViewModel.meditationStreaks.collectAsStateWithLifecycle()
                    MeditationScreen(sessions = sessions, streaks = streaks)
                }

                WellnessLeaf.HABIT_LIST ->
                    HabitsScreen(modifier = Modifier.fillMaxSize())

                WellnessLeaf.GOALS ->
                    GoalsScreen(viewModel = goalsViewModel, modifier = Modifier.fillMaxSize())

                WellnessLeaf.ONLINE ->
                    DigitalTimeScreen()

                WellnessLeaf.TASKS -> {
                    val todoistUiState by todoistViewModel.uiState.collectAsStateWithLifecycle()
                    TodoistScreen(
                        completions = todoistUiState.completions,
                        weeklyActivity = todoistUiState.weeklyActivity,
                        todayCount = todoistUiState.todayCount,
                        weeklyCount = todoistUiState.weeklyCount,
                        totalCount = todoistUiState.totalCount,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                WellnessLeaf.TRENDS -> {
                    val trendsUiState by trendsViewModel.uiState.collectAsStateWithLifecycle()
                    SenseOfDayTrendsScreen(uiState = trendsUiState, modifier = Modifier.fillMaxSize())
                }

                WellnessLeaf.REFLECT -> {
                    val summaries by reflectionViewModel.summaries.collectAsStateWithLifecycle()
                    val weeklyUsageStats by reflectionViewModel.weeklyUsageStats.collectAsStateWithLifecycle()
                    val weeklyHealthTrends by reflectionViewModel.weeklyHealthTrends.collectAsStateWithLifecycle()
                    val isGenerating by reflectionViewModel.isGeneratingReflection.collectAsStateWithLifecycle()
                    val isGeneratingWeekly by reflectionViewModel.isGeneratingWeeklyInsight.collectAsStateWithLifecycle()
                    val isGeneratingTomorrowForecast by reflectionViewModel.isGeneratingTomorrowForecast.collectAsStateWithLifecycle()
                    val isGeneratingCircadian by reflectionViewModel.isGeneratingCircadian.collectAsStateWithLifecycle()
                    LaunchedEffect(Unit) { reflectionViewModel.loadWeeklyHealthTrends() }
                    ReflectionScreen(
                        summaries = summaries,
                        isGenerating = isGenerating,
                        onGenerateReflection = { reflectionViewModel.generateReflection() },
                        onCancelReflection = { reflectionViewModel.cancelReflection() },
                        onClearAll = { reflectionViewModel.clearAllSummaries() },
                        onDeleteSummary = { id -> reflectionViewModel.deleteSummary(id) },
                        weeklyUsageStats = weeklyUsageStats,
                        weeklyHealthTrends = weeklyHealthTrends,
                        isGeneratingWeeklyInsight = isGeneratingWeekly,
                        onGenerateWeeklyInsight = { reflectionViewModel.generateWeeklyInsight() },
                        isGeneratingTomorrowForecast = isGeneratingTomorrowForecast,
                        onGenerateTomorrowForecast = { reflectionViewModel.generateTomorrowForecast() },
                        isGeneratingCircadian = isGeneratingCircadian,
                        onGenerateCircadianInsight = { reflectionViewModel.generateCircadianInsight() }
                    )
                }
            }
        }
    }
}
