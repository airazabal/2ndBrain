package com.alex.a2ndbrain.core.reflection

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class CircadianAnalysisWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    private val manager: CircadianInsightManager by inject()

    override suspend fun doWork(): Result {
        return try {
            Log.d("CircadianAnalysisWorker", "Starting weekly circadian analysis")
            val error = manager.generateInsight()
            if (error != null) {
                Log.e("CircadianAnalysisWorker", "Analysis failed: $error")
                Result.failure()
            } else {
                Result.success()
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e("CircadianAnalysisWorker", "Unexpected error", e)
            Result.failure()
        }
    }
}
