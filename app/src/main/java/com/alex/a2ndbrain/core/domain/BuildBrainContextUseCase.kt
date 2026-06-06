package com.alex.a2ndbrain.core.domain

import com.alex.a2ndbrain.core.agents.BrainContext
import com.alex.a2ndbrain.core.agents.DynamicContextFlags
import com.alex.a2ndbrain.core.agents.OrchestratorAgent

class BuildBrainContextUseCase(private val orchestrator: OrchestratorAgent) {
    suspend operator fun invoke(
        query: String = "",
        flags: DynamicContextFlags? = null
    ): BrainContext = orchestrator.buildContext(query, flags)
}
