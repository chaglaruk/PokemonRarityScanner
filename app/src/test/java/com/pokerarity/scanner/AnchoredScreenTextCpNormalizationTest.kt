package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.CropProvenance
import com.pokerarity.scanner.util.ocr.ScreenClassificationResult
import com.pokerarity.scanner.util.ocr.ScreenCrop
import com.pokerarity.scanner.util.ocr.ScreenField
import com.pokerarity.scanner.util.ocr.ScreenGeometry
import com.pokerarity.scanner.util.ocr.ScreenType
import com.pokerarity.scanner.util.ocr.CpCropOcrRecovery
import com.pokerarity.scanner.util.ocr.ExtractionContext
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.OcrDocumentStatus
import com.pokerarity.scanner.util.ocr.TextParser
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AnchoredScreenTextCpNormalizationTest {
    private val parser = TextParser(ApplicationProvider.getApplicationContext<Context>())

    @Test
    fun anchoredCpDigitsRecoverCommonOcrConfusions() {
        listOf(
            "CP I278" to 1278,
            "C P 12B8" to 1288,
            "CP 1 5O0" to 1500
        ).forEach { (label, expected) ->
            val lines = detailLines().map {
                if (it.text.startsWith("CP ")) block(label, 430, 210, 650, 260) else it
            }

            val result = extract(lines)

            assertEquals(label, expected, result.cp)
            assertEquals(label, FieldReadStatus.READ, result.cpRead.status)
            assertFalse(label, result.numericConflict)
        }
    }

    @Test
    fun completeCpTokenNeverTruncatesOrAcceptsAnEmbeddedPrefix() {
        listOf("CP 12345", "CP 10O00", "CP 123456", "CP 1", "CP OI").forEach { label ->
            val result = extract(detailLines().map {
                if (it.text.startsWith("CP ")) block(label, 430, 210, 650, 260) else it
            })
            assertNull(label, result.cp)
            assertEquals(label, FieldReadStatus.VISIBLE_UNREADABLE, result.cpRead.status)
        }
    }

    @Test
    fun completeCpTokenRepairsEveryDigitWithoutDiscardingCharacters() {
        listOf(
            "CP 12 78" to 1278,
            "CP 12,78" to 1278,
            "CP I278" to 1278,
            "CP 1 5O0" to 1500
        ).forEach { (label, expected) ->
            val result = extract(detailLines().map {
                if (it.text.startsWith("CP ")) block(label, 430, 210, 650, 260) else it
            })
            assertEquals(label, expected, result.cp)
            assertEquals(label, FieldReadStatus.READ, result.cpRead.status)
        }
    }

    @Test
    fun cpNormalizationRejectsOutOfDomainAndMalformedTokensWithoutTruncating() {
        listOf(
            "CP OI",
            "CP 10O00",
            "CP 12B8X",
            "CP 1280/2000",
            "CP 1280.5"
        ).forEach { label ->
            val lines = detailLines().map {
                if (it.text.startsWith("CP ")) block(label, 430, 210, 650, 260) else it
            }

            val result = extract(lines)

            assertNull(label, result.cp)
            assertEquals(label, FieldReadStatus.VISIBLE_UNREADABLE, result.cpRead.status)
        }

        val mixed = extract(detailLines() + block("CP I278", 430, 290, 650, 340))
        assertNull(mixed.cp)
        assertEquals(FieldReadStatus.CONFLICT, mixed.cpRead.status)
        assertTrue(mixed.numericConflict)
    }

    @Test
    fun missingFullPageCpUsesExplicitlyLabeledTargetedCropText() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fullPage = MLKitOcrProvider.Layout(
                detailLines().filterNot { it.text.startsWith("CP ") }, emptyList()
            )
            val geometry = ScreenGeometry(
                1080, 2340,
                ScreenClassificationResult(ScreenType.PokemonDetail, .9f, emptyList(), emptyList(), false),
                mapOf(ScreenField.CP to ScreenCrop(ScreenField.CP, Rect(108, 105, 972, 257), .8f,
                    CropProvenance.AnchorDerived)),
                emptyList()
            )
            var calls = 0

            val recovered = CpCropOcrRecovery.recoverMissingCp(
                bitmap, extract(fullPage.lines), geometry
            ) { crop ->
                calls++
                assertEquals(864, crop.width)
                assertEquals(152, crop.height)
                MLKitOcrProvider.Layout(
                    listOf(
                        block("CP 1278", 10, 20, 190, 65),
                        block("1278", 210, 20, 300, 65)
                    ),
                    emptyList()
                )
            }

            assertEquals(1, calls)
            assertEquals(1278, recovered.fields.cp)
            assertEquals(FieldReadStatus.READ, recovered.fields.cpRead.status)
            assertEquals("supplemented", recovered.diagnostic.status)
            assertEquals("targeted_ocr", recovered.diagnostic.source)
            assertEquals(108, recovered.diagnostic.left)
            assertEquals(105, recovered.diagnostic.top)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun targetedCpFallbackDoesNotOverrideReadUnreadableOrConflictingFullPageEvidence() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val geometry = ScreenGeometry(1080, 2340,
                ScreenClassificationResult(ScreenType.PokemonDetail, .9f, emptyList(), emptyList(), false),
                mapOf(ScreenField.CP to ScreenCrop(ScreenField.CP, Rect(108, 105, 972, 257), .8f,
                    CropProvenance.AnchorDerived)), emptyList())
            val readable = MLKitOcrProvider.Layout(detailLines(), emptyList())
            val unreadable = MLKitOcrProvider.Layout(
                detailLines().map { if (it.text.startsWith("CP ")) block("CP 12345", 430, 210, 650, 260) else it },
                emptyList()
            )
            val conflicting = MLKitOcrProvider.Layout(
                detailLines() + block("CP 500", 430, 290, 650, 340), emptyList()
            )
            var calls = 0

            listOf(readable, unreadable, conflicting).forEach { layout ->
                val fullFields = extract(layout.lines)
                val result = CpCropOcrRecovery.recoverMissingCp(
                    bitmap, fullFields, geometry
                ) {
                    calls++
                    MLKitOcrProvider.Layout(listOf(block("CP 1278", 10, 20, 190, 65)), emptyList())
                }
                assertTrue(result.fields === fullFields)
                assertEquals("skipped", result.diagnostic.status)
            }

            assertEquals(0, calls)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun targetedCpFallbackSkipsScrolledOrUnclassifiedScreens() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fields = extract(detailLines().filterNot { it.text.startsWith("CP ") })
            val crop = ScreenCrop(ScreenField.CP, Rect(108, 105, 972, 257), .8f,
                CropProvenance.AnchorDerived)
            var calls = 0

            listOf(ScreenType.PokemonDetailScrolled, ScreenType.Unknown, ScreenType.Appraisal).forEach { screenType ->
                val geometry = ScreenGeometry(
                    1080, 2340,
                    ScreenClassificationResult(screenType, .9f, emptyList(), emptyList(), false),
                    mapOf(ScreenField.CP to crop),
                    emptyList()
                )

                val result = CpCropOcrRecovery.recoverMissingCp(bitmap, fields, geometry) {
                    calls++
                    MLKitOcrProvider.Layout(listOf(block("CP 1278", 10, 20, 190, 65)), emptyList())
                }

                assertEquals("$screenType must not trigger a CP-only retry", 0, calls)
                assertTrue(result.fields === fields)
                assertEquals("skipped", result.diagnostic.status)
                assertEquals(listOf("cp_crop_requires_unscrolled_detail"), result.diagnostic.reasons)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun conflictingTargetedCpInvalidatesDetailScreenAndReportsConflict() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fullPage = MLKitOcrProvider.Layout(
                detailLines().filterNot { it.text.startsWith("CP ") }, emptyList()
            )
            val fullFields = extract(fullPage.lines).copy(detailScreen = true)
            assertTrue(fullFields.detailScreen)
            assertFalse(fullFields.numericConflict)
            val geometry = ScreenGeometry(1080, 2340,
                ScreenClassificationResult(ScreenType.PokemonDetail, .9f, emptyList(), emptyList(), false),
                mapOf(ScreenField.CP to ScreenCrop(ScreenField.CP, Rect(108, 105, 972, 257), .8f,
                    CropProvenance.AnchorDerived)), emptyList())

            val recovered = CpCropOcrRecovery.recoverMissingCp(
                bitmap, fullFields, geometry
            ) {
                MLKitOcrProvider.Layout(
                    listOf(
                        block("CP 1278", 10, 20, 190, 65),
                        block("CP 1288", 210, 20, 390, 65)
                    ),
                    emptyList()
                )
            }

            assertEquals(FieldReadStatus.CONFLICT, recovered.fields.cpRead.status)
            assertTrue(recovered.fields.numericConflict)
            assertFalse("conflicting crop evidence must revoke detail-screen corroboration",
                recovered.fields.detailScreen)
            assertEquals("conflict", recovered.diagnostic.status)
            assertEquals(listOf("cp_crop_conflicting_candidates"), recovered.diagnostic.reasons)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun unlabeledOrMalformedTargetedCropTextDoesNotCreateAValidCp() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fullPage = MLKitOcrProvider.Layout(
                detailLines().filterNot { it.text.startsWith("CP ") }, emptyList()
            )
            val geometry = ScreenGeometry(1080, 2340,
                ScreenClassificationResult(ScreenType.PokemonDetail, .9f, emptyList(), emptyList(), false),
                mapOf(ScreenField.CP to ScreenCrop(ScreenField.CP, Rect(108, 105, 972, 257), .8f,
                    CropProvenance.AnchorDerived)), emptyList())

            listOf(
                Triple(block("1278", 10, 20, 190, 65),
                    FieldReadStatus.MISSING_NOT_VISIBLE, "empty"),
                Triple(block("CP 12345", 10, 20, 190, 65),
                    FieldReadStatus.VISIBLE_UNREADABLE, "unreadable")
            ).forEach { (cropLine, expectedStatus, expectedDiagnosticStatus) ->
                val recovered = CpCropOcrRecovery.recoverMissingCp(
                    bitmap, extract(fullPage.lines), geometry
                ) {
                    MLKitOcrProvider.Layout(listOf(cropLine), emptyList())
                }
                assertNull(recovered.fields.cp)
                assertEquals(expectedStatus, recovered.fields.cpRead.status)
                assertEquals(expectedDiagnosticStatus, recovered.diagnostic.status)
            }

            val failed = CpCropOcrRecovery.recoverMissingCp(
                bitmap, extract(fullPage.lines), geometry
            ) {
                MLKitOcrProvider.Layout(emptyList(), emptyList(), OcrDocumentStatus.FAILED)
            }
            assertNull(failed.fields.cp)
            assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, failed.fields.cpRead.status)
            assertEquals("failed", failed.diagnostic.status)
        } finally {
            bitmap.recycle()
        }
    }

    private fun extract(lines: List<MLKitOcrProvider.RecognizedBlock>) = AnchoredScreenText.extract(
        layout = MLKitOcrProvider.Layout(lines, emptyList()),
        parser = parser,
        width = 1080,
        height = 2340,
        context = ExtractionContext(bar = Rect(310, 745, 770, 758))
    )

    private fun detailLines() = listOf(
        block("CP 424", 430, 210, 650, 260),
        block("Eevee", 320, 660, 760, 725),
        block("80 / 80 HP", 400, 790, 680, 830),
        block("NORMAL", 435, 935, 645, 965),
        block("WEIGHT", 100, 985, 290, 1020),
        block("HEIGHT", 790, 985, 980, 1020),
        block("EEVEE CANDY", 490, 1140, 800, 1180),
        block("POWER UP", 130, 1370, 410, 1420)
    )

    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        MLKitOcrProvider.RecognizedBlock(text, Rect(left, top, right, bottom))
}
