package com.alex.a2ndbrain.core.senseofday

import kotlin.math.sqrt

object CorrelationEngine {

    fun pearson(xs: List<Float>, ys: List<Float>): Float {
        val n = xs.size
        if (n < 3) return 0f
        val mx = xs.average().toFloat()
        val my = ys.average().toFloat()
        val num = xs.zip(ys).sumOf { (x, y) -> ((x - mx) * (y - my)).toDouble() }.toFloat()
        val dx = sqrt(xs.sumOf { ((it - mx) * (it - mx)).toDouble() }.toFloat())
        val dy = sqrt(ys.sumOf { ((it - my) * (it - my)).toDouble() }.toFloat())
        return if (dx == 0f || dy == 0f) 0f else (num / (dx * dy)).coerceIn(-1f, 1f)
    }

    fun insight(r: Float, a: String, b: String): String = when {
        r >= 0.65f  -> "Strong link — high $a reliably lifts your $b"
        r >= 0.35f  -> "Moderate link — $a and $b tend to rise together"
        r <= -0.65f -> "Inverse — high $a consistently lowers $b"
        r <= -0.35f -> "Slight inverse — $a and $b pull in opposite directions"
        else        -> "No clear pattern yet — more days will sharpen this"
    }

    fun buildCorrelations(sorted: List<SenseOfDaySnapshot>): List<CorrelationData> {
        if (sorted.size < 3) return emptyList()
        val result = mutableListOf<CorrelationData>()

        val sleepVals = sorted.dropLast(1).map { it.sleepProgress }
        val scoreVals = sorted.drop(1).map { it.score / 100f }
        val rSS = pearson(sleepVals, scoreVals)
        result += CorrelationData(
            chipLabel = "Sleep / Score", chartTitle = "Sleep → Next-day Score",
            aLabel = "Sleep", aPillarIdx = 1, aValues = sorted.map { it.sleepProgress },
            bLabel = "Score", bPillarIdx = -1, bValues = sorted.map { it.score / 100f },
            r = rSS, insight = insight(rSS, "sleep", "next-day score")
        )

        val sfDays = sorted.filter { it.stepsProgress > 0f && it.focusProgress > 0f }
        if (sfDays.size >= 3) {
            val rSF = pearson(sfDays.map { it.stepsProgress }, sfDays.map { it.focusProgress })
            result += CorrelationData(
                chipLabel = "Steps / Focus", chartTitle = "Steps × Focus",
                aLabel = "Steps", aPillarIdx = 0, aValues = sorted.map { it.stepsProgress },
                bLabel = "Focus", bPillarIdx = 3, bValues = sorted.map { it.focusProgress },
                r = rSF, insight = insight(rSF, "steps", "focus time")
            )
        }

        val emDays = sorted.filter { it.exerciseProgress > 0f && it.moodProgress >= 0f }
        if (emDays.size >= 3) {
            val rEM = pearson(emDays.map { it.exerciseProgress }, emDays.map { it.moodProgress })
            result += CorrelationData(
                chipLabel = "Exercise / Mood", chartTitle = "Exercise × Mood",
                aLabel = "Exercise", aPillarIdx = 2, aValues = sorted.map { it.exerciseProgress },
                bLabel = "Mood", bPillarIdx = 4,
                bValues = sorted.map { if (it.moodProgress >= 0f) it.moodProgress else 0f },
                r = rEM, insight = insight(rEM, "exercise", "mood")
            )
        }

        return result
    }
}
