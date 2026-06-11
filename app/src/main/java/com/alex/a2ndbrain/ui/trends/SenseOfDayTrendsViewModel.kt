package com.alex.a2ndbrain.ui.trends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.senseofday.SenseOfDayHistoryRepository
import com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshotEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.sqrt

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

    private fun pearson(xs: List<Float>, ys: List<Float>): Float {
        val n = xs.size
        if (n < 3) return 0f
        val mx = xs.average().toFloat()
        val my = ys.average().toFloat()
        val num = xs.zip(ys).sumOf { (x, y) -> ((x - mx) * (y - my)).toDouble() }.toFloat()
        val dx = sqrt(xs.sumOf { ((it - mx) * (it - mx)).toDouble() }.toFloat())
        val dy = sqrt(ys.sumOf { ((it - my) * (it - my)).toDouble() }.toFloat())
        return if (dx == 0f || dy == 0f) 0f else (num / (dx * dy)).coerceIn(-1f, 1f)
    }

    private fun insight(r: Float, a: String, b: String) = when {
        r >= 0.65f  -> "Strong link — high $a reliably lifts your $b"
        r >= 0.35f  -> "Moderate link — $a and $b tend to rise together"
        r <= -0.65f -> "Inverse — high $a consistently lowers $b"
        r <= -0.35f -> "Slight inverse — $a and $b pull in opposite directions"
        else        -> "No clear pattern yet — more days will sharpen this"
    }

    private fun buildCorrelations(sorted: List<SenseOfDaySnapshotEntity>): List<CorrelationData> {
        if (sorted.size < 3) return emptyList()
        val result = mutableListOf<CorrelationData>()

        // Sleep → next-day Score (1-day lag)
        val sleepVals  = sorted.dropLast(1).map { it.sleepProgress }
        val scoreVals  = sorted.drop(1).map { it.score / 100f }
        val rSleepScore = pearson(sleepVals, scoreVals)
        result += CorrelationData(
            chipLabel  = "Sleep / Score",
            chartTitle = "Sleep → Next-day Score",
            aLabel = "Sleep",  aPillarIdx = 1, aValues = sorted.map { it.sleepProgress },
            bLabel = "Score",  bPillarIdx = -1, bValues = sorted.map { it.score / 100f },
            r = rSleepScore, insight = insight(rSleepScore, "sleep", "next-day score")
        )

        // Steps × Focus (same day)
        val stepsFocusDays = sorted.filter { it.stepsProgress > 0f && it.focusProgress > 0f }
        if (stepsFocusDays.size >= 3) {
            val rSF = pearson(stepsFocusDays.map { it.stepsProgress }, stepsFocusDays.map { it.focusProgress })
            result += CorrelationData(
                chipLabel  = "Steps / Focus",
                chartTitle = "Steps × Focus",
                aLabel = "Steps", aPillarIdx = 0, aValues = sorted.map { it.stepsProgress },
                bLabel = "Focus", bPillarIdx = 3, bValues = sorted.map { it.focusProgress },
                r = rSF, insight = insight(rSF, "steps", "focus time")
            )
        }

        // Exercise × Mood (same day, mood-logged days only)
        val exMoodDays = sorted.filter { it.exerciseProgress > 0f && it.moodProgress >= 0f }
        if (exMoodDays.size >= 3) {
            val rEM = pearson(exMoodDays.map { it.exerciseProgress }, exMoodDays.map { it.moodProgress })
            result += CorrelationData(
                chipLabel  = "Exercise / Mood",
                chartTitle = "Exercise × Mood",
                aLabel = "Exercise", aPillarIdx = 2, aValues = sorted.map { it.exerciseProgress },
                bLabel = "Mood",     bPillarIdx = 4,
                bValues = sorted.map { if (it.moodProgress >= 0f) it.moodProgress else 0f },
                r = rEM, insight = insight(rEM, "exercise", "mood")
            )
        }

        return result
    }
}
