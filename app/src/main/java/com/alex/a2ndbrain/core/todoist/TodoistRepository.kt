package com.alex.a2ndbrain.core.todoist

import android.util.Log
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TodoistTask(
    val id: String,
    val content: String,
    val description: String,
    val priority: Int,         // 1=normal … 4=urgent
    val dueDateStr: String?,   // "yyyy-MM-dd" or null
    val deadlineDateStr: String?, // "yyyy-MM-dd" or null — separate from due date
    val url: String
)

class TodoistRepository(private val settingsManager: CaptureSettingsManager) {

    private val baseUrl = "https://api.todoist.com/api/v1"

    data class SplitTasks(val today: List<TodoistTask>, val overdue: List<TodoistTask>)

    // Single paginated fetch — splits into today's tasks and overdue tasks so callers
    // don't need two round-trips.
    suspend fun fetchTodayAndOverdue(): SplitTasks = withContext(Dispatchers.IO) {
        val token = settingsManager.getTodoistApiToken()
        Log.d("Todoist", "fetchTodayAndOverdue: token blank=${token.isBlank()}")
        if (token.isBlank()) return@withContext SplitTasks(emptyList(), emptyList())

        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val nowCal = java.util.Calendar.getInstance()
        val nowMinutes = nowCal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + nowCal.get(java.util.Calendar.MINUTE)

        val todayList = mutableListOf<TodoistTask>()
        val overdueList = mutableListOf<TodoistTask>()
        var cursor: String? = null
        var page = 0
        try {
            do {
                val urlStr = if (cursor != null)
                    "$baseUrl/tasks?cursor=${java.net.URLEncoder.encode(cursor, "UTF-8")}"
                else "$baseUrl/tasks"
                Log.d("Todoist", "Fetching page $page: $urlStr")
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 10_000; readTimeout = 10_000
                }
                val code = conn.responseCode
                if (code != 200) {
                    Log.w("Todoist", "HTTP $code on page $page")
                    conn.disconnect(); break
                }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val root = org.json.JSONTokener(body).nextValue() as? JSONObject ?: break
                val arr = root.optJSONArray("results") ?: JSONArray()
                parseTasks(arr).forEach { task ->
                    val rawDate = task.dueDateStr ?: task.deadlineDateStr ?: return@forEach
                    val dateOnly = rawDate.take(10)
                    when {
                        dateOnly < todayStr -> overdueList.add(task)
                        dateOnly == todayStr -> {
                            // Timed task whose time has already passed → overdue
                            val pastDue = rawDate.length > 10 && try {
                                val t = rawDate.substring(11, 16)
                                val (h, m) = t.split(":").map { it.toInt() }
                                h * 60 + m < nowMinutes
                            } catch (_: Exception) { false }
                            if (pastDue) overdueList.add(task) else todayList.add(task)
                        }
                    }
                }
                cursor = root.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
                page++
            } while (cursor != null)
            Log.d("Todoist", "Done: ${todayList.size} today, ${overdueList.size} overdue across $page page(s)")
            SplitTasks(todayList, overdueList)
        } catch (e: Exception) {
            Log.e("Todoist", "fetchTodayAndOverdue failed", e)
            SplitTasks(emptyList(), emptyList())
        }
    }

    suspend fun getTodayTasks(): List<TodoistTask> = fetchTodayAndOverdue().today

    suspend fun getOverdueTasks(): List<TodoistTask> = fetchTodayAndOverdue().overdue

    suspend fun closeTask(taskId: String): Boolean = withContext(Dispatchers.IO) {
        val token = settingsManager.getTodoistApiToken()
        if (token.isBlank()) return@withContext false
        try {
            val url = URL("$baseUrl/tasks/$taskId/close")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $token")
                connectTimeout = 10_000
                readTimeout = 10_000
            }
            val ok = conn.responseCode == 204
            conn.disconnect()
            ok
        } catch (e: Exception) {
            Log.e("Todoist", "closeTask $taskId failed", e)
            false
        }
    }

    private fun parseTasks(arr: JSONArray): List<TodoistTask> {
        val result = mutableListOf<TodoistTask>()
        for (i in 0 until arr.length()) {
            val obj: JSONObject = arr.getJSONObject(i)
            val due = obj.optJSONObject("due")
            val deadline = obj.optJSONObject("deadline")
            // deadline may also appear as a flat string in some API versions
            val deadlineDateStr = deadline?.optString("date")?.ifBlank { null }
                ?: obj.optString("deadline").ifBlank { null }
            result.add(
                TodoistTask(
                    id = obj.getString("id"),
                    content = obj.getString("content"),
                    description = obj.optString("description", ""),
                    priority = obj.optInt("priority", 1),
                    dueDateStr = due?.optString("date"),
                    deadlineDateStr = deadlineDateStr,
                    url = obj.optString("url", "")
                )
            )
        }
        return result
    }
}
