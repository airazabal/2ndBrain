// BrainWatchBridge.kt
// Drop this into your 2ndBrain Android project.
// It wraps the Zepp SDK's BLE messaging channel and exposes a simple
// send API that your OrchestratorAgent / ReflectionAgent can call.
//
// Dependencies to add to your build.gradle.kts:
//   implementation("com.zepp.sdk:zepp-android-sdk:1.0.0")
//   (or the local .aar — download from https://developer.zepp.com/)

package com.alex.a2ndbrain.core.agents

import android.content.Context
import com.zepp.sdk.WatchMessageHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

// ─── Data models ─────────────────────────────────────────────────────────────

@Serializable
data class WatchBriefing(
    val title: String = "Morning Briefing",
    val summary: String,
    val generatedAt: String = Instant.now().toString()
)

@Serializable
data class WatchHabit(
    val id: String,
    val name: String,
    val done: Boolean,
    val streak: Int = 0,
    val category: String = "default"   // health | focus | meditation | exercise | learning
)

@Serializable
data class WatchStats(
    val readinessScore: Int?,          // 0–100, computed by your AI agents
    val steps: Int?,
    val heartRate: Int?,
    val focusMinutes: Int?,
    val meditationDone: Boolean = false
)

// ─── Payload wrappers ─────────────────────────────────────────────────────────

@Serializable
private data class SyncAllPayload(
    val type: String = "SYNC_ALL",
    val timestamp: String = Instant.now().toString(),
    val briefing: WatchBriefing,
    val habits: List<WatchHabit>,
    val stats: WatchStats
)

@Serializable
private data class SyncBriefingPayload(
    val type: String = "SYNC_BRIEFING",
    val briefing: WatchBriefing
)

@Serializable
private data class SyncHabitsPayload(
    val type: String = "SYNC_HABITS",
    val habits: List<WatchHabit>
)

@Serializable
private data class SyncStatsPayload(
    val type: String = "SYNC_STATS",
    val stats: WatchStats
)

// ─── Bridge ───────────────────────────────────────────────────────────────────

/**
 * BrainWatchBridge — singleton that manages BLE communication to the Zepp OS watch app.
 *
 * Usage (from your OrchestratorAgent or ViewModel):
 *
 *   BrainWatchBridge.init(context)
 *
 *   // After morning briefing is generated:
 *   BrainWatchBridge.syncAll(
 *     briefing = WatchBriefing(summary = agentOutput.dailySummary),
 *     habits   = habitRepository.getTodayHabits().map { it.toWatchHabit() },
 *     stats    = WatchStats(
 *       readinessScore = healthAgent.readinessScore,
 *       steps          = healthData.steps,
 *       heartRate      = healthData.currentHR,
 *       focusMinutes   = activityData.focusMinutes,
 *       meditationDone = meditationSession.completed
 *     )
 *   )
 */
object BrainWatchBridge {

    private val scope  = CoroutineScope(Dispatchers.IO)
    private val json   = Json { encodeDefaults = true }
    private var helper: WatchMessageHelper? = null

    /**
     * Call once from your Application.onCreate() or MainActivity.onCreate().
     */
    fun init(context: Context) {
        helper = WatchMessageHelper.getInstance(context)
        helper?.connect()
    }

    // ── Send full sync (call this after morning briefing is generated) ───────
    fun syncAll(briefing: WatchBriefing, habits: List<WatchHabit>, stats: WatchStats) {
        val payload = SyncAllPayload(briefing = briefing, habits = habits, stats = stats)
        send(payload)
    }

    // ── Partial sync helpers (call after individual data updates) ────────────
    fun syncBriefing(briefing: WatchBriefing) {
        send(SyncBriefingPayload(briefing = briefing))
    }

    fun syncHabits(habits: List<WatchHabit>) {
        send(SyncHabitsPayload(habits = habits))
    }

    fun syncStats(stats: WatchStats) {
        send(SyncStatsPayload(stats = stats))
    }

    // ── Ping watch to check if app is running ────────────────────────────────
    fun ping() {
        send(mapOf("type" to "PING", "timestamp" to Instant.now().toString()))
    }

    // ── Internal send ─────────────────────────────────────────────────────────
    private inline fun <reified T> send(payload: T) {
        scope.launch {
            try {
                val jsonString = json.encodeToString(payload)
                helper?.sendMessage(jsonString.toByteArray())
                    ?: run { /* Watch not connected — queue for retry if needed */ }
            } catch (e: Exception) {
                // Log to your existing logging infra
                android.util.Log.e("BrainWatchBridge", "Failed to send to watch: ${e.message}")
            }
        }
    }
}

// ─── Extension — map your domain models to watch models ─────────────────────
// Add these in your habit/task repository or mapper files.

// Example: if you have a Habit domain class:
// fun Habit.toWatchHabit() = WatchHabit(
//     id       = this.id,
//     name     = this.name,
//     done     = this.completedToday,
//     streak   = this.currentStreak,
//     category = this.category.lowercase()
// )
