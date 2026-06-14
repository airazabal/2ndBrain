package com.alex.a2ndbrain.core.exercise

data class ExerciseSession(
    val id: String,
    val deviceId: String,
    val type: ExerciseType,
    val durationMinutes: Int,
    val startedAt: Long = 0L,
    val notes: String = "",
    val date: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

fun ExerciseSessionEntity.toDomain() = ExerciseSession(
    id = id,
    deviceId = deviceId,
    type = runCatching { ExerciseType.valueOf(type) }.getOrDefault(ExerciseType.OTHER),
    durationMinutes = durationMinutes,
    startedAt = startedAt,
    notes = notes,
    date = date,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = isDeleted != 0
)

fun ExerciseSession.toEntity() = ExerciseSessionEntity(
    id = id,
    deviceId = deviceId,
    type = type.name,
    durationMinutes = durationMinutes,
    startedAt = startedAt,
    notes = notes,
    date = date,
    createdAt = createdAt,
    lastModifiedAt = lastModifiedAt,
    isDeleted = if (isDeleted) 1 else 0
)
