package com.alex.a2ndbrain.core.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class HealthMetrics(
    val steps: Long = 0,
    val sleepMinutes: Int = 0,
    val minHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val avgHeartRate: Int = 0
)

class HealthConnectManager(private val context: Context) {

    private val healthConnectClient by lazy {
        try {
            if (isAvailable()) HealthConnectClient.getOrCreate(context) else null
        } catch (e: Exception) {
            null
        }
    }

    val permissions = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class)
    )

    fun isAvailable(): Boolean {
        return try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    suspend fun hasPermissions(): Boolean {
        val client = healthConnectClient ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(permissions)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun fetchHealthMetricsToday(): HealthMetrics {
        val client = healthConnectClient ?: return HealthMetrics()
        if (!hasPermissions()) return HealthMetrics()

        val zoneId = ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zoneId).toInstant()
        val todayEnd = Instant.now()

        // 1. Fetch Steps
        var totalSteps = 0L
        try {
            val stepsRequest = ReadRecordsRequest(
                recordType = StepsRecord::class,
                timeRangeFilter = TimeRangeFilter.between(todayStart, todayEnd)
            )
            val stepsResponse = client.readRecords(stepsRequest)
            totalSteps = stepsResponse.records.sumOf { it.count }
        } catch (e: Exception) {
            // Ignore individual query failures
        }

        // 2. Fetch Sleep (Look back 24 hours to capture last night's sleep session)
        var totalSleepMins = 0
        try {
            val sleepStart = Instant.now().minus(24, ChronoUnit.HOURS)
            val sleepRequest = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(sleepStart, todayEnd)
            )
            val sleepResponse = client.readRecords(sleepRequest)
            val totalSleepDuration = sleepResponse.records.sumOf { record ->
                ChronoUnit.MINUTES.between(record.startTime, record.endTime)
            }
            totalSleepMins = totalSleepDuration.toInt()
        } catch (e: Exception) {
            // Ignore
        }

        // 3. Fetch Heart Rate
        var minBpm = 0
        var maxBpm = 0
        var avgBpm = 0
        try {
            val hrRequest = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(todayStart, todayEnd)
            )
            val hrResponse = client.readRecords(hrRequest)
            val samples = hrResponse.records.flatMap { it.samples }
            if (samples.isNotEmpty()) {
                val bpms = samples.map { it.beatsPerMinute.toInt() }
                minBpm = bpms.minOrNull() ?: 0
                maxBpm = bpms.maxOrNull() ?: 0
                avgBpm = bpms.average().toInt()
            }
        } catch (e: Exception) {
            // Ignore
        }

        return HealthMetrics(
            steps = totalSteps,
            sleepMinutes = totalSleepMins,
            minHeartRate = minBpm,
            maxHeartRate = maxBpm,
            avgHeartRate = avgBpm
        )
    }
}
