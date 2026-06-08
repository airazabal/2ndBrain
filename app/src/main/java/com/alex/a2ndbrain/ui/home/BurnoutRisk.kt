package com.alex.a2ndbrain.ui.home

enum class BurnoutLevel { LOW, MODERATE, HIGH, CRITICAL }

data class BurnoutRisk(
    val score: Int = 0,
    val level: BurnoutLevel = BurnoutLevel.LOW,
    val sleepScore: Int = 0,
    val workoutScore: Int = 0,
    val digitalScore: Int = 0,
    val meetingScore: Int = 0,
    val drivers: List<String> = emptyList()
)
