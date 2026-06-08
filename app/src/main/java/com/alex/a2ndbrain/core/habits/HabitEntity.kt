package com.alex.a2ndbrain.core.habits

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val emoji: String = "✅",
    val timeString: String = "",           // "HH:mm" or empty for no specific time
    val repeatRule: String? = null,        // Todoist due_string recurrence, e.g. "every day"
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val todoistTaskId: String? = null      // null = app-only; non-null = synced to Todoist
)
