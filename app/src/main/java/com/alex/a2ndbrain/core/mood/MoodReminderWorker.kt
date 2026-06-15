package com.alex.a2ndbrain.core.mood

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.*
import java.util.Calendar
import java.util.concurrent.TimeUnit

class MoodReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        if (hour < 7 || hour >= 22) return Result.success()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Mood Check-in", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Daily one-tap prompt to log how you're feeling."
            }
        )

        fun moodAction(emoji: String, mood: Int): NotificationCompat.Action {
            val intent = Intent(MoodQuickLogReceiver.ACTION_LOG_MOOD).apply {
                setClass(context, MoodQuickLogReceiver::class.java)
                putExtra(MoodQuickLogReceiver.EXTRA_MOOD, mood)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE_BASE + mood,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            return NotificationCompat.Action(0, emoji, pi)
        }

        val title = when {
            hour < 12 -> "Morning check-in"
            hour < 17 -> "Afternoon check-in"
            else      -> "Evening check-in"
        }

        nm.notify(
            MoodQuickLogReceiver.NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText("How are you feeling right now?")
                .addAction(moodAction("😩", 1))
                .addAction(moodAction("😕", 2))
                .addAction(moodAction("😐", 3))
                .addAction(moodAction("🙂", 4))
                .addAction(moodAction("😄", 5))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
        )

        return Result.success()
    }

    companion object {
        private const val CHANNEL_ID = "mood_checkin"
        private const val WORK_NAME = "mood_reminder"
        private const val REQUEST_CODE_BASE = 1100

        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<MoodReminderWorker>(24, TimeUnit.HOURS)
                    .setInitialDelay(delayUntil3pm(), TimeUnit.MILLISECONDS)
                    .build()
            )
        }

        private fun delayUntil3pm(): Long {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 15)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (!after(now)) add(Calendar.DAY_OF_YEAR, 1)
            }
            return target.timeInMillis - now.timeInMillis
        }
    }
}
