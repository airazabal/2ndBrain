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

    suspend fun getTodayTasks(): List<TodoistTask> = withContext(Dispatchers.IO) {
        val token = settingsManager.getTodoistApiToken()
        Log.d("Todoist", "getTodayTasks: token blank=${token.isBlank()}, length=${token.length}")
        if (token.isBlank()) return@withContext emptyList()
        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        val allTasks = mutableListOf<TodoistTask>()
        var cursor: String? = null
        var page = 0
        try {
            do {
                val urlStr = if (cursor != null)
                    "$baseUrl/tasks?cursor=${java.net.URLEncoder.encode(cursor, "UTF-8")}"
                else
                    "$baseUrl/tasks"
                Log.d("Todoist", "Fetching page $page: $urlStr")
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("Authorization", "Bearer $token")
                    connectTimeout = 10_000
                    readTimeout = 10_000
                }
                val code = conn.responseCode
                if (code != 200) {
                    val err = conn.errorStream?.bufferedReader()?.readText()
                    Log.w("Todoist", "HTTP $code on page $page: $err")
                    conn.disconnect()
                    break
                }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val root = org.json.JSONTokener(body).nextValue() as? JSONObject ?: break
                val arr = root.optJSONArray("results") ?: JSONArray()
                val pageTasks = parseTasks(arr)
                val todayTasks = pageTasks.filter { task ->
                    task.dueDateStr?.startsWith(todayStr) == true ||
                    task.deadlineDateStr?.startsWith(todayStr) == true
                }
                allTasks.addAll(todayTasks)
                Log.d("Todoist", "page $page: ${pageTasks.size} tasks, ${todayTasks.size} due/deadline today, running total=${allTasks.size}")
                cursor = root.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
                page++
            } while (cursor != null)
            Log.d("Todoist", "Done: ${allTasks.size} tasks for today ($todayStr) across $page page(s)")
            allTasks
        } catch (e: Exception) {
            Log.e("Todoist", "getTodayTasks failed", e)
            emptyList()
        }
    }

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
