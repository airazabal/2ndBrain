package com.alex.a2ndbrain

import com.alex.a2ndbrain.core.agents.ModelRouter
import com.alex.a2ndbrain.core.capture.CaptureSettingsManager
import com.alex.a2ndbrain.core.reflection.GeminiAgent
import com.alex.a2ndbrain.core.reflection.ModelPicker
import com.alex.a2ndbrain.core.reflection.SummaryResult
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ModelRouterFallbackTest {

    private val settings = mockk<CaptureSettingsManager>(relaxed = true)
    private val geminiAgent = mockk<GeminiAgent>(relaxed = true)
    private val modelPicker = mockk<ModelPicker>(relaxed = true)
    private lateinit var router: ModelRouter

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { Log.d(any(), any()) } returns 0
        every { modelPicker.getBestModel() } returns ModelPicker.ModelType.GEMINI_CLOUD
        every { settings.getGeminiModel() } returns "gemini-2.5-flash"
        every { settings.getLastSuccessfulModel() } returns ""
        every { settings.getSelectedLiteRTModel() } returns "Gemma-3-1B-IT"
        router = ModelRouter(settings, geminiAgent, modelPicker)
    }

    @Test
    fun `cloud success returns model name without fallback marker`() = runTest {
        coEvery { geminiAgent.chatInference(any(), any(), any(), any(), any()) } returns
            SummaryResult("cloud response", "gemini-2.5-flash")

        val (text, modelUsed) = router.run("hello")

        assertFalse(modelUsed.contains("offline fallback"))
        assertTrue(text == "cloud response")
    }

    @Test
    fun `cloud exception falls back to LiteRT and marks offline fallback`() = runTest {
        coEvery { geminiAgent.chatInference(any(), any(), any(), any(), any()) } throws
            RuntimeException("network error")
        coEvery { modelPicker.runLiteRTInference(any()) } returns "local answer"

        val (text, modelUsed) = router.run("hello")

        assertTrue(modelUsed.contains("offline fallback"))
        assertTrue(text == "local answer")
    }

    @Test
    fun `cloud timeout falls back and marks offline fallback`() = runTest {
        coEvery { geminiAgent.chatInference(any(), any(), any(), any(), any()) } throws
            Exception("timeout simulated")
        coEvery { modelPicker.runLiteRTInference(any()) } returns "offline answer"

        val (_, modelUsed) = router.run("hello")

        assertTrue(modelUsed.contains("offline fallback"))
    }

    @Test
    fun `when LiteRT also fails fallback modelUsed still contains offline fallback`() = runTest {
        coEvery { geminiAgent.chatInference(any(), any(), any(), any(), any()) } throws
            RuntimeException("network error")
        coEvery { modelPicker.runLiteRTInference(any()) } throws RuntimeException("no model")

        val (_, modelUsed) = router.run("hello")

        assertTrue(modelUsed.contains("offline fallback"))
    }

    @Test
    fun `runWithHistory cloud exception falls back to local`() = runTest {
        val history = listOf(com.alex.a2ndbrain.core.agents.AgentMessage("user", "what day is it?"))
        coEvery { geminiAgent.chatMultiTurn(any(), any(), any(), any(), any()) } throws
            RuntimeException("cloud down")
        coEvery { modelPicker.runLiteRTInference(any()) } returns "offline multi-turn answer"

        val (text, modelUsed) = router.runWithHistory(history)

        assertTrue(modelUsed.contains("offline fallback"))
        assertTrue(text == "offline multi-turn answer")
    }

    @Test
    fun `LiteRT model path never marks offline fallback`() = runTest {
        every { modelPicker.getBestModel() } returns ModelPicker.ModelType.LITERT_LOCAL
        coEvery { modelPicker.runLiteRTInference(any()) } returns "on-device answer"

        val (_, modelUsed) = router.run("hello")

        assertFalse(modelUsed.contains("offline fallback"))
    }
}
