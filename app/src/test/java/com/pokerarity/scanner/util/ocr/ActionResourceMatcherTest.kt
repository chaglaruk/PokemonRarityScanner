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

    private fun bitmap(): Bitmap =
        Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.WHITE)
            for (amount in listOf(inventory, cost)) {
                val left = amount.left - 27
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
    fun absentLabelRemainsUnknown() {
        val image = bitmap()
        try {
            assertEquals(ActionResourceKind.UNKNOWN,
                ActionResourceMatcher.observe(image, layout(labelled = false)).single { it.bounds == cost }.kind)
        } finally { image.recycle() }
    }

    @Test
    fun glyphClippedByFrameBoundaryRemainsUnknown() {
        val image = Bitmap.createBitmap(200, 400, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val edgeInventory = Rect(30, 120, 50, 140)
        val edgeCost = Rect(30, 250, 50, 270)
        try {
            for (y in 124 until 136) for (x in 0 until 4) image.setPixel(x, y, Color.GREEN)
            for (y in 254 until 266) for (x in 0 until 4) image.setPixel(x, y, Color.GREEN)
            val document = MLKitOcrProvider.Layout(listOf(block("CANDY", Rect(10, 150, 160, 165))),
                listOf(block("123", edgeInventory), block("25", edgeCost)))
            assertEquals(ActionResourceKind.UNKNOWN,
                ActionResourceMatcher.observe(image, document).single { it.bounds == edgeCost }.kind)
        } finally { image.recycle() }
    }

    @Test
    fun clippedAdjacentDigitDoesNotBecomePartOfCompleteResourceGlyph() {
        val image = bitmap()
        try {
            for (y in 240 until 280) for (x in 40 until 100) image.setPixel(x, y, Color.WHITE)
            drawGlyph(image, cost.centerY(), 64)
            for (y in 254 until 267) for (x in 90 until 101) image.setPixel(x, y, Color.BLACK)
            val witness = ActionResourceMatcher.observe(image, layout()).single { it.bounds == cost }
            assertEquals(ActionResourceKind.CANDY, witness.kind)
            assertEquals(ActionResourceRole.COST, witness.role)
        } finally { image.recycle() }
    }

    @Test
    fun indistinguishableCandyAndXlReferencesRemainUnknown() {
        val image = bitmap()
        try {
            drawGlyph(image, 130, 133)
            val document = MLKitOcrProvider.Layout(listOf(
                block("CANDY", Rect(80, 150, 125, 165)), block("CANDY XL", Rect(140, 150, 190, 165))),
                listOf(block("123", inventory), block("90", Rect(160, 120, 180, 140)), block("25", cost)))
            val witness = ActionResourceMatcher.observe(image, document).single { it.bounds == cost }
            assertEquals(ActionResourceKind.UNKNOWN, witness.kind)
            assertEquals(ActionResourceRole.UNKNOWN, witness.role)
        } finally { image.recycle() }
    }

    @Test
    fun explicitAndWrappedMegaEnergyRemainDistinctFromSpecialItems() {
        val image = bitmap()
        try {
            for (wrapped in listOf(false, true)) {
                val label = block(if (wrapped) "MEDICHAM MEGA" else "MEGA ENERGY", Rect(50, 150, 160, 165))
                val lines = listOf(label) + if (wrapped) listOf(block("ENERGY", Rect(80, 166, 130, 178)))
                    else emptyList()
                val document = layout().copy(lines = lines)
                val witness = ActionResourceMatcher.observe(image, document).single { it.bounds == cost }
                assertEquals(ActionResourceKind.MEGA_ENERGY, witness.kind)
            }
        } finally { image.recycle() }
    }

    @Test
    fun contradictoryInventoryLabelsCannotSupplyAResourceKind() {
        val image = bitmap()
        try {
            val document = layout().copy(lines = listOf(block("CANDY", Rect(50, 150, 160, 165)),
                block("STARDUST", Rect(50, 150, 160, 165))))
            assertEquals(ActionResourceKind.UNKNOWN,
                ActionResourceMatcher.observe(image, document).single { it.bounds == cost }.kind)
        } finally { image.recycle() }
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
