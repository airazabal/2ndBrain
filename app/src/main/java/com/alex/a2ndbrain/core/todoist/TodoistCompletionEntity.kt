package com.alex.a2ndbrain.core.todoist

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "todoist_completions")
data class TodoistCompletionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val taskContent: String,
    val completedAt: Long,
    val date: String,   // "yyyy-MM-dd"
    val status: String = STATUS_COMPLETED
) {
    companion object {
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_MISSED    = "MISSED"
    }
}
