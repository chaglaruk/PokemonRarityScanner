package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

/** Synthetic perturbations of independently measured 900-wide OCR line geometry. */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class AlternateCandyInventoryWitnessTest {
    private fun line(text: String, l: Int, t: Int, r: Int, b: Int) =
        MLKitOcrProvider.RecognizedBlock(text, Rect(l, t, r, b))

    private fun fields(family: String, types: Set<String> = setOf("fire"), cost: Int = 2500) =
        AnchoredScreenText.Fields(
            name = null, nameRaw = null, cp = null, hp = null,
            candy = family, powerUpCost = cost, types = types,
            detailScreen = true, hpRect = null, nameRect = null,
            candyRect = null, costRect = null,
            candyRead = FieldRead.read(family, 2),
            powerUpRead = FieldRead.read(cost), evolveRead = FieldRead.read(25)
        )

    private fun standardLines() = listOf(
        line("WEIGHT", 146, 900, 252, 919),
        line("FIRE", 410, 900, 510, 920),
        line("HEIGHT", 668, 900, 765, 920),
        line("TORCHIC CANDY", 530, 1088, 744, 1107),
        line("TORCHIC CANDY XL", 137, 1239, 389, 1258),
        line("POWER UP", 143, 1400, 341, 1426),
        line("EVOLVE", 228, 1600, 373, 1625)
    )

    private fun horizontalLines() = listOf(
        line("WEIGHT", 146, 300, 252, 320),
        line("NORMAL FLYING", 390, 300, 530, 320),
        line("HEIGHT", 668, 300, 765, 320),
        line("PIKIPEK CANDY", 376, 478, 574, 497),
        line("PIKIPEK CANDY XL", 633, 479, 868, 498),
        line("POWER UP", 143, 700, 341, 726),
        line("EVOLVE", 228, 840, 373, 865)
    )

    private fun wrappedLines() = listOf(
        line("WEIGHT", 146, 600, 252, 620),
        line("NORMAL", 410, 600, 510, 620),
        line("HEIGHT", 668, 600, 765, 620),
        line("SKWOVET CANDY", 361, 847, 589, 866),
        line("SKWOVET CANDY", 636, 848, 862, 867),
        line("XL", 736, 878, 763, 896),
        line("POWER UP", 143, 1100, 341, 1126),
        line("EVOLVE", 228, 1260, 373, 1285)
    )

    private fun witnessed(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        fields: AnchoredScreenText.Fields,
        status: OcrDocumentStatus = OcrDocumentStatus.SUCCESS
    ): Boolean = AlternateCandyInventoryWitness.corroborates(
        MLKitOcrProvider.Layout(lines, emptyList(), status), fields, 900, 1950
    )

    @Test fun observedPrefixedStackedAndHorizontalLabelsCanQualify() {
        assertTrue(witnessed(standardLines(), fields("Torchic")))
        assertTrue(witnessed(horizontalLines(), fields("Pikipek", setOf("normal", "flying"), 1000)))
    }

    @Test fun detachedXlMustAssociateWithExactlyOneActualCandyParent() {
        assertTrue(witnessed(wrappedLines(), fields("Skwovet", setOf("normal"), 5000)))
        val misplaced = wrappedLines().map {
            if (it.text == "XL") it.copy(bounds = Rect(600, 878, 627, 896)) else it
        }
        assertFalse(witnessed(misplaced, fields("Skwovet", setOf("normal"), 5000)))
        assertFalse(witnessed(
            wrappedLines() + line("XL", 736, 899, 763, 918),
            fields("Skwovet", setOf("normal"), 5000)
        ))
    }

    @Test fun foreignOrFuzzyFamiliesAndUnrelatedCandyRolesFailClosed() {
        val lines = standardLines()
        assertFalse(witnessed(lines, fields("Pikipek")))
        assertFalse(witnessed(lines.map {
            if (it.text == "TORCHIC CANDY") it.copy(text = "TORCH CANDY") else it
        }, fields("Torchic")))
        assertFalse(witnessed(lines.map {
            if (it.text == "TORCHIC CANDY XL") it.copy(text = "CANDY XL") else it
        }, fields("Torchic")))
        assertFalse(witnessed(lines + line("TORCHIC CANDY", 330, 1050, 500, 1070), fields("Torchic")))
    }

    @Test fun missingOrContradictoryTypedEvidenceNeverQualifies() {
        val base = fields("Torchic")
        val lines = standardLines()
        assertFalse(witnessed(lines, base.copy(
            numericConflict = true, identityBlockingNumericConflict = true
        )))
        assertFalse(witnessed(lines, base.copy(detailScreen = false)))
        assertFalse(witnessed(lines, base.copy(candyRead = FieldRead.missing("no_candy"))))
        assertFalse(witnessed(lines, base.copy(candyRead = FieldRead.read("Torchic", 1))))
        assertFalse(witnessed(lines, base.copy(candyRead = FieldRead.conflict("family", 2))))
        assertFalse(witnessed(lines, base.copy(powerUpRead = FieldRead.missing("no_cost"))))
        assertFalse(witnessed(lines, base.copy(powerUpRead = FieldRead.read(5000))))
        assertFalse(witnessed(lines, base.copy(evolveRead = FieldRead.conflict("multiple_evolve_actions", 2))))
        assertFalse(witnessed(lines, base.copy(cpRead = FieldRead.conflict("cp", 2))))
        assertFalse(witnessed(lines, base.copy(types = null)))
    }

    @Test fun conflictingActionCostDoesNotEraseTheObservedInventoryRole() {
        val conflicted = fields("Torchic").copy(
            powerUpCost = null,
            numericConflict = true,
            powerUpRead = FieldRead.conflict("valid_and_unreadable_cost_candidates", 2)
        )
        assertTrue(witnessed(standardLines(), conflicted))
    }

    @Test fun missingWrongOrderOrOffCanvasUiLabelsAreNotWitnesses() {
        val lines = standardLines()
        val fields = fields("Torchic")
        for (missing in listOf("WEIGHT", "HEIGHT", "POWER UP", "EVOLVE", "FIRE")) {
            assertFalse(missing, witnessed(lines.filterNot { it.text == missing }, fields))
        }
        assertFalse(witnessed(lines.map {
            if (it.text == "EVOLVE") it.copy(bounds = Rect(228, 1300, 373, 1325)) else it
        }, fields))
        assertFalse(witnessed(lines.map {
            if (it.text == "TORCHIC CANDY XL") it.copy(bounds = Rect(137, -1, 389, 20)) else it
        }, fields))
        assertFalse(witnessed(lines + line("EVOLVE", 228, 1700, 373, 1725), fields))
    }

    @Test fun failedAndEmptyDocumentsCannotSupplyRoleEvidence() {
        val lines = standardLines()
        val fields = fields("Torchic")
        assertFalse(witnessed(lines, fields, OcrDocumentStatus.FAILED))
        assertFalse(witnessed(lines, fields, OcrDocumentStatus.EMPTY))
        assertFalse(witnessed(emptyList(), fields))
    }
}
