package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pokerarity.scanner.service.FrameOcr
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.ActionRowStatus
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.FrameRouteDiagnostic
import com.pokerarity.scanner.util.ocr.FrameOcrRequest
import com.pokerarity.scanner.util.ocr.FullGridActionRowProbe
import com.pokerarity.scanner.util.ocr.OCRProcessor
import com.pokerarity.scanner.util.ocr.OcrFrameResult
import com.pokerarity.scanner.util.ocr.ScreenRouteAction
import com.pokerarity.scanner.util.ocr.ScreenStateRouter
import com.pokerarity.scanner.util.ocr.ScreenType
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.ScanDecisionType
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Text-free production route trace for one confirmed, non-holdout scrolled development fixture. */
@RunWith(AndroidJUnit4::class)
class ScrolledTorchicRouteTraceTest {
    @Test
    fun fixture06AtProductionScaleReachesLowConfidenceGateAfterWitness() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val fixtureContext = instrumentation.context
        val appContext = instrumentation.targetContext
        val asset = "scan_fixtures/pr06_1080_development/fixture_06.png"
        val bytes = fixtureContext.assets.open(asset).use { it.readBytes() }
        val sha256 = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
        assertEquals(
            "071151cc4628bdc6fbb70f3e13199d568d9ec6a27aa9af7d002faea25c79c528",
            sha256
        )

        val decodedPreflight = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val sourceWidth = decodedPreflight.width
        val sourceHeight = decodedPreflight.height
        val preflightBitmap = toProductionInputSize(decodedPreflight)
        val preflight = ScreenStateRouter().routeWithClassification(preflightBitmap)
        val gridConfidence = preflight.classification.anchors
            .single { it.name.name == "StorageGrid" }.confidence
        val actionRows = FullGridActionRowProbe.evaluate(preflightBitmap, preflight)
        val pair = actionRows.pair
        try {
            assertEquals(ScreenType.StorageList, preflight.classification.screenType)
            assertEquals(1.0f, gridConfidence)
            assertEquals(ScreenRouteAction.REJECT_NON_DETAIL, preflight.decision.action)
            assertEquals(ActionRowStatus.PRESENT, actionRows.status)
            assertNotNull(pair)
        } finally {
            preflightBitmap.recycle()
            if (preflightBitmap !== decodedPreflight) decodedPreflight.recycle()
        }

        val decodedBitmap = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
        val bitmap = toProductionInputSize(decodedBitmap)
        if (bitmap !== decodedBitmap) decodedBitmap.recycle()
        val productionInputWidth = bitmap.width
        val productionInputHeight = bitmap.height
        val manager = ScanManager(appContext)
        val cpQuality = ScanManager::class.java
            .getDeclaredMethod("estimateCpQuality", Bitmap::class.java)
            .apply { isAccessible = true }
            .invoke(manager, bitmap) as Double
        val ocr = OCRProcessor(appContext)
        var capturedRequest: FrameOcrRequest? = null
        var capturedResult: OcrFrameResult? = null
        manager.frameOcr = FrameOcr { request ->
            capturedRequest = request
            ocr.processImageWithDiagnostics(request).also { capturedResult = it }
        }

        val results = mutableListOf<ScanFrameCandidate>()
        val frameDiagnostics = mutableListOf<FrameDiagnostic>()
        val frameRoutes = mutableListOf<FrameRouteDiagnostic>()
        try {
            manager.processRoutedFrame(
                ScanManager.DecodedFrame(
                    index = 0,
                    path = "confirmed-development-fixture",
                    bitmap = bitmap,
                    cpQuality = cpQuality,
                    pooled = false,
                    sourceWidth = sourceWidth,
                    sourceHeight = sourceHeight
                ),
                results,
                frameDiagnostics,
                frameRoutes
            )
        } finally {
            ocr.release()
        }

        val request = capturedRequest
        val result = capturedResult
        assertNotNull("full-grid action witness should reach provisional OCR", request)
        assertNotNull("provisional OCR should produce a bounded result", result)
        assertEquals(900, productionInputWidth)
        assertEquals(1950, productionInputHeight)
        assertEquals("storage_detail_probe", request!!.frameRole)
        assertEquals(null, request.calibration)
        assertEquals(1, frameRoutes.size)

        val diagnostic = result!!.diagnostic
        val speciesEvidence = results.single().speciesEvidence
        val bestEntry = requireNotNull(ScanFrameFusion.selectBestFrame(results))
        val consistency = ScanConsistencyGate(appContext, RarityCalculator(appContext)).evaluate(
            authoritative = bestEntry.data,
            candidate = bestEntry.data,
            speciesEvidence = speciesEvidence
        )
        val confidence = ScanConfidenceGate().evaluate(
            ScanConfidenceInput(
                pokemon = consistency.pokemon,
                frames = frameDiagnostics.toList(),
                consistencyReason = consistency.reason,
                consistencyRequestedRetry = consistency.shouldRetry,
                cpCropQuality = bestEntry.cpQuality,
                speciesEvidence = speciesEvidence
            )
        )
        assertEquals("Torchic", speciesEvidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.INDEPENDENT_PROFILE, speciesEvidence.authority)
        assertEquals(SpeciesProfileStatus.COMPATIBLE, speciesEvidence.profileStatus)
        assertEquals("MISSING_NOT_VISIBLE", diagnostic.structuredFields.single { it.field == "Cp" }.status)
        assertEquals("MISSING_NOT_VISIBLE", diagnostic.structuredFields.single { it.field == "Hp" }.status)
        assertNull(bestEntry.data.cp)
        assertNull(bestEntry.data.hp)
        assertEquals(ScreenRouteAction.PROCEED_DETAIL, frameRoutes.single().action)
        assertEquals("storage_detail_ui_corroborated", frameRoutes.single().reason)
        assertFalse(consistency.shouldRetry)
        assertEquals("accepted", consistency.reason)
        assertEquals(ScanDecisionType.ACCEPT_LOW_CONFIDENCE, confidence.decision)
        assertTrue(confidence.confidence >= 0.56f)
        assertTrue(confidence.mayShowOverlay)
        assertTrue(confidence.maySaveScan)
        assertEquals(
            "only a same-document detail witness can admit this storage-list frame",
            true,
            result.detailStorageUiCorroborated
        )
    }

    private companion object {
        fun toProductionInputSize(bitmap: android.graphics.Bitmap): android.graphics.Bitmap =
            if (bitmap.width <= 900) {
                bitmap
            } else {
                val scale = 900f / bitmap.width
                Bitmap.createScaledBitmap(bitmap, 900, (bitmap.height * scale).toInt(), true)
            }
    }
}
