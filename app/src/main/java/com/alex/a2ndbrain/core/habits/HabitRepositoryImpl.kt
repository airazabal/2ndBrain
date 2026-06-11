package com.alex.a2ndbrain.core.habits

import com.alex.a2ndbrain.core.memory.MemoryRepository
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class HabitRepositoryImpl(
    private val dao: HabitsDao,
    private val memoryRepository: MemoryRepository
) : HabitRepository {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    override fun getAllActiveHabitsFlow() = dao.getAllActiveHabitsFlow()
    override fun getTodayHabitsFlow() = dao.getTodayHabitsFlow()
    override fun getCompletionsForDateFlow(date: String) = dao.getCompletionsForDateFlow(date)
    override fun getCompletionsSinceFlow(sinceDate: String) = dao.getCompletionsSinceFlow(sinceDate)

    override suspend fun addHabit(name: String, emoji: String, timeString: String, repeatRule: String?) {
        dao.upsertHabit(
            HabitEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                emoji = emoji,
                timeString = timeString,
                repeatRule = repeatRule
            )
        )
    }

    override suspend fun updateHabit(id: String, name: String, emoji: String, timeString: String, repeatRule: String?) {
        val existing = dao.getById(id)
        dao.upsertHabit(
            HabitEntity(
                id = id,
                name = name,
                emoji = emoji,
                timeString = timeString,
                repeatRule = repeatRule,
                isActive = existing?.isActive ?: true,
                createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                lastModifiedAt = System.currentTimeMillis(),
                isDeleted = existing?.isDeleted ?: false,
                todoistTaskId = existing?.todoistTaskId
            )
        )
    }

    override suspend fun deleteHabit(id: String) {
        dao.softDelete(id, System.currentTimeMillis())
    }

    override suspend fun markComplete(habitId: String, date: String) {
        dao.upsertCompletion(HabitCompletionEntity(habitId = habitId, date = date))
        val habit = dao.getById(habitId)
        if (habit != null) {
            memoryRepository.insertEpisodicEvent(
                content   = "${habit.emoji} ${habit.name} completed on $date",
                sourceTag = "habit"
            )
        }
    }

    override suspend fun markIncomplete(habitId: String, date: String) {
        dao.deleteCompletion(habitId, date)
    }

    override suspend fun getStreakForHabit(habitId: String): Int {
        val today = dateFmt.format(Date())
        // Look back up to 90 days to find the streak
        val since = dateFmt.format(Date(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000))
        val completions = dao.getCompletionsForHabitSince(habitId, since)
            .map { it.date }
            .toSet()

        var streak = 0
        val cal = Calendar.getInstance()
        // Start from today and count back consecutive days
        while (true) {
            val dateStr = dateFmt.format(cal.time)
            if (dateStr !in completions) {
                // Allow today to be incomplete without breaking streak
                if (dateStr == today) {
                    cal.add(Calendar.DAY_OF_YEAR, -1)
                    continue
                }
                break
            }
            streak++
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return streak
    }

    override suspend fun getWeeklyCompletionRate(habitId: String): Float {
        val since = dateFmt.format(Date(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000))
        val completions = dao.getCompletionsForHabitSince(habitId, since)
        return (completions.size / 7f).coerceIn(0f, 1f)
    }

    override suspend fun getTodayCompletions(date: String) = dao.getCompletionsForDate(date)

    override suspend fun getRecentCompletions(sinceDate: String) = dao.getAllCompletionsSince(sinceDate)

    override suspend fun updateTodoistTaskId(id: String, todoistTaskId: String) {
        dao.updateTodoistTaskId(id, todoistTaskId, System.currentTimeMillis())
    }

    override suspend fun getHabitsWithoutTodoistId() = dao.getHabitsWithoutTodoistId()

    override suspend fun getByTodoistTaskId(todoistTaskId: String) = dao.getByTodoistTaskId(todoistTaskId)

    override suspend fun getAllActiveHabitsList() = dao.getAllActiveHabitsList()

    override suspend fun getById(id: String) = dao.getById(id)
    override suspend fun findDeletedByName(name: String) = dao.findDeletedByName(name)
    override suspend fun restore(id: String, todoistTaskId: String) {
        dao.restore(id, todoistTaskId, System.currentTimeMillis())
    }
}
