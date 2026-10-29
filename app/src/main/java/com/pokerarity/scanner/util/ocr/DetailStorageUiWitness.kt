package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import kotlin.math.abs

/**
 * Post-OCR screen-state corroboration, NOT species identity or publication authority.
 *
 * Used only for a conflicting StorageList preflight that would otherwise be terminal.
 * All witnesses must come from one successful OCR document on the same bitmap.
 * The strict combination intentionally excludes detail screens missing an EVOLVE row:
 * unsupported layouts must remain rejected rather than guessed.
 */
internal object DetailStorageUiWitness {
    // A bounded, fail-closed witness deliberately lists many independent mandatory
    // predicates. Keep the audit-visible rejection gates instead of obscuring them.
    @Suppress("CyclomaticComplexMethod", "ComplexCondition", "ReturnCount")
    fun corroborates(
        layout: MLKitOcrProvider.Layout,
        fields: AnchoredScreenText.Fields,
        width: Int,
        height: Int
    ): Boolean {
        if (layout.documentStatus != OcrDocumentStatus.SUCCESS ||
            !fields.detailScreen || fields.identityBlockingNumericConflict ||
            fields.candyRead.status != FieldReadStatus.READ ||
            fields.candyRead.candidateCount < 2 ||
            fields.types.isNullOrEmpty() ||
            !fields.powerUpRead.supportsActionRowGeometry()
        ) return false

        when (fields.powerUpRead.status) {
            FieldReadStatus.READ -> if (fields.powerUpRead.value != fields.powerUpCost) return false
            FieldReadStatus.CONFLICT -> if (fields.powerUpCost != null) return false
            else -> return false
        }

        val lines = layout.lines.filter { it.bounds != null }
        fun unique(text: String): Rect? =
            lines.filter { it.text.trim().equals(text, ignoreCase = true) }
                .singleOrNull()?.bounds

        // Keep the proven generic-label template intact. A missing generic pair
        // may use a separately bounded observed-family inventory-role template.
        val candy = unique("CANDY")
        val candyXl = unique("CANDY XL")
        if (candy == null || candyXl == null) {
            return AlternateCandyInventoryWitness.corroborates(layout, fields, width, height)
        }
        val weight = unique("WEIGHT") ?: return false
        val height = unique("HEIGHT") ?: return false
        val powerUp = lines.filter {
            it.text.filter(Char::isLetter).equals("POWERUP", ignoreCase = true)
        }.singleOrNull()?.bounds ?: return false
        val evolve = unique("EVOLVE") ?: return false

        // Two separate inventory columns, aligned with one another and above actions.
        if (candy.right >= candyXl.left ||
            abs(candy.centerY() - candyXl.centerY()) > maxOf(candy.height(), candyXl.height()) * 2
        ) return false

        // Independent measurements/type row above inventory, then distinct action rows.
        val inventoryTop = minOf(candy.top, candyXl.top)
        val inventoryBottom = maxOf(candy.bottom, candyXl.bottom)
        return maxOf(weight.bottom, height.bottom) < inventoryTop &&
            inventoryBottom < powerUp.top &&
            powerUp.bottom < evolve.top &&
            powerUp.centerX() < width / 2 &&
            evolve.centerX() < width / 2
    }
}
