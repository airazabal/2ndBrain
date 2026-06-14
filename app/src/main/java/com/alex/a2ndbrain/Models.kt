package com.alex.a2ndbrain

import java.util.UUID

enum class ConflictType { OVERLAP, OVERDUE_HABIT, DISTRACTION_GAP }
enum class ConflictSeverity { WARNING, ALERT }

enum class AppTab(val index: Int, val label: String, val title: String) {
    TODAY(0, "Today", "Today"),
    FEED(1, "Feed", "Feed"),
    WELLNESS(2, "Wellness", "Wellness"),
    COPILOT(3, "Co-pilot", "Co-pilot"),
    SETTINGS(4, "Settings", "Settings"),
    NOTES(5, "Notes", "Notes"),
    SEARCH(6, "Search", "Search");

    companion object {
        fun fromIndex(index: Int): AppTab = entries.find { it.index == index } ?: TODAY
    }
}

data class TimelineConflict(
    val id: String,
    val type: ConflictType,
    val severity: ConflictSeverity,
    val title: String,
    val description: String,
    val deepDivePrompt: String,
    val relatedEventIds: List<String> = emptyList()
)

data class TimelineEvent(
    val id: String = UUID.randomUUID().toString(),
    val time: String,
    val title: String,
    val description: String,
    val appName: String,
    val sourcePackage: String,
    val minutesFromMidnight: Int
)

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val modelUsed: String? = null,
    val wasFallback: Boolean = false
)

data class ConsolidatedUsage(
    val packageName: String,
    val totalTimeMs: Long,
    val deviceBreakdown: Map<String, Long> = emptyMap(),
    val lastTimestamp: Long
)
