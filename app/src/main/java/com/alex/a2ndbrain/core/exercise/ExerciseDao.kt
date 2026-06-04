package com.alex.a2ndbrain.core.exercise

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: ExerciseSessionEntity)

    @Query("UPDATE exercise_sessions SET isDeleted = 1, lastModifiedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long = System.currentTimeMillis())

    @Query("""
        SELECT * FROM exercise_sessions
        WHERE isDeleted = 0
        ORDER BY date DESC, createdAt DESC
    """)
    fun getAllSessionsFlow(): Flow<List<ExerciseSessionEntity>>

    @Query("""
        SELECT * FROM exercise_sessions
        WHERE date >= :sinceDate AND isDeleted = 0
        ORDER BY date DESC, createdAt DESC
    """)
    fun getSessionsSince(sinceDate: String): Flow<List<ExerciseSessionEntity>>

    @Query("""
        SELECT * FROM exercise_sessions
        WHERE date >= :sinceDate AND isDeleted = 0
        ORDER BY date DESC
    """)
    suspend fun getSessionsSinceSync(sinceDate: String): List<ExerciseSessionEntity>

    @Query("""
        SELECT * FROM exercise_sessions
        WHERE lastModifiedAt > :since
        ORDER BY lastModifiedAt DESC
    """)
    suspend fun getModifiedSince(since: Long): List<ExerciseSessionEntity>

    @Query("SELECT * FROM exercise_sessions WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ExerciseSessionEntity?

    @Query("UPDATE exercise_sessions SET type = :type, durationMinutes = :durationMinutes, notes = :notes, lastModifiedAt = :now WHERE id = :id")
    suspend fun updateSession(id: String, type: String, durationMinutes: Int, notes: String, now: Long = System.currentTimeMillis())
}
