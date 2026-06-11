package com.alex.a2ndbrain.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.senseofday.SenseOfDayHistoryRepository
import com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshotEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// pillarIdx: 0=Steps,1=Sleep,2=Exercise,3=Focus,4=Mood,-1=Overall Score
data class CorrelationData(
    val chipLabel: String,
    val chartTitle: String,
    val aLabel: String,
    val aPillarIdx: Int,
    val aValues: List<Float>,
    val bLabel: String,
    val bPillarIdx: Int,
    val bValues: List<Float>,
    val r: Float,
    val insight: String
)

data class TrendsUiState(
    val todayScore: Int = 0,
    val weekAvg: Int = 0,
    val monthAvg: Int = 0,
    val last14Days: List<SenseOfDaySnapshotEntity> = emptyList(),
    val weeklyAverages: List<Pair<String, Float>> = emptyList(),
    val avgStepsProgress: Float = 0f,
    val avgSleepProgress: Float = 0f,
    val avgExerciseProgress: Float = 0f,
    val avgFocusProgress: Float = 0f,
    val avgMoodProgress: Float = 0f,
    val correlations: List<CorrelationData> = emptyList()
)

class SenseOfDayTrendsViewModel(
    private val repository: SenseOfDayHistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrendsUiState())
    val uiState: StateFlow<TrendsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getLast14DaysFlow().collect { snapshots ->
                val (todayScore, weekAvg, monthAvg) = repository.getStats()
                val weeklyAverages = repository.getWeeklyAverages(8)

                val avg = { selector: (SenseOfDaySnapshotEntity) -> Float ->
                    if (snapshots.isNotEmpty()) snapshots.map(selector).average().toFloat() else 0f
                }
                val moodSnapshots = snapshots.filter { it.moodProgress >= 0f }
                val avgMood = if (moodSnapshots.isNotEmpty()) moodSnapshots.map { it.moodProgress }.average().toFloat() else 0f

                val sorted = snapshots.sortedBy { it.date }
                _uiState.value = TrendsUiState(
                    todayScore = todayScore,
                    weekAvg = weekAvg,
                    monthAvg = monthAvg,
                    last14Days = sorted,
                    weeklyAverages = weeklyAverages,
                    avgStepsProgress = avg { it.stepsProgress },
                    avgSleepProgress = avg { it.sleepProgress },
                    avgExerciseProgress = avg { it.exerciseProgress },
                    avgFocusProgress = avg { it.focusProgress },
                    avgMoodProgress = avgMood,
                    correlations = buildCorrelations(sorted)
                )
            }
        }
    }

    private fun buildCorrelations(sorted: List<SenseOfDaySnapshotEntity>) =
        CorrelationEngine.buildCorrelations(sorted)
}
