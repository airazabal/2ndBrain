package com.alex.a2ndbrain.core.domain

data class Memory(
    val id: Long,
    val source: String,
    val packageName: String?,
    val title: String?,
    val content: String,
    val deepLink: String?,
    val isRead: Boolean,
    val timestamp: Long,
    val duplicateCount: Int,
    val tags: String?
)
