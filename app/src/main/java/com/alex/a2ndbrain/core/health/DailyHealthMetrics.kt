package com.alex.a2ndbrain.core.health

data class DailyHealthMetrics(
    val date: String,           // "yyyy-MM-dd"
    val steps: Long = 0,
    val sleepMinutes: Int = 0,
    val minHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val avgHeartRate: Int = 0
) {
    val sleepHours: Int get() = sleepMinutes / 60
    val sleepMins: Int  get() = sleepMinutes % 60
    val hasSteps: Boolean  get() = steps > 0
    val hasSleep: Boolean  get() = sleepMinutes > 0
    val hasHeart: Boolean  get() = avgHeartRate > 0
}

fun HealthSnapshotEntity.toDailyMetrics() = DailyHealthMetrics(
    date          = date,
    steps         = steps,
    sleepMinutes  = sleepMinutes,
    minHeartRate  = minHeartRate,
    maxHeartRate  = maxHeartRate,
    avgHeartRate  = avgHeartRate
)
