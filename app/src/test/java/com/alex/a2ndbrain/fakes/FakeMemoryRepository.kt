package com.alex.a2ndbrain.fakes

import androidx.paging.PagingData
import com.alex.a2ndbrain.core.agents.ConsolidatedMemory
import com.alex.a2ndbrain.core.agents.EpisodicEvent
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.time.Instant

class FakeMemoryRepository : MemoryRepository {

    val memories = MutableStateFlow<List<MemoryEntity>>(emptyList())
    val summaries = MutableStateFlow<List<DailySummaryEntity>>(emptyList())
    val insertedMemories = mutableListOf<MemoryEntity>()
    val deletedIds = mutableListOf<Long>()
    val prunedTimestamps = mutableListOf<Long>()

    override fun getPagedMemories(query: String): Flow<PagingData<MemoryEntity>> =
        MutableStateFlow(PagingData.empty())

    override fun getAllMemoriesFlow(): Flow<List<MemoryEntity>> = memories

    override fun getAllSummariesFlow(): Flow<List<DailySummaryEntity>> = summaries

    override suspend fun getAllSummariesSync(): List<DailySummaryEntity> = summaries.value

    override suspend fun getRecentMemoriesSync(): List<MemoryEntity> = memories.value

    override suspend fun searchMemoriesSync(query: String): List<MemoryEntity> =
        memories.value.filter {
            it.content.contains(query, ignoreCase = true) ||
            it.title?.contains(query, ignoreCase = true) == true
        }

    override suspend fun insertMemory(memory: MemoryEntity) {
        insertedMemories += memory
        memories.value = memories.value + memory
    }

    override suspend fun markAsRead(id: Long) {
        memories.value = memories.value.map { if (it.id == id) it.copy(isRead = true) else it }
    }

    override suspend fun markAsReadByPackageAndTitle(packageName: String, title: String) {}

    override suspend fun markMultipleAsRead(ids: List<Long>) {
        memories.value = memories.value.map { if (it.id in ids) it.copy(isRead = true) else it }
    }

    override suspend fun markMultipleAsUnread(ids: List<Long>) {
        memories.value = memories.value.map { if (it.id in ids) it.copy(isRead = false) else it }
    }

    override suspend fun deleteMemoryById(id: Long) {
        deletedIds += id
        memories.value = memories.value.filter { it.id != id }
    }

    override suspend fun pruneOldMemories(timestamp: Long) {
        prunedTimestamps += timestamp
        memories.value = memories.value.filter { it.timestamp >= timestamp }
    }

    override suspend fun deleteAllMemories() {
        memories.value = emptyList()
    }

    override suspend fun clearAllSummaries() {
        summaries.value = emptyList()
    }

    override suspend fun deleteSummary(id: Long) {
        summaries.value = summaries.value.filter { it.id != id }
    }

    override suspend fun findExisting(
        source: String,
        packageName: String?,
        title: String?,
        content: String
    ): MemoryEntity? = memories.value.firstOrNull {
        it.source == source && it.packageName == packageName &&
        it.title == title && it.content == content
    }

    override suspend fun getRecentMemories(startTime: Long): List<MemoryEntity> =
        memories.value.filter { it.timestamp >= startTime }

    override suspend fun getMemoriesByPackageSync(packageName: String): List<MemoryEntity> =
        memories.value.filter { it.packageName == packageName }

    override suspend fun deleteMemoriesByPackage(packageName: String) {
        memories.value = memories.value.filter { it.packageName != packageName }
    }

    override suspend fun insertEpisodicEvent(content: String, sourceTag: String) {}
    override suspend fun getEpisodicEvents(since: Instant): List<EpisodicEvent> = emptyList()
    override suspend fun countSimilarEvents(content: String, since: Instant): Int = 0
    override suspend fun getLongTermMemories(): List<ConsolidatedMemory> = emptyList()
    override suspend fun insertConsolidatedMemories(memories: List<ConsolidatedMemory>) {}
    override suspend fun pruneOldLongTermMemories(olderThan: Instant, importanceBelow: Float) {}
    override suspend fun pruneOldEpisodicEvents(olderThan: Instant) {}
}
