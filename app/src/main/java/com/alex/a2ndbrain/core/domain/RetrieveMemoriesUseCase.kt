package com.alex.a2ndbrain.core.domain

class RetrieveMemoriesUseCase(private val memoryRetriever: MemoryRetriever) {
    suspend operator fun invoke(query: String = "", limit: Int = 50): List<Memory> =
        memoryRetriever.retrieve(query, limit)
}
