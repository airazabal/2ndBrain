package com.alex.a2ndbrain.core.todoist

data class TodoistCompletion(
    val id: String,
    val taskId: String,
    val taskContent: String,
    val completedAt: Long,
    val date: String,
    val status: String = TodoistCompletionEntity.STATUS_COMPLETED
) {
    val isMissed get() = status == TodoistCompletionEntity.STATUS_MISSED
}

fun TodoistCompletionEntity.toDomain() = TodoistCompletion(
    id = id, taskId = taskId, taskContent = taskContent,
    completedAt = completedAt, date = date, status = status
)

fun TodoistCompletion.toEntity() = TodoistCompletionEntity(
    id = id, taskId = taskId, taskContent = taskContent,
    completedAt = completedAt, date = date, status = status
)
