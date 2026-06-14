package com.alex.a2ndbrain.core.todoist

import kotlinx.coroutines.flow.Flow

interface TodoistStatsRepository {
    fun getAllCompletionsFlow(): Flow<List<TodoistCompletion>>
    fun getWeeklyActivity(): Flow<List<Pair<String, Int>>>
    suspend fun saveCompletion(taskId: String, taskContent: String)
    suspend fun getTodayCount(): Int
    suspend fun getWeeklyCount(): Int
    suspend fun getTotalCount(): Int
    suspend fun getAllCompletions(): List<TodoistCompletion>
    suspend fun insertCompletion(completion: TodoistCompletion)
}
