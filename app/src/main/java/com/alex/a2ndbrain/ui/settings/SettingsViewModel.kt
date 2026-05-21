package com.alex.a2ndbrain.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
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
            val habit = HabitEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                timeString = time,
                isMedication = isMedication,
                isActive = true
            )
            habitsDao.insertHabit(habit)
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            habitsDao.deleteHabitById(id)
        }
    }

    fun toggleHabitActive(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitsDao.getHabitById(id)
            if (habit != null) {
                habitsDao.insertHabit(habit.copy(isActive = !habit.isActive))
            }
        }
    }

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) {
        val habits = habitsDao.getAllHabitsSync()
        val monitoredApps = settingsManager.getMonitoredApps()

        val habitsArray = org.json.JSONArray()
        habits.forEach { habit ->
            habitsArray.put(org.json.JSONObject().apply {
                put("id", habit.id)
                put("name", habit.name)
                put("timeString", habit.timeString)
                put("isMedication", habit.isMedication)
                put("isActive", habit.isActive)
                put("createdAt", habit.createdAt)
            })
        }

        val appsArray = org.json.JSONArray()
        monitoredApps.forEach { appsArray.put(it) }

        org.json.JSONObject().apply {
            put("version", 1)
            put("habits", habitsArray)
            put("monitoredApps", appsArray)
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
                habitsDao.insertHabit(HabitEntity(
                    id = h.getString("id"),
                    name = h.getString("name"),
                    timeString = h.getString("timeString"),
                    isMedication = h.getBoolean("isMedication"),
                    isActive = h.optBoolean("isActive", true),
                    createdAt = h.optLong("createdAt", System.currentTimeMillis())
                ))
            }
        }
    }
}
