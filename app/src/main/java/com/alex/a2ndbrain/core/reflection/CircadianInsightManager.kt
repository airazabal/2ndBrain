package com.alex.a2ndbrain.core.reflection

import android.content.Context
import androidx.work.*
import com.alex.a2ndbrain.core.agents.ModelRouter
import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.habits.HabitsDao
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class CircadianInsightManager(
    private val context: Context,
    private val memoryDao: MemoryDao,
    private val habitsDao: HabitsDao,
    private val exerciseRepository: ExerciseRepository,
    private val modelRouter: ModelRouter
) {
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    suspend fun generateInsight(): String? = withContext(Dispatchers.IO) {
        val windowMs = 28L * 24 * 60 * 60 * 1000
        val cutoffMs = System.currentTimeMillis() - windowMs
        val cutoffDate = dateFormat.format(Date(cutoffMs))

        // Accumulate event counts per hour (0–23) from three sources
        val notifByHour = IntArray(24)
        val habitsByHour = IntArray(24)
        val exerciseByHour = IntArray(24)

        memoryDao.getMemoriesSince(cutoffMs).forEach { mem ->
            val h = hourOf(mem.timestamp)
            notifByHour[h]++
        }

        habitsDao.getAllCompletionsSince(cutoffDate).forEach { c ->
            val h = hourOf(c.completedAt)
            habitsByHour[h]++
        }

        exerciseRepository.getRecentSessions(28).filter { it.startedAt > 0L }.forEach { s ->
            val h = hourOf(s.startedAt)
            exerciseByHour[h]++
        }

        val totalByHour = IntArray(24) { i -> notifByHour[i] + habitsByHour[i] + exerciseByHour[i] }
        val maxCount = totalByHour.max().coerceAtLeast(1)

        // Compact histogram for hours 5 am – 10 pm
        val histogram = (5..22).joinToString("\n") { h ->
            val bar = "█".repeat((totalByHour[h].toFloat() / maxCount * 12).toInt().coerceIn(0, 12))
            val label = when {
                h == 0 || h == 12 -> "12${if (h == 0) "am" else "pm"}"
                h < 12 -> "${h}am"
                else -> "${h - 12}pm"
            }
            "%-5s %-12s  total=%3d (habits=%d, activity=%d, exercise=%d)".format(
                label, bar, totalByHour[h], habitsByHour[h], notifByHour[h], exerciseByHour[h]
            )
        }

        // Peak 2-hour window (highest combined adjacent hours)
        val peakH = (5..21).maxByOrNull { totalByHour[it] + totalByHour[it + 1] } ?: 9
        val peakLabel = hourLabel(peakH)

        val prompt = """
You are a behavioral scientist analyzing 28 days of a user's activity timestamps to map their natural circadian rhythm.

ACTIVITY DENSITY BY HOUR (5am – 10pm, past 28 days):
$histogram

DATA NOTES:
- "activity" = notification captures (email, messages, apps)
- "habits" = intentional habit completions logged by the user
- "exercise" = workout session start times (when recorded)
- Calculated peak 2-hour window: $peakLabel–${hourLabel(peakH + 2)} (highest combined adjacent activity)

Your task:
1. Identify 2-3 distinct energy phases from the data (e.g., morning focus, afternoon dip, evening recharge).
2. Note any patterns worth optimizing (e.g., late-night notification spikes, missed morning routine hours).
3. Give 2 concrete, time-specific schedule recommendations based on what you observe.

Keep the response concise — 4-6 sentences total. Be specific about hours. Do not mention the raw counts directly.
        """.trimIndent()

        val (text, modelUsed) = try {
            modelRouter.run(prompt, ModelRouter.Complexity.MEDIUM)
        } catch (e: Exception) {
            return@withContext "Circadian analysis failed: ${e.message}"
        }

        val todayStr = dateFormat.format(Date())
        memoryDao.insertSummary(
            DailySummaryEntity(
                date = todayStr,
                type = "circadian_pattern",
                summary = text,
                timestamp = System.currentTimeMillis(),
                modelName = modelUsed
            )
        )
        return@withContext null
    }

    fun scheduleWeeklyAnalysis() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<CircadianAnalysisWorker>(7, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(nextSundayDelayMs(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "circadian_weekly",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    private fun nextSundayDelayMs(): Long {
        val cal = Calendar.getInstance()
        val daysUntilSunday = (Calendar.SUNDAY - cal.get(Calendar.DAY_OF_WEEK) + 7) % 7
        cal.add(Calendar.DAY_OF_YEAR, if (daysUntilSunday == 0) 7 else daysUntilSunday)
        cal.set(Calendar.HOUR_OF_DAY, 8)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return (cal.timeInMillis - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun hourOf(ms: Long): Int =
        Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.HOUR_OF_DAY)

    private fun hourLabel(h: Int): String = when {
        h == 0 || h == 24 -> "12am"
        h == 12 -> "12pm"
        h < 12 -> "${h}am"
        else -> "${h - 12}pm"
    }
}
