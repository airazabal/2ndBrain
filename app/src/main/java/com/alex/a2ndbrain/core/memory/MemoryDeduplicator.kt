package com.alex.a2ndbrain.core.memory

data class MergedMemory(
    val primary: MemoryEntity,
    val allIds: List<Long>,
    val duplicateCount: Int,
    val isRead: Boolean
)

fun deduplicateMemories(memories: List<MemoryEntity>): List<MergedMemory> {
    val sorted = memories.sortedByDescending { it.timestamp }
    val mergedList = mutableListOf<MergedMemory>()

    for (item in sorted) {
        val isChatApp = (item.packageName ?: "").contains("whatsapp") ||
                        (item.packageName ?: "").contains("telegram") ||
                        (item.packageName ?: "").contains("signal") ||
                        (item.packageName ?: "").contains("discord") ||
                        (item.packageName ?: "").contains("messenger") ||
                        (item.packageName ?: "").contains("slack")

        val existingIdx = if (item.source == "voice") -1 else mergedList.indexOfFirst { merged ->
            val existing = merged.primary
            if (existing.source != item.source || existing.packageName != item.packageName) {
                return@indexOfFirst false
            }

            val similarity = calculateSimilarity(existing.content, item.content)
            val titleSimilarity = calculateSimilarity(existing.title ?: "", item.title ?: "")

            val existingLines = existing.content.split("\n").filter { it.isNotBlank() }
            val itemLines = item.content.split("\n").filter { it.isNotBlank() }
            val hasLineOverlap = if (existingLines.isNotEmpty() && itemLines.isNotEmpty()) {
                val intersection = existingLines.intersect(itemLines.toSet())
                (intersection.size.toFloat() / itemLines.size.toFloat()) > 0.5f
            } else false

            val isGmailSummary = (existing.packageName ?: "").contains("gm") &&
                                 (existing.title ?: "").contains("messages") &&
                                 (item.title ?: "").contains("messages")

            when {
                existing.content == item.content -> true
                isChatApp && (existing.content.contains(item.content) || item.content.contains(existing.content)) -> true
                similarity > 0.8 && (existing.title == item.title || titleSimilarity > 0.8) -> true
                existing.source != "voice" && existing.content.take(15) == item.content.take(15) -> true
                hasLineOverlap || isGmailSummary -> true
                else -> false
            }
        }

        if (existingIdx != -1) {
            val merged = mergedList[existingIdx]
            mergedList[existingIdx] = merged.copy(
                allIds = merged.allIds + item.id,
                duplicateCount = merged.duplicateCount + item.duplicateCount,
                isRead = merged.isRead && item.isRead
            )
        } else {
            mergedList.add(
                MergedMemory(
                    primary = item,
                    allIds = listOf(item.id),
                    duplicateCount = item.duplicateCount,
                    isRead = item.isRead
                )
            )
        }
    }
    return mergedList
}

private fun calculateSimilarity(s1: String, s2: String): Double {
    if (s1 == s2) return 1.0
    if (s1.isEmpty() || s2.isEmpty()) return 0.0
    val maxLen = maxOf(s1.length, s2.length)
    val distance = levenshteinDistance(s1, s2)
    return (maxLen - distance).toDouble() / maxLen.toDouble()
}

private fun levenshteinDistance(s1: String, s2: String): Int {
    val len1 = s1.length
    val len2 = s2.length
    val dp = IntArray(len2 + 1) { it }
    for (i in 1..len1) {
        var prev = dp[0]
        dp[0] = i
        for (j in 1..len2) {
            val temp = dp[j]
            if (s1[i - 1] == s2[j - 1]) {
                dp[j] = prev
            } else {
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + 1)
            }
            prev = temp
        }
    }
    return dp[len2]
}
