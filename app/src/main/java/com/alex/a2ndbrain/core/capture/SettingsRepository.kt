package com.alex.a2ndbrain.core.capture

interface SettingsRepository {
    // Package allow/deny
    fun isPackageAllowed(packageName: String): Boolean
    fun setPackageAllowed(packageName: String, allowed: Boolean)
    fun setPackagesAllowed(packageNames: List<String>, allowed: Boolean)
    fun setAllAllowed(allowed: Boolean)
    fun areNotificationsEnabled(): Boolean

    // Monitored apps
    fun getMonitoredApps(): Set<String>
    fun saveMonitoredApps(apps: Set<String>)

    // API keys (stored encrypted)
    fun getGeminiApiKey(): String
    fun saveGeminiApiKey(key: String)
    fun getTodoistApiToken(): String
    fun saveTodoistApiToken(token: String)

    // AI model settings
    fun getGeminiModel(): String
    fun saveGeminiModel(model: String)
    fun getPreferredModelType(): String
    fun savePreferredModelType(type: String)
    fun getSelectedLiteRTModel(): String
    fun saveSelectedLiteRTModel(modelName: String)
    fun getLastSuccessfulModel(): String
    fun saveLastSuccessfulModel(modelName: String)

    // General settings
    fun getRefreshIntervalMinutes(): Int
    fun setRefreshIntervalMinutes(minutes: Int)
    fun getObsidianVaultUri(): String
    fun saveObsidianVaultUri(uri: String)
    fun getThemePreference(): String
    fun saveThemePreference(theme: String)

    // Permission checks
    fun isNotificationAccessGranted(): Boolean
    fun isUsageAccessGranted(): Boolean

    // Onboarding
    fun hasCompletedSetup(): Boolean
    fun markSetupCompleted()

    // Home summary config
    fun getHomeSummaryConfig(): HomeSummaryConfig
    fun saveHomeSummaryConfig(config: HomeSummaryConfig)
    fun getLastDetailsExpanded(): Boolean
    fun saveLastDetailsExpanded(expanded: Boolean)

    // Integrations
    fun isCalendarSyncEnabled(): Boolean
    fun setCalendarSyncEnabled(enabled: Boolean)
    fun getTodoistHabitProjectId(): String
    fun saveTodoistHabitProjectId(id: String)

    // Daily goals
    fun getStepsGoal(): Int
    fun setStepsGoal(goal: Int)
    fun getSleepGoalHours(): Float
    fun setSleepGoalHours(hours: Float)
    fun getExerciseGoalMinutes(): Int
    fun setExerciseGoalMinutes(minutes: Int)
    fun getDigitalFocusBaselineMinutes(): Int
    fun setDigitalFocusBaselineMinutes(minutes: Int)

    // Local goal variants (with timestamp)
    fun setStepsGoalLocal(goal: Int)
    fun setSleepGoalHoursLocal(hours: Float)
    fun setExerciseGoalMinutesLocal(minutes: Int)
    fun setDigitalFocusBaselineMinutesLocal(minutes: Int)
    fun getSettingsUpdatedAt(): Long

    // Sync timestamps
    fun getLastNearbySyncTimestamp(): Long
    fun setLastNearbySyncTimestamp(ts: Long)

    // Conflict dismissal
    fun getDismissedConflictIds(date: String): Set<String>
    fun addDismissedConflictId(date: String, id: String)

    // Quick Settings tile
    fun saveSenseOfDayScoreForTile(score: Int)

    // App label resolution
    fun getAppLabel(packageName: String?): String

    // Distraction tracking
    fun getDistractionApps(): Set<String>
    fun setDistractionApps(apps: Set<String>)
    fun getDistractionThresholdMinutes(): Int
    fun setDistractionThresholdMinutes(mins: Int)

    // P2P sync status
    fun getLastP2pSyncTime(): Long
    fun setLastP2pSyncTime(time: Long)
    fun getLastP2pSyncSuccess(): Boolean
    fun setLastP2pSyncSuccess(success: Boolean)
    fun getConsecutiveP2pSyncFailures(): Int
    fun setConsecutiveP2pSyncFailures(count: Int)
}
