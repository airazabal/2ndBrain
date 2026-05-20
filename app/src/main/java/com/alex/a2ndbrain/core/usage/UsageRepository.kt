package com.alex.a2ndbrain.core.usage

import com.alex.a2ndbrain.core.memory.MemoryDao
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsageRepository(private val memoryDao: MemoryDao) {

    fun getUsageStatsForToday(): Flow<List<UsageStatEntity>> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return memoryDao.getUsageStatsForDate(today)
    }

    suspend fun getUsageStatsForTodaySync(): List<UsageStatEntity> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return memoryDao.getUsageStatsForDateSync(today)
    }

    suspend fun insertUsageStat(stat: UsageStatEntity) = memoryDao.insertUsageStat(stat)

    suspend fun getUsageStatByKey(date: String, packageName: String, deviceId: String): UsageStatEntity? =
        memoryDao.getUsageStatByKey(date, packageName, deviceId)

    suspend fun getUsageStatsSince(startDate: String): List<UsageStatEntity> = memoryDao.getUsageStatsSince(startDate)

    fun getUsageStatsSinceFlow(startDate: String): Flow<List<UsageStatEntity>> = memoryDao.getUsageStatsSinceFlow(startDate)

    suspend fun deleteUsageStatsByPackage(packageName: String) {
        memoryDao.deleteUsageStatsByPackage(packageName)
    }
}
