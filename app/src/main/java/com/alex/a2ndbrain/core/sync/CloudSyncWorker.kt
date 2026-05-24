package com.alex.a2ndbrain.core.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.alex.a2ndbrain.core.health.HealthRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.util.concurrent.TimeUnit

class CloudSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val cloudHealthSyncManager: CloudHealthSyncManager by inject()
    private val healthRepository: HealthRepository by inject()

    override suspend fun doWork(): Result {
        return try {
            // Push only if HC is available, permissions granted, AND local snapshot has real
            // wearable data (sleep or steps > 0). A device with HC permissions but no wearable
            // (e.g. tablet) would otherwise overwrite valid phone data with zeroes.
            val hcReady = healthRepository.healthConnectManager.isAvailable() &&
                healthRepository.healthConnectManager.hasPermissions()
            val localSnap = if (hcReady) healthRepository.getSnapshotsForSync(1).firstOrNull() else null
            val hasWearableData = localSnap != null &&
                (localSnap.sleepMinutes > 0 || localSnap.steps > 0 || localSnap.avgHeartRate > 0)
            val canPush = hcReady && hasWearableData
            Log.d("CloudSyncWorker", "hcReady=$hcReady hasWearableData=$hasWearableData canPush=$canPush")
            if (canPush) {
                cloudHealthSyncManager.pushTodaySnapshot()
                Log.d("CloudSyncWorker", "Push complete")
            } else {
                val pulled = cloudHealthSyncManager.pullTodaySnapshot()
                if (pulled != null) {
                    healthRepository.saveSnapshot(pulled)
                    Log.d("CloudSyncWorker", "Pull complete: sleep=${pulled.sleepMinutes}min steps=${pulled.steps}")
                } else {
                    Log.d("CloudSyncWorker", "Pull returned null (Firestore empty or sign-in failed)")
                }
            }
            Result.success()
        } catch (e: Exception) {
            Log.e("CloudSyncWorker", "Cloud health sync failed", e)
            Result.retry()
        }
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val request = PeriodicWorkRequestBuilder<CloudSyncWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "cloud_health_sync",
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
            // Also run once immediately so first push doesn't wait 30 min
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<CloudSyncWorker>()
                    .setConstraints(constraints)
                    .build()
            )
        }

        fun runNow(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            WorkManager.getInstance(context).enqueue(
                OneTimeWorkRequestBuilder<CloudSyncWorker>()
                    .setConstraints(constraints)
                    .build()
            )
        }
    }
}
