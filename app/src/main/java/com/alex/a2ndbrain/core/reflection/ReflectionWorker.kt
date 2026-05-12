package com.alex.a2ndbrain.core.reflection

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import android.util.Log

class ReflectionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            Log.d("ReflectionWorker", "Starting daily reflection generation...")
            val manager = ReflectionManager(applicationContext)
            manager.generateDailyReflection()
            Result.success()
        } catch (e: Exception) {
            Log.e("ReflectionWorker", "Error generating reflection", e)
            Result.retry()
        }
    }
}
