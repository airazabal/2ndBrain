package com.alex.a2ndbrain.core.mood

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "mood_logs")
data class MoodLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,       // "yyyy-MM-dd"
    val timestamp: Long,
    val mood: Int,          // 1–5
    val energy: Int,        // 1–5
    val note: String = ""
)
