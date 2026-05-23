package com.alex.a2ndbrain.core.nudge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alex.a2ndbrain.MainActivity
import com.alex.a2ndbrain.core.memory.MemoryDao
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.Calendar

class UnlockNudgeReceiver : BroadcastReceiver(), KoinComponent {

    private val memoryDao: MemoryDao by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_USER_PRESENT) return

        val cal = Calendar.getInstance()
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        if (hour < 7 || hour >= 22) return

        val prefs = context.getSharedPreferences("unlock_nudge", Context.MODE_PRIVATE)
        val twoHoursMs = 2 * 60 * 60 * 1_000L
        if (System.currentTimeMillis() - prefs.getLong("last_nudge_ms", 0L) < twoHoursMs) return

        prefs.edit().putLong("last_nudge_ms", System.currentTimeMillis()).apply()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val summary = memoryDao.getLatestSummary()
                val body = summary?.summary?.take(140)?.trimEnd()
                    ?.let { if (summary.summary.length > 140) "$it…" else it }
                    ?: "Open your 2nd Brain to review your day."
                showNotification(context, hour, body)
            } catch (e: Exception) {
                Log.e("UnlockNudge", "Failed to build nudge", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun showNotification(context: Context, hour: Int, body: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "unlock_nudge"

        nm.createNotificationChannel(
            NotificationChannel(channelId, "Daily Nudge", NotificationManager.IMPORTANCE_LOW).apply {
                description = "A brief check-in when you unlock your phone."
            }
        )

        val title = when {
            hour < 12 -> "Good morning — here's your briefing"
            hour < 17 -> "Afternoon check-in"
            else -> "Evening reflection"
        }

        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()

        nm.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 9001
    }
}
