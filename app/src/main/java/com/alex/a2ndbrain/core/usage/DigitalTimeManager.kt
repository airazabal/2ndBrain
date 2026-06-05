package com.alex.a2ndbrain.core.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.work.*
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.usage.UsageRepository
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

class DigitalTimeManager(
    private val context: Context,
    private val usageRepository: UsageRepository,
    private val settingsManager: CaptureSettingsManager
) {
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(4, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            "usage_sync",
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun isPermissionGranted(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = appOps.checkOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            context.packageName
        )
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    suspend fun syncUsageStats() {
        if (!isPermissionGranted()) {
            Log.w("DigitalTime", "Permission not granted for usage stats")
            return
        }

        val deviceId = android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
        val deviceName = android.os.Build.MODEL ?: "Unknown Device"

        val calendar = Calendar.getInstance()
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startTime = calendar.timeInMillis
        val endTime = System.currentTimeMillis()

        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (stats.isNullOrEmpty()) {
            // Even if empty, we might have remote data to pull
            syncWithVault(todayStr, deviceId, deviceName)
            return
        }

        stats.forEach { stat ->
            val packageName = stat.packageName

            val timeSpent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                stat.totalTimeVisible
            } else {
                stat.totalTimeInForeground
            }
            
            if (timeSpent > 0) {
                val entity = UsageStatEntity(
                    date = todayStr,
                    packageName = packageName,
                    totalTimeVisibleMs = timeSpent,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    lastTimestamp = stat.lastTimeUsed
                )
                usageRepository.insertUsageStat(entity)
            }
        }

        syncWithVault(todayStr, deviceId, deviceName)
    }

    private suspend fun syncWithVault(date: String, myDeviceId: String, myDeviceName: String) {
        val vaultUri = settingsManager.getObsidianVaultUri()
        if (vaultUri.isBlank()) return

        try {
            val root = DocumentFile.fromTreeUri(context, android.net.Uri.parse(vaultUri)) ?: return
            
            // 1. Ensure .2ndbrain/usage directory exists
            var brainDir = root.findFile(".2ndbrain")
            if (brainDir == null) brainDir = root.createDirectory(".2ndbrain")
            
            var usageDir = brainDir?.findFile("usage")
            if (usageDir == null) usageDir = brainDir?.createDirectory("usage")
            
            if (usageDir == null) return

            // 2. Export local stats for this date
            val localStats = usageRepository.getUsageStatsSince(date)
            if (localStats.isNotEmpty()) {
                val json = JSONObject()
                json.put("deviceName", myDeviceName)
                val appsArray = JSONArray()
                localStats.filter { it.deviceId == myDeviceId }.forEach { stat ->
                    val appJson = JSONObject()
                    appJson.put("pkg", stat.packageName)
                    appJson.put("time", stat.totalTimeVisibleMs)
                    appJson.put("ts", stat.lastTimestamp)
                    appsArray.put(appJson)
                }
                json.put("apps", appsArray)

                val fileName = "${date}_${myDeviceId}.json"
                var file = usageDir.findFile(fileName)
                if (file == null) file = usageDir.createFile("application/json", fileName)
                
                file?.let {
                    context.contentResolver.openOutputStream(it.uri)?.use { stream ->
                        stream.write(json.toString().toByteArray())
                    }
                }
            }

            // 3. Import remote stats
            usageDir.listFiles().forEach { file ->
                val name = file.name ?: ""
                if (name.startsWith(date) && !name.contains(myDeviceId) && name.endsWith(".json")) {
                    importFile(file, date)
                }
            }
        } catch (e: Exception) {
            Log.e("DigitalTime", "Vault sync failed", e)
        }
    }

    private suspend fun importFile(file: DocumentFile, date: String) {
        try {
            val content = context.contentResolver.openInputStream(file.uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: return
            
            val json = JSONObject(content)
            val deviceName = json.optString("deviceName", "Remote Device")
            val deviceId = file.name?.substringAfter("_")?.removeSuffix(".json") ?: "remote"
            val apps = json.getJSONArray("apps")
            
            for (i in 0 until apps.length()) {
                val app = apps.getJSONObject(i)
                val entity = UsageStatEntity(
                    date = date,
                    packageName = app.getString("pkg"),
                    totalTimeVisibleMs = app.getLong("time"),
                    deviceId = deviceId,
                    deviceName = deviceName,
                    lastTimestamp = app.getLong("ts")
                )
                usageRepository.insertUsageStat(entity)
            }
        } catch (e: Exception) {
            Log.e("DigitalTime", "Failed to import ${file.name}", e)
        }
    }
    
    suspend fun getWeeklyReport(): Map<String, Long> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        
        val stats = usageRepository.getUsageStatsSince(startDate)
        return stats.groupBy { it.packageName }
            .mapValues { entry -> entry.value.sumOf { it.totalTimeVisibleMs } }
    }

    fun getUsageStatsSinceFlow(startDate: String): kotlinx.coroutines.flow.Flow<List<UsageStatEntity>> {
        return usageRepository.getUsageStatsSinceFlow(startDate)
    }
}
