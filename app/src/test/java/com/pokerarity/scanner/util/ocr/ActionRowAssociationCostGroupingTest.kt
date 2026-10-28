package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ActionRowAssociationCostGroupingTest {
    private fun block(text: String, left: Int, right: Int) =
        MLKitOcrProvider.RecognizedBlock(text, Rect(left, 210, right, 230))

    private fun powerUpRead(first: Pair<String, IntRange>, second: Pair<String, IntRange>): FieldRead<Int> {
        val label = block("POWER UP", 15, 60)
        val layout = MLKitOcrProvider.Layout(
            lines = listOf(label),
            elements = listOf(label, block(first.first, first.second.first, first.second.last),
                block(second.first, second.second.first, second.second.last))
        )
        return powerUpEvidence(layout, layout.lines, width = 200, context = ExtractionContext()).cost.read
    }

    @Test
    fun adjacentThousandsFragmentsAreReconstructedAsOneCost() {
        val read = powerUpRead("2" to (100..110), "500" to (113..143))

        assertEquals(FieldReadStatus.READ, read.status)
        assertEquals(2500, read.value)
    }

    @Test
    fun separatedNumericTokensRemainConflictingEvidence() {
        val read = powerUpRead("2" to (100..110), "500" to (140..150))

        assertEquals(FieldReadStatus.CONFLICT, read.status)
        assertEquals(null, read.value)
    }

    @Test
    fun groupedAmountOutsidePowerUpDomainRemainsUnreadable() {
        val read = powerUpRead("31" to (100..115), "000" to (118..148))

        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, read.status)
        assertEquals(null, read.value)
    }

    @Test
    fun zeroLeadingGroupDoesNotBecomeAValidShorterCost() {
        val read = powerUpRead("0" to (100..110), "500" to (113..143))

        assertEquals(FieldReadStatus.CONFLICT, read.status)
        assertEquals(null, read.value)
    }
}
