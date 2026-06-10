package com.alex.a2ndbrain.core.workers

import android.content.Context
import androidx.work.*
import com.alex.a2ndbrain.core.agents.MemoryAgent
import com.alex.a2ndbrain.core.memory.MemoryRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
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
        // TODO: implement once MemoryRepository gains episodic/consolidation APIs
        return Result.success()
    }

    companion object {
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
                .setRequiredNetworkType(NetworkType.UNMETERED)  // WiFi only
                .setRequiresCharging(true)
                .build()

            val request = PeriodicWorkRequestBuilder<MemoryConsolidationWorker>(
                repeatInterval = 24,
                repeatIntervalTimeUnit = TimeUnit.HOURS
            )
                .setConstraints(constraints)
                .setInitialDelay(calculateDelayUntil2AM(), TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .addTag(WORK_NAME)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,  // don't reset if already scheduled
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

