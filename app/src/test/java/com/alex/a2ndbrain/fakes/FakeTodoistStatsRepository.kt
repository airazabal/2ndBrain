package com.alex.a2ndbrain.fakes

import com.alex.a2ndbrain.core.todoist.TodoistCompletion
import com.alex.a2ndbrain.core.todoist.TodoistStatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeTodoistStatsRepository : TodoistStatsRepository {

    private val completions = MutableStateFlow<List<TodoistCompletion>>(emptyList())
    val savedCompletions = mutableListOf<Pair<String, String>>()

    override fun getAllCompletionsFlow(): Flow<List<TodoistCompletion>> = completions

    override fun getWeeklyActivity(): Flow<List<Pair<String, Int>>> =
        MutableStateFlow(emptyList())

    override suspend fun saveCompletion(taskId: String, taskContent: String) {
        savedCompletions += taskId to taskContent
    }

    override suspend fun getTodayCount(): Int = 0
    override suspend fun getWeeklyCount(): Int = 0
    override suspend fun getTotalCount(): Int = savedCompletions.size

    override suspend fun getAllCompletions(): List<TodoistCompletion> = completions.value

    override suspend fun insertCompletion(completion: TodoistCompletion) {
        completions.value = completions.value + completion
    }
}
