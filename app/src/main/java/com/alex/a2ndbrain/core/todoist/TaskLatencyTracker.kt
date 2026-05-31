package com.alex.a2ndbrain.core.todoist

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class TaskLatencyStats(
    val staleDays: Map<String, Int> = emptyMap(), // taskId → days since first seen overdue
    val avgDays: Float = 0f,
    val bestDays: Int = 0,
    val worstDays: Int = 0,
    val completedCount: Int = 0
)

class TaskLatencyTracker(context: Context) {

    private val prefs = context.getSharedPreferences("task_latency_prefs", Context.MODE_PRIVATE)

    /** Call every time the overdue list is refreshed. Records first-seen timestamp per task. */
    fun markSeen(tasks: List<TodoistTask>) {
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        tasks.forEach { task ->
            val key = firstSeenKey(task.id)
            if (!prefs.contains(key)) editor.putLong(key, now)
        }
        editor.apply()
    }

    /** Call when a task is completed. Records latency and cleans up first-seen entry. */
    fun recordCompletion(task: TodoistTask) {
        val now = System.currentTimeMillis()
        val firstSeen = prefs.getLong(firstSeenKey(task.id), now)
        val daysOverdue = ((now - firstSeen) / MS_PER_DAY).toInt()

        val records = loadRecords().toMutableList()
        records.add(CompletedRecord(task.id, task.content, firstSeen, now, daysOverdue))
        if (records.size > MAX_RECORDS) records.removeAt(0)

        prefs.edit()
            .remove(firstSeenKey(task.id))
            .putString(RECORDS_KEY, serializeRecords(records))
            .apply()
    }

    /** Returns stats for the current overdue list + historical completions. */
    fun getStats(overdueTasks: List<TodoistTask>): TaskLatencyStats {
        val now = System.currentTimeMillis()
        val staleDays = overdueTasks.associate { task ->
            val firstSeen = prefs.getLong(firstSeenKey(task.id), now)
            task.id to ((now - firstSeen) / MS_PER_DAY).toInt()
        }
        val completed = loadRecords()
        return TaskLatencyStats(
            staleDays = staleDays,
            avgDays = if (completed.isEmpty()) 0f
                      else completed.sumOf { it.daysOverdue }.toFloat() / completed.size,
            bestDays = completed.minOfOrNull { it.daysOverdue } ?: 0,
            worstDays = completed.maxOfOrNull { it.daysOverdue } ?: 0,
            completedCount = completed.size
        )
    }

    private fun firstSeenKey(taskId: String) = "first_seen_$taskId"

    private fun loadRecords(): List<CompletedRecord> {
        val json = prefs.getString(RECORDS_KEY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CompletedRecord(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    firstSeenMs = obj.getLong("firstMs"),
                    completedMs = obj.getLong("completedMs"),
                    daysOverdue = obj.getInt("days")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun serializeRecords(records: List<CompletedRecord>): String {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("name", r.name)
                put("firstMs", r.firstSeenMs)
                put("completedMs", r.completedMs)
                put("days", r.daysOverdue)
            })
        }
        return arr.toString()
    }

    private data class CompletedRecord(
        val id: String,
        val name: String,
        val firstSeenMs: Long,
        val completedMs: Long,
        val daysOverdue: Int
    )

    companion object {
        private const val RECORDS_KEY = "completed_records"
        private const val MAX_RECORDS = 30
        private const val MS_PER_DAY = 24 * 60 * 60 * 1000L
    }
}
