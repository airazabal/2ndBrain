package com.alex.a2ndbrain.core.memory

import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface MemoryRepository {
    fun getPagedMemories(query: String = ""): Flow<PagingData<MemoryEntity>>
    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>>
    fun getAllSummariesFlow(): Flow<List<DailySummaryEntity>>
    suspend fun getAllSummariesSync(): List<DailySummaryEntity>
    suspend fun getRecentMemoriesSync(): List<MemoryEntity>
    suspend fun searchMemoriesSync(query: String): List<MemoryEntity>
    suspend fun insertMemory(memory: MemoryEntity)
    suspend fun markAsRead(id: Long)
    suspend fun markAsReadByPackageAndTitle(packageName: String, title: String)
    suspend fun markMultipleAsRead(ids: List<Long>)
    suspend fun markMultipleAsUnread(ids: List<Long>)
    suspend fun deleteMemoryById(id: Long)
    suspend fun pruneOldMemories(timestamp: Long)
    suspend fun deleteAllMemories()
    suspend fun clearAllSummaries()
    suspend fun deleteSummary(id: Long)
    suspend fun findExisting(source: String, packageName: String?, title: String?, content: String): MemoryEntity?
    suspend fun getRecentMemories(startTime: Long): List<MemoryEntity>
    suspend fun getMemoriesByPackageSync(packageName: String): List<MemoryEntity>
    suspend fun deleteMemoriesByPackage(packageName: String)
}
