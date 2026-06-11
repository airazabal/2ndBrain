package com.alex.a2ndbrain.core.memory

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.alex.a2ndbrain.core.agents.ConsolidatedMemory
import com.alex.a2ndbrain.core.agents.EpisodicEvent
import com.alex.a2ndbrain.data.db.ConsolidatedMemoryDao
import com.alex.a2ndbrain.data.db.EpisodicEventDao
import com.alex.a2ndbrain.data.db.EpisodicEventEntity
import com.alex.a2ndbrain.data.db.toDomain
import com.alex.a2ndbrain.data.db.toEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class MemoryRepositoryImpl(
    private val memoryDao: MemoryDao,
    private val consolidatedMemoryDao: ConsolidatedMemoryDao,
    private val episodicEventDao: EpisodicEventDao
) : MemoryRepository {

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

    override suspend fun insertEpisodicEvent(content: String, sourceTag: String) {
        episodicEventDao.insert(EpisodicEventEntity(
            content   = content,
            timestamp = System.currentTimeMillis(),
            sourceTag = sourceTag
        ))
    }

    override suspend fun getEpisodicEvents(since: Instant): List<EpisodicEvent> =
        episodicEventDao.getEventsSince(since.toEpochMilli()).map { it.toDomain() }

    override suspend fun countSimilarEvents(content: String, since: Instant): Int {
        val keyword = content.split(" ").maxByOrNull { it.length } ?: return 1
        return episodicEventDao.countSimilarEvents(keyword, since.toEpochMilli())
    }

    override suspend fun getLongTermMemories(): List<ConsolidatedMemory> =
        consolidatedMemoryDao.getLongTermMemories().map { it.toDomain() }

    override suspend fun insertConsolidatedMemories(memories: List<ConsolidatedMemory>) =
        consolidatedMemoryDao.insertAll(memories.map { it.toEntity() })

    override suspend fun pruneOldLongTermMemories(olderThan: Instant, importanceBelow: Float) =
        consolidatedMemoryDao.pruneOldMemories(olderThan.toEpochMilli(), importanceBelow)

    override suspend fun pruneOldEpisodicEvents(olderThan: Instant) =
        episodicEventDao.deleteOlderThan(olderThan.toEpochMilli())
}
