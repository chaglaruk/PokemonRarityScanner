package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.CpCropOcrRecovery
import com.pokerarity.scanner.util.ocr.CropProvenance
import com.pokerarity.scanner.util.ocr.ExtractionContext
import com.pokerarity.scanner.util.ocr.ScreenAnchor
import com.pokerarity.scanner.util.ocr.ScreenAnchorName
import com.pokerarity.scanner.util.ocr.ScreenClassificationResult
import com.pokerarity.scanner.util.ocr.ScreenCrop
import com.pokerarity.scanner.util.ocr.ScreenField
import com.pokerarity.scanner.util.ocr.ScreenGeometry
import com.pokerarity.scanner.util.ocr.ScreenType
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.OcrDocumentStatus
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.anchoredRecognitionObservation
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
    fun cpLabelAndDigitsAcceptUnicodeSpacingWithoutTruncating() {
        val result = extract(detailLines().map {
            if (it.text.startsWith("CP ")) {
                block("C\u00A0P\u00A01\u00A0278", 430, 210, 650, 260)
            } else {
                it
            }
        })

        assertEquals(1278, result.cp)
        assertEquals(FieldReadStatus.READ, result.cpRead.status)
    }

    @Test
    fun cpTokenAcceptsTerminalBidiMarksButRejectsInvisibleDigitContinuation() {
        listOf("\u200E", "\u200F").forEach { mark ->
            val result = extract(detailLines().map {
                if (it.text.startsWith("CP ")) block("CP 1280$mark", 430, 210, 650, 260) else it
            })

            assertEquals(1280, result.cp)
            assertEquals(FieldReadStatus.READ, result.cpRead.status)
        }

        val hiddenDigit = extract(detailLines().map {
            if (it.text.startsWith("CP ")) block("CP 1280\u200B\u200B 5", 430, 210, 650, 260) else it
        })
        assertNull(hiddenDigit.cp)
        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, hiddenDigit.cpRead.status)
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
    fun cpBeforeCommaDelimitedHpValueIsPreserved() {
        val result = extract(detailLines().map {
            if (it.text.startsWith("CP ")) {
                block("CP 1280, HP 50/50", 430, 210, 650, 260)
            } else {
                it
            }
        })

        assertEquals(1280, result.cp)
        assertEquals(FieldReadStatus.READ, result.cpRead.status)
    }

    @Test
    fun cpNormalizationRejectsOutOfDomainAndMalformedTokensWithoutTruncating() {
        listOf(
            "CP OI",
            "CP 10O00",
            "CP 12B8X",
            "CP 1280/2000",
            "CP 1280.5",
            "CP 123-45",
            "CP 1280_5",
            "CP 123 - 45",
            "CP 123+45",
            "CP 123 + 45",
            "CP 123 – 45",
            "CP 123 — 45",
            "CP 1280 \u2044 2000",
            "CP 1280\u22152000",
            "CP 1280 \uFF0F 2000",
            "CP 1280\u00A05",
            "CP 1280\u200B5",
            "CP 1280\u200B\u200B5",
            "CP 1280\u200B 5",
            "CP 1280 \u200B\u00A05",
            "CP 1280\u200B -45",
            "CP 1280\u200B\u0665",
            "CP 1280\u200B\uFF15",
            "CP 1280.-45",
            "CP 1280.HP 50/50",
            "CP 1280\u200B.-45",
            "CP 1280, X",
            "CP 1280 _ 5",
            "CP 1280 / 2000",
            "CP 1280 . 5"
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

        val validAndMalformed = extract(
            detailLines().map {
                if (it.text.startsWith("CP ")) block("CP 1280", 430, 210, 650, 260) else it
            } + block("CP 1280 \u2044 2000", 430, 290, 650, 340)
        )
        assertNull(validAndMalformed.cp)
        assertEquals(FieldReadStatus.CONFLICT, validAndMalformed.cpRead.status)
        assertTrue(validAndMalformed.numericConflict)
    }

    @Test
    fun splitCpLabelAndAdjacentDigitsAreRecoveredFromFullFrame() {
        val separateValue = extract(detailLines().map {
            if (it.text.startsWith("CP ")) block("CP", 430, 210, 470, 260) else it
        } + block("1278", 480, 210, 565, 260))
        assertEquals(1278, separateValue.cp)
        assertEquals(FieldReadStatus.READ, separateValue.cpRead.status)

        val splitPrefix = extract(detailLines().map {
            if (it.text.startsWith("CP ")) block("CP 1", 430, 210, 475, 260) else it
        } + block("500", 480, 210, 535, 260))
        assertEquals(1500, splitPrefix.cp)
        assertEquals(FieldReadStatus.READ, splitPrefix.cpRead.status)

        val conflictingAdjacentValues = extract(
            detailLines().filterNot { it.text.startsWith("CP ") } + listOf(
                block("CP", 430, 210, 480, 260),
                block("1500", 485, 210, 560, 260),
                block("1600", 485, 210, 560, 260)
            )
        )
        assertNull(conflictingAdjacentValues.cp)
        assertEquals(FieldReadStatus.CONFLICT, conflictingAdjacentValues.cpRead.status)
    }

    @Test
    fun splitCpLabelAndAdjacentDigitsAreRecoveredFromTargetedCrop() = runBlocking {
        listOf(
            listOf(block("CP", 10, 20, 75, 65), block("1278", 80, 20, 170, 65)) to 1278,
            listOf(block("CP 1", 10, 20, 80, 65), block("500", 85, 20, 150, 65)) to 1500
        ).forEach { (lines, expected) ->
            val recovered = recoverTargetedCrop(lines)
            assertEquals(expected, recovered.fields.cp)
            assertEquals(FieldReadStatus.READ, recovered.fields.cpRead.status)
            assertEquals("supplemented", recovered.diagnostic.status)
        }
    }

    @Test
    fun targetedCropKeepsConflictingAdjacentCpValuesAsConflict() = runBlocking {
        val conflict = recoverTargetedCrop(
            listOf(
                block("CP", 10, 20, 75, 65),
                block("1500", 80, 20, 170, 65),
                block("1600", 80, 20, 170, 65)
            )
        )
        assertNull(conflict.fields.cp)
        assertEquals(FieldReadStatus.CONFLICT, conflict.fields.cpRead.status)
        assertTrue(conflict.fields.numericConflict)
        assertFalse(conflict.fields.detailScreen)
    }

    @Test
    fun targetedCropDoesNotSilentlyReplaceACompleteCpWithAdjacentDigits() = runBlocking {
        val conflict = recoverTargetedCrop(
            listOf(
                block("CP 123", 10, 20, 80, 65),
                block("4", 85, 20, 120, 65)
            )
        )

        assertNull(conflict.fields.cp)
        assertEquals(FieldReadStatus.CONFLICT, conflict.fields.cpRead.status)
        assertTrue(conflict.fields.numericConflict)
        assertFalse(conflict.fields.detailScreen)
    }

    @Test
    fun targetedCropRejectsMalformedAndDistantAdjacentCpDigits() = runBlocking {
        val invalidCases = listOf(
            listOf(block("CP 1 X", 10, 20, 80, 65), block("500", 85, 20, 150, 65)),
            listOf(block("CP", 10, 20, 75, 65), block("1500", 200, 20, 290, 65))
        )
        invalidCases.forEach { lines ->
            val unreadable = recoverTargetedCrop(lines)
            assertNull(unreadable.fields.cp)
            assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, unreadable.fields.cpRead.status)
        }
    }

    @Test
    fun missingFullPageCpUsesExplicitlyLabeledTargetedCropText() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fullPage = MLKitOcrProvider.Layout(
                detailLines().filterNot { it.text.startsWith("CP ") }, emptyList()
            )
            val geometry = detailGeometry()
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
            val geometry = detailGeometry()
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
    fun targetedCpFallbackSkipsUnanchoredScrolledOrUnclassifiedScreens() = runBlocking {
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
                assertEquals(listOf("cp_crop_requires_detail_and_visible_header"), result.diagnostic.reasons)
            }
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun targetedCpFallbackSkipsNormalDetailWithoutMeasuredCpHeader() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fields = extract(detailLines().filterNot { it.text.startsWith("CP ") })
            val geometry = detailGeometry(cpHeader = null)
            var calls = 0

            val result = CpCropOcrRecovery.recoverMissingCp(bitmap, fields, geometry) {
                calls++
                MLKitOcrProvider.Layout(listOf(block("CP 1278", 10, 20, 190, 65)), emptyList())
            }

            assertEquals("generic top crop must not run without a measured CP header", 0, calls)
            assertTrue(result.fields === fields)
            assertEquals("skipped", result.diagnostic.status)
            assertEquals(listOf("cp_crop_requires_detail_and_visible_header"), result.diagnostic.reasons)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun targetedCpFallbackSkipsWeakDetailClassificationEvenWithCpHeader() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fields = extract(detailLines().filterNot { it.text.startsWith("CP ") })
            val weakClassifications = listOf(
                detailGeometry(confidence = .69f),
                detailGeometry(safeFallback = true)
            )
            var calls = 0

            weakClassifications.forEach { geometry ->
                val result = CpCropOcrRecovery.recoverMissingCp(bitmap, fields, geometry) {
                    calls++
                    MLKitOcrProvider.Layout(listOf(block("CP 1278", 10, 20, 190, 65)), emptyList())
                }
                assertEquals("weak detail classifications must be skipped", "skipped", result.diagnostic.status)
                assertTrue(result.fields === fields)
            }

            assertEquals("weak detail classifications must not run crop OCR", 0, calls)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun scrolledDetailWithMeasuredCpHeaderMayUseTargetedRetry() = runBlocking {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fields = extract(detailLines().filterNot { it.text.startsWith("CP ") })
            val rect = Rect(108, 105, 972, 257)
            val cpHeader = ScreenAnchor(ScreenAnchorName.CpHeader, rect, .78f, "measured_cp_header")
            val geometry = ScreenGeometry(
                1080,
                2340,
                ScreenClassificationResult(
                    ScreenType.PokemonDetailScrolled,
                    .9f,
                    emptyList(),
                    listOf(cpHeader),
                    false
                ),
                mapOf(ScreenField.CP to ScreenCrop(ScreenField.CP, rect, .78f, CropProvenance.AnchorDerived)),
                listOf(cpHeader)
            )
            var calls = 0

            val recovered = CpCropOcrRecovery.recoverMissingCp(bitmap, fields, geometry) {
                calls++
                MLKitOcrProvider.Layout(listOf(block("CP 1278", 10, 20, 190, 65)), emptyList())
            }

            assertEquals(1, calls)
            assertEquals(1278, recovered.fields.cp)
            assertEquals("supplemented", recovered.diagnostic.status)
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
            val geometry = detailGeometry()

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
            assertTrue("crop conflicts must block identity independently of action costs",
                recovered.fields.identityBlockingNumericConflict)
            val observation = anchoredRecognitionObservation(recovered.fields, 0, emptySet())
            assertTrue(observation.identityBlockingNumericConflict)
            assertFalse(observation.detailScreen)
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
            val geometry = detailGeometry()

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
            assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, failed.fields.cpRead.status)
            assertEquals("cp_crop_ocr_failed", failed.fields.cpRead.reasonCode)
            assertEquals(extract(fullPage.lines).hp, failed.fields.hp)
            assertEquals(extract(fullPage.lines).nameRaw, failed.fields.nameRaw)
            assertEquals(extract(fullPage.lines).identityBlockingNumericConflict,
                failed.fields.identityBlockingNumericConflict)
            assertEquals("failed", failed.diagnostic.status)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun malformedAdjacentNumericReadMustRemainConflictingEvidence() {
        val result = extract(
            detailLines().filterNot { it.text.startsWith("CP ") } +
                block("CP", 430, 210, 480, 260) +
                block("1500", 485, 210, 625, 260) +
                block("1600/2000", 485, 210, 725, 260)
        )

        assertNull(result.cp)
        assertEquals(FieldReadStatus.CONFLICT, result.cpRead.status)
        assertTrue(result.identityBlockingNumericConflict)
    }

    @Test
    fun adjacentDigitsDoNotSilentlyReplaceACompleteCp() {
        listOf(
            "CP 12" to "34",
            "CP 123" to "4"
        ).forEach { (prefix, suffix) ->
            val result = extractWithAdjacentCpDigits(prefix, suffix)

            assertNull("$prefix + $suffix", result.cp)
            assertEquals("$prefix + $suffix", FieldReadStatus.CONFLICT, result.cpRead.status)
            assertTrue("$prefix + $suffix", result.numericConflict)
        }

        val incompletePrefix = extractWithAdjacentCpDigits("CP 1", "234")
        assertEquals(1234, incompletePrefix.cp)
        assertEquals(FieldReadStatus.READ, incompletePrefix.cpRead.status)

        val separated = extractWithAdjacentCpDigits("CP 123", "4", suffixLeft = 635)
        assertEquals(123, separated.cp)
        assertEquals(FieldReadStatus.READ, separated.cpRead.status)

        val oversized = extractWithAdjacentCpDigits("CP 1280", "5")
        assertEquals(1280, oversized.cp)
        assertEquals(FieldReadStatus.READ, oversized.cpRead.status)
    }

    private fun extractWithAdjacentCpDigits(prefix: String, suffix: String, suffixLeft: Int = 627) = extract(
        detailLines().filterNot { it.text.startsWith("CP ") } +
            block(prefix, 430, 210, 625, 260) +
            block(suffix, suffixLeft, 210, suffixLeft + 48, 260)
    )

    private fun extract(lines: List<MLKitOcrProvider.RecognizedBlock>) = AnchoredScreenText.extract(
        layout = MLKitOcrProvider.Layout(lines, emptyList()),
        parser = parser,
        width = 1080,
        height = 2340,
        context = ExtractionContext(bar = Rect(310, 745, 770, 758))
    )

    private fun detailGeometry(
        cpHeader: ScreenAnchor? = ScreenAnchor(
            ScreenAnchorName.CpHeader,
            Rect(108, 105, 972, 257),
            .8f,
            "measured_test_cp_header"
        ),
        confidence: Float = .9f,
        safeFallback: Boolean = false
    ): ScreenGeometry {
        val anchors = listOfNotNull(cpHeader)
        return ScreenGeometry(
            1080,
            2340,
            ScreenClassificationResult(ScreenType.PokemonDetail, confidence, emptyList(), anchors, safeFallback),
            mapOf(
                ScreenField.CP to ScreenCrop(
                    ScreenField.CP,
                    Rect(108, 105, 972, 257),
                    .8f,
                    CropProvenance.AnchorDerived
                )
            ),
            anchors
        )
    }

    private suspend fun recoverTargetedCrop(
        cropLines: List<MLKitOcrProvider.RecognizedBlock>
    ): CpCropOcrRecovery.Result {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fullPage = detailLines().filterNot { it.text.startsWith("CP ") }
            val geometry = detailGeometry()
            return CpCropOcrRecovery.recoverMissingCp(bitmap, extract(fullPage), geometry) {
                MLKitOcrProvider.Layout(cropLines, emptyList())
            }
        } finally {
            bitmap.recycle()
        }
    }

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
