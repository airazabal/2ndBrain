package com.alex.a2ndbrain.core.todoist

import com.alex.a2ndbrain.core.memory.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class TodoistStatsRepositoryImpl(
    private val dao: TodoistDao,
    private val memoryRepository: MemoryRepository
) : TodoistStatsRepository {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private fun sinceDate(daysBack: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -daysBack)
        return dateFormat.format(cal.time)
    }

    override fun getAllCompletionsFlow(): Flow<List<TodoistCompletionEntity>> =
        dao.getAllCompletionsFlow()

    override fun getWeeklyActivity(): Flow<List<Pair<String, Int>>> {
        val since = sinceDate(6)
        return dao.getCompletionsSince(since).map { completions ->
            (0..6).map { offset ->
                val cal = Calendar.getInstance()
                cal.add(Calendar.DAY_OF_YEAR, -(6 - offset))
                val dateStr = dateFormat.format(cal.time)
                val label = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> "Mon"
                    Calendar.TUESDAY -> "Tue"
                    Calendar.WEDNESDAY -> "Wed"
                    Calendar.THURSDAY -> "Thu"
                    Calendar.FRIDAY -> "Fri"
                    Calendar.SATURDAY -> "Sat"
                    else -> "Sun"
                }
                label to completions.count { it.date == dateStr }
            }
        }
    }

    override suspend fun saveCompletion(taskId: String, taskContent: String) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        dao.insert(
            TodoistCompletionEntity(
                id = UUID.randomUUID().toString(),
                taskId = taskId,
                taskContent = taskContent,
                completedAt = now,
                date = dateFormat.format(Date(now))
            )
        )
        try { memoryRepository.insertEpisodicEvent("Completed task: $taskContent", "task") }
        catch (e: Exception) { /* non-fatal */ }
    }

    override suspend fun getTodayCount(): Int = withContext(Dispatchers.IO) {
        dao.getCountForDate(dateFormat.format(Date()))
    }

    override suspend fun getWeeklyCount(): Int = withContext(Dispatchers.IO) {
        dao.getCountSince(sinceDate(6))
    }

    override suspend fun getTotalCount(): Int = withContext(Dispatchers.IO) {
        dao.getTotalCount()
    }
}
