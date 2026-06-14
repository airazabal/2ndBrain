package com.alex.a2ndbrain.ui.reflection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.capture.SettingsRepository
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.domain.GenerateWeeklyInsightUseCase
import com.alex.a2ndbrain.core.domain.GetWeeklyHealthTrendsUseCase
import com.alex.a2ndbrain.core.reflection.CircadianInsightManager
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class ReflectionViewModel(
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val getWeeklyHealthTrends: GetWeeklyHealthTrendsUseCase,
    private val generateWeeklyInsight: GenerateWeeklyInsightUseCase,
    private val settingsManager: SettingsRepository,
    private val reflectionManager: ReflectionManager,
    private val circadianInsightManager: CircadianInsightManager
) : ViewModel() {

    val summaries: StateFlow<List<DailySummaryEntity>> = memoryRepository.getAllSummariesFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val weeklyUsageStats: StateFlow<List<UsageStatEntity>> = flow<List<UsageStatEntity>> {
        val cal = Calendar.getInstance()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val startDate = dateFormat.format(cal.time)
        emitAll(usageRepository.getUsageStatsSinceFlow(startDate))
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _weeklyHealthTrends = MutableStateFlow<List<Pair<String, HealthMetrics>>>(emptyList())
    val weeklyHealthTrends = _weeklyHealthTrends.asStateFlow()

    val isGeneratingReflection: StateFlow<Boolean> = reflectionManager.getManualReflectionRunning()
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _isGeneratingWeeklyInsight = MutableStateFlow(false)
    val isGeneratingWeeklyInsight = _isGeneratingWeeklyInsight.asStateFlow()

    private val _isGeneratingTomorrowForecast = MutableStateFlow(false)
    val isGeneratingTomorrowForecast = _isGeneratingTomorrowForecast.asStateFlow()

    private val _isGeneratingCircadian = MutableStateFlow(false)
    val isGeneratingCircadian = _isGeneratingCircadian.asStateFlow()

    init {
        loadWeeklyHealthTrends()
    }

    fun loadWeeklyHealthTrends() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _weeklyHealthTrends.value = getWeeklyHealthTrends()
            } catch (e: Exception) {
                android.util.Log.e("ReflectionViewModel", "loadWeeklyHealthTrends failed", e)
            }
        }
    }

    fun generateReflection() {
        reflectionManager.enqueueManualReflection()
    }

    fun cancelReflection() {
        reflectionManager.cancelManualReflection()
    }

    fun clearAllSummaries() {
        viewModelScope.launch {
            memoryRepository.clearAllSummaries()
        }
    }

    fun deleteSummary(id: Long) {
        viewModelScope.launch {
            memoryRepository.deleteSummary(id)
        }
    }

    fun generateWeeklyInsight() {
        _isGeneratingWeeklyInsight.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                generateWeeklyInsight.invoke()  // explicit invoke — avoids calling this function recursively
            } catch (e: Exception) {
                android.util.Log.e("2ndBrain", "Failed to generate weekly insight", e)
            } finally {
                _isGeneratingWeeklyInsight.value = false
            }
        }
    }

    fun generateTomorrowForecast() {
        _isGeneratingTomorrowForecast.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                reflectionManager.generateTomorrowForecast()
            } catch (e: Exception) {
                android.util.Log.e("2ndBrain", "Failed to generate tomorrow forecast", e)
            } finally {
                _isGeneratingTomorrowForecast.value = false
            }
        }
    }

    fun generateCircadianInsight() {
        _isGeneratingCircadian.value = true
        viewModelScope.launch(Dispatchers.IO) {
            try {
                circadianInsightManager.generateInsight()
            } catch (e: Exception) {
                android.util.Log.e("2ndBrain", "Failed to generate circadian insight", e)
            } finally {
                _isGeneratingCircadian.value = false
            }
        }
    }
}
