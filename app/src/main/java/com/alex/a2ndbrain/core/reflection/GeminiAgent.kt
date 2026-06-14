package com.alex.a2ndbrain.core.reflection

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.ai.client.generativeai.type.RequestOptions
import android.util.Log

import com.alex.a2ndbrain.core.capture.SettingsRepository
import com.alex.a2ndbrain.core.agents.AgentMessage

class GeminiAgent(private val settingsManager: SettingsRepository) {

    // Instance-scoped — each GeminiAgent instance tracks its own last-good model.
    // Previously a companion object var, which caused silent model downgrade to persist
    // across the entire app lifetime after any transient failure.
    private var cachedModel: Pair<String, String>? = null

    companion object {
        var fallbackModels = listOf(
            "gemini-2.5-flash-lite" to "v1beta",
            "gemini-2.5-flash" to "v1beta",
            "gemini-1.5-flash" to "v1beta"
        )
    }

    private fun createModel(name: String, version: String, systemInstruction: String? = null) =
        GenerativeModel(
            modelName = name.trim(),
            apiKey = settingsManager.getGeminiApiKey().trim(),
            systemInstruction = systemInstruction?.let { content { text(it) } },
            requestOptions = RequestOptions(apiVersion = version)
        )

    suspend fun summarizeMemories(
        memoriesText: String, 
        preferredModel: String? = null, 
        lastSuccessfulModel: String? = null,
        onSuccessModel: ((String) -> Unit)? = null,
        isMorningBriefing: Boolean = false
    ): SummaryResult = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.getGeminiApiKey()
        if (apiKey.isBlank()) {
            return@withContext SummaryResult(
                "❌ Gemini API Key is missing.\n\nPlease go to Settings (gear icon in navigation rail) and enter your Gemini API Key.",
                "N/A"
            )
        }
        if (memoriesText.isBlank()) return@withContext SummaryResult("No significant memories to process yet.", "N/A")
        
        val modeTask = if (isMorningBriefing) {
            """
            MORNING BRIEFING MODE:
            - Focus on the day ahead. Look at Calendar events captured so far today.
            - Review yesterday's unfinished business or recurring tasks from Todoist.
            - Set the 'Focus of the Day'. Suggest which tasks are most realistic given the meeting schedule.
            - Warn about potential conflicts (e.g., 'You have 3 meetings back-to-back, maybe don't try to finish that deep-work task today').
            """.trimIndent()
        } else {
            """
            EVENING REFLECTION MODE:
            - Analyze how the day went. Compare intentions (Todoist) with reality (Digital Time usage).
            - Identify 'Distraction Gaps': e.g., you spent 2 hours on social media while your Todoist was full of high-priority items.
            - High-five productivity wins: Highlight when you stayed focused or hit key meetings.
            - Correlate apps: Did an email trigger a calendar event? Did a message lead to a new task?
            """.trimIndent()
        }

        val prompt = """
            You are a 'Second Brain' AI assistant. Analyze the following notification, clipboard, and usage data and provide a concise, personal, and highly intelligent synthesis.
            
            $modeTask
            
            ADVISORY TONE:
            Don't just list what happened. Provide actionable advice:
            - Suggest focus areas based on what wasn't finished or what seems urgent.
            - Flag potential 'time crunches' or 'energy drains'.
            - Spot patterns across People and Projects (mention them by name).

            FORMAT:
            Friendly, professional, and insightful. Use clear headings. Keep it brief.

            RAW DATA (with timestamps):
            $memoriesText
        """.trimIndent()

        // Prioritize preferred model, then persistent lastSuccessfulModel, then in-memory cachedModel
        val attempts = mutableListOf<Pair<String, String>>()
        if (preferredModel != null && preferredModel.isNotBlank()) {
            val pref = preferredModel.trim()
            val version = "v1beta" // v1beta is safest for most
            attempts.add(pref to version)
        } else if (lastSuccessfulModel != null && lastSuccessfulModel.isNotBlank()) {
            attempts.add(lastSuccessfulModel.trim() to "v1beta")
        } else if (cachedModel != null) {
            attempts.add(cachedModel!!)
        }
        attempts.addAll(fallbackModels.filter { it.first != preferredModel && it.first != lastSuccessfulModel && it.first != cachedModel?.first })
        
        val keySnippet = if (apiKey.length > 6) "${apiKey.take(4)}...${apiKey.takeLast(2)}" else "Invalid/Short"
        val errorLog = mutableListOf<String>()
        val debugInfo = "Debug Key: $keySnippet"

        for ((name, version) in attempts) {
            try {
                Log.d("GeminiAgent", "Attempting $name with $version")
                val model = createModel(name, version)
                val response = model.generateContent(prompt)
                val result = response.text
                if (!result.isNullOrBlank()) {
                    cachedModel = name to version
                    onSuccessModel?.invoke(name)
                    return@withContext SummaryResult(result, name)
                }
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

    suspend fun chatInference(
        prompt: String,
        preferredModel: String? = null,
        lastSuccessfulModel: String? = null,
        onSuccessModel: ((String) -> Unit)? = null,
        systemInstruction: String? = null
    ): SummaryResult = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.getGeminiApiKey()
        if (apiKey.isBlank()) {
            return@withContext SummaryResult(
                "❌ Gemini API Key is missing.\n\nPlease go to Settings (gear icon in navigation rail) and enter your Gemini API Key.",
                "N/A"
            )
        }
        if (prompt.isBlank()) return@withContext SummaryResult("Prompt cannot be empty.", "N/A")
        
        val attempts = mutableListOf<Pair<String, String>>()
        if (preferredModel != null && preferredModel.isNotBlank()) {
            val pref = preferredModel.trim()
            attempts.add(pref to "v1beta")
        } else if (lastSuccessfulModel != null && lastSuccessfulModel.isNotBlank()) {
            attempts.add(lastSuccessfulModel.trim() to "v1beta")
        } else if (cachedModel != null) {
            attempts.add(cachedModel!!)
        }
        attempts.addAll(fallbackModels.filter { it.first != preferredModel && it.first != lastSuccessfulModel && it.first != cachedModel?.first })
        
        for ((name, version) in attempts) {
            try {
                Log.d("GeminiAgent", "Chat attempt $name with $version")
                val model = createModel(name, version, systemInstruction)
                val response = model.generateContent(prompt)
                val result = response.text
                if (!result.isNullOrBlank()) {
                    cachedModel = name to version
                    onSuccessModel?.invoke(name)
                    return@withContext SummaryResult(result, name)
                }
            } catch (e: Exception) {
                Log.e("GeminiAgent", "Chat failed $name/$version: ${e.message}")
            }
        }
        
        SummaryResult("All Gemini models failed. Please check network/billing.", "Error")
    }

    /**
     * Multi-turn chat using Gemini's startChat() API.
     * All elements except the last are passed as history; the last user message
     * is sent via chat.sendMessage() so each turn gets its own proper response.
     */
    suspend fun chatMultiTurn(
        history: List<AgentMessage>,
        preferredModel: String? = null,
        lastSuccessfulModel: String? = null,
        onSuccessModel: ((String) -> Unit)? = null,
        systemInstruction: String? = null
    ): SummaryResult = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.getGeminiApiKey()
        if (apiKey.isBlank()) {
            return@withContext SummaryResult(
                "❌ Gemini API Key is missing. Please go to Settings and enter your Gemini API Key.",
                "N/A"
            )
        }
        if (history.isEmpty()) return@withContext SummaryResult("No message to respond to.", "N/A")

        val lastMessage = history.last()
        // Build startChat() history from all turns before the last user message.
        // Must alternate user/model; drop any trailing model turn to stay valid.
        val priorTurns = history.dropLast(1).let { turns ->
            if (turns.lastOrNull()?.role == "model") turns else turns.dropLastWhile { it.role != "model" }
        }
        val chatHistory = priorTurns.map { msg ->
            content(role = msg.role) { text(msg.content) }
        }

        val attempts = mutableListOf<Pair<String, String>>()
        if (!preferredModel.isNullOrBlank()) attempts.add(preferredModel.trim() to "v1beta")
        else if (!lastSuccessfulModel.isNullOrBlank()) attempts.add(lastSuccessfulModel.trim() to "v1beta")
        else if (cachedModel != null) attempts.add(cachedModel!!)
        attempts.addAll(fallbackModels.filter {
            it.first != preferredModel && it.first != lastSuccessfulModel && it.first != cachedModel?.first
        })

        for ((name, version) in attempts) {
            try {
                Log.d("GeminiAgent", "Multi-turn attempt $name prior=${priorTurns.size} turns")
                val model = createModel(name, version, systemInstruction)
                val chat = model.startChat(history = chatHistory)
                val response = chat.sendMessage(lastMessage.content)
                val result = response.text
                if (!result.isNullOrBlank()) {
                    cachedModel = name to version
                    onSuccessModel?.invoke(name)
                    return@withContext SummaryResult(result, name)
                }
            } catch (e: Exception) {
                Log.e("GeminiAgent", "Multi-turn failed $name: ${e.message}")
            }
        }
        SummaryResult("All Gemini models failed. Please check network/billing.", "Error")
    }
}

data class SummaryResult(val text: String, val modelName: String)
