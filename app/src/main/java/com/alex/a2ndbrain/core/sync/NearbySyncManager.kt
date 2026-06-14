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
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.habits.HabitCompletionEntity
import com.alex.a2ndbrain.core.exercise.ExerciseSession
import com.alex.a2ndbrain.core.exercise.ExerciseType
import com.alex.a2ndbrain.core.goals.Goal
import com.alex.a2ndbrain.core.goals.GoalType
import com.alex.a2ndbrain.core.goals.GoalRepository
import com.alex.a2ndbrain.core.habits.HabitRepository
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.health.HealthSnapshotEntity
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.mood.MoodLogEntity
import com.alex.a2ndbrain.core.mood.MoodRepository
import com.alex.a2ndbrain.core.todoist.TodoistCompletion
import com.alex.a2ndbrain.core.todoist.TodoistStatsRepository
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
    private val meditationRepository: com.alex.a2ndbrain.core.meditation.MeditationRepository,
    private val healthRepository: HealthRepository,
    private val digitalTimeManager: com.alex.a2ndbrain.core.usage.DigitalTimeManager,
    private val exerciseRepository: ExerciseRepository,
    private val goalRepository: GoalRepository,
    private val moodRepository: MoodRepository,
    private val todoistStatsRepository: TodoistStatsRepository,
    private val memoryRepository: MemoryRepository,
    private val settingsManager: CaptureSettingsManager,
    private val habitRepository: HabitRepository
) {
    private val _meditationSyncTrigger = MutableSharedFlow<Unit>(replay = 0)
    val meditationSyncTrigger = _meditationSyncTrigger.asSharedFlow()

    private val _healthSyncTrigger = MutableSharedFlow<Unit>(replay = 0)
    val healthSyncTrigger = _healthSyncTrigger.asSharedFlow()

    private val _exerciseSyncTrigger = MutableSharedFlow<Unit>(replay = 0)
    val exerciseSyncTrigger = _exerciseSyncTrigger.asSharedFlow()

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
        data class Success(val deviceName: String, val atMs: Long = System.currentTimeMillis()) : SyncStatus()
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

    fun getLastSyncedAtMs(): Long = settingsManager.getLastNearbySyncTimestamp()

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
                val now = System.currentTimeMillis()
                settingsManager.setLastNearbySyncTimestamp(now)
                _syncStatus.value = SyncStatus.Success(remoteName, now)
                
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

                // Fetch exercise sessions modified in the last 30 days (includes tombstones)
                val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
                val exerciseSessions = exerciseRepository.getModifiedSince(thirtyDaysAgo)
                val exerciseJsonArray = JSONArray()
                exerciseSessions.forEach { session ->
                    val json = JSONObject()
                    json.put("id", session.id)
                    json.put("deviceId", session.deviceId)
                    json.put("type", session.type.name)
                    json.put("durationMinutes", session.durationMinutes)
                    json.put("startedAt", session.startedAt)
                    json.put("notes", session.notes)
                    json.put("date", session.date)
                    json.put("createdAt", session.createdAt)
                    json.put("lastModifiedAt", session.lastModifiedAt)
                    json.put("isDeleted", if (session.isDeleted) 1 else 0)
                    exerciseJsonArray.put(json)
                }

                // Serialize habit completions (last 30 days)
                val thirtyDaysAgoDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000))
                val habitCompletions = habitRepository.getRecentCompletions(thirtyDaysAgoDateStr)
                val habitCompletionsArray = JSONArray()
                habitCompletions.forEach { c ->
                    val habit = habitRepository.getById(c.habitId)
                    val json = JSONObject()
                    json.put("habitId", c.habitId)
                    json.put("todoistTaskId", habit?.todoistTaskId ?: "")
                    json.put("date", c.date)
                    json.put("completedAt", c.completedAt)
                    habitCompletionsArray.put(json)
                }

                // Serialize user goal settings so peer stays in sync with phone preferences
                val settingsJson = JSONObject().apply {
                    put("stepsGoal", settingsManager.getStepsGoal())
                    put("sleepGoalHours", settingsManager.getSleepGoalHours())
                    put("exerciseGoalMinutes", settingsManager.getExerciseGoalMinutes())
                    put("digitalFocusBaselineMinutes", settingsManager.getDigitalFocusBaselineMinutes())
                    put("updatedAt", settingsManager.getSettingsUpdatedAt())
                }

                // Serialize daily reflections/briefings (last 7 days)
                val allSummaries = memoryRepository.getAllSummariesSync()
                val sevenDaysAgoMs = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
                val recentSummaries = allSummaries.filter { it.timestamp >= sevenDaysAgoMs }
                val summariesJsonArray = JSONArray()
                recentSummaries.forEach { s ->
                    val json = JSONObject()
                    json.put("date", s.date)
                    json.put("type", s.type)
                    json.put("summary", s.summary)
                    json.put("timestamp", s.timestamp)
                    if (s.modelName != null) json.put("modelName", s.modelName)
                    summariesJsonArray.put(json)
                }

                // Serialize mood logs (last 30 days)
                val thirtyDaysAgoDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000))
                val localMoodLogs = moodRepository.getLogsSince(thirtyDaysAgoDate)
                val moodJsonArray = JSONArray()
                localMoodLogs.forEach { log ->
                    val json = JSONObject()
                    json.put("date", log.date)
                    json.put("timestamp", log.timestamp)
                    json.put("mood", log.mood)
                    json.put("energy", log.energy)
                    json.put("note", log.note)
                    moodJsonArray.put(json)
                }

                // Serialize Todoist completion history
                val localCompletions = todoistStatsRepository.getAllCompletions()
                val completionsJsonArray = JSONArray()
                localCompletions.forEach { c ->
                    val json = JSONObject()
                    json.put("id", c.id)
                    json.put("taskId", c.taskId)
                    json.put("taskContent", c.taskContent)
                    json.put("completedAt", c.completedAt)
                    json.put("date", c.date)
                    completionsJsonArray.put(json)
                }

                // Serialize active goals
                val localGoals = goalRepository.getActiveGoals()
                val goalsJsonArray = JSONArray()
                localGoals.forEach { goal ->
                    val json = JSONObject()
                    json.put("id", goal.id)
                    json.put("title", goal.title)
                    json.put("type", goal.type.name)
                    json.put("targetValue", goal.targetValue)
                    json.put("periodDays", goal.periodDays)
                    json.put("isActive", goal.isActive)
                    json.put("createdAt", goal.createdAt)
                    if (goal.linkedHabitId != null) json.put("linkedHabitId", goal.linkedHabitId)
                    if (goal.linkedExerciseType != null) json.put("linkedExerciseType", goal.linkedExerciseType)
                    goalsJsonArray.put(json)
                }

                val payloadObject = JSONObject()
                payloadObject.put("usage", usageJsonArray)
                payloadObject.put("meditations", meditationJsonArray)
                if (healthJsonArray.length() > 0) payloadObject.put("health", healthJsonArray)
                if (alertsJsonArray.length() > 0) payloadObject.put("alerts", alertsJsonArray)
                if (dismissedJsonArray.length() > 0) payloadObject.put("dismissedAlertIds", dismissedJsonArray)
                if (exerciseJsonArray.length() > 0) payloadObject.put("exercise", exerciseJsonArray)
                if (goalsJsonArray.length() > 0) payloadObject.put("goals", goalsJsonArray)
                if (moodJsonArray.length() > 0) payloadObject.put("mood", moodJsonArray)
                if (completionsJsonArray.length() > 0) payloadObject.put("todoistCompletions", completionsJsonArray)
                if (summariesJsonArray.length() > 0) payloadObject.put("reflections", summariesJsonArray)
                payloadObject.put("settings", settingsJson)
                if (habitCompletionsArray.length() > 0) payloadObject.put("habitCompletions", habitCompletionsArray)

                val payloadBytes = payloadObject.toString().toByteArray()
                connectionsClient.sendPayload(endpointId, Payload.fromBytes(payloadBytes))
                Log.d("NearbySync", "Sent ${filteredStats.size} usage, ${localMeditations.size} meditations, ${healthJsonArray.length()} health, ${alertsJsonArray.length()} alerts, ${exerciseJsonArray.length()} exercise, ${localGoals.size} goals, ${localMoodLogs.size} mood, ${localCompletions.size} task completions")
                val now = System.currentTimeMillis()
                settingsManager.setLastNearbySyncTimestamp(now)
                val remoteName = (_syncStatus.value as? SyncStatus.Syncing)?.deviceName ?: "Device"
                _syncStatus.value = SyncStatus.Success(remoteName, now)
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
                if (jsonObject.has("exercise")) {
                    importExerciseSessions(jsonObject.getJSONArray("exercise"))
                }
                if (jsonObject.has("goals")) {
                    importGoals(jsonObject.getJSONArray("goals"))
                }
                if (jsonObject.has("mood")) {
                    importMoodLogs(jsonObject.getJSONArray("mood"))
                }
                if (jsonObject.has("todoistCompletions")) {
                    importTodoistCompletions(jsonObject.getJSONArray("todoistCompletions"))
                }
                if (jsonObject.has("reflections")) {
                    importReflections(jsonObject.getJSONArray("reflections"))
                }
                if (jsonObject.has("settings")) {
                    importSettings(jsonObject.getJSONObject("settings"))
                }
                if (jsonObject.has("habitCompletions")) {
                    importHabitCompletions(jsonObject.getJSONArray("habitCompletions"))
                }
                // Trigger consolidation so imported events become long-term memories promptly
                scope.launch {
                    try {
                        com.alex.a2ndbrain.core.workers.MemoryConsolidationWorker.runOnce(
                            WorkManager.getInstance(context)
                        )
                    } catch (_: Exception) {}
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

    private fun importExerciseSessions(jsonArray: JSONArray) {
        scope.launch(Dispatchers.IO) {
            try {
                var importedCount = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    val incomingLastModified = obj.getLong("lastModifiedAt")
                    val existing = exerciseRepository.getById(id)
                    if (existing == null || incomingLastModified > existing.lastModifiedAt) {
                        exerciseRepository.upsert(
                            ExerciseSession(
                                id = id,
                                deviceId = obj.getString("deviceId"),
                                type = try { ExerciseType.valueOf(obj.getString("type")) } catch (_: Exception) { ExerciseType.OTHER },
                                durationMinutes = obj.getInt("durationMinutes"),
                                startedAt = obj.getLong("startedAt"),
                                notes = obj.getString("notes"),
                                date = obj.getString("date"),
                                createdAt = obj.getLong("createdAt"),
                                lastModifiedAt = incomingLastModified,
                                isDeleted = obj.getInt("isDeleted") == 1
                            )
                        )
                    }
                }
                Log.d("NearbySync", "Imported $importedCount/${jsonArray.length()} exercise sessions (skipped older/unchanged)")
                if (importedCount > 0) {
                    scope.launch { _exerciseSyncTrigger.emit(Unit) }
                    // Write episodic events for newly imported sessions
                    for (i in 0 until jsonArray.length()) {
                        try {
                            val obj = jsonArray.getJSONObject(i)
                            val type = obj.getString("type")
                            val mins = obj.getInt("durationMinutes")
                            val date = obj.getString("date")
                            val notes = obj.optString("notes", "")
                            val content = buildString {
                                append("$type ${mins}min on $date")
                                if (notes.isNotBlank()) append(": $notes")
                            }
                            memoryRepository.insertEpisodicEvent(content, "exercise")
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to import exercise sessions", e)
            }
        }
    }

    private fun importHabitCompletions(jsonArray: JSONArray) {
        scope.launch(Dispatchers.IO) {
            try {
                var importedCount = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val incomingHabitId = obj.getString("habitId")
                    val todoistTaskId = obj.optString("todoistTaskId").takeIf { it.isNotEmpty() }
                    val date = obj.getString("date")

                    // Remap the habit ID using todoistTaskId so completions from another device
                    // (which may have a different local UUID for the same Todoist habit) are stored
                    // under the correct local habit ID.
                    val localHabitId = if (todoistTaskId != null) {
                        habitRepository.getByTodoistTaskId(todoistTaskId)?.id ?: incomingHabitId
                    } else {
                        incomingHabitId
                    }

                    // Skip if no local habit found for this completion
                    if (habitRepository.getById(localHabitId) == null) continue

                    val existing = habitRepository.getTodayCompletions(date)
                    if (existing.none { it.habitId == localHabitId }) {
                        habitRepository.upsertCompletion(HabitCompletionEntity(
                            habitId = localHabitId,
                            date = date,
                            completedAt = obj.getLong("completedAt")
                        ))
                    }
                }
                if (importedCount > 0) {
                    for (i in 0 until jsonArray.length()) {
                        try {
                            val obj = jsonArray.getJSONObject(i)
                            val date = obj.getString("date")
                            val incomingHabitId = obj.getString("habitId")
                            val todoistTaskId = obj.optString("todoistTaskId").takeIf { it.isNotEmpty() }
                            val localId = if (todoistTaskId != null) habitRepository.getByTodoistTaskId(todoistTaskId)?.id ?: incomingHabitId else incomingHabitId
                            val habit = habitRepository.getById(localId)
                            if (habit != null) {
                                val content = "${habit.emoji} ${habit.name} completed on $date"
                                memoryRepository.insertEpisodicEvent(content, "habit")
                            }
                        } catch (_: Exception) {}
                    }
                }
                Log.d("NearbySync", "Imported $importedCount/${jsonArray.length()} habit completions")
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to import habit completions", e)
            }
        }
    }

    private fun importSettings(obj: JSONObject) {
        try {
            val incomingTs = obj.optLong("updatedAt", 0L)
            val localTs = settingsManager.getSettingsUpdatedAt()
            // Only apply if peer's settings are newer than ours
            if (incomingTs <= localTs) {
                Log.d("NearbySync", "Skipping settings import — local settings are newer ($localTs >= $incomingTs)")
                return
            }
            if (obj.has("stepsGoal")) settingsManager.setStepsGoalLocal(obj.getInt("stepsGoal"))
            if (obj.has("sleepGoalHours")) settingsManager.setSleepGoalHoursLocal(obj.getDouble("sleepGoalHours").toFloat())
            if (obj.has("exerciseGoalMinutes")) settingsManager.setExerciseGoalMinutesLocal(obj.getInt("exerciseGoalMinutes"))
            if (obj.has("digitalFocusBaselineMinutes")) settingsManager.setDigitalFocusBaselineMinutesLocal(obj.getInt("digitalFocusBaselineMinutes"))
            Log.d("NearbySync", "Applied settings from peer (peer ts=$incomingTs)")
        } catch (e: Exception) {
            Log.e("NearbySync", "Failed to import settings", e)
        }
    }

    private fun importReflections(jsonArray: JSONArray) {
        scope.launch(Dispatchers.IO) {
            try {
                val existing = memoryRepository.getAllSummariesSync()
                val existingTimestamps = existing.map { it.timestamp }.toSet()
                var importedCount = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val ts = obj.getLong("timestamp")
                    if (ts !in existingTimestamps) {
                        memoryRepository.insertSummary(DailySummaryEntity(
                            id = 0, // auto-generate
                            date = obj.getString("date"),
                            type = obj.getString("type"),
                            summary = obj.getString("summary"),
                            timestamp = ts,
                            modelName = obj.optString("modelName").takeIf { it.isNotEmpty() }
                        ))
                    }
                }
                Log.d("NearbySync", "Imported $importedCount/${jsonArray.length()} reflections")
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to import reflections", e)
            }
        }
    }

    private fun importMoodLogs(jsonArray: JSONArray) {
        scope.launch(Dispatchers.IO) {
            try {
                val thirtyDaysAgoDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Date(System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000))
                val existingTimestamps = moodRepository.getLogsSince(thirtyDaysAgoDate).map { it.timestamp }.toSet()
                var importedCount = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val ts = obj.getLong("timestamp")
                    if (ts !in existingTimestamps) {
                        moodRepository.insertLog(MoodLogEntity(
                            id = 0, // auto-generate on this device
                            date = obj.getString("date"),
                            timestamp = ts,
                            mood = obj.getInt("mood"),
                            energy = obj.getInt("energy"),
                            note = obj.optString("note", "")
                        ))
                    }
                }
                if (importedCount > 0) {
                    for (i in 0 until jsonArray.length()) {
                        try {
                            val obj = jsonArray.getJSONObject(i)
                            val mood = obj.getInt("mood")
                            val energy = obj.getInt("energy")
                            val note = obj.optString("note", "")
                            val content = buildString {
                                append("Mood $mood/5, Energy $energy/5")
                                if (note.isNotBlank()) append(": $note")
                            }
                            memoryRepository.insertEpisodicEvent(content, "mood")
                        } catch (_: Exception) {}
                    }
                }
                Log.d("NearbySync", "Imported $importedCount/${jsonArray.length()} mood logs")
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to import mood logs", e)
            }
        }
    }

    private fun importTodoistCompletions(jsonArray: JSONArray) {
        scope.launch(Dispatchers.IO) {
            try {
                val existingIds = todoistStatsRepository.getAllCompletions().map { it.id }.toSet()
                var importedCount = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    if (id !in existingIds) {
                        todoistStatsRepository.insertCompletion(TodoistCompletion(
                            id = id,
                            taskId = obj.getString("taskId"),
                            taskContent = obj.getString("taskContent"),
                            completedAt = obj.getLong("completedAt"),
                            date = obj.getString("date")
                        ))
                    }
                }
                if (importedCount > 0) {
                    for (i in 0 until jsonArray.length()) {
                        try {
                            val obj = jsonArray.getJSONObject(i)
                            val content = "Completed task: ${obj.getString("taskContent")}"
                            memoryRepository.insertEpisodicEvent(content, "task")
                        } catch (_: Exception) {}
                    }
                }
                Log.d("NearbySync", "Imported $importedCount/${jsonArray.length()} Todoist completions")
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to import Todoist completions", e)
            }
        }
    }

    private fun importGoals(jsonArray: JSONArray) {
        scope.launch(Dispatchers.IO) {
            try {
                val existingIds = goalRepository.getActiveGoals().map { it.id }.toSet()
                var importedCount = 0
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val id = obj.getString("id")
                    if (id !in existingIds) {
                        goalRepository.upsert(
                            Goal(
                                id = id,
                                title = obj.getString("title"),
                                type = try { GoalType.valueOf(obj.getString("type")) } catch (_: Exception) { GoalType.EXERCISE_SESSIONS },
                                targetValue = obj.getDouble("targetValue").toFloat(),
                                periodDays = obj.getInt("periodDays"),
                                isActive = obj.getInt("isActive") == 1,
                                createdAt = obj.getLong("createdAt"),
                                linkedHabitId = obj.optString("linkedHabitId").takeIf { it.isNotEmpty() },
                                linkedExerciseType = obj.optString("linkedExerciseType").takeIf { it.isNotEmpty() }
                            )
                        )
                    }
                }
                Log.d("NearbySync", "Imported $importedCount/${jsonArray.length()} goals")
            } catch (e: Exception) {
                Log.e("NearbySync", "Failed to import goals", e)
            }
        }
    }
}
