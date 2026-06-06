package com.alex.a2ndbrain.core.usage

import com.alex.a2ndbrain.core.memory.UsageStatEntity
import kotlinx.coroutines.flow.Flow

interface UsageRepository {
    fun getUsageStatsForToday(): Flow<List<UsageStatEntity>>
    fun getUsageStatsForDate(date: String): Flow<List<UsageStatEntity>>
    suspend fun getUsageStatsForTodaySync(): List<UsageStatEntity>
    suspend fun insertUsageStat(stat: UsageStatEntity)
    suspend fun getUsageStatByKey(date: String, packageName: String, deviceId: String): UsageStatEntity?
    suspend fun getUsageStatsSince(startDate: String): List<UsageStatEntity>
    fun getUsageStatsSinceFlow(startDate: String): Flow<List<UsageStatEntity>>
    suspend fun deleteUsageStatsByPackage(packageName: String)
}
