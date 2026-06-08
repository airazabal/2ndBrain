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
    val vaultNoteLines: List<String> = emptyList(),
    val timelineEvents: List<TimelineEvent> = emptyList(),
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
