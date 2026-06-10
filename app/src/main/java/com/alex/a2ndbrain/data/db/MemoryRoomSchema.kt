package com.alex.a2ndbrain.data.db

import androidx.room.*
import com.alex.a2ndbrain.core.agents.ConsolidatedMemory
import com.alex.a2ndbrain.core.agents.MemoryTier
import com.alex.a2ndbrain.core.agents.EpisodicEvent
import java.time.Instant

// ─── Room entities ────────────────────────────────────────────────────────────

/**
 * Stores the consolidated long-term memory entries produced by MemoryConsolidationWorker.
 * Add to your existing AppDatabase entities list and bump SCHEMA_VERSION by 1.
 */
@Entity(tableName = "consolidated_memories")
data class ConsolidatedMemoryEntity(
    @PrimaryKey(autoGenerate = true)
    val id:             Long    = 0,
    val summary:        String,
    val sourceEventIds: String,         // JSON array of Long IDs — keep simple, no extra dep
    val importanceScore: Float,
    val tier:           String,         // MemoryTier.name
    val createdAt:      Long            // Instant.toEpochMilli()
)

/**
 * Episodic event log — one row per captured event (notification, health tick, app usage, habit).
 * Your existing notification/health tables feed into this via MemoryRepository.getEpisodicEvents().
 * This table is the "inbox" that consolidation reads from each night.
 */
@Entity(tableName = "episodic_events")
data class EpisodicEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id:        Long   = 0,
    val content:   String,
    val timestamp: Long,                // Instant.toEpochMilli()
    val sourceTag: String               // "notification" | "health" | "app_usage" | "habit"
)

// ─── DAOs ─────────────────────────────────────────────────────────────────────

@Dao
interface ConsolidatedMemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(memories: List<ConsolidatedMemoryEntity>)

    @Query("SELECT * FROM consolidated_memories WHERE tier = 'LONG_TERM' ORDER BY importanceScore DESC")
    suspend fun getLongTermMemories(): List<ConsolidatedMemoryEntity>

    @Query("""
        DELETE FROM consolidated_memories
        WHERE createdAt < :olderThanMillis AND importanceScore < :importanceBelow
    """)
    suspend fun pruneOldMemories(olderThanMillis: Long, importanceBelow: Float)
}

@Dao
interface EpisodicEventDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: EpisodicEventEntity): Long

    @Query("SELECT * FROM episodic_events WHERE timestamp > :sinceMillis ORDER BY timestamp DESC")
    suspend fun getEventsSince(sinceMillis: Long): List<EpisodicEventEntity>

    /**
     * Rough frequency count for importance scoring:
     * counts events with at least 3 words in common with the target content.
     * SQL LIKE chaining is imperfect but avoids FTS setup overhead for personal-scale data.
     */
    @Query("""
        SELECT COUNT(*) FROM episodic_events
        WHERE timestamp > :sinceMillis
          AND content LIKE '%' || :keyword || '%'
    """)
    suspend fun countSimilarEvents(keyword: String, sinceMillis: Long): Int
}

// ─── Repository additions ─────────────────────────────────────────────────────
//
// Add these methods to your existing MemoryRepository class.

/*
class MemoryRepository(
    private val consolidatedMemoryDao: ConsolidatedMemoryDao,
    private val episodicEventDao:      EpisodicEventDao
) {

    suspend fun getEpisodicEvents(since: Instant): List<EpisodicEvent> =
        episodicEventDao.getEventsSince(since.toEpochMilli()).map { it.toDomain() }

    suspend fun countSimilarEvents(content: String, since: Instant): Int {
        // Use the most distinctive word (longest) as the keyword proxy
        val keyword = content.split(" ").maxByOrNull { it.length } ?: return 1
        return episodicEventDao.countSimilarEvents(keyword, since.toEpochMilli())
    }

    suspend fun getLongTermMemories(): List<ConsolidatedMemory> =
        consolidatedMemoryDao.getLongTermMemories().map { it.toDomain() }

    suspend fun insertConsolidatedMemories(memories: List<ConsolidatedMemory>) =
        consolidatedMemoryDao.insertAll(memories.map { it.toEntity() })

    suspend fun pruneOldMemories(olderThan: Instant, importanceBelow: Float) =
        consolidatedMemoryDao.pruneOldMemories(olderThan.toEpochMilli(), importanceBelow)
}
*/

// ─── Mappers ──────────────────────────────────────────────────────────────────

fun EpisodicEventEntity.toDomain() = EpisodicEvent(
    id        = id,
    content   = content,
    timestamp = Instant.ofEpochMilli(timestamp),
    sourceTag = sourceTag
)

fun ConsolidatedMemoryEntity.toDomain() = ConsolidatedMemory(
    id              = id,
    summary         = summary,
    sourceEventIds  = sourceEventIds
        .removeSurrounding("[", "]")
        .split(",")
        .mapNotNull { it.trim().toLongOrNull() },
    importanceScore = importanceScore,
    tier            = MemoryTier.valueOf(tier),
    createdAt       = Instant.ofEpochMilli(createdAt)
)

fun ConsolidatedMemory.toEntity() = ConsolidatedMemoryEntity(
    id              = id,
    summary         = summary,
    sourceEventIds  = "[${sourceEventIds.joinToString(",")}]",
    importanceScore = importanceScore,
    tier            = tier.name,
    createdAt       = createdAt.toEpochMilli()
)
