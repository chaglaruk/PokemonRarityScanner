package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
@Suppress("TooManyFunctions") // Small focused helpers keep the pixel-replay assertions readable.
class QaExactFrameCaptureTest {

    @Test
    fun exactFramesAreAbsentUnlessTheDedicatedQaScanIsExplicitlyOptedIn() {
        val recorder = VariantStageDiagnosticRecorder()
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        try {
            recorder.captureExactFrame(captureInput(bitmap, Rect(1, 2, 7, 8), false))
            assertNull(recorder.pendingExactFrame())
            assertEquals("opt_in_disabled", recorder.snapshot(testBuild(), testMetadata()).exactFrameEvidence.status)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun optedInCaptureStoresOnlyThreeBoundedPngsAndCropGeometry() {
        val recorder = VariantStageDiagnosticRecorder(exactFrameCaptureOptIn = true)
        val bitmap = Bitmap.createBitmap(16, 12, Bitmap.Config.ARGB_8888)
        try {
            recorder.captureExactFrame(captureInput(bitmap, Rect(3, 2, 14, 10), true))
            val pending = recorder.pendingExactFrame()
            assertNotNull(pending)
            assertTrue(pending!!.framePng.size + pending.maskedSpritePng.size + pending.analysisRegionPng.size <=
                VariantStageDiagnosticRecorder.MAX_EXACT_FRAME_SET_BYTES)
            assertEquals(3, pending.crop.left)
            assertEquals(2, pending.crop.top)
            assertEquals(14, pending.crop.right)
            assertEquals(10, pending.crop.bottom)
            assertEquals(16, pending.crop.analysisWidth)
            assertEquals(12, pending.crop.analysisHeight)
            assertTrue(pending.crop.shadowMaskApplied)
            assertEquals(16, pending.frameWidth)
            assertEquals(12, pending.frameHeight)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun oversizedCaptureFailsClosedAndDoesNotRetainPartialImages() {
        val recorder = VariantStageDiagnosticRecorder(exactFrameCaptureOptIn = true)
        val oversized = Bitmap.createBitmap(
            VariantStageDiagnosticRecorder.MAX_IMAGE_DIMENSION + 1,
            1,
            Bitmap.Config.ARGB_8888
        )
        val small = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            recorder.captureExactFrame(captureInput(oversized, Rect(0, 0, 2, 2), false, small))
            assertNull(recorder.pendingExactFrame())
            val evidence = recorder.snapshot(testBuild(), testMetadata()).exactFrameEvidence
            assertEquals("capture_failed", evidence.status)
            assertEquals("bounded_png_capture_failed", evidence.failureReasonCode)
        } finally {
            oversized.recycle()
            small.recycle()
        }
    }

    @Test
    fun persistedEvidenceSummaryIsBoundedAndContainsNoAbsolutePaths() {
        val recorder = VariantStageDiagnosticRecorder(exactFrameCaptureOptIn = true)
        val summary = QaExactFrameEvidence(
            status = "saved",
            relativeDirectory = "variant_diagnostics/exact_frames/${recorder.scanId}",
            crop = QaExactFrameCrop(1, 2, 9, 11, 16, 16, 32, 32, false),
            images = (0..7).map { index ->
                QaExactFrameImage("frame-$index.png", "a".repeat(64), 100, 16, 16)
            },
            costumeReferenceDistances = (0..19).map { index ->
                QaCostumeReferenceDistance(
                    key = "key-$index",
                    costume = index % 2 == 0,
                    aHashDistance = .1f,
                    dHashDistance = .2f,
                    edgeDistance = .3f,
                    pHashDistance = .4f,
                    headPHashDistance = .5f,
                    weightedScore = .25f,
                    rank = index + 1
                )
            }
        )
        recorder.recordExactFrameEvidence(summary)
        val json = QaVariantDiagnosticWriter.serialize(recorder.snapshot(testBuild(), testMetadata()))

        assertTrue(json.toByteArray(Charsets.UTF_8).size <= QaVariantDiagnosticWriter.MAX_REPORT_BYTES)
        assertTrue(json.contains("variant_diagnostics/exact_frames/"))
        assertFalse(json.contains("C:\\Users\\"))
        assertFalse(json.contains("rawOcrText"))
        val report = recorder.snapshot(testBuild(), testMetadata())
        assertEquals(
            VariantStageDiagnosticRecorder.MAX_EXACT_FRAME_IMAGES,
            report.exactFrameEvidence.images.size
        )
        assertEquals(
            VariantStageDiagnosticRecorder.MAX_CANDIDATES,
            report.exactFrameEvidence.costumeReferenceDistances.size
        )
    }

    @Test
    fun pngReplayUsesProductionCostumeAndShinyScoresDeterministically() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pixels = Bitmap.createBitmap(48, 56, Bitmap.Config.ARGB_8888)
        try {
            fillPattern(pixels)
            val decoded = pngRoundTrip(pixels)
            try {
                assertReplayIsStable(context, pixels, decoded)
            } finally {
                decoded.recycle()
            }
        } finally {
            pixels.recycle()
        }
    }

    private fun testBuild() = QaBuildIdentity(
        "com.pokerarity.scanner.sprint6qa.diag2",
        "test",
        1,
        "a".repeat(40)
    )

    private fun testMetadata() = QaMetadataRevision(
        "qa_apk_bundled_assets",
        "b".repeat(64),
        emptyList()
    )

    private fun captureInput(
        detectorInput: Bitmap,
        crop: Rect,
        shadowMaskApplied: Boolean,
        region: Bitmap = detectorInput
    ) = QaExactFrameCaptureInput(
        detectorInput = detectorInput,
        maskedSprite = region,
        analysisRegion = region,
        crop = crop,
        analysisWidth = detectorInput.width,
        analysisHeight = detectorInput.height,
        shadowMaskApplied = shadowMaskApplied
    )

    private fun fillPattern(bitmap: Bitmap) {
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = if ((x + y) % 3 == 0) android.graphics.Color.CYAN else android.graphics.Color.MAGENTA
                bitmap.setPixel(x, y, color)
            }
        }
    }

    private fun pngRoundTrip(bitmap: Bitmap): Bitmap {
        val output = ByteArrayOutputStream()
        assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
        return BitmapFactory.decodeByteArray(output.toByteArray(), 0, output.size())
            ?: error("PNG replay fixture failed to decode")
    }

    private fun assertReplayIsStable(context: Context, pixels: Bitmap, decoded: Bitmap) {
        val first = QaExactFrameReplay.evaluate(context, "Cottonee", pixels, pixels, false)
        val replayed = QaExactFrameReplay.evaluate(context, "Cottonee", decoded, decoded, false)
        assertEquals(4, first.costumeReferenceDistances.size)
        assertEquals(first.costumeReferenceDistances, replayed.costumeReferenceDistances)
        val baseNormal = first.costumeReferenceDistances.single { it.key == "546_00" }
        assertEquals(
            0.28f * baseNormal.dHashDistance + 0.20f * baseNormal.aHashDistance +
                0.18f * baseNormal.edgeDistance + 0.14f * baseNormal.pHashDistance +
                0.20f * baseNormal.headPHashDistance,
            baseNormal.weightedScore,
            0.000001f
        )
        assertNotNull(first.shinyPrimary?.normalComponents)
        assertNotNull(first.shinyPrimary?.shinyComponents)
        assertEquals(first.shinyPrimary, replayed.shinyPrimary)
        assertEquals(first.shinyAlternates, replayed.shinyAlternates)
        assertEquals(first.hueEvidence, replayed.hueEvidence)
    }
}
