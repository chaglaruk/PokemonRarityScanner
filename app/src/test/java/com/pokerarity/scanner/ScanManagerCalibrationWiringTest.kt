package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.service.FrameOcr
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.AnchorDiagnostic
import com.pokerarity.scanner.util.ocr.CALIBRATED_BAR_ANCHOR_REASON
import com.pokerarity.scanner.util.ocr.CalibrationDiagnostic
import com.pokerarity.scanner.util.ocr.CalibrationResolution
import com.pokerarity.scanner.util.ocr.DisplayGeometrySignature
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.FrameCalibrationHint
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.NormalizedRect
import com.pokerarity.scanner.util.ocr.OcrFrameResult
import com.pokerarity.scanner.util.ocr.PokemonSummary
import com.pokerarity.scanner.util.ocr.ScreenAnchor
import com.pokerarity.scanner.util.ocr.ScreenAnchorName
import com.pokerarity.scanner.util.ocr.ScreenClassificationResult
import com.pokerarity.scanner.util.ocr.ScreenRouteAction
import com.pokerarity.scanner.util.ocr.ScreenStateRouter
import com.pokerarity.scanner.util.ocr.ScreenCalibrationManager
import com.pokerarity.scanner.util.ocr.ScreenCalibrationStore
import com.pokerarity.scanner.util.ocr.ScreenGeometry
import com.pokerarity.scanner.util.ocr.ScreenGeometryBuilder
import com.pokerarity.scanner.util.ocr.ScreenType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.Config

/**
 * Phase 2B wiring proof: persistent calibration participates in the REAL production frame
 * step ([ScanManager.processRoutedFrame]) — the router's classification feeds calibration,
 * the hint reaches the OCR seam, post-OCR anchor evidence validates the record, and a
 * stored calibration can never bypass the Phase 2A routing gates.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScanManagerCalibrationWiringTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val frameWidth = 300
    private val frameHeight = 650
    private val sourceWidth = 1080
    private val sourceHeight = 2340
    private val barRect = Rect(75, 294, 225, 298)
    private val cardTop = (frameHeight * 0.334f).toInt()

    private lateinit var store: ScreenCalibrationStore
    private lateinit var calibration: ScreenCalibrationManager

    @Before
    fun setUp() {
        context.getSharedPreferences("screen_calibration_geometry", Context.MODE_PRIVATE)
            .edit().clear().commit()
        store = ScreenCalibrationStore(context)
        calibration = ScreenCalibrationManager(store)
    }

    private fun classification(screenType: ScreenType) = ScreenClassificationResult(
        screenType = screenType,
        confidence = 0.9f,
        reasons = listOf("calibration_wiring_test"),
        anchors = if (screenType == ScreenType.PokemonDetail) {
            listOf(
                ScreenAnchor(
                    ScreenAnchorName.DetailCard,
                    Rect(0, cardTop, frameWidth, (frameHeight * 0.7f).toInt()),
                    0.74f,
                    "wiring_test_card"
                )
            )
        } else {
            emptyList()
        },
        safeFallback = false
    )

    private fun manager(
        ocr: CapturingOcr,
        builder: ScreenGeometryBuilder = ScreenGeometryBuilder(),
        classifyInvocations: MutableList<Int>? = null
    ): ScanManager {
        val manager = ScanManager(context)
        manager.screenRouter = ScreenStateRouter { _ ->
            classifyInvocations?.add(1)
            classification(ScreenType.PokemonDetail)
        }
        manager.screenGeometryBuilder = builder
        manager.screenCalibration = ScreenCalibrationManager(store)
        manager.frameOcr = ocr
        return manager
    }

    private fun frameInput(withSourceGeometry: Boolean = true): ScanManager.DecodedFrame {
        val bitmap = Bitmap.createBitmap(frameWidth, frameHeight, Bitmap.Config.ARGB_8888)
        return ScanManager.DecodedFrame(
            index = 0,
            path = "calibration-wiring-frame",
            bitmap = bitmap,
            cpQuality = 0.9,
            pooled = false,
            sourceWidth = if (withSourceGeometry) sourceWidth else 0,
            sourceHeight = if (withSourceGeometry) sourceHeight else 0
        )
    }

    private class CapturingOcr(
        private val emitLiveBar: Boolean,
        private val barRect: Rect
    ) : FrameOcr {
        val hints = mutableListOf<FrameCalibrationHint?>()
        val diagnostics = mutableListOf<FrameDiagnostic>()

        override suspend fun recognize(request: FrameOcrRequest): OcrFrameResult {
            val calibration = request.calibration
            hints += calibration
            val pokemon = PokemonData(
                cp = 150, hp = 61, maxHp = 61, name = "Weedle", realName = "Weedle",
                candyName = "Weedle", megaEnergy = null, weight = null, height = null,
                stardust = null, caughtDate = null
            )
            val bar = if (emitLiveBar) barRect else null
            val diagnostic = FrameDiagnostic(
                frameIndex = request.frameIndex,
                imageWidth = request.bitmap.width,
                imageHeight = request.bitmap.height,
                screenState = ScreenType.PokemonDetail.name,
                anchors = bar?.let {
                    listOf(
                        AnchorDiagnostic(
                            "hp_bar", it.left, it.top, it.right, it.bottom,
                            0.9f, "green_bar_on_white"
                        )
                    )
                }.orEmpty(),
                fieldCandidates = listOf(
                    FieldCandidateDiagnostic(
                        field = "NameTextual", source = "wiring_test", rawText = "Weedle",
                        parsedValue = "Weedle", status = "found", candidateScore = 0.95f,
                        winner = true, reason = "exact_canonical", selectedValue = "Weedle"
                    )
                ),
                selected = PokemonSummary.from(pokemon)
            )
            diagnostics += diagnostic
            return OcrFrameResult(pokemon, diagnostic)
        }
    }

    private fun process(manager: ScanManager, input: ScanManager.DecodedFrame): FrameDiagnostic {
        val results = mutableListOf<ScanFrameCandidate>()
        val diagnostics = mutableListOf<FrameDiagnostic>()
        val routes = mutableListOf<FrameRouteDiagnostic>()
        runBlocking { manager.processRoutedFrame(input, results, diagnostics, routes) }
        assertEquals(1, diagnostics.size)
        return diagnostics.single()
    }

    @Test
    fun coldDetailFrameRebuildsCalibrationAndPersistsIt() {
        val ocr = CapturingOcr(emitLiveBar = true, barRect = barRect)
        val diagnostic = process(manager(ocr), frameInput())

        assertEquals(1, ocr.hints.size)
        assertNull("a cold frame must not receive a seed", ocr.hints.single())
        assertEquals(CalibrationResolution.REBUILT.name, diagnostic.calibration?.resolution)
        assertEquals(CalibrationDiagnostic.PROVENANCE_REBUILT, diagnostic.calibration?.provenance)
        assertTrue(diagnostic.stageTimings.any { it.stage == "calibration_validate" })
        val persisted = store.load(currentSignature().stableKey)
        assertNotNull("rebuild must persist beyond the frame", persisted)
    }

    @Test
    fun warmDetailFrameReceivesSeedHintAndValidatesHealthy() {
        coldBuild()

        val ocr = CapturingOcr(emitLiveBar = true, barRect = barRect)
        val diagnostic = process(manager(ocr), frameInput())

        val hint = ocr.hints.single()
        assertNotNull("a same-scroll-state warm frame must receive the calibrated seed", hint)
        hint!!
        assertEquals(barRect, hint.seededBarRect)
        assertEquals(currentSignature().stableKey, hint.signatureKey)
        assertEquals(CalibrationResolution.HIT_VALIDATED.name, diagnostic.calibration?.resolution)
        assertEquals(CalibrationDiagnostic.PROVENANCE_PERSISTED, diagnostic.calibration?.provenance)
        assertEquals(CalibrationDiagnostic.BAR_SOURCE_LIVE, diagnostic.calibration?.barSource)
        assertTrue(diagnostic.stageTimings.any { it.stage == "calibration_lookup" })
    }

    @Test
    fun seededFallbackIsVisibleInDiagnosticsWhenLiveBarMissing() {
        coldBuild()

        val ocr = CapturingOcr(emitLiveBar = false, barRect = barRect)
        val diagnostic = process(manager(ocr), frameInput())

        assertEquals(CalibrationResolution.HIT_SEEDED.name, diagnostic.calibration?.resolution)
        assertEquals(CalibrationDiagnostic.BAR_SOURCE_CALIBRATED, diagnostic.calibration?.barSource)
    }

    @Test
    fun missingSourceGeometryKeepsCalibrationUnavailable() {
        coldBuild()

        val ocr = CapturingOcr(emitLiveBar = true, barRect = barRect)
        val diagnostic = process(manager(ocr), frameInput(withSourceGeometry = false))

        assertNull("no source geometry → no calibration may be fabricated", ocr.hints.single())
        assertEquals(CalibrationResolution.UNAVAILABLE.name, diagnostic.calibration?.resolution)
        assertNull(diagnostic.calibration?.signatureKey)
    }

    @Test
    fun storedCalibrationCannotBypassTheNonDetailRoutingGate() {
        coldBuild()

        val ocr = CapturingOcr(emitLiveBar = true, barRect = barRect)
        val manager = ScanManager(context)
        manager.screenRouter = ScreenStateRouter { _ -> classification(ScreenType.StorageList) }
        manager.screenCalibration = calibration
        manager.frameOcr = ocr
        val routes = mutableListOf<FrameRouteDiagnostic>()
        val results = mutableListOf<ScanFrameCandidate>()

        runBlocking {
            manager.processRoutedFrame(frameInput(), results, mutableListOf(), routes)
        }

        assertEquals(
            "a stored calibration must never push a non-detail frame into OCR",
            0,
            ocr.hints.size
        )
        assertEquals(0, results.size)
        assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, routes.single().action)
    }

    @Test
    fun routedDetailFrameClassifiesExactlyOnceIncludingGeometry() {
        val classifyInvocations = mutableListOf<Int>()
        val ocr = CapturingOcr(emitLiveBar = true, barRect = barRect)
        val manager = manager(ocr, classifyInvocations = classifyInvocations)

        process(manager, frameInput())

        assertEquals(
            "routing + geometry must share ONE classification per detail frame",
            1,
            classifyInvocations.size
        )
    }

    @Test
    fun geometryProducedFromRoutedClassificationIsConsumedByCalibration() {
        // The recording builder marks the detail card it produced; if calibration consumed
        // the ScreenGeometry result, the persisted record must carry the marked card top —
        // a parallel re-extraction of the raw anchor would not see the mark.
        val markedCardTop = cardTop + 40
        val recording = RecordingGeometryBuilder(markedCardTop)
        val ocr = CapturingOcr(emitLiveBar = true, barRect = barRect)
        val manager = manager(ocr, builder = recording)

        process(manager, frameInput())

        assertTrue(
            "the production path must build geometry from the routed classification",
            recording.buildCalls.isNotEmpty()
        )
        val persisted = store.load(currentSignature().stableKey)
        assertNotNull(persisted)
        persisted!!
        // Compare in recognition space: persisted normalized floats are quantized to the
        // store's fixed precision, so pixel-space equality is the stable contract.
        val markedCard = requireNotNull(
            NormalizedRect.fromRect(
                Rect(0, markedCardTop, frameWidth, (frameHeight * 0.7f).toInt()),
                frameWidth,
                frameHeight
            )
        )
        val geometryBand = requireNotNull(
            NormalizedRect.fromRect(
                requireNotNull(ScreenGeometryBuilder.deriveNameBand(barRect, frameWidth, frameHeight)),
                frameWidth,
                frameHeight
            )
        )
        assertEquals(
            "calibration detail card must come from the ScreenGeometry result",
            requireNotNull(markedCard.toRect(frameWidth, frameHeight)),
            requireNotNull(persisted.detailCard.toRect(frameWidth, frameHeight))
        )
        assertEquals(
            "derived name band must come from the geometry layer",
            requireNotNull(geometryBand.toRect(frameWidth, frameHeight)),
            requireNotNull(persisted.nameBand.toRect(frameWidth, frameHeight))
        )
    }

    private fun coldBuild() {
        // Drive one full cold frame through the production wiring so the store holds a
        // real rebuilt record for the current display signature.
        val ocr = CapturingOcr(emitLiveBar = true, barRect = barRect)
        process(manager(ocr), frameInput())
        assertNotNull(store.load(currentSignature().stableKey))
    }

    /** Delegates to a real builder and shifts the DetailCard anchor it returns. */
    private class RecordingGeometryBuilder(
        private val markedCardTop: Int
    ) : ScreenGeometryBuilder() {
        val buildCalls = mutableListOf<Pair<Int, Int>>()

        override fun build(bitmap: Bitmap, classification: ScreenClassificationResult): ScreenGeometry {
            buildCalls += bitmap.width to bitmap.height
            val geometry = super.build(bitmap, classification)
            val card = geometry.anchors.firstOrNull { it.name == ScreenAnchorName.DetailCard }
            val marked = card?.copy(rect = Rect(0, markedCardTop, frameWidthStatic, (frameHeightStatic * 0.7f).toInt()))
            return geometry.copy(
                anchors = listOfNotNull(marked) + geometry.anchors.filter { it.name != ScreenAnchorName.DetailCard }
            )
        }

        companion object {
            // Frame dims match ScanManagerCalibrationWiringTest constants; kept in sync there.
            const val frameWidthStatic = 300
            const val frameHeightStatic = 650
        }
    }

    private fun currentSignature(): DisplayGeometrySignature = requireNotNull(
        DisplayGeometrySignature.from(
            sourceWidth,
            sourceHeight,
            context.resources.configuration.densityDpi,
            frameWidth,
            frameHeight
        )
    )
}
