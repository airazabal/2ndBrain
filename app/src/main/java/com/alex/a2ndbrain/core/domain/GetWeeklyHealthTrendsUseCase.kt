package com.alex.a2ndbrain.core.domain

import com.alex.a2ndbrain.core.health.HealthMetrics
import com.alex.a2ndbrain.core.health.HealthRepository

class GetWeeklyHealthTrendsUseCase(private val healthRepository: HealthRepository) {
    suspend operator fun invoke(): List<Pair<String, HealthMetrics>> =
        healthRepository.getWeeklyTrends()
}
