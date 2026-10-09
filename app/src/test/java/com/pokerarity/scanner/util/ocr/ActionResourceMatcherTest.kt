package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Constructed geometry checks only; measured screen replay is a separate gate. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActionResourceMatcherTest {
    private val inventory = Rect(100, 120, 120, 140)
    private val cost = Rect(100, 250, 120, 270)
    private fun block(text: String, rect: Rect) = MLKitOcrProvider.RecognizedBlock(text, rect)

    private fun bitmap(clipped: Boolean = false): Bitmap =
        Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (amount in listOf(inventory, cost)) {
                val left = if (clipped) amount.left - amount.height() * 2 else amount.left - 27
                drawGlyph(this, amount.centerY(), left)
            }
        }

    private fun drawGlyph(image: Bitmap, center: Int, left: Int) {
        for (y in center - 6 until center + 6) for (x in left until left + 9) {
            image.setPixel(x, y, Color.rgb(120, 190, 130))
        }
    }

    private fun layout(xl: Boolean = false, labelled: Boolean = true) = MLKitOcrProvider.Layout(
        if (labelled) listOf(block(if (xl) "CANDY XL" else "CANDY", Rect(50, 150, 160, 165))) else emptyList(),
        listOf(block("123", inventory), block("25", cost)))

    @Test
    fun identicalSameFrameGlyphUsesLabelButInventoryNeverBecomesCost() {
        val image = bitmap()
        try {
            val observed = ActionResourceMatcher.observe(image, layout())
            assertEquals(ActionResourceRole.INVENTORY, observed.single { it.bounds == inventory }.role)
            val action = observed.single { it.bounds == cost }
            assertEquals(ActionResourceKind.CANDY, action.kind)
            assertEquals(ActionResourceRole.COST, action.role)
        } finally { image.recycle() }
    }

    @Test
    fun xlLabelDoesNotSupplyOrdinaryCandyEvidence() {
        val image = bitmap()
        try {
            assertEquals(ActionResourceKind.CANDY_XL,
                ActionResourceMatcher.observe(image, layout(xl = true)).single { it.bounds == cost }.kind)
        } finally { image.recycle() }
    }

    @Test
    fun neighboringWrappedXlColumnCannotRelabelOrdinaryCandy() {
        val image = bitmap()
        val labels = listOf(block("CANDY", Rect(50, 150, 190, 165)), block("XL", Rect(170, 166, 190, 177)))
        try {
            assertEquals(ActionResourceKind.CANDY,
                ActionResourceMatcher.observe(image, layout().copy(lines = labels)).single { it.bounds == cost }.kind)
        } finally { image.recycle() }
    }

    @Test
    fun absentLabelAndClippedGlyphRemainUnknown() {
        for (clipped in listOf(false, true)) {
            val image = bitmap(clipped)
            try {
                val observed = ActionResourceMatcher.observe(image, layout(labelled = clipped))
                assertEquals(ActionResourceKind.UNKNOWN, observed.single { it.bounds == cost }.kind)
            } finally { image.recycle() }
        }
    }

    @Test
    fun saturatedOccluderBesideRecognizedDigitsBlocksAmount() {
        val image = bitmap()
        try {
            for (y in cost.top until cost.bottom) for (x in cost.right until cost.right + cost.height() / 2) {
                image.setPixel(x, y, Color.RED)
            }
            assertFalse(ActionResourceMatcher.observe(image, layout()).single { it.bounds == cost }.amountClear)
        } finally { image.recycle() }
    }
}
