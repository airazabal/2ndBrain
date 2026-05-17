package com.alex.a2ndbrain.core.usage

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class UsageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("UsageSyncWorker", "Starting periodic background sync...")
            val database = com.alex.a2ndbrain.core.memory.AppDatabase.getDatabase(applicationContext)
            val settingsManager = com.alex.a2ndbrain.core.capture.CaptureSettingsManager(applicationContext)
            val usageRepository = UsageRepository(database.memoryDao())
            val manager = DigitalTimeManager(applicationContext, usageRepository, settingsManager)
            manager.syncUsageStats()
            Result.success()
        } catch (e: Exception) {
            Log.e("UsageSyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}
