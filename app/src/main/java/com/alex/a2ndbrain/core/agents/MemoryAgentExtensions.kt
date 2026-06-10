package com.alex.a2ndbrain.core.agents

import java.time.Instant

// ─── Data models ─────────────────────────────────────────────────────────────

enum class MemoryTier { SHORT_TERM, LONG_TERM, EPISODIC }

data class ConsolidatedMemory(
    val id:             Long        = 0,
    val summary:        String,
    val sourceEventIds: List<Long>,
    val importanceScore: Float,
    val tier:           MemoryTier,
    val createdAt:      Instant
)

// ─── MemoryAgent extensions ──────────────────────────────────────────────────
//
// Add these functions to your existing MemoryAgent class.
// They use your existing GenerativeModel (flash-lite for low cost) to produce
// a tight one-sentence summary of each event cluster before long-term storage.

/**
 * Takes a list of raw event strings from one cluster and returns a single
 * concise summary suitable for injecting into future agent contexts.
 *
 * Costs ~100–200 input tokens per cluster (flash-lite = effectively free at
 * personal scale within the 1,500 RPD free tier).
 */
suspend fun MemoryAgent.summarizeCluster(events: List<String>): String {
    if (events.size == 1) return events.first().take(200)

    val prompt = buildString {
        appendLine("Summarize the following related observations into ONE concise sentence (max 30 words).")
        appendLine("Focus on the key pattern or insight, not the individual events.")
        appendLine("Return only the summary sentence, no preamble.")
        appendLine()
        events.forEachIndexed { i, e -> appendLine("- $e") }
    }

    // TODO: wire generativeModel once MemoryAgent exposes it
    return events.first().take(200)
}

/**
 * Retrieves the top-K long-term memories most relevant to a given query,
 * to be injected into the agent's context window before task execution.
 *
 * This uses keyword overlap scoring (no vector DB). Replace with embedding
 * similarity if you later add Gemini text-embedding-004 calls.
 *
 * Usage in OrchestratorAgent.buildContext():
 *   val relevantMemories = memoryAgent.recallForContext(userQuery, topK = 5)
 *   // inject into BrainContext.longTermMemories
 */
suspend fun MemoryAgent.recallForContext(query: String, topK: Int = 5): List<ConsolidatedMemory> {
    // TODO: implement once MemoryRepository exposes getLongTermMemories()
    return emptyList()
}

// ─── BrainContext extension ───────────────────────────────────────────────────
//
// Add longTermMemories to your existing BrainContext data class so agents
// automatically receive relevant history alongside the current session snapshot.
//
// In BrainContext.kt, add:
//
//   val longTermMemories: List<ConsolidatedMemory> = emptyList()
//
// In OrchestratorAgent.buildContext(), populate it:
//
//   val memories = memoryAgent.recallForContext(currentQuery)
//   return BrainContext(
//       ...existing fields...,
//       longTermMemories = memories
//   )
//
// In ReflectionAgent prompt construction, include memories like:
//
//   if (context.longTermMemories.isNotEmpty()) {
//       appendLine("## Relevant patterns from your history:")
//       context.longTermMemories.forEach {
//           appendLine("- ${it.summary} (importance: ${"%.0f".format(it.importanceScore * 100)}%)")
//       }
//   }
