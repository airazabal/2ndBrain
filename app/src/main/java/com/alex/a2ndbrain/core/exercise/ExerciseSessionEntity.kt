package com.alex.a2ndbrain.core.exercise

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ExerciseType(val displayName: String) {
    WALKING("Walking"),
    RUNNING("Running"),
    CYCLING("Cycling"),
    SWIMMING("Swimming"),
    STRENGTH("Strength"),
    STRETCHING("Stretching"),
    HIIT("HIIT"),
    OTHER("Other")
}

@Entity(tableName = "exercise_sessions")
data class ExerciseSessionEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val type: String,               // ExerciseType.name
    val durationMinutes: Int,
    val startedAt: Long = 0L,      // epoch ms; 0 = not provided, use createdAt
    val notes: String = "",
    val date: String,               // "yyyy-MM-dd" — denormalized for fast range scans
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Int = 0          // soft delete for future P2P sync tombstones
)
