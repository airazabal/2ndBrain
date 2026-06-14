package com.alex.a2ndbrain.core.calendar

import android.content.Context
import android.net.Uri
import android.util.Log
import com.alex.a2ndbrain.TimelineEvent
import java.util.Calendar

class CalendarRepositoryImpl(private val context: Context) : CalendarRepository {

    override fun getEventsForRange(startMs: Long, endMs: Long): List<TimelineEvent> {
        return try {
            val uri = Uri.parse("content://com.android.calendar/instances/when/$startMs/$endMs")
            val projection = arrayOf("title", "begin", "allDay", "eventId")
            val cursor = context.contentResolver.query(uri, projection, null, null, "begin ASC")
                ?: return emptyList()
            val results = mutableListOf<TimelineEvent>()
            cursor.use { c ->
                val titleIdx = c.getColumnIndex("title")
                val beginIdx = c.getColumnIndex("begin")
                val allDayIdx = c.getColumnIndex("allDay")
                val eventIdIdx = c.getColumnIndex("eventId")
                while (c.moveToNext()) {
                    val title = c.getString(titleIdx) ?: continue
                    val begin = c.getLong(beginIdx)
                    val allDay = c.getInt(allDayIdx) == 1
                    if (allDay) continue
                    val eventId = if (eventIdIdx >= 0) c.getLong(eventIdIdx).toString() else begin.toString()
                    val cal = Calendar.getInstance().apply { timeInMillis = begin }
                    val hour = cal.get(Calendar.HOUR_OF_DAY)
                    val min = cal.get(Calendar.MINUTE)
                    val totalMinutes = hour * 60 + min
                    val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
                    val ampm = if (hour >= 12) "PM" else "AM"
                    val timeStr = "$displayHour:${min.toString().padStart(2, '0')} $ampm"
                    results.add(
                        TimelineEvent(
                            id = "cal_${eventId}_$begin",
                            time = timeStr,
                            title = title,
                            description = title,
                            appName = "Calendar",
                            sourcePackage = "calendar",
                            minutesFromMidnight = totalMinutes
                        )
                    )
                }
            }
            results
        } catch (e: Exception) {
            Log.e("CalendarRepository", "Calendar provider query failed", e)
            emptyList()
        }
    }
}
