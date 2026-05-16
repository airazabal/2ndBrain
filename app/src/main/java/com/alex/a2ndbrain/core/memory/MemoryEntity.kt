package com.alex.a2ndbrain.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String, // e.g., "notification", "clipboard", "voice"
    val packageName: String?, // for notifications
    val title: String?,
    val content: String,
    val deepLink: String? = null, // URI to jump to specific content
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val duplicateCount: Int = 1
)
