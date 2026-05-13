package com.alex.a2ndbrain.core.usage

import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.memory.UsageStatEntity
import java.text.SimpleDateFormat
import java.util.*

class DigitalTimeManager(private val context: Context) {
    private val database = AppDatabase.getDatabase(context)
    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager

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

        if (stats.isNullOrEmpty()) return

        stats.forEach { stat ->
            val timeSpent = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                stat.totalTimeVisible
            } else {
                stat.totalTimeInForeground
            }
            
            if (timeSpent > 0) {
                val entity = UsageStatEntity(
                    date = todayStr,
                    packageName = stat.packageName,
                    totalTimeVisibleMs = timeSpent,
                    deviceId = deviceId,
                    deviceName = deviceName,
                    lastTimestamp = stat.lastTimeUsed
                )
                database.memoryDao().insertUsageStat(entity)
            }
        }
    }
    
    suspend fun getWeeklyReport(): Map<String, Long> {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -7)
        val startDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        
        val stats = database.memoryDao().getUsageStatsSince(startDate)
        return stats.groupBy { it.packageName }
            .mapValues { entry -> entry.value.sumOf { it.totalTimeVisibleMs } }
    }
}
