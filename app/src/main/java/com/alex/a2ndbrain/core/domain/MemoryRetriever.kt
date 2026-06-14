package com.alex.a2ndbrain.core.domain

interface MemoryRetriever {
    suspend fun retrieve(query: String = "", limit: Int = 50): List<Memory>
}
