package com.alex.a2ndbrain.ui.home

data class CategorizedNotification(
    val memoryId: Long,
    val summary: String,
    val sourceApp: String
)

data class NotificationCategory(
    val name: String,
    val emoji: String,
    val items: List<CategorizedNotification>
)

data class GrandCentralResult(
    val isLoading: Boolean = false,
    val suggestedActions: List<CategorizedNotification> = emptyList(),
    val categories: List<NotificationCategory> = emptyList(),
    val idToCategoryMap: Map<Long, String> = emptyMap(),  // memoryId → categoryName
    val categoryEmojiMap: Map<String, String> = emptyMap() // categoryName → emoji
)
