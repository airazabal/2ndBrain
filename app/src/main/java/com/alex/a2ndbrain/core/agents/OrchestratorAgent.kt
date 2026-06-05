package com.alex.a2ndbrain.core.agents

import android.util.Log
import com.alex.a2ndbrain.core.exercise.ExerciseRepository
import com.alex.a2ndbrain.core.usage.UsageRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Calendar

/**
 * OrchestratorAgent — single entry point for all AI-powered features.
 *
 * Responsibilities:
 *   1. Build BrainContext by fetching memory + health + usage in parallel
 *   2. Route reflection tasks to ReflectionAgent (prompt construction)
 *   3. Route chat turns to ModelRouter with SessionMemory history
 *   4. Never hold UI state — ViewModels own StateFlow/SharedFlow
 *
 * ViewModels call orchestrator.buildContext() or orchestrator.chat() —
 * they never touch DAOs, GeminiAgent, or HealthConnectManager directly.
 *
 * CLAUDE.md data flow rule: "UI screens receive only plain data and lambda
 * callbacks — no ViewModel references are passed into composables."
 */
private const val COPILOT_SYSTEM_PROMPT =
    "You are the user's personal 2ndBrain assistant. Answer accurately, concisely, and friendly.\n" +
    "Focus ONLY on the section most relevant to the user's question — ignore unrelated data sections.\n" +
    "If the data below doesn't contain relevant details, say so politely."

class OrchestratorAgent(
    private val memoryAgent: MemoryAgent,
    private val healthAgent: HealthAgent,
    private val reflectionAgent: ReflectionAgent,
    private val modelRouter: ModelRouter,
    private val usageRepository: UsageRepository,
    private val exerciseRepository: ExerciseRepository
) {

    /**
     * Build a BrainContext snapshot. Fetches only what the query needs (ReAct pattern).
     *
     * @param query Optional search query to focus memory retrieval.
     * @param flags Controls which data sources are fetched. When null (reflection path or
     *              blank query), all sources are fetched unconditionally. When provided,
     *              each async fetch is gated on the relevant flag so irrelevant I/O is skipped.
     */
    suspend fun buildContext(
        query: String = "",
        flags: DynamicContextFlags? = null
    ): BrainContext = withContext(Dispatchers.IO) {
        val needsMemory   = flags == null || flags.includeMemories
        val needsHealth   = flags == null || flags.includeHealth || flags.includeMeditation
        val needsUsage    = flags == null || flags.includeUsage
        val needsExercise = flags == null || flags.includeExercise

        coroutineScope {
            val memoriesDeferred = async {
                if (needsMemory) memoryAgent.retrieve(query) else emptyList()
            }
            val healthPairDeferred = async {
                if (needsHealth) healthAgent.fetchAll()
                else Pair(HealthContext(), MeditationContext())
            }
            val usageDeferred = async {
                if (needsUsage) {
                    try { usageRepository.getUsageStatsForTodaySync() }
                    catch (e: Exception) { emptyList() }
                } else emptyList()
            }
            val exerciseDeferred = async {
                if (needsExercise) {
                    try { exerciseRepository.getRecentSessions(7) }
                    catch (e: Exception) { emptyList() }
                } else emptyList()
            }

            val memories = memoriesDeferred.await()
            val (healthCtx, meditationCtx) = healthPairDeferred.await()
            val usage = usageDeferred.await()
            val exerciseSessions = exerciseDeferred.await()

            BrainContext(
                memories = memories,
                health = healthCtx,
                usageStats = usage,
                meditation = meditationCtx,
                exercise = ExerciseContext(recentSessions = exerciseSessions)
            )
        }
    }

    /**
     * Generate a daily or weekly reflection with a one-shot critique loop.
     *
     * Builds context, generates a draft, then scores it against a quality rubric.
     * If the draft fails (too short, wrong format, data denial), appends a critique
     * and retries once. At most two model calls per reflection.
     */
    suspend fun reflect(type: ReflectionAgent.ReflectionType): Pair<String, String> {
        Log.d("OrchestratorAgent", "reflect($type) starting")
        return try {
            val ctx = buildContext()
            val prompt = reflectionAgent.buildPrompt(type, ctx)
            val complexity = when (type) {
                ReflectionAgent.ReflectionType.WEEKLY_CORRELATION -> ModelRouter.Complexity.HIGH
                else -> ModelRouter.Complexity.MEDIUM
            }
            val (draft, modelName) = modelRouter.run(prompt, complexity)

            val critique = reflectionAgent.critique(draft, type, ctx)
            if (critique != null) {
                Log.w("OrchestratorAgent", "reflect($type) — critique failed, retrying. Reason: $critique")
                val revisedPrompt = buildString {
                    append(prompt)
                    append("\n\n---\nPREVIOUS DRAFT (do not repeat it verbatim):\n")
                    append(draft)
                    append("\n\nCRITIQUE: $critique\n")
                    append("Address the critique above and produce an improved response.")
                }
                Log.d("OrchestratorAgent", "reflect($type) — retry prompt size: ${revisedPrompt.length} chars")
                if (revisedPrompt.length > 15_000) {
                    Log.w("OrchestratorAgent", "reflect($type) — retry prompt exceeds 15k chars, returning draft")
                    draft to modelName
                } else {
                    modelRouter.run(revisedPrompt, complexity)
                }
            } else {
                draft to modelName
            }
        } catch (e: Exception) {
            Log.e("OrchestratorAgent", "reflect() failed", e)
            "Reflection failed: ${e.message}" to "Error"
        }
    }

    /**
     * Handle a Copilot chat turn with full conversation history.
     *
     * Stores the raw question in SessionMemory (not the enriched prompt) so
     * prior turns don't pollute the context window with stale data from previous
     * topics. The enriched prompt (question + relevant data) is injected only for
     * the current turn at inference time.
     */
    suspend fun chat(
        userMessage: String,
        sessionMemory: SessionMemory,
        dynamicContextFlags: DynamicContextFlags = DynamicContextFlags.fromMessage(userMessage)
    ): Pair<String, String> {
        Log.d("OrchestratorAgent", "chat() — ${sessionMemory.messageCount()} prior turns")
        return try {
            val ctx = buildContext(query = userMessage, flags = dynamicContextFlags)
            val enrichedPrompt = buildCopilotPrompt(userMessage, ctx, dynamicContextFlags)

            // Store the raw question so prior turns don't pollute the context
            // window with stale data dumps from previous topics.
            sessionMemory.add(AgentMessage("user", userMessage))

            // For inference, swap the last history entry for the enriched version
            // so only the current turn carries its data context.
            val historyForInference = sessionMemory.getHistory().dropLast(1) +
                AgentMessage("user", enrichedPrompt)

            val (replyText, modelName) = modelRouter.runWithHistory(
                history = historyForInference,
                complexity = ModelRouter.Complexity.LOW,
                systemInstruction = COPILOT_SYSTEM_PROMPT
            )

            sessionMemory.add(AgentMessage("model", replyText))

            replyText to modelName
        } catch (e: Exception) {
            Log.e("OrchestratorAgent", "chat() failed", e)
            "Sorry, I ran into an error: ${e.message}" to "Error"
        }
    }

    private fun buildCopilotPrompt(
        userMessage: String,
        ctx: BrainContext,
        flags: DynamicContextFlags
    ): String = buildString {
        if (flags.includeHealth && ctx.health.isAvailable) {
            val m = ctx.health.metrics
            append("PHYSICAL HEALTH TODAY:\n")
            append("- Steps: ${m.steps} (goal: 10,000)\n")
            append("- Sleep last night: ${m.sleepMinutes / 60}h ${m.sleepMinutes % 60}m\n")
            append("- Heart rate: ${m.minHeartRate}–${m.maxHeartRate} BPM (avg ${m.avgHeartRate})\n\n")
        }

        if (flags.includeUsage && ctx.usageStats.isNotEmpty()) {
            val activeStats = ctx.usageStats.filter { it.totalTimeVisibleMs / 60_000L > 0 }
            if (activeStats.isNotEmpty()) {
                append("APP SCREEN TIME TODAY:\n")
                val limit = if (flags.isGeneral) 5 else activeStats.size
                activeStats.take(limit).forEach { stat ->
                    val mins = stat.totalTimeVisibleMs / 60_000L
                    val label = stat.packageName.substringAfterLast(".")
                        .replaceFirstChar { it.titlecase() }
                    append("- $label: ${mins}m\n")
                }
                if (flags.isGeneral && activeStats.size > 5) {
                    append("- ...and ${activeStats.size - 5} other apps\n")
                }
                append("\n")
            }
        }

        if (flags.includeExercise && ctx.exercise.recentSessions.isNotEmpty()) {
            val sessions = ctx.exercise.recentSessions
            val totalMins = sessions.sumOf { it.durationMinutes }
            append("EXERCISE (last 7 days):\n")
            append("- Sessions: ${sessions.size}, total time: ${totalMins / 60}h ${totalMins % 60}m\n")
            sessions.take(5).forEach { s ->
                val type = runCatching { com.alex.a2ndbrain.core.exercise.ExerciseType.valueOf(s.type).displayName }
                    .getOrDefault(s.type)
                val dur = if (s.durationMinutes >= 60) "${s.durationMinutes / 60}h ${s.durationMinutes % 60}m"
                          else "${s.durationMinutes}m"
                val notes = if (s.notes.isNotBlank()) " — ${s.notes}" else ""
                append("- ${s.date}: $type $dur$notes\n")
            }
            append("\n")
        }

        if (flags.includeMeditation && ctx.meditation.sessions.isNotEmpty()) {
            val streaks = ctx.meditation.streaks
            append("MEDITATION (Zendence):\n")
            append("- Current week streak: ${streaks.currentWeekStreak} days\n")
            append("- Max streak: ${streaks.maxOverallStreak} days\n")
            val limit = if (flags.isGeneral) 2 else 5
            ctx.meditation.sessions.take(limit).forEach { session ->
                val date = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(session.timestamp))
                append("- $date: ${session.durationMinutes}m — \"${session.insight}\"\n")
            }
            append("\n")
        }

        if (flags.includeMemories && ctx.memories.isNotEmpty()) {
            val lower = userMessage.lowercase(java.util.Locale.getDefault())
            val isFinanceQuery = DynamicContextFlags.FINANCE_KEYWORDS.any { lower.matchesKeyword(it) }
            val header = if (isFinanceQuery)
                "CAPTURED MEMORIES (search ALL sources — notifications, SMS, email, clipboard — for bank/payment/shopping entries):\n"
            else
                "CAPTURED MEMORIES (all sources: notifications, SMS, email, clipboard):\n"
            append(header)
            ctx.memories.take(20).forEach { mem ->
                val date = java.text.SimpleDateFormat("MMM dd HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(mem.timestamp))
                val source = mem.packageName?.substringAfterLast(".")
                    ?: mem.source
                val title = mem.title?.let { " | $it" } ?: ""
                append("- [$date][$source$title][${mem.tags ?: ""}] ${mem.content}\n")
            }
            append("\n")
        }

        append("USER'S QUESTION: $userMessage\n")
    }
}

/**
 * Determines which context sections to include based on the user's message.
 * Mirrors the dynamic routing logic in CopilotViewModel but owned by the agent layer.
 */
data class DynamicContextFlags(
    val includeHealth: Boolean,
    val includeUsage: Boolean,
    val includeMeditation: Boolean,
    val includeMemories: Boolean,
    val includeExercise: Boolean,
    val isGeneral: Boolean
) {
    companion object {
        // Single source of truth for finance keyword matching.
        // Referenced by both fromMessage() and buildCopilotPrompt() to prevent drift.
        val FINANCE_KEYWORDS = listOf(
            "money", "spend", "spent", "paid", "pay", "payment", "bank", "transaction",
            "cost", "price", "purchase", "budget", "expense", "shop", "bought", "dollar",
            "financ", "bill", "invoice", "charge", "receipt"
        )

        fun fromMessage(message: String): DynamicContextFlags {
            val lower = message.lowercase(java.util.Locale.getDefault())
            // "spend/spent" removed — too ambiguous; finance keywords below catch money questions
            val health = listOf("step", "sleep", "heart", "bpm", "walk", "physical", "active", "health", "run", "fit", "calories").any { lower.matchesKeyword(it) }
            val usage = listOf("screen time", "app time", "screen", "usage", "youtube", "chrome", "social", "distract", "phone", "tablet", "device", "online", "digital", "how long").any { lower.matchesKeyword(it) }
            val meditation = listOf("meditat", "zendence", "streak", "mindful", "calm", "insight", "breath", "relax", "practice", "mantra", "sit").any { lower.matchesKeyword(it) }
            val exercise = listOf("exercise", "workout", "gym", "training", "fitness", "jog", "lift", "weightlift", "cycling", "swimming", "hiit", "stretching").any { lower.matchesKeyword(it) }
            // Finance keywords route to memories (bank/payment notifications captured there)
            val finance = FINANCE_KEYWORDS.any { lower.matchesKeyword(it) }
            val memories = finance || listOf("notification", "clipboard", "log", "memory", "captured", "tag", "remember", "text", "copy", "message", "email", "chat", "gmail", "slack", "whatsapp").any { lower.matchesKeyword(it) }
            val general = !health && !usage && !meditation && !exercise && !memories
            return DynamicContextFlags(
                includeHealth = health || general,
                includeUsage = usage || general,
                includeMeditation = meditation || general,
                includeExercise = exercise || general,
                includeMemories = memories || general,
                isGeneral = general
            )
        }
    }
}

// Left-boundary word match: \bkeyword allows prefix matches ("run" → "running", "meditat" → "meditation")
// but prevents interior matches ("fit" no longer matches "outfit", "run" no longer matches "overrun").
private fun String.matchesKeyword(keyword: String): Boolean =
    Regex("\\b${Regex.escape(keyword)}").containsMatchIn(this)
