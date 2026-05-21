package com.alex.a2ndbrain.core.memory

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitsDao {
    @Query("SELECT * FROM habits WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isDeleted = 0 ORDER BY createdAt ASC")
    suspend fun getAllHabitsSync(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE isActive = 1 AND isDeleted = 0 ORDER BY createdAt ASC")
    fun getActiveHabits(): Flow<List<HabitEntity>>

    // Includes soft-deleted records so tombstones propagate to peers during sync
    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    suspend fun getAllHabitsForSync(): List<HabitEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getHabitById(id: String): HabitEntity?

    @Query("UPDATE habits SET isDeleted = 1, lastModifiedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteHabit(id: String, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    // Habit Completions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HabitCompletionEntity)

    @Delete
    suspend fun deleteCompletion(completion: HabitCompletionEntity)

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getCompletionByKey(habitId: String, date: String): HabitCompletionEntity?

    @Query("UPDATE habit_completions SET isDeleted = 1, lastModifiedAt = :timestamp WHERE habitId = :habitId AND date = :date")
    suspend fun softDeleteCompletion(habitId: String, date: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM habit_completions WHERE date = :date AND isDeleted = 0")
    fun getCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE date = :date AND isDeleted = 0")
    suspend fun getCompletionsForDateSync(date: String): List<HabitCompletionEntity>

    @Query("SELECT * FROM habit_completions WHERE date >= :startDate AND date <= :endDate AND isDeleted = 0")
    fun getCompletionsInRange(startDate: String, endDate: String): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE date >= :startDate AND date <= :endDate AND isDeleted = 0")
    suspend fun getCompletionsInRangeSync(startDate: String, endDate: String): List<HabitCompletionEntity>

    // Includes soft-deleted records so un-check tombstones propagate to peers
    @Query("SELECT * FROM habit_completions WHERE date >= :startDate")
    suspend fun getAllCompletionsForSync(startDate: String): List<HabitCompletionEntity>
}
