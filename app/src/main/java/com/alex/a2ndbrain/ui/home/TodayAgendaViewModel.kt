package com.alex.a2ndbrain.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.habits.HabitSyncManager
import com.alex.a2ndbrain.core.todoist.TaskLatencyStats
import com.alex.a2ndbrain.core.todoist.TaskLatencyTracker
import com.alex.a2ndbrain.core.todoist.TodoistRepository
import com.alex.a2ndbrain.core.todoist.TodoistReminderNotifier
import com.alex.a2ndbrain.core.todoist.TodoistStatsRepository
import com.alex.a2ndbrain.core.todoist.TodoistTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TodayAgendaViewModel(
    private val todoistRepository: TodoistRepository,
    private val habitSyncManager: HabitSyncManager,
    private val todoistStatsRepository: TodoistStatsRepository,
    private val taskLatencyTracker: TaskLatencyTracker,
    private val reminderNotifier: TodoistReminderNotifier
) : ViewModel() {

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val today = dateFmt.format(Date())

    private val _todoistToday = MutableStateFlow<List<TodoistTask>>(emptyList())
    private val _todoistOverdue = MutableStateFlow<List<TodoistTask>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _taskLatencyStats = MutableStateFlow(TaskLatencyStats())
    val taskLatencyStats: StateFlow<TaskLatencyStats> = _taskLatencyStats.asStateFlow()

    val agendaItems: StateFlow<List<TodayAgendaItem>> = combine(
        _todoistToday,
        _todoistOverdue,
        habitSyncManager.getTodayHabitsFlow(),
        habitSyncManager.getCompletionsForDateFlow(today)
    ) { todayTasks, overdueTasks, habits, completions ->
        val completedHabitIds = completions.map { it.habitId }.toSet()
        val nowCal = Calendar.getInstance()
        val currentMinutes = nowCal.get(Calendar.HOUR_OF_DAY) * 60 + nowCal.get(Calendar.MINUTE)

        // Habits linked to Todoist already appear as Habit items — suppress the duplicate Task entry.
        val habitTodoistIds = habits.mapNotNull { it.todoistTaskId }.toSet()

        val items = mutableListOf<TodayAgendaItem>()

        overdueTasks.filter { it.id !in habitTodoistIds }.forEach { task ->
            items.add(TodayAgendaItem.Task(task, isOverdue = true))
        }
        todayTasks.filter { it.id !in habitTodoistIds }.forEach { task ->
            items.add(TodayAgendaItem.Task(task, isOverdue = false))
        }
        habits.forEach { habit ->
            val isCompleted = habit.id in completedHabitIds
            val scheduledMinutes = habit.timeString.toScheduledMinutes()
            val isOverdue = !isCompleted && scheduledMinutes != null &&
                    currentMinutes - scheduledMinutes in 30..300
            items.add(TodayAgendaItem.Habit(habit, isCompleted, isOverdue))
        }

        items.sortedWith(
            compareBy(
                { it.isCompleted },
                { !it.isOverdue },
                { it.scheduledMinutes == null },
                { it.scheduledMinutes ?: Int.MAX_VALUE },
                { if (it is TodayAgendaItem.Task) -it.task.priority else 0 }
            )
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val overdueCount: StateFlow<Int> = agendaItems
        .map { items -> items.count { it.isOverdue && !it.isCompleted } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            val split = todoistRepository.fetchTodayAndOverdue()
            _todoistToday.value = split.today
            _todoistOverdue.value = split.overdue
            _isLoading.value = false
            taskLatencyTracker.markSeen(split.overdue)
            _taskLatencyStats.value = taskLatencyTracker.getStats(split.overdue)
            val allPending = split.today + split.overdue
            reminderNotifier.maybeNotify(allPending)
        }
    }

    fun completeTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = _todoistOverdue.value.find { it.id == taskId }
                ?: _todoistToday.value.find { it.id == taskId }
            val ok = todoistRepository.closeTask(taskId)
            if (ok) {
                if (task != null) {
                    taskLatencyTracker.recordCompletion(task)
                    todoistStatsRepository.saveCompletion(task.id, task.content)
                }
                _todoistToday.value = _todoistToday.value.filter { it.id != taskId }
                _todoistOverdue.value = _todoistOverdue.value.filter { it.id != taskId }
                _taskLatencyStats.value = taskLatencyTracker.getStats(_todoistOverdue.value)
            }
        }
    }

    fun toggleHabit(habitId: String) {
        val completedIds = agendaItems.value
            .filterIsInstance<TodayAgendaItem.Habit>()
            .filter { it.isCompleted }
            .map { it.id }
            .toSet()
        viewModelScope.launch(Dispatchers.IO) {
            if (habitId in completedIds) {
                habitSyncManager.markIncomplete(habitId, today)
            } else {
                habitSyncManager.markComplete(habitId, today)
            }
        }
    }

}
