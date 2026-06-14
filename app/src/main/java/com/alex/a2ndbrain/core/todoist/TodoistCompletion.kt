package com.alex.a2ndbrain.core.todoist

data class TodoistCompletion(
    val id: String,
    val taskId: String,
    val taskContent: String,
    val completedAt: Long,
    val date: String
)

fun TodoistCompletionEntity.toDomain() = TodoistCompletion(
    id = id,
    taskId = taskId,
    taskContent = taskContent,
    completedAt = completedAt,
    date = date
)

fun TodoistCompletion.toEntity() = TodoistCompletionEntity(
    id = id,
    taskId = taskId,
    taskContent = taskContent,
    completedAt = completedAt,
    date = date
)
