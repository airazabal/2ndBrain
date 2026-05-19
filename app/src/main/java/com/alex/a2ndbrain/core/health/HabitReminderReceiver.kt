package com.alex.a2ndbrain.core.health

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.alex.a2ndbrain.MainActivity
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HabitReminderReceiver : BroadcastReceiver(), KoinComponent {

    private val habitsDao: com.alex.a2ndbrain.core.memory.HabitsDao by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val habitId = intent.getStringExtra("HABIT_ID") ?: return
        val habitName = intent.getStringExtra("HABIT_NAME") ?: "Routine Task"
        val isMedication = intent.getBooleanExtra("HABIT_IS_MEDICATION", false)

        Log.d("HabitReminderReceiver", "Alarm fired for habit: $habitName ($habitId)")

        // 1. Post notification
        showNotification(context, habitId, habitName, isMedication)

        // 2. Reschedule alarm for tomorrow to maintain self-perpetuating loop
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val habit = habitsDao.getHabitById(habitId)
                if (habit != null && habit.isActive) {
                    val scheduler = HabitAlarmScheduler(context)
                    scheduler.scheduleHabitAlarm(habit)
                }
            } catch (e: Exception) {
                Log.e("HabitReminderReceiver", "Failed to reschedule alarm for $habitId", e)
            }
        }
    }

    private fun showNotification(context: Context, habitId: String, habitName: String, isMedication: Boolean) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "habit_reminders_channel"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Daily Routines & Medication Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies you at scheduled times to complete your morning meds and routines."
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Action 1: Open App
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            habitId.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        // Action 2: Quick-Action Mark Done
        val markDoneIntent = Intent(context, HabitActionReceiver::class.java).apply {
            action = "com.alex.a2ndbrain.action.MARK_HABIT_DONE"
            putExtra("HABIT_ID", habitId)
            putExtra("NOTIFICATION_ID", habitId.hashCode())
        }
        val markDonePendingIntent = PendingIntent.getBroadcast(
            context,
            habitId.hashCode() + 1,
            markDoneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val title = if (isMedication) "💊 Medication Reminder" else "🏃 Routine Routine"
        val text = if (isMedication) "Time to take your medication: $habitName." else "Time for your routine: $habitName."

        // Use standard system notification drawables
        val smallIcon = if (isMedication) {
            android.R.drawable.ic_dialog_alert
        } else {
            android.R.drawable.ic_lock_idle_alarm
        }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                if (isMedication) android.R.drawable.checkbox_on_background else android.R.drawable.ic_media_play,
                "MARK DONE",
                markDonePendingIntent
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)

        notificationManager.notify(habitId.hashCode(), builder.build())
    }
}
