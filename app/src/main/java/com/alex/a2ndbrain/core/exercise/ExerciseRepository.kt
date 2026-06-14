package com.alex.a2ndbrain.core.exercise

import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    suspend fun logSession(type: ExerciseType, durationMinutes: Int, startedAt: Long = 0L, notes: String = "")
    suspend fun deleteSession(id: String)
    suspend fun updateSession(id: String, type: ExerciseType, durationMinutes: Int, notes: String)
    fun getAllSessionsFlow(): Flow<List<ExerciseSession>>
    fun getWeeklyConsistency(): Flow<List<Pair<String, Float>>>
    suspend fun getWeeklySummary(): Pair<Int, Int>
    suspend fun getTodaySummary(): Pair<Int, Int>
    suspend fun getTotalSessionCount(): Int
    suspend fun getRecentSessions(daysBack: Int): List<ExerciseSession>
    suspend fun getModifiedSince(since: Long): List<ExerciseSession>
    suspend fun getById(id: String): ExerciseSession?
    suspend fun upsert(session: ExerciseSession)
}
