package com.alex.a2ndbrain.core.domain

import com.alex.a2ndbrain.core.agents.DynamicContextFlags
import com.alex.a2ndbrain.core.agents.SessionMemory

class ChatWithCopilotUseCase(private val copilot: CopilotService) {
    suspend operator fun invoke(
        message: String,
        sessionMemory: SessionMemory,
        flags: DynamicContextFlags = DynamicContextFlags.fromMessage(message)
    ): Pair<String, String> = copilot.chat(
        message = message,
        sessionMemory = sessionMemory,
        flags = flags
    )
}
