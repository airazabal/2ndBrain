package com.alex.a2ndbrain.core.memory

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "memories")
data class MemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val source: String, // e.g., "notification", "clipboard", "voice"
    val packageName: String?, // for notifications
    val title: String?,
    val content: String,
    val deepLink: String? = null, // URI to jump to specific content
    val isRead: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val duplicateCount: Int = 1,
    val tags: String? = null
) {
    companion object {

        // ── Step 1: Package-name lookup (authoritative, O(1)) ─────────────────
        // These are checked before any text analysis. A known package always
        // gets its tag regardless of notification content, and prevents the text
        // fallback from adding a contradictory tag for that category.
        internal val PACKAGE_TAGS: Map<String, List<String>> = mapOf(
            // Health & fitness
            "com.alex.zendence"                        to listOf("#Health"),
            "com.zeroner.app"                          to listOf("#Health"), // Zepp Life / Amazfit
            "com.huami.watch.hmwatchmanager"           to listOf("#Health"), // Zepp OS
            "com.samsung.health"                       to listOf("#Health"),
            "com.sec.android.app.shealth"              to listOf("#Health"),
            "com.google.android.apps.fitness"          to listOf("#Health"), // Google Fit
            "com.garmin.android.apps.connectmobile"    to listOf("#Health"), // Garmin Connect
            "com.fitbit.FitbitMobile"                  to listOf("#Health"),
            "com.strava"                               to listOf("#Health"),
            "com.nike.plusgps"                         to listOf("#Health"), // Nike Run Club
            "com.adidas.runtastic"                     to listOf("#Health"), // Runtastic
            "com.google.android.apps.healthdata"       to listOf("#Health"), // Health Connect

            // Finance
            "com.venmo"                                to listOf("#Finance"),
            "com.paypal.android.p2pmobile"             to listOf("#Finance"),
            "com.cashapp"                              to listOf("#Finance"),
            "com.robinhood.android"                    to listOf("#Finance"),
            "com.coinbase.android"                     to listOf("#Finance"),
            "com.chase.sig.android"                    to listOf("#Finance"),
            "com.bankofamerica.cashpay"                to listOf("#Finance"),
            "com.americanexpress.android.acctsvcs"     to listOf("#Finance"),
            "com.mint"                                 to listOf("#Finance"),
            "com.wealthfront.android"                  to listOf("#Finance"),

            // Work & productivity
            "com.todoist"                              to listOf("#Work"),
            "com.microsoft.teams"                      to listOf("#Work"),
            "com.slack"                                to listOf("#Work"),
            "com.google.android.calendar"              to listOf("#Work"),
            "com.microsoft.office.outlook"             to listOf("#Work"),
            "com.atlassian.android.jira.core"          to listOf("#Work"),
            "com.github.android"                       to listOf("#Work"),
            "com.gitlab.android"                       to listOf("#Work"),
            "com.asana.app"                            to listOf("#Work"),
            "com.trello"                               to listOf("#Work"),
            "notion.id"                                to listOf("#Work"),
            "com.linearapp.mobile"                     to listOf("#Work"),

            // Social
            "com.whatsapp"                             to listOf("#Social"),
            "com.google.android.gm"                    to listOf("#Social"),
            "org.telegram.messenger"                   to listOf("#Social"),
            "com.facebook.orca"                        to listOf("#Social"),
            "com.instagram.android"                    to listOf("#Social"),
            "com.twitter.android"                      to listOf("#Social"),
            "com.snapchat.android"                     to listOf("#Social"),
            "com.linkedin.android"                     to listOf("#Social"),
            "com.discord"                              to listOf("#Social"),
            "com.reddit.frontpage"                     to listOf("#Social"),
            "com.google.android.apps.messaging"        to listOf("#Social"),
            "com.zhiliaoapp.musically"                 to listOf("#Social"), // TikTok
        )

        // ── Step 2: Regex fallback (word-bounded, precompiled) ────────────────
        // Only runs for categories not already resolved by package lookup.
        // "heart" alone is too broad; require "heart rate"/"heartbeat".
        // "step" alone matches "misstep"/"footstep"; use "steps?".
        // "work" alone matches "network"/"framework"; \b prevents those.
        private val HEALTH_RE = Regex(
            """\b(steps?|heart rate|heartbeat|resting heart|sleep|zepp|workout|kcal|calories?|health connect|bpm|blood pressure|spo2)\b""",
            RegexOption.IGNORE_CASE
        )
        private val WORK_RE = Regex(
            """\b(todoist|tasks?|meeting|scheduled?|calendar|due date|project|work|deadline|sprint|stand-?up)\b""",
            RegexOption.IGNORE_CASE
        )
        private val SOCIAL_RE = Regex(
            """\b(whatsapp|gmail|outlook|messenger|telegram|signal|instagram|e?mail|messages?|chat)\b""",
            RegexOption.IGNORE_CASE
        )
        private val FINANCE_RE = Regex(
            """\b(transaction|spent|amount|payment|credit card|debit card|bank(ing)?|cost|price|invoice|receipt|balance|charged?)\b""",
            RegexOption.IGNORE_CASE
        )

        fun create(
            source: String,
            packageName: String?,
            title: String?,
            content: String,
            deepLink: String? = null,
            isRead: Boolean = false,
            timestamp: Long = System.currentTimeMillis(),
            duplicateCount: Int = 1
        ): MemoryEntity {
            // LinkedHashSet preserves insertion order and deduplicates automatically
            val tags = linkedSetOf<String>()

            // Step 1: package lookup — authoritative, O(1)
            packageName?.let { PACKAGE_TAGS[it] }?.forEach { tags.add(it) }

            // Step 2: regex text analysis — supplements package tags and handles
            // unknown packages; skip a category already resolved by package lookup
            if (source == "clipboard") {
                tags.add("#Reference")
            } else {
                val text = "${title ?: ""} $content ${packageName ?: ""}"
                if ("#Health" !in tags && HEALTH_RE.containsMatchIn(text))   tags.add("#Health")
                if ("#Work" !in tags && WORK_RE.containsMatchIn(text))        tags.add("#Work")
                if ("#Social" !in tags && SOCIAL_RE.containsMatchIn(text))    tags.add("#Social")
                if ("#Finance" !in tags && FINANCE_RE.containsMatchIn(text))  tags.add("#Finance")
            }

            return MemoryEntity(
                source = source,
                packageName = packageName,
                title = title,
                content = content,
                deepLink = deepLink,
                isRead = isRead,
                timestamp = timestamp,
                duplicateCount = duplicateCount,
                tags = if (tags.isEmpty()) null else tags.joinToString(" ")
            )
        }
    }
}
