package com.alex.a2ndbrain.core.domain

import com.alex.a2ndbrain.core.agents.DynamicContextFlags
import com.alex.a2ndbrain.core.agents.SessionMemory

interface CopilotService {
    suspend fun chat(
        message: String,
        sessionMemory: SessionMemory,
        flags: DynamicContextFlags
    ): Pair<String, String>
}
