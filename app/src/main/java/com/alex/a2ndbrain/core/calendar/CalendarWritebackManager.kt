package com.alex.a2ndbrain.core.calendar

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import java.util.TimeZone
import java.util.concurrent.TimeUnit

class CalendarWritebackManager(
    private val context: Context,
    private val settingsManager: CaptureSettingsManager
) {

    fun writeHabitCompletion(habitName: String) {
        if (!settingsManager.isCalendarSyncEnabled()) return
        if (!hasPermission()) return

        val calendarId = getPrimaryCalendarId() ?: return
        val now = System.currentTimeMillis()
        val startMs = now
        val endMs = now + TimeUnit.MINUTES.toMillis(15)

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, "✓ $habitName")
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
            put(CalendarContract.Events.DESCRIPTION, "Completed via 2nd Brain")
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        }

        try {
            context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
            Log.d("CalendarWriteback", "Wrote completion for $habitName")
        } catch (e: Exception) {
            Log.e("CalendarWriteback", "Failed to write calendar event", e)
        }
    }

    private fun getPrimaryCalendarId(): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        return try {
            context.contentResolver.query(
                CalendarContract.Calendars.CONTENT_URI,
                projection,
                "${CalendarContract.Calendars.VISIBLE} = 1",
                null,
                "${CalendarContract.Calendars.IS_PRIMARY} DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}
