package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/** Exact scaled OCR boxes from two independently labelled Farfetch'd detail fixtures. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class DetailStorageUiWitnessTest {
    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        MLKitOcrProvider.RecognizedBlock(text, Rect(left, top, right, bottom))

    private fun fields(
        type: Set<String>? = setOf("fighting"),
        cost: Int? = 1300,
        candyStatus: FieldRead<String> = FieldRead.read("Farfetch'd", 2)
    ) = AnchoredScreenText.Fields(
        name = null, nameRaw = null, cp = null, hp = null,
        candy = "Farfetch'd", powerUpCost = cost, types = type,
        detailScreen = true, hpRect = null, nameRect = null,
        candyRect = null, costRect = null, candyRead = candyStatus,
        powerUpRead = cost?.let { FieldRead.read(it) } ?: FieldRead.missing("no_cost"),
        evolveRead = FieldRead.read(50), evolutionCandyCost = 50
    )

    private fun detailLines(offset: Int = 0) = listOf(
        block("WEIGHT", 146, 139 + offset, 252, 158 + offset),
        block("FIGHTING", 404, 133 + offset, 525, 160 + offset),
        block("HEIGHT", 668, 138 + offset, 765, 158 + offset),
        block("FARFETCH'D", 397, 345 + offset, 554, 364 + offset),
        block("CANDY", 431, 375 + offset, 521, 394 + offset),
        block("FARFETCH'D", 672, 341 + offset, 827, 367 + offset),
        block("CANDY XL", 687, 376 + offset, 815, 395 + offset),
        block("POWER UP", 143, 483 + offset, 341, 509 + offset),
        block("1,300", 512, 478 + offset, 600, 515 + offset),
        block("EVOLVE", 228, 613 + offset, 373, 638 + offset)
    )

    @Test fun scrolledFarfetchdMatchesStrictUiArrangement() {
        assertTrue(DetailStorageUiWitness.corroborates(
            MLKitOcrProvider.Layout(detailLines(), emptyList()), fields(), 900, 1950))
    }

    @Test fun normalFarfetchdMatchesSameRelativeArrangement() {
        assertTrue(DetailStorageUiWitness.corroborates(
            MLKitOcrProvider.Layout(detailLines(965), emptyList()), fields(), 900, 1950))
    }

    @Test fun incompleteOrFailedDocumentNeverCorroborates() {
        val lines = detailLines()
        for (status in listOf(OcrDocumentStatus.FAILED, OcrDocumentStatus.EMPTY)) {
            assertFalse(DetailStorageUiWitness.corroborates(
                MLKitOcrProvider.Layout(lines, emptyList(), status), fields(), 900, 1950))
        }
        for (excluded in listOf("EVOLVE", "CANDY XL", "WEIGHT", "HEIGHT", "POWER UP")) {
            assertFalse(excluded, DetailStorageUiWitness.corroborates(
                MLKitOcrProvider.Layout(lines.filterNot { it.text == excluded }, emptyList()), fields(), 900, 1950))
        }
    }

    @Test fun missingOrConflictingFieldEvidenceNeverCorroborates() {
        val layout = MLKitOcrProvider.Layout(detailLines(), emptyList())
        assertFalse(DetailStorageUiWitness.corroborates(layout, fields(cost = null), 900, 1950))
        assertFalse(DetailStorageUiWitness.corroborates(layout, fields(type = null), 900, 1950))
        assertFalse(DetailStorageUiWitness.corroborates(
            layout, fields(candyStatus = FieldRead.missing("no_candy")), 900, 1950
        ))
        assertFalse(DetailStorageUiWitness.corroborates(
            layout, fields(candyStatus = FieldRead.conflict("multiple_candy_families", 2)), 900, 1950
        ))
        assertFalse(DetailStorageUiWitness.corroborates(layout, fields(
            candyStatus = FieldRead.read("Farfetch'd", 1)), 900, 1950))
    }

    @Test fun conflictingActionCostDoesNotEraseTheGeometricDetailWitness() {
        val conflicted = fields(cost = null).copy(
            numericConflict = true,
            powerUpRead = FieldRead.conflict("valid_and_unreadable_cost_candidates", 2)
        )
        assertTrue(DetailStorageUiWitness.corroborates(
            MLKitOcrProvider.Layout(detailLines(), emptyList()), conflicted, 900, 1950))
        assertFalse(DetailStorageUiWitness.corroborates(
            MLKitOcrProvider.Layout(detailLines(), emptyList()), conflicted.copy(
                powerUpRead = FieldRead.conflict("multiple_power_up_actions", 2)
            ), 900, 1950))
        assertFalse(DetailStorageUiWitness.corroborates(
            MLKitOcrProvider.Layout(detailLines(), emptyList()), conflicted.copy(
                identityBlockingNumericConflict = true
            ), 900, 1950))
    }

    @Test fun provisionalFalseHpBarMustNotHideTheRealPowerUpRow() {
        val lines = detailLines()
        val cost = block("1,300", 512, 478, 600, 515)
        val layout = MLKitOcrProvider.Layout(lines, listOf(cost))
        val falseBar = Rect(180, 508, 418, 532)
        val invalidNameBand = ScreenGeometryBuilder.deriveNameBand(falseBar, 900, 1950)
        val falselyAnchored = powerUpEvidence(
            layout, lines, 900, ExtractionContext(nameBand = invalidNameBand)
        )
        val barFreeProbe = powerUpEvidence(layout, lines, 900, ExtractionContext())
        assertEquals(FieldReadStatus.MISSING_NOT_VISIBLE, falselyAnchored.cost.read.status)
        assertEquals("action_not_detected", falselyAnchored.cost.read.reasonCode)
        assertEquals(FieldReadStatus.READ, barFreeProbe.cost.read.status)
        assertEquals(1300, barFreeProbe.cost.value)
    }

    @Test fun swappedRowsAndGenericListTextCannotQualify() {
        val lines = detailLines()
        val swapped = lines.map {
            if (it.text == "EVOLVE") it.copy(bounds = Rect(228, 400, 373, 425))
            else it
        }
        assertFalse(DetailStorageUiWitness.corroborates(
            MLKitOcrProvider.Layout(swapped, emptyList()), fields(), 900, 1950))
        val list = listOf(
            block("POKÉMON", 350, 50, 530, 85), block("Search", 370, 275, 525, 313),
            block("CP 337", 60, 480, 190, 510),
            block("Ponyta", 320, 600, 490, 635)
        )
        assertFalse(DetailStorageUiWitness.corroborates(
            MLKitOcrProvider.Layout(list, emptyList()), fields(), 900, 1950))
    }
}
