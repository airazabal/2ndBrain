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
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.MemoryDao
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.meditation.MeditationRepository
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
    private val memoryDao: MemoryDao by inject()
    private val usageRepository: UsageRepository by inject()
    private val meditationRepository: MeditationRepository by inject()
    private val settingsManager: CaptureSettingsManager by inject()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val health = healthRepository.getTodayMetrics()
            val steps = health.steps.toInt()

            // Count Todoist notifications captured today
            val startOfToday = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val todoistCount = memoryDao.getMemoriesByPackageSync("com.todoist")
                .count { it.timestamp >= startOfToday }

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

            val stepsRatio = (steps.toFloat() / settingsManager.getStepsGoal().toFloat()).coerceIn(0f, 1f)
            val score = ((stepsRatio * 25f) + (focusRatio * 55f) + (if (meditated) 20f else 0f))
                .toInt().coerceIn(10, 100)

            val tasksText = if (todoistCount > 99) "99+" else "$todoistCount"

            val manager = GlanceAppWidgetManager(context)
            val glanceIds = manager.getGlanceIds(BrainWidget::class.java)
            glanceIds.forEach { glanceId ->
                updateAppWidgetState(context, glanceId) { prefs: MutablePreferences ->
                    prefs[BrainWidget.PREF_SCORE] = score
                    prefs[BrainWidget.PREF_STEPS] = steps
                    prefs[BrainWidget.PREF_HABITS] = tasksText
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
