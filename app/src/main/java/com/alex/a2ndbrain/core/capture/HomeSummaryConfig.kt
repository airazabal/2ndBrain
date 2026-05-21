package com.alex.a2ndbrain.core.capture

data class HomeSummaryConfig(
    val defaultMode: HomeDefaultMode = HomeDefaultMode.SUMMARY_ONLY,
    val showHabitPill: Boolean = true,
    val showNextEventPill: Boolean = true,
    val showStepsPill: Boolean = true,
    val showSleepMeditationPill: Boolean = true,
    val showAlerts: Boolean = true,
    val showSenseOfDayText: Boolean = true
)

enum class HomeDefaultMode {
    SUMMARY_ONLY,
    REMEMBER_LAST,
    ALWAYS_EXPANDED
}
