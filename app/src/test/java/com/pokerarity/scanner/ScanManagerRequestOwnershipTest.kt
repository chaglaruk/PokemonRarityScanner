// Purpose: Phase 2F ScanManager-level stale-publication and ownership propagation tests.
package com.pokerarity.scanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.service.FrameOcr
import com.pokerarity.scanner.service.OverlayStateStore
import com.pokerarity.scanner.service.OverlayState
import com.pokerarity.scanner.service.RequestAcceptance
import com.pokerarity.scanner.service.RequestOrigin
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.ScanPublicationSink
import com.pokerarity.scanner.service.ScanRarityInputs
import com.pokerarity.scanner.service.ScanRequestToken
import com.pokerarity.scanner.service.ScanRequests
import com.pokerarity.scanner.service.ScreenCaptureService
import com.pokerarity.scanner.service.parseCaptureSequenceId
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
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.ConscryptMode

/**
 * Phase 2F ScanManager-level integration tests: they reproduce the real stale-result
 * defect class through the production pipeline (request A held inside the frame OCR
 * seam while request B is accepted; A resumes and must not publish), plus the
 * capture→pipeline ownership propagation and fail-closed intake rules. All waits are
 * barriers or bounded state-transition polls — never timing luck.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScanManagerRequestOwnershipTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class RecordingSink : ScanPublicationSink {
        val overlayIntents = mutableListOf<Intent>()
        val saves = mutableListOf<String>()
        val telemetry = mutableListOf<String>()
        val firstSave = CompletableDeferred<Unit>()

        override fun showResultOverlay(intent: Intent) {
            overlayIntents.add(intent)
        }

        override suspend fun saveScan(pokemon: PokemonData, features: VisualFeatures, rarityScore: RarityScore) {
            saves.add(pokemon.name ?: "null")
            firstSave.complete(Unit)
        }

        override fun enqueueTelemetry(payload: com.pokerarity.scanner.service.TelemetryPayload) {
            telemetry.add(payload.pokemon.name ?: "null")
        }
    }

    private class GatedOcr(fixture: () -> OcrFrameResult) : FrameOcr {
        val invocations = mutableListOf<FrameOcrRequest>()
        val firstCallEntered = CompletableDeferred<Unit>()
        private val firstCallGate = CompletableDeferred<Unit>()
        var supplier: () -> OcrFrameResult = fixture

        fun releaseFirstCall() {
            firstCallGate.complete(Unit)
        }

        override suspend fun recognize(request: FrameOcrRequest): OcrFrameResult {
            invocations.add(request)
            if (invocations.size == 1) {
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
        // Robolectric serves no packaged assets: seed the recognition snapshot holder
        // from the checked-in generated assets so candy/family relations resolve.
        RecognitionSnapshotTestSupport.seedHolder()
        ScanRequests.resetForTest()
        OverlayStateStore.resetToIdle()
        sink = RecordingSink()
        screenshotFile = newScreenshotFile("scan_own_test.png")
    }

    @After
    fun cleanUp() {
        ScanRequests.resetForTest()
        OverlayStateStore.resetToIdle()
        screenshotFile.delete()
    }

    private fun newScreenshotFile(name: String): File {
        val bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xFF888888.toInt())
        val file = File(context.cacheDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    private fun acceptedToken(c: com.pokerarity.scanner.service.ScanRequestCoordinator): ScanRequestToken =
        (c.acceptRequest(RequestOrigin.USER) as RequestAcceptance.Accepted).token

    private fun readyIntent(token: ScanRequestToken, file: File): Intent =
        Intent(ScreenCaptureService.ACTION_SCREENSHOT_READY).apply {
            setPackage(context.packageName)
            putStringArrayListExtra(
                ScreenCaptureService.EXTRA_SCREENSHOT_PATHS,
                arrayListOf(file.absolutePath)
            )
            putOwnershipExtras(token)
            putCaptureSequenceExtra(token.requestId * 10)
        }

    private fun manager(ocr: FrameOcr): ScanManager {
        val manager = ScanManager(context)
        manager.screenRouter = ScreenStateRouter { _ ->
            ScreenClassificationResult(
                screenType = ScreenType.PokemonDetail,
                confidence = 0.95f,
                reasons = listOf("ownership_test"),
                anchors = emptyList(),
                safeFallback = false
            )
        }
        manager.frameOcr = ocr
        manager.rarityInputs = FakeRarityInputs()
        manager.telemetryUploadIdProvider = { "ownership-test-upload" }
        manager.publicationSink = sink
        return manager
    }

    private fun awaitStateTransition(timeoutMs: Long = 10_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!predicate()) {
            assertTrue("state transition not observed within ${timeoutMs}ms", System.currentTimeMillis() < deadline)
            Thread.sleep(10)
        }
    }

    // ── The real stale-result defect class, at the ScanManager level ──────

    @Test
    fun stalePipelinePublicationIsSuppressedAndNewerRequestPublishesExactlyOnce() = runBlocking {
        val coordinator = ScanRequests.coordinator
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)

        val a = acceptedToken(coordinator)
        assertTrue(manager.handleScreenshotReadyBroadcast(readyIntent(a, screenshotFile)))
        withTimeout(10_000) { ocr.firstCallEntered.await() }

        // Newer USER intent B accepted while A is held inside the frame OCR seam.
        val b = acceptedToken(coordinator)
        assertFalse("A must lose publication rights on B's acceptance", coordinator.hasPublicationRights(a))
        ocr.releaseFirstCall()

        awaitStateTransition {
            coordinator.snapshot(a)?.terminalOutcome == com.pokerarity.scanner.service.TerminalOutcome.STALE_SUPPRESSED
        }
        assertEquals(0, sink.overlayIntents.size)
        assertEquals(0, sink.saves.size)
        assertEquals(0, sink.telemetry.size)

        assertTrue(manager.handleScreenshotReadyBroadcast(readyIntent(b, screenshotFile)))
        withTimeout(10_000) { sink.firstSave.await() }
        // The overlay is dispatched on Dispatchers.Main; drain the paused Robolectric
        // main looper deterministically before asserting publication counts.
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("exactly one overlay publication", 1, sink.overlayIntents.size)
        assertEquals("exactly one history save", 1, sink.saves.size)
        assertEquals("exactly one telemetry enqueue", 1, sink.telemetry.size)
        assertEquals("B published, not A", "Weedle", sink.saves.single())
        assertEquals(
            com.pokerarity.scanner.service.TerminalOutcome.STALE_SUPPRESSED,
            coordinator.snapshot(a)?.terminalOutcome
        )
        assertEquals(
            com.pokerarity.scanner.service.TerminalOutcome.SUCCESS_PUBLISHED,
            coordinator.snapshot(b)?.terminalOutcome
        )
    }

    @Test
    fun staleFailureDoesNotEmitLateErrorOrRetryOverNewerRequest() = runBlocking {
        val coordinator = ScanRequests.coordinator
        val ocr = GatedOcr { namelessFixture() }
        val manager = manager(ocr)

        val a = acceptedToken(coordinator)
        assertTrue(manager.handleScreenshotReadyBroadcast(readyIntent(a, screenshotFile)))
        withTimeout(10_000) { ocr.firstCallEntered.await() }

        val b = acceptedToken(coordinator)
        ocr.releaseFirstCall()

        awaitStateTransition {
            coordinator.snapshot(a)?.terminalOutcome == com.pokerarity.scanner.service.TerminalOutcome.STALE_SUPPRESSED
        }

        // A's failing pipeline was suppressed: no retry traffic, no error state change.
        val requests = shadowOf(context as android.app.Application).broadcastIntents
            .count { it.action == "com.pokerarity.scanner.CAPTURE_REQUESTED" }
        assertEquals(0, requests)
        assertEquals(OverlayState.Idle, OverlayStateStore.state.value)

        // B still owns the pipeline and publishes normally (success fixture for B).
        ocr.supplier = { weedleFixture() }
        assertTrue(manager.handleScreenshotReadyBroadcast(readyIntent(b, screenshotFile)))
        withTimeout(10_000) { sink.firstSave.await() }
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(1, sink.saves.size)
        assertEquals(1, sink.overlayIntents.size)
    }

    // ── Capture → pipeline ownership propagation / fail-closed intake ────

    @Test
    fun intakeRejectsUnownedUnknownAndOldEpochScreenshotReady() = runBlocking {
        val coordinator = ScanRequests.coordinator
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)

        // Missing ownership metadata: fail closed.
        val unowned = Intent(ScreenCaptureService.ACTION_SCREENSHOT_READY).apply {
            setPackage(context.packageName)
            putStringArrayListExtra(
                ScreenCaptureService.EXTRA_SCREENSHOT_PATHS,
                arrayListOf(screenshotFile.absolutePath)
            )
        }
        assertFalse(manager.handleScreenshotReadyBroadcast(unowned))
        assertEquals(0, ocr.invocations.size)

        // Unknown request id: rejected.
        val rogue = acceptedToken(coordinator).copy(requestId = 99_999L)
        assertFalse(manager.handleScreenshotReadyBroadcast(readyIntent(rogue, screenshotFile)))
        assertEquals(0, ocr.invocations.size)

        // Old projection epoch: rejected.
        val staleEpoch = acceptedToken(coordinator).let { original ->
            original.copy(projectionEpoch = original.projectionEpoch + 5)
        }
        assertFalse(manager.handleScreenshotReadyBroadcast(readyIntent(staleEpoch, screenshotFile)))
        assertEquals(0, ocr.invocations.size)
    }

    @Test
    fun intakeRejectsOwnedScreenshotReadyWithoutCaptureSequence() = runBlocking {
        val coordinator = ScanRequests.coordinator
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)
        val token = acceptedToken(coordinator)
        val missingSequence = readyIntent(token, screenshotFile).apply {
            removeExtra("extra_capture_sequence_id")
        }

        assertFalse(manager.handleScreenshotReadyBroadcast(missingSequence))
        assertEquals(0, ocr.invocations.size)
        assertTrue("missing capture metadata must not terminalize the valid request", coordinator.isLiveRequest(token))
    }

    @Test
    fun intakeRejectsOldAttemptAfterRetryAdvancesTheLogicalRequest() = runBlocking {
        val coordinator = ScanRequests.coordinator
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)
        val attempt1 = acceptedToken(coordinator)
        val attempt2 = coordinator.acceptRetry(attempt1)!!

        assertFalse(manager.handleScreenshotReadyBroadcast(readyIntent(attempt1, screenshotFile)))
        assertEquals(0, ocr.invocations.size)
        assertTrue("late attempt 1 must not kill attempt 2", coordinator.isLiveRequest(attempt2))
        assertTrue(coordinator.hasPublicationRights(attempt2))
    }

    @Test
    fun ownedRequestPropagatesThroughIntakeIntoPipelineAndDiagnostics() = runBlocking {
        val coordinator = ScanRequests.coordinator
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)

        val a = acceptedToken(coordinator)
        val intent = readyIntent(a, screenshotFile)
        assertTrue(manager.handleScreenshotReadyBroadcast(intent))
        withTimeout(10_000) { ocr.firstCallEntered.await() }

        // The token that entered the intake reached the production pipeline seam.
        assertEquals(1, ocr.invocations.size)
        ocr.releaseFirstCall()

        awaitStateTransition {
            coordinator.snapshot(a)?.terminalOutcome != null
        }
        val snapshot = coordinator.snapshot(a)
        assertNotNull(snapshot)
        assertEquals(a.requestId, snapshot?.requestId)
        assertEquals(1, snapshot?.attemptId)
        assertEquals(a.projectionEpoch, snapshot?.projectionEpoch)
        assertEquals(a.requestId * 10, snapshot?.captureSequenceId)
    }

    @Test
    fun retryRebroadcastCarriesSameRequestIdAndIncrementedAttempt() = runBlocking {
        val coordinator = ScanRequests.coordinator
        val ocr = GatedOcr { namelessFixture() }
        val manager = manager(ocr)

        val a = acceptedToken(coordinator)
        assertTrue(manager.handleScreenshotReadyBroadcast(readyIntent(a, screenshotFile)))
        withTimeout(10_000) { ocr.firstCallEntered.await() }
        ocr.releaseFirstCall()

        // Failing pipeline with no newer request: the retry belongs to the SAME request.
        awaitStateTransition {
            shadowOf(context as android.app.Application).broadcastIntents
                .any { it.action == "com.pokerarity.scanner.CAPTURE_REQUESTED" }
        }
        val broadcasts = shadowOf(context as android.app.Application).broadcastIntents
            .filter { it.action == "com.pokerarity.scanner.CAPTURE_REQUESTED" }
        assertEquals(1, broadcasts.size)
        val retryOwnership = broadcasts.single().parseOwnership()
        assertNotNull(retryOwnership)
        assertEquals(a.requestId, retryOwnership?.requestId)
        assertEquals(2, retryOwnership?.attemptId)
        assertEquals(RequestOrigin.RETRY, retryOwnership?.origin)
    }

    // ── Fixtures ──────────────────────────────────────────────────────────

    private fun weedlePokemon(): PokemonData = PokemonData(
        cp = 300, hp = 80, maxHp = 80, name = "Weedle", realName = "Weedle",
        candyName = "Weedle", megaEnergy = null, weight = null, height = null,
        stardust = null, caughtDate = null,
        rawOcrText = "Name:Weedle|CP:300|HP:80/80|Candy:Weedle Candy",
        // Anchored-path fixture: JVM tests have no packaged assets, so the legacy
        // base-stats evaluator and the refiner's parser must not run. The anchored
        // observation routes identity through the (fail-closed) snapshot seam and the
        // textual NameTextual candidate carries the exact-canonical hard authority.
        recognitionObservation = com.pokerarity.scanner.util.ocr.RecognitionObservation(
            candySpecies = "Weedle",
            powerUpStardust = null,
            types = setOf("bug", "poison"),
            detailScreen = true
        )
    )

    private fun weedleFixture(): OcrFrameResult {
        val pokemon = weedlePokemon()
        val diagnostic = FrameDiagnostic(
            frameIndex = 0,
            imageWidth = 64,
            imageHeight = 128,
            screenState = ScreenType.PokemonDetail.name,
            screenConfidence = 0.95f,
            fieldCandidates = listOf(
                FieldCandidateDiagnostic(
                    field = "NameTextual", source = "ownership_test", rawText = "Weedle",
                    parsedValue = "Weedle", status = "found", candidateScore = 0.95f,
                    winner = true, reason = "exact_canonical", selectedValue = "Weedle"
                ),
                FieldCandidateDiagnostic(
                    field = "CP", source = "ownership_test", rawText = pokemon.cp.toString(),
                    parsedValue = pokemon.cp.toString(), status = "found", candidateScore = 0.9f,
                    winner = true, reason = "cp_parser"
                ),
                FieldCandidateDiagnostic(
                    field = "HP", source = "ownership_test",
                    rawText = "${pokemon.maxHp}/${pokemon.maxHp}",
                    parsedValue = "${pokemon.maxHp}/${pokemon.maxHp}", status = "found",
                    candidateScore = 0.9f, winner = true, reason = "hp_parser"
                )
            ),
            selected = com.pokerarity.scanner.util.ocr.PokemonSummary.from(pokemon)
        )
        return OcrFrameResult(pokemon, diagnostic)
    }

    private fun namelessFixture(): OcrFrameResult {
        val pokemon = PokemonData(
            cp = 150, hp = 61, maxHp = 61, name = null, realName = null,
            candyName = null, megaEnergy = null, weight = null, height = null,
            stardust = null, caughtDate = null, rawOcrText = "CP:150|HP:61/61"
        )
        val diagnostic = FrameDiagnostic(
            frameIndex = 0,
            imageWidth = 64,
            imageHeight = 128,
            screenState = ScreenType.PokemonDetail.name,
            fieldCandidates = emptyList(),
            selected = com.pokerarity.scanner.util.ocr.PokemonSummary.from(pokemon)
        )
        return OcrFrameResult(pokemon, diagnostic)
    }
}
