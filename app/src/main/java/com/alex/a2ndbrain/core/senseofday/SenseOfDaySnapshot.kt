package com.alex.a2ndbrain.core.senseofday

data class SenseOfDaySnapshot(
    val date: String,
    val score: Int,
    val stepsProgress: Float,
    val sleepProgress: Float,
    val exerciseProgress: Float,
    val focusProgress: Float,
    val savedAt: Long,
    val moodProgress: Float = -1f
)

fun SenseOfDaySnapshotEntity.toDomain() = SenseOfDaySnapshot(
    date = date,
    score = score,
    stepsProgress = stepsProgress,
    sleepProgress = sleepProgress,
    exerciseProgress = exerciseProgress,
    focusProgress = focusProgress,
    savedAt = savedAt,
    moodProgress = moodProgress
)
