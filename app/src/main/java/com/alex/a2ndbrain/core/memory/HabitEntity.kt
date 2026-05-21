package com.alex.a2ndbrain.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val timeString: String,         // "08:00" 24h format
    val isMedication: Boolean,
    val isActive: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastModifiedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false  // soft delete — keeps record for sync tombstone propagation
)
