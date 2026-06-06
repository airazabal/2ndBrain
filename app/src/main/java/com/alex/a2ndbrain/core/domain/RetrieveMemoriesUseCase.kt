package com.alex.a2ndbrain.core.domain

import com.alex.a2ndbrain.core.agents.MemoryAgent
import com.alex.a2ndbrain.core.memory.MemoryEntity

class RetrieveMemoriesUseCase(private val memoryAgent: MemoryAgent) {
    suspend operator fun invoke(query: String = "", limit: Int = 50): List<MemoryEntity> =
        memoryAgent.retrieve(query, limit)
}
