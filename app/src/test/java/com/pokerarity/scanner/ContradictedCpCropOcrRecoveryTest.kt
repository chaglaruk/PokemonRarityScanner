package com.pokerarity.scanner

import android.graphics.Bitmap
import android.graphics.Rect
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.CpCropOcrRecovery
import com.pokerarity.scanner.util.ocr.CropProvenance
import com.pokerarity.scanner.util.ocr.FieldRead
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.OcrDocumentStatus
import com.pokerarity.scanner.util.ocr.ScreenAnchor
import com.pokerarity.scanner.util.ocr.ScreenAnchorName
import com.pokerarity.scanner.util.ocr.ScreenClassificationResult
import com.pokerarity.scanner.util.ocr.ScreenCrop
import com.pokerarity.scanner.util.ocr.ScreenField
import com.pokerarity.scanner.util.ocr.ScreenGeometry
import com.pokerarity.scanner.util.ocr.ScreenType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ContradictedCpCropOcrRecoveryTest {

    @Test
    fun rawCropValueIsKeptAsCandidateWithoutReplacingFullPageRead() = runBlocking {
        withBitmap { bitmap ->
            val recovered = CpCropOcrRecovery.recoverContradictedCp(
                bitmap, fields(424), detailGeometry()
            ) { crop ->
                assertEquals(864, crop.width)
                assertEquals(152, crop.height)
                layout("CP 1278")
            }

            assertEquals(424, recovered.fields.cp)
            assertEquals(FieldReadStatus.READ, recovered.fields.cpRead.status)
            assertEquals(1278, recovered.candidateValue)
            assertEquals("1278", recovered.diagnostic.candidateValue)
            assertEquals("candidate", recovered.diagnostic.status)
        }
    }

    @Test
    fun neutralRetryOffersAlternateOnlyAfterTheRawCropRepeatsTheReadCp() = runBlocking {
        withBitmap { bitmap ->
            var calls = 0
            val recovered = CpCropOcrRecovery.recoverContradictedCp(
                bitmap, fields(424), detailGeometry()
            ) { crop ->
                calls++
                if (calls == 1) {
                    assertEquals(864, crop.width)
                    layout("CP 424")
                } else {
                    assertEquals(1_452, crop.width)
                    layout("CP 1278")
                }
            }

            assertEquals(2, calls)
            assertEquals(424, recovered.fields.cp)
            assertEquals(1278, recovered.candidateValue)
            assertEquals("neutral_profile_contradiction_crop_candidate", recovered.diagnostic.reasons.single())
        }
    }

    @Test
    fun agreeingOrConflictingCropEvidenceNeverOverwritesOriginalRead() = runBlocking {
        withBitmap { bitmap ->
            var calls = 0
            val agreed = CpCropOcrRecovery.recoverContradictedCp(
                bitmap, fields(424), detailGeometry()
            ) {
                calls++
                layout("CP 424")
            }
            assertEquals("unchanged", agreed.diagnostic.status)
            assertEquals(424, agreed.fields.cp)

            calls = 0
            val conflict = CpCropOcrRecovery.recoverContradictedCp(
                bitmap, fields(424), detailGeometry()
            ) {
                calls++
                MLKitOcrProvider.Layout(
                    listOf(block("CP 1278", 10, 20), block("CP 1279", 210, 20)), emptyList()
                )
            }
            assertEquals(1, calls)
            assertEquals("unresolved", conflict.diagnostic.status)
            assertEquals(424, conflict.fields.cp)
        }
    }

    @Test
    fun retryRequiresReadCpAndAnchoredDetailGeometry() = runBlocking {
        withBitmap { bitmap ->
            var calls = 0
            val missing = CpCropOcrRecovery.recoverContradictedCp(
                bitmap, fields(null), detailGeometry()
            ) {
                calls++
                MLKitOcrProvider.Layout(emptyList(), emptyList(), OcrDocumentStatus.EMPTY)
            }
            assertEquals("skipped", missing.diagnostic.status)

            val unanchored = CpCropOcrRecovery.recoverContradictedCp(
                bitmap, fields(424), detailGeometry(cpHeader = null)
            ) {
                calls++
                MLKitOcrProvider.Layout(emptyList(), emptyList(), OcrDocumentStatus.EMPTY)
            }
            assertEquals("skipped", unanchored.diagnostic.status)
            assertEquals(0, calls)
        }
    }

    private suspend fun withBitmap(block: suspend (Bitmap) -> Unit) {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            block(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private fun fields(cp: Int?) = AnchoredScreenText.Fields(
        name = null,
        nameRaw = null,
        cp = cp,
        hp = null,
        candy = null,
        powerUpCost = null,
        types = null,
        detailScreen = true,
        hpRect = null,
        nameRect = null,
        candyRect = null,
        costRect = null,
        cpRead = if (cp != null) FieldRead.read(cp) else FieldRead.missing("no_cp_label")
    )

    private fun detailGeometry(
        cpHeader: ScreenAnchor? = ScreenAnchor(
            ScreenAnchorName.CpHeader,
            Rect(108, 105, 972, 257),
            .8f,
            "measured_test_cp_header"
        )
    ): ScreenGeometry {
        val anchors = listOfNotNull(cpHeader)
        return ScreenGeometry(
            1080,
            2340,
            ScreenClassificationResult(ScreenType.PokemonDetail, .9f, emptyList(), anchors, false),
            mapOf(ScreenField.CP to ScreenCrop(
                ScreenField.CP, Rect(108, 105, 972, 257), .8f, CropProvenance.AnchorDerived
            )),
            anchors
        )
    }

    private fun layout(text: String) = MLKitOcrProvider.Layout(listOf(block(text, 10, 20)), emptyList())

    private fun block(text: String, left: Int, top: Int) = MLKitOcrProvider.RecognizedBlock(
        text, Rect(left, top, left + 180, top + 45)
    )
}
