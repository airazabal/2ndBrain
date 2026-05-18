package com.alex.a2ndbrain.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String, // e.g. UUID.randomUUID().toString()
    val name: String,
    val timeString: String, // e.g. "08:00" in 24h format
    val isMedication: Boolean,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)
