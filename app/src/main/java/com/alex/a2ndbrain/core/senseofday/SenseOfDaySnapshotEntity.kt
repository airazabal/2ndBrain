package com.alex.a2ndbrain.core.senseofday

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sense_of_day_snapshots")
data class SenseOfDaySnapshotEntity(
    @PrimaryKey val date: String,
    val score: Int,
    val stepsProgress: Float,
    val sleepProgress: Float,
    val exerciseProgress: Float,
    val focusProgress: Float,
    val savedAt: Long
)
