package com.alex.a2ndbrain.core.goals

import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.habits.HabitRepository
import com.alex.a2ndbrain.core.health.HealthRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.text.SimpleDateFormat
import java.util.*

class GoalProgressCalculator(
    private val exerciseRepository: ExerciseRepository,
    private val habitRepository: HabitRepository,
    private val healthRepository: HealthRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun compute(goals: List<Goal>): List<GoalProgress> = coroutineScope {
        goals.filter { it.isActive }.map { goal ->
            async {
                try { computeSingle(goal) } catch (e: Exception) { null }
            }
        }.mapNotNull { it.await() }
    }

    private suspend fun computeSingle(goal: Goal): GoalProgress {
        val cutoffDate = dateFormat.format(Date(System.currentTimeMillis() - goal.periodDays * 86_400_000L))

        val (current, displayCurrent, displayTarget) = when (goal.type) {
            GoalType.EXERCISE_SESSIONS -> {
                val sessions = exerciseRepository.getRecentSessions(goal.periodDays)
                val count = sessions.count {
                    goal.linkedExerciseType == null || it.type.name.equals(goal.linkedExerciseType, ignoreCase = true)
                }.toFloat()
                val target = goal.targetValue.toInt()
                Triple(count, "${count.toInt()} sessions", "$target sessions")
            }

            GoalType.HABIT_COMPLETION -> {
                val completions = habitRepository.getRecentCompletions(cutoffDate)
                    .let { all ->
                        if (goal.linkedHabitId != null) all.filter { it.habitId == goal.linkedHabitId } else all
                    }
                val count = completions.size.toFloat()
                val target = goal.targetValue.toInt()
                Triple(count, "${count.toInt()} completions", "$target times")
            }

            GoalType.STEPS_DAILY -> {
                val (metrics, _) = healthRepository.getPeriodMetrics(goal.periodDays)
                val days = metrics.filter { it.hasSteps }
                val avg = if (days.isEmpty()) 0f else days.sumOf { it.steps }.toFloat() / days.size
                val target = goal.targetValue.toInt()
                Triple(avg, "%,d avg".format(avg.toInt()), "%,d steps/day".format(target))
            }

            GoalType.SLEEP_DAILY -> {
                val (metrics, _) = healthRepository.getPeriodMetrics(goal.periodDays)
                val days = metrics.filter { it.hasSleep }
                val avgH = if (days.isEmpty()) 0f else days.sumOf { it.sleepMinutes }.toFloat() / days.size / 60f
                Triple(avgH, "%.1fh avg".format(avgH), "%.1fh/night".format(goal.targetValue))
            }
        }

        val fraction = (current / goal.targetValue.coerceAtLeast(0.001f)).coerceIn(0f, 2f)
        val trend = when {
            fraction >= 1f   -> GoalTrend.AHEAD
            fraction >= 0.7f -> GoalTrend.ON_TRACK
            fraction >= 0.4f -> GoalTrend.BEHIND
            else             -> GoalTrend.CRITICAL
        }

        return GoalProgress(goal, current, fraction.coerceIn(0f, 1f), trend, displayCurrent, displayTarget)
    }
}
