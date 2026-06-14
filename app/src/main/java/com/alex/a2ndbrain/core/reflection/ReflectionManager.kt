package com.alex.a2ndbrain.core.reflection

import android.content.Context
import android.util.Log
import androidx.work.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.alex.a2ndbrain.core.agents.BrainContext
import com.alex.a2ndbrain.core.agents.DriftContext
import com.alex.a2ndbrain.core.agents.HealthAgent
import com.alex.a2ndbrain.core.agents.MemoryAgent
import com.alex.a2ndbrain.core.agents.ModelRouter
import com.alex.a2ndbrain.core.agents.PillarAverages
import com.alex.a2ndbrain.core.agents.ReflectionAgent
import com.alex.a2ndbrain.core.agents.recallForContext
import com.alex.a2ndbrain.core.senseofday.SenseOfDayHistoryRepository
import com.alex.a2ndbrain.core.capture.SettingsRepository
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.core.memory.toDomain
import com.alex.a2ndbrain.core.workers.MemoryConsolidationWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import com.alex.a2ndbrain.core.domain.ReflectionService

/**
 * ReflectionManager — slimmed to scheduler + persistence only.
 *
 * Previously: 524-line God class doing data fetching, prompt building,
 *             model selection, inference, and DB persistence all in one.
 *
 * Now: Three responsibilities only:
 *   1. Schedule WorkManager jobs (periodic + expedited sync)
 *   2. Build BrainContext via agents, delegate prompt to ReflectionAgent
 *   3. Persist the resulting DailySummaryEntity to Room
 *
 * All data fetching → HealthAgent + MemoryAgent (via OrchestratorAgent)
 * All prompt construction → ReflectionAgent
 * All model selection + inference → ModelRouter
 *
 * runChatInference() is kept as a legacy shim for ReflectionWorker compatibility
 * until callers are fully migrated to OrchestratorAgent.chat().
 */
class ReflectionManager(
    private val context: Context,
    private val memoryRepository: MemoryRepository,
    private val digitalTimeManager: DigitalTimeManager,
    private val settingsManager: SettingsRepository,
    private val memoryAgent: MemoryAgent,
    private val healthAgent: HealthAgent,
    private val modelRouter: ModelRouter,
    private val usageRepository: UsageRepository,
    private val senseOfDayRepository: SenseOfDayHistoryRepository
) : ReflectionService {

    // ── WorkManager scheduling ────────────────────────────────────────────

    fun schedulePeriodicReflection() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ReflectionWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_reflection",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun scheduleWorkers() {
        schedulePeriodicReflection()
        MemoryConsolidationWorker.schedule(WorkManager.getInstance(context))
    }

    fun scheduleExpeditedSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<com.alex.a2ndbrain.core.usage.UsageSyncWorker>()
            .setConstraints(constraints)
            // Bypasses Samsung background freezing
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "usage_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    // ── Daily reflection ──────────────────────────────────────────────────

    /**
     * Called by ReflectionWorker every 6 hours.
     * Builds BrainContext via agents, delegates prompt to ReflectionAgent,
     * routes inference through ModelRouter, persists result to Room.
     * Returns null on success, error message on failure.
     */

    fun enqueueManualReflection() {
        val request = OneTimeWorkRequestBuilder<ReflectionWorker>().build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "manual_reflection", ExistingWorkPolicy.REPLACE, request
        )
    }

    fun cancelManualReflection() {
        WorkManager.getInstance(context).cancelUniqueWork("manual_reflection")
    }

    fun getManualReflectionRunning(): Flow<Boolean> =
        WorkManager.getInstance(context)
            .getWorkInfosForUniqueWorkFlow("manual_reflection")
            .map { infos -> infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED } }

    suspend fun generateDailyReflection(): String? = withContext(Dispatchers.IO) {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        val isMorning = hour in 4..11
        val summaryType = if (isMorning) "briefing" else "reflection"
        val reflectionType = if (isMorning)
            ReflectionAgent.ReflectionType.MORNING_BRIEFING
        else
            ReflectionAgent.ReflectionType.EVENING_REFLECTION

        // Sync latest digital time before building context
        try { digitalTimeManager.syncUsageStats() } catch (e: Exception) {
            Log.w("ReflectionManager", "Usage sync failed before reflection", e)
        }

        // Build BrainContext in parallel
        val ctx: BrainContext = try {
            buildContext(isMorning)
        } catch (e: Exception) {
            Log.e("ReflectionManager", "buildContext failed", e)
            return@withContext "Failed to gather context for reflection."
        }

        if (ctx.memories.isEmpty()) {
            return@withContext "No sufficient data found to generate a reflection yet."
        }

        // Delegate prompt construction to ReflectionAgent
        val reflectionAgent = ReflectionAgent()
        val prompt = reflectionAgent.buildPrompt(reflectionType, ctx)

        // Route through ModelRouter
        val complexity = ModelRouter.Complexity.MEDIUM
        val (summaryText, modelUsed) = try {
            modelRouter.run(prompt, complexity)
        } catch (e: Exception) {
            Log.e("ReflectionManager", "ModelRouter failed", e)
            "Reflection failed: ${e.message}" to "Error"
        }

        // Persist result
        val summaryEntity = DailySummaryEntity(
            date = todayStr,
            type = summaryType,
            summary = summaryText,
            timestamp = System.currentTimeMillis(),
            modelName = modelUsed
        )
        memoryRepository.insertSummary(summaryEntity)
        return@withContext null // null = success
    }

    // ── Weekly correlation ────────────────────────────────────────────────

    /**
     * Generates the weekly cross-dimensional correlation insight.
     * Uses HIGH complexity routing (gemini-2.5-flash) since this requires
     * multi-source reasoning across 7 days of data.
     */
    override suspend fun generateWeeklyCorrelation(): String? = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().time)

        val ctx: BrainContext = try {
            // Weekly context: past 7 days of memories + weekly health trends
            val startCal = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -6)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            coroutineScope {
                val memoriesDeferred = async { memoryAgent.retrieveSince(startCal.timeInMillis).map { it.toDomain() } }
                val longTermDeferred = async { try { memoryAgent.recallForContext("") } catch (e: Exception) { emptyList() } }
                val healthPairDeferred = async { healthAgent.fetchAll() }
                val weeklyTrendsDeferred = async { healthAgent.fetchWeeklyTrends() }
                val usageDeferred = async {
                    try { usageRepository.getUsageStatsForTodaySync() } catch (e: Exception) { emptyList() }
                }

                val memories = memoriesDeferred.await()
                val longTermMemories = longTermDeferred.await()
                val (healthCtx, meditationCtx) = healthPairDeferred.await()
                val weeklyTrends = weeklyTrendsDeferred.await()
                val usage = usageDeferred.await()

                BrainContext(
                    memories = memories,
                    health = healthCtx.copy(weeklyTrends = weeklyTrends),
                    usageStats = usage,
                    meditation = meditationCtx,
                    longTermMemories = longTermMemories
                )
            }
        } catch (e: Exception) {
            Log.e("ReflectionManager", "buildContext (weekly) failed", e)
            return@withContext "Failed to gather weekly context."
        }

        val reflectionAgent = ReflectionAgent()
        val prompt = reflectionAgent.buildPrompt(ReflectionAgent.ReflectionType.WEEKLY_CORRELATION, ctx)

        val (summaryText, modelUsed) = try {
            modelRouter.run(prompt, ModelRouter.Complexity.HIGH)
        } catch (e: Exception) {
            Log.e("ReflectionManager", "ModelRouter (weekly) failed", e)
            "Weekly analysis failed: ${e.message}" to "Error"
        }

        val summaryEntity = DailySummaryEntity(
            date = todayStr,
            type = "weekly_correlation",
            summary = summaryText,
            timestamp = System.currentTimeMillis(),
            modelName = modelUsed
        )
        memoryRepository.insertSummary(summaryEntity)
        return@withContext null
    }

    // ── Legacy shim — kept for ReflectionWorker compatibility ────────────

    /**
     * @deprecated Use OrchestratorAgent.chat() instead.
     * Kept to avoid breaking ReflectionWorker until full migration is complete.
     */
    suspend fun runChatInference(promptContext: String): Pair<String, String> =
        modelRouter.run(promptContext, ModelRouter.Complexity.LOW)

    // ── Private helpers ───────────────────────────────────────────────────

    // ── Tomorrow Forecast ─────────────────────────────────────────────────

    suspend fun generateTomorrowForecast(): String? = withContext(Dispatchers.IO) {
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Calendar.getInstance().time)

        val ctx = try {
            buildContext(isMorning = false)
        } catch (e: Exception) {
            Log.e("ReflectionManager", "buildContext (tomorrow) failed", e)
            return@withContext "Failed to gather context for tomorrow forecast."
        }

        val reflectionAgent = ReflectionAgent()
        val prompt = reflectionAgent.buildPrompt(ReflectionAgent.ReflectionType.TOMORROW_FORECAST, ctx)
        val (summaryText, modelUsed) = try {
            modelRouter.run(prompt, ModelRouter.Complexity.MEDIUM)
        } catch (e: Exception) {
            Log.e("ReflectionManager", "ModelRouter (tomorrow) failed", e)
            "Tomorrow forecast failed: ${e.message}" to "Error"
        }

        memoryRepository.insertSummary(DailySummaryEntity(
            date = todayStr,
            type = "tomorrow_forecast",
            summary = summaryText,
            timestamp = System.currentTimeMillis(),
            modelName = modelUsed
        ))
        return@withContext null
    }

    // ── Private helpers ───────────────────────────────────────────────────

    private suspend fun buildContext(isMorning: Boolean): BrainContext {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        val searchStart = if (isMorning) startOfToday - (12 * 60 * 60 * 1000L) else startOfToday

        return coroutineScope {
            val memoriesDeferred = async { memoryAgent.retrieveSince(searchStart).map { it.toDomain() } }
            val longTermDeferred = async { try { memoryAgent.recallForContext("") } catch (e: Exception) { emptyList() } }
            val healthPairDeferred = async { healthAgent.fetchAll() }
            val usageDeferred = async {
                try { usageRepository.getUsageStatsForTodaySync() } catch (e: Exception) { emptyList() }
            }
            val driftDeferred = async {
                try {
                    val snapshots = senseOfDayRepository.getRecentSnapshots(28)
                    if (snapshots.size < 7) return@async DriftContext()
                    val thisWeek = snapshots.takeLast(7)
                    fun avg(fn: (com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshot) -> Float, list: List<com.alex.a2ndbrain.core.senseofday.SenseOfDaySnapshot>) =
                        if (list.isEmpty()) 0f else list.map(fn).average().toFloat()
                    DriftContext(
                        currentWeek = PillarAverages(
                            steps = avg({ it.stepsProgress }, thisWeek),
                            sleep = avg({ it.sleepProgress }, thisWeek),
                            exercise = avg({ it.exerciseProgress }, thisWeek),
                            focus = avg({ it.focusProgress }, thisWeek),
                            overall = avg({ it.score.toFloat() }, thisWeek)
                        ),
                        fourWeekRolling = PillarAverages(
                            steps = avg({ it.stepsProgress }, snapshots),
                            sleep = avg({ it.sleepProgress }, snapshots),
                            exercise = avg({ it.exerciseProgress }, snapshots),
                            focus = avg({ it.focusProgress }, snapshots),
                            overall = avg({ it.score.toFloat() }, snapshots)
                        ),
                        hasEnoughData = snapshots.size >= 14
                    )
                } catch (e: Exception) { DriftContext() }
            }

            val memories = memoriesDeferred.await()
            val longTermMemories = longTermDeferred.await()
            val (healthCtx, meditationCtx) = healthPairDeferred.await()
            val usage = usageDeferred.await()
            val driftCtx = driftDeferred.await()

            BrainContext(
                memories = memories,
                health = healthCtx,
                usageStats = usage,
                meditation = meditationCtx,
                drift = driftCtx,
                longTermMemories = longTermMemories
            )
        }
    }
}
