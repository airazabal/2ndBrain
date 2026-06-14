package com.alex.a2ndbrain.core.senseofday

// pillarIdx: 0=Steps, 1=Sleep, 2=Exercise, 3=Focus, 4=Mood, -1=Overall Score
data class CorrelationData(
    val chipLabel: String,
    val chartTitle: String,
    val aLabel: String,
    val aPillarIdx: Int,
    val aValues: List<Float>,
    val bLabel: String,
    val bPillarIdx: Int,
    val bValues: List<Float>,
    val r: Float,
    val insight: String
)
