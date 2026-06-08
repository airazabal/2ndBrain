package com.alex.a2ndbrain.fakes

import com.alex.a2ndbrain.core.todoist.SplitTasks
import com.alex.a2ndbrain.core.todoist.TodoistRepository
import com.alex.a2ndbrain.core.todoist.TodoistTask

class FakeTodoistRepository : TodoistRepository {

    var todayTasks: List<TodoistTask> = emptyList()
    var overdueTasks: List<TodoistTask> = emptyList()
    var closeTaskResult: Boolean = true
    val closedTaskIds = mutableListOf<String>()

    override suspend fun fetchTodayAndOverdue(): SplitTasks =
        SplitTasks(todayTasks, overdueTasks)

    override suspend fun getTodayTasks(): List<TodoistTask> = todayTasks

    override suspend fun getOverdueTasks(): List<TodoistTask> = overdueTasks

    override suspend fun closeTask(taskId: String): Boolean {
        if (closeTaskResult) closedTaskIds += taskId
        return closeTaskResult
    }
}
