package com.alex.a2ndbrain.core.todoist

interface TodoistRepository {
    suspend fun fetchTodayAndOverdue(): SplitTasks
    suspend fun getTodayTasks(): List<TodoistTask>
    suspend fun getOverdueTasks(): List<TodoistTask>
    suspend fun closeTask(taskId: String): Boolean
}
