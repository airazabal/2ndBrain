package com.alex.a2ndbrain.ui.widget

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alex.a2ndbrain.core.memory.HabitsDao
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.meditation.ZendenceMeditationRepository
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class WidgetUpdateWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val healthRepository: HealthRepository by inject()
    private val habitsDao: HabitsDao by inject()
    private val usageRepository: UsageRepository by inject()
    private val meditationRepository: ZendenceMeditationRepository by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val health = healthRepository.getTodayMetrics()
            val steps = health.steps.toInt()

            val allHabits = habitsDao.getAllHabitsSync()
            val completedIds = habitsDao.getCompletionsForDateSync(today).map { it.habitId }.toSet()
            val active = allHabits.size.coerceAtLeast(1)
            val completed = completedIds.size
            val habitsRatio = completed.toFloat() / active.toFloat()

            val usageStats = usageRepository.getUsageStatsForTodaySync()
            var totalFocusMs = 0L
            var totalSocialMs = 0L
            usageStats.forEach { stat ->
                val pkg = stat.packageName.lowercase()
                if (pkg.contains("todoist") || pkg.contains("calendar") ||
                    pkg.contains("chrome") || pkg.contains("keep") || pkg.contains("brain")
                ) {
                    totalFocusMs += stat.totalTimeVisibleMs
                } else if (pkg.contains("instagram") || pkg.contains("facebook") ||
                    pkg.contains("tiktok") || pkg.contains("youtube") ||
                    pkg.contains("twitter") || pkg.contains("x.android")
                ) {
                    totalSocialMs += stat.totalTimeVisibleMs
                }
            }
            val totalTime = totalFocusMs + totalSocialMs
            val focusRatio = if (totalTime == 0L) 1f else totalFocusMs.toFloat() / totalTime.toFloat()

            val sessions = meditationRepository.loadSessions()
            val meditated = sessions.any { dateFormat.format(Date(it.timestamp)) == today }

            val stepsRatio = (steps.toFloat() / 10_000f).coerceIn(0f, 1f)
            val score = ((habitsRatio * 25f) + (stepsRatio * 25f) + (focusRatio * 30f) + (if (meditated) 20f else 0f))
                .toInt().coerceIn(10, 100)

            val habitsText = "$completed/$active"

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(BrainWidget::class.java)
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
                    prefs[BrainWidget.PREF_SCORE] = score
                    prefs[BrainWidget.PREF_STEPS] = steps
                    prefs[BrainWidget.PREF_HABITS] = habitsText
                }
                BrainWidget().update(context, glanceId)
            }

            Result.success()
        } catch (e: Exception) {
            android.util.Log.e("WidgetUpdateWorker", "Failed to update widget", e)
            Result.failure()
        }
    }

    companion object {
        private const val WORK_NAME = "widget_update"

        fun schedule(context: Context) {
            val periodic = PeriodicWorkRequestBuilder<WidgetUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                periodic
            )
        }

        fun runNow(context: Context) {
            val oneShot = OneTimeWorkRequestBuilder<WidgetUpdateWorker>().build()
            WorkManager.getInstance(context).enqueue(oneShot)
        }
    }
}
