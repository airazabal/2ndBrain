package com.alex.a2ndbrain.core.goals

enum class GoalTrend { AHEAD, ON_TRACK, BEHIND, CRITICAL }

data class GoalProgress(
    val goal: GoalEntity,
    val currentValue: Float,
    val progressFraction: Float,
    val trend: GoalTrend,
    val displayCurrent: String,
    val displayTarget: String
)
