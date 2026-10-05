// Purpose: Phase 2F on-device overlap/stale-request validation through the real pipeline.
package com.pokerarity.scanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.service.RequestAcceptance
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.ScanPublicationSink
import com.pokerarity.scanner.service.ScanRarityInputs
import com.pokerarity.scanner.service.ScanRequestToken
import com.pokerarity.scanner.service.ScanRequests
import com.pokerarity.scanner.service.ScreenCaptureService
import com.pokerarity.scanner.service.TerminalOutcome
import com.pokerarity.scanner.service.parseOwnership
import com.pokerarity.scanner.service.putCaptureSequenceExtra
import com.pokerarity.scanner.service.putOwnershipExtras
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OcrFrameResult
import com.pokerarity.scanner.util.ocr.ScreenClassificationResult
import com.pokerarity.scanner.util.ocr.ScreenStateRouter
import com.pokerarity.scanner.util.ocr.ScreenType
import com.pokerarity.scanner.util.vision.Phase2VariantClassifier
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase 2F bounded device validation: the stale-result defect class reproduced through
 * the REAL ScanManager pipeline on hardware — request A held inside the frame OCR seam
 * while request B is accepted; A must never publish, B publishes exactly once.
 * Deterministic barriers only (CompletableDeferred + CountDownLatch), no timing luck.
 */
@RunWith(AndroidJUnit4::class)
class ScanManagerOwnershipDeviceTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    private class RecordingSink : ScanPublicationSink {
        val overlay = CountDownLatch(1)
        val saves = mutableListOf<String>()
        val telemetry = mutableListOf<String>()
        private val firstSave = CountDownLatch(1)

        override fun showResultOverlay(intent: Intent) {
            overlay.countDown()
        }

        override suspend fun saveScan(pokemon: PokemonData, features: VisualFeatures, rarityScore: RarityScore) {
            synchronized(saves) { saves.add(pokemon.name ?: "null") }
            firstSave.countDown()
        }

        override fun enqueueTelemetry(payload: com.pokerarity.scanner.service.TelemetryPayload) {
            synchronized(telemetry) { telemetry.add(payload.pokemon.name ?: "null") }
        }

        fun awaitFirstSave() = firstSave.await(15, TimeUnit.SECONDS)
    }

    private class GatedOcr(private val fixture: () -> OcrFrameResult) : com.pokerarity.scanner.service.FrameOcr {
        val firstCallEntered = CompletableDeferred<Unit>()
        private val firstCallGate = CompletableDeferred<Unit>()
        @Volatile private var firstCallHandled = false
        var supplier: () -> OcrFrameResult = fixture

        fun releaseFirstCall() = firstCallGate.complete(Unit)

        override suspend fun recognize(request: FrameOcrRequest): OcrFrameResult {
            if (!firstCallHandled) {
                firstCallHandled = true
                firstCallEntered.complete(Unit)
                firstCallGate.await()
            }
            return supplier()
        }
    }

    private class FakeRarityInputs : ScanRarityInputs {
        override suspend fun baseRarity(name: String?): Int = 5
        override suspend fun eventBonus(pokemon: PokemonData, features: VisualFeatures): Int = 0
        override suspend fun liveEventContext(pokemon: PokemonData, features: VisualFeatures) = null
    }

    private lateinit var sink: RecordingSink
    private lateinit var screenshotFile: File

    @Before
    fun prepare() {
        ScanRequests.resetForTest()
        sink = RecordingSink()
        val bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF888888.toInt())
        screenshotFile = File(context.cacheDir, "scan_own_device.png")
        screenshotFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @After
    fun cleanUp() {
        ScanRequests.resetForTest()
        screenshotFile.delete()
    }

    private fun weedleFixture(): OcrFrameResult {
        val pokemon = PokemonData(
            cp = 300, hp = 80, maxHp = 80, name = "Weedle", realName = "Weedle",
            candyName = "Weedle", megaEnergy = null, weight = null, height = null,
            stardust = null, caughtDate = null,
            rawOcrText = "Name:Weedle|CP:300|HP:80/80|Candy:Weedle Candy",
            recognitionObservation = com.pokerarity.scanner.util.ocr.RecognitionObservation(
                candySpecies = "Weedle",
                powerUpStardust = null,
                types = setOf("bug", "poison"),
                detailScreen = true
            )
        )
        val diagnostic = FrameDiagnostic(
            frameIndex = 0,
            imageWidth = 64,
            imageHeight = 128,
            screenState = ScreenType.PokemonDetail.name,
            screenConfidence = 0.95f,
            fieldCandidates = listOf(
                FieldCandidateDiagnostic(
                    field = "NameTextual", source = "ownership_device_test", rawText = "Weedle",
                    parsedValue = "Weedle", status = "found", candidateScore = 0.95f,
                    winner = true, reason = "exact_canonical", selectedValue = "Weedle"
                ),
                FieldCandidateDiagnostic(
                    field = "CP", source = "ownership_device_test", rawText = "300",
                    parsedValue = "300", status = "found", candidateScore = 0.9f,
                    winner = true, reason = "cp_parser"
                ),
                FieldCandidateDiagnostic(
                    field = "HP", source = "ownership_device_test", rawText = "80/80",
                    parsedValue = "80/80", status = "found", candidateScore = 0.9f,
                    winner = true, reason = "hp_parser"
                )
            ),
            selected = com.pokerarity.scanner.util.ocr.PokemonSummary.from(pokemon)
        )
        return OcrFrameResult(pokemon, diagnostic)
    }

    private fun manager(ocr: GatedOcr): ScanManager {
        val manager = ScanManager(context)
        manager.screenRouter = ScreenStateRouter { _ ->
            ScreenClassificationResult(
                screenType = ScreenType.PokemonDetail,
                confidence = 0.95f,
                reasons = listOf("ownership_device_test"),
                anchors = emptyList(),
                safeFallback = false
            )
        }
        manager.frameOcr = ocr
        manager.rarityInputs = FakeRarityInputs()
        manager.telemetryUploadIdProvider = { "ownership-device-test" }
        manager.publicationSink = sink
        return manager
    }

    private fun acceptedToken(): ScanRequestToken =
        (ScanRequests.coordinator.acceptRequest(com.pokerarity.scanner.service.RequestOrigin.USER)
            as RequestAcceptance.Accepted).token

    private fun readyIntent(token: ScanRequestToken): Intent =
        Intent(ScreenCaptureService.ACTION_SCREENSHOT_READY).apply {
            setPackage(context.packageName)
            putStringArrayListExtra(
                ScreenCaptureService.EXTRA_SCREENSHOT_PATHS,
                arrayListOf(screenshotFile.absolutePath)
            )
            putOwnershipExtras(token)
            putCaptureSequenceExtra(token.requestId * 10)
        }

    @Test
    fun singleOwnedRequestPublishesExactlyOnce() = runBlocking {
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)
        val a = acceptedToken()

        assertTrue(manager.handleScreenshotReadyBroadcast(readyIntent(a)))
        kotlinx.coroutines.withTimeout(15_000) { ocr.firstCallEntered.await() }
        ocr.releaseFirstCall()

        assertTrue(sink.awaitFirstSave())
        sink.overlay.await(15, TimeUnit.SECONDS)
        assertEquals(1, synchronized(sink.saves) { sink.saves.size })
        assertEquals(1, synchronized(sink.telemetry) { sink.telemetry.size })
        assertEquals(
            TerminalOutcome.SUCCESS_PUBLISHED,
            ScanRequests.coordinator.snapshot(a)?.terminalOutcome
        )
    }

    @Test
    fun stalePipelineSuppressedAndNewerRequestPublishesExactlyOnce() = runBlocking {
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)
        val a = acceptedToken()

        assertTrue(manager.handleScreenshotReadyBroadcast(readyIntent(a)))
        kotlinx.coroutines.withTimeout(15_000) { ocr.firstCallEntered.await() }

        // Newer USER intent accepted while A is held inside the frame OCR seam.
        val b = acceptedToken()
        assertTrue(
            "A must lose publication rights immediately",
            !ScanRequests.coordinator.hasPublicationRights(a)
        )
        ocr.releaseFirstCall()

        assertTrue(
            "A must end stale-suppressed",
            awaitState { ScanRequests.coordinator.snapshot(a)?.terminalOutcome == TerminalOutcome.STALE_SUPPRESSED }
        )
        assertEquals(0, synchronized(sink.saves) { sink.saves.size })

        assertTrue(manager.handleScreenshotReadyBroadcast(readyIntent(b)))
        assertTrue(sink.awaitFirstSave())
        sink.overlay.await(15, TimeUnit.SECONDS)
        synchronized(sink.saves) {
            assertEquals("exactly one save, for B", listOf("Weedle"), sink.saves)
        }
        synchronized(sink.telemetry) {
            assertEquals("exactly one telemetry enqueue", 1, sink.telemetry.size)
        }
        assertEquals(
            TerminalOutcome.STALE_SUPPRESSED,
            ScanRequests.coordinator.snapshot(a)?.terminalOutcome
        )
        assertEquals(
            TerminalOutcome.SUCCESS_PUBLISHED,
            ScanRequests.coordinator.snapshot(b)?.terminalOutcome
        )
    }

    private fun awaitState(timeoutMs: Long = 15_000L, predicate: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(25)
        }
        return true
    }
}
