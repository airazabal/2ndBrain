package com.alex.a2ndbrain.core.todoist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alex.a2ndbrain.MainActivity
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class TodoistReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 7 || hour >= 22) return Result.success()

        val repository = TodoistRepository(CaptureSettingsManager(context))

        val tasks = try {
            repository.getTodayTasks()
        } catch (e: Exception) {
            Log.e("TodoistReminder", "Failed to fetch tasks", e)
            return Result.success()
        }

        Log.d("TodoistReminder", "Fetched ${tasks.size} incomplete task(s) for today")

        if (tasks.isEmpty()) return Result.success()

        fireNotification(tasks)
        return Result.success()
    }

    private fun fireNotification(tasks: List<TodoistTask>) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Todoist Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Hourly reminders for incomplete tasks due today."
            }
        )

        val openIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (tasks.size == 1) "1 task still pending" else "${tasks.size} tasks still pending"
        val body = tasks.take(5).joinToString(" · ") { it.content }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
        Log.d("TodoistReminder", "Notification fired: $title — $body")
    }

    companion object {
        private const val CHANNEL_ID = "task_reminders_v2"
        private const val NOTIFICATION_ID = 9001
        private const val WORK_NAME = "todoist_reminder"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TodoistReminderWorker>(1, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun runNow(context: Context) {
            WorkManager.getInstance(context).enqueue(OneTimeWorkRequestBuilder<TodoistReminderWorker>().build())
        }
    }
}
