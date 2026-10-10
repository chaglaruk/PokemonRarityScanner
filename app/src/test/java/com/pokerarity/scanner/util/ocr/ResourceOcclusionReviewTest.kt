package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Synthetic review counterexamples only, not physical screens or independent OCR truth. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ResourceOcclusionReviewTest {
    private val inventory = Rect(100, 120, 120, 140)
    private val cost = Rect(100, 250, 120, 270)
    private val candy = MLKitOcrProvider.RecognizedBlock("CANDY", Rect(50, 150, 160, 165))
    private val evolve = MLKitOcrProvider.RecognizedBlock("EVOLVE", Rect(15, 250, 60, 270))

    private fun image(cover: Int, background: Int = Color.WHITE) =
        Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888).apply {
        eraseColor(background)
        for (amount in listOf(inventory, cost)) drawGlyph(this, amount)
        // A visible monochrome panel begins where a trailing digit would be hidden.
        for (y in cost.top until cost.bottom) for (x in cost.right until cost.right + 10) setPixel(x, y, cover)
    }

    private fun drawGlyph(bitmap: Bitmap, amount: Rect) {
        for (y in amount.centerY() - 6 until amount.centerY() + 6) {
            for (x in amount.left - 27 until amount.left - 18) bitmap.setPixel(x, y, Color.rgb(120, 190, 130))
        }
    }

    private fun layout() = MLKitOcrProvider.Layout(listOf(candy, evolve), listOf(
        MLKitOcrProvider.RecognizedBlock("123", inventory), MLKitOcrProvider.RecognizedBlock("20", cost)))

    @Test
    fun neutralOccluderMustNotDeclareTruncatedAmountClear() {
        val bitmap = image(Color.GRAY)
        try {
            val witness = ActionResourceMatcher.observe(bitmap, layout()).single { it.bounds == cost }
            assertEquals(ActionResourceKind.CANDY, witness.kind)
            assertFalse("A visible gray cover must not certify the amount as clear", witness.amountClear)
        } finally { bitmap.recycle() }
    }

    @Test
    fun neutralOccluderMustNotPublishTruncatedCandyCost() {
        val bitmap = image(Color.GRAY)
        try {
            val document = layout()
            val witnesses = ActionResourceMatcher.observe(bitmap, document)
            val read = evolveEvidence(document, document.lines, 200,
                ExtractionContext(actionResources = witnesses)).cost.read
            assertEquals("Covered 200 represented by truncated OCR20 must remain unreadable",
                FieldReadStatus.VISIBLE_UNREADABLE, read.status)
        } finally { bitmap.recycle() }
    }

    @Test
    fun saturatedOccluderControlRemainsUnreadable() {
        val bitmap = image(Color.RED)
        try {
            val document = layout()
            val read = evolveEvidence(document, document.lines, 200,
                ExtractionContext(actionResources = ActionResourceMatcher.observe(bitmap, document))).cost.read
            assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, read.status)
        } finally { bitmap.recycle() }
    }

    @Test
    fun uniformNeutralActionBackgroundRemainsReadable() {
        val bitmap = image(Color.GRAY, Color.GRAY)
        try {
            val document = layout()
            val witnesses = ActionResourceMatcher.observe(bitmap, document)
            assertTrue(witnesses.single { it.bounds == cost }.amountClear)
            val read = evolveEvidence(document, document.lines, 200,
                ExtractionContext(actionResources = witnesses)).cost.read
            assertEquals(FieldReadStatus.READ, read.status)
            assertEquals(20, read.value)
        } finally { bitmap.recycle() }
    }

    @Test
    fun partialNeutralCoverAtDigitEdgeRemainsUnreadable() {
        val bitmap = image(Color.WHITE)
        try {
            for (y in cost.centerY() until cost.bottom) {
                for (x in cost.right until cost.right + 10) bitmap.setPixel(x, y, Color.GRAY)
            }
            val document = layout()
            val read = evolveEvidence(document, document.lines, 200,
                ExtractionContext(actionResources = ActionResourceMatcher.observe(bitmap, document))).cost.read
            assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, read.status)
        } finally { bitmap.recycle() }
    }
}
