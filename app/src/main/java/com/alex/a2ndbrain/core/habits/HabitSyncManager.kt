package com.alex.a2ndbrain.core.habits

import android.util.Log
import com.alex.a2ndbrain.core.todoist.TodoistHabitClient
import kotlinx.coroutines.flow.Flow
import java.util.*

private const val TAG = "HabitSyncManager"

/**
 * Bidirectional sync layer between local Room habits and Todoist.
 *
 * All mutation operations (add/update/delete/complete/incomplete) go through here
 * so the local DB and Todoist stay in sync transparently. API failures never block
 * local operations — Todoist sync is always best-effort.
 *
 * Read operations (flows) delegate straight to the local repository so UI
 * reactivity stays fast and works offline.
 *
 * Sync rules:
 *   - Local habit with todoistTaskId == null → pushed to Todoist on next sync
 *   - Todoist task labelled "2ndbrain" not in local DB → imported as new habit
 *   - Completion in app → closes Todoist recurring task (Todoist auto-creates next occurrence)
 *   - Un-completion in app → reopens Todoist task
 *   - Task deleted in Todoist → local habit soft-deleted on next sync
 */
class HabitSyncManager(
    private val localRepo: HabitRepository,
    private val todoistClient: TodoistHabitClient
) {

    // ── Read delegates ────────────────────────────────────────────────────────

    fun getTodayHabitsFlow(): Flow<List<HabitEntity>> = localRepo.getTodayHabitsFlow()

    fun getAllActiveHabitsFlow(): Flow<List<HabitEntity>> = localRepo.getAllActiveHabitsFlow()

    fun getCompletionsForDateFlow(date: String): Flow<List<HabitCompletionEntity>> =
        localRepo.getCompletionsForDateFlow(date)

    suspend fun getStreakForHabit(id: String) = localRepo.getStreakForHabit(id)

    suspend fun getWeeklyCompletionRate(id: String) = localRepo.getWeeklyCompletionRate(id)

    // ── Synced writes ─────────────────────────────────────────────────────────

    suspend fun addHabit(name: String, emoji: String, timeString: String, repeatRule: String? = null) {
        localRepo.addHabit(name, emoji, timeString, repeatRule)
        pushLatestUnlinkedByName(name)
    }

    suspend fun updateHabit(id: String, name: String, emoji: String, timeString: String, repeatRule: String? = null) {
        val todoistId = localRepo.getById(id)?.todoistTaskId
        localRepo.updateHabit(id, name, emoji, timeString, repeatRule)
        if (todoistId != null) {
            todoistClient.updateTask(todoistId, name, timeString, repeatRule)
        }
    }

    suspend fun deleteHabit(id: String) {
        val todoistId = localRepo.getById(id)?.todoistTaskId
        localRepo.deleteHabit(id)
        if (todoistId != null) {
            todoistClient.deleteTask(todoistId)
        }
    }

    suspend fun markComplete(habitId: String, date: String) {
        localRepo.markComplete(habitId, date)
        val todoistId = localRepo.getById(habitId)?.todoistTaskId
        if (todoistId != null) {
            todoistClient.closeTask(todoistId)
        }
    }

    suspend fun markIncomplete(habitId: String, date: String) {
        localRepo.markIncomplete(habitId, date)
        val todoistId = localRepo.getById(habitId)?.todoistTaskId
        if (todoistId != null) {
            todoistClient.reopenTask(todoistId)
        }
    }

    // ── Full bidirectional sync ───────────────────────────────────────────────

    /**
     * Pull Todoist → local, then push local-only habits → Todoist.
     * No-op when no API token is configured.
     */
    suspend fun syncWithTodoist() {
        val projectId = try {
            todoistClient.findHabitTrackerProjectId()
        } catch (e: Exception) {
            Log.w(TAG, "syncWithTodoist: project lookup failed, skipping", e); return
        }
        if (projectId == null) {
            Log.d(TAG, "syncWithTodoist: no token or 'Habit Tracker' project not found, skipping"); return
        }

        pullFromTodoist()
        pushUnlinkedHabits()
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private suspend fun pullFromTodoist() {
        val remoteTasks = try {
            todoistClient.fetchHabitTasks()
        } catch (e: Exception) {
            Log.e(TAG, "pullFromTodoist: fetch failed", e); return
        }
        val remoteIds = remoteTasks.map { it.id }.toSet()

        // Guard: if remote returned nothing, don't touch local data — could be a transient API failure
        if (remoteTasks.isEmpty()) {
            Log.w(TAG, "pullFromTodoist: remote returned 0 tasks — skipping sync to avoid accidental deletion")
            return
        }

        val remoteNames = remoteTasks.map { it.content.trim().lowercase() }.toSet()

        // Import or restore or update each remote task
        for (task in remoteTasks) {
            val time = extractTime(task.dueDatetime, task.dueString)
            val recurrence = extractRecurrence(task.dueString)
            val existing = try { localRepo.getByTodoistTaskId(task.id) } catch (e: Exception) { null }
            when {
                existing != null -> {
                    // Already linked — update if anything changed
                    if (existing.name != task.content || existing.repeatRule != recurrence || existing.timeString != time) {
                        localRepo.updateHabit(existing.id, task.content, existing.emoji, time, recurrence)
                        Log.d(TAG, "pullFromTodoist: updated '${existing.name}' → '${task.content}'")
                    }
                }
                else -> {
                    // No active habit for this Todoist ID. Try to find one by name (active or deleted).
                    // Recurring tasks get a new Todoist ID on each completion — re-link instead of creating
                    // a fresh row so that completion history is preserved.
                    val sameNameActive = try { localRepo.getAllActiveHabitsList() } catch (e: Exception) { emptyList() }
                        .firstOrNull { it.name.equals(task.content, ignoreCase = true) }
                    val deleted = if (sameNameActive == null)
                        try { localRepo.findDeletedByName(task.content) } catch (e: Exception) { null }
                    else null

                    when {
                        sameNameActive != null -> {
                            localRepo.updateTodoistTaskId(sameNameActive.id, task.id)
                            Log.d(TAG, "pullFromTodoist: re-linked active '${task.content}' → ${task.id}")
                        }
                        deleted != null -> {
                            localRepo.restore(deleted.id, task.id)
                            Log.d(TAG, "pullFromTodoist: restored deleted '${task.content}' with history intact")
                        }
                        else -> {
                            localRepo.addHabit(name = task.content, emoji = "✅", timeString = time, repeatRule = recurrence)
                            pushLatestUnlinkedByName(task.content, forceId = task.id)
                            Log.d(TAG, "pullFromTodoist: imported '${task.content}' (repeat=$recurrence, time=$time)")
                        }
                    }
                }
            }
        }

        // Only delete habits that are genuinely gone from Todoist — not in remote list by ID or name
        val allLocal = try { localRepo.getAllActiveHabitsList() } catch (e: Exception) { emptyList() }
        for (habit in allLocal) {
            if (habit.todoistTaskId != null
                && habit.todoistTaskId !in remoteIds
                && habit.name.trim().lowercase() !in remoteNames) {
                localRepo.deleteHabit(habit.id)
                Log.d(TAG, "pullFromTodoist: removed '${habit.name}' (deleted in Todoist)")
            }
        }
    }

    private suspend fun pushUnlinkedHabits() {
        val unlinked = try { localRepo.getHabitsWithoutTodoistId() } catch (e: Exception) { emptyList() }
        for (habit in unlinked) {
            val newId = try { todoistClient.createTask(habit.name, habit.timeString, habit.repeatRule) } catch (e: Exception) { null }
            if (newId != null) {
                localRepo.updateTodoistTaskId(habit.id, newId)
                Log.d(TAG, "pushUnlinkedHabits: pushed '${habit.name}' → Todoist $newId")
            }
        }
    }

    /**
     * Links the most-recently-added local habit named [name] to Todoist.
     * If [forceId] is given (import case), that ID is written directly without
     * making an API call (avoids creating a duplicate task).
     */
    private suspend fun pushLatestUnlinkedByName(name: String, forceId: String? = null) {
        val unlinked = try { localRepo.getHabitsWithoutTodoistId() } catch (e: Exception) { emptyList() }
        val target = unlinked.lastOrNull { it.name == name } ?: return
        val todoistId = forceId
            ?: try { todoistClient.createTask(target.name, target.timeString, target.repeatRule) } catch (e: Exception) { null }
        if (todoistId != null) {
            localRepo.updateTodoistTaskId(target.id, todoistId)
            Log.d(TAG, "pushLatestUnlinkedByName: linked '${target.name}' ↔ $todoistId")
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun extractRecurrence(dueString: String?): String? {
        if (dueString.isNullOrBlank()) return null
        val clean = dueString.lowercase()
            .replace(Regex("\\s+at\\s+\\d{1,2}(?::\\d{2})?\\s*(am|pm)?\\s*$"), "")
            .trim()
        return if (clean.startsWith("every")) clean else null
    }

    private fun extractTime(datetime: String?, dueString: String?): String {
        // dueString is already in the user's local timezone — prefer it.
        if (!dueString.isNullOrBlank()) {
            // Matches: "at 7am", "at 7:00am", "at 7:00 am", "at 07:00", "at 19:00"
            val m = Regex("at (\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?").find(dueString.lowercase())
            if (m != null) {
                var h = m.groupValues[1].toIntOrNull() ?: 0
                val min = m.groupValues[2].toIntOrNull() ?: 0
                val ampm = m.groupValues[3]
                if (ampm == "pm" && h < 12) h += 12
                if (ampm == "am" && h == 12) h = 0
                return String.format("%02d:%02d", h, min)
            }
            // dueString has no time component — fall through to dueDatetime
        }
        // Fall back to dueDatetime. If it has a "Z" it's UTC → convert to local.
        // If there's no timezone marker, Todoist is giving local time directly → parse as-is.
        if (!datetime.isNullOrBlank() && datetime.length > 10) {
            return try {
                val isUtc = datetime.contains("Z", ignoreCase = true)
                val normalized = datetime.substringBefore("Z").substringBefore("+").take(19)
                val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).apply {
                    if (isUtc) timeZone = java.util.TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(normalized) ?: return ""
                val cal = Calendar.getInstance().apply { time = date }
                String.format("%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
            } catch (_: Exception) { "" }
        }
        return ""
    }
}
