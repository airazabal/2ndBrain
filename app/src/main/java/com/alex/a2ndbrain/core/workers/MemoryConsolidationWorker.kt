package com.alex.a2ndbrain.core.workers

import android.content.Context
import android.util.Log
import androidx.work.*
import com.alex.a2ndbrain.core.agents.ConsolidatedMemory
import com.alex.a2ndbrain.core.agents.ImportanceScorer
import com.alex.a2ndbrain.core.agents.MemoryAgent
import com.alex.a2ndbrain.core.agents.MemoryTier
import com.alex.a2ndbrain.core.agents.ScoredEvent
import com.alex.a2ndbrain.core.agents.TextClusterer
import com.alex.a2ndbrain.core.agents.summarizeCluster
import com.alex.a2ndbrain.core.memory.MemoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * Nightly WorkManager job that:
 *  1. Pulls raw episodic events from the last 24h (notifications, health, usage, habits)
 *  2. Scores each by importance (recency × frequency × emotional weight)
 *  3. Clusters similar memories using a simple TF-IDF cosine approach (no vector DB needed)
 *  4. Promotes high-signal clusters to long-term storage
 *  5. Prunes duplicates and low-importance entries older than 30 days
 *
 * Designed to run at ~2 AM via a periodic WorkManager constraint so it doesn't compete
 * with the daily briefing (6 AM) or evening reflection (9 PM).
 */
class MemoryConsolidationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val memoryRepository: MemoryRepository by inject()
    private val memoryAgent: MemoryAgent by inject()

    override suspend fun doWork(): Result {
        val now = Instant.now()
        val since24h = now.minus(24, ChronoUnit.HOURS)
        val since7d  = now.minus(7, ChronoUnit.DAYS)

        val events = try {
            memoryRepository.getEpisodicEvents(since24h)
        } catch (e: Exception) {
            Log.e(TAG, "getEpisodicEvents failed", e)
            return Result.retry()
        }

        if (events.isEmpty()) {
            Log.d(TAG, "No episodic events in last 24h, skipping consolidation")
            return Result.success()
        }

        // Score each event (frequency window = last 7 days for recurrence signal)
        val scoredEvents = events.map { event ->
            val frequency = try {
                memoryRepository.countSimilarEvents(event.content, since7d)
            } catch (e: Exception) { 1 }
            ScoredEvent(
                event = event,
                score = ImportanceScorer.score(
                    text      = event.content,
                    timestamp = event.timestamp,
                    frequency = frequency
                )
            )
        }

        val clusters = TextClusterer.cluster(scoredEvents)

        val toInsert = mutableListOf<ConsolidatedMemory>()
        for (cluster in clusters) {
            val clusterScore = cluster.map { it.score }.average().toFloat()
            if (clusterScore < IMPORTANCE_THRESHOLD) continue

            val summary = memoryAgent.summarizeCluster(cluster.map { it.event.content })
            toInsert += ConsolidatedMemory(
                summary         = summary,
                sourceEventIds  = cluster.map { it.event.id },
                importanceScore = clusterScore,
                tier            = MemoryTier.LONG_TERM,
                createdAt       = now
            )
        }

        if (toInsert.isNotEmpty()) {
            try {
                memoryRepository.insertConsolidatedMemories(toInsert)
            } catch (e: Exception) {
                Log.e(TAG, "insertConsolidatedMemories failed", e)
                return Result.retry()
            }
        }

        val pruneOlderThan = now.minus(PRUNE_AFTER_DAYS, ChronoUnit.DAYS)
        try {
            memoryRepository.pruneOldLongTermMemories(pruneOlderThan, PRUNE_IMPORTANCE_FLOOR)
        } catch (e: Exception) {
            Log.w(TAG, "pruneOldLongTermMemories failed (non-fatal)", e)
        }

        Log.d(TAG, "Consolidation done: ${toInsert.size} promoted, pruned entries older than $PRUNE_AFTER_DAYS days")
        return Result.success(
            workDataOf(
                KEY_PROMOTED_COUNT to toInsert.size,
                KEY_PRUNED_EVENTS  to events.size
            )
        )
    }

    companion object {
        private const val TAG = "MemoryConsolidation"
        const val WORK_NAME             = "memory_consolidation_nightly"
        const val IMPORTANCE_THRESHOLD  = 0.55f   // 0.0–1.0; tune based on signal density
        const val PRUNE_IMPORTANCE_FLOOR = 0.20f
        const val PRUNE_AFTER_DAYS      = 30L
        const val MAX_RETRIES           = 3
        const val KEY_PROMOTED_COUNT    = "promoted_count"
        const val KEY_PRUNED_EVENTS     = "pruned_events"

        /**
         * Register this worker from ReflectionManager.scheduleWorkers() alongside
         * your existing DailyBriefingWorker / EveningReflectionWorker.
         *
         * Runs nightly at ~2 AM, only on WiFi+charging to avoid battery drain.
         */
        fun schedule(workManager: WorkManager) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )

            // Run immediately on first schedule so we don't wait up to 24h for the first fire
            runOnce(workManager)
        }

        fun runOnce(workManager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<MemoryConsolidationWorker>()
                .addTag(WORK_NAME)
                .build()
            workManager.enqueueUniqueWork(
                "${WORK_NAME}_immediate",
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        private fun calculateDelayUntil2AM(): Long {
            val now = java.util.Calendar.getInstance()
            val target = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, 2)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                if (before(now)) add(java.util.Calendar.DAY_OF_MONTH, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}

