package com.alex.a2ndbrain.core.memory

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitsDao {
    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    fun getAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY createdAt ASC")
    suspend fun getAllHabitsSync(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE isActive = 1 ORDER BY createdAt ASC")
    fun getActiveHabits(): Flow<List<HabitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHabit(habit: HabitEntity)

    @Delete
    suspend fun deleteHabit(habit: HabitEntity)

    @Query("DELETE FROM habits WHERE id = :id")
    suspend fun deleteHabitById(id: String)

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getHabitById(id: String): HabitEntity?

    // Habit Completions
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCompletion(completion: HabitCompletionEntity)

    @Delete
    suspend fun deleteCompletion(completion: HabitCompletionEntity)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCompletion(habitId: String, date: String)

    @Query("SELECT * FROM habit_completions WHERE date = :date")
    fun getCompletionsForDate(date: String): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE date = :date")
    suspend fun getCompletionsForDateSync(date: String): List<HabitCompletionEntity>

    @Query("SELECT * FROM habit_completions WHERE date >= :startDate AND date <= :endDate")
    fun getCompletionsInRange(startDate: String, endDate: String): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE date >= :startDate AND date <= :endDate")
    suspend fun getCompletionsInRangeSync(startDate: String, endDate: String): List<HabitCompletionEntity>
}
