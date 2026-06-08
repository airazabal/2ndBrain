package com.alex.a2ndbrain.core.habits

import kotlinx.coroutines.flow.Flow

interface HabitRepository {
    fun getAllActiveHabitsFlow(): Flow<List<HabitEntity>>
    fun getTodayHabitsFlow(): Flow<List<HabitEntity>>
    fun getCompletionsForDateFlow(date: String): Flow<List<HabitCompletionEntity>>
    suspend fun addHabit(name: String, emoji: String, timeString: String, repeatRule: String? = null)
    suspend fun updateHabit(id: String, name: String, emoji: String, timeString: String, repeatRule: String? = null)
    suspend fun deleteHabit(id: String)
    suspend fun markComplete(habitId: String, date: String)
    suspend fun markIncomplete(habitId: String, date: String)
    suspend fun getStreakForHabit(habitId: String): Int
    suspend fun getWeeklyCompletionRate(habitId: String): Float
    suspend fun getTodayCompletions(date: String): List<HabitCompletionEntity>
    suspend fun getRecentCompletions(sinceDate: String): List<HabitCompletionEntity>
    suspend fun updateTodoistTaskId(id: String, todoistTaskId: String)
    suspend fun getHabitsWithoutTodoistId(): List<HabitEntity>
    suspend fun getByTodoistTaskId(todoistTaskId: String): HabitEntity?
    suspend fun getAllActiveHabitsList(): List<HabitEntity>
    suspend fun getById(id: String): HabitEntity?
}
