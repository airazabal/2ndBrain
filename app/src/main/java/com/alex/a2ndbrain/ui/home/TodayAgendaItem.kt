package com.alex.a2ndbrain.ui.home

import com.alex.a2ndbrain.core.habits.HabitEntity
import com.alex.a2ndbrain.core.todoist.TodoistTask

sealed class TodayAgendaItem {
    abstract val id: String
    abstract val title: String
    abstract val scheduledMinutes: Int?  // minutes from midnight, null = no time
    abstract val isCompleted: Boolean
    abstract val isOverdue: Boolean

    data class Task(
        val task: TodoistTask,
        override val isOverdue: Boolean
    ) : TodayAgendaItem() {
        override val id = task.id
        override val title = task.content
        override val scheduledMinutes: Int? = null
        override val isCompleted = false
    }

    data class Habit(
        val habit: HabitEntity,
        val isCompletedToday: Boolean,
        override val isOverdue: Boolean
    ) : TodayAgendaItem() {
        override val id = habit.id
        override val title = habit.name
        override val scheduledMinutes: Int? = habit.timeString.toScheduledMinutes()
        override val isCompleted = isCompletedToday
    }
}

fun String.toScheduledMinutes(): Int? {
    if (isBlank()) return null
    val parts = split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
    return h * 60 + m
}
