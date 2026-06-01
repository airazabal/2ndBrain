package com.alex.a2ndbrain.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.exercise.ExerciseSessionEntity
import com.alex.a2ndbrain.core.exercise.ExerciseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExerciseUiState(
    val sessions: List<ExerciseSessionEntity> = emptyList(),
    val weeklyConsistency: List<Pair<String, Float>> = emptyList(),
    val weeklySessionCount: Int = 0,
    val weeklyTotalMinutes: Int = 0,
    val isLoading: Boolean = false,
    val showLogSheet: Boolean = false,
    val selectedType: ExerciseType = ExerciseType.WALKING,
    val durationMinutes: Int = 30,
    val notes: String = "",
    val startedAt: Long = 0L
)

class ExerciseViewModel(
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExerciseUiState())
    val uiState: StateFlow<ExerciseUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            exerciseRepository.getAllSessionsFlow().collect { sessions ->
                _uiState.update { it.copy(sessions = sessions) }
            }
        }
        viewModelScope.launch {
            exerciseRepository.getWeeklyConsistency().collect { consistency ->
                _uiState.update { it.copy(weeklyConsistency = consistency) }
            }
        }
        refreshWeeklySummary()
    }

    private fun refreshWeeklySummary() {
        viewModelScope.launch(Dispatchers.IO) {
            val (count, minutes) = exerciseRepository.getWeeklySummary()
            _uiState.update { it.copy(weeklySessionCount = count, weeklyTotalMinutes = minutes) }
        }
    }

    fun showLogSheet() = _uiState.update { it.copy(showLogSheet = true) }

    fun hideLogSheet() = _uiState.update {
        it.copy(
            showLogSheet = false,
            selectedType = ExerciseType.WALKING,
            durationMinutes = 30,
            notes = "",
            startedAt = 0L
        )
    }

    fun selectType(type: ExerciseType) = _uiState.update { it.copy(selectedType = type) }
    fun setDuration(minutes: Int) = _uiState.update { it.copy(durationMinutes = minutes) }
    fun setNotes(text: String) = _uiState.update { it.copy(notes = text) }

    fun logSession() {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            exerciseRepository.logSession(
                type = state.selectedType,
                durationMinutes = state.durationMinutes,
                startedAt = state.startedAt,
                notes = state.notes
            )
            _uiState.update { it.copy(isLoading = false) }
            hideLogSheet()
            refreshWeeklySummary()
        }
    }

    fun deleteSession(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            exerciseRepository.deleteSession(id)
            refreshWeeklySummary()
        }
    }
}
