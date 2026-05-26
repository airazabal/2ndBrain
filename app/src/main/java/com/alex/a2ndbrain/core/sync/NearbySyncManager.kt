package com.alex.a2ndbrain.core.sync

import android.content.Context
import android.provider.Settings
import android.util.Log
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.alex.a2ndbrain.ConflictSeverity
import com.alex.a2ndbrain.ConflictType
import com.alex.a2ndbrain.TimelineConflict
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.health.HealthSnapshotEntity
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class NearbySyncManager(
    private val context: Context,
    private val usageRepository: UsageRepository,
    private val scope: CoroutineScope,
    private val meditationRepository: com.alex.a2ndbrain.core.meditation.ZendenceMeditationRepository,
    private val healthRepository: HealthRepository,
    private val digitalTimeManager: com.alex.a2ndbrain.core.usage.DigitalTimeManager
) {
    private val _meditationSyncTrigger = MutableSharedFlow<Unit>(replay = 0)
    val meditationSyncTrigger = _meditationSyncTrigger.asSharedFlow()

    private val _healthSyncTrigger = MutableSharedFlow<Unit>(replay = 0)
    val healthSyncTrigger = _healthSyncTrigger.asSharedFlow()

    // Conflicts from the remote peer, refreshed on every successful sync
    private val _remoteConflicts = MutableStateFlow<List<TimelineConflict>>(emptyList())
    val remoteConflicts = _remoteConflicts.asStateFlow()

    // Dismissed conflict IDs received from peer
    private val _remoteDismissedIds = MutableSharedFlow<Set<String>>(replay = 1)
    val remoteDismissedIds = _remoteDismissedIds.asSharedFlow()

    @Volatile private var localConflictsForSync: List<TimelineConflict> = emptyList()
    @Volatile private var localDismissedForSync: MutableSet<String> = mutableSetOf()

    fun updateLocalConflicts(conflicts: List<TimelineConflict>) {
        localConflictsForSync = conflicts
    }

    fun addDismissedConflict(id: String) {
        localDismissedForSync.add(id)
    }

    // Bypass the 30-second rate limit and initiate a sync immediately.
    // No-ops if a scan/connection/sync is already in progress — the in-flight
    // session will include the latest DB state and there is no benefit in
    // restarting it (which would call stopSyncInternal() and could trigger error 8002).
    fun requestImmediateSync() {
        val s = _syncStatus.value
        if (s is SyncStatus.Scanning || s is SyncStatus.Connecting || s is SyncStatus.Syncing) return
        startSync(force = true)
    }

    // Start advertising+discovering only if currently idle or failed.
    // Safe to call from the phone on every app open — does not interrupt an
    // in-progress scan, connection, or successful sync session.
    fun ensureScanning() {
        val s = _syncStatus.value
        if (s !is SyncStatus.Idle && s !is SyncStatus.Failed) return
        startSync(force = false)
    }

    private val connectionsClient = Nearby.getConnectionsClient(context)
    private val serviceId = "com.alex.a2ndbrain.SYNC"

    private val _syncStatus = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val syncStatus = _syncStatus.asStateFlow()

    private val localDeviceName = android.os.Build.MODEL ?: "Android Device"
    private val localDeviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    // Flag indicating if the current session was initiated by an explicit user action (Sync/Refresh click)
    private var isForcedSync = false

    // Mapping of endpointId -> remoteDeviceId to reliably track remote IDs during connection lifecycles
    private val endpointToDeviceMap = mutableMapOf<String, String>()

    // Rate-limiting map to prevent infinite sync loops (Remote Device ID -> Last Sync/Connection Time)
    private val lastSyncTimes = mutableMapOf<String, Long>()

    sealed class SyncStatus {
        object Idle : SyncStatus()
        object Scanning : SyncStatus()
        data class Connecting(val deviceName: String) : SyncStatus()
        data class Syncing(val deviceName: String) : SyncStatus()
        data class Success(val deviceName: String) : SyncStatus()
        data class Failed(val reason: String) : SyncStatus()
    }

    fun schedulePeriodicP2pSync() {
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<P2pSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "p2p_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun startSync(force: Boolean = false) {
        Log.d("NearbySync", "Starting Nearby Sync (force=$force)...")
        isForcedSync = force
        if (force) {
            lastSyncTimes.clear()
        }
        
        // Reset and stop any active discovery/advertising to clear error 8002
        stopSyncInternal()

        _syncStatus.value = SyncStatus.Scanning
        startAdvertising()
        startDiscovery()
    }

    fun stopSync() {
        Log.d("NearbySync", "Stopping Nearby Sync...")
        stopSyncInternal()
        isForcedSync = false
        _syncStatus.value = SyncStatus.Idle
    }

    private fun stopSyncInternal() {
        try {
            connectionsClient.stopAdvertising()
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        } catch (e: Exception) {
            Log.e("NearbySync", "Error stopping Nearby Sync services", e)
        }
        endpointToDeviceMap.clear()
    }

    private fun startAdvertising() {
        val advertisingName = "$localDeviceName|$localDeviceId"
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        connectionsClient.startAdvertising(
            advertisingName,
            serviceId,
            connectionLifecycleCallback,
            advertisingOptions
        ).addOnFailureListener { e ->
            Log.e("NearbySync", "Advertising failed", e)
            handleSyncFailure("Advertising failed: ${e.message}")
        }
    }

    private fun startDiscovery() {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_STAR)
            .build()

        connectionsClient.startDiscovery(
            serviceId,
            endpointDiscoveryCallback,
            discoveryOptions
        ).addOnFailureListener { e ->
            Log.e("NearbySync", "Discovery failed", e)
            handleSyncFailure("Discovery failed: ${e.message}")
        }
    }

    private fun handleSyncFailure(reason: String) {
        val wasForced = isForcedSync
        _syncStatus.value = SyncStatus.Failed(reason)
        isForcedSync = false

        // Automatically restart scanning after 5 seconds to self-heal from failures.
        // Preserve wasForced so rate limits / tie-breaker stay bypassed on retry.
        scope.launch(Dispatchers.Main) {
            kotlinx.coroutines.delay(5000)
            if (_syncStatus.value is SyncStatus.Failed) {
                Log.d("NearbySync", "Restarting search after failure to recover.")
                startSync(force = wasForced)
            }
        }
    }

    private val endpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            val parts = info.endpointName.split("|")
            val remoteDeviceId = parts.lastOrNull() ?: ""
            val remoteDeviceName = info.endpointName.substringBeforeLast("|", info.endpointName)

            Log.d("NearbySync", "Endpoint found: $remoteDeviceName (ID: $remoteDeviceId)")

            // Ignore self-discovery loops or empty device IDs
            if (remoteDeviceId.isEmpty() || remoteDeviceId == localDeviceId) {
                Log.d("NearbySync", "Discovered self or empty device ID ($remoteDeviceId). Ignoring.")
                return
            }

            endpointToDeviceMap[endpointId] = remoteDeviceId

            // Only enforce rate-limiting and tie-breaking for automatic scans.
            // Bypassed if the user explicitly requested a sync/refresh on this device.
            if (!isForcedSync) {
                // Check 30-second rate-limit for automatic syncs to prevent foreground loop spam
                val lastSync = lastSyncTimes[remoteDeviceId] ?: 0L
                if (System.currentTimeMillis() - lastSync < 30000) {
                    Log.d("NearbySync", "Rate limit active for $remoteDeviceName. Skipping connection request.")
                    return
                }

                // Tie-breaker to prevent both devices requesting connection simultaneously
                if (localDeviceId < remoteDeviceId) {
                    Log.d("NearbySync", "Local device ID ($localDeviceId) is less than remote ($remoteDeviceId). Waiting for remote to initiate.")
                    return
                }
            }

            Log.d("NearbySync", "Initiating connection request to: $remoteDeviceName")
            
            // Set rate limit timestamp IMMEDIATELY to prevent loop spam if connection request fails
            lastSyncTimes[remoteDeviceId] = System.currentTimeMillis()
            
            _syncStatus.value = SyncStatus.Connecting(remoteDeviceName)
            connectionsClient.requestConnection(
                "$localDeviceName|$localDeviceId",
                endpointId,
                connectionLifecycleCallback
            ).addOnFailureListener { e ->
                Log.e("NearbySync", "Request connection failed", e)
                handleSyncFailure("Connection request failed")
            }
        }

        override fun onEndpointLost(endpointId: String) {
            Log.d("NearbySync", "Endpoint lost: $endpointId")
            endpointToDeviceMap.remove(endpointId)
        }
    }

    private val connectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            val parts = info.endpointName.split("|")
            val remoteDeviceId = parts.lastOrNull() ?: ""
            val remoteDeviceName = info.endpointName.substringBeforeLast("|", info.endpointName)
            Log.d("NearbySync", "Connection initiated with: $remoteDeviceName (ID: $remoteDeviceId)")
            
            endpointToDeviceMap[endpointId] = remoteDeviceId
            
            // Mark connection attempt timestamp immediately to enforce 30s rate limiting
            lastSyncTimes[remoteDeviceId] = System.currentTimeMillis()
            
            _syncStatus.value = SyncStatus.Connecting(remoteDeviceName)
            connectionsClient.acceptConnection(endpointId, payloadCallback)
        }

        override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
            val currentStatus = _syncStatus.value
            val remoteName = if (currentStatus is SyncStatus.Connecting) currentStatus.deviceName else "Device"
            val remoteDeviceId = endpointToDeviceMap[endpointId] ?: ""
            
            // Re-enforce rate limit timestamp for this device ID on connection completion
            if (remoteDeviceId.isNotEmpty()) {
                lastSyncTimes[remoteDeviceId] = System.currentTimeMillis()
            }

            when (result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Log.d("NearbySync", "Connected successfully to $remoteName")
                    isForcedSync = false
                    _syncStatus.value = SyncStatus.Syncing(remoteName)
                    sendLocalStats(endpointId)
                }
                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Log.d("NearbySync", "Connection rejected")
                    handleSyncFailure("Connection rejected by $remoteName")
                }
                else -> {
                    Log.e("NearbySync", "Connection failed with code: ${result.status.statusCode}")
                    handleSyncFailure("Connection failed: ${result.status.statusMessage}")
                }
            }
        }

        override fun onDisconnected(endpointId: String) {
            Log.d("NearbySync", "Disconnected from: $endpointId")
            val remoteDeviceId = endpointToDeviceMap[endpointId] ?: ""
            if (remoteDeviceId.isNotEmpty()) {
                lastSyncTimes[remoteDeviceId] = System.currentTimeMillis()
            }
            endpointToDeviceMap.remove(endpointId)

            // Only restart discovery if we failed (to self-heal).
            // On success, we do nothing. Advertising and discovery are already running in the background!
            scope.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(5000)
                if (_syncStatus.value is SyncStatus.Failed) {
                    Log.d("NearbySync", "Sync failed, restarting search to try again.")
                    startSync(force = false)
                }
            }
        }
    }

    private val payloadCallback = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            if (payload.type == Payload.Type.BYTES) {
                val bytes = payload.asBytes() ?: return
                val jsonStr = String(bytes)
                Log.d("NearbySync", "Received payload: $jsonStr")
                importReceivedPayload(jsonStr)
                
                val currentStatus = _syncStatus.value
                val remoteName = if (currentStatus is SyncStatus.Syncing) currentStatus.deviceName else "Device"
                _syncStatus.value = SyncStatus.Success("Synced with $remoteName")
                
                val remoteDeviceId = endpointToDeviceMap[endpointId] ?: ""
                if (remoteDeviceId.isNotEmpty()) {
                    lastSyncTimes[remoteDeviceId] = System.currentTimeMillis()
                }

                // Disconnect after 1 second to ensure both devices completely finish exchanging data,
                // while keeping advertising/discovery running so they can reconnect later.
                scope.launch {
                    kotlinx.coroutines.delay(1000)
                    try {
                        connectionsClient.disconnectFromEndpoint(endpointId)
                    } catch (e: Exception) {
                        Log.e("NearbySync", "Error disconnecting from endpoint", e)
                    }
                }
            }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        }
    }

    private fun sendLocalStats(endpointId: String) {
        scope.launch(Dispatchers.IO) {
            try {
                // Refresh local usage stats from UsageStatsManager before sending,
                // so the peer always receives current data rather than a stale DB snapshot.
                digitalTimeManager.syncUsageStats()

                val calendar = Calendar.getInstance()
                calendar.add(Calendar.DAY_OF_YEAR, -7)
                val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)

                // Fetch local usage stats
                val localStats = usageRepository.getUsageStatsSince(startDate)
                val filteredStats = localStats.filter { it.deviceId == localDeviceId }

                val usageJsonArray = JSONArray()
                filteredStats.forEach { stat ->
                    val json = JSONObject()
                    json.put("date", stat.date)
                    json.put("packageName", stat.packageName)
                    json.put("totalTimeVisibleMs", stat.totalTimeVisibleMs)
                    json.put("deviceId", localDeviceId)
                    json.put("deviceName", localDeviceName)
                    json.put("lastTimestamp", stat.lastTimestamp)
                    usageJsonArray.put(json)
                }

                // Fetch local meditations
                val localMeditations = meditationRepository.loadSessions()
                val meditationJsonArray = JSONArray()
                localMeditations.forEach { session ->
                    val json = JSONObject()
                    json.put("id", session.id)
                    json.put("durationMinutes", session.durationMinutes)
                    json.put("insight", session.insight)
                    json.put("timestamp", session.timestamp)
                    meditationJsonArray.put(json)
                }

                // Fetch health snapshots if Health Connect is available on this device
                val healthJsonArray = JSONArray()
                try {
                    val snapshots = healthRepository.getSnapshotsForSync(7)
                    snapshots.forEach { snapshot ->
                        val json = JSONObject()
                        json.put("date", snapshot.date)
                        json.put("deviceId", snapshot.deviceId)
                        json.put("steps", snapshot.steps)
                        json.put("sleepMinutes", snapshot.sleepMinutes)
                        json.put("minHeartRate", snapshot.minHeartRate)
                        json.put("maxHeartRate", snapshot.maxHeartRate)
                        json.put("avgHeartRate", snapshot.avgHeartRate)
                        json.put("lastTimestamp", snapshot.lastTimestamp)
                        healthJsonArray.put(json)
                    }
                } catch (e: Exception) {
                    Log.w("NearbySync", "Could not fetch health snapshots for sync", e)
                }

                // Serialize current local alerts
                val alertsJsonArray = JSONArray()
                localConflictsForSync.forEach { conflict ->
                    val json = JSONObject()
                    json.put("id", conflict.id)
                    json.put("type", conflict.type.name)
                    json.put("severity", conflict.severity.name)
                    json.put("title", conflict.title)
                    json.put("description", conflict.description)
                    json.put("deepDivePrompt", conflict.deepDivePrompt)
                    val relatedArr = JSONArray()
                    conflict.relatedEventIds.forEach { relatedArr.put(it) }
                    json.put("relatedEventIds", relatedArr)
                    alertsJsonArray.put(json)
                }

                // Serialize dismissed IDs so peer can apply them too
                val dismissedJsonArray = JSONArray()
                localDismissedForSync.forEach { dismissedJsonArray.put(it) }

                val payloadObject = JSONObject()
                payloadObject.put("usage", usageJsonArray)
                payloadObject.put("meditations", meditationJsonArray)
                if (healthJsonArray.length() > 0) payloadObject.put("health", healthJsonArray)
                if (alertsJsonArray.length() > 0) payloadObject.put("alerts", alertsJsonArray)
                if (dismissedJsonArray.length() > 0) payloadObject.put("dismissedAlertIds", dismissedJsonArray)

                val payloadBytes = payloadObject.toString().toByteArray()
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadBytes))
                Log.d("NearbySync", "Sent ${filteredStats.size} usage, ${localMeditations.size} meditations, ${healthJsonArray.length()} health, ${alertsJsonArray.length()} alerts")
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to send local stats", e)
            }
        }
    }

    private fun importReceivedPayload(jsonStr: String) {
        try {
            val trimmed = jsonStr.trim()
            if (trimmed.startsWith("{")) {
                val jsonObject = JSONObject(trimmed)
                if (jsonObject.has("usage")) {
                    val usageArray = jsonObject.getJSONArray("usage")
                    importUsageStats(usageArray)
                }
                if (jsonObject.has("meditations")) {
                    val meditationsArray = jsonObject.getJSONArray("meditations")
                    importMeditations(meditationsArray)
                }
                if (jsonObject.has("health")) {
                    val healthArray = jsonObject.getJSONArray("health")
                    importHealthSnapshots(healthArray)
                }
                if (jsonObject.has("alerts")) {
                    importRemoteAlerts(jsonObject.getJSONArray("alerts"))
                }
                if (jsonObject.has("dismissedAlertIds")) {
                    importRemoteDismissedIds(jsonObject.getJSONArray("dismissedAlertIds"))
                }
            } else if (trimmed.startsWith("[")) {
                val usageArray = JSONArray(trimmed)
                importUsageStats(usageArray)
            }
        } catch (e: Exception) {
            Log.e("NearbySync", "Failed to parse incoming payload", e)
        }
    }

    private fun importUsageStats(jsonArray: JSONArray) {
        scope.launch(Dispatchers.IO) {
            try {
                var importedCount = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val incoming = UsageStatEntity(
                        date = obj.getString("date"),
                        packageName = obj.getString("packageName"),
                        totalTimeVisibleMs = obj.getLong("totalTimeVisibleMs"),
                        deviceId = obj.getString("deviceId"),
                        deviceName = obj.getString("deviceName"),
                        lastTimestamp = obj.getLong("lastTimestamp")
                    )
                    // Only write if there is no local record or the incoming one is more recent.
                    // Prevents a stale sync from overwriting a locally-updated measurement.
                    val existing = usageRepository.getUsageStatByKey(
                        incoming.date, incoming.packageName, incoming.deviceId
                    )
                    if (existing == null || incoming.lastTimestamp > existing.lastTimestamp) {
                        usageRepository.insertUsageStat(incoming)
                        importedCount++
                    }
                }
                Log.d("NearbySync", "Imported $importedCount/${jsonArray.length()} stats (skipped older records)")
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to import received stats", e)
            }
        }
    }

    private fun importMeditations(jsonArray: JSONArray) {
        scope.launch(Dispatchers.IO) {
            try {
                val existingSessions = meditationRepository.loadSessions()
                val existingIds = existingSessions.map { it.id }.toSet()
                var importedCount = 0

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getLong("id")
                    if (!existingIds.contains(id)) {
                        val session = com.alex.a2ndbrain.core.meditation.MeditationSession(
                            id = id,
                            durationMinutes = obj.getInt("durationMinutes"),
                            insight = obj.getString("insight"),
                            timestamp = obj.getLong("timestamp")
                        )
                        meditationRepository.insertSession(session)
                        importedCount++
                    }
                }
                Log.d("NearbySync", "Imported $importedCount new meditations out of ${jsonArray.length()} received")
                scope.launch { _meditationSyncTrigger.emit(Unit) }
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to import received meditations", e)
            }
        }
    }

    private fun importHealthSnapshots(jsonArray: JSONArray) {
        scope.launch(Dispatchers.IO) {
            try {
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    healthRepository.saveSnapshot(
                        HealthSnapshotEntity(
                            date         = obj.getString("date"),
                            deviceId     = obj.getString("deviceId"),
                            steps        = obj.getLong("steps"),
                            sleepMinutes = obj.getInt("sleepMinutes"),
                            minHeartRate = obj.getInt("minHeartRate"),
                            maxHeartRate = obj.getInt("maxHeartRate"),
                            avgHeartRate = obj.getInt("avgHeartRate"),
                            lastTimestamp = obj.getLong("lastTimestamp")
                        )
                    )
                }
                Log.d("NearbySync", "Imported ${jsonArray.length()} health snapshots")
                if (jsonArray.length() > 0) scope.launch { _healthSyncTrigger.emit(Unit) }
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to import health snapshots", e)
            }
        }
    }

    private fun importRemoteAlerts(jsonArray: JSONArray) {
        try {
            val conflicts = mutableListOf<TimelineConflict>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val relatedIds = mutableListOf<String>()
                val relatedArr = obj.optJSONArray("relatedEventIds")
                if (relatedArr != null) {
                    for (j in 0 until relatedArr.length()) relatedIds.add(relatedArr.getString(j))
                }
                conflicts.add(
                    TimelineConflict(
                        id = obj.getString("id"),
                        type = ConflictType.valueOf(obj.getString("type")),
                        severity = ConflictSeverity.valueOf(obj.getString("severity")),
                        title = obj.getString("title"),
                        description = obj.getString("description"),
                        deepDivePrompt = obj.getString("deepDivePrompt"),
                        relatedEventIds = relatedIds
                    )
                )
            }
            _remoteConflicts.value = conflicts
            Log.d("NearbySync", "Received ${conflicts.size} remote alerts")
        } catch (e: Exception) {
            Log.e("NearbySync", "Failed to import remote alerts", e)
        }
    }

    private fun importRemoteDismissedIds(jsonArray: JSONArray) {
        try {
            val ids = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) ids.add(jsonArray.getString(i))
            if (ids.isNotEmpty()) {
                scope.launch { _remoteDismissedIds.emit(ids) }
                Log.d("NearbySync", "Received ${ids.size} remote dismissed alert IDs")
            }
        } catch (e: Exception) {
            Log.e("NearbySync", "Failed to import remote dismissed IDs", e)
        }
    }
}
