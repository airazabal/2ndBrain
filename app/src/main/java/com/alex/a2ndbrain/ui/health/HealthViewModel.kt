package com.alex.a2ndbrain.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.capture.SettingsRepository
import com.alex.a2ndbrain.core.health.DailyHealthMetrics
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.sync.NearbySyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class HealthPeriod { TODAY, WEEK, MONTH }

class HealthViewModel(
    private val healthRepository: HealthRepository,
    private val nearbySyncManager: NearbySyncManager,
    private val settingsManager: SettingsRepository
) : ViewModel() {

    val stepsGoal: Int get() = settingsManager.getStepsGoal()

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
            val (metrics, hasWearableData) = healthRepository.getPeriodMetrics(days)
            _healthAvailable.value = hasWearableData
            if (!hasWearableData && requestSync) {
                nearbySyncManager.requestImmediateSync()
            }
            _dailyMetrics.value = metrics.sortedByDescending { it.date }
            _lastRefreshedMs.value = System.currentTimeMillis()
            _isLoading.value = false
        }
    }
}
