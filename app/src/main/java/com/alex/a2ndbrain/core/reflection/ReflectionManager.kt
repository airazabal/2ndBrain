package com.alex.a2ndbrain.core.reflection

import android.content.Context
import androidx.work.*
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import com.alex.a2ndbrain.core.memory.MemoryEntity
import com.alex.a2ndbrain.core.usage.DigitalTimeManager
import kotlinx.coroutines.withTimeout
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class ReflectionManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val settingsManager = CaptureSettingsManager(context)

    fun schedulePeriodicReflection() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<ReflectionWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "daily_reflection",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    suspend fun generateDailyReflection(): String? {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        
        val isMorning = hour in 4..11
        val summaryType = if (isMorning) "briefing" else "reflection"

        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfToday = calendar.timeInMillis

        // For morning briefing, we look at items from the last 24 hours to get yesterday's context
        // For evening reflection, we just look at today.
        val searchStartTime = if (isMorning) startOfToday - (12 * 60 * 60 * 1000) else startOfToday

        // Sync latest digital time before reflection
        val digitalTimeManager = DigitalTimeManager(context)
        digitalTimeManager.syncUsageStats()

        val memories = database.memoryDao().getMemoriesSince(searchStartTime)
        if (memories.isEmpty()) {
            return "No sufficient data found to generate a reflection yet."
        }

        val usageStats = database.memoryDao().getUsageStatsSince(todayStr)
        val usageByDevice = usageStats.groupBy { it.deviceName }
        
        val usageReport = usageByDevice.entries.joinToString("\n\n") { (device, stats) ->
            val topApps = stats.sortedByDescending { it.totalTimeVisibleMs }
                .take(3)
                .joinToString("\n") { 
                    "- ${it.packageName.substringAfterLast(".")}: ${it.totalTimeVisibleMs / 1000 / 60} mins"
                }
            "Device: $device\n$topApps"
        }

        val apiKey = settingsManager.getGeminiApiKey()
        val preferredModel = settingsManager.getGeminiModel()
        
        val (summaryText, modelUsed) = if (apiKey.isNotBlank()) {
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val rawData = memories.sortedBy { it.timestamp }.joinToString("\n") { 
                val time = timeFormat.format(Date(it.timestamp))
                "- [$time][${getAppName(it)}] ${it.title ?: ""}: ${it.content.take(150)}"
            }
            
            val promptContext = """
                $rawData
                
                DIGITAL USAGE (Today's Totals):
                $usageReport
            """.trimIndent()

            try {
                // Apply a 30-second timeout for the AI response
                withTimeout(30000L) {
                    val result = GeminiAgent(apiKey).summarizeMemories(promptContext, preferredModel, isMorningBriefing = isMorning)
                    result.text to result.modelName
                }
            } catch (e: Exception) {
                "AI response timed out or failed. Please try again." to "Timeout"
            }
        } else {
            generateBasicSummary(memories) to "Local Template"
        }

        val summaryEntity = DailySummaryEntity(
            date = todayStr,
            type = summaryType,
            summary = summaryText,
            timestamp = System.currentTimeMillis(),
            modelName = modelUsed
        )

        database.memoryDao().insertSummary(summaryEntity)
        return null // Success
    }

    private fun generateBasicSummary(memories: List<MemoryEntity>): String = buildString {
        // 1. Executive Summary
        val appCount = memories.filter { it.source == "notification" }.mapNotNull { it.packageName }.distinct().size
        append("TODAY'S SYNTHESIS\n")
        append("Processed $appCount apps across ${memories.size} total interactions.\n\n")

        // 2. Timeline View
        append("📅 TIMELINE\n")
        val morning = memories.filter { isDuring(it.timestamp, 5, 12) }
        val afternoon = memories.filter { isDuring(it.timestamp, 12, 18) }
        val evening = memories.filter { isDuring(it.timestamp, 18, 24) }

        if (morning.isNotEmpty()) append("• Morning: Active in ${morning.firstNotNullOfOrNull { getAppName(it) }}\n")
        if (afternoon.isNotEmpty()) append("• Afternoon: Focused on ${afternoon.groupBy { getAppName(it) }.maxBy { it.value.size }.key}\n")
        if (evening.isNotEmpty()) append("• Evening: Caught up on ${evening.size} updates\n")
        append("\n")

        // 3. Action Items / Potentials
        val potentialActions = memories.filter { 
            it.content.contains(Regex("(meet|call|email|send|buy|check|remind|tomorrow|today)", RegexOption.IGNORE_CASE))
        }
        if (potentialActions.isNotEmpty()) {
            append("🚀 POTENTIAL ACTIONS\n")
            potentialActions.distinctBy { it.content }.take(3).forEach {
                append("- ${it.content.take(50).trim()}...\n")
            }
            append("\n")
        }

        // 4. Top Connections
        val people = memories.filter { it.packageName == "com.google.android.gm" || it.packageName?.contains("messaging") == true }
            .mapNotNull { it.title?.split(" ")?.firstOrNull() }
            .filter { it.length > 2 }
            .groupBy { it }
            .toList()
            .sortedByDescending { it.second.size }
            .take(3)
        
        if (people.isNotEmpty()) {
            append("👤 TOP CONNECTIONS\n")
            append(people.joinToString(", ") { it.first })
            append("\n\n")
        }

        append("\n[Tip: Add a Gemini API Key in Settings for AI-powered reflections!]")
    }

    private fun isDuring(timestamp: Long, startHour: Int, endHour: Int): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in startHour until endHour
    }

    private fun getAppName(memory: MemoryEntity): String? {
        val key = memory.packageName ?: memory.source
        return try {
            val appInfo = context.packageManager.getApplicationInfo(key, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            if (key == "clipboard") "Clipboard" else key
        }
    }
}
