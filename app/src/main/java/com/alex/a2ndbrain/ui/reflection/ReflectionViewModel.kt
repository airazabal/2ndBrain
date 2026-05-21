package com.alex.a2ndbrain.ui.reflection

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.reflection.ReflectionManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

class ReflectionViewModel(
    private val memoryRepository: MemoryRepository,
    private val usageRepository: UsageRepository,
    private val reflectionManager: ReflectionManager,
    private val healthConnectManager: HealthConnectManager,
    private val settingsManager: CaptureSettingsManager,
    private val applicationContext: Context,
    private val orchestrator: OrchestratorAgent
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

    val isGeneratingReflection: StateFlow<Boolean> = WorkManager.getInstance(applicationContext)
        .getWorkInfosForUniqueWorkFlow("manual_reflection")
        .map { infos ->
            infos.any { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, false)

    private val _isGeneratingWeeklyInsight = MutableStateFlow(false)
    val isGeneratingWeeklyInsight = _isGeneratingWeeklyInsight.asStateFlow()

    init {
        loadWeeklyHealthTrends()
    }

    fun loadWeeklyHealthTrends() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (healthConnectManager.hasPermissions()) {
                    val zoneId = ZoneId.systemDefault()
                    val localToday = LocalDate.now()
                    val tempTrends = mutableListOf<Pair<String, HealthMetrics>>()
                    for (i in 6 downTo 0) {
                        val date = localToday.minusDays(i.toLong())
                        val startInstant = date.atStartOfDay(zoneId).toInstant()
                        val endInstant = if (i == 0) Instant.now() else date.plusDays(1).atStartOfDay(zoneId).toInstant()
                        val dailyMetrics = healthConnectManager.fetchHealthMetricsForRange(startInstant, endInstant)
                        tempTrends.add(date.toString() to dailyMetrics)
                    }
                    _weeklyHealthTrends.value = tempTrends
                }
            } catch (e: Exception) {
                android.util.Log.e("2ndBrain", "Failed to load weekly health metrics inside ReflectionViewModel", e)
            }
        }
    }

    fun generateReflection() {
        val request = OneTimeWorkRequestBuilder<com.alex.a2ndbrain.core.reflection.ReflectionWorker>().build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            "manual_reflection",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelReflection() {
        WorkManager.getInstance(applicationContext).cancelUniqueWork("manual_reflection")
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
                // Use OrchestratorAgent to build BrainContext (with weekly health trends)
                // then persist the result via the existing ReflectionManager path.
                val ctx = orchestrator.buildContext()
                val ctxWithTrends = ctx.copy(
                    health = ctx.health.copy(
                        weeklyTrends = emptyList() // HealthAgent.fetchWeeklyTrends() wired in next phase
                    )
                )
                val reflectionAgent = ReflectionAgent()
                val prompt = reflectionAgent.buildPrompt(ReflectionAgent.ReflectionType.WEEKLY_CORRELATION, ctxWithTrends)
                // Fall through to legacy path for persistence until ReflectionManager is fully slimmed
                reflectionManager.generateWeeklyCorrelation()
            } catch (e: Exception) {
                android.util.Log.e("2ndBrain", "Failed to generate weekly insight", e)
            } finally {
                _isGeneratingWeeklyInsight.value = false
            }
        }
    }
}
