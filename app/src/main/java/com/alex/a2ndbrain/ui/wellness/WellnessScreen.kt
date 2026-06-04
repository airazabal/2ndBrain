package com.alex.a2ndbrain.ui.wellness

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alex.a2ndbrain.ui.exercise.ExerciseScreen
import com.alex.a2ndbrain.ui.exercise.ExerciseViewModel
import com.alex.a2ndbrain.ui.health.HealthScreen
import com.alex.a2ndbrain.ui.health.HealthViewModel
import com.alex.a2ndbrain.ui.home.HomeViewModel
import com.alex.a2ndbrain.ui.meditation.MeditationScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionViewModel
import com.alex.a2ndbrain.ui.todoist.TodoistScreen
import com.alex.a2ndbrain.ui.todoist.TodoistViewModel
import com.alex.a2ndbrain.ui.usage.DigitalTimeScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private enum class WellnessTab(val label: String) {
    HEALTH("Health"),
    EXERCISE("Exercise"),
    TASKS("Tasks"),
    MEDITATION("Meditation"),
    ONLINE("Online"),
    REFLECT("Reflect")
}

@Composable
fun WellnessScreen(
    initialTab: String = "HEALTH",
    modifier: Modifier = Modifier
) {
    val healthViewModel: HealthViewModel = koinViewModel()
    val homeViewModel: HomeViewModel = koinViewModel()
    val reflectionViewModel: ReflectionViewModel = koinViewModel()
    val exerciseViewModel: ExerciseViewModel = koinViewModel()
    val todoistViewModel: TodoistViewModel = koinViewModel()

    val sessions by homeViewModel.meditationSessions.collectAsStateWithLifecycle()
    val todoistUiState by todoistViewModel.uiState.collectAsStateWithLifecycle()
    val streaks by homeViewModel.meditationStreaks.collectAsStateWithLifecycle()
    val summaries by reflectionViewModel.summaries.collectAsStateWithLifecycle()
    val weeklyUsageStats by reflectionViewModel.weeklyUsageStats.collectAsStateWithLifecycle()
    val weeklyHealthTrends by reflectionViewModel.weeklyHealthTrends.collectAsStateWithLifecycle()
    val isGenerating by reflectionViewModel.isGeneratingReflection.collectAsStateWithLifecycle()
    val isGeneratingWeekly by reflectionViewModel.isGeneratingWeeklyInsight.collectAsStateWithLifecycle()
    val exerciseUiState by exerciseViewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        launch { healthViewModel.refresh() }
        launch { reflectionViewModel.loadWeeklyHealthTrends() }
    }

    val startTab = WellnessTab.entries.firstOrNull { it.name == initialTab } ?: WellnessTab.HEALTH
    var selectedTab by remember { mutableStateOf(startTab) }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = selectedTab.ordinal) {
            WellnessTab.entries.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                WellnessTab.HEALTH -> HealthScreen(viewModel = healthViewModel, modifier = Modifier.fillMaxSize())
                WellnessTab.TASKS -> TodoistScreen(
                    completions = todoistUiState.completions,
                    weeklyActivity = todoistUiState.weeklyActivity,
                    todayCount = todoistUiState.todayCount,
                    weeklyCount = todoistUiState.weeklyCount,
                    totalCount = todoistUiState.totalCount,
                    modifier = Modifier.fillMaxSize()
                )
                WellnessTab.EXERCISE -> ExerciseScreen(
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
                WellnessTab.MEDITATION -> MeditationScreen(sessions = sessions, streaks = streaks)
                WellnessTab.ONLINE -> DigitalTimeScreen()
                WellnessTab.REFLECT -> ReflectionScreen(
                    summaries = summaries,
                    isGenerating = isGenerating,
                    onGenerateReflection = { reflectionViewModel.generateReflection() },
                    onCancelReflection = { reflectionViewModel.cancelReflection() },
                    onClearAll = { reflectionViewModel.clearAllSummaries() },
                    onDeleteSummary = { id -> reflectionViewModel.deleteSummary(id) },
                    weeklyUsageStats = weeklyUsageStats,
                    weeklyHealthTrends = weeklyHealthTrends,
                    isGeneratingWeeklyInsight = isGeneratingWeekly,
                    onGenerateWeeklyInsight = { reflectionViewModel.generateWeeklyInsight() }
                )
            }
        }
    }
}
