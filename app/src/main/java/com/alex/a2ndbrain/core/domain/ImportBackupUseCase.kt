package com.alex.a2ndbrain.core.domain

import com.alex.a2ndbrain.core.capture.SettingsRepository
import org.json.JSONObject

class ImportBackupUseCase(
    private val settingsManager: SettingsRepository
) {
    suspend operator fun invoke(json: String) {
        val obj = JSONObject(json)

        val appsArray = obj.optJSONArray("monitoredApps")
        if (appsArray != null) {
            val apps = (0 until appsArray.length()).map { appsArray.getString(it) }.toSet()
            settingsManager.saveMonitoredApps(apps)
        }

        obj.optString("todoistApiToken").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveTodoistApiToken(it) }
        obj.optString("geminiApiKey").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveGeminiApiKey(it) }
        obj.optString("geminiModel").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveGeminiModel(it) }
        obj.optString("preferredModelType").takeIf { it.isNotBlank() }
            ?.let { settingsManager.savePreferredModelType(it) }
        obj.optString("selectedLiteRTModel").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveSelectedLiteRTModel(it) }
        obj.optString("themePreference").takeIf { it.isNotBlank() }
            ?.let { settingsManager.saveThemePreference(it) }
        if (obj.has("refreshIntervalMinutes"))
            settingsManager.setRefreshIntervalMinutes(obj.getInt("refreshIntervalMinutes"))
        if (obj.has("calendarSyncEnabled"))
            settingsManager.setCalendarSyncEnabled(obj.getBoolean("calendarSyncEnabled"))
        if (obj.has("stepsGoal"))
            settingsManager.setStepsGoal(obj.getInt("stepsGoal"))
        if (obj.has("sleepGoalHours"))
            settingsManager.setSleepGoalHours(obj.getDouble("sleepGoalHours").toFloat())
        if (obj.has("exerciseGoalMinutes"))
            settingsManager.setExerciseGoalMinutes(obj.getInt("exerciseGoalMinutes"))
        if (obj.has("digitalFocusBaselineMinutes"))
            settingsManager.setDigitalFocusBaselineMinutes(obj.getInt("digitalFocusBaselineMinutes"))
        val distractionAppsArray = obj.optJSONArray("distractionApps")
        if (distractionAppsArray != null) {
            val apps = (0 until distractionAppsArray.length()).map { distractionAppsArray.getString(it) }.toSet()
            settingsManager.setDistractionApps(apps)
        }
        if (obj.has("distractionThresholdMinutes"))
            settingsManager.setDistractionThresholdMinutes(obj.getInt("distractionThresholdMinutes"))
    }
}
