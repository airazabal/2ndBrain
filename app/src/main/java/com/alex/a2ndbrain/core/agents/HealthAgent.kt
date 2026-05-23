package com.alex.a2ndbrain.core.agents

import android.util.Log
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.meditation.MeditationManager
import com.alex.a2ndbrain.core.meditation.MeditationSession
import com.alex.a2ndbrain.core.meditation.StreakResult
import com.alex.a2ndbrain.core.meditation.ZendenceMeditationRepository
import com.alex.a2ndbrain.core.memory.HabitsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * HealthAgent — single source of truth for all physical + routine data.
 *
 * All health data access is delegated to HealthRepository, which owns
 * the fallback chain (HC → DB today → DB yesterday sleep).
 */
class HealthAgent(
    private val healthRepository: HealthRepository,
    private val habitsDao: HabitsDao,
    private val meditationRepository: ZendenceMeditationRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Fetches today's health snapshot. Runs health + habits + meditation in parallel.
     */
    suspend fun fetch(): HealthContext = withContext(Dispatchers.IO) {
        coroutineScope {
            val healthDeferred = async { healthRepository.getTodayMetrics() }
            val habitsDeferred = async { fetchHabits() }
            val meditationDeferred = async { fetchMeditation() }

            val health = healthDeferred.await()
            habitsDeferred.await()
            meditationDeferred.await()

            val hcAvailable = healthRepository.healthConnectManager.isAvailable()
                && healthRepository.healthConnectManager.hasPermissions()
            HealthContext(
                metrics = health,
                isAvailable = hcAvailable || (health.steps > 0 || health.sleepMinutes > 0)
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
                val healthDeferred     = async { healthRepository.getTodayMetrics() }
                val trendsDeferred     = async { healthRepository.getWeeklyTrends() }
                val habitsDeferred     = async { fetchHabits() }
                val meditationDeferred = async { fetchMeditation() }

                val healthMetrics = healthDeferred.await()
                val hcAvailable = healthRepository.healthConnectManager.isAvailable()
                    && healthRepository.healthConnectManager.hasPermissions()
                Triple(
                    HealthContext(
                        metrics      = healthMetrics,
                        isAvailable  = hcAvailable || (healthMetrics.steps > 0 || healthMetrics.sleepMinutes > 0),
                        weeklyTrends = trendsDeferred.await()
                    ),
                    habitsDeferred.await(),
                    meditationDeferred.await()
                )
            }
        }

    /**
     * Fetches weekly health trends for the Reflection weekly correlation report.
     */
    suspend fun fetchWeeklyTrends(): List<Pair<String, HealthMetrics>> =
        healthRepository.getWeeklyTrends()

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
