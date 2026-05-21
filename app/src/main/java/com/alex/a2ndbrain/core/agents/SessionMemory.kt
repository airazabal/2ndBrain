package com.alex.a2ndbrain.core.agents

/**
 * SessionMemory — rolling conversation history for multi-turn Copilot sessions.
 *
 * Maintains a capped window of (role, content) pairs that are passed to the
 * Gemini SDK's startChat() on each turn. The model sees the full conversation
 * history, enabling real multi-turn interactions like:
 *   "What did I eat yesterday?" → "Compare that to last week"
 *
 * Window is capped at [maxTokens] (estimated at ~4 chars/token) to avoid
 * ballooning costs or hitting model context limits on the free tier.
 *
 * Usage:
 *   val memory = SessionMemory()
 *   memory.add(AgentMessage("user", userPromptWithContext))
 *   memory.add(AgentMessage("model", modelReply))
 *   val history = memory.getHistory()  // pass to Gemini startChat()
 */
class SessionMemory(private val maxTokens: Int = 4000) {

    private val history = ArrayDeque<AgentMessage>()

    fun add(message: AgentMessage) {
        history.addLast(message)
        trimToFit()
    }

    /**
     * Returns the full conversation history as a list.
     * Roles must alternate user/model as required by the Gemini API.
     * The list is already ordered oldest→newest.
     */
    fun getHistory(): List<AgentMessage> = history.toList()

    /**
     * Returns only the user-visible display messages (not the enriched
     * system-context portions injected into the first user turn).
     */
    fun getDisplayMessages(): List<AgentMessage> = history.toList()

    fun isEmpty(): Boolean = history.isEmpty()

    fun clear() = history.clear()

    fun messageCount(): Int = history.size

    /** Rough token estimate: ~4 chars per token, matching OpenAI/Gemini typical ratios. */
    fun estimatedTokens(): Int = history.sumOf { it.content.length / 4 }

    private fun trimToFit() {
        // Always keep at least the last 2 messages (one user, one model)
        // to preserve coherence even if the window is very small.
        while (estimatedTokens() > maxTokens && history.size > 2) {
            history.removeFirst()
        }
    }
}

data class AgentMessage(
    val role: String,   // "user" or "model"
    val content: String
)
