package com.alex.a2ndbrain.core.memory

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class MemoryRepository(private val memoryDao: MemoryDao) {

    fun getPagedMemories(query: String = ""): Flow<PagingData<MemoryEntity>> {
        return Pager(
            config = PagingConfig(
                pageSize = 20,
                enablePlaceholders = false
            ),
            pagingSourceFactory = {
                if (query.isEmpty()) {
                    memoryDao.getPagedMemories()
                } else {
                    memoryDao.searchPagedMemories(query)
                }
            }
        ).flow
    }

    fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
    fun getAllSummariesFlow(): Flow<List<DailySummaryEntity>> = memoryDao.getAllSummaries()

    suspend fun getRecentMemoriesSync(): List<MemoryEntity> = memoryDao.getRecentMemoriesSync()
    suspend fun searchMemoriesSync(query: String): List<MemoryEntity> = memoryDao.searchMemoriesSync(query)

    suspend fun insertMemory(memory: MemoryEntity) = memoryDao.insert(memory)
    
    suspend fun markAsRead(id: Long) = memoryDao.markAsRead(id)
    
    suspend fun markMultipleAsRead(ids: List<Long>) = memoryDao.markMultipleAsRead(ids)
    
    suspend fun markMultipleAsUnread(ids: List<Long>) = memoryDao.markMultipleAsUnread(ids)
    
    suspend fun deleteMemoryById(id: Long) = memoryDao.deleteMemoryById(id)

    suspend fun pruneOldMemories(timestamp: Long) = memoryDao.pruneOldMemories(timestamp)
    
    suspend fun clearAllSummaries() = memoryDao.deleteAllSummaries()
    
    suspend fun deleteSummary(id: Long) = memoryDao.deleteSummary(id)

    suspend fun findExisting(source: String, packageName: String?, title: String?, content: String): MemoryEntity? {
        return memoryDao.findExisting(source, packageName, title, content)
    }

    suspend fun getRecentMemories(startTime: Long): List<MemoryEntity> {
        return memoryDao.getMemoriesSince(startTime)
    }

    suspend fun deleteMemoriesByPackage(packageName: String) {
        memoryDao.deleteMemoriesByPackage(packageName)
    }
}
