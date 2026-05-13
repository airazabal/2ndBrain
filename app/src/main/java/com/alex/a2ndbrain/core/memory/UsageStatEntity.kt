package com.alex.a2ndbrain.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "usage_stats")
data class UsageStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // format: YYYY-MM-DD
    val packageName: String,
    val totalTimeVisibleMs: Long,
    val lastTimestamp: Long = System.currentTimeMillis()
)
