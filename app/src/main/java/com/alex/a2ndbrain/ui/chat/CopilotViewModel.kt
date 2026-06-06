package com.alex.a2ndbrain.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alex.a2ndbrain.ChatMessage
import com.alex.a2ndbrain.core.agents.SessionMemory
import com.alex.a2ndbrain.core.domain.ChatWithCopilotUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * CopilotViewModel — refactored to use OrchestratorAgent + SessionMemory.
 *
 * Previously: rebuilt full data context from DAOs on every message, no memory
 *             across turns, all fetching logic duplicated from ReflectionManager.
 *
 * Now: delegates all context-building and inference to OrchestratorAgent.
 *      SessionMemory maintains a rolling conversation window so multi-turn
 *      exchanges like "What did I eat yesterday?" → "Compare to last week" work.
 *
 * CLAUDE.md data flow: UI receives plain data only — no DAO/Manager refs here.
 */
class CopilotViewModel(
    private val chatWithCopilot: ChatWithCopilotUseCase,
    private val sessionMemory: SessionMemory
) : ViewModel() {

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(
        listOf(
            ChatMessage(
                text = "Hello! I am your 2ndBrain Co-Pilot. Ask me anything about your captured " +
                        "notifications, clipboard history, daily digital usage, or smartwatch health stats!",
                isUser = false
            )
        )
    )
    val chatMessages = _chatMessages.asStateFlow()

    private val _chatIsThinking = MutableStateFlow(false)
    val chatIsThinking = _chatIsThinking.asStateFlow()

    fun sendChatMessage(message: String) {
        if (message.isBlank()) return

        // Add user message to display list immediately (optimistic)
        _chatMessages.value = _chatMessages.value + ChatMessage(text = message, isUser = true)
        _chatIsThinking.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val (replyText, modelUsed) = chatWithCopilot(message, sessionMemory)
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    text = replyText,
                    isUser = false,
                    modelUsed = modelUsed
                )
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    text = "Sorry, I ran into an error: ${e.message}",
                    isUser = false,
                    modelUsed = "Error"
                )
            } finally {
                _chatIsThinking.value = false
            }
        }
    }

    /** Reset the conversation — clears session memory and display history. */
    fun clearSession() {
        sessionMemory.clear()
        _chatMessages.value = listOf(
            ChatMessage(
                text = "Session cleared. What would you like to explore?",
                isUser = false
            )
        )
    }

    fun sessionTokenEstimate(): Int = sessionMemory.estimatedTokens()
}
