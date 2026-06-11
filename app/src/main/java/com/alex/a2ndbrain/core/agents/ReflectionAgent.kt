package com.alex.a2ndbrain.core.agents

import android.util.Log
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
        WEEKLY_CORRELATION,
        TOMORROW_FORECAST;

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
        Log.d("ReflectionAgent", "buildPrompt: longTermMemories=${ctx.longTermMemories.size}")
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val now = Calendar.getInstance()
        val todayStr = dateFormat.format(now.time)
        val greeting = when (type) {
            ReflectionType.MORNING_BRIEFING -> "Good morning"
            ReflectionType.EVENING_REFLECTION -> "Good evening"
            ReflectionType.WEEKLY_CORRELATION -> "Good morning"
            ReflectionType.TOMORROW_FORECAST -> "Good evening"
        }

        // ── System instruction ────────────────────────────────────────────
        append("You are a 'Second Brain' AI assistant. ")
        append("Analyze the data below and provide a concise, personal, and highly intelligent synthesis.\n\n")
        append("Always open with \"$greeting, Alex\" (never use a different time-of-day greeting).\n\n")

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
            ReflectionType.TOMORROW_FORECAST -> {
                append("""
                    TOMORROW FORECAST MODE:
                    You are looking ahead. Based on today's data and tomorrow's schedule:
                    1. READINESS CHECK — given today's pillar scores (steps/sleep/exercise/focus) and habit compliance, rate readiness for tomorrow (High / Medium / Low) and explain why in one sentence.
                    2. RISKS — identify 1-2 specific risks for tomorrow (e.g., "back-to-back meetings with low sleep will hurt focus", "exercise streak at risk").
                    3. PREP ACTIONS — give exactly 3 specific actions to take tonight or tomorrow morning.
                    Format: use the three numbered sections above. Keep each section tight — 2-3 sentences max.

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

        // ── Drift alerts ──────────────────────────────────────────────────
        if (type in listOf(ReflectionType.EVENING_REFLECTION, ReflectionType.TOMORROW_FORECAST, ReflectionType.WEEKLY_CORRELATION)) {
            val drifted = ctx.drift.driftedPillars()
            if (drifted.isNotEmpty()) {
                append("DRIFT ALERTS (>20% below 4-week rolling average):\n")
                drifted.forEach { (pillar, drop) ->
                    append("- $pillar is ${drop}% below baseline\n")
                }
                append("→ Acknowledge these regressions and suggest specific recovery steps.\n\n")
            }
        }

        // ── Tomorrow's schedule ───────────────────────────────────────────
        if (ctx.tomorrowEvents.isNotEmpty()) {
            append("TOMORROW'S SCHEDULE:\n")
            ctx.tomorrowEvents.sortedBy { it.minutesFromMidnight }.forEach { event ->
                append("- ${event.time}  ${event.title} (${event.appName})\n")
            }
            append("\n")
        }

        // ── Long-term memory patterns ────────────────────────────────────
        if (ctx.longTermMemories.isNotEmpty()) {
            append("LONG-TERM PATTERNS (from your history — you MUST reference at least one of these in your response under a '## Patterns' heading):\n")
            ctx.longTermMemories.forEach {
                append("- ${it.summary} (importance: ${"%.0f".format(it.importanceScore * 100)}%)\n")
            }
            append("\n")
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

        // ── Exercise ──────────────────────────────────────────────────────
        if (ctx.exercise.recentSessions.isNotEmpty()) {
            when (type) {
                ReflectionType.WEEKLY_CORRELATION -> {
                    val sessions = ctx.exercise.recentSessions
                    val totalMins = sessions.sumOf { it.durationMinutes }
                    append("PAST 7 DAYS EXERCISE:\n")
                    append("- Sessions: ${sessions.size}, total: ${totalMins / 60}h ${totalMins % 60}m\n")
                    sessions.forEach { s ->
                        val exType = runCatching {
                            com.alex.a2ndbrain.core.exercise.ExerciseType.valueOf(s.type).displayName
                        }.getOrDefault(s.type)
                        val dur = if (s.durationMinutes >= 60) "${s.durationMinutes / 60}h ${s.durationMinutes % 60}m"
                                  else "${s.durationMinutes}m"
                        val notes = if (s.notes.isNotBlank()) " — ${s.notes}" else ""
                        append("- ${s.date}: $exType $dur$notes\n")
                    }
                }
                else -> {
                    val todaySessions = ctx.exercise.recentSessions.filter { it.date == todayStr }
                    if (todaySessions.isNotEmpty()) {
                        val totalMins = todaySessions.sumOf { it.durationMinutes }
                        append("EXERCISE TODAY:\n")
                        todaySessions.forEach { s ->
                            val exType = runCatching {
                                com.alex.a2ndbrain.core.exercise.ExerciseType.valueOf(s.type).displayName
                            }.getOrDefault(s.type)
                            val dur = if (s.durationMinutes >= 60) "${s.durationMinutes / 60}h ${s.durationMinutes % 60}m"
                                      else "${s.durationMinutes}m"
                            val notes = if (s.notes.isNotBlank()) " — ${s.notes}" else ""
                            append("- $exType $dur$notes\n")
                        }
                        append("- Total: ${totalMins / 60}h ${totalMins % 60}m\n")
                    } else {
                        val recentSession = ctx.exercise.recentSessions.firstOrNull()
                        if (recentSession != null) {
                            val exType = runCatching {
                                com.alex.a2ndbrain.core.exercise.ExerciseType.valueOf(recentSession.type).displayName
                            }.getOrDefault(recentSession.type)
                            append("RECENT EXERCISE:\n")
                            append("- Last session: ${recentSession.date} $exType ${recentSession.durationMinutes}m\n")
                        }
                    }
                }
            }
            append("\n")
        }

        // ── Mood & Energy ────────────────────────────────────────────────
        if (ctx.mood.todayLogs.isNotEmpty() || (type == ReflectionType.WEEKLY_CORRELATION && ctx.mood.recentLogs.isNotEmpty())) {
            when (type) {
                ReflectionType.WEEKLY_CORRELATION -> {
                    val logs = ctx.mood.recentLogs
                    if (logs.isNotEmpty()) {
                        val avgMood = logs.map { it.mood }.average()
                        val avgEnergy = logs.map { it.energy }.average()
                        append("PAST 7 DAYS MOOD & ENERGY:\n")
                        append("- Average mood: ${"%.1f".format(avgMood)}/5, average energy: ${"%.1f".format(avgEnergy)}/5\n")
                        logs.take(7).forEach { log ->
                            val note = if (log.note.isNotBlank()) " — \"${log.note}\"" else ""
                            append("- ${log.date}: mood ${log.mood}/5, energy ${log.energy}/5$note\n")
                        }
                    }
                }
                else -> {
                    val latest = ctx.mood.todayLogs.firstOrNull()
                    if (latest != null) {
                        append("MOOD & ENERGY TODAY:\n")
                        append("- Mood: ${latest.mood}/5, Energy: ${latest.energy}/5\n")
                        if (latest.note.isNotBlank()) append("- Note: \"${latest.note}\"\n")
                    }
                }
            }
            append("\n")
        }

        // ── Habits ────────────────────────────────────────────────────────
        if (ctx.habits.todayHabits.isNotEmpty() || (type == ReflectionType.WEEKLY_CORRELATION && ctx.habits.recentCompletions.isNotEmpty())) {
            when (type) {
                ReflectionType.WEEKLY_CORRELATION -> {
                    val completions = ctx.habits.recentCompletions
                    val habitNames = ctx.habits.todayHabits.associateBy({ it.id }, { "${it.emoji} ${it.name}" })
                    val byHabit = completions.groupBy { it.habitId }
                    if (ctx.habits.todayHabits.isNotEmpty()) {
                        append("PAST 7 DAYS HABITS:\n")
                        ctx.habits.todayHabits.forEach { habit ->
                            val count = byHabit[habit.id]?.size ?: 0
                            append("- ${habit.emoji} ${habit.name}: $count/7 days completed\n")
                        }
                    }
                }
                else -> {
                    val completed = ctx.habits.completedTodayIds
                    val total = ctx.habits.todayHabits.size
                    val doneCount = ctx.habits.todayHabits.count { it.id in completed }
                    append("HABITS TODAY ($doneCount/$total):\n")
                    ctx.habits.todayHabits.forEach { habit ->
                        val status = if (habit.id in completed) "✓" else "○"
                        append("- $status ${habit.emoji} ${habit.name}\n")
                    }
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
