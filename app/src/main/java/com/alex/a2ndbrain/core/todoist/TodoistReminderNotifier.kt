package com.alex.a2ndbrain.core.todoist

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.alex.a2ndbrain.MainActivity

class TodoistReminderNotifier(private val context: Context) {

    private val prefs = context.getSharedPreferences("todoist_reminder_prefs", Context.MODE_PRIVATE)
    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun maybeNotify(tasks: List<TodoistTask>) {
        if (tasks.isEmpty()) return
        if (System.currentTimeMillis() - prefs.getLong("last_notified_ms", 0L) < RATE_LIMIT_MS) return

        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Todoist Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Hourly reminders for incomplete tasks due today."
            }
        )

        val openIntent = PendingIntent.getActivity(
            context, NOTIFICATION_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (tasks.size == 1) "1 task still pending" else "${tasks.size} tasks still pending"
        val body = tasks.take(5).joinToString(" · ") { it.content }

        nm.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .build()
        )

        prefs.edit().putLong("last_notified_ms", System.currentTimeMillis()).apply()
    }

    companion object {
        private const val CHANNEL_ID = "task_reminders_v2"
        private const val NOTIFICATION_ID = 9001
        private const val RATE_LIMIT_MS = 60 * 60 * 1000L
    }
}
