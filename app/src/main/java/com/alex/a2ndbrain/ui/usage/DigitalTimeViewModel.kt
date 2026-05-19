package com.alex.a2ndbrain.ui.usage

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.alex.a2ndbrain.ConsolidatedUsage
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DigitalTimeViewModel(
    private val digitalTimeManager: DigitalTimeManager,
    private val applicationContext: Context
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(TimePeriod.TODAY)
    val selectedPeriod = _selectedPeriod.asStateFlow()

    private val _isPermissionGranted = MutableStateFlow(digitalTimeManager.isPermissionGranted())
    val isPermissionGranted = _isPermissionGranted.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing = _isSyncing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val usageStats: StateFlow<List<UsageStatEntity>> = _selectedPeriod
        .flatMapLatest { period ->
            val cal = Calendar.getInstance()
            when (period) {
                TimePeriod.TODAY -> {} // Already today
                TimePeriod.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -7)
                TimePeriod.MONTH -> cal.add(Calendar.MONTH, -1)
            }
            val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
            digitalTimeManager.getUsageStatsSinceFlow(startDate)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val consolidatedUsage: StateFlow<List<ConsolidatedUsage>> = usageStats
        .map { statsList ->
            if (statsList.isEmpty()) return@map emptyList()
            statsList.groupBy { it.packageName }
                .map { (packageName, stats) ->
                    val totalTime = stats.sumOf { it.totalTimeVisibleMs }
                    val deviceBreakdown = stats.groupBy { it.deviceName }
                        .mapValues { entry -> entry.value.sumOf { it.totalTimeVisibleMs } }
                    ConsolidatedUsage(
                        packageName = packageName,
                        totalTimeMs = totalTime,
                        deviceBreakdown = deviceBreakdown,
                        lastTimestamp = stats.maxOfOrNull { it.lastTimestamp } ?: 0L
                    )
                }.sortedByDescending { it.totalTimeMs }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun setPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
    }

    fun updatePermissionStatus() {
        _isPermissionGranted.value = digitalTimeManager.isPermissionGranted()
    }

    fun syncUsageStats() {
        viewModelScope.launch {
            if (digitalTimeManager.isPermissionGranted()) {
                _isPermissionGranted.value = true
                _isSyncing.value = true
                try {
                    digitalTimeManager.syncUsageStats()
                } catch (e: Exception) {
                    android.util.Log.e("DigitalTimeVM", "Failed to sync usage stats", e)
                } finally {
                    _isSyncing.value = false
                }
            } else {
                _isPermissionGranted.value = false
            }
        }
    }
}
