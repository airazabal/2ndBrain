package com.alex.a2ndbrain.core.senseofday

import com.alex.a2ndbrain.core.mood.MoodLogEntity

data class SenseOfDayScore(
    val score: Int,
    val stepsProgress: Float,
    val sleepProgress: Float,
    val exerciseProgress: Float,
    val focusProgress: Float,
    val moodProgress: Float,
    val moodLogged: Boolean,
    val contextText: String
)

class ComputeSenseOfDayUseCase {

    operator fun invoke(
        steps: Long,
        sleepMinutes: Int,
        exerciseMinutes: Int,
        focusMinutes: Int,
        todayMood: MoodLogEntity?,
        stepsGoal: Int,
        sleepGoalHours: Float,
        exerciseGoalMinutes: Int,
        digitalFocusBaselineMinutes: Int
    ): SenseOfDayScore {
        val stepsProgress = (steps.toFloat() / stepsGoal).coerceIn(0f, 1f)
        val sleepProgress = (sleepMinutes / 60f / sleepGoalHours).coerceIn(0f, 1f)
        val exerciseProgress = (exerciseMinutes.toFloat() / exerciseGoalMinutes).coerceIn(0f, 1f)
        val focusProgress = (focusMinutes.toFloat() / digitalFocusBaselineMinutes).coerceIn(0f, 1f)

        val rawMoodProgress = todayMood?.let { ((it.mood + it.energy) / 2f - 1f) / 4f } ?: -1f
        val moodLogged = rawMoodProgress >= 0f
        val moodProgress = if (moodLogged) rawMoodProgress else 0f

        val denominator = if (moodLogged) 5f else 4f
        val score = ((stepsProgress + sleepProgress + exerciseProgress + focusProgress + moodProgress) / denominator * 100f)
            .toInt().coerceIn(0, 100)

        val contextText = when {
            score >= 85 -> "Outstanding balance across all pillars today."
            score >= 70 -> "Great progress. Keep the pillars balanced."
            !moodLogged && score >= 50 -> "Log your mood to complete today's score."
            stepsProgress < 0.3f && exerciseProgress < 0.3f -> "Movement is low. A short walk could flip your score."
            sleepProgress < 0.5f -> "Poor sleep is dragging the score. Prioritize rest tonight."
            focusProgress < 0.3f -> "Focus time is low. Try a 25-min deep-work block."
            moodLogged && rawMoodProgress < 0.4f -> "Mood is low — consider a break or short walk."
            score < 20 -> "Calibrating... log some activity to update your Sense of Day."
            else -> "Steady progress. Keep an eye on your lowest pillar."
        }

        return SenseOfDayScore(
            score = score,
            stepsProgress = stepsProgress,
            sleepProgress = sleepProgress,
            exerciseProgress = exerciseProgress,
            focusProgress = focusProgress,
            moodProgress = moodProgress,
            moodLogged = moodLogged,
            contextText = contextText
        )
    }
}
