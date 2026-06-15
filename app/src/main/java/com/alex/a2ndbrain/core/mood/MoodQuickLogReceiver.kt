package com.alex.a2ndbrain.core.mood

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class MoodQuickLogReceiver : BroadcastReceiver(), KoinComponent {

    private val moodRepository: MoodRepository by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val mood = intent.getIntExtra(EXTRA_MOOD, -1)
        if (mood !in 1..5) return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                // Mirror mood as energy for a quick one-tap log; full log screen still supports independent values
                moodRepository.logMood(mood = mood, energy = mood)
            } finally {
                pendingResult.finish()
            }
        }

        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .cancel(NOTIFICATION_ID)
    }

    companion object {
        const val ACTION_LOG_MOOD = "com.alex.a2ndbrain.ACTION_MOOD_LOG"
        const val EXTRA_MOOD = "extra_mood"
        const val NOTIFICATION_ID = 9020
    }
}
