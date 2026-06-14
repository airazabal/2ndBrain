package com.alex.a2ndbrain.core.goals

data class Goal(
    val id: String,
    val title: String,
    val type: GoalType,
    val targetValue: Float,
    val periodDays: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val linkedHabitId: String?,
    val linkedExerciseType: String?
)

fun GoalEntity.toDomain() = Goal(
    id = id,
    title = title,
    type = goalType,
    targetValue = targetValue,
    periodDays = periodDays,
    isActive = isActive == 1,
    createdAt = createdAt,
    linkedHabitId = linkedHabitId,
    linkedExerciseType = linkedExerciseType
)

fun Goal.toEntity() = GoalEntity(
    id = id,
    title = title,
    type = type.name,
    targetValue = targetValue,
    periodDays = periodDays,
    isActive = if (isActive) 1 else 0,
    createdAt = createdAt,
    linkedHabitId = linkedHabitId,
    linkedExerciseType = linkedExerciseType
)
