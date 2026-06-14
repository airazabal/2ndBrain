package com.alex.a2ndbrain.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.domain.ExportBackupUseCase
import com.alex.a2ndbrain.core.domain.ImportBackupUseCase
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
    private val exportBackup: ExportBackupUseCase,
    private val importBackup: ImportBackupUseCase
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

    suspend fun exportBackupJson(): String = withContext(Dispatchers.IO) { exportBackup() }

    suspend fun importFromJson(json: String) = withContext(Dispatchers.IO) { importBackup(json) }
}
