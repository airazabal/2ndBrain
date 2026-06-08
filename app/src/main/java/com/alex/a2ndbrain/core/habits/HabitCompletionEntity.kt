package com.alex.a2ndbrain.core.habits

import androidx.room.Entity

@Entity(tableName = "habit_completions", primaryKeys = ["habitId", "date"])
data class HabitCompletionEntity(
    val habitId: String,
    val date: String,           // "yyyy-MM-dd"
    val completedAt: Long = System.currentTimeMillis()
)
