package com.alex.a2ndbrain.core.domain

import com.alex.a2ndbrain.core.agents.DynamicContextFlags
import com.alex.a2ndbrain.core.agents.OrchestratorAgent
import com.alex.a2ndbrain.core.agents.SessionMemory

class ChatWithCopilotUseCase(private val orchestrator: OrchestratorAgent) {
    suspend operator fun invoke(
        message: String,
        sessionMemory: SessionMemory,
        flags: DynamicContextFlags = DynamicContextFlags.fromMessage(message)
    ): Pair<String, String> = orchestrator.chat(
        userMessage = message,
        sessionMemory = sessionMemory,
        dynamicContextFlags = flags
    )
}
