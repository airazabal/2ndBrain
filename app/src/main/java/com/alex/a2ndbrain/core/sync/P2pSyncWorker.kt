package com.alex.a2ndbrain.core.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class P2pSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val nearbySyncManager: NearbySyncManager by inject()

    override suspend fun doWork(): Result {
        // If the app is actively in the foreground (status is not Idle), skip background sync
        // to prevent interrupting the foreground advertising/discovery session.
        val currentStatus = nearbySyncManager.syncStatus.value
        if (currentStatus !is NearbySyncManager.SyncStatus.Idle) {
            Log.d("P2pSyncWorker", "App is in foreground (status: $currentStatus). Skipping background sync.")
            return Result.success()
        }

        Log.d("P2pSyncWorker", "Starting periodic P2P background sync...")
        
        if (!hasSyncPermissions()) {
            Log.d("P2pSyncWorker", "Sync permissions not granted. Skipping background sync.")
            return Result.failure()
        }

        val syncSettings = CaptureSettingsManager(applicationContext)
        try {
            // Start sync in background
            nearbySyncManager.startSync(force = false)

            // Wait for success, failure or timeout (60 seconds)
            val syncResult = withTimeoutOrNull(60000) {
                nearbySyncManager.syncStatus.first { status ->
                    status is NearbySyncManager.SyncStatus.Success ||
                    status is NearbySyncManager.SyncStatus.Failed
                }
            }

            Log.d("P2pSyncWorker", "P2P background sync finished with: $syncResult")

            // Clean up resources immediately to save battery
            nearbySyncManager.stopSync()

            return if (syncResult is NearbySyncManager.SyncStatus.Success) {
                syncSettings.setLastP2pSyncTime(System.currentTimeMillis())
                syncSettings.setLastP2pSyncSuccess(true)
                syncSettings.setConsecutiveP2pSyncFailures(0)
                Result.success()
            } else {
                syncSettings.setLastP2pSyncSuccess(false)
                syncSettings.setConsecutiveP2pSyncFailures(syncSettings.getConsecutiveP2pSyncFailures() + 1)
                Result.retry()
            }
        } catch (e: Exception) {
            Log.e("P2pSyncWorker", "Exception in P2P background sync worker", e)
            nearbySyncManager.stopSync()
            syncSettings.setLastP2pSyncSuccess(false)
            syncSettings.setConsecutiveP2pSyncFailures(syncSettings.getConsecutiveP2pSyncFailures() + 1)
            return Result.failure()
        }
    }

    private fun hasSyncPermissions(): Boolean {
        val permissions = mutableListOf(android.Manifest.permission.ACCESS_FINE_LOCATION)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
            permissions.add(android.Manifest.permission.BLUETOOTH_SCAN)
            permissions.add(android.Manifest.permission.BLUETOOTH_CONNECT)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return permissions.all {
            applicationContext.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }
}
