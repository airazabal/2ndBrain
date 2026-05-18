package com.alex.a2ndbrain.core.capture

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.memory.MemoryRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class NotificationCaptureService : NotificationListenerService() {

    private val memoryRepository: MemoryRepository by inject()
    private val settingsManager: CaptureSettingsManager by inject()
    private val applicationScope: CoroutineScope by inject()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "CHECK_ACTIVE") {
            checkActiveNotifications()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun checkActiveNotifications() {
        try {
            val active = activeNotifications
            val count = active?.size ?: 0
            CaptureDebugStore.logEvent("Scanning $count active...")
            Log.d("2ndBrain", "Manual scan started. Found $count notifications.")
            active?.forEach { processNotification(it, isManual = true) }
        } catch (e: Exception) {
            Log.e("2ndBrain", "Failed to get active notifications", e)
            CaptureDebugStore.logEvent("Scan failed: ${e.message}")
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        CaptureDebugStore.logEvent("CONNECTED to System")
        Log.d("2ndBrain", "Notification Listener Service Connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        processNotification(sbn, isManual = false)
    }

    private fun processNotification(sbn: StatusBarNotification, isManual: Boolean) {
        val packageName = sbn.packageName
        val notification = sbn.notification ?: return

        val monitoredApps = settingsManager.getMonitoredApps()
        if (monitoredApps.isNotEmpty() && !monitoredApps.contains(packageName)) {
            return
        }

        val extras = notification.extras ?: Bundle()
        
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""
        val infoText = extras.getCharSequence(Notification.EXTRA_INFO_TEXT)?.toString()?.trim() ?: ""
        val summaryText = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim() ?: ""
        
        val messagingMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val messagingContent = if (messagingMessages != null && messagingMessages.isNotEmpty()) {
            messagingMessages.joinToString("\n") { 
                (it as? Bundle)?.getCharSequence("text")?.toString()?.trim() ?: ""
            }.trim()
        } else ""

        val inboxLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val inboxContent = inboxLines?.filterNotNull()?.joinToString("\n") { it.toString().trim() } ?: ""

        val isSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (packageName == "com.google.android.gm" && isSummary && inboxContent.isEmpty() && bigText.isEmpty()) {
            return
        }

        if (!isManual) CaptureDebugStore.logEvent("Post: ${packageName.split(".").lastOrNull() ?: packageName}")

        val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim() ?: ""
        val androidText = extras.getCharSequence("android.text")?.toString()?.trim() ?: ""
        val ticker = notification.tickerText?.toString()?.trim() ?: ""

        val contentOptions = if (packageName == "com.google.android.gm" || packageName.contains("todoist")) {
            listOf(inboxContent, bigText, androidText, messagingContent, text, subText, infoText, summaryText, ticker)
        } else {
            listOf(bigText, messagingContent, inboxContent, text, subText, infoText, summaryText, ticker)
        }
        
        val finalContent = contentOptions.firstOrNull { it.isNotBlank() } ?: ""

        if (title.isEmpty() && finalContent.isEmpty() && bigTitle.isEmpty()) {
            return
        }

        var finalTitle = listOf(title, bigTitle, packageName).first { it.isNotEmpty() }
        if (packageName == "com.google.android.gm" && subText.isNotEmpty() && subText.contains("@") && !finalTitle.contains(subText)) {
            finalTitle = "$finalTitle ($subText)"
        }
        
        var deepLink: String? = null
        if (packageName == "com.google.android.gm") {
            val searchQuery = if (title.isNotEmpty()) "from:$title $finalContent" else finalContent
            deepLink = "https://mail.google.com/mail/u/0/#search/${android.net.Uri.encode(searchQuery.take(50))}"
        } else if (packageName == "com.google.android.calendar" || packageName == "com.samsung.android.calendar") {
            deepLink = "content://com.android.calendar/time/${System.currentTimeMillis()}"
        } else if (packageName == "com.google.android.apps.maps") {
            deepLink = "geo:0,0?q=${android.net.Uri.encode(title)}"
        }

        applicationScope.launch {
            val contentToStore = finalContent.ifEmpty { "[Notification from $packageName]" }
            
            // Deduplication logic
            val existing = memoryRepository.findExisting("notification", packageName, finalTitle, contentToStore)
            
            if (existing != null) {
                // Exact match, just update timestamp and increment duplicateCount
                val updated = existing.copy(
                    timestamp = System.currentTimeMillis(),
                    duplicateCount = existing.duplicateCount + 1
                )
                memoryRepository.insertMemory(updated)
                return@launch
            }
            
            // Fuzzy match: check if a recent notification from this app has very similar content 
            // or is part of a chat thread (e.g. WhatsApp/Telegram appends, progress bar updates)
            val recentMemories = memoryRepository.getRecentMemories(System.currentTimeMillis() - 30 * 60 * 1000) // last 30 mins
            val fuzzyMatch = recentMemories.find { 
                it.packageName == packageName && 
                it.title == finalTitle
            }

            val isChatApp = packageName.contains("whatsapp") || 
                            packageName.contains("telegram") || 
                            packageName.contains("signal") || 
                            packageName.contains("discord") || 
                            packageName.contains("messenger") || 
                            packageName.contains("slack")

            var merged = false
            if (fuzzyMatch != null) {
                val shouldMerge = when {
                    // Chat app: new content contains the old content as a prefix/substring
                    isChatApp && contentToStore.contains(fuzzyMatch.content) -> true
                    // Progress bar or slight edit: similarity > 80%
                    calculateSimilarity(fuzzyMatch.content, contentToStore) > 0.8 -> true
                    // Start prefix match (e.g. "Downloading... 10%" vs "Downloading... 20%")
                    contentToStore.take(15) == fuzzyMatch.content.take(15) -> true
                    else -> false
                }

                if (shouldMerge) {
                    val updated = fuzzyMatch.copy(
                        content = contentToStore,
                        timestamp = System.currentTimeMillis(),
                        duplicateCount = fuzzyMatch.duplicateCount + 1
                    )
                    memoryRepository.insertMemory(updated)
                    Log.d("2ndBrain", "Merged fuzzy duplicate for $packageName: $finalTitle")
                    merged = true
                }
            }

            if (!merged) {
                val entity = MemoryEntity.create(
                    source = "notification",
                    packageName = packageName,
                    title = finalTitle,
                    content = contentToStore,
                    deepLink = deepLink
                )
                memoryRepository.insertMemory(entity)
                Log.d("2ndBrain", "Captured new memory: $finalTitle")
            }
        }
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
}
