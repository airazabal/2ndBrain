package com.alex.a2ndbrain.core.agents

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ReflectionAgent — pure prompt construction for daily/weekly AI reflections.
 *
 * This class has NO dependencies on DAOs, Managers, or Contexts.
 * It receives a pre-built [BrainContext] from OrchestratorAgent and
 * returns a prompt string ready for ModelRouter.
 *
 * Previously this logic lived embedded in ReflectionManager.generateDailyReflection()
 * mixed together with data fetching, model selection, and DB writes.
 * That class is now a thin scheduler (WorkManager wiring) and persistence layer.
 */
class ReflectionAgent {

    enum class ReflectionType {
        MORNING_BRIEFING,
        EVENING_REFLECTION,
        WEEKLY_CORRELATION;

        companion object {
            /** Infer reflection type from current hour of day. */
            fun forCurrentTime(): ReflectionType {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                return when {
                    hour in 4..11 -> MORNING_BRIEFING
                    else -> EVENING_REFLECTION
                }
            }
        }
    }

    /**
     * Score a reflection draft against quality criteria. Returns null if the draft passes,
     * or a non-empty critique string to append on retry.
     *
     * Checks (in order):
     *   1. Minimum word count — 80 for daily modes, 150 for weekly
     *   2. Weekly correlations must be narrative prose, not bullet lists
     *   3. Data denial — output claims no data when BrainContext has it
     */
    fun critique(output: String, type: ReflectionType, ctx: BrainContext): String? {
        val issues = mutableListOf<String>()

        val wordCount = output.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
        val minWords = if (type == ReflectionType.WEEKLY_CORRELATION) 150 else 80
        if (wordCount < minWords) {
            issues += "Response is too short ($wordCount words, minimum $minWords). Provide a more detailed analysis."
        }

        if (type == ReflectionType.WEEKLY_CORRELATION) {
            val bulletLines = output.lines().count {
                val t = it.trimStart()
                t.startsWith("- ") || t.startsWith("• ") || t.startsWith("* ")
            }
            if (bulletLines > 3) {
                issues += "Weekly correlation must be cohesive narrative prose — rewrite without bullet points."
            }
        }

        val lower = output.lowercase()
        val isDenying = listOf("no data", "i don't have", "no information", "not available", "no records")
            .any { lower.contains(it) }
        if (isDenying && (ctx.memories.isNotEmpty() || ctx.health.isAvailable || ctx.usageStats.isNotEmpty())) {
            issues += "Data was provided in the context but the response claims it's unavailable — reference the data above."
        }

        return if (issues.isEmpty()) null else issues.joinToString(" ")
    }

    /**
     * Build a fully-formed prompt from a BrainContext snapshot.
     * Returns a prompt string — no AI calls are made here.
     */
    fun buildPrompt(type: ReflectionType, ctx: BrainContext): String = buildString {
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val todayStr = dateFormat.format(Date())

        // ── System instruction ────────────────────────────────────────────
        append("You are a 'Second Brain' AI assistant. ")
        append("Analyze the data below and provide a concise, personal, and highly intelligent synthesis.\n\n")

        // ── Mode-specific task ────────────────────────────────────────────
        when (type) {
            ReflectionType.MORNING_BRIEFING -> {
                append("""
                    MORNING BRIEFING MODE:
                    - Focus on the day ahead. Review captured calendar events and reminders.
                    - Review yesterday's unfinished business from Todoist or clipboard.
                    - Suggest which tasks are most realistic given the meeting schedule.
                    - Warn about potential conflicts (e.g., back-to-back meetings with deep-work tasks).
                    
                """.trimIndent())
            }
            ReflectionType.EVENING_REFLECTION -> {
                append("""
                    EVENING REFLECTION MODE:
                    - Analyze how the day went. Compare intentions (Todoist) with reality (screen time).
                    - Identify 'Distraction Gaps': e.g., social media during high-priority task windows.
                    - Celebrate productivity wins: highlight focus wins or completed key meetings.
                    - Correlate apps: did an email trigger a calendar event? Did a message lead to a task?
                    
                """.trimIndent())
            }
            ReflectionType.WEEKLY_CORRELATION -> {
                append("""
                    WEEKLY CORRELATION MODE:
                    You are the user's private cognitive co-pilot.
                    Analyze the multi-dimensional physical, digital, and routine history for the last 7 days.
                    Discover key correlations between physical activity, sleep cycles, habit compliance, and screen distractions.
                    Highlight positive habits and provide specific, actionable cognitive advice.
                    Tone: friendly, premium, concise. Write as a cohesive narrative — no bullet points.
                    
                """.trimIndent())
            }
        }

        // ── Advisory tone ─────────────────────────────────────────────────
        if (type != ReflectionType.WEEKLY_CORRELATION) {
            append("""
                ADVISORY TONE:
                Don't just list what happened. Provide actionable advice:
                - Suggest focus areas based on what wasn't finished or what seems urgent.
                - Flag potential time crunches or energy drains.
                - Spot patterns across people and projects (mention them by name).
                FORMAT: Friendly, professional, and insightful. Use clear headings. Keep it brief.
                
            """.trimIndent())
        }

        // ── Memory timeline ───────────────────────────────────────────────
        if (ctx.memories.isNotEmpty()) {
            append("RAW CAPTURED DATA (with timestamps):\n")
            ctx.memories
                .sortedBy { it.timestamp }
                .take(if (type == ReflectionType.WEEKLY_CORRELATION) 50 else 80)
                .forEach { mem ->
                    val time = timeFormat.format(Date(mem.timestamp))
                    val source = mem.packageName?.substringAfterLast(".") ?: mem.source
                    append("- [$time][$source] ${mem.title ?: ""}: ${mem.content.take(150)}\n")
                }
            append("\n")
        }

        // ── Usage report ──────────────────────────────────────────────────
        if (ctx.usageStats.isNotEmpty()) {
            val byDevice = ctx.usageStats.groupBy { it.deviceName }
            append("DIGITAL USAGE (today's totals):\n")
            byDevice.forEach { (device, stats) ->
                append("Device: $device\n")
                stats.sortedByDescending { it.totalTimeVisibleMs }.take(5).forEach { stat ->
                    val mins = stat.totalTimeVisibleMs / 1000 / 60
                    append("  - ${stat.packageName.substringAfterLast(".")}: ${mins}m\n")
                }
            }
            append("\n")
        }

        // ── Health metrics ────────────────────────────────────────────────
        if (ctx.health.isAvailable) {
            val m = ctx.health.metrics
            when (type) {
                ReflectionType.WEEKLY_CORRELATION -> {
                    if (ctx.health.weeklyTrends.isNotEmpty()) {
                        append("PAST 7 DAYS PHYSICAL WELLNESS:\n")
                        ctx.health.weeklyTrends.forEach { (date, metrics) ->
                            append("- $date: ${metrics.steps} steps, sleep ${metrics.sleepMinutes / 60}h ${metrics.sleepMinutes % 60}m, HR avg ${metrics.avgHeartRate} BPM\n")
                        }
                    }
                }
                else -> {
                    append("PHYSICAL WELLNESS:\n")
                    append("- Steps today: ${m.steps}\n")
                    append("- Sleep last night: ${m.sleepMinutes / 60}h ${m.sleepMinutes % 60}m\n")
                    append("- Heart rate: ${m.minHeartRate}–${m.maxHeartRate} BPM (avg ${m.avgHeartRate})\n")
                }
            }
            append("\n")
        }

        // ── Meditation ────────────────────────────────────────────────────
        if (ctx.meditation.sessions.isNotEmpty()) {
            val streaks = ctx.meditation.streaks
            when (type) {
                ReflectionType.WEEKLY_CORRELATION -> {
                    append("PAST 7 DAYS MEDITATION (Zendence):\n")
                    append("- Total sessions: ${ctx.meditation.sessions.size}\n")
                    append("- Current week streak: ${streaks.currentWeekStreak} days\n")
                    append("- Max overall streak: ${streaks.maxOverallStreak} days\n")
                    append("- Total duration: ${ctx.meditation.sessions.sumOf { it.durationMinutes }}m\n")
                }
                else -> {
                    val todaySessions = ctx.meditation.sessions.filter {
                        dateFormat.format(Date(it.timestamp)) == todayStr
                    }
                    if (todaySessions.isNotEmpty()) {
                        val totalMins = todaySessions.sumOf { it.durationMinutes }
                        val insights = todaySessions.mapNotNull { it.insight }
                            .joinToString("; ") { "\"$it\"" }
                        append("MEDITATION (Zendence):\n")
                        append("- Meditated: ${totalMins}m across ${todaySessions.size} session(s)\n")
                        if (insights.isNotBlank()) append("- Insights: $insights\n")
                    }
                }
            }
            append("\n")
        }
    }
}
