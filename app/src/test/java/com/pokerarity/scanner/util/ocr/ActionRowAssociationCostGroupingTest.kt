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

    private fun powerUpRead(
        tokens: List<Pair<String, IntRange>>,
        width: Int = 200,
        labelLeft: Int = 15
    ): FieldRead<Int> {
        val label = block("POWER UP", labelLeft, labelLeft + 45)
        val layout = MLKitOcrProvider.Layout(
            lines = listOf(label),
            elements = listOf(label) + tokens.map { (text, range) ->
                block(text, range.first, range.last)
            }
        )
        return powerUpEvidence(layout, layout.lines, width, context = ExtractionContext()).cost.read
    }

    @Test
    fun adjacentThousandsFragmentsAreReconstructedAsOneCost() {
        val read = powerUpRead(listOf("2" to (100..110), "500" to (113..143)))

        assertEquals(FieldReadStatus.READ, read.status)
        assertEquals(2500, read.value)
    }

    @Test
    fun separatedNumericTokensRemainConflictingEvidence() {
        val read = powerUpRead(listOf("2" to (100..110), "500" to (140..150)))

        assertEquals(FieldReadStatus.CONFLICT, read.status)
        assertEquals(null, read.value)
    }

    @Test
    fun groupedAmountOutsidePowerUpDomainRemainsUnreadable() {
        val read = powerUpRead(listOf("31" to (100..115), "000" to (118..148)))

        assertEquals(FieldReadStatus.VISIBLE_UNREADABLE, read.status)
        assertEquals(null, read.value)
    }

    @Test
    fun zeroLeadingGroupDoesNotBecomeAValidShorterCost() {
        val read = powerUpRead(listOf("0" to (100..110), "500" to (113..143)))

        assertEquals(FieldReadStatus.CONFLICT, read.status)
        assertEquals(null, read.value)
    }

    @Test
    fun reconstructedCostConflictsWithAnotherInRegionNumericCandidate() {
        val read = powerUpRead(
            listOf("2" to (100..110), "500" to (113..143), "25" to (145..151))
        )

        assertEquals(FieldReadStatus.CONFLICT, read.status)
        assertEquals(null, read.value)
    }

    @Test
    fun splitCostDoesNotJoinRightHandInventoryToken() {
        val read = powerUpRead(
            listOf("2" to (180..190), "500" to (193..226), "123" to (228..258)),
            width = 300,
            labelLeft = 100
        )

        assertEquals(FieldReadStatus.READ, read.status)
        assertEquals(2500, read.value)
    }
}
