package com.alex.a2ndbrain.core.reflection

import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.ai.client.generativeai.type.RequestOptions
import android.util.Log

class GeminiAgent(private val apiKey: String) {

    private fun createModel(name: String, version: String) = GenerativeModel(
        modelName = name.trim(),
        apiKey = apiKey.trim(),
        requestOptions = RequestOptions(apiVersion = version)
    )

    suspend fun summarizeMemories(memoriesText: String, preferredModel: String? = null): SummaryResult = withContext(Dispatchers.IO) {
        if (memoriesText.isBlank()) return@withContext SummaryResult("No significant memories to process today.", "N/A")
        
        val prompt = """
            You are a 'Second Brain' AI assistant. Analyze the following notification and clipboard captures from a user's day and provide a concise, personal, and insightful daily reflection.
            Group related activities, highlight key interactions with people, and list potential action items. Format it in a friendly, journal-like tone.

            RAW MEMORIES:
            $memoriesText
        """.trimIndent()

        // Updated for Gemini 3.1 and 2.5 (Gemini 1.5 is retired in many regions/SDKs)
        val baseAttempts = listOf(
            "gemini-3.1-flash-lite-preview" to "v1beta",
            "gemini-3.1-pro-preview" to "v1beta",
            "gemini-3-flash-preview" to "v1beta",
            "gemini-2.5-flash" to "v1beta",
            "gemini-2.5-pro" to "v1beta",
            "gemini-2.0-flash" to "v1beta"
        )
        
        // Prioritize preferred model if provided
        val attempts = if (preferredModel != null && preferredModel.isNotBlank()) {
            val pref = preferredModel.trim()
            val version = if (pref.contains("2.0")) "v1beta" else "v1beta" // v1beta is safest for most
            listOf(pref to version) + baseAttempts.filter { it.first != pref }
        } else {
            baseAttempts
        }
        
        val keySnippet = if (apiKey.length > 6) "${apiKey.take(4)}...${apiKey.takeLast(2)}" else "Invalid/Short"
        val errorLog = mutableListOf<String>()
        val debugInfo = "Debug Key: $keySnippet"

        for ((name, version) in attempts) {
            try {
                Log.d("GeminiAgent", "Attempting $name with $version")
                val model = createModel(name, version)
                val response = model.generateContent(prompt)
                val result = response.text
                if (!result.isNullOrBlank()) return@withContext SummaryResult(result, name)
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                Log.e("GeminiAgent", "Failed $name/$version: $msg")
                errorLog.add("[$name/$version]: $msg")
                
                // Continue if it's a 404, 400 or specific "not found" error
                if (msg.contains("404") || msg.contains("400") || msg.contains("not found", ignoreCase = true)) {
                    continue
                }
                
                // For critical errors (Auth, Quota), stop immediately
                if (msg.contains("401") || msg.contains("403") || msg.contains("API_KEY") || msg.contains("QUOTA")) {
                    break
                }
            }
        }

        val lastError = errorLog.lastOrNull() ?: "No attempts made"
        val fullLog = "$debugInfo\n${errorLog.joinToString("\n")}"
        
        val finalResult = when {
            lastError.contains("API_KEY_INVALID") || lastError.contains("401") -> 
                "❌ Invalid API Key. Please re-generate your key in Google AI Studio.\n\n$debugInfo"
            
            lastError.contains("prepayment credits") || lastError.contains("depleted") || lastError.contains("403") -> 
                "💳 Your Google AI Studio access is restricted.\n\n" +
                "Even on the free tier, users in the EU/UK/CH often need an active 'Pay-as-you-go' billing plan with a positive balance to access the API.\n\n" +
                "✅ Fix:\n" +
                "1. Go to aistudio.google.com > Settings > Billing.\n" +
                "2. Ensure your payment method is active.\n" +
                "3. If you just enabled billing, it may take up to an hour to sync.\n\n" +
                "Technical Log:\n$fullLog"
                
            lastError.contains("QUOTA_EXCEEDED") || lastError.contains("429") -> 
                "⏳ API Quota exceeded. Please wait a minute.\n\n$debugInfo"
                
            errorLog.isNotEmpty() && errorLog.all { it.contains("404") || it.contains("400") || it.contains("not found") } -> 
                "❗ All models returned 404/400. This usually means regional restrictions.\n\n" +
                "✅ Solution for EU/UK users: Link a billing account in AI Studio to 'unlock' models.\n\n" +
                "Technical Log:\n$fullLog"
                
            else -> "❗ AI Error: $lastError\n\nFull Log:\n$fullLog"
        }

        return@withContext SummaryResult(finalResult, "Error Fallback")
    }
}

data class SummaryResult(val text: String, val modelName: String)
