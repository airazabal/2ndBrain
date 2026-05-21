package com.alex.a2ndbrain.core.agents

import com.alex.a2ndbrain.TimelineEvent
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.meditation.MeditationSession
import com.alex.a2ndbrain.core.meditation.StreakResult
import com.alex.a2ndbrain.core.memory.HabitEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.UsageStatEntity

/**
 * Immutable snapshot of all personal data for one inference call.
 * Built once per session by OrchestratorAgent, shared across all agents.
 * Never holds DAOs, Managers, or Contexts — data only.
 */
data class BrainContext(
    val memories: List<MemoryEntity> = emptyList(),
    val health: HealthContext = HealthContext(),
    val usageStats: List<UsageStatEntity> = emptyList(),
    val habits: HabitsContext = HabitsContext(),
    val meditation: MeditationContext = MeditationContext(),
    val vaultNoteLines: List<String> = emptyList(),
    val timelineEvents: List<TimelineEvent> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

data class HealthContext(
    val metrics: HealthMetrics = HealthMetrics(),
    val isAvailable: Boolean = false,
    val weeklyTrends: List<Pair<String, HealthMetrics>> = emptyList()
)

data class HabitsContext(
    val activeHabits: List<HabitEntity> = emptyList(),
    val completedHabitIds: Set<String> = emptySet(),
    val todayDateString: String = ""
)

data class MeditationContext(
    val sessions: List<MeditationSession> = emptyList(),
    val streaks: StreakResult = StreakResult(0, 0, 0),
    val meditatedToday: Boolean = false
)
