package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.domain.ExportBackupUseCase
import com.alex.a2ndbrain.core.domain.ImportBackupUseCase
import com.alex.a2ndbrain.core.health.HealthRepository
import com.alex.a2ndbrain.fakes.FakeMemoryRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupDistractionTest {

    private val settings = mockk<CaptureSettingsManager>(relaxed = true)
    private val healthRepo = mockk<HealthRepository>(relaxed = true)
    private val memoryRepo = FakeMemoryRepository()

    private lateinit var exportUseCase: ExportBackupUseCase
    private lateinit var importUseCase: ImportBackupUseCase

    @Before
    fun setup() {
        every { settings.getMonitoredApps() } returns emptySet()
        every { settings.getTodoistApiToken() } returns ""
        every { settings.getGeminiApiKey() } returns ""
        every { settings.getGeminiModel() } returns "gemini-2.5-flash"
        every { settings.getPreferredModelType() } returns "AUTO"
        every { settings.getSelectedLiteRTModel() } returns "Gemma-3-1B-IT"
        every { settings.getThemePreference() } returns "SYSTEM"
        every { settings.getRefreshIntervalMinutes() } returns 30
        every { settings.isCalendarSyncEnabled() } returns false
        every { settings.getStepsGoal() } returns 10000
        every { settings.getSleepGoalHours() } returns 7.5f
        every { settings.getExerciseGoalMinutes() } returns 30
        every { settings.getDigitalFocusBaselineMinutes() } returns 120
        coEvery { healthRepo.getSnapshotsForSync(any()) } returns emptyList()

        exportUseCase = ExportBackupUseCase(memoryRepo, healthRepo, settings)
        importUseCase = ImportBackupUseCase(settings)
    }

    @Test
    fun `export includes distraction apps`() = runTest {
        every { settings.getDistractionApps() } returns
            setOf("com.instagram.android", "com.twitter.android")
        every { settings.getDistractionThresholdMinutes() } returns 30

        val json = JSONObject(exportUseCase())
        val arr = json.getJSONArray("distractionApps")
        val exported = (0 until arr.length()).map { arr.getString(it) }.toSet()

        assertTrue(exported.contains("com.instagram.android"))
        assertTrue(exported.contains("com.twitter.android"))
    }

    @Test
    fun `export includes distraction threshold`() = runTest {
        every { settings.getDistractionApps() } returns emptySet()
        every { settings.getDistractionThresholdMinutes() } returns 20

        val json = JSONObject(exportUseCase())

        assertEquals(20, json.getInt("distractionThresholdMinutes"))
    }

    @Test
    fun `import restores distraction apps`() = runTest {
        val capturedApps = slot<Set<String>>()
        io.mockk.justRun { settings.setDistractionApps(capture(capturedApps)) }

        val json = JSONObject().apply {
            put("distractionApps", org.json.JSONArray().apply {
                put("com.netflix.mediaclient")
                put("com.reddit.frontpage")
            })
        }.toString()

        importUseCase(json)

        assertEquals(setOf("com.netflix.mediaclient", "com.reddit.frontpage"), capturedApps.captured)
    }

    @Test
    fun `import restores distraction threshold`() = runTest {
        io.mockk.justRun { settings.setDistractionThresholdMinutes(any()) }

        val json = JSONObject().apply {
            put("distractionThresholdMinutes", 25)
        }.toString()

        importUseCase(json)

        verify { settings.setDistractionThresholdMinutes(25) }
    }

    @Test
    fun `import with no distraction keys leaves settings untouched`() = runTest {
        val json = JSONObject().toString()

        importUseCase(json)

        verify(exactly = 0) { settings.setDistractionApps(any()) }
        verify(exactly = 0) { settings.setDistractionThresholdMinutes(any()) }
    }

    @Test
    fun `round trip preserves distraction apps and threshold`() = runTest {
        val apps = setOf("com.instagram.android", "tv.twitch.android.app")
        every { settings.getDistractionApps() } returns apps
        every { settings.getDistractionThresholdMinutes() } returns 60

        val json = exportUseCase()

        val capturedApps = slot<Set<String>>()
        val capturedThreshold = slot<Int>()
        io.mockk.justRun { settings.setDistractionApps(capture(capturedApps)) }
        io.mockk.justRun { settings.setDistractionThresholdMinutes(capture(capturedThreshold)) }

        importUseCase(json)

        assertEquals(apps, capturedApps.captured)
        assertEquals(60, capturedThreshold.captured)
    }
}
