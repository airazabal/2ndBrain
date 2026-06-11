package com.alex.a2ndbrain.core.agents

import android.util.Log
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.reflection.GeminiAgent
import com.alex.a2ndbrain.core.reflection.ModelPicker
import kotlinx.coroutines.withTimeout

/**
 * ModelRouter — routes inference tasks to the cheapest model that can handle them.
 *
 * Routing table (May 2026 pricing):
 *   LOW/MEDIUM  → gemini-2.5-flash-lite  ($0.10/$0.40 per 1M tokens)
 *   HIGH        → gemini-2.5-flash       ($0.30/$2.50 per 1M tokens)
 *   On-device   → LiteRT (Qwen)          free, private
 *   No key      → basic template         free
 *
 * Single entry point for all AI calls. ViewModels never touch GeminiAgent directly.
 * ReflectionManager.runChatInference() is the legacy path — new code uses this class.
 */
class ModelRouter(
    private val settingsManager: CaptureSettingsManager,
    private val geminiAgent: GeminiAgent,
    private val modelPicker: ModelPicker
) {
    enum class Complexity { LOW, MEDIUM, HIGH }

    /**
     * Run a single-turn inference. Used by ReflectionAgent and for simple Copilot calls.
     */
    suspend fun run(
        prompt: String,
        complexity: Complexity = Complexity.MEDIUM,
        timeoutMs: Long = 60_000L
    ): Pair<String, String> {
        return when (modelPicker.getBestModel()) {
            ModelPicker.ModelType.LITERT_LOCAL -> {
                val start = System.currentTimeMillis()
                val raw = modelPicker.runLiteRTInference(prompt)
                val elapsed = "%.1fs".format((System.currentTimeMillis() - start) / 1000f)
                val model = settingsManager.getSelectedLiteRTModel()
                cleanLiteRTResponse(raw) to "LiteRT ($model) — $elapsed"
            }

            ModelPicker.ModelType.BASIC_TEMPLATE -> {
                "Add a Gemini API key in Settings to enable AI responses." to "Template"
            }

            ModelPicker.ModelType.GEMINI_CLOUD -> {
                val targetModel = when (complexity) {
                    Complexity.LOW, Complexity.MEDIUM -> "gemini-2.5-flash-lite"
                    Complexity.HIGH -> "gemini-2.5-flash"
                }
                val preferredModel = settingsManager.getGeminiModel()
                    .takeIf { it.isNotBlank() } ?: targetModel
                val lastSuccessful = settingsManager.getLastSuccessfulModel()

                try {
                    withTimeout(timeoutMs) {
                        val start = System.currentTimeMillis()
                        val result = geminiAgent.chatInference(
                            prompt = prompt,
                            preferredModel = preferredModel,
                            lastSuccessfulModel = lastSuccessful,
                            onSuccessModel = { settingsManager.saveLastSuccessfulModel(it) }
                        )
                        val elapsed = "%.1fs".format((System.currentTimeMillis() - start) / 1000f)
                        result.text to "${result.modelName} ($elapsed)"
                    }
                } catch (e: Exception) {
                    Log.e("ModelRouter", "Inference failed for $preferredModel", e)
                    "AI response timed out or failed. Please try again." to "Timeout"
                }
            }
        }
    }

    /**
     * Run a multi-turn inference using conversation history.
     * Used by OrchestratorAgent for Copilot sessions with SessionMemory.
     * Falls back to single-turn if history is empty.
     */
    suspend fun runWithHistory(
        history: List<AgentMessage>,
        complexity: Complexity = Complexity.LOW,
        timeoutMs: Long = 60_000L,
        systemInstruction: String? = null
    ): Pair<String, String> {
        if (history.isEmpty()) {
            return "No message to respond to." to "Empty"
        }

        // For LiteRT and template, only send the current enriched prompt —
        // small on-device models can't reason over a multi-turn collapse and
        // end up re-answering whichever question appears first in the string.
        return when (modelPicker.getBestModel()) {
            ModelPicker.ModelType.LITERT_LOCAL,
            ModelPicker.ModelType.BASIC_TEMPLATE -> {
                val currentPrompt = history.lastOrNull { it.role == "user" }?.content
                    ?: return "No message to respond to." to "Empty"
                run(currentPrompt, complexity, timeoutMs)
            }

            ModelPicker.ModelType.GEMINI_CLOUD -> {
                val targetModel = when (complexity) {
                    Complexity.LOW, Complexity.MEDIUM -> "gemini-2.5-flash-lite"
                    Complexity.HIGH -> "gemini-2.5-flash"
                }
                val preferredModel = settingsManager.getGeminiModel()
                    .takeIf { it.isNotBlank() } ?: targetModel
                val lastSuccessful = settingsManager.getLastSuccessfulModel()

                try {
                    withTimeout(timeoutMs) {
                        val start = System.currentTimeMillis()
                        val result = geminiAgent.chatMultiTurn(
                            history = history,
                            preferredModel = preferredModel,
                            lastSuccessfulModel = lastSuccessful,
                            onSuccessModel = { settingsManager.saveLastSuccessfulModel(it) },
                            systemInstruction = systemInstruction
                        )
                        val elapsed = "%.1fs".format((System.currentTimeMillis() - start) / 1000f)
                        result.text to "${result.modelName} ($elapsed)"
                    }
                } catch (e: Exception) {
                    Log.e("ModelRouter", "Multi-turn inference failed", e)
                    "AI response timed out. Please try again." to "Timeout"
                }
            }
        }
    }

    private fun cleanLiteRTResponse(response: String): String =
        response
            .replace(Regex("<think>.*?</think>", RegexOption.DOT_MATCHES_ALL), "")
            .replace("<think>", "")
            .replace("</think>", "")
            .trim()
}
