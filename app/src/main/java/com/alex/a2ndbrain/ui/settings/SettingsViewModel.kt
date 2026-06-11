package com.alex.a2ndbrain.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val settingsManager: CaptureSettingsManager,
    private val nearbySyncManager: com.alex.a2ndbrain.core.sync.NearbySyncManager,
    private val healthRepository: HealthRepository,
    private val applicationContext: Context
) : ViewModel() {

    val syncStatus = nearbySyncManager.syncStatus

    fun getLastSyncedAtMs(): Long = nearbySyncManager.getLastSyncedAtMs()

    fun startNearbySync(force: Boolean = false) {
        nearbySyncManager.startSync(force)
    }

    fun stopNearbySync() {
        nearbySyncManager.stopSync()
    }

    override fun onCleared() {
        super.onCleared()
        nearbySyncManager.stopSync()
    }

    fun deleteUnmonitoredAppData(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            memoryRepository.deleteMemoriesByPackage(packageName)
            usageRepository.deleteUsageStatsByPackage(packageName)
        }
    }

    private val _themePreference = MutableStateFlow(settingsManager.getThemePreference())
    val themePreference = _themePreference.asStateFlow()

    fun saveThemePreference(theme: String) {
        settingsManager.saveThemePreference(theme)
        _themePreference.value = theme
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val monitoredApps = settingsManager.getMonitoredApps()
        val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val memories = memoryRepository.getRecentMemories(ninetyDaysAgo)
        val summaries = memoryRepository.getAllSummariesSync()
        val healthSnapshots = healthRepository.getSnapshotsForSync(90)

        val appsArray = org.json.JSONArray()
        monitoredApps.forEach { appsArray.put(it) }

        val memoriesArray = org.json.JSONArray()
        memories.forEach { m ->
            memoriesArray.put(org.json.JSONObject().apply {
                put("id", m.id)
                put("source", m.source)
                put("packageName", m.packageName ?: "")
                put("title", m.title ?: "")
                put("content", m.content)
                put("tags", m.tags ?: "")
                put("timestamp", m.timestamp)
            })
        }

        val summariesArray = org.json.JSONArray()
        summaries.forEach { s ->
            summariesArray.put(org.json.JSONObject().apply {
                put("date", s.date)
                put("type", s.type)
                put("summary", s.summary)
                put("modelName", s.modelName)
                put("timestamp", s.timestamp)
            })
        }

        val healthArray = org.json.JSONArray()
        healthSnapshots.forEach { snap ->
            healthArray.put(org.json.JSONObject().apply {
                put("date", snap.date)
                put("steps", snap.steps)
                put("sleepMinutes", snap.sleepMinutes)
                put("avgHeartRate", snap.avgHeartRate)
                put("deviceId", snap.deviceId)
            })
        }

        val todoistToken = settingsManager.getTodoistApiToken()
        val geminiKey = settingsManager.getGeminiApiKey()

        org.json.JSONObject().apply {
            put("version", 4)
            put("exportedAt", System.currentTimeMillis())
            put("monitoredApps", appsArray)
            put("memories", memoriesArray)
            put("reflections", summariesArray)
            put("healthSnapshots", healthArray)
            if (todoistToken.isNotBlank()) put("todoistApiToken", todoistToken)
            if (geminiKey.isNotBlank()) put("geminiApiKey", geminiKey)
            // App preferences
            put("geminiModel", settingsManager.getGeminiModel())
            put("preferredModelType", settingsManager.getPreferredModelType())
            put("selectedLiteRTModel", settingsManager.getSelectedLiteRTModel())
            put("themePreference", settingsManager.getThemePreference())
            put("refreshIntervalMinutes", settingsManager.getRefreshIntervalMinutes())
            put("calendarSyncEnabled", settingsManager.isCalendarSyncEnabled())
            // Daily Goals
            put("stepsGoal", settingsManager.getStepsGoal())
            put("sleepGoalHours", settingsManager.getSleepGoalHours())
            put("exerciseGoalMinutes", settingsManager.getExerciseGoalMinutes())
            put("digitalFocusBaselineMinutes", settingsManager.getDigitalFocusBaselineMinutes())
        }.toString(2)
    }

    suspend fun importFromJson(json: String) = withContext(Dispatchers.IO) {
        val obj = org.json.JSONObject(json)

        val appsArray = obj.optJSONArray("monitoredApps")
        if (appsArray != null) {
            val apps = (0 until appsArray.length()).map { appsArray.getString(it) }.toSet()
            settingsManager.saveMonitoredApps(apps)
        }

        obj.optString("todoistApiToken").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveTodoistApiToken(it) }

        obj.optString("geminiApiKey").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveGeminiApiKey(it) }

        // App preferences
        obj.optString("geminiModel").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveGeminiModel(it) }
        obj.optString("preferredModelType").takeIf { it.isNotBlank() }
            ?.let { settingsManager.savePreferredModelType(it) }
        obj.optString("selectedLiteRTModel").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveSelectedLiteRTModel(it) }
        obj.optString("themePreference").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveThemePreference(it) }
        if (obj.has("refreshIntervalMinutes"))
            settingsManager.setRefreshIntervalMinutes(obj.getInt("refreshIntervalMinutes"))
        if (obj.has("calendarSyncEnabled"))
            settingsManager.setCalendarSyncEnabled(obj.getBoolean("calendarSyncEnabled"))

        // Daily Goals
        if (obj.has("stepsGoal"))
            settingsManager.setStepsGoal(obj.getInt("stepsGoal"))
        if (obj.has("sleepGoalHours"))
            settingsManager.setSleepGoalHours(obj.getDouble("sleepGoalHours").toFloat())
        if (obj.has("exerciseGoalMinutes"))
            settingsManager.setExerciseGoalMinutes(obj.getInt("exerciseGoalMinutes"))
        if (obj.has("digitalFocusBaselineMinutes"))
            settingsManager.setDigitalFocusBaselineMinutes(obj.getInt("digitalFocusBaselineMinutes"))
    }
}
