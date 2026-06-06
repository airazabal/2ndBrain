package com.alex.a2ndbrain.core.todoist

import android.util.Log
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.TimeZone

class TodoistRepositoryImpl(private val settingsManager: CaptureSettingsManager) : TodoistRepository {

    private val baseUrl = "https://api.todoist.com/api/v1"

    // Single paginated fetch — splits into today's tasks and overdue tasks so callers
    // don't need two round-trips.
    override suspend fun fetchTodayAndOverdue(): SplitTasks = withContext(Dispatchers.IO) {
        val token = settingsManager.getTodoistApiToken()
        Log.d("Todoist", "fetchTodayAndOverdue: token blank=${token.isBlank()}")
        if (token.isBlank()) return@withContext SplitTasks(emptyList(), emptyList())

        val localFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
        val nowCal = java.util.Calendar.getInstance()
        val todayStr = localFmt.format(nowCal.time)
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
                    // Prefer the due datetime; fall back to deadline date.
                    // Guard against empty strings returned by optString() when the key is absent.
                    val rawDate = task.dueDateStr?.takeIf { it.isNotBlank() }
                        ?: task.deadlineDateStr?.takeIf { it.isNotBlank() }
                        ?: return@forEach

                    // Convert to local date + local time-of-day minutes.
                    // The Todoist API v1 returns datetimes in UTC when a timezone is set
                    // (e.g. "2026-06-01T11:00:00Z" for a 6 AM Eastern task).
                    // We normalise to local before comparing so that the overdue/today
                    // split is always correct regardless of the device's UTC offset.
                    val (localDateOnly, localTaskMinutes) = localDateAndMinutes(rawDate)

                    when {
                        localDateOnly < todayStr -> overdueList.add(task)
                        localDateOnly == todayStr -> {
                            // Timed entry whose local time has already passed → overdue
                            val pastDue = localTaskMinutes != null && localTaskMinutes < nowMinutes
                            if (pastDue) overdueList.add(task) else todayList.add(task)
                        }
                        // Future tasks (tomorrow or later) are not surfaced on the home screen.
                        // Previously, tomorrow's recurring-task instance was added when no today
                        // instance existed — but this caused completed recurring tasks to reappear
                        // immediately (Todoist creates tomorrow's instance on completion).
                    }
                }
                cursor = root.optString("next_cursor").takeIf { it.isNotBlank() && it != "null" }
                page++
            } while (cursor != null)
            Log.d("Todoist", "Done: ${todayList.size} today, ${overdueList.size} overdue across $page page(s)")
            SplitTasks(todayList.distinctBy { it.id }, overdueList.distinctBy { it.id })
        } catch (e: Exception) {
            Log.e("Todoist", "fetchTodayAndOverdue failed", e)
            SplitTasks(emptyList(), emptyList())
        }
    }

    /**
     * Parse a Todoist due-date string (which may be date-only "yyyy-MM-dd",
     * a floating local datetime "yyyy-MM-ddTHH:mm:ss", or a UTC datetime
     * "yyyy-MM-ddTHH:mm:ssZ") and return a pair of:
     *   - the local date string ("yyyy-MM-dd")
     *   - the local time-of-day in minutes from midnight, or null if no time component
     */
    private fun localDateAndMinutes(raw: String): Pair<String, Int?> {
        val dateOnly = raw.take(10)
        if (raw.length <= 10) return dateOnly to null

        return try {
            if (raw.endsWith("Z") || raw.contains("+") || raw.length > 19) {
                // UTC or offset-aware datetime — parse properly and convert to local
                val utcFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault()).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                // Strip any trailing Z, milliseconds, or offset so the formatter accepts it
                val normalized = raw.substringBefore("Z").substringBefore("+").take(19)
                val date = utcFmt.parse(normalized)
                if (date != null) {
                    val localCal = java.util.Calendar.getInstance()
                    localCal.time = date
                    val localDate = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(localCal.time)
                    val localMins = localCal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + localCal.get(java.util.Calendar.MINUTE)
                    localDate to localMins
                } else {
                    dateOnly to null
                }
            } else {
                // Floating local datetime (no Z, no offset) — substring is already local time
                val t = raw.substring(11, 16)
                val (h, m) = t.split(":").map { it.toInt() }
                dateOnly to (h * 60 + m)
            }
        } catch (_: Exception) {
            dateOnly to null
        }
    }

    override suspend fun getTodayTasks(): List<TodoistTask> = fetchTodayAndOverdue().today

    override suspend fun getOverdueTasks(): List<TodoistTask> = fetchTodayAndOverdue().overdue

    override suspend fun closeTask(taskId: String): Boolean = withContext(Dispatchers.IO) {
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
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
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

            // Prefer due.datetime (UTC full datetime, e.g. "2026-06-01T11:00:00.000000Z")
            // over due.date (which Todoist returns as date-only even for timed tasks).
            // Falling back to due.date handles tasks with no specific time of day.
            val dueDateStr = due?.optString("datetime")?.ifBlank { null }
                ?: due?.optString("date")?.ifBlank { null }

            if (i == 0) Log.d("Todoist", "Sample task due obj: $due | dueDateStr=$dueDateStr")

            result.add(
                TodoistTask(
                    id = obj.getString("id"),
                    content = obj.getString("content"),
                    description = obj.optString("description", ""),
                    priority = obj.optInt("priority", 1),
                    dueDateStr = dueDateStr,
                    deadlineDateStr = deadlineDateStr,
                    url = obj.optString("url", "")
                )
            )
        }
        return result
    }
}
