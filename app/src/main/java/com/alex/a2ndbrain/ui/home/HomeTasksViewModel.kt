package com.alex.a2ndbrain.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.todoist.TaskLatencyStats
import com.alex.a2ndbrain.core.todoist.TaskLatencyTracker
import com.alex.a2ndbrain.core.todoist.TodoistRepository
import com.alex.a2ndbrain.core.todoist.TodoistReminderNotifier
import com.alex.a2ndbrain.core.todoist.TodoistStatsRepository
import com.alex.a2ndbrain.core.todoist.TodoistTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeTasksViewModel(
    private val todoistRepository: TodoistRepository,
    private val todoistStatsRepository: TodoistStatsRepository,
    private val taskLatencyTracker: TaskLatencyTracker,
    private val reminderNotifier: TodoistReminderNotifier
) : ViewModel() {

    private val _todoistTasks = MutableStateFlow<List<TodoistTask>>(emptyList())
    val todoistTasks: StateFlow<List<TodoistTask>> = _todoistTasks.asStateFlow()

    private val _overdueTasks = MutableStateFlow<List<TodoistTask>>(emptyList())
    val overdueTasks: StateFlow<List<TodoistTask>> = _overdueTasks.asStateFlow()

    private val _todoistLoading = MutableStateFlow(false)
    val todoistLoading: StateFlow<Boolean> = _todoistLoading.asStateFlow()

    private val _taskLatencyStats = MutableStateFlow(TaskLatencyStats())
    val taskLatencyStats: StateFlow<TaskLatencyStats> = _taskLatencyStats.asStateFlow()

    init {
        refreshTodoistTasks()
    }

    fun refreshTodoistTasks() {
        viewModelScope.launch(Dispatchers.IO) {
            _todoistLoading.value = true
            val split = todoistRepository.fetchTodayAndOverdue()
            _todoistTasks.value = split.today
            _overdueTasks.value = split.overdue
            _todoistLoading.value = false
            taskLatencyTracker.markSeen(split.overdue)
            _taskLatencyStats.value = taskLatencyTracker.getStats(split.overdue)
            val allPending = split.today + split.overdue
            reminderNotifier.maybeNotify(allPending)
        }
    }

    fun completeTodoistTask(taskId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val task = _overdueTasks.value.find { it.id == taskId }
                ?: _todoistTasks.value.find { it.id == taskId }
            val ok = todoistRepository.closeTask(taskId)
            if (ok) {
                if (task != null) {
                    taskLatencyTracker.recordCompletion(task)
                    todoistStatsRepository.saveCompletion(task.id, task.content)
                }
                _todoistTasks.value = _todoistTasks.value.filter { it.id != taskId }
                _overdueTasks.value = _overdueTasks.value.filter { it.id != taskId }
                _taskLatencyStats.value = taskLatencyTracker.getStats(_overdueTasks.value)
            }
        }
    }

}
