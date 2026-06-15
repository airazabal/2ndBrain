package com.alex.a2ndbrain.ui.todoist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.todoist.TodoistCompletion
import com.alex.a2ndbrain.core.todoist.TodoistStatsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class TodoistUiState(
    val completions: List<TodoistCompletion> = emptyList(),
    val weeklyActivity: List<Pair<String, Int>> = emptyList(),
    val todayCount: Int = 0,
    val weeklyCount: Int = 0,
    val totalCount: Int = 0,
    val todayMissedCount: Int = 0,
    val weeklyMissedCount: Int = 0
)

class TodoistViewModel(
    private val repository: TodoistStatsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodoistUiState())
    val uiState: StateFlow<TodoistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getAllCompletionsFlow().collect { completions ->
                _uiState.update { it.copy(completions = completions) }
            }
        }
        viewModelScope.launch {
            repository.getWeeklyActivity().collect { activity ->
                _uiState.update { it.copy(weeklyActivity = activity) }
            }
        }
        refreshCounts()
    }

    private fun refreshCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            val today = repository.getTodayCount()
            val weekly = repository.getWeeklyCount()
            val total = repository.getTotalCount()
            val todayMissed = repository.getTodayMissedCount()
            val weeklyMissed = repository.getWeeklyMissedCount()
            _uiState.update {
                it.copy(
                    todayCount = today,
                    weeklyCount = weekly,
                    totalCount = total,
                    todayMissedCount = todayMissed,
                    weeklyMissedCount = weeklyMissed
                )
            }
        }
    }
}
