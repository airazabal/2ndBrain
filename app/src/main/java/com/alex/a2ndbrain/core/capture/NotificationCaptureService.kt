package com.alex.a2ndbrain.core.capture

import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.memory.MemoryEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationCaptureService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private val database by lazy { AppDatabase.getDatabase(this) }
    private val settingsManager by lazy { CaptureSettingsManager(this) }

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

        // Respect monitored apps setting
        val monitoredApps = settingsManager.getMonitoredApps()
        if (monitoredApps.isNotEmpty() && !monitoredApps.contains(packageName)) {
            return
        }

        val extras = notification.extras ?: Bundle()
        
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim() ?: ""
        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()?.trim() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.trim() ?: ""
        
        // Messaging Style (SMS, WhatsApp, Gmail)
        val messagingMessages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        val messagingContent = if (messagingMessages != null && messagingMessages.isNotEmpty()) {
            messagingMessages.joinToString("\n") { 
                (it as? Bundle)?.getCharSequence("text")?.toString()?.trim() ?: ""
            }.trim()
        } else ""

        // Inbox Style (Gmail Summary - fallback if not a group summary)
        val inboxLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        val inboxContent = inboxLines?.filterNotNull()?.joinToString("\n") { it.toString().trim() } ?: ""

        // Gmail duplication fix: Only ignore summaries if they DON'T have useful unique content
        val isSummary = (notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0
        if (packageName == "com.google.android.gm" && isSummary && inboxContent.isEmpty() && bigText.isEmpty()) {
            Log.d("2ndBrain", "Skipping empty Gmail summary")
            return
        }

        Log.d("2ndBrain", "Processing trigger from: $packageName (Manual: $isManual)")
        if (!isManual) CaptureDebugStore.logEvent("Trigger: $packageName")

        val bigSummary = extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.trim() ?: ""
        val bigTitle = extras.getCharSequence(Notification.EXTRA_TITLE_BIG)?.toString()?.trim() ?: ""

        // Gmail specific logic: check for "android.text" if other things fail
        val androidText = extras.getCharSequence("android.text")?.toString()?.trim() ?: ""
        val ticker = notification.tickerText?.toString()?.trim() ?: ""

        // Improved content selection priority
        val contentOptions = if (packageName == "com.google.android.gm") {
            listOf(inboxContent, bigText, androidText, messagingContent, text, subText, bigSummary, ticker)
        } else {
            listOf(bigText, messagingContent, inboxContent, text, subText, bigSummary, ticker)
        }
        
        val finalContent = contentOptions.firstOrNull { it.isNotBlank() } ?: ""

        if (title.isEmpty() && finalContent.isEmpty() && bigTitle.isEmpty()) {
            return
        }

        // Standardize title and append account only if it's an email address not already present
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

        scope.launch {
            val contentToStore = finalContent.ifEmpty { "[Notification from $packageName]" }
            // Strict duplicate check: only merge if it's the exact same content from the same sender
            val existing = database.memoryDao().findExisting("notification", packageName, finalTitle, contentToStore)
            
            if (existing != null) {
                // If it's the exact same notification (like a tray refresh), just update the time
                val updated = existing.copy(
                    timestamp = System.currentTimeMillis()
                    // We don't mark as unread here because nothing actually changed
                )
                database.memoryDao().insert(updated)
            } else {
                // It's a new unique message or update, so create a new entry (default is unread)
                val entity = MemoryEntity(
                    source = "notification",
                    packageName = packageName,
                    title = finalTitle,
                    content = contentToStore,
                    deepLink = deepLink
                )
                database.memoryDao().insert(entity)
                Log.d("2ndBrain", "Captured new unique memory: $finalTitle")
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
