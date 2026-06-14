package com.alex.a2ndbrain.core.calendar

import com.alex.a2ndbrain.TimelineEvent

interface CalendarRepository {
    fun getEventsForRange(startMs: Long, endMs: Long): List<TimelineEvent>
}
