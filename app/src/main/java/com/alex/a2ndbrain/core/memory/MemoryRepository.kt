package com.alex.a2ndbrain.core.memory

import androidx.paging.PagingData
import com.alex.a2ndbrain.core.agents.ConsolidatedMemory
import com.alex.a2ndbrain.core.agents.EpisodicEvent
import kotlinx.coroutines.flow.Flow
import java.time.Instant

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

    // Long-term memory consolidation
    suspend fun insertEpisodicEvent(content: String, sourceTag: String)
    suspend fun getEpisodicEvents(since: Instant): List<EpisodicEvent>
    suspend fun countSimilarEvents(content: String, since: Instant): Int
    suspend fun getLongTermMemories(): List<ConsolidatedMemory>
    suspend fun insertConsolidatedMemories(memories: List<ConsolidatedMemory>)
    suspend fun pruneOldLongTermMemories(olderThan: Instant, importanceBelow: Float)
    suspend fun pruneOldEpisodicEvents(olderThan: Instant)
}
