package com.alex.a2ndbrain.core.capture

import android.content.Context
import android.content.SharedPreferences
import com.alex.a2ndbrain.BuildConfig

class CaptureSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("capture_settings", Context.MODE_PRIVATE)

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
        val savedKey = prefs.getString("gemini_api_key", "")?.trim() ?: ""
        if (savedKey.isNotBlank()) return savedKey
        return BuildConfig.GEMINI_API_KEY
    }

    fun saveGeminiApiKey(key: String) {
        prefs.edit().putString("gemini_api_key", key.trim()).apply()
    }

    fun getGeminiModel(): String {
        return prefs.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
    }

    fun saveGeminiModel(model: String) {
        prefs.edit().putString("gemini_model", model).apply()
    }
}
