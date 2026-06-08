package com.alex.a2ndbrain.core.mood

import kotlinx.coroutines.flow.Flow

interface MoodRepository {
    suspend fun logMood(mood: Int, energy: Int, note: String = "")
    suspend fun getLogsForDate(date: String): List<MoodLogEntity>
    fun getLogsSinceFlow(sinceDate: String): Flow<List<MoodLogEntity>>
    suspend fun getLogsSince(sinceDate: String): List<MoodLogEntity>
    suspend fun getLatest(): MoodLogEntity?
}
