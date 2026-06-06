package com.alex.a2ndbrain.core.usage

import com.alex.a2ndbrain.core.memory.MemoryDao
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class UsageRepositoryImpl(private val memoryDao: MemoryDao) : UsageRepository {

    override fun getUsageStatsForToday(): Flow<List<UsageStatEntity>> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return memoryDao.getUsageStatsForDate(today)
    }

    override fun getUsageStatsForDate(date: String): Flow<List<UsageStatEntity>> =
        memoryDao.getUsageStatsForDate(date)

    override suspend fun getUsageStatsForTodaySync(): List<UsageStatEntity> {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        return memoryDao.getUsageStatsForDateSync(today)
    }

    override suspend fun insertUsageStat(stat: UsageStatEntity) = memoryDao.insertUsageStat(stat)

    override suspend fun getUsageStatByKey(date: String, packageName: String, deviceId: String): UsageStatEntity? =
        memoryDao.getUsageStatByKey(date, packageName, deviceId)

    override suspend fun getUsageStatsSince(startDate: String): List<UsageStatEntity> = memoryDao.getUsageStatsSince(startDate)

    override fun getUsageStatsSinceFlow(startDate: String): Flow<List<UsageStatEntity>> = memoryDao.getUsageStatsSinceFlow(startDate)

    override suspend fun deleteUsageStatsByPackage(packageName: String) {
        memoryDao.deleteUsageStatsByPackage(packageName)
    }
}
