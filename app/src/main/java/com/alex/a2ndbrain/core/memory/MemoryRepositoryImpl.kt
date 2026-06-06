package com.alex.a2ndbrain.core.memory

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

class MemoryRepositoryImpl(private val memoryDao: MemoryDao) : MemoryRepository {

    override fun getPagedMemories(query: String): Flow<PagingData<MemoryEntity>> {
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

    override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = memoryDao.getAllMemories()
    override fun getAllSummariesFlow(): Flow<List<DailySummaryEntity>> = memoryDao.getAllSummaries()
    override suspend fun getAllSummariesSync(): List<DailySummaryEntity> = memoryDao.getAllSummariesSync()

    override suspend fun getRecentMemoriesSync(): List<MemoryEntity> = memoryDao.getRecentMemoriesSync()
    override suspend fun searchMemoriesSync(query: String): List<MemoryEntity> = memoryDao.searchMemoriesSync(query)

    override suspend fun insertMemory(memory: MemoryEntity) = memoryDao.insert(memory)

    override suspend fun markAsRead(id: Long) = memoryDao.markAsRead(id)

    override suspend fun markAsReadByPackageAndTitle(packageName: String, title: String) =
        memoryDao.markAsReadByPackageAndTitleLike(packageName, "${title.replace("%", "\\%").replace("_", "\\_")}%")

    override suspend fun markMultipleAsRead(ids: List<Long>) = memoryDao.markMultipleAsRead(ids)

    override suspend fun markMultipleAsUnread(ids: List<Long>) = memoryDao.markMultipleAsUnread(ids)

    override suspend fun deleteMemoryById(id: Long) = memoryDao.deleteMemoryById(id)

    override suspend fun pruneOldMemories(timestamp: Long) = memoryDao.pruneOldMemories(timestamp)

    override suspend fun deleteAllMemories() = memoryDao.deleteAllMemories()

    override suspend fun clearAllSummaries() = memoryDao.deleteAllSummaries()

    override suspend fun deleteSummary(id: Long) = memoryDao.deleteSummary(id)

    override suspend fun findExisting(source: String, packageName: String?, title: String?, content: String): MemoryEntity? {
        return memoryDao.findExisting(source, packageName, title, content)
    }

    override suspend fun getRecentMemories(startTime: Long): List<MemoryEntity> {
        return memoryDao.getMemoriesSince(startTime)
    }

    override suspend fun getMemoriesByPackageSync(packageName: String): List<MemoryEntity> =
        memoryDao.getMemoriesByPackageSync(packageName)

    override suspend fun deleteMemoriesByPackage(packageName: String) {
        memoryDao.deleteMemoriesByPackage(packageName)
    }
}
