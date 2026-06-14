package com.alex.a2ndbrain.core.domain

import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.core.memory.MemoryRepository
import org.json.JSONArray
import org.json.JSONObject

class ExportBackupUseCase(
    private val memoryRepository: MemoryRepository,
    private val healthRepository: HealthRepository,
    private val settingsManager: CaptureSettingsManager
) {
    suspend operator fun invoke(): String {
        val monitoredApps = settingsManager.getMonitoredApps()
        val ninetyDaysAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        val memories = memoryRepository.getRecentMemories(ninetyDaysAgo)
        val summaries = memoryRepository.getAllSummariesSync()
        val healthSnapshots = healthRepository.getSnapshotsForSync(90)

        val appsArray = JSONArray().also { arr -> monitoredApps.forEach { arr.put(it) } }

        val memoriesArray = JSONArray()
        memories.forEach { m ->
            memoriesArray.put(JSONObject().apply {
                put("id", m.id)
                put("source", m.source)
                put("packageName", m.packageName ?: "")
                put("title", m.title ?: "")
                put("content", m.content)
                put("tags", m.tags ?: "")
                put("timestamp", m.timestamp)
            })
        }

        val summariesArray = JSONArray()
        summaries.forEach { s ->
            summariesArray.put(JSONObject().apply {
                put("date", s.date)
                put("type", s.type)
                put("summary", s.summary)
                put("modelName", s.modelName)
                put("timestamp", s.timestamp)
            })
        }

        val healthArray = JSONArray()
        healthSnapshots.forEach { snap ->
            healthArray.put(JSONObject().apply {
                put("date", snap.date)
                put("steps", snap.steps)
                put("sleepMinutes", snap.sleepMinutes)
                put("avgHeartRate", snap.avgHeartRate)
                put("deviceId", snap.deviceId)
            })
        }

        val todoistToken = settingsManager.getTodoistApiToken()
        val geminiKey = settingsManager.getGeminiApiKey()

        return JSONObject().apply {
            put("version", 4)
            put("exportedAt", System.currentTimeMillis())
            put("monitoredApps", appsArray)
            put("memories", memoriesArray)
            put("reflections", summariesArray)
            put("healthSnapshots", healthArray)
            if (todoistToken.isNotBlank()) put("todoistApiToken", todoistToken)
            if (geminiKey.isNotBlank()) put("geminiApiKey", geminiKey)
            put("geminiModel", settingsManager.getGeminiModel())
            put("preferredModelType", settingsManager.getPreferredModelType())
            put("selectedLiteRTModel", settingsManager.getSelectedLiteRTModel())
            put("themePreference", settingsManager.getThemePreference())
            put("refreshIntervalMinutes", settingsManager.getRefreshIntervalMinutes())
            put("calendarSyncEnabled", settingsManager.isCalendarSyncEnabled())
            put("stepsGoal", settingsManager.getStepsGoal())
            put("sleepGoalHours", settingsManager.getSleepGoalHours())
            put("exerciseGoalMinutes", settingsManager.getExerciseGoalMinutes())
            put("digitalFocusBaselineMinutes", settingsManager.getDigitalFocusBaselineMinutes())
            put("distractionApps", JSONArray().also { arr -> settingsManager.getDistractionApps().forEach { arr.put(it) } })
            put("distractionThresholdMinutes", settingsManager.getDistractionThresholdMinutes())
        }.toString(2)
    }
}
