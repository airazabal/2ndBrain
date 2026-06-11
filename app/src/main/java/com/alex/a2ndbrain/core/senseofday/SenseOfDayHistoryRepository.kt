package com.alex.a2ndbrain.core.senseofday

import kotlinx.coroutines.flow.Flow

interface SenseOfDayHistoryRepository {
    fun getLast14DaysFlow(): Flow<List<SenseOfDaySnapshotEntity>>
    suspend fun saveSnapshot(
        score: Int,
        stepsProgress: Float,
        sleepProgress: Float,
        exerciseProgress: Float,
        focusProgress: Float,
        moodProgress: Float = -1f
    )
    suspend fun getStats(): Triple<Int, Int, Int>
    suspend fun getWeeklyAverages(weeks: Int = 8): List<Pair<String, Float>>
    suspend fun getRecentSnapshots(days: Int): List<SenseOfDaySnapshotEntity>
}
