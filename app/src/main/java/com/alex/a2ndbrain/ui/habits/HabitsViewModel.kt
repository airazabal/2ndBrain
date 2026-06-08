package com.alex.a2ndbrain.ui.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.habits.HabitEntity
import com.alex.a2ndbrain.core.habits.HabitSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class HabitWithStreak(
    val habit: HabitEntity,
    val streak: Int,
    val weeklyRate: Float,
    val isCompletedToday: Boolean
)

data class HabitsUiState(
    val habitsWithStats: List<HabitWithStreak> = emptyList(),
    val completedTodayIds: Set<String> = emptySet(),
    val showAddSheet: Boolean = false,
    val editingHabit: HabitEntity? = null,
    val sheetName: String = "",
    val sheetEmoji: String = "✅",
    val sheetTime: String = "",
    val sheetRepeatRule: String? = null,
    val isSaving: Boolean = false,
    val isSyncing: Boolean = false
)

class HabitsViewModel(private val syncManager: HabitSyncManager) : ViewModel() {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val today get() = dateFmt.format(Date())

    private val _uiState = MutableStateFlow(HabitsUiState())
    val uiState: StateFlow<HabitsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                syncManager.getTodayHabitsFlow(),
                syncManager.getCompletionsForDateFlow(today)
            ) { habits, completions ->
                habits to completions.map { it.habitId }.toSet()
            }.collect { (habits, completedIds) ->
                val withStats = habits.map { habit ->
                    HabitWithStreak(
                        habit = habit,
                        streak = 0,
                        weeklyRate = 0f,
                        isCompletedToday = habit.id in completedIds
                    )
                }
                _uiState.update { it.copy(habitsWithStats = withStats, completedTodayIds = completedIds) }
                loadStreaks(habits, completedIds)
            }
        }
        // Pull from Todoist on open (fire-and-forget; failures are silent)
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncing = true) }
            try { syncManager.syncWithTodoist() } catch (_: Exception) {}
            _uiState.update { it.copy(isSyncing = false) }
        }
    }

    private fun loadStreaks(habits: List<HabitEntity>, completedIds: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val withStats = habits.map { habit ->
                HabitWithStreak(
                    habit = habit,
                    streak = syncManager.getStreakForHabit(habit.id),
                    weeklyRate = syncManager.getWeeklyCompletionRate(habit.id),
                    isCompletedToday = habit.id in completedIds
                )
            }
            _uiState.update { it.copy(habitsWithStats = withStats) }
        }
    }

    fun toggleCompletion(habitId: String) {
        val completedIds = _uiState.value.completedTodayIds
        viewModelScope.launch(Dispatchers.IO) {
            if (habitId in completedIds) {
                syncManager.markIncomplete(habitId, today)
            } else {
                syncManager.markComplete(habitId, today)
            }
        }
    }

    fun showAddSheet() = _uiState.update {
        it.copy(showAddSheet = true, editingHabit = null, sheetName = "", sheetEmoji = "✅", sheetTime = "", sheetRepeatRule = null)
    }

    fun showEditSheet(habit: HabitEntity) = _uiState.update {
        it.copy(showAddSheet = true, editingHabit = habit, sheetName = habit.name, sheetEmoji = habit.emoji, sheetTime = habit.timeString, sheetRepeatRule = habit.repeatRule)
    }

    fun hideSheet() = _uiState.update {
        it.copy(showAddSheet = false, editingHabit = null, sheetName = "", sheetEmoji = "✅", sheetTime = "", sheetRepeatRule = null, isSaving = false)
    }

    fun setName(v: String) = _uiState.update { it.copy(sheetName = v) }
    fun setEmoji(v: String) = _uiState.update { it.copy(sheetEmoji = v) }
    fun setTime(v: String) = _uiState.update { it.copy(sheetTime = v) }
    fun setRepeatRule(v: String?) = _uiState.update { it.copy(sheetRepeatRule = v) }

    fun save() {
        val s = _uiState.value
        if (s.sheetName.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSaving = true) }
            val editing = s.editingHabit
            if (editing != null) {
                syncManager.updateHabit(editing.id, s.sheetName.trim(), s.sheetEmoji, s.sheetTime.trim(), s.sheetRepeatRule)
            } else {
                syncManager.addHabit(s.sheetName.trim(), s.sheetEmoji, s.sheetTime.trim(), s.sheetRepeatRule)
            }
            _uiState.update { it.copy(isSaving = false) }
            hideSheet()
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch(Dispatchers.IO) { syncManager.deleteHabit(id) }
    }
}
