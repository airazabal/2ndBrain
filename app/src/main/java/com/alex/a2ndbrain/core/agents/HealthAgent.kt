package com.alex.a2ndbrain.core.agents

import android.content.Context
import android.util.Log
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.meditation.MeditationManager
import com.alex.a2ndbrain.core.meditation.MeditationSession
import com.alex.a2ndbrain.core.meditation.StreakResult
import com.alex.a2ndbrain.core.meditation.ZendenceMeditationRepository
import com.alex.a2ndbrain.core.memory.HabitsDao
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale

/**
 * HealthAgent — single source of truth for all physical + routine data.
 *
 * Replaces the duplicated fetch logic that previously existed in:
 *   - ReflectionManager.generateDailyReflection()
 *   - ReflectionManager.generateWeeklyCorrelation()
 *   - CopilotViewModel.sendChatMessage()
 *   - HomeViewModel (health metrics)
 *
 * All callers should go through OrchestratorAgent.buildContext() which
 * calls HealthAgent.fetch() once and shares the result.
 */
class HealthAgent(
    private val healthConnectManager: HealthConnectManager,
    private val habitsDao: HabitsDao,
    private val meditationRepository: ZendenceMeditationRepository,
    private val context: Context
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Fetches today's health snapshot. Runs health + habits + meditation in parallel.
     */
    suspend fun fetch(): HealthContext = withContext(Dispatchers.IO) {
        coroutineScope {
            val healthDeferred = async { fetchHealthMetrics() }
            val habitsDeferred = async { fetchHabits() }
            val meditationDeferred = async { fetchMeditation() }

            val health = healthDeferred.await()
            val habits = habitsDeferred.await()
            val meditation = meditationDeferred.await()

            // Merge into HabitsContext + MeditationContext (returned separately in BrainContext)
            // HealthAgent returns HealthContext only; habits/meditation surface separately so
            // ViewModels can still observe them individually via their own StateFlows.
            HealthContext(
                metrics = health,
                isAvailable = healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()
            )
        }
    }

    /**
     * Fetches today's health + habits + meditation in one parallel call.
     * Returns the full trio needed to build BrainContext.
     */
    suspend fun fetchAll(): Triple<HealthContext, HabitsContext, MeditationContext> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val healthDeferred = async { fetchHealthMetrics() }
                val habitsDeferred = async { fetchHabits() }
                val meditationDeferred = async { fetchMeditation() }

                val health = healthDeferred.await()
                val habits = habitsDeferred.await()
                val meditation = meditationDeferred.await()

                Triple(
                    HealthContext(
                        metrics = health,
                        isAvailable = healthConnectManager.isAvailable() &&
                                healthConnectManager.hasPermissions()
                    ),
                    habits,
                    meditation
                )
            }
        }

    /**
     * Fetches weekly health trends for the Reflection weekly correlation report.
     */
    suspend fun fetchWeeklyTrends(): List<Pair<String, HealthMetrics>> =
        withContext(Dispatchers.IO) {
            try {
                if (!healthConnectManager.isAvailable() || !healthConnectManager.hasPermissions()) {
                    return@withContext emptyList()
                }
                val zoneId = ZoneId.systemDefault()
                val localToday = LocalDate.now()
                (6 downTo 0).map { i ->
                    val date = localToday.minusDays(i.toLong())
                    val startInstant = date.atStartOfDay(zoneId).toInstant()
                    val endInstant = if (i == 0) Instant.now()
                    else date.plusDays(1).atStartOfDay(zoneId).toInstant()
                    val metrics = healthConnectManager.fetchHealthMetricsForRange(startInstant, endInstant)
                    date.toString() to metrics
                }
            } catch (e: Exception) {
                Log.e("HealthAgent", "fetchWeeklyTrends failed", e)
                emptyList()
            }
        }

    private suspend fun fetchHealthMetrics(): HealthMetrics {
        return try {
            if (healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()) {
                healthConnectManager.fetchHealthMetricsToday()
            } else {
                HealthMetrics()
            }
        } catch (e: Exception) {
            Log.e("HealthAgent", "fetchHealthMetrics failed", e)
            HealthMetrics()
        }
    }

    private suspend fun fetchHabits(): HabitsContext {
        return try {
            val todayStr = dateFormat.format(Date())
            val habits = habitsDao.getAllHabitsSync()
            val completions = habitsDao.getCompletionsForDateSync(todayStr)
            val completedIds = completions.map { it.habitId }.toSet()
            HabitsContext(
                activeHabits = habits,
                completedHabitIds = completedIds,
                todayDateString = todayStr
            )
        } catch (e: Exception) {
            Log.e("HealthAgent", "fetchHabits failed", e)
            HabitsContext()
        }
    }

    private suspend fun fetchMeditation(): MeditationContext {
        return try {
            val sessions = meditationRepository.loadSessions()
            val streaks = MeditationManager.calculateStreaks(sessions)
            val todayStr = dateFormat.format(Date())
            val meditatedToday = sessions.any { dateFormat.format(Date(it.timestamp)) == todayStr }
            MeditationContext(
                sessions = sessions,
                streaks = streaks,
                meditatedToday = meditatedToday
            )
        } catch (e: Exception) {
            Log.e("HealthAgent", "fetchMeditation failed", e)
            MeditationContext()
        }
    }
}
