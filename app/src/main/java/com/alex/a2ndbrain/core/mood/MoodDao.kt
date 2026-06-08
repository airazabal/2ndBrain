package com.alex.a2ndbrain.core.mood

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MoodDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(log: MoodLogEntity)

    @Query("SELECT * FROM mood_logs WHERE date = :date ORDER BY timestamp DESC")
    suspend fun getLogsForDate(date: String): List<MoodLogEntity>

    @Query("SELECT * FROM mood_logs WHERE date >= :sinceDate ORDER BY timestamp DESC")
    fun getLogsSinceFlow(sinceDate: String): Flow<List<MoodLogEntity>>

    @Query("SELECT * FROM mood_logs WHERE date >= :sinceDate ORDER BY timestamp DESC")
    suspend fun getLogsSince(sinceDate: String): List<MoodLogEntity>

    @Query("SELECT * FROM mood_logs ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatest(): MoodLogEntity?
}
