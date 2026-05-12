package com.alex.a2ndbrain.core.reflection

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log

class GeminiAgent(apiKey: String, private val modelName: String) {
    private val model = GenerativeModel(
        modelName = modelName,
        apiKey = apiKey
    )

    suspend fun summarizeMemories(memoriesText: String): String = withContext(Dispatchers.IO) {
        if (memoriesText.isBlank()) return@withContext "No significant memories to process today."
        
        Log.d("GeminiAgent", "Requesting summary from model: $modelName")
        
        try {
            val response = model.generateContent(
                content {
                    text("You are a 'Second Brain' AI assistant. Analyze the following notification and clipboard captures from a user's day and provide a concise, personal, and insightful daily reflection. ")
                    text("Group related activities, highlight key interactions with people, and list potential action items. Format it in a friendly, journal-like tone.")
                    text("\n\nRAW MEMORIES:\n$memoriesText")
                }
            )
            response.text ?: "AI failed to generate a summary."
        } catch (e: Exception) {
            Log.e("GeminiAgent", "Error with model $modelName: ${e.message}")
            "AI Reflection Error ($modelName): ${e.localizedMessage}"
        }
    }
}
