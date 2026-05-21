package com.alex.a2ndbrain.ui.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.health.DailyHealthMetrics
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthDao
import com.alex.a2ndbrain.core.health.toDailyMetrics
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
    private val healthDao: HealthDao
) : ViewModel() {

    private val _selectedPeriod = MutableStateFlow(HealthPeriod.TODAY)
    val selectedPeriod = _selectedPeriod.asStateFlow()

    private val _dailyMetrics = MutableStateFlow<List<DailyHealthMetrics>>(emptyList())
    val dailyMetrics = _dailyMetrics.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _healthAvailable = MutableStateFlow(false)
    val healthAvailable = _healthAvailable.asStateFlow()

    init {
        loadPeriod(HealthPeriod.TODAY)
    }

    fun selectPeriod(period: HealthPeriod) {
        _selectedPeriod.value = period
        loadPeriod(period)
    }

    fun refresh() = loadPeriod(_selectedPeriod.value)

    private fun loadPeriod(period: HealthPeriod) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val days = when (period) {
                HealthPeriod.TODAY -> 1
                HealthPeriod.WEEK  -> 7
                HealthPeriod.MONTH -> 30
            }
            val hasHC = healthConnectManager.isAvailable() && healthConnectManager.hasPermissions()
            _healthAvailable.value = hasHC

            val metrics = if (hasHC) {
                healthConnectManager.fetchDailyBreakdown(days)
            } else {
                // Fall back to synced snapshots from DB (received from phone via P2P)
                val sinceDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    .format(Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -(days - 1)) }.time)
                healthDao.getSnapshotsSince(sinceDate).map { it.toDailyMetrics() }
            }

            _dailyMetrics.value = metrics.sortedByDescending { it.date }
            _isLoading.value = false
        }
    }
}
