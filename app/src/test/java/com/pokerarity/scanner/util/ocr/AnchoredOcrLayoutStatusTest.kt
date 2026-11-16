package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AnchoredOcrLayoutStatusTest {
    @Test
    fun failedOrEmptyOcrCannotPublishRetainedSizeOrLuckyTags() {
        listOf(OcrDocumentStatus.FAILED, OcrDocumentStatus.EMPTY).forEach { status ->
            val result = sizeTagAndLuckyFromOcrLayout(layout(status))

            assertEquals("status=$status", null to false, result)
        }
    }

    @Test
    fun successfulOcrPublishesRecognizedSizeAndLuckyTags() {
        val result = sizeTagAndLuckyFromOcrLayout(layout(OcrDocumentStatus.SUCCESS))

        assertEquals("XL" to true, result)
    }

    @Test
    fun shadowBonusCueRequiresExactLowerDetailTextAndSuccessfulOcr() {
        val detailLabel = shadowLayout(OcrDocumentStatus.SUCCESS, "Shadow Bonus", Rect(10, 780, 210, 820))

        assertTrue(hasShadowBonusDetailCue(detailLabel, imageHeight = 1_000, detailScreen = true))
        assertFalse(hasShadowBonusDetailCue(detailLabel, imageHeight = 1_000, detailScreen = false))
        listOf(OcrDocumentStatus.EMPTY, OcrDocumentStatus.FAILED).forEach { status ->
            assertFalse(
                "status=$status cannot publish a positive Shadow result",
                hasShadowBonusDetailCue(
                    shadowLayout(status, "Shadow Bonus", Rect(10, 780, 210, 820)),
                    imageHeight = 1_000,
                    detailScreen = true
                )
            )
        }
    }

    @Test
    fun shadowBonusCueRejectsUnanchoredOrAmbiguousText() {
        assertFalse(
            hasShadowBonusDetailCue(
                shadowLayout(OcrDocumentStatus.SUCCESS, "Shadow Bonus", Rect(10, 100, 210, 140)),
                imageHeight = 1_000,
                detailScreen = true
            )
        )
        listOf("Shadow", "Shadow Bonus 2", "Bonus Shadow", "Shadow-Bonus").forEach { text ->
            assertFalse(
                text,
                hasShadowBonusDetailCue(
                    shadowLayout(OcrDocumentStatus.SUCCESS, text, Rect(10, 780, 210, 820)),
                    imageHeight = 1_000,
                    detailScreen = true
                )
            )
        }
        assertFalse(
            hasShadowBonusDetailCue(
                shadowLayout(OcrDocumentStatus.SUCCESS, "Shadow Bonus", null),
                imageHeight = 1_000,
                detailScreen = true
            )
        )
    }

    private fun layout(status: OcrDocumentStatus) = MLKitOcrProvider.Layout(
        lines = listOf(
            MLKitOcrProvider.RecognizedBlock("XL", Rect(10, 10, 30, 30)),
            MLKitOcrProvider.RecognizedBlock("Lucky Pokémon", Rect(40, 10, 130, 30))
        ),
        elements = emptyList(),
        documentStatus = status
    )

    private fun shadowLayout(status: OcrDocumentStatus, text: String, bounds: Rect?) =
        MLKitOcrProvider.Layout(
            lines = listOf(MLKitOcrProvider.RecognizedBlock(text, bounds)),
            elements = emptyList(),
            documentStatus = status
        )
}
