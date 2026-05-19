package com.alex.a2ndbrain.core.capture

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class CaptureSettingsManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("capture_settings", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
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
            if (oldKey.isNotBlank()) {
                sp.edit().putString("gemini_api_key", oldKey).apply()
            }
            prefs.edit().remove("gemini_api_key").apply()
        }
        sp
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

    fun getGeminiModel(): String {
        return prefs.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash"
    }

    fun saveGeminiModel(model: String) {
        prefs.edit().putString("gemini_model", model).apply()
    }

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
}
