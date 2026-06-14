package com.alex.a2ndbrain.ui.exercise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.exercise.ExerciseSession
import com.alex.a2ndbrain.core.exercise.ExerciseType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ExerciseUiState(
    val sessions: List<ExerciseSession> = emptyList(),
    val weeklyConsistency: List<Pair<String, Float>> = emptyList(),
    val todaySessionCount: Int = 0,
    val todayTotalMinutes: Int = 0,
    val weeklySessionCount: Int = 0,
    val weeklyTotalMinutes: Int = 0,
    val totalSessionCount: Int = 0,
    val isLoading: Boolean = false,
    val showLogSheet: Boolean = false,
    val selectedType: ExerciseType = ExerciseType.WALKING,
    val durationMinutes: Int = 30,
    val notes: String = "",
    val startedAt: Long = 0L,
    val editingSession: ExerciseSession? = null,
    val editSelectedType: ExerciseType = ExerciseType.WALKING,
    val editDurationMinutes: Int = 30,
    val editNotes: String = ""
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
            val (todayCount, todayMins) = exerciseRepository.getTodaySummary()
            val (weekCount, weekMins) = exerciseRepository.getWeeklySummary()
            val total = exerciseRepository.getTotalSessionCount()
            _uiState.update {
                it.copy(
                    todaySessionCount = todayCount,
                    todayTotalMinutes = todayMins,
                    weeklySessionCount = weekCount,
                    weeklyTotalMinutes = weekMins,
                    totalSessionCount = total
                )
            }
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

    fun showEditSheet(session: ExerciseSession) = _uiState.update {
        val type = session.type
        it.copy(
            editingSession = session,
            editSelectedType = type,
            editDurationMinutes = session.durationMinutes,
            editNotes = session.notes
        )
    }

    fun hideEditSheet() = _uiState.update {
        it.copy(editingSession = null, editSelectedType = ExerciseType.WALKING, editDurationMinutes = 30, editNotes = "")
    }

    fun setEditType(type: ExerciseType) = _uiState.update { it.copy(editSelectedType = type) }
    fun setEditDuration(minutes: Int) = _uiState.update { it.copy(editDurationMinutes = minutes) }
    fun setEditNotes(text: String) = _uiState.update { it.copy(editNotes = text) }

    fun saveEdit() {
        val state = _uiState.value
        val session = state.editingSession ?: return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isLoading = true) }
            exerciseRepository.updateSession(session.id, state.editSelectedType, state.editDurationMinutes, state.editNotes)
            _uiState.update { it.copy(isLoading = false) }
            hideEditSheet()
            refreshWeeklySummary()
        }
    }
}
