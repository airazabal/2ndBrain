package com.alex.a2ndbrain.core.todoist

import android.util.Log
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

private const val TAG = "TodoistHabitClient"
private const val HABIT_PROJECT_NAME = "Habit Tracker"
private const val BASE_URL = "https://api.todoist.com/api/v1"

data class TodoistHabitTask(
    val id: String,
    val content: String,
    val dueString: String?,     // e.g. "every day at 08:00"
    val dueDatetime: String?,   // UTC datetime for time extraction
    val isRecurring: Boolean = false
)

class TodoistHabitClient(private val settings: CaptureSettingsManager) {

    // ── Project lookup ────────────────────────────────────────────────────────

    /**
     * Returns the ID of the "Habit Tracker" project, caching it after the first
     * successful lookup. Returns null if the project isn't found or no token set.
     */
    suspend fun findHabitTrackerProjectId(): String? = withContext(Dispatchers.IO) {
        val token = settings.getTodoistApiToken()
        if (token.isBlank()) return@withContext null

        val cached = settings.getTodoistHabitProjectId()
        if (cached.isNotBlank()) return@withContext cached

        try {
            val conn = get(token, "$BASE_URL/projects") ?: return@withContext null
            if (conn.responseCode != 200) { conn.disconnect(); return@withContext null }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()

            // Projects endpoint returns a JSON array directly
            val arr = try { JSONArray(body) } catch (_: Exception) {
                // Some API versions wrap in {"results": [...]}
                JSONObject(body).optJSONArray("results") ?: return@withContext null
            }
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                if (obj.optString("name").equals(HABIT_PROJECT_NAME, ignoreCase = true)) {
                    val id = obj.getString("id")
                    settings.saveTodoistHabitProjectId(id)
                    Log.d(TAG, "findHabitTrackerProjectId: found '$HABIT_PROJECT_NAME' → $id")
                    return@withContext id
                }
            }
            Log.w(TAG, "findHabitTrackerProjectId: project '$HABIT_PROJECT_NAME' not found")
            null
        } catch (e: Exception) {
            Log.e(TAG, "findHabitTrackerProjectId failed", e); null
        }
    }

    private suspend fun getProjectId(): String? = findHabitTrackerProjectId()

    // ── Fetch ────────────────────────────────────────────────────────────────

    /**
     * Fetches all tasks in the "Habit Tracker" project (with pagination).
     */
    suspend fun fetchHabitTasks(): List<TodoistHabitTask> = withContext(Dispatchers.IO) {
        val token = settings.getTodoistApiToken()
        if (token.isBlank()) return@withContext emptyList()
        val projectId = getProjectId() ?: return@withContext emptyList()

        val results = mutableListOf<TodoistHabitTask>()
        var cursor: String? = null
        try {
            do {
                val urlStr = buildString {
                    append("$BASE_URL/tasks?project_id=${java.net.URLEncoder.encode(projectId, "UTF-8")}")
                    if (cursor != null) append("&cursor=${java.net.URLEncoder.encode(cursor, "UTF-8")}")
                }
                val conn = get(token, urlStr) ?: break
                if (conn.responseCode != 200) { conn.disconnect(); break }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val root = JSONObject(body)
                val arr = root.optJSONArray("results") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val due = obj.optJSONObject("due")
                    results.add(
                        TodoistHabitTask(
                            id = obj.getString("id"),
                            content = obj.getString("content"),
                            dueString = due?.optString("string")?.ifBlank { null },
                            dueDatetime = due?.optString("datetime")?.ifBlank { null }
                                ?: due?.optString("date")?.ifBlank { null },
                            isRecurring = due?.optBoolean("is_recurring", false) ?: false
                        )
                    )
                }
                cursor = root.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
            } while (cursor != null)
        } catch (e: Exception) {
            Log.e(TAG, "fetchHabitTasks failed", e)
        }
        results
    }

    // ── Mutations ────────────────────────────────────────────────────────────

    /**
     * Creates a task in the "Habit Tracker" project.
     * [repeatRule] is the Todoist recurrence string (e.g. "every day", "every week").
     * Returns the new task ID, or null on failure.
     */
    suspend fun createTask(name: String, timeString: String, repeatRule: String? = null): String? = withContext(Dispatchers.IO) {
        val token = settings.getTodoistApiToken()
        if (token.isBlank()) return@withContext null
        val projectId = getProjectId() ?: return@withContext null
        try {
            val body = JSONObject().apply {
                put("content", name)
                put("project_id", projectId)
                val due = buildDueString(repeatRule, timeString)
                if (due != null) put("due_string", due)
            }
            val conn = post(token, "$BASE_URL/tasks", body) ?: return@withContext null
            if (conn.responseCode !in 200..299) { conn.disconnect(); return@withContext null }
            val resp = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            JSONObject(resp).optString("id").ifBlank { null }
        } catch (e: Exception) {
            Log.e(TAG, "createTask failed for '$name'", e); null
        }
    }

    /** Updates content, due_string, and recurrence on an existing task. */
    suspend fun updateTask(taskId: String, name: String, timeString: String, repeatRule: String? = null): Boolean = withContext(Dispatchers.IO) {
        val token = settings.getTodoistApiToken()
        if (token.isBlank()) return@withContext false
        try {
            val body = JSONObject().apply {
                put("content", name)
                val due = buildDueString(repeatRule, timeString)
                if (due != null) put("due_string", due)
            }
            val conn = postUpdate(token, "$BASE_URL/tasks/$taskId", body) ?: return@withContext false
            val code = conn.responseCode; conn.disconnect(); code in 200..299
        } catch (e: Exception) { Log.e(TAG, "updateTask $taskId failed", e); false }
    }

    private fun buildDueString(repeatRule: String?, timeString: String): String? = when {
        !repeatRule.isNullOrBlank() && timeString.isNotBlank() -> "$repeatRule at $timeString"
        !repeatRule.isNullOrBlank() -> repeatRule
        timeString.isNotBlank() -> "every day at $timeString"
        else -> null
    }

    /** Permanently deletes a task. */
    suspend fun deleteTask(taskId: String): Boolean = withContext(Dispatchers.IO) {
        val token = settings.getTodoistApiToken()
        if (token.isBlank()) return@withContext false
        try {
            val conn = (URL("$BASE_URL/tasks/$taskId").openConnection() as HttpURLConnection).apply {
                requestMethod = "DELETE"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10_000; readTimeout = 10_000
            }
            val code = conn.responseCode; conn.disconnect(); code in 200..299
        } catch (e: Exception) { Log.e(TAG, "deleteTask $taskId failed", e); false }
    }

    /** Closes (completes) a recurring task; Todoist auto-creates the next occurrence. */
    suspend fun closeTask(taskId: String): Boolean = withContext(Dispatchers.IO) {
        val token = settings.getTodoistApiToken()
        if (token.isBlank()) return@withContext false
        try {
            val conn = post(token, "$BASE_URL/tasks/$taskId/close", null) ?: return@withContext false
            val code = conn.responseCode; conn.disconnect(); code in 200..299
        } catch (e: Exception) { Log.e(TAG, "closeTask $taskId failed", e); false }
    }

    /** Reopens a previously closed task. */
    suspend fun reopenTask(taskId: String): Boolean = withContext(Dispatchers.IO) {
        val token = settings.getTodoistApiToken()
        if (token.isBlank()) return@withContext false
        try {
            val conn = post(token, "$BASE_URL/tasks/$taskId/reopen", null) ?: return@withContext false
            val code = conn.responseCode; conn.disconnect(); code in 200..299
        } catch (e: Exception) { Log.e(TAG, "reopenTask $taskId failed", e); false }
    }

    // ── HTTP helpers ─────────────────────────────────────────────────────────

    private fun get(token: String, urlStr: String): HttpURLConnection? = try {
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = 10_000; readTimeout = 10_000
        }
    } catch (e: Exception) { Log.e(TAG, "GET $urlStr failed", e); null }

    private fun post(token: String, urlStr: String, body: JSONObject?): HttpURLConnection? = try {
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
            connectTimeout = 10_000; readTimeout = 10_000
            if (body != null) {
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                outputStream.bufferedWriter().use { it.write(body.toString()) }
            }
        }
    } catch (e: Exception) { Log.e(TAG, "POST $urlStr failed", e); null }

    private fun postUpdate(token: String, urlStr: String, body: JSONObject): HttpURLConnection? = try {
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Request-Id", UUID.randomUUID().toString())
            connectTimeout = 10_000; readTimeout = 10_000
            doOutput = true
            outputStream.bufferedWriter().use { it.write(body.toString()) }
        }
    } catch (e: Exception) { Log.e(TAG, "POST-update $urlStr failed", e); null }
}
