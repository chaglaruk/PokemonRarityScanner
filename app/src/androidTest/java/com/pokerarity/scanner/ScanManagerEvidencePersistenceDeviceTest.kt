// F1: controlled optional writer outcomes through real publication and persistence.
package com.pokerarity.scanner

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
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
import com.pokerarity.scanner.service.putCaptureSequenceExtra
import com.pokerarity.scanner.service.putOwnershipExtras
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.OcrFrameResult
import com.pokerarity.scanner.util.ocr.ScreenClassificationResult
import com.pokerarity.scanner.util.ocr.ScreenStateRouter
import com.pokerarity.scanner.util.ocr.ScreenType
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.Room

@RunWith(AndroidJUnit4::class)
class ScanManagerEvidencePersistenceDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private class RecordingSink(private val dao: com.pokerarity.scanner.data.local.db.ScanHistoryDao) : ScanPublicationSink {
        val overlayIntents = java.util.concurrent.CopyOnWriteArrayList<Intent>()
        val saves = java.util.concurrent.CopyOnWriteArrayList<PokemonData>()
        val telemetry = java.util.concurrent.CopyOnWriteArrayList<String>()
        val firstSave = CompletableDeferred<Unit>()

        override fun showResultOverlay(intent: Intent) {
            overlayIntents.add(intent)
        }

        override suspend fun saveScan(pokemon: PokemonData, features: VisualFeatures, rarityScore: RarityScore) {
            dao.insert(com.pokerarity.scanner.data.repository.ScanHistoryMapper.toEntity(pokemon, features, rarityScore))
            saves.add(pokemon)
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

    private lateinit var database: com.pokerarity.scanner.data.local.db.AppDatabase
    private val managers = mutableListOf<ScanManager>()
    private lateinit var sink: RecordingSink
    private lateinit var screenshotFile: File

    @Before
    fun prepare() {
        // Robolectric serves no packaged assets: seed the recognition snapshot holder
        // from the checked-in generated assets so candy/family relations resolve.
        // Packaged recognition assets are loaded by the real Android holder.
        ScanRequests.resetForTest()
        OverlayStateStore.resetToIdle()
        com.pokerarity.scanner.service.OwnedFrameHandoff.enabled = true
        ScanRequests.coordinator.onScannerLifecycle(true)
        database = Room.inMemoryDatabaseBuilder(context,
            com.pokerarity.scanner.data.local.db.AppDatabase::class.java).build()
        sink = RecordingSink(database.scanHistoryDao())
        screenshotFile = newScreenshotFile("scan_own_test.png")
    }

    @After
    fun cleanUp() {
        managers.forEach { it.stop() }
        database.close()
        ScanRequests.resetForTest()
        OverlayStateStore.resetToIdle()
        screenshotFile.delete()
        com.pokerarity.scanner.service.OwnedFrameHandoff.invalidate()
        com.pokerarity.scanner.service.OwnedFrameHandoff.enabled = false
        File(context.cacheDir, "scan_writer_failure.png").delete()
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
        managers.add(manager)
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

    private enum class Outcome(val status: String) {
        SUCCESS("WRITTEN"), IO_FAILURE("FAILED"), EXCEPTIONAL("FAILED"),
        COMPRESSION_FAILURE("FAILED"), FALSE_COMPLETION("FAILED"),
        PENDING("PENDING"), LATE_FAILURE("PENDING"), TIMEOUT("TIMED_OUT"), CANCELLED("CANCELLED"), MISSING("UNAVAILABLE")
    }

    private fun frame(path: String): Any {
        val registry = com.pokerarity.scanner.service.OwnedFrameHandoff::class.java
            .getDeclaredField("frames").apply { isAccessible = true }.get(null) as Map<*, *>
        return registry[path]!!
    }

    private fun writer(): java.util.concurrent.ThreadPoolExecutor =
        com.pokerarity.scanner.service.OwnedFrameHandoff::class.java.getDeclaredField("writer")
            .apply { isAccessible = true }.get(null) as java.util.concurrent.ThreadPoolExecutor

    private fun capture(token: ScanRequestToken, destination: File): String {
        val pixels = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888)
        return try {
            com.pokerarity.scanner.service.OwnedFrameHandoff.capture(
                pixels, destination, token, token.requestId * 10, 0
            )
        } finally { pixels.recycle() }
    }

    private fun ownedIntent(token: ScanRequestToken, path: String) = readyIntent(token, File(path)).apply {
        putExtra(ScreenCaptureService.EXTRA_OWNED_FRAMES, true)
    }

    private fun assertReleased() {
        awaitStateTransition {
            val m = com.pokerarity.scanner.service.OwnedFrameHandoff.metrics()
            listOf("liveSourceFrames", "liveSourceBytes", "liveReaderLeases", "managedJobs",
                "liveTaskCopies", "liveTaskBytes", "pendingNativeTasks", "evidenceQueueSize")
                .all { m[it] == 0L }
        }
    }

    private fun runOutcome(outcome: Outcome) = runBlocking {
        val token = acceptedToken(ScanRequests.coordinator)
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)
        val destination = File(context.cacheDir, "scan_f1_${outcome.name}.png")
        if (outcome == Outcome.IO_FAILURE || outcome == Outcome.LATE_FAILURE) destination.mkdirs()
        val gate = java.util.concurrent.CountDownLatch(1)
        val entered = java.util.concurrent.CountDownLatch(1)
        val blocked = outcome == Outcome.PENDING || outcome == Outcome.TIMEOUT || outcome == Outcome.LATE_FAILURE
        if (blocked) {
            writer().execute { entered.countDown(); gate.await(10, TimeUnit.SECONDS) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
        }
        try {
            val path = capture(token, destination)
            if (!blocked) {
                try {
                    com.pokerarity.scanner.service.OwnedFrameHandoff.awaitEvidence(path)
                    assertTrue(outcome != Outcome.IO_FAILURE)
                } catch (failure: java.util.concurrent.ExecutionException) {
                    assertEquals(Outcome.IO_FAILURE, outcome)
                    assertTrue(failure.cause is java.io.IOException)
                }
            }
            assertTrue(manager.handleScreenshotReadyBroadcast(ownedIntent(token, path)))
            withTimeout(10_000) { ocr.firstCallEntered.await() }
            val record = frame(path)
            when (outcome) {
                Outcome.EXCEPTIONAL, Outcome.COMPRESSION_FAILURE, Outcome.FALSE_COMPLETION, Outcome.CANCELLED -> {
                    val future = java.util.concurrent.CompletableFuture<Boolean>()
                    when (outcome) {
                        Outcome.FALSE_COMPLETION -> future.complete(false)
                        Outcome.CANCELLED -> future.cancel(false)
                        Outcome.COMPRESSION_FAILURE -> future.completeExceptionally(IllegalStateException("compress false"))
                        else -> future.completeExceptionally(java.io.IOException("controlled writer failure"))
                    }
                    record.javaClass.getDeclaredField("written").apply { isAccessible = true }.set(record, future)
                }
                Outcome.TIMEOUT -> record.javaClass.getDeclaredField("evidenceDeadlineNanos")
                    .apply { isAccessible = true }.setLong(record, 0L)
                Outcome.MISSING -> {
                    val owner = com.pokerarity.scanner.service.OwnedFrameHandoff
                    val lock = requireNotNull(owner.javaClass.getDeclaredField("lock").apply { isAccessible = true }.get(null))
                    synchronized(lock) {
                        val records = owner.javaClass.getDeclaredField("frames").apply { isAccessible = true }
                            .get(null) as MutableMap<*, *>
                        records.remove(path)
                        owner.javaClass.getDeclaredMethod("closeRoot", record.javaClass)
                            .apply { isAccessible = true }.invoke(owner, record)
                    }
                }
                else -> Unit
            }
            ocr.releaseFirstCall()
            // This must complete while a real writer is still blocked. No 30-second wait.
            withTimeout(5_000) { sink.firstSave.await() }
            awaitStateTransition { com.pokerarity.scanner.service.OwnedFrameHandoff.metrics()["managedJobs"] == 0L }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(com.pokerarity.scanner.service.TerminalOutcome.SUCCESS_PUBLISHED,
                ScanRequests.coordinator.snapshot(token)?.terminalOutcome)
            assertEquals(1, sink.overlayIntents.size)
            assertEquals("F1: SUCCESS_PUBLISHED/card scheduled but zero Room save", 1, sink.saves.size)
            assertEquals(1, sink.telemetry.size)
            assertEquals("actual Room row count", 1, database.scanHistoryDao().count())
            assertEquals(300, database.scanHistoryDao().getByPokemonName("Weedle").single().cp)
            assertEquals(outcome.status, sink.saves.single().screenshotEvidenceStatus)
            assertEquals(weedlePokemon().rawOcrText, sink.saves.single().rawOcrText)
            if (outcome == Outcome.SUCCESS) {
                assertTrue(destination.isFile)
                assertTrue(sink.saves.single().ocrDiagnosticsFiles.values.all { File(it).isFile })
                assertTrue(sink.saves.single().ocrDiagnosticsFiles.isNotEmpty())
            } else {
                assertTrue(sink.saves.single().ocrDiagnosticsFiles.isEmpty())
                assertNull(sink.saves.single().ocrDiagnosticsDir)
            }
            if (blocked) {
                assertEquals(1L, com.pokerarity.scanner.service.OwnedFrameHandoff.metrics()["liveSourceFrames"])
            }
        } finally {
            gate.countDown()
            assertReleased()
            destination.delete()
        }
        assertEquals(1, sink.saves.size)
        assertEquals(1, sink.telemetry.size)
        assertEquals(com.pokerarity.scanner.service.TerminalOutcome.SUCCESS_PUBLISHED,
            ScanRequests.coordinator.snapshot(token)?.terminalOutcome)
    }

    @Test fun lateWriterFailureCannotRetryPublishedSuccess() = runOutcome(Outcome.LATE_FAILURE)
    @Test fun successfulWriterPreservesDiagnosticsAndPersistence() = runOutcome(Outcome.SUCCESS)
    @Test fun failedOwnedWriterStillSavesAcceptedResultExactlyOnce() = runOutcome(Outcome.IO_FAILURE)
    @Test fun exceptionalFutureCannotVetoPersistence() = runOutcome(Outcome.EXCEPTIONAL)
    @Test fun compressionFailureCannotVetoPersistence() = runOutcome(Outcome.COMPRESSION_FAILURE)
    @Test fun falseCompletionIsNotSuccessfulEvidence() = runOutcome(Outcome.FALSE_COMPLETION)
    @Test fun pendingWriterDoesNotBlockCardOrSave() = runOutcome(Outcome.PENDING)
    @Test fun timedOutOwnedWriterStillSavesAcceptedResultExactlyOnce() = runOutcome(Outcome.TIMEOUT)
    @Test fun cancelledEvidenceIsExplicitAndDoesNotCancelAcceptedHistory() = runOutcome(Outcome.CANCELLED)
    @Test fun missingRegistrationIsNotSuccessfulEvidence() = runOutcome(Outcome.MISSING)

    @Test fun staleOwnedFailureCannotPublishOverNewerRequest() = runBlocking {
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)
        val a = acceptedToken(ScanRequests.coordinator)
        val bad = File(context.cacheDir, "scan_f1_stale.png").apply { mkdirs() }
        assertTrue(manager.handleScreenshotReadyBroadcast(ownedIntent(a, capture(a, bad))))
        withTimeout(10_000) { ocr.firstCallEntered.await() }
        val b = acceptedToken(ScanRequests.coordinator)
        ocr.releaseFirstCall()
        awaitStateTransition { com.pokerarity.scanner.service.OwnedFrameHandoff.metrics()["managedJobs"] == 0L }
        assertEquals(com.pokerarity.scanner.service.TerminalOutcome.STALE_SUPPRESSED,
            ScanRequests.coordinator.snapshot(a)?.terminalOutcome)
        assertEquals(0, sink.saves.size)
        assertEquals(0, database.scanHistoryDao().count())
        assertTrue(manager.handleScreenshotReadyBroadcast(readyIntent(b, screenshotFile)))
        withTimeout(10_000) { sink.firstSave.await() }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(1, sink.saves.size)
        assertEquals(1, database.scanHistoryDao().count())
        assertEquals(1, sink.overlayIntents.size)
        assertEquals(1, sink.telemetry.size)
        assertNull(sink.saves.single().screenshotEvidenceStatus)
        assertReleased()
        bad.delete()
        Unit
    }

    @Test fun cancellingOwnedRecognitionReleasesAllLeasesWithoutPublication() = runBlocking {
        val ocr = GatedOcr { weedleFixture() }
        val manager = manager(ocr)
        val token = acceptedToken(ScanRequests.coordinator)
        val destination = File(context.cacheDir, "scan_f1_cancel.png")
        assertTrue(manager.handleScreenshotReadyBroadcast(ownedIntent(token, capture(token, destination))))
        withTimeout(10_000) { ocr.firstCallEntered.await() }
        manager.stop()
        assertReleased()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(0, sink.saves.size)
        assertEquals(0, database.scanHistoryDao().count())
        assertEquals(0, sink.overlayIntents.size)
        assertEquals(0, sink.telemetry.size)
        destination.delete()
        Unit
    }

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

}
