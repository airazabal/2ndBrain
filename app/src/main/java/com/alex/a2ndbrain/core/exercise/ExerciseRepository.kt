package com.alex.a2ndbrain.core.exercise

import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    suspend fun logSession(type: ExerciseType, durationMinutes: Int, startedAt: Long = 0L, notes: String = "")
    suspend fun deleteSession(id: String)
    suspend fun updateSession(id: String, type: ExerciseType, durationMinutes: Int, notes: String)
    fun getAllSessionsFlow(): Flow<List<ExerciseSessionEntity>>
    fun getWeeklyConsistency(): Flow<List<Pair<String, Float>>>
    suspend fun getWeeklySummary(): Pair<Int, Int>
    suspend fun getTodaySummary(): Pair<Int, Int>
    suspend fun getTotalSessionCount(): Int
    suspend fun getRecentSessions(daysBack: Int): List<ExerciseSessionEntity>
    suspend fun getModifiedSince(since: Long): List<ExerciseSessionEntity>
    suspend fun getById(id: String): ExerciseSessionEntity?
    suspend fun upsert(session: ExerciseSessionEntity)
}
