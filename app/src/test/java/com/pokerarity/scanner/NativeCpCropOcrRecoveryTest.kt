package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.CpCropOcrRecovery
import com.pokerarity.scanner.util.ocr.CropDiagnostic
import com.pokerarity.scanner.util.ocr.CropProvenance
import com.pokerarity.scanner.util.ocr.ExtractionContext
import com.pokerarity.scanner.util.ocr.FieldRead
import com.pokerarity.scanner.util.ocr.ScreenAnchor
import com.pokerarity.scanner.util.ocr.ScreenAnchorName
import com.pokerarity.scanner.util.ocr.ScreenClassificationResult
import com.pokerarity.scanner.util.ocr.ScreenCrop
import com.pokerarity.scanner.util.ocr.ScreenField
import com.pokerarity.scanner.util.ocr.ScreenGeometry
import com.pokerarity.scanner.util.ocr.ScreenType
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.NativeCpRetryEligibility
import com.pokerarity.scanner.util.ocr.NativeResolutionRetryInput
import com.pokerarity.scanner.util.ocr.OcrDocumentStatus
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.anchoredRecognitionObservation
import com.pokerarity.scanner.util.ocr.scaledTo
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
class NativeCpCropOcrRecoveryTest {
    private val parser = TextParser(ApplicationProvider.getApplicationContext<Context>())
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
    fun nativeCpRetryIsLimitedToAConfirmedEmptyAnchoredCropAndUsableSourceSize() {
        val fastGeometry = detailGeometry().scaledTo(900, 1950)
        val missingCp = extract(detailLines().filterNot { it.text.startsWith("CP ") })
        val emptyTargetedCrop = CropDiagnostic(
            field = "CP", source = "targeted_ocr", left = 90, top = 88, right = 810, bottom = 214,
            status = "empty", provenance = "anchor-derived", confidence = .8f,
            reasons = listOf("cp_crop_label_not_found")
        )

        assertTrue(NativeCpRetryEligibility.shouldAttempt(
            retryInput(missingCp, emptyTargetedCrop, fastGeometry)
        ))
        assertFalse(NativeCpRetryEligibility.shouldAttempt(
            retryInput(missingCp, emptyTargetedCrop.copy(status = "unreadable"), fastGeometry)
        ))
        assertFalse(NativeCpRetryEligibility.shouldAttempt(
            retryInput(missingCp, emptyTargetedCrop.copy(reasons = listOf("cp_crop_ocr_failed")), fastGeometry)
        ))
        assertFalse(NativeCpRetryEligibility.shouldAttempt(
            retryInput(missingCp, emptyTargetedCrop, fastGeometry, sourceSize = 1440 to 3200)
        ))
        assertFalse(NativeCpRetryEligibility.shouldAttempt(
            retryInput(missingCp.copy(cpRead = FieldRead.read(424)), emptyTargetedCrop, fastGeometry)
        ))
    }

    @Test
    fun nativeResolutionCropRecoversCpAfterBaselineAndTargetedCropMiss() = runBlocking {
        val fields = extract(detailLines().filterNot { it.text.startsWith("CP ") })
        val fastGeometry = requireNotNull(detailGeometry().scaledTo(900, 1950))
        val fastBitmap = Bitmap.createBitmap(900, 1950, Bitmap.Config.ARGB_8888)
        val nativeBitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        try {
            val fastMiss = CpCropOcrRecovery.recoverMissingCp(
                fastBitmap, fields, fastGeometry
            ) { MLKitOcrProvider.Layout(emptyList(), emptyList(), OcrDocumentStatus.EMPTY) }
            assertNull(fastMiss.fields.cp)
            assertEquals("cp_crop_label_not_found", fastMiss.diagnostic.reasons.single())

            val nativeRead = CpCropOcrRecovery.recoverMissingCp(
                nativeBitmap, fastMiss.fields, detailGeometry()
            ) { crop ->
                MLKitOcrProvider.Layout(listOf(block("CP 2122", 300, 30, 570, 90)), emptyList())
            }

            assertEquals(2122, nativeRead.fields.cp)
            assertEquals(FieldReadStatus.READ, nativeRead.fields.cpRead.status)
            assertEquals("supplemented", nativeRead.diagnostic.status)
            assertEquals("explicit_cp_label_read", nativeRead.diagnostic.reasons.single())
        } finally {
            fastBitmap.recycle()
            nativeBitmap.recycle()
        }
    }

    @Test
    fun nativeNeutralBrightRetryRunsOnlyAfterRawCropMissAndKeepsExplicitCpRead() = runBlocking {
        val fields = extract(detailLines().filterNot { it.text.startsWith("CP ") })
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        var calls = 0
        try {
            val recovered = CpCropOcrRecovery.recoverNativeCpWithNeutralTextRetry(
                bitmap, fields, detailGeometry()
            ) { input ->
                calls++
                if (calls == 1) {
                    assertEquals(864, input.width)
                    assertEquals(152, input.height)
                    MLKitOcrProvider.Layout(emptyList(), emptyList(), OcrDocumentStatus.EMPTY)
                } else {
                    assertEquals(1_452, input.width)
                    assertEquals(304, input.height)
                    MLKitOcrProvider.Layout(listOf(block("CP 2122", 20, 20, 300, 100)), emptyList())
                }
            }

            assertEquals(2, calls)
            assertEquals(2122, recovered.fields.cp)
            assertEquals("native_targeted_ocr", recovered.diagnostic.source)
            assertEquals(listOf("native_raw_crop_no_cp_label", "neutral_bright_retry_read"),
                recovered.diagnostic.reasons)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun nativeNeutralBrightRetryDoesNotRunWhenRawCropHasAnExplicitCpRead() = runBlocking {
        val fields = extract(detailLines().filterNot { it.text.startsWith("CP ") })
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        var calls = 0
        try {
            val recovered = CpCropOcrRecovery.recoverNativeCpWithNeutralTextRetry(
                bitmap, fields, detailGeometry()
            ) {
                calls++
                MLKitOcrProvider.Layout(listOf(block("CP 2122", 20, 20, 300, 100)), emptyList())
            }

            assertEquals(1, calls)
            assertEquals(2122, recovered.fields.cp)
            assertEquals(listOf("explicit_cp_label_read"), recovered.diagnostic.reasons)
        } finally {
            bitmap.recycle()
        }
    }

    @Test
    fun neutralBrightTextCropPreservesWhiteTextAndSuppressesSaturatedPixels() {
        val bitmap = Bitmap.createBitmap(100, 40, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.BLUE)
        bitmap.setPixel(50, 20, Color.WHITE)
        bitmap.setPixel(25, 10, Color.CYAN)
        val filtered = CpCropOcrRecovery.createNeutralBrightTextCrop(bitmap)
        try {
            assertEquals(168, filtered.width)
            assertEquals(80, filtered.height)
            assertEquals(Color.BLACK, filtered.getPixel(84, 40))
            assertEquals(Color.WHITE, filtered.getPixel(34, 20))
            assertEquals(Color.WHITE, filtered.getPixel(0, 0))
        } finally {
            filtered.recycle()
            bitmap.recycle()
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

    private fun retryInput(
        fields: AnchoredScreenText.Fields,
        crop: CropDiagnostic,
        geometry: ScreenGeometry?,
        sourceSize: Pair<Int, Int> = 1080 to 2340
    ): NativeResolutionRetryInput {
        return NativeResolutionRetryInput(
            fields, crop, geometry, 900, 1950, sourceSize.first, sourceSize.second
        )
    }

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
