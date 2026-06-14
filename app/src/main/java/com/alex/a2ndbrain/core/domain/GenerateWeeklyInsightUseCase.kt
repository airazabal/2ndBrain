package com.alex.a2ndbrain.core.domain

class GenerateWeeklyInsightUseCase(private val reflectionService: ReflectionService) {
    suspend operator fun invoke(): String? = reflectionService.generateWeeklyCorrelation()
}
