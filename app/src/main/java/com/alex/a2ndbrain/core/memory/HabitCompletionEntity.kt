package com.alex.a2ndbrain.core.memory

import androidx.room.Entity

@Entity(
    tableName = "habit_completions",
    primaryKeys = ["habitId", "date"]
)
data class HabitCompletionEntity(
    val habitId: String,
    val date: String, // e.g. "2026-05-18"
    val completedAt: Long = System.currentTimeMillis()
)
