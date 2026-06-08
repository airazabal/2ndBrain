package com.alex.a2ndbrain.core.capture

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

class CaptureSettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("capture_settings", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy { createSecurePrefs() }

    private fun createSecurePrefs(): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return try {
            val sp = EncryptedSharedPreferences.create(
                context,
                "secure_capture_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            // Migrate key if exists in old unencrypted prefs
            if (prefs.contains("gemini_api_key")) {
                val oldKey = prefs.getString("gemini_api_key", "")?.trim() ?: ""
                if (oldKey.isNotBlank()) sp.edit().putString("gemini_api_key", oldKey).apply()
                prefs.edit().remove("gemini_api_key").apply()
            }
            sp
        } catch (e: Exception) {
            // Keystore key was lost (e.g. after reinstall with backup restore).
            // Wipe the stale encrypted file and start fresh — user will need to re-enter keys.
            Log.w("CaptureSettingsManager", "EncryptedSharedPreferences corrupt, resetting: ${e.message}")
            File(context.filesDir.parent, "shared_prefs/secure_capture_settings.xml").delete()
            EncryptedSharedPreferences.create(
                context,
                "secure_capture_settings",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }

    fun isPackageAllowed(packageName: String): Boolean {
        // Default to true for now, or false if you want opt-in
        // Let's default to true but allow user to disable
        return prefs.getBoolean("pkg_$packageName", true)
    }

    fun setPackageAllowed(packageName: String, allowed: Boolean) {
        prefs.edit().putBoolean("pkg_$packageName", allowed).apply()
    }

    fun setPackagesAllowed(packageNames: List<String>, allowed: Boolean) {
        val editor = prefs.edit()
        packageNames.forEach { editor.putBoolean("pkg_$it", allowed) }
        editor.apply()
    }
    
    fun setAllAllowed(allowed: Boolean) {
        // This is tricky with individual keys, but we can have a master switch
        prefs.edit().putBoolean("all_notifications_enabled", allowed).apply()
    }
    
    fun areNotificationsEnabled(): Boolean {
        return prefs.getBoolean("all_notifications_enabled", true)
    }

    fun getMonitoredApps(): Set<String> {
        return prefs.getStringSet("monitored_apps_list", emptySet()) ?: emptySet()
    }

    fun saveMonitoredApps(apps: Set<String>) {
        prefs.edit().putStringSet("monitored_apps_list", apps).apply()
    }

    fun getGeminiApiKey(): String {
        return securePrefs.getString("gemini_api_key", "")?.trim() ?: ""
    }

    fun saveGeminiApiKey(key: String) {
        securePrefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun getTodoistApiToken(): String {
        return securePrefs.getString("todoist_api_token", "")?.trim() ?: ""
    }

    fun saveTodoistApiToken(token: String) {
        securePrefs.edit().putString("todoist_api_token", token.trim()).apply()
    }

    fun getGeminiModel(): String {
        return prefs.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
    }

    fun saveGeminiModel(model: String) {
        prefs.edit().putString("gemini_model", model).apply()
    }

    fun getRefreshIntervalMinutes(): Int = prefs.getInt("refresh_interval_min", 30)
    fun setRefreshIntervalMinutes(minutes: Int) { prefs.edit().putInt("refresh_interval_min", minutes).apply() }

    fun getObsidianVaultUri(): String {
        return prefs.getString("obsidian_vault_uri", "") ?: ""
    }

    fun saveObsidianVaultUri(uri: String) {
        prefs.edit().putString("obsidian_vault_uri", uri).apply()
    }

    fun getPreferredModelType(): String {
        return prefs.getString("preferred_model_type", "AUTO") ?: "AUTO"
    }

    fun savePreferredModelType(type: String) {
        prefs.edit().putString("preferred_model_type", type).apply()
    }

    fun getSelectedLiteRTModel(): String {
        return prefs.getString("selected_litert_model", "Gemma-3-1B-IT") ?: "Gemma-3-1B-IT"
    }

    fun saveSelectedLiteRTModel(modelName: String) {
        prefs.edit().putString("selected_litert_model", modelName).apply()
    }

    fun getLastSuccessfulModel(): String {
        return prefs.getString("last_successful_model", "") ?: ""
    }

    fun saveLastSuccessfulModel(modelName: String) {
        prefs.edit().putString("last_successful_model", modelName).apply()
    }

    fun getThemePreference(): String {
        return prefs.getString("theme_preference", "SYSTEM") ?: "SYSTEM"
    }

    fun saveThemePreference(theme: String) {
        prefs.edit().putString("theme_preference", theme).apply()
    }

    fun isNotificationAccessGranted(): Boolean {
        return androidx.core.app.NotificationManagerCompat
            .getEnabledListenerPackages(context)
            .contains(context.packageName)
    }

    fun isUsageAccessGranted(): Boolean {
        val appOps = context.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }
    fun hasCompletedSetup(): Boolean {
        return prefs.getBoolean("has_completed_setup", false)
    }

    fun markSetupCompleted() {
        prefs.edit().putBoolean("has_completed_setup", true).apply()
    }

    fun getHomeSummaryConfig(): HomeSummaryConfig {
        val json = prefs.getString("home_summary_config", null) ?: return HomeSummaryConfig()
        return try {
            val obj = org.json.JSONObject(json)
            HomeSummaryConfig(
                defaultMode = HomeDefaultMode.valueOf(
                    obj.optString("defaultMode", HomeDefaultMode.SUMMARY_ONLY.name)
                ),
                showNextEventPill      = obj.optBoolean("showNextEventPill", true),
                showStepsPill          = obj.optBoolean("showStepsPill", true),
                showSleepMeditationPill = obj.optBoolean("showSleepMeditationPill", true),
                showAlerts             = obj.optBoolean("showAlerts", true),
                showSenseOfDayText     = obj.optBoolean("showSenseOfDayText", true)
            )
        } catch (e: Exception) {
            HomeSummaryConfig()
        }
    }

    fun saveHomeSummaryConfig(config: HomeSummaryConfig) {
        val json = org.json.JSONObject().apply {
            put("defaultMode",            config.defaultMode.name)
            put("showNextEventPill",      config.showNextEventPill)
            put("showStepsPill",          config.showStepsPill)
            put("showSleepMeditationPill", config.showSleepMeditationPill)
            put("showAlerts",             config.showAlerts)
            put("showSenseOfDayText",     config.showSenseOfDayText)
        }.toString()
        prefs.edit().putString("home_summary_config", json).apply()
    }

    fun getLastDetailsExpanded(): Boolean = prefs.getBoolean("home_last_details_expanded", false)

    fun saveLastDetailsExpanded(expanded: Boolean) {
        prefs.edit().putBoolean("home_last_details_expanded", expanded).apply()
    }

    fun isCalendarSyncEnabled(): Boolean = prefs.getBoolean("calendar_sync_enabled", false)

    fun setCalendarSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("calendar_sync_enabled", enabled).apply()
    }

    fun getTodoistHabitProjectId(): String = prefs.getString("todoist_habit_project_id", "") ?: ""
    fun saveTodoistHabitProjectId(id: String) { prefs.edit().putString("todoist_habit_project_id", id).apply() }

    // ── Daily Goals (Sense of Day) ────────────────────────────────────────────
    fun getStepsGoal(): Int = prefs.getInt("steps_goal", 10000)
    fun setStepsGoal(goal: Int) { prefs.edit().putInt("steps_goal", goal).apply() }

    fun getSleepGoalHours(): Float = prefs.getFloat("sleep_goal_hours", 7.5f)
    fun setSleepGoalHours(hours: Float) { prefs.edit().putFloat("sleep_goal_hours", hours).apply() }

    fun getExerciseGoalMinutes(): Int = prefs.getInt("exercise_goal_minutes", 30)
    fun setExerciseGoalMinutes(minutes: Int) { prefs.edit().putInt("exercise_goal_minutes", minutes).apply() }

    fun getDigitalFocusBaselineMinutes(): Int = prefs.getInt("digital_focus_baseline_minutes", 120)
    fun setDigitalFocusBaselineMinutes(minutes: Int) { prefs.edit().putInt("digital_focus_baseline_minutes", minutes).apply() }
}
