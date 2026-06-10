package com.alex.a2ndbrain.fakes

import com.alex.a2ndbrain.core.habits.HabitCompletionEntity
import com.alex.a2ndbrain.core.habits.HabitEntity
import com.alex.a2ndbrain.core.habits.HabitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeHabitRepository : HabitRepository {

    val habits = MutableStateFlow<List<HabitEntity>>(emptyList())
    val completions = MutableStateFlow<List<HabitCompletionEntity>>(emptyList())

    override fun getAllActiveHabitsFlow(): Flow<List<HabitEntity>> = habits
    override fun getTodayHabitsFlow(): Flow<List<HabitEntity>> = habits
    override fun getCompletionsForDateFlow(date: String): Flow<List<HabitCompletionEntity>> =
        MutableStateFlow(completions.value.filter { it.date == date })
    override fun getCompletionsSinceFlow(sinceDate: String): Flow<List<HabitCompletionEntity>> =
        completions.map { list -> list.filter { it.date >= sinceDate } }

    override suspend fun addHabit(name: String, emoji: String, timeString: String, repeatRule: String?) {}
    override suspend fun updateHabit(id: String, name: String, emoji: String, timeString: String, repeatRule: String?) {}
    override suspend fun deleteHabit(id: String) {}
    override suspend fun markComplete(habitId: String, date: String) {
        completions.value = completions.value + HabitCompletionEntity(habitId, date)
    }
    override suspend fun markIncomplete(habitId: String, date: String) {
        completions.value = completions.value.filter { !(it.habitId == habitId && it.date == date) }
    }
    override suspend fun getStreakForHabit(habitId: String): Int = 0
    override suspend fun getWeeklyCompletionRate(habitId: String): Float = 0f
    override suspend fun getTodayCompletions(date: String): List<HabitCompletionEntity> =
        completions.value.filter { it.date == date }
    override suspend fun getRecentCompletions(sinceDate: String): List<HabitCompletionEntity> =
        completions.value.filter { it.date >= sinceDate }
    override suspend fun updateTodoistTaskId(id: String, todoistTaskId: String) {}
    override suspend fun getHabitsWithoutTodoistId(): List<HabitEntity> = emptyList()
    override suspend fun getByTodoistTaskId(todoistTaskId: String): HabitEntity? = null
    override suspend fun getAllActiveHabitsList(): List<HabitEntity> = habits.value
    override suspend fun getById(id: String): HabitEntity? = habits.value.find { it.id == id }
    override suspend fun findDeletedByName(name: String): HabitEntity? = null
    override suspend fun restore(id: String, todoistTaskId: String) {}

    fun seedCompletions(vararg c: HabitCompletionEntity) {
        completions.value = c.toList()
    }
}
