package com.alex.a2ndbrain

import java.util.UUID

enum class ConflictType { OVERLAP, OVERDUE_HABIT, DISTRACTION_GAP }
enum class ConflictSeverity { WARNING, ALERT }

enum class AppTab(val index: Int, val label: String, val title: String) {
    HOME(0, "Home", "Welcome to your 2ndBrain"),
    FEED(1, "Feed", "Your daily stream of captures"),
    BRAIN(2, "Brain", "Reflections & daily insights"),
    NOTES(3, "Notes", "Your space for ideas & thoughts"),
    TIME(4, "Time", "Understanding your routine"),
    MEDITATION(5, "Zen", "Meditation Sessions (Zendence)"),
    SETTINGS(6, "Settings", "Configure capture and permissions"),
    COPILOT(7, "Co-pilot", "Ask your 2ndBrain Co-Pilot"),
    HEALTH(8, "Health", "Your health & fitness trends");

    companion object {
        fun fromIndex(index: Int): AppTab = entries.find { it.index == index } ?: HOME
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
    val modelUsed: String? = null
)

data class ConsolidatedUsage(
    val packageName: String,
    val totalTimeMs: Long,
    val deviceBreakdown: Map<String, Long> = emptyMap(),
    val lastTimestamp: Long
)
