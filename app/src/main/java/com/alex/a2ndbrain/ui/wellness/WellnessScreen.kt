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
import org.koin.androidx.compose.koinViewModel

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
        healthViewModel.refresh()
        reflectionViewModel.loadWeeklyHealthTrends()
    }

    val tabs = listOf("Health", "Time", "Zen", "Reflect")
    var selectedTab by remember { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (selectedTab) {
                0 -> HealthScreen(viewModel = healthViewModel, modifier = Modifier.fillMaxSize())
                1 -> DigitalTimeScreen()
                2 -> MeditationScreen(sessions = sessions, streaks = streaks)
                3 -> ReflectionScreen(
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
