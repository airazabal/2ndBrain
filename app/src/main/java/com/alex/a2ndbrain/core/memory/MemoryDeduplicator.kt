package com.alex.a2ndbrain.core.memory

data class MergedMemory(
    val primary: MemoryEntity,
    val allIds: List<Long>,
    val duplicateCount: Int,
    val isRead: Boolean
)

// Packages whose notifications are treated as chat messages (content-substring merge).
// Exact prefix matching avoids false positives from unrelated apps that happen to
// contain "slack" or "messenger" in their package name.
private val CHAT_PACKAGE_PREFIXES = setOf(
    "com.whatsapp",
    "org.telegram",
    "com.discord",
    "com.facebook.orca",
    "com.slack",
    "com.signal",
)

// Maximum string length fed to the Levenshtein matrix.
// A 200×200 table costs 40 000 cells — acceptable.
// Without this cap a 2 000-char notification body would cost 4 000 000 cells per pair.
internal const val MAX_LEV_LEN = 200

// If the shorter string is less than this fraction of the longer one, similarity is
// provably below 0.4 and can never satisfy the 0.8 merge threshold — skip entirely.
internal const val MIN_LENGTH_RATIO = 0.4f

fun deduplicateMemories(memories: List<MemoryEntity>): List<MergedMemory> {
    val sorted = memories.sortedByDescending { it.timestamp }
    val mergedList = mutableListOf<MergedMemory>()

    for (item in sorted) {
        val isChatApp = CHAT_PACKAGE_PREFIXES.any {
            (item.packageName ?: "").startsWith(it)
        }

        val existingIdx = if (item.source == "voice") -1 else mergedList.indexOfFirst { merged ->
            val existing = merged.primary
            if (existing.source != item.source || existing.packageName != item.packageName) {
                return@indexOfFirst false
            }

            val existingLines = existing.content.split("\n").filter { it.isNotBlank() }
            val itemLines    = item.content.split("\n").filter { it.isNotBlank() }
            val hasLineOverlap = if (existingLines.isNotEmpty() && itemLines.isNotEmpty()) {
                val intersection = existingLines.intersect(itemLines.toSet())
                (intersection.size.toFloat() / itemLines.size.toFloat()) > 0.5f
            } else false

            val isGmailSummary = (existing.packageName ?: "").contains("gm") &&
                                 (existing.title ?: "").contains("messages") &&
                                 (item.title ?: "").contains("messages")

            when {
                existing.content == item.content -> true
                isChatApp && (existing.content.contains(item.content) ||
                              item.content.contains(existing.content)) -> true
                // Cheap prefix check before the O(n·m) similarity call
                existing.source != "voice" &&
                    existing.content.length >= 15 && item.content.length >= 15 &&
                    existing.content.take(15) == item.content.take(15) -> true
                hasLineOverlap || isGmailSummary -> true
                else -> {
                    val similarity      = calculateSimilarity(existing.content, item.content)
                    val titleSimilarity = calculateSimilarity(existing.title ?: "", item.title ?: "")
                    similarity > 0.8 && (existing.title == item.title || titleSimilarity > 0.8)
                }
            }
        }

        if (existingIdx != -1) {
            val merged = mergedList[existingIdx]
            mergedList[existingIdx] = merged.copy(
                allIds         = merged.allIds + item.id,
                duplicateCount = merged.duplicateCount + item.duplicateCount,
                isRead         = merged.isRead && item.isRead
            )
        } else {
            mergedList.add(
                MergedMemory(
                    primary        = item,
                    allIds         = listOf(item.id),
                    duplicateCount = item.duplicateCount,
                    isRead         = item.isRead
                )
            )
        }
    }
    return mergedList
}

// Exposed as internal so unit tests can verify guard behaviour directly.
internal fun calculateSimilarity(s1: String, s2: String): Double {
    if (s1 == s2) return 1.0
    if (s1.isEmpty() || s2.isEmpty()) return 0.0

    val len1 = s1.length
    val len2 = s2.length

    // Guard 1: length-ratio pre-filter — O(1), eliminates the matrix entirely
    // when strings are structurally too different to be duplicates.
    val minLen = minOf(len1, len2).toFloat()
    val maxLen = maxOf(len1, len2).toFloat()
    if (minLen / maxLen < MIN_LENGTH_RATIO) return 0.0

    // Guard 2: truncate to cap the DP table at MAX_LEV_LEN × MAX_LEV_LEN cells.
    // Leading content is the most diagnostic part for deduplication.
    val a = if (len1 > MAX_LEV_LEN) s1.substring(0, MAX_LEV_LEN) else s1
    val b = if (len2 > MAX_LEV_LEN) s2.substring(0, MAX_LEV_LEN) else s2
    val cappedMax = maxOf(a.length, b.length).toDouble()

    val distance = levenshteinDistance(a, b)
    return (cappedMax - distance) / cappedMax
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
            dp[j] = if (s1[i - 1] == s2[j - 1]) prev
                    else minOf(dp[j] + 1, dp[j - 1] + 1, prev + 1)
            prev = temp
        }
    }
    return dp[len2]
}
