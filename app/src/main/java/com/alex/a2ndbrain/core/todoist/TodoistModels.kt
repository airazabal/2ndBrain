package com.alex.a2ndbrain.core.todoist

data class TodoistTask(
    val id: String,
    val content: String,
    val description: String,
    val priority: Int,
    val dueDateStr: String?,
    val deadlineDateStr: String?,
    val url: String
)

data class SplitTasks(val today: List<TodoistTask>, val overdue: List<TodoistTask>)
