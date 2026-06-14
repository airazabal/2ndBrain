package com.alex.a2ndbrain.ui.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.goals.*
import com.alex.a2ndbrain.core.goals.Goal
import com.alex.a2ndbrain.core.habits.HabitEntity
import com.alex.a2ndbrain.core.habits.HabitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class GoalsUiState(
    val progresses: List<GoalProgress> = emptyList(),
    val habits: List<HabitEntity> = emptyList(),
    val isLoading: Boolean = false,
    val showSheet: Boolean = false,
    val editingGoalId: String? = null,
    val sheetTitle: String = "",
    val sheetType: GoalType = GoalType.EXERCISE_SESSIONS,
    val sheetTarget: String = "",
    val sheetPeriod: Int = 7,
    val sheetLinkedHabitId: String? = null,
    val isSaving: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class GoalsViewModel(
    private val goalRepository: GoalRepository,
    private val calculator: GoalProgressCalculator,
    private val habitRepository: HabitRepository,
    private val exerciseRepository: ExerciseRepository
) : ViewModel() {

    private val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())

    private val _state = MutableStateFlow(GoalsUiState())
    val state: StateFlow<GoalsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            habitRepository.getAllActiveHabitsFlow().collect { habits ->
                _state.update { it.copy(habits = habits) }
            }
        }
        // Recompute whenever goals, exercise sessions, or habit completions change.
        viewModelScope.launch {
            combine(
                goalRepository.getActiveGoalsFlow(),
                exerciseRepository.getAllSessionsFlow(),
                habitRepository.getCompletionsForDateFlow(today)
            ) { goals, _, _ -> goals }
                .collect { goals -> recompute(goals) }
        }
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            recompute(goalRepository.getActiveGoals())
        }
    }

    private fun recompute(goals: List<Goal>) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true) }
            val progresses = calculator.compute(goals)
            _state.update { it.copy(progresses = progresses, isLoading = false) }
        }
    }

    fun showAddSheet() = _state.update {
        it.copy(showSheet = true, editingGoalId = null, sheetTitle = "",
            sheetType = GoalType.EXERCISE_SESSIONS, sheetTarget = "", sheetPeriod = 7, sheetLinkedHabitId = null)
    }

    fun showEditSheet(goal: Goal) = _state.update {
        it.copy(showSheet = true, editingGoalId = goal.id,
            sheetTitle = goal.title, sheetType = goal.type,
            sheetTarget = if (goal.targetValue % 1 == 0f) goal.targetValue.toInt().toString() else goal.targetValue.toString(),
            sheetPeriod = goal.periodDays, sheetLinkedHabitId = goal.linkedHabitId)
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
            val existing = s.editingGoalId?.let { goalRepository.getById(it) }
            goalRepository.upsert(
                Goal(
                    id = s.editingGoalId ?: java.util.UUID.randomUUID().toString(),
                    title = s.sheetTitle.trim(),
                    type = s.sheetType,
                    targetValue = target,
                    periodDays = s.sheetPeriod,
                    isActive = true,
                    createdAt = existing?.createdAt ?: System.currentTimeMillis(),
                    linkedHabitId = s.sheetLinkedHabitId,
                    linkedExerciseType = null
                )
            )
            _state.update { it.copy(isSaving = false, showSheet = false) }
        }
    }

    fun deleteGoal(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            goalRepository.delete(id)
        }
    }
}
