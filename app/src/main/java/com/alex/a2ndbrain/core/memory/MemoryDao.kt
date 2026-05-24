package com.alex.a2ndbrain.core.memory

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(memory: MemoryEntity)

    @Query("SELECT * FROM memories WHERE source = :source AND packageName IS :packageName AND title IS :title AND content = :content LIMIT 1")
    suspend fun findExisting(source: String, packageName: String?, title: String?, content: String): MemoryEntity?

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getAllMemories(): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC")
    fun getPagedMemories(): PagingSource<Int, MemoryEntity>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY timestamp DESC")
    fun searchPagedMemories(query: String): PagingSource<Int, MemoryEntity>

    @Query("SELECT * FROM memories WHERE source = 'clipboard' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastClipboardMemory(): MemoryEntity?

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%'")
    fun searchMemories(query: String): Flow<List<MemoryEntity>>

    @Query("SELECT * FROM memories WHERE timestamp >= :startTime")
    suspend fun getMemoriesSince(startTime: Long): List<MemoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSummary(summary: DailySummaryEntity)

    @Query("SELECT * FROM daily_summaries ORDER BY timestamp DESC")
    fun getAllSummaries(): Flow<List<DailySummaryEntity>>

    @Query("SELECT * FROM daily_summaries WHERE summary LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 10")
    suspend fun searchSummaries(query: String): List<DailySummaryEntity>

    @Query("SELECT * FROM daily_summaries ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSummary(): DailySummaryEntity?

    @Query("DELETE FROM daily_summaries")
    suspend fun deleteAllSummaries()

    @Query("DELETE FROM daily_summaries WHERE id = :id")
    suspend fun deleteSummary(id: Long)

    @Query("UPDATE memories SET isRead = 1 WHERE id = :id")
    suspend fun markAsRead(id: Long)

    @Query("UPDATE memories SET isRead = 1 WHERE id IN (:ids)")
    suspend fun markMultipleAsRead(ids: List<Long>)

    @Query("UPDATE memories SET isRead = 0 WHERE id IN (:ids)")
    suspend fun markMultipleAsUnread(ids: List<Long>)

    @Query("DELETE FROM memories WHERE id = :id")
    suspend fun deleteMemoryById(id: Long)

    @Query("DELETE FROM memories WHERE timestamp < :timestamp")
    suspend fun pruneOldMemories(timestamp: Long)

    @Query("DELETE FROM memories")
    suspend fun deleteAllMemories()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsageStat(stat: UsageStatEntity)

    @Query("SELECT * FROM usage_stats WHERE date = :date")
    fun getUsageStatsForDate(date: String): Flow<List<UsageStatEntity>>

    @Query("SELECT * FROM usage_stats WHERE date = :date")
    suspend fun getUsageStatsForDateSync(date: String): List<UsageStatEntity>

    @Query("SELECT * FROM usage_stats WHERE date = :date AND packageName = :packageName AND deviceId = :deviceId LIMIT 1")
    suspend fun getUsageStatByKey(date: String, packageName: String, deviceId: String): UsageStatEntity?

    @Query("SELECT * FROM usage_stats WHERE date >= :startDate")
    fun getUsageStatsSinceFlow(startDate: String): Flow<List<UsageStatEntity>>

    @Query("SELECT * FROM usage_stats WHERE date >= :startDate")
    suspend fun getUsageStatsSince(startDate: String): List<UsageStatEntity>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT 50")
    suspend fun getRecentMemoriesSync(): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE content LIKE '%' || :query || '%' OR title LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 30")
    suspend fun searchMemoriesSync(query: String): List<MemoryEntity>

    @Query("SELECT * FROM memories WHERE packageName = :packageName ORDER BY timestamp DESC")
    suspend fun getMemoriesByPackageSync(packageName: String): List<MemoryEntity>

    @Query("DELETE FROM memories WHERE packageName = :packageName")
    suspend fun deleteMemoriesByPackage(packageName: String)

    @Query("DELETE FROM usage_stats WHERE packageName = :packageName")
    suspend fun deleteUsageStatsByPackage(packageName: String)
}