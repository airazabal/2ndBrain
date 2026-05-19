package com.alex.a2ndbrain.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.HabitEntity
import com.alex.a2ndbrain.core.memory.HabitsDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class SettingsViewModel(
    private val habitsDao: HabitsDao,
    private val settingsManager: CaptureSettingsManager,
    private val applicationContext: Context
) : ViewModel() {

    val activeHabits: StateFlow<List<HabitEntity>> = habitsDao.getActiveHabits()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun addCustomHabit(name: String, time: String, isMedication: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = HabitEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                timeString = time,
                isMedication = isMedication,
                isActive = true
            )
            habitsDao.insertHabit(habit)
        }
    }

    fun deleteHabit(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            habitsDao.deleteHabitById(id)
        }
    }

    fun toggleHabitActive(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val habit = habitsDao.getHabitById(id)
            if (habit != null) {
                habitsDao.insertHabit(habit.copy(isActive = !habit.isActive))
            }
        }
    }
}
