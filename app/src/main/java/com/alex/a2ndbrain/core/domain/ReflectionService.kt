package com.alex.a2ndbrain.core.domain

interface ReflectionService {
    suspend fun generateWeeklyCorrelation(): String?
}
