package com.alex.a2ndbrain.ui.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.core.todoist.TaskLatencyStats
import com.alex.a2ndbrain.core.todoist.TaskLatencyTracker
import com.alex.a2ndbrain.core.todoist.TodoistRepository
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
    private val applicationContext: Context
) : ViewModel() {

    private val _todoistTasks = MutableStateFlow<List<TodoistTask>>(emptyList())
    val todoistTasks: StateFlow<List<TodoistTask>> = _todoistTasks.asStateFlow()

    private val _overdueTasks = MutableStateFlow<List<TodoistTask>>(emptyList())
    val overdueTasks: StateFlow<List<TodoistTask>> = _overdueTasks.asStateFlow()

    private val _todoistLoading = MutableStateFlow(false)
    val todoistLoading: StateFlow<Boolean> = _todoistLoading.asStateFlow()

    private val taskLatencyTracker = TaskLatencyTracker(applicationContext)
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
            if (allPending.isNotEmpty()) maybeFireTodoistReminder(allPending)
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

    private fun maybeFireTodoistReminder(tasks: List<TodoistTask>) {
        val prefs = applicationContext.getSharedPreferences("todoist_reminder_prefs", Context.MODE_PRIVATE)
        val lastMs = prefs.getLong("last_notified_ms", 0L)
        if (System.currentTimeMillis() - lastMs < 60 * 60 * 1000L) return

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val channelId = "task_reminders_v2"
        nm.createNotificationChannel(
            android.app.NotificationChannel(channelId, "Todoist Reminders", android.app.NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Hourly reminders for incomplete tasks due today."
            }
        )
        val openIntent = android.app.PendingIntent.getActivity(
            applicationContext, 9001,
            android.content.Intent(applicationContext, com.alex.a2ndbrain.MainActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (tasks.size == 1) "1 task still pending" else "${tasks.size} tasks still pending"
        val body = tasks.take(5).joinToString(" · ") { it.content }
        val notification = androidx.core.app.NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .build()
        nm.notify(9001, notification)
        prefs.edit().putLong("last_notified_ms", System.currentTimeMillis()).apply()
    }
}
