package com.alex.a2ndbrain.core.memory

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "usage_stats",
    indices = [Index(value = ["date", "packageName", "deviceId"], unique = true)]
)
data class UsageStatEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // format: YYYY-MM-DD
    val packageName: String,
    val totalTimeVisibleMs: Long,
    val deviceId: String,
    val deviceName: String,
    val lastTimestamp: Long = System.currentTimeMillis()
)
