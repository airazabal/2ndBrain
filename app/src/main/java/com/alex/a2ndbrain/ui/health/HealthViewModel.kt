package com.alex.a2ndbrain.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.health.DailyHealthMetrics
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthDao
import com.alex.a2ndbrain.core.health.toDailyMetrics
import com.alex.a2ndbrain.core.sync.NearbySyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class HealthPeriod { TODAY, WEEK, MONTH }

class HealthViewModel(
    private val healthConnectManager: HealthConnectManager,
    private val healthDao: HealthDao,
    private val nearbySyncManager: NearbySyncManager
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(HealthPeriod.TODAY)
    val selectedPeriod = _selectedPeriod.asStateFlow()

    private val _dailyMetrics = MutableStateFlow<List<DailyHealthMetrics>>(emptyList())
    val dailyMetrics = _dailyMetrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _healthAvailable = MutableStateFlow(false)
    val healthAvailable = _healthAvailable.asStateFlow()

    private val _lastRefreshedMs = MutableStateFlow(0L)
    val lastRefreshedMs = _lastRefreshedMs.asStateFlow()

    init {
        // Don't request a sync on init — HomeViewModel.checkHealthPermissionsAndSync()
        // already triggers requestImmediateSync() for no-HC devices. The healthSyncTrigger
        // observer below reloads the DB when new data arrives from the phone.
        loadPeriod(HealthPeriod.TODAY, requestSync = false)
        viewModelScope.launch {
            nearbySyncManager.healthSyncTrigger.collect {
                loadPeriod(_selectedPeriod.value, requestSync = false)
            }
        }
    }

    fun selectPeriod(period: HealthPeriod) {
        _selectedPeriod.value = period
        loadPeriod(period)
    }

    fun refresh() = loadPeriod(_selectedPeriod.value)

    private fun loadPeriod(period: HealthPeriod, requestSync: Boolean = true) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val days = when (period) {
                HealthPeriod.TODAY -> 1
                HealthPeriod.WEEK  -> 7
                HealthPeriod.MONTH -> 30
            }
            val hasHC = healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()
            // If HC is available but has no wearable data across any day (no sleep,
            // no HR), this device has HC permissions without a paired watch (e.g. a tablet).
            // Fall through to the synced DB path so we show the phone's Zepp data.
            val hcData = if (hasHC) healthConnectManager.fetchDailyBreakdown(days) else null
            val hasWearableData = hcData?.any { it.hasSleep || it.hasHeart } == true
            _healthAvailable.value = hasWearableData
            val metrics = if (hasWearableData) {
                hcData!!
            } else {
                if (requestSync) {
                    // Request a fresh push from the phone. Fresh data arrives via
                    // healthSyncTrigger, which calls loadPeriod(requestSync=false)
                    // to reload the DB without triggering another sync cycle.
                    nearbySyncManager.requestImmediateSync()
                }
                val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                val sinceDate = fmt.format(
                    Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }.time
                )
                val snapshots = healthDao.getSnapshotsSince(sinceDate)
                // Mirror the phone's sleep fallback: if today's snapshot has partial sleep
                // (< 2h), use yesterday's snapshot sleep value instead.
                val todayStr = fmt.format(java.util.Date())
                val yesterdayStr = fmt.format(
                    Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }.time
                )
                val yesterdaySleep = snapshots.firstOrNull { it.date == yesterdayStr }?.sleepMinutes ?: 0
                snapshots.map { snap ->
                    val corrected = if (snap.date == todayStr && snap.sleepMinutes < 120 && yesterdaySleep > snap.sleepMinutes)
                        snap.copy(sleepMinutes = yesterdaySleep)
                    else snap
                    corrected.toDailyMetrics()
                }
            }

            _dailyMetrics.value = metrics.sortedByDescending { it.date }
            _lastRefreshedMs.value = System.currentTimeMillis()
            _isLoading.value = false
        }
    }
}
