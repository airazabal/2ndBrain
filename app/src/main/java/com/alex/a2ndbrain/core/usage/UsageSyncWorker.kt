package com.alex.a2ndbrain.core.usage

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class UsageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val digitalTimeManager: DigitalTimeManager by inject()

    override suspend fun doWork(): Result {
        return try {
            Log.d("UsageSyncWorker", "Starting periodic background sync...")
            digitalTimeManager.syncUsageStats()
            Result.success()
        } catch (e: Exception) {
            Log.e("UsageSyncWorker", "Background sync failed", e)
            Result.retry()
        }
    }
}
