package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.memory.MemoryEntity
import org.junit.Assert.*
import org.junit.Test

/**
 * Verifies that auto-tagging uses word-bounded regex rather than naive contains(),
 * preventing partial-word false positives while preserving true positives.
 */
class MemoryEntityTaggingTest {

    private fun make(title: String = "", content: String = "", pkg: String? = null) =
        MemoryEntity.create(source = "notification", packageName = pkg, title = title, content = content)

    // ── #Health true positives ────────────────────────────────────────────────

    @Test fun `heart rate triggers Health`() {
        assertTrue(make(content = "Heart rate: 72 bpm").tags!!.contains("#Health"))
    }

    @Test fun `heartbeat triggers Health`() {
        assertTrue(make(content = "Elevated heartbeat detected").tags!!.contains("#Health"))
    }

    @Test fun `steps triggers Health`() {
        assertTrue(make(content = "You walked 8432 steps today").tags!!.contains("#Health"))
    }

    @Test fun `step triggers Health`() {
        assertTrue(make(content = "Each step counts toward your goal").tags!!.contains("#Health"))
    }

    @Test fun `sleep triggers Health`() {
        assertTrue(make(content = "You slept 7h 23m — good sleep last night").tags!!.contains("#Health"))
    }

    @Test fun `workout triggers Health`() {
        assertTrue(make(content = "Workout complete: 45 min cycling").tags!!.contains("#Health"))
    }

    @Test fun `bpm triggers Health`() {
        assertTrue(make(content = "Resting bpm: 58").tags!!.contains("#Health"))
    }

    // ── #Health false positives ───────────────────────────────────────────────

    @Test fun `bare heart in social message does NOT trigger Health`() {
        val tags = make(content = "Thanks from the bottom of my heart").tags
        assertFalse(tags?.contains("#Health") == true)
    }

    @Test fun `misstep does NOT trigger Health`() {
        val tags = make(content = "That was a costly misstep in negotiations").tags
        assertFalse(tags?.contains("#Health") == true)
    }

    @Test fun `footstep does NOT trigger Health`() {
        val tags = make(content = "I heard a footstep outside").tags
        assertFalse(tags?.contains("#Health") == true)
    }

    // ── #Work true positives ──────────────────────────────────────────────────

    @Test fun `task triggers Work`() {
        assertTrue(make(content = "New task assigned: update the report").tags!!.contains("#Work"))
    }

    @Test fun `meeting triggers Work`() {
        assertTrue(make(content = "Meeting at 3 PM in Room B").tags!!.contains("#Work"))
    }

    @Test fun `due date triggers Work`() {
        assertTrue(make(content = "Reminder: due date is tomorrow").tags!!.contains("#Work"))
    }

    @Test fun `deadline triggers Work`() {
        assertTrue(make(content = "Project deadline extended to Friday").tags!!.contains("#Work"))
    }

    // ── #Work false positives ─────────────────────────────────────────────────

    @Test fun `network does NOT trigger Work`() {
        val tags = make(content = "Wi-Fi network connection lost").tags
        assertFalse(tags?.contains("#Work") == true)
    }

    @Test fun `framework does NOT trigger Work`() {
        val tags = make(content = "New framework released by Google").tags
        assertFalse(tags?.contains("#Work") == true)
    }

    @Test fun `produce does NOT trigger Work via due`() {
        val tags = make(content = "The farm will produce a great harvest").tags
        assertFalse(tags?.contains("#Work") == true)
    }

    // ── #Finance true positives ───────────────────────────────────────────────

    @Test fun `payment triggers Finance`() {
        assertTrue(make(content = "Payment of \$12.99 received").tags!!.contains("#Finance"))
    }

    @Test fun `transaction triggers Finance`() {
        assertTrue(make(content = "Transaction approved at Starbucks").tags!!.contains("#Finance"))
    }

    @Test fun `credit card triggers Finance`() {
        assertTrue(make(content = "Your credit card statement is ready").tags!!.contains("#Finance"))
    }

    @Test fun `bank triggers Finance`() {
        assertTrue(make(content = "Bank transfer completed").tags!!.contains("#Finance"))
    }

    // ── #Finance false positives ──────────────────────────────────────────────

    @Test fun `riverbank does NOT trigger Finance`() {
        val tags = make(content = "We picnicked on the riverbank").tags
        assertFalse(tags?.contains("#Finance") == true)
    }

    // ── #Social true positives ────────────────────────────────────────────────

    @Test fun `gmail pkg triggers Social`() {
        assertTrue(make(content = "You have a new email", pkg = "com.google.android.gm").tags!!.contains("#Social"))
    }

    @Test fun `whatsapp in text triggers Social`() {
        assertTrue(make(content = "WhatsApp: John sent you a message").tags!!.contains("#Social"))
    }

    @Test fun `email triggers Social`() {
        assertTrue(make(content = "New email from your manager").tags!!.contains("#Social"))
    }

    // ── Clipboard always gets #Reference ─────────────────────────────────────

    @Test fun `clipboard source gets Reference not Social`() {
        val entity = MemoryEntity.create(source = "clipboard", packageName = null, title = null, content = "some text")
        assertTrue(entity.tags!!.contains("#Reference"))
        assertFalse(entity.tags.contains("#Social"))
    }

    // ── Untagged when no keywords match ──────────────────────────────────────

    @Test fun `unrelated content produces no tags`() {
        assertNull(make(content = "Happy birthday! Hope you have a great day").tags)
    }

    // ── Package-name priority (Step 1) ───────────────────────────────────────

    @Test fun `Zendence package immediately tags Health without text match`() {
        val entity = make(content = "Meditation complete", pkg = "com.alex.zendence")
        assertTrue(entity.tags!!.contains("#Health"))
    }

    @Test fun `Zepp package tags Health even when content is generic`() {
        val entity = make(content = "You have a new notification", pkg = "com.zeroner.app")
        assertTrue(entity.tags!!.contains("#Health"))
    }

    @Test fun `Todoist package tags Work`() {
        val entity = make(content = "Reminder: buy groceries", pkg = "com.todoist")
        assertTrue(entity.tags!!.contains("#Work"))
    }

    @Test fun `Slack package tags Work`() {
        val entity = make(content = "Alex mentioned you in general", pkg = "com.slack")
        assertTrue(entity.tags!!.contains("#Work"))
    }

    @Test fun `Venmo package tags Finance`() {
        val entity = make(content = "John paid you", pkg = "com.venmo")
        assertTrue(entity.tags!!.contains("#Finance"))
    }

    @Test fun `PayPal package tags Finance`() {
        val entity = make(content = "You received a payment", pkg = "com.paypal.android.p2pmobile")
        assertTrue(entity.tags!!.contains("#Finance"))
    }

    @Test fun `WhatsApp package tags Social`() {
        val entity = make(content = "John: hey!", pkg = "com.whatsapp")
        assertTrue(entity.tags!!.contains("#Social"))
    }

    @Test fun `Gmail package tags Social`() {
        val entity = make(content = "New message from Alex", pkg = "com.google.android.gm")
        assertTrue(entity.tags!!.contains("#Social"))
    }

    @Test fun `package tag prevents regex adding same category again — no duplicates`() {
        // WhatsApp pkg → #Social; text also contains "message" which would trigger SOCIAL_RE
        val entity = make(content = "New message from John", pkg = "com.whatsapp")
        val tagList = entity.tags!!.split(" ")
        assertEquals(1, tagList.count { it == "#Social" })
    }

    @Test fun `package tag does not block a DIFFERENT category from regex`() {
        // Todoist → #Work; content also mentions a payment → should also get #Finance
        val entity = make(content = "Payment of \$50 received for task completion", pkg = "com.todoist")
        assertTrue(entity.tags!!.contains("#Work"))
        assertTrue(entity.tags.contains("#Finance"))
    }

    @Test fun `unknown package falls through to regex tagging`() {
        val entity = make(content = "Your heart rate is elevated", pkg = "com.unknown.app")
        assertTrue(entity.tags!!.contains("#Health"))
    }

    @Test fun `all known health packages are in the map`() {
        val healthPkgs = listOf(
            "com.alex.zendence", "com.zeroner.app", "com.huami.watch.hmwatchmanager",
            "com.samsung.health", "com.google.android.apps.fitness",
            "com.garmin.android.apps.connectmobile", "com.fitbit.FitbitMobile",
            "com.strava"
        )
        healthPkgs.forEach { pkg ->
            val tags = MemoryEntity.PACKAGE_TAGS[pkg]
            assertNotNull("Expected $pkg in PACKAGE_TAGS", tags)
            assertTrue("Expected #Health for $pkg", tags!!.contains("#Health"))
        }
    }

    // ── Regex is precompiled (smoke test: multiple rapid calls don't throw) ──

    @Test fun `repeated calls are stable`() {
        repeat(100) {
            make(content = "Heart rate: 65 bpm — steps: 4200 — meeting at noon")
        }
    }
}
