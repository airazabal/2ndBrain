package com.alex.a2ndbrain.core.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import android.provider.Settings
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

data class HealthMetrics(
    val steps: Long = 0,
    val sleepMinutes: Int = 0,
    val minHeartRate: Int = 0,
    val maxHeartRate: Int = 0,
    val avgHeartRate: Int = 0
)

class HealthConnectManager(private val context: Context) {

    private val localDeviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
    }

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
        val zoneId = ZoneId.systemDefault()
        val todayStart = LocalDate.now().atStartOfDay(zoneId).toInstant()
        return fetchHealthMetricsForRange(todayStart, Instant.now())
    }

    suspend fun fetchDailyBreakdown(days: Int = 30): List<DailyHealthMetrics> {
        if (!hasPermissions()) return emptyList()
        val zoneId = ZoneId.systemDefault()
        val results = mutableListOf<DailyHealthMetrics>()
        for (i in 0 until days) {
            val date = LocalDate.now().minusDays(i.toLong())
            val start = date.atStartOfDay(zoneId).toInstant()
            val end = if (i == 0) Instant.now() else date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val m = fetchHealthMetricsForRange(start, end)
            if (m.steps > 0 || m.sleepMinutes > 0 || m.avgHeartRate > 0) {
                results += DailyHealthMetrics(
                    date         = date.toString(),
                    steps        = m.steps,
                    sleepMinutes = m.sleepMinutes,
                    minHeartRate = m.minHeartRate,
                    maxHeartRate = m.maxHeartRate,
                    avgHeartRate = m.avgHeartRate
                )
            }
        }
        return results
    }

    suspend fun fetchDailySnapshotsForSync(days: Int = 7): List<HealthSnapshotEntity> {
        if (!hasPermissions()) return emptyList()
        val zoneId = ZoneId.systemDefault()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val results = mutableListOf<HealthSnapshotEntity>()
        for (i in 0 until days) {
            val date = LocalDate.now().minusDays(i.toLong())
            val start = date.atStartOfDay(zoneId).toInstant()
            val end = if (i == 0) Instant.now() else date.plusDays(1).atStartOfDay(zoneId).toInstant()
            val metrics = fetchHealthMetricsForRange(start, end)
            if (metrics.steps > 0 || metrics.sleepMinutes > 0 || metrics.avgHeartRate > 0) {
                results += HealthSnapshotEntity(
                    date = dateFormat.format(java.util.Date(start.toEpochMilli())),
                    deviceId = localDeviceId,
                    steps = metrics.steps,
                    sleepMinutes = metrics.sleepMinutes,
                    minHeartRate = metrics.minHeartRate,
                    maxHeartRate = metrics.maxHeartRate,
                    avgHeartRate = metrics.avgHeartRate,
                    lastTimestamp = System.currentTimeMillis()
                )
            }
        }
        return results
    }

    suspend fun fetchHealthMetricsForRange(startTime: Instant, endTime: Instant): HealthMetrics {
        val client = healthConnectClient ?: return HealthMetrics()
        if (!hasPermissions()) return HealthMetrics()

        var totalSteps = 0L
        try {
            val stepsAggregateRequest = AggregateRequest(
                metrics = setOf(StepsRecord.COUNT_TOTAL),
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
            )
            val stepsResponse = client.aggregate(stepsAggregateRequest)
            totalSteps = stepsResponse[StepsRecord.COUNT_TOTAL] ?: 0L
        } catch (e: Exception) {
            // Ignore
        }

        var totalSleepMins = 0
        try {
            // Sleep starts the previous evening — look back 16 h to catch last night's session.
            // Only count sessions that END after startTime so the same session isn't attributed
            // to both yesterday and today.
            val sleepQueryStart = startTime.minus(16, ChronoUnit.HOURS)
            val sleepRequest = ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = TimeRangeFilter.between(sleepQueryStart, endTime)
            )
            val sleepResponse = client.readRecords(sleepRequest)
            val totalSleepDuration = sleepResponse.records
                .filter { it.endTime.isAfter(startTime) }
                .sumOf { record -> ChronoUnit.MINUTES.between(record.startTime, record.endTime) }
            totalSleepMins = totalSleepDuration.toInt()
        } catch (e: Exception) {
            // Ignore
        }

        var minBpm = 0
        var maxBpm = 0
        var avgBpm = 0
        try {
            val hrRequest = ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = TimeRangeFilter.between(startTime, endTime)
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
