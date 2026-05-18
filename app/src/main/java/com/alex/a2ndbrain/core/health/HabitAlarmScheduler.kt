package com.alex.a2ndbrain.core.health

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alex.a2ndbrain.core.memory.HabitEntity
import java.util.*

class HabitAlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun scheduleHabitAlarm(habit: HabitEntity) {
        if (!habit.isActive) {
            cancelHabitAlarm(habit)
            return
        }

        try {
            val parts = habit.timeString.split(":")
            if (parts.size != 2) return
            val hour = parts[0].toIntOrNull() ?: return
            val minute = parts[1].toIntOrNull() ?: return

            val calendar = Calendar.getInstance().apply {
                timeInMillis = System.currentTimeMillis()
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            // If the time has already passed today, schedule it for tomorrow
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            val intent = Intent(context, HabitReminderReceiver::class.java).apply {
                putExtra("HABIT_ID", habit.id)
                putExtra("HABIT_NAME", habit.name)
                putExtra("HABIT_IS_MEDICATION", habit.isMedication)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                habit.id.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )

            // setAndAllowWhileIdle ensures it fires even in Doze mode, perfect for medications!
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
            Log.d("HabitAlarmScheduler", "Scheduled alarm for habit '${habit.name}' at ${calendar.time}")
        } catch (e: Exception) {
            Log.e("HabitAlarmScheduler", "Failed to schedule alarm for habit ${habit.id}", e)
        }
    }

    fun cancelHabitAlarm(habit: HabitEntity) {
        try {
            val intent = Intent(context, HabitReminderReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                habit.id.hashCode(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE
            )
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent)
                pendingIntent.cancel()
                Log.d("HabitAlarmScheduler", "Cancelled alarm for habit '${habit.name}'")
            }
        } catch (e: Exception) {
            Log.e("HabitAlarmScheduler", "Failed to cancel alarm for habit ${habit.id}", e)
        }
    }
}
