package com.alex.a2ndbrain.core.agents

import android.util.Log
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.alex.a2ndbrain.core.agents.ConsolidatedMemory

/**
 * MemoryAgent — scored, deduplicated memory retrieval.
 *
 * Replaces the direct MemoryDao calls and naive words.first() keyword search
 * previously scattered across ReflectionManager and CopilotViewModel.
 *
 * Scoring weights:
 *   - Recency (0–1, decays by hour): 30%
 *   - Keyword overlap with query:    50%
 *   - Source weight (clipboard > notification > other): 20%
 *
 * Note: getAllMemoriesFlow() loads all records into RAM — kept for reactive UI
 * in ViewModels. For agent retrieval, always use retrieve() which uses the
 * efficient SQL-backed getRecentMemoriesSync() + searchMemoriesSync().
 */
class MemoryAgent(
    private val repo: MemoryRepository,
    private val modelRouter: ModelRouter
) {

    /**
     * Retrieve relevant memories for a given query.
     * Falls back to recent memories when query is blank.
     * Always deduplicates by id before scoring.
     */
    suspend fun retrieve(query: String = "", limit: Int = 50): List<MemoryEntity> =
        withContext(Dispatchers.IO) {
            try {
                val candidates = if (query.isBlank()) {
                    repo.getRecentMemoriesSync()
                } else {
                    // Merge keyword search results with recent feed; dedup below
                    val searched = repo.searchMemoriesSync(query)
                    val recent = repo.getRecentMemoriesSync()
                    (searched + recent).distinctBy { it.id }
                }

                if (query.isBlank()) {
                    candidates.take(limit)
                } else {
                    candidates
                        .map { it to score(it, query) }
                        .sortedByDescending { it.second }
                        .take(limit)
                        .map { it.first }
                }
            } catch (e: Exception) {
                Log.e("MemoryAgent", "retrieve() failed", e)
                emptyList()
            }
        }

    /**
     * Retrieve memories for a specific package (e.g. meditation sessions).
     * Uses efficient package-scoped SQL query rather than loading everything.
     */
    suspend fun retrieveByPackage(packageName: String): List<MemoryEntity> =
        withContext(Dispatchers.IO) {
            try {
                repo.getMemoriesByPackageSync(packageName)
            } catch (e: Exception) {
                Log.e("MemoryAgent", "retrieveByPackage($packageName) failed", e)
                emptyList()
            }
        }

    /**
     * Retrieve memories since a given epoch timestamp.
     */
    suspend fun retrieveSince(startTime: Long): List<MemoryEntity> =
        withContext(Dispatchers.IO) {
            try {
                repo.getRecentMemories(startTime)
            } catch (e: Exception) {
                Log.e("MemoryAgent", "retrieveSince() failed", e)
                emptyList()
            }
        }

    internal fun getModelRouter(): ModelRouter = modelRouter

    suspend fun getLongTermMemories(): List<ConsolidatedMemory> =
        withContext(Dispatchers.IO) {
            try {
                repo.getLongTermMemories()
            } catch (e: Exception) {
                Log.e("MemoryAgent", "getLongTermMemories() failed", e)
                emptyList()
            }
        }

    private fun score(memory: MemoryEntity, query: String): Float {
        // Recency: decays from 1.0 at time=now toward 0 as hours increase
        val ageHours = (System.currentTimeMillis() - memory.timestamp) / 3_600_000f
        val recency = 1f / (1f + ageHours)

        // Keyword overlap: count how many query words appear in content/title/tags
        val queryWords = query.lowercase().split(" ").filter { it.length > 2 }
        val searchableText = listOf(
            memory.content.lowercase(),
            memory.title?.lowercase() ?: "",
            memory.tags?.lowercase() ?: ""
        ).joinToString(" ")
        val keywordHits = queryWords.count { searchableText.contains(it) }.toFloat()
        val keywordScore = if (queryWords.isEmpty()) 0f else keywordHits / queryWords.size

        // Source weight: clipboard and manual captures are higher signal
        val sourceWeight = when (memory.source) {
            "clipboard" -> 1.5f
            "agenda", "voice" -> 1.2f
            "notification" -> 1.0f
            else -> 0.8f
        }

        return (recency * 0.3f) + (keywordScore * 0.5f) + (sourceWeight / 1.5f * 0.2f)
    }
}
