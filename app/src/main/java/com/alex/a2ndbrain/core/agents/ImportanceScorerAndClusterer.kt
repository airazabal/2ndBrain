package com.alex.a2ndbrain.core.agents

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Scores a memory event 0.0–1.0 based on three signals:
 *
 *   Recency   — exponential decay; half-life = 12 hours
 *   Frequency — how many similar events occurred in the same window (log-scaled)
 *   Emotion   — presence of emotional/health-related signal words (+0.15 boost)
 *
 * Final score = weighted average, clamped to [0, 1].
 * Weights are tunable constants — adjust after a few days of real data.
 */
object ImportanceScorer {

    private const val RECENCY_WEIGHT    = 0.50f
    private const val FREQUENCY_WEIGHT  = 0.35f
    private const val EMOTION_WEIGHT    = 0.15f
    private const val HALF_LIFE_HOURS   = 12.0

    // Words that indicate emotionally significant or health-relevant content.
    // Extend this list based on your notification/health data patterns.
    private val EMOTIONAL_SIGNALS = setOf(
        "stress", "anxious", "happy", "sad", "excited", "tired", "pain",
        "sleep", "workout", "meditation", "focus", "headache", "mood",
        "goal", "completed", "streak", "missed", "reminder", "urgent"
    )

    fun score(
        text:       String,
        timestamp:  Instant,
        frequency:  Int,
        hasEmotion: Boolean = hasEmotionalSignal(text)
    ): Float {
        val hoursOld    = ChronoUnit.HOURS.between(timestamp, Instant.now()).toDouble()
        val recency     = Math.pow(0.5, hoursOld / HALF_LIFE_HOURS).toFloat()
        val freq        = (ln(frequency.coerceAtLeast(1).toDouble() + 1) / ln(10.0)).toFloat()
            .coerceIn(0f, 1f)
        val emotion     = if (hasEmotion) 1.0f else 0.0f

        return (recency * RECENCY_WEIGHT + freq * FREQUENCY_WEIGHT + emotion * EMOTION_WEIGHT)
            .coerceIn(0f, 1f)
    }

    fun hasEmotionalSignal(text: String): Boolean {
        val lower = text.lowercase()
        return EMOTIONAL_SIGNALS.any { lower.contains(it) }
    }
}

/**
 * Groups ScoredEvents into clusters of semantically similar content using
 * TF-IDF vectors and cosine similarity — no external library or vector DB needed.
 *
 * This is intentionally lightweight. It runs on-device at 2 AM on a small corpus
 * (~50–200 events per day). If your event volume grows significantly, swap this
 * for a proper embedding-based approach using Gemini's text-embedding-004 model.
 *
 * Algorithm:
 *  1. Build a TF-IDF matrix across all event texts
 *  2. For each unvisited event, find all events with cosine similarity ≥ threshold
 *  3. Group into clusters; singletons below importance threshold are dropped
 */
object TextClusterer {

    fun cluster(
        events:              List<ScoredEvent>,
        similarityThreshold: Float = 0.45f
    ): List<List<ScoredEvent>> {
        if (events.isEmpty()) return emptyList()

        val texts   = events.map { it.event.content }
        val vectors = buildTfIdfVectors(texts)

        val visited  = BooleanArray(events.size)
        val clusters = mutableListOf<List<ScoredEvent>>()

        for (i in events.indices) {
            if (visited[i]) continue
            visited[i] = true

            val cluster = mutableListOf(events[i])
            for (j in i + 1 until events.size) {
                if (!visited[j] && cosineSimilarity(vectors[i], vectors[j]) >= similarityThreshold) {
                    cluster.add(events[j])
                    visited[j] = true
                }
            }
            clusters.add(cluster)
        }

        return clusters
    }

    // ─── TF-IDF internals ────────────────────────────────────────────────────

    private fun buildTfIdfVectors(texts: List<String>): List<Map<String, Float>> {
        val tokenized  = texts.map { tokenize(it) }
        val docCount   = texts.size.toDouble()
        val allTerms   = tokenized.flatten().toSet()

        // IDF: log(N / df(t)) for each term
        val idf = allTerms.associateWith { term ->
            val df = tokenized.count { it.contains(term) }.coerceAtLeast(1)
            ln(docCount / df).toFloat()
        }

        // TF-IDF vector per document
        return tokenized.map { tokens ->
            val tf = tokens.groupingBy { it }.eachCount()
            val maxTf = tf.values.maxOrNull()?.toFloat() ?: 1f
            tf.entries.associate { (term, count) ->
                term to (count / maxTf) * (idf[term] ?: 0f)
            }
        }
    }

    private fun tokenize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length > 2 && it !in STOP_WORDS }

    private fun cosineSimilarity(a: Map<String, Float>, b: Map<String, Float>): Float {
        val dot   = a.entries.sumOf { (k, v) -> (v * (b[k] ?: 0f)).toDouble() }.toFloat()
        val normA = sqrt(a.values.sumOf { (it * it).toDouble() }).toFloat()
        val normB = sqrt(b.values.sumOf { (it * it).toDouble() }).toFloat()
        return if (normA == 0f || normB == 0f) 0f else dot / (normA * normB)
    }

    private val STOP_WORDS = setOf(
        "the", "and", "for", "are", "was", "with", "you", "this",
        "that", "from", "have", "has", "had", "not", "but", "they",
        "your", "its", "our", "just", "can", "will", "been"
    )
}

data class ScoredEvent(val event: EpisodicEvent, val score: Float)

data class EpisodicEvent(
    val id: Long,
    val content: String,
    val timestamp: Instant,
    val sourceTag: String
)
