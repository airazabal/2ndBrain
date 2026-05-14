package com.alex.a2ndbrain.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String, // format: YYYY-MM-DD
    val type: String = "reflection", // "briefing" or "reflection"
    val summary: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelName: String? = null
)
