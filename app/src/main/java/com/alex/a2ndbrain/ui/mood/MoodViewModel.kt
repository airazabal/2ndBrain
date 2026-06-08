package com.alex.a2ndbrain.ui.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.mood.MoodLogEntity
import com.alex.a2ndbrain.core.mood.MoodRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class MoodUiState(
    val recentLogs: List<MoodLogEntity> = emptyList(),
    val todayLog: MoodLogEntity? = null,
    val showCheckInSheet: Boolean = false,
    val selectedMood: Int = 3,
    val selectedEnergy: Int = 3,
    val note: String = "",
    val isSaving: Boolean = false
)

class MoodViewModel(private val repository: MoodRepository) : ViewModel() {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    private val _uiState = MutableStateFlow(MoodUiState())
    val uiState: StateFlow<MoodUiState> = _uiState.asStateFlow()

    init {
        val sinceDate = dateFmt.format(Date(System.currentTimeMillis() - 14 * 24 * 60 * 60 * 1000L))
        viewModelScope.launch {
            repository.getLogsSinceFlow(sinceDate).collect { logs ->
                val today = dateFmt.format(Date())
                _uiState.update {
                    it.copy(
                        recentLogs = logs,
                        todayLog = logs.firstOrNull { log -> log.date == today }
                    )
                }
            }
        }
    }

    fun showCheckIn() = _uiState.update { it.copy(showCheckInSheet = true) }

    fun hideCheckIn() = _uiState.update {
        it.copy(showCheckInSheet = false, selectedMood = 3, selectedEnergy = 3, note = "")
    }

    fun setMood(value: Int) = _uiState.update { it.copy(selectedMood = value) }
    fun setEnergy(value: Int) = _uiState.update { it.copy(selectedEnergy = value) }
    fun setNote(value: String) = _uiState.update { it.copy(note = value) }

    fun save() {
        val state = _uiState.value
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }
            repository.logMood(state.selectedMood, state.selectedEnergy, state.note)
            _uiState.update { it.copy(isSaving = false) }
            hideCheckIn()
        }
    }
}
