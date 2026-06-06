package com.alex.a2ndbrain.core.domain

import com.alex.a2ndbrain.core.reflection.ReflectionManager

class GenerateWeeklyInsightUseCase(private val reflectionManager: ReflectionManager) {
    suspend operator fun invoke(): String? = reflectionManager.generateWeeklyCorrelation()
}
