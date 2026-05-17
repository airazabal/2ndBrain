package com.alex.a2ndbrain.core.reflection

import android.content.Context
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager

import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.SamplerConfig
import java.io.File

class ModelPicker(private val context: Context) {
    private val settingsManager = CaptureSettingsManager(context)

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
                maxNumTokens = 2048
            )

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
                    conversation.sendMessageAsync(prompt).collect { chunk ->
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
