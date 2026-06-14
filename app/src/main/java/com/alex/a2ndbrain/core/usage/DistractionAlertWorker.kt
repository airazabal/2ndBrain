package com.alex.a2ndbrain.core.usage

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alex.a2ndbrain.MainActivity
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class DistractionAlertWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 7 || hour >= 22) return Result.success()

        if (!isPermissionGranted()) return Result.success()

        val settingsManager = CaptureSettingsManager(context)
        val distractionPackages = settingsManager.getDistractionApps()
        val thresholdMs = settingsManager.getDistractionThresholdMinutes() * 60_000L

        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            .format(Calendar.getInstance().time)
        val prefs = context.getSharedPreferences("distraction_alerts", Context.MODE_PRIVATE)

        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
        }
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            cal.timeInMillis,
            System.currentTimeMillis()
        ) ?: return Result.success()

        val usageByPackage = stats.associate { it.packageName to it.totalTimeVisible }

        for (pkg in distractionPackages) {
            val alertKey = "alerted_${pkg}_$today"
            if (prefs.getBoolean(alertKey, false)) continue

            val ms = usageByPackage[pkg] ?: continue
            if (ms < thresholdMs) continue

            val appName = resolveAppName(pkg) ?: continue
            val minutes = (ms / 60_000).toInt()
            fireNotification(pkg, appName, minutes)
            prefs.edit().putBoolean(alertKey, true).apply()
            Log.d("DistractionAlert", "Fired alert for $appName (${minutes}min today)")
        }

        return Result.success()
    }

    private fun fireNotification(pkg: String, appName: String, minutes: Int) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "distraction_alerts"

        val channel = NotificationChannel(channelId, "Distraction Nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Gentle nudges when you've been on a distracting app for a while."
        }
        nm.createNotificationChannel(channel)

        val openIntent = PendingIntent.getActivity(
            context,
            pkg.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hours = minutes / 60
        val mins = minutes % 60
        val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("$appName · $timeStr today")
            .setContentText("You've spent $timeStr on $appName. Time to refocus?")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        nm.notify(pkg.hashCode(), notification)
    }

    private fun resolveAppName(pkg: String): String? = try {
        val info = context.packageManager.getApplicationInfo(pkg, 0)
        context.packageManager.getApplicationLabel(info).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        null  // app not installed — skip
    }

    private fun isPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        return appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<DistractionAlertWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "distraction_alert",
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
