package com.alex.a2ndbrain.core.health

import com.alex.a2ndbrain.core.sync.CloudHealthSyncManager

interface HealthRepository {
    val healthConnectManager: HealthConnectManager
    fun setCloudSync(sync: CloudHealthSyncManager)
    suspend fun getHCMetricsIfWearable(): HealthMetrics?
    suspend fun getTodayMetrics(): HealthMetrics
    suspend fun getPeriodMetrics(days: Int): Pair<List<DailyHealthMetrics>, Boolean>
    suspend fun getWeeklyTrends(): List<Pair<String, HealthMetrics>>
    suspend fun getSnapshotsForSync(days: Int = 7): List<HealthSnapshotEntity>
    suspend fun saveSnapshot(snapshot: HealthSnapshotEntity)
}
