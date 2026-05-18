package com.alex.a2ndbrain.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String, // e.g., "notification", "clipboard", "voice"
    val packageName: String?, // for notifications
    val title: String?,
    val content: String,
    val deepLink: String? = null, // URI to jump to specific content
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val duplicateCount: Int = 1,
    val tags: String? = null
) {
    companion object {
        fun create(
            source: String,
            packageName: String?,
            title: String?,
            content: String,
            deepLink: String? = null,
            isRead: Boolean = false,
            timestamp: Long = System.currentTimeMillis(),
            duplicateCount: Int = 1
        ): MemoryEntity {
            val tagsList = mutableListOf<String>()
            val text = "${title ?: ""} $content ${packageName ?: ""}".lowercase()
            
            if (text.contains("step") || text.contains("heart") || text.contains("sleep") || text.contains("zepp") || text.contains("workout") || text.contains("kcal") || text.contains("health")) {
                tagsList.add("#Health")
            }
            if (text.contains("todoist") || text.contains("task") || text.contains("meeting") || text.contains("schedule") || text.contains("calendar") || text.contains("due") || text.contains("project") || text.contains("work")) {
                tagsList.add("#Work")
            }
            if (source == "clipboard" || text.contains("whatsapp") || text.contains("message") || text.contains("chat") || text.contains("gmail") || text.contains("mail") || text.contains("outlook")) {
                if (source == "clipboard") {
                    tagsList.add("#Reference")
                } else {
                    tagsList.add("#Social")
                }
            }
            if (text.contains("transaction") || text.contains("spent") || text.contains("amount") || text.contains("payment") || text.contains("card") || text.contains("bank") || text.contains("cost") || text.contains("price")) {
                tagsList.add("#Finance")
            }
            
            return MemoryEntity(
                source = source,
                packageName = packageName,
                title = title,
                content = content,
                deepLink = deepLink,
                isRead = isRead,
                timestamp = timestamp,
                duplicateCount = duplicateCount,
                tags = if (tagsList.isEmpty()) null else tagsList.joinToString(" ")
            )
        }
    }
}
