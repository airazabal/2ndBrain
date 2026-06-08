package com.alex.a2ndbrain.core.agents

import com.alex.a2ndbrain.TimelineEvent
import com.alex.a2ndbrain.core.exercise.ExerciseSessionEntity
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.meditation.MeditationSession
import com.alex.a2ndbrain.core.meditation.StreakResult
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.habits.HabitCompletionEntity
import com.alex.a2ndbrain.core.habits.HabitEntity
import com.alex.a2ndbrain.core.mood.MoodLogEntity

/**
 * Immutable snapshot of all personal data for one inference call.
 * Built once per session by OrchestratorAgent, shared across all agents.
 * Never holds DAOs, Managers, or Contexts — data only.
 */
data class BrainContext(
    val memories: List<MemoryEntity> = emptyList(),
    val health: HealthContext = HealthContext(),
    val usageStats: List<UsageStatEntity> = emptyList(),
    val meditation: MeditationContext = MeditationContext(),
    val exercise: ExerciseContext = ExerciseContext(),
    val mood: MoodContext = MoodContext(),
    val habits: HabitContext = HabitContext(),
    val drift: DriftContext = DriftContext(),
    val vaultNoteLines: List<String> = emptyList(),
    val timelineEvents: List<TimelineEvent> = emptyList(),
    val tomorrowEvents: List<TimelineEvent> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class HealthContext(
    val metrics: HealthMetrics = HealthMetrics(),
    val isAvailable: Boolean = false,
    val weeklyTrends: List<Pair<String, HealthMetrics>> = emptyList()
)

data class MeditationContext(
    val sessions: List<MeditationSession> = emptyList(),
    val streaks: StreakResult = StreakResult(0, 0, 0),
    val meditatedToday: Boolean = false
)

data class ExerciseContext(
    val recentSessions: List<ExerciseSessionEntity> = emptyList()
)

data class MoodContext(
    val todayLogs: List<MoodLogEntity> = emptyList(),
    val recentLogs: List<MoodLogEntity> = emptyList()   // last 7 days
)

data class HabitContext(
    val todayHabits: List<HabitEntity> = emptyList(),
    val completedTodayIds: Set<String> = emptySet(),
    val recentCompletions: List<HabitCompletionEntity> = emptyList()   // last 7 days
)

data class PillarAverages(
    val steps: Float = 0f,
    val sleep: Float = 0f,
    val exercise: Float = 0f,
    val focus: Float = 0f,
    val overall: Float = 0f
)

data class DriftContext(
    val currentWeek: PillarAverages = PillarAverages(),
    val fourWeekRolling: PillarAverages = PillarAverages(),
    val hasEnoughData: Boolean = false
) {
    /** Returns pillars that are >20% below the 4-week rolling average, with their drop %. */
    fun driftedPillars(threshold: Float = 0.20f): List<Pair<String, Int>> {
        if (!hasEnoughData) return emptyList()
        val result = mutableListOf<Pair<String, Int>>()
        fun check(label: String, current: Float, baseline: Float) {
            if (baseline > 0f && current < baseline * (1f - threshold)) {
                result += label to ((baseline - current) / baseline * 100).toInt()
            }
        }
        check("Steps", currentWeek.steps, fourWeekRolling.steps)
        check("Sleep", currentWeek.sleep, fourWeekRolling.sleep)
        check("Exercise", currentWeek.exercise, fourWeekRolling.exercise)
        check("Focus", currentWeek.focus, fourWeekRolling.focus)
        return result
    }
}
