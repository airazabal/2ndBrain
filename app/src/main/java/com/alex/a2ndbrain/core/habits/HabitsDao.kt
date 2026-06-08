package com.alex.a2ndbrain.core.habits

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHabit(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCompletion(completion: HabitCompletionEntity)

    @Query("DELETE FROM habit_completions WHERE habitId = :habitId AND date = :date")
    suspend fun deleteCompletion(habitId: String, date: String)

    @Query("UPDATE habits SET isDeleted = 1, lastModifiedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("UPDATE habits SET isDeleted = 0, isActive = 1, todoistTaskId = :todoistTaskId, lastModifiedAt = :now WHERE id = :id")
    suspend fun restore(id: String, todoistTaskId: String, now: Long)

    @Query("SELECT * FROM habits WHERE name = :name AND isDeleted = 1 ORDER BY lastModifiedAt DESC LIMIT 1")
    suspend fun findDeletedByName(name: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE todoistTaskId = :todoistTaskId LIMIT 1")
    suspend fun getByTodoistTaskIdIncludingDeleted(todoistTaskId: String): HabitEntity?

    @Query("UPDATE habits SET todoistTaskId = :todoistTaskId, lastModifiedAt = :now WHERE id = :id")
    suspend fun updateTodoistTaskId(id: String, todoistTaskId: String, now: Long)

    @Query("SELECT * FROM habits WHERE isDeleted = 0 AND todoistTaskId IS NULL AND isActive = 1")
    suspend fun getHabitsWithoutTodoistId(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE todoistTaskId = :todoistTaskId AND isDeleted = 0 LIMIT 1")
    suspend fun getByTodoistTaskId(todoistTaskId: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE isDeleted = 0 AND isActive = 1 ORDER BY createdAt ASC")
    suspend fun getAllActiveHabitsList(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getById(id: String): HabitEntity?

    @Query("SELECT * FROM habits WHERE isDeleted = 0 ORDER BY createdAt ASC")
    fun getAllActiveHabitsFlow(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE isActive = 1 AND isDeleted = 0 ORDER BY timeString ASC, createdAt ASC")
    fun getTodayHabitsFlow(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habit_completions WHERE date = :date")
    fun getCompletionsForDateFlow(date: String): Flow<List<HabitCompletionEntity>>

    @Query("SELECT * FROM habit_completions WHERE date = :date")
    suspend fun getCompletionsForDate(date: String): List<HabitCompletionEntity>

    @Query("SELECT * FROM habit_completions WHERE habitId = :habitId AND date >= :sinceDate ORDER BY date DESC")
    suspend fun getCompletionsForHabitSince(habitId: String, sinceDate: String): List<HabitCompletionEntity>

    @Query("SELECT * FROM habit_completions WHERE date >= :sinceDate ORDER BY date DESC")
    suspend fun getAllCompletionsSince(sinceDate: String): List<HabitCompletionEntity>
}
