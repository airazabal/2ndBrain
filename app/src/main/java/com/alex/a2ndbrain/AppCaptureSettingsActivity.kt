package com.alex.a2ndbrain

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.alex.a2ndbrain.core.capture.SettingsRepository
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.sync.NearbySyncManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.ui.settings.AppCaptureSettingsScreen
import com.alex.a2ndbrain.ui.theme.BrainTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class AppCaptureSettingsActivity : ComponentActivity() {
    private val memoryRepository: MemoryRepository by inject()
    private val usageRepository: UsageRepository by inject()
    private val nearbySyncManager: NearbySyncManager by inject()
    private val settingsManager: SettingsRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val themePreference by remember { mutableStateOf(settingsManager.getThemePreference()) }
            val isDark = when (themePreference) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            BrainTheme(darkTheme = isDark) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val syncStatus by nearbySyncManager.syncStatus.collectAsState(NearbySyncManager.SyncStatus.Idle)
                    val lastSyncTimestamp = (syncStatus as? NearbySyncManager.SyncStatus.Success)?.atMs
                        ?: nearbySyncManager.getLastSyncedAtMs()
                    AppCaptureSettingsScreen(
                        settingsManager = settingsManager,
                        onBack = { finish() },
                        onRestartService = {
                            val cn = android.content.ComponentName(
                                this,
                                com.alex.a2ndbrain.core.capture.NotificationCaptureService::class.java
                            )
                            packageManager.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
                            packageManager.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
                        },
                        onUnmonitoredAppRemoved = { packageName ->
                            lifecycleScope.launch(Dispatchers.IO) {
                                memoryRepository.deleteMemoriesByPackage(packageName)
                                usageRepository.deleteUsageStatsByPackage(packageName)
                            }
                        },
                        syncStatus = syncStatus,
                        lastSyncTimestamp = lastSyncTimestamp,
                        onStartSync = { force -> nearbySyncManager.startSync(force) },
                        onStopSync = { nearbySyncManager.stopSync() }
                    )
                }
            }
        }
    }
}
