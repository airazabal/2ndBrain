package com.alex.a2ndbrain.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_summaries")
data class DailySummaryEntity(
    @PrimaryKey val date: String, // format: YYYY-MM-DD
    val summary: String,
    val timestamp: Long = System.currentTimeMillis()
)