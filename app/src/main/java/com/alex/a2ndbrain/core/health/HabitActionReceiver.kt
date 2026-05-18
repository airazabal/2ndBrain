package com.alex.a2ndbrain.core.health

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.memory.HabitCompletionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HabitActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.alex.a2ndbrain.action.MARK_HABIT_DONE") {
            val habitId = intent.getStringExtra("HABIT_ID") ?: return
            val notificationId = intent.getIntExtra("NOTIFICATION_ID", -1)

            Log.d("HabitActionReceiver", "Marking habit $habitId as complete from notification quick-action")

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val todayStr = dateFormat.format(Date())

            val db = AppDatabase.getDatabase(context)
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    // Mark completed in database
                    db.habitsDao().insertCompletion(
                        HabitCompletionEntity(habitId = habitId, date = todayStr)
                    )

                    // Dismiss notification
                    if (notificationId != -1) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        notificationManager.cancel(notificationId)
                    }

                    Log.d("HabitActionReceiver", "Habit $habitId successfully marked complete.")
                } catch (e: Exception) {
                    Log.e("HabitActionReceiver", "Failed to insert habit completion for $habitId", e)
                }
            }
        }
    }
}
