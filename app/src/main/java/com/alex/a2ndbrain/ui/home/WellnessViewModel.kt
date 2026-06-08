package com.alex.a2ndbrain.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.health.HealthConnectManager
import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.meditation.MeditationManager
import com.alex.a2ndbrain.core.meditation.MeditationRepository
import com.alex.a2ndbrain.core.meditation.MeditationSession
import com.alex.a2ndbrain.core.meditation.StreakResult
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import com.alex.a2ndbrain.core.senseofday.SenseOfDayHistoryRepository
import com.alex.a2ndbrain.core.sync.NearbySyncManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WellnessViewModel(
    private val healthRepository: HealthRepository,
    private val exerciseRepository: ExerciseRepository,
    private val usageRepository: UsageRepository,
    private val senseOfDayHistoryRepository: SenseOfDayHistoryRepository,
    private val settingsManager: CaptureSettingsManager,
    private val meditationRepository: MeditationRepository,
    private val nearbySyncManager: NearbySyncManager
) : ViewModel() {

    val healthConnectManager: HealthConnectManager get() = healthRepository.healthConnectManager

    private val _healthMetricsToday = MutableStateFlow(HealthMetrics())
    val healthMetricsToday: StateFlow<HealthMetrics> = _healthMetricsToday.asStateFlow()

    private val _healthPermissionsGranted = MutableStateFlow(false)
    val healthPermissionsGranted: StateFlow<Boolean> = _healthPermissionsGranted.asStateFlow()

    // ── Meditation ────────────────────────────────────────────────────────────
    private val _meditationSessions = MutableStateFlow<List<MeditationSession>>(emptyList())
    val meditationSessions: StateFlow<List<MeditationSession>> = _meditationSessions.asStateFlow()

    val meditationStreaks: StateFlow<StreakResult> = meditationSessions.map { sessions ->
        MeditationManager.calculateStreaks(sessions)
    }.stateIn(viewModelScope, SharingStarted.Lazily, StreakResult(0, 0, 0))

    private val meditatedToday: StateFlow<Boolean> = meditationSessions.map { sessions ->
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        sessions.any { sdf.format(Date(it.timestamp)) == todayStr }
    }.stateIn(viewModelScope, SharingStarted.Lazily, false)

    fun refreshMeditationSessions() {
        viewModelScope.launch(Dispatchers.IO) {
            _meditationSessions.value = meditationRepository.loadSessions()
        }
    }

    // ── Exercise ──────────────────────────────────────────────────────────────
    private val _minuteTicker: StateFlow<Long> = flow {
        while (true) { emit(System.currentTimeMillis()); kotlinx.coroutines.delay(60_000L) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, System.currentTimeMillis())

    val exerciseWeekSessions: StateFlow<Int> =
        exerciseRepository.getAllSessionsFlow()
            .combine(_minuteTicker) { sessions, _ ->
                val cutoff = run {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -6)
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                }
                sessions.count { it.isDeleted == 0 && it.date >= cutoff }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    val exerciseTotalMinutesThisWeek: StateFlow<Int> =
        exerciseRepository.getAllSessionsFlow()
            .combine(_minuteTicker) { sessions, _ ->
                val cutoff = run {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -6)
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
                }
                sessions.filter { it.isDeleted == 0 && it.date >= cutoff }.sumOf { it.durationMinutes }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    private val exerciseTodayMinutes: StateFlow<Int> =
        exerciseRepository.getAllSessionsFlow()
            .combine(_minuteTicker) { sessions, _ ->
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                sessions.filter { it.isDeleted == 0 && it.date == today }.sumOf { it.durationMinutes }
            }
            .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    // Private usage subscription for SenseOfDay focus pillar
    private val usageStats: StateFlow<List<UsageStatEntity>> = _minuteTicker
        .map { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date()) }
        .distinctUntilChanged()
        .flatMapLatest { today -> usageRepository.getUsageStatsForDate(today) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // ── Sense of Day ──────────────────────────────────────────────────────────
    private val _senseOfDayScore = MutableStateFlow(0)
    val senseOfDayScore: StateFlow<Int> = _senseOfDayScore.asStateFlow()

    private val _senseOfDayContext = MutableStateFlow("Calibrating your day...")
    val senseOfDayContext: StateFlow<String> = _senseOfDayContext.asStateFlow()

    private val _senseOfDayPillars = MutableStateFlow<List<SenseOfDayPillar>>(emptyList())
    val senseOfDayPillars: StateFlow<List<SenseOfDayPillar>> = _senseOfDayPillars.asStateFlow()

    // ── Burnout Risk ──────────────────────────────────────────────────────────
    private val _meetingCount = MutableStateFlow(0)
    private val _avgSleepMinutes7d = MutableStateFlow(0)
    private val _burnoutRisk = MutableStateFlow(BurnoutRisk())
    val burnoutRisk: StateFlow<BurnoutRisk> = _burnoutRisk.asStateFlow()

    fun setMeetingCount(count: Int) {
        _meetingCount.value = count
    }

    init {
        refreshMeditationSessions()

        viewModelScope.launch(Dispatchers.IO) { refreshSleepHistory() }

        viewModelScope.launch {
            nearbySyncManager.meditationSyncTrigger.collect {
                android.util.Log.d("WellnessViewModel", "Meditation sync triggered, refreshing sessions")
                refreshMeditationSessions()
            }
        }

        viewModelScope.launch {
            nearbySyncManager.healthSyncTrigger.collect {
                loadSyncedHealthSnapshot()
            }
        }

        // Poll Health Connect every 15 minutes
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                kotlinx.coroutines.delay(15 * 60 * 1000L)
                checkHealthPermissionsAndSync()
            }
        }

        // Recalculate scores whenever health, usage, meditation, or exercise changes
        viewModelScope.launch {
            combine(_healthMetricsToday, usageStats, meditatedToday, exerciseTodayMinutes) { _, _, _, _ -> }
                .collect {
                    updateSenseOfDayScore()
                    updateBurnoutRisk()
                }
        }

        // Also recalculate burnout when meeting count changes
        viewModelScope.launch {
            _meetingCount.collect { updateBurnoutRisk() }
        }
    }

    fun checkHealthPermissionsAndSync() {
        updateSenseOfDayScore()
        viewModelScope.launch(Dispatchers.IO) {
            val hcMetrics = healthRepository.getHCMetricsIfWearable()
            if (hcMetrics != null) {
                _healthPermissionsGranted.value = true
                _healthMetricsToday.value = hcMetrics
                nearbySyncManager.ensureScanning()
                return@launch
            }
            _healthPermissionsGranted.value = false
            nearbySyncManager.requestImmediateSync()
            loadSyncedHealthSnapshot()
        }
    }

    private suspend fun loadSyncedHealthSnapshot() {
        val metrics = healthRepository.getTodayMetrics()
        if (metrics.steps > 0 || metrics.sleepMinutes > 0 || metrics.avgHeartRate > 0) {
            _healthMetricsToday.value = metrics
            _healthPermissionsGranted.value = true
        }
        refreshSleepHistory()
    }

    private suspend fun refreshSleepHistory() {
        val (metrics, _) = healthRepository.getPeriodMetrics(7)
        val daysWithData = metrics.filter { it.hasSleep }
        _avgSleepMinutes7d.value = if (daysWithData.isEmpty()) 0
        else daysWithData.sumOf { it.sleepMinutes } / daysWithData.size
    }

    private fun updateBurnoutRisk() {
        viewModelScope.launch(Dispatchers.Default) {
            val sleepGoalMinutes = (settingsManager.getSleepGoalHours() * 60).toInt()
            val digitalBaseline = settingsManager.getDigitalFocusBaselineMinutes()

            // Sleep deficit: 7-day avg vs goal (fall back to today if no history)
            val avgSleep = if (_avgSleepMinutes7d.value > 0) _avgSleepMinutes7d.value
                           else _healthMetricsToday.value.sleepMinutes
            val sleepScore = if (sleepGoalMinutes == 0 || avgSleep == 0) 0
                else ((1f - avgSleep.toFloat() / sleepGoalMinutes) * 100).toInt().coerceIn(0, 100)

            // Missed workouts: inverse of exercise sessions this week (goal = 3)
            val workoutScore = ((1f - exerciseWeekSessions.value / 3f) * 100).toInt().coerceIn(0, 100)

            // Digital overload: screen time today above baseline (score 0 when at/below baseline)
            val totalMs = usageStats.value.sumOf { it.totalTimeVisibleMs }
            val todayScreenMinutes = (totalMs / 60_000L).toInt()
            val digitalScore = if (todayScreenMinutes <= digitalBaseline) 0
                else ((todayScreenMinutes - digitalBaseline).toFloat() / digitalBaseline * 100).toInt().coerceIn(0, 100)

            // Meeting density: 5+ calendar events = max pressure
            val meetingScore = (_meetingCount.value / 5f * 100).toInt().coerceIn(0, 100)

            val composite = (sleepScore * 0.35f + workoutScore * 0.25f + digitalScore * 0.25f + meetingScore * 0.15f).toInt()

            val level = when {
                composite < 26 -> BurnoutLevel.LOW
                composite < 51 -> BurnoutLevel.MODERATE
                composite < 76 -> BurnoutLevel.HIGH
                else -> BurnoutLevel.CRITICAL
            }

            val drivers = buildList {
                if (sleepScore >= 50) add("sleep deficit")
                if (workoutScore >= 50) add("low activity")
                if (digitalScore >= 50) add("screen overload")
                if (meetingScore >= 50) add("meeting load")
            }.take(2)

            _burnoutRisk.value = BurnoutRisk(
                score = composite,
                level = level,
                sleepScore = sleepScore,
                workoutScore = workoutScore,
                digitalScore = digitalScore,
                meetingScore = meetingScore,
                drivers = drivers
            )
        }
    }

    private fun updateSenseOfDayScore() {
        viewModelScope.launch(Dispatchers.Default) {
            val stepsGoal = settingsManager.getStepsGoal()
            val sleepGoalHours = settingsManager.getSleepGoalHours()
            val exerciseGoalMinutes = settingsManager.getExerciseGoalMinutes()
            val digitalFocusBaseline = settingsManager.getDigitalFocusBaselineMinutes()

            val steps = _healthMetricsToday.value.steps
            val sleepMinutes = _healthMetricsToday.value.sleepMinutes
            val todayExerciseMins = exerciseTodayMinutes.value

            val stepsProgress = (steps.toFloat() / stepsGoal).coerceIn(0f, 1f)
            val sleepProgress = (sleepMinutes / 60f / sleepGoalHours).coerceIn(0f, 1f)
            val exerciseProgress = (todayExerciseMins.toFloat() / exerciseGoalMinutes).coerceIn(0f, 1f)

            val totalFocusMs = usageStats.value.sumOf { it.totalTimeVisibleMs }
            val focusMinutes = (totalFocusMs / 60_000L).toInt()
            val focusProgress = (focusMinutes.toFloat() / digitalFocusBaseline).coerceIn(0f, 1f)

            val score = ((stepsProgress + sleepProgress + exerciseProgress + focusProgress) / 4f * 100f)
                .toInt().coerceIn(0, 100)
            _senseOfDayScore.value = score

            val stepsDisplay = if (steps > 0) "%,d".format(steps) else "0"
            val sleepH = sleepMinutes / 60; val sleepM = sleepMinutes % 60
            val sleepDisplay = if (sleepMinutes > 0) "${sleepH}h ${sleepM}m" else "--"
            val exerciseDisplay = "${todayExerciseMins}m"
            val focusH = focusMinutes / 60; val focusM = focusMinutes % 60
            val focusDisplay = if (focusMinutes >= 60) "${focusH}h ${focusM}m" else "${focusMinutes}m"
            val sleepGoalDisplay = if (sleepGoalHours == sleepGoalHours.toInt().toFloat())
                "${sleepGoalHours.toInt()}h" else "${sleepGoalHours}h"

            _senseOfDayPillars.value = listOf(
                SenseOfDayPillar("Steps", stepsDisplay, "/ %,d".format(stepsGoal), stepsProgress),
                SenseOfDayPillar("Sleep", sleepDisplay, "/ $sleepGoalDisplay", sleepProgress),
                SenseOfDayPillar("Exercise", exerciseDisplay, "/ ${exerciseGoalMinutes}m", exerciseProgress),
                SenseOfDayPillar("Focus", focusDisplay, "/ ${digitalFocusBaseline / 60}h", focusProgress)
            )

            val contextText = when {
                score >= 85 -> "Outstanding balance across all pillars today."
                score >= 70 -> "Great progress. Keep the pillars balanced."
                stepsProgress < 0.3f && exerciseProgress < 0.3f -> "Movement is low. A short walk could flip your score."
                sleepProgress < 0.5f -> "Poor sleep is dragging the score. Prioritize rest tonight."
                focusProgress < 0.3f -> "Focus time is low. Try a 25-min deep-work block."
                score < 20 -> "Calibrating... log some activity to update your Sense of Day."
                else -> "Steady progress. Keep an eye on your lowest pillar."
            }
            _senseOfDayContext.value = contextText

            withContext(Dispatchers.IO) {
                senseOfDayHistoryRepository.saveSnapshot(
                    score = score,
                    stepsProgress = stepsProgress,
                    sleepProgress = sleepProgress,
                    exerciseProgress = exerciseProgress,
                    focusProgress = focusProgress
                )
            }
        }
    }
}
