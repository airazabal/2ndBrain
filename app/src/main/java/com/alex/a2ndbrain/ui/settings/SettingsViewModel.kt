package com.alex.a2ndbrain.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.memory.HabitEntity
import com.alex.a2ndbrain.core.memory.HabitsDao
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(
    private val habitsDao: HabitsDao,
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val settingsManager: CaptureSettingsManager,
    private val nearbySyncManager: com.alex.a2ndbrain.core.sync.NearbySyncManager,
    private val healthRepository: HealthRepository,
    private val applicationContext: Context
) : ViewModel() {

    val syncStatus = nearbySyncManager.syncStatus

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

    val activeHabits: StateFlow<List<HabitEntity>> = habitsDao.getActiveHabits()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addCustomHabit(name: String, time: String, isMedication: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            habitsDao.insertHabit(HabitEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                timeString = time,
                isMedication = isMedication,
                isActive = true,
                createdAt = now,
                lastModifiedAt = now
            ))
            nearbySyncManager.requestImmediateSync()
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            habitsDao.softDeleteHabit(id)
            nearbySyncManager.requestImmediateSync()
        }
    }

    fun toggleHabitActive(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitsDao.getHabitById(id)
            if (habit != null) {
                habitsDao.insertHabit(habit.copy(
                    isActive = !habit.isActive,
                    lastModifiedAt = System.currentTimeMillis()
                ))
                nearbySyncManager.requestImmediateSync()
            }
        }
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val habits = habitsDao.getAllHabitsSync()
        val monitoredApps = settingsManager.getMonitoredApps()
        val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val memories = memoryRepository.getRecentMemories(ninetyDaysAgo)
        val summaries = memoryRepository.getAllSummariesSync()
        val healthSnapshots = healthRepository.getSnapshotsForSync(90)

        val habitsArray = org.json.JSONArray()
        habits.forEach { h ->
            habitsArray.put(org.json.JSONObject().apply {
                put("id", h.id)
                put("name", h.name)
                put("timeString", h.timeString)
                put("isMedication", h.isMedication)
                put("isActive", h.isActive)
                put("createdAt", h.createdAt)
            })
        }

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

        org.json.JSONObject().apply {
            put("version", 2)
            put("exportedAt", System.currentTimeMillis())
            put("habits", habitsArray)
            put("monitoredApps", appsArray)
            put("memories", memoriesArray)
            put("reflections", summariesArray)
            put("healthSnapshots", healthArray)
        }.toString(2)
    }

    suspend fun importFromJson(json: String) = withContext(Dispatchers.IO) {
        val obj = org.json.JSONObject(json)

        val appsArray = obj.optJSONArray("monitoredApps")
        if (appsArray != null) {
            val apps = (0 until appsArray.length()).map { appsArray.getString(it) }.toSet()
            settingsManager.saveMonitoredApps(apps)
        }

        val habitsArray = obj.optJSONArray("habits")
        if (habitsArray != null) {
            for (i in 0 until habitsArray.length()) {
                val h = habitsArray.getJSONObject(i)
                val createdAt = h.optLong("createdAt", System.currentTimeMillis())
                habitsDao.insertHabit(HabitEntity(
                    id = h.getString("id"),
                    name = h.getString("name"),
                    timeString = h.getString("timeString"),
                    isMedication = h.getBoolean("isMedication"),
                    isActive = h.optBoolean("isActive", true),
                    isDeleted = h.optBoolean("isDeleted", false),
                    createdAt = createdAt,
                    lastModifiedAt = h.optLong("lastModifiedAt", createdAt)
                ))
            }
        }
    }
}
