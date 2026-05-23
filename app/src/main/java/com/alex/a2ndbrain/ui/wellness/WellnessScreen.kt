package com.alex.a2ndbrain.ui.wellness

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.ui.health.HealthScreen
import com.alex.a2ndbrain.ui.health.HealthViewModel
import com.alex.a2ndbrain.ui.home.HomeViewModel
import com.alex.a2ndbrain.ui.meditation.MeditationScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionScreen
import com.alex.a2ndbrain.ui.reflection.ReflectionViewModel
import com.alex.a2ndbrain.ui.usage.DigitalTimeScreen
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private enum class WellnessTab(val label: String) {
    HEALTH("Health"), TIME("Time"), ZEN("Zen"), REFLECT("Reflect")
}

@Composable
fun WellnessScreen(
    settingsManager: CaptureSettingsManager,
    modifier: Modifier = Modifier
) {
    val healthViewModel: HealthViewModel = koinViewModel()
    val homeViewModel: HomeViewModel = koinViewModel()
    val reflectionViewModel: ReflectionViewModel = koinViewModel()

    val sessions by homeViewModel.meditationSessions.collectAsStateWithLifecycle()
    val streaks by homeViewModel.meditationStreaks.collectAsStateWithLifecycle()
    val summaries by reflectionViewModel.summaries.collectAsStateWithLifecycle()
    val weeklyUsageStats by reflectionViewModel.weeklyUsageStats.collectAsStateWithLifecycle()
    val weeklyHealthTrends by reflectionViewModel.weeklyHealthTrends.collectAsStateWithLifecycle()
    val isGenerating by reflectionViewModel.isGeneratingReflection.collectAsStateWithLifecycle()
    val isGeneratingWeekly by reflectionViewModel.isGeneratingWeeklyInsight.collectAsStateWithLifecycle()
    val pastWeekHabitCompletions by homeViewModel.pastWeekHabitCompletions.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        launch { healthViewModel.refresh() }
        launch { reflectionViewModel.loadWeeklyHealthTrends() }
    }

    var selectedTab by remember { mutableStateOf(WellnessTab.HEALTH) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.ordinal) {
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
                WellnessTab.TIME -> DigitalTimeScreen()
                WellnessTab.ZEN -> MeditationScreen(sessions = sessions, streaks = streaks)
                WellnessTab.REFLECT -> ReflectionScreen(
                    summaries = summaries,
                    settingsManager = settingsManager,
                    isGenerating = isGenerating,
                    onGenerateReflection = { reflectionViewModel.generateReflection() },
                    onCancelReflection = { reflectionViewModel.cancelReflection() },
                    onClearAll = { reflectionViewModel.clearAllSummaries() },
                    onDeleteSummary = { id -> reflectionViewModel.deleteSummary(id) },
                    weeklyUsageStats = weeklyUsageStats,
                    weeklyHealthTrends = weeklyHealthTrends,
                    pastWeekHabitCompletions = pastWeekHabitCompletions,
                    isGeneratingWeeklyInsight = isGeneratingWeekly,
                    onGenerateWeeklyInsight = { reflectionViewModel.generateWeeklyInsight() }
                )
            }
        }
    }
}
