package com.alex.a2ndbrain.core.health

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "health_snapshots")
data class HealthSnapshotEntity(
    @PrimaryKey val date: String,      // "yyyy-MM-dd"
    val deviceId: String,
    val steps: Long = 0,
    val sleepMinutes: Int = 0,
    val minHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val avgHeartRate: Int = 0,
    val lastTimestamp: Long = System.currentTimeMillis()
) {
    fun toHealthMetrics() = HealthMetrics(
        steps = steps,
        sleepMinutes = sleepMinutes,
        minHeartRate = minHeartRate,
        maxHeartRate = maxHeartRate,
        avgHeartRate = avgHeartRate
    )
}
