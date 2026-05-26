package com.alex.a2ndbrain.core.health

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HealthRepository(
    val healthConnectManager: HealthConnectManager,
    private val healthDao: HealthDao,
    // Injected after CloudHealthSyncManager is created; null until Firebase is configured.
    var cloudSync: com.alex.a2ndbrain.core.sync.CloudHealthSyncManager? = null
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Returns HC metrics only when a paired wearable provides sleep or HR data.
     * Returns null when HC is unavailable, permissions not granted, or no wearable
     * is paired (e.g. a tablet whose accelerometer supplies steps but not sleep/HR).
     */
    suspend fun getHCMetricsIfWearable(): HealthMetrics? = withContext(Dispatchers.IO) {
        if (!healthConnectManager.isAvailable() || !healthConnectManager.hasPermissions()) return@withContext null
        val metrics = healthConnectManager.fetchHealthMetricsToday()
        if (metrics.sleepMinutes > 0 || metrics.avgHeartRate > 0) metrics else null
    }

    /**
     * Today's canonical health metrics.
     * Fallback chain:
     *   1. HC with wearable data (sleep or HR > 0)
     *   2. DB today's snapshot
     *   3. DB yesterday's sleep if today's < 120 min (Zepp upload timing)
     */
    suspend fun getTodayMetrics(): HealthMetrics = withContext(Dispatchers.IO) {
        try {
            getHCMetricsIfWearable() ?: getMetricsFromDb()
        } catch (e: Exception) {
            Log.e("HealthRepository", "getTodayMetrics failed", e)
            HealthMetrics()
        }
    }

    /**
     * Health metrics for the requested period. Returns (metrics, hasWearableData).
     * Falls back to DB for no-wearable devices (tablets).
     */
    suspend fun getPeriodMetrics(days: Int): Pair<List<DailyHealthMetrics>, Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val hasHC = healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()
                val hcData = if (hasHC) healthConnectManager.fetchDailyBreakdown(days) else null
                val hasWearableData = hcData?.any { it.hasSleep || it.hasHeart } == true
                // Use HC data whenever it has anything (steps-only counts). Only fall through
                // to DB when HC returned nothing — e.g. a tablet with no HC at all.
                if (!hcData.isNullOrEmpty()) return@withContext Pair(hcData, hasWearableData)

                val sinceDate = dateFormat.format(
                    Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }.time
                )
                val allSnapshots = healthDao.getSnapshotsSince(sinceDate)
                val todayStr = dateFormat.format(Date())
                val yesterdayStr = dateFormat.format(
                    Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
                )
                // Collapse multiple device rows per date into the best one
                val byDate = allSnapshots.groupBy { it.date }
                    .mapValues { (_, snaps) -> bestSnapshot(snaps)!! }
                val yesterdaySleep = byDate[yesterdayStr]?.sleepMinutes ?: 0
                val metrics = byDate.values.map { snap ->
                    val corrected = if (snap.date == todayStr
                        && snap.sleepMinutes < 120
                        && yesterdaySleep > snap.sleepMinutes
                    ) snap.copy(sleepMinutes = yesterdaySleep) else snap
                    corrected.toDailyMetrics()
                }.sortedBy { it.date }
                Pair(metrics, false)
            } catch (e: Exception) {
                Log.e("HealthRepository", "getPeriodMetrics failed", e)
                Pair(emptyList(), false)
            }
        }

    /**
     * 7-day weekly trend for Reflection correlations.
     */
    suspend fun getWeeklyTrends(): List<Pair<String, HealthMetrics>> = withContext(Dispatchers.IO) {
        try {
            if (!healthConnectManager.isAvailable() || !healthConnectManager.hasPermissions()) {
                return@withContext emptyList()
            }
            val zoneId = ZoneId.systemDefault()
            val localToday = LocalDate.now()
            (6 downTo 0).map { i ->
                val date = localToday.minusDays(i.toLong())
                val startInstant = date.atStartOfDay(zoneId).toInstant()
                val endInstant = if (i == 0) Instant.now()
                else date.plusDays(1).atStartOfDay(zoneId).toInstant()
                date.toString() to healthConnectManager.fetchHealthMetricsForRange(startInstant, endInstant)
            }
        } catch (e: Exception) {
            Log.e("HealthRepository", "getWeeklyTrends failed", e)
            emptyList()
        }
    }

    /**
     * Snapshots to push via P2P or cloud sync. Applies the sleep fallback on today's
     * record so the tablet always receives a realistic sleep value.
     */
    suspend fun getSnapshotsForSync(days: Int = 7): List<HealthSnapshotEntity> =
        withContext(Dispatchers.IO) {
            try {
                healthConnectManager.fetchDailySnapshotsForSync(days)
            } catch (e: Exception) {
                Log.e("HealthRepository", "getSnapshotsForSync failed", e)
                emptyList()
            }
        }

    /**
     * Persist a snapshot received from P2P or cloud sync.
     */
    suspend fun saveSnapshot(snapshot: HealthSnapshotEntity) = withContext(Dispatchers.IO) {
        healthDao.insertSnapshot(snapshot)
    }

    private fun bestSnapshot(snapshots: List<HealthSnapshotEntity>): HealthSnapshotEntity? =
        snapshots.maxByOrNull { it.sleepMinutes * 10_000 + it.avgHeartRate * 100 + it.steps.coerceAtMost(9999) }

    private suspend fun getMetricsFromDb(): HealthMetrics {
        val today = dateFormat.format(Date())
        var metrics = bestSnapshot(healthDao.getSnapshotsForDate(today))?.toHealthMetrics() ?: HealthMetrics()
        if (metrics.sleepMinutes < 120) {
            val yesterday = dateFormat.format(
                Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
            )
            val prev = bestSnapshot(healthDao.getSnapshotsForDate(yesterday))
            if (prev != null && prev.sleepMinutes > metrics.sleepMinutes) {
                metrics = metrics.copy(sleepMinutes = prev.sleepMinutes)
            }
        }
        // 4th fallback: pull from Firestore when DB has no sleep data (P2P hasn't synced yet)
        if (metrics.sleepMinutes == 0) {
            val cloud = cloudSync ?: return metrics
            try {
                val pulled = cloud.pullTodaySnapshot()
                if (pulled != null && pulled.sleepMinutes > 0) {
                    healthDao.insertSnapshot(pulled)
                    return pulled.toHealthMetrics()
                }
            } catch (e: Exception) {
                Log.e("HealthRepository", "Firestore fallback failed", e)
            }
        }
        return metrics
    }
}
