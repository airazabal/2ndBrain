package com.alex.a2ndbrain.core.reflection

import android.content.Context
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import android.util.Log
import java.io.File

class ModelPicker(private val context: Context, private val settingsManager: CaptureSettingsManager) {
    enum class ModelType {
        GEMINI_CLOUD,
        LITERT_LOCAL, // Replaces GEMINI_NANO with the broader LiteRT-LM
        BASIC_TEMPLATE
    }

    fun getBestModel(): ModelType {
        val preferred = settingsManager.getPreferredModelType()
        if (preferred != "AUTO") {
            try {
                return ModelType.valueOf(preferred)
            } catch (_: Exception) {}
        }

        val apiKey = settingsManager.getGeminiApiKey()
        
        return when {
            apiKey.isNotBlank() -> ModelType.GEMINI_CLOUD
            isLiteRTModelPresent() -> ModelType.LITERT_LOCAL
            else -> ModelType.BASIC_TEMPLATE
        }
    }

    fun isLiteRTModelPresent(): Boolean {
        val selectedModel = settingsManager.getSelectedLiteRTModel()
        val modelFile = File(context.filesDir, "models/$selectedModel.litertlm")
        return modelFile.exists() && modelFile.length() > 0
    }

    fun isLiteRTModelDownloading(): Boolean {
        val selectedModel = settingsManager.getSelectedLiteRTModel()
        val modelFile = File(context.filesDir, "models/$selectedModel.litertlm")
        return modelFile.exists() && modelFile.length() == 0L
    }

    suspend fun runLiteRTInference(prompt: String): String {
        return try {
            val selectedModel = settingsManager.getSelectedLiteRTModel()
            val modelFile = File(context.filesDir, "models/$selectedModel.litertlm")
            if (!modelFile.exists()) return "Error: Model file not found at ${modelFile.absolutePath}"

            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = com.google.ai.edge.litertlm.Backend.CPU(numOfThreads = 4),
                maxNumTokens = 1024
            )

            // Truncate to on-device token limit. Log so caller knows context was discarded.
            if (prompt.length > 1000) {
                Log.w("ModelPicker", "LiteRT prompt truncated: ${prompt.length} chars → 1000 (on-device limit). Switch to cloud model for full context.")
            }
            val safePrompt = if (prompt.length > 1000) prompt.take(1000) + "... [truncated]" else prompt

            // Chat template wrapping based on model architecture
            val isGemma = selectedModel.contains("Gemma", ignoreCase = true)
            val formattedPrompt = if (isGemma) {
                "<start_of_turn>system\n" +
                "You are a helpful personal assistant. Generate a high-quality daily reflection based on the user's memories.\n" +
                "Instructions:\n" +
                "- Summarize the day's main activities and mood.\n" +
                "- Highlight key connections or interesting events.\n" +
                "- Provide a brief, encouraging thought for tomorrow.\n" +
                "- Be concise (aim for 2-3 short, descriptive paragraphs).\n" +
                "- Do NOT include any internal thought processes, reasoning, or <think> tags.\n" +
                "- Start directly with the reflection.\n" +
                "<end_of_turn>\n" +
                "<start_of_turn>user\n" +
                "Memories:\n$safePrompt<end_of_turn>\n" +
                "<start_of_turn>model\n"
            } else {
                "<|im_start|>system\n" +
                "You are a helpful personal assistant. Generate a high-quality daily reflection based on the user's memories.\n" +
                "Instructions:\n" +
                "- Summarize the day's main activities and mood.\n" +
                "- Highlight key connections or interesting events.\n" +
                "- Provide a brief, encouraging thought for tomorrow.\n" +
                "- Be concise (aim for 2-3 short, descriptive paragraphs).\n" +
                "- Do NOT include any internal thought processes, reasoning, or <think> tags.\n" +
                "- Start directly with the reflection.\n" +
                "<|im_end|>\n" +
                "<|im_start|>user\n" +
                "Memories:\n$safePrompt<|im_end|>\n" +
                "<|im_start|>assistant\n"
            }

            Engine(engineConfig).use { engine ->
                engine.initialize()
                val samplerConfig = SamplerConfig(
                    topK = 40,
                    topP = 0.9,
                    temperature = 0.7
                )
                val conversationConfig = ConversationConfig(samplerConfig = samplerConfig)
                
                engine.createConversation(conversationConfig).use { conversation ->
                    val response = StringBuilder()
                    conversation.sendMessageAsync(formattedPrompt).collect { chunk ->
                        response.append(chunk)
                    }
                    response.toString()
                }
            }
        } catch (e: Exception) {
            "LiteRT Error: ${e.message}"
        }
    }
}
