package com.alex.a2ndbrain.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.goals.*
import com.alex.a2ndbrain.core.habits.HabitEntity
import com.alex.a2ndbrain.core.habits.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class GoalsUiState(
    val progresses: List<GoalProgress> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val isLoading: Boolean = false,
    val showSheet: Boolean = false,
    val sheetTitle: String = "",
    val sheetType: GoalType = GoalType.EXERCISE_SESSIONS,
    val sheetTarget: String = "",
    val sheetPeriod: Int = 7,
    val sheetLinkedHabitId: String? = null,
    val isSaving: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModel(
    private val goalDao: GoalDao,
    private val calculator: GoalProgressCalculator,
    private val habitRepository: HabitRepository
) : ViewModel() {

    private val _state = MutableStateFlow(GoalsUiState())
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            habitRepository.getAllActiveHabitsFlow().collect { habits ->
                _state.update { it.copy(habits = habits) }
            }
        }
        viewModelScope.launch {
            goalDao.getActiveGoalsFlow().collect { goals ->
                recompute(goals)
            }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            recompute(goalDao.getActiveGoals())
        }
    }

    private fun recompute(goals: List<GoalEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val progresses = calculator.compute(goals)
            _state.update { it.copy(progresses = progresses, isLoading = false) }
        }
    }

    fun showAddSheet() = _state.update {
        it.copy(showSheet = true, sheetTitle = "", sheetType = GoalType.EXERCISE_SESSIONS,
            sheetTarget = "", sheetPeriod = 7, sheetLinkedHabitId = null)
    }

    fun hideSheet() = _state.update { it.copy(showSheet = false) }

    fun setTitle(v: String) = _state.update { it.copy(sheetTitle = v) }
    fun setType(v: GoalType) = _state.update { it.copy(sheetType = v, sheetLinkedHabitId = null) }
    fun setTarget(v: String) = _state.update { it.copy(sheetTarget = v) }
    fun setPeriod(v: Int) = _state.update { it.copy(sheetPeriod = v) }
    fun setLinkedHabit(id: String?) = _state.update { it.copy(sheetLinkedHabitId = id) }

    fun saveGoal() {
        val s = _state.value
        val target = s.sheetTarget.toFloatOrNull() ?: return
        if (s.sheetTitle.isBlank()) return
        _state.update { it.copy(isSaving = true) }
        viewModelScope.launch(Dispatchers.IO) {
            goalDao.upsert(
                GoalEntity(
                    title = s.sheetTitle.trim(),
                    type = s.sheetType.name,
                    targetValue = target,
                    periodDays = s.sheetPeriod,
                    linkedHabitId = s.sheetLinkedHabitId
                )
            )
            _state.update { it.copy(isSaving = false, showSheet = false) }
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            goalDao.delete(id)
        }
    }
}
