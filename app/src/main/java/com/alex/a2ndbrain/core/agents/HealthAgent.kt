package com.alex.a2ndbrain.core.agents

import android.util.Log
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.meditation.MeditationManager
import com.alex.a2ndbrain.core.meditation.MeditationSession
import com.alex.a2ndbrain.core.meditation.StreakResult
import com.alex.a2ndbrain.core.meditation.MeditationRepository
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
    private val meditationRepository: MeditationRepository
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /**
     * Fetches today's health snapshot. Runs health + meditation in parallel.
     */
    suspend fun fetch(): HealthContext = withContext(Dispatchers.IO) {
        coroutineScope {
            val healthDeferred = async { healthRepository.getTodayMetrics() }
            val meditationDeferred = async { fetchMeditation() }

            val health = healthDeferred.await()
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
     * Fetches today's health + meditation in one parallel call.
     * Returns the pair needed to build BrainContext.
     */
    suspend fun fetchAll(): Pair<HealthContext, MeditationContext> =
        withContext(Dispatchers.IO) {
            coroutineScope {
                val healthDeferred     = async { healthRepository.getTodayMetrics() }
                val trendsDeferred     = async { healthRepository.getWeeklyTrends() }
                val meditationDeferred = async { fetchMeditation() }

                val healthMetrics = healthDeferred.await()
                val hcAvailable = healthRepository.healthConnectManager.isAvailable()
                    && healthRepository.healthConnectManager.hasPermissions()
                Pair(
                    HealthContext(
                        metrics      = healthMetrics,
                        isAvailable  = hcAvailable || (healthMetrics.steps > 0 || healthMetrics.sleepMinutes > 0),
                        weeklyTrends = trendsDeferred.await()
                    ),
                    meditationDeferred.await()
                )
            }
        }

    /**
     * Fetches weekly health trends for the Reflection weekly correlation report.
     */
    suspend fun fetchWeeklyTrends(): List<Pair<String, HealthMetrics>> =
        healthRepository.getWeeklyTrends()

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
