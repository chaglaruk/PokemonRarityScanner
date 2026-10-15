package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.FrameOcr
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.OcrFrameResult
import com.pokerarity.scanner.util.ocr.ScreenClassificationResult
import com.pokerarity.scanner.util.ocr.ScreenRouteAction
import com.pokerarity.scanner.util.ocr.ScreenStateRouter
import com.pokerarity.scanner.util.ocr.ScreenType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/**
 * Phase 2A OCR-skip proof: the routing seam runs BEFORE normal species OCR, so a terminal
 * non-detail route causes zero OCR invocations and a valid detail route exactly one.
 * The seam is the production frame step, not a parallel approximation.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScanManagerFrameRoutingTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun classification(screenType: ScreenType) = ScreenClassificationResult(
        screenType = screenType,
        confidence = 0.95f,
        reasons = listOf("routing_test"),
        anchors = emptyList(),
        safeFallback = false
    )

    private fun frameInput() = ScanManager.DecodedFrame(
        index = 0,
        path = "routing-test-frame",
        bitmap = Bitmap.createBitmap(64, 128, Bitmap.Config.ARGB_8888),
        cpQuality = 0.9,
        pooled = false
    )

    private fun ocrResult(): OcrFrameResult {
        val pokemon = PokemonData(
            cp = 150, hp = 61, maxHp = 61, name = "Weedle", realName = "Weedle",
            candyName = "Weedle", megaEnergy = null, weight = null, height = null,
            stardust = null, caughtDate = null
        )
        val diagnostic = FrameDiagnostic(
            frameIndex = 0,
            imageWidth = 64,
            imageHeight = 128,
            screenState = ScreenType.PokemonDetail.name,
            fieldCandidates = listOf(
                FieldCandidateDiagnostic(
                    field = "NameTextual", source = "routing_test", rawText = "Weedle",
                    parsedValue = "Weedle", status = "found", candidateScore = 0.95f,
                    winner = true, reason = "exact_canonical", selectedValue = "Weedle")
            ),
            selected = com.pokerarity.scanner.util.ocr.PokemonSummary.from(pokemon)
        )
        return OcrFrameResult(pokemon, diagnostic)
    }

    private class CountingOcr(
            val invocationsCounter: MutableList<Int>,
            val result: () -> OcrFrameResult
        ) : FrameOcr {
        val invocations: Int get() = invocationsCounter.size

        val requests = mutableListOf<FrameOcrRequest>()
        override suspend fun recognize(request: FrameOcrRequest): OcrFrameResult {
            invocationsCounter.add(1)
            requests.add(request)
            return result()
        }
    }

    @Test
    fun terminalNonDetailRouteSkipsOcrCompletely() = runBlocking {
        val manager = ScanManager(context)
        val invocations = mutableListOf<Int>()
        val ocr = CountingOcr(invocations) { ocrResult() }
        manager.screenRouter = ScreenStateRouter { _ -> classification(ScreenType.StorageList) }
        manager.frameOcr = ocr

        val results = mutableListOf<ScanFrameCandidate>()
        val diagnostics = mutableListOf<FrameDiagnostic>()
        val routes = mutableListOf<FrameRouteDiagnostic>()
        val shouldStop = manager.processRoutedFrame(frameInput(), results, diagnostics, routes)

        assertEquals(0, ocr.invocations)
        assertEquals(0, results.size)
        assertEquals(0, diagnostics.size)
        assertEquals(1, routes.size)
        assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, routes.single().action)
        assertEquals(ScreenType.StorageList.name, routes.single().screenType)
        assertFalse(shouldStop)
    }

    @Test
    fun validDetailRouteInvokesOcrExactlyOnce() = runBlocking {
        val manager = ScanManager(context)
        val invocations = mutableListOf<Int>()
        val ocr = CountingOcr(invocations) { ocrResult() }
        manager.screenRouter = ScreenStateRouter { _ -> classification(ScreenType.PokemonDetail) }
        manager.frameOcr = ocr

        val results = mutableListOf<ScanFrameCandidate>()
        val diagnostics = mutableListOf<FrameDiagnostic>()
        val routes = mutableListOf<FrameRouteDiagnostic>()
        val shouldStop = manager.processRoutedFrame(frameInput(), results, diagnostics, routes)

        assertEquals(1, ocr.invocations)
        assertEquals(1, results.size)
        assertEquals(1, diagnostics.size)
        assertEquals(1, routes.size)
        assertEquals(ScreenRouteAction.PROCEED_DETAIL, routes.single().action)
        assertFalse(shouldStop)
    }

    @Test
    fun transitionAndUnknownRoutesNeverReachOcr() = runBlocking {
        for (screenType in listOf(ScreenType.Transition, ScreenType.Unknown)) {
            val manager = ScanManager(context)
            val invocations = mutableListOf<Int>()
        val ocr = CountingOcr(invocations) { ocrResult() }
            manager.screenRouter = ScreenStateRouter { _ -> classification(screenType) }
            manager.frameOcr = ocr

            val routes = mutableListOf<FrameRouteDiagnostic>()
            manager.processRoutedFrame(frameInput(), mutableListOf(), mutableListOf(), routes)

            assertEquals("screenType=$screenType", 0, ocr.invocations)
            assertEquals(screenType_transitionExpected(screenType), routes.single().action)
        }
    }

    private fun ambiguousStorageClassification() = ScreenClassificationResult(
        screenType = ScreenType.StorageList,
        confidence = 11f / 12f,
        reasons = listOf("measured_false_storage_grid"),
        anchors = listOf(
            com.pokerarity.scanner.util.ocr.ScreenAnchor(
                com.pokerarity.scanner.util.ocr.ScreenAnchorName.StorageGrid,
                Rect(0, 24, 64, 118), 11f / 12f, "repetitive_grid_cells"),
            com.pokerarity.scanner.util.ocr.ScreenAnchor(
                com.pokerarity.scanner.util.ocr.ScreenAnchorName.DetailCard,
                Rect(0, 38, 64, 90), .74f, "bright_panel"),
            com.pokerarity.scanner.util.ocr.ScreenAnchor(
                com.pokerarity.scanner.util.ocr.ScreenAnchorName.CpHeader,
                Rect(6, 8, 58, 16), .78f, "header"),
            com.pokerarity.scanner.util.ocr.ScreenAnchor(
                com.pokerarity.scanner.util.ocr.ScreenAnchorName.NameHpBand,
                Rect(6, 36, 58, 53), .70f, "band")
        ),
        safeFallback = true
    )

    @Test
    fun ambiguousStorageProbeCannotFuseWithoutContentWitness() = runBlocking {
        val manager = ScanManager(context)
        manager.screenRouter = ScreenStateRouter { _ -> ambiguousStorageClassification() }
        val calls = mutableListOf<Int>()
        val ocr = CountingOcr(calls) { ocrResult() } // default witness = false
        manager.frameOcr = ocr
        val results = mutableListOf<ScanFrameCandidate>()
        val diagnostics = mutableListOf<FrameDiagnostic>()
        val routes = mutableListOf<FrameRouteDiagnostic>()

        val shouldStop = manager.processRoutedFrame(frameInput(), results, diagnostics, routes)

        assertEquals(1, ocr.invocations)
        assertEquals("storage_detail_probe", ocr.requests.single().frameRole)
        assertEquals(null, ocr.requests.single().calibration)
        assertEquals(0, results.size)
        assertEquals(0, diagnostics.size)
        assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, routes.single().action)
        assertEquals("storage_detail_ui_not_corroborated", routes.single().reason)
        assertFalse(shouldStop)
    }

    @Test
    fun corroboratedStorageProbeEntersNormalFusionOnceWithoutCalibration() = runBlocking {
        val manager = ScanManager(context)
        manager.screenRouter = ScreenStateRouter { _ -> ambiguousStorageClassification() }
        val calls = mutableListOf<Int>()
        val ocr = CountingOcr(calls) { ocrResult().copy(detailStorageUiCorroborated = true) }
        manager.frameOcr = ocr
        val results = mutableListOf<ScanFrameCandidate>()
        val diagnostics = mutableListOf<FrameDiagnostic>()
        val routes = mutableListOf<FrameRouteDiagnostic>()

        manager.processRoutedFrame(frameInput(), results, diagnostics, routes)

        assertEquals(1, ocr.invocations)
        assertEquals(1, results.size)
        assertEquals(1, diagnostics.size)
        assertEquals(1, routes.size)
        assertEquals(ScreenRouteAction.PROCEED_DETAIL, routes.single().action)
        assertEquals("storage_detail_ui_corroborated", routes.single().reason)
        assertEquals(null, ocr.requests.single().calibration)
        assertEquals(null, results.single().recognitionContext?.calibrationHint)
    }

    @Test
    fun fullGridStorageProbesButCannotFuseWithoutIndependentContentWitness() = runBlocking {
        val manager = ScanManager(context)
        manager.screenRouter = ScreenStateRouter { _ ->
            ambiguousStorageClassification().copy(
                confidence = 1f,
                anchors = ambiguousStorageClassification().anchors.map {
                    if (it.name == com.pokerarity.scanner.util.ocr.ScreenAnchorName.StorageGrid)
                        it.copy(confidence = 1f) else it
                })
        }
        val calls = mutableListOf<Int>()
        // A synthetic OCR name/CP/HP alone is never a same-document UI witness.
        val ocr = CountingOcr(calls) { ocrResult() }
        manager.frameOcr = ocr
        val results = mutableListOf<ScanFrameCandidate>()
        val diagnostics = mutableListOf<FrameDiagnostic>()
        val routes = mutableListOf<FrameRouteDiagnostic>()

        val shouldStop = manager.processRoutedFrame(frameInput(), results, diagnostics, routes)

        assertEquals(1, ocr.invocations)
        assertEquals("storage_detail_probe", ocr.requests.single().frameRole)
        assertEquals(null, ocr.requests.single().calibration)
        assertEquals(0, results.size)
        assertEquals(0, diagnostics.size)
        assertEquals(1, routes.size)
        assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, routes.single().action)
        assertEquals("storage_detail_ui_not_corroborated", routes.single().reason)
        assertFalse(shouldStop)
    }

    private fun screenType_transitionExpected(screenType: ScreenType) = when (screenType) {
        ScreenType.Transition -> ScreenRouteAction.RETRY_UNSTABLE
        else -> ScreenRouteAction.RETRY_UNKNOWN
    }

}
