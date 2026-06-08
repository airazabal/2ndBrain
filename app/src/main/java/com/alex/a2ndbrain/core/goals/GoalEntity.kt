package com.alex.a2ndbrain.core.goals

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class GoalType {
    EXERCISE_SESSIONS,
    HABIT_COMPLETION,
    STEPS_DAILY,
    SLEEP_DAILY
}

@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val type: String,                       // GoalType.name
    val targetValue: Float,
    val periodDays: Int = 7,
    val isActive: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val linkedHabitId: String? = null,
    val linkedExerciseType: String? = null
) {
    val goalType: GoalType get() = GoalType.valueOf(type)
}
