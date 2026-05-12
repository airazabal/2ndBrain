package com.alex.a2ndbrain.core.agents

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.alex.a2ndbrain.core.memory.AppDatabase
import com.alex.a2ndbrain.core.memory.DailySummaryEntity
import java.text.SimpleDateFormat
import java.util.*

class ReflectionWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.memoryDao()

        // Get memories from the last 24 hours
        val yesterday = System.currentTimeMillis() - (24 * 60 * 60 * 1000)
        val memories = dao.getMemoriesSince(yesterday)

        if (memories.isNotEmpty()) {
            val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            
            // In a real app, you'd send 'memories' to an LLM here.
            // For now, we simulate a summary.
            val summaryText = "You had ${memories.size} notifications today. " +
                    "Frequent apps: ${memories.groupBy { it.packageName }.keys.joinToString { it?.substringAfterLast('.') ?: "" }}. " +
                    "Highlights: ${memories.take(3).joinToString { it.title ?: "No Title" }}"

            dao.insertSummary(DailySummaryEntity(dateStr, summaryText))
        }

        return Result.success()
    }
}