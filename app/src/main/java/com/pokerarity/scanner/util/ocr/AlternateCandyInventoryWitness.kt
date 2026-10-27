package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import kotlin.math.abs

/**
 * A second, fail-closed inventory UI *role* witness for real species-prefixed labels.
 * Never supplies species identity, confidence or a publication decision. The caller
 * supplies one successful OCR layout and the typed fields extracted from that layout.
 */
internal object AlternateCandyInventoryWitness {
    private val candyLabel = Regex("^(.+?)\\s+CANDY(\\s+XL)?$", RegexOption.IGNORE_CASE)
    private val exactPowerUp = Regex("^POWER\\s*UP$", RegexOption.IGNORE_CASE)

    private data class Label(val xl: Boolean, val box: Rect)

    @Suppress("CyclomaticComplexMethod", "ComplexCondition", "ReturnCount", "LongMethod")
    fun corroborates(
        layout: MLKitOcrProvider.Layout,
        fields: AnchoredScreenText.Fields,
        width: Int,
        height: Int
    ): Boolean {
        if (width <= 0 || height <= 0 || layout.documentStatus != OcrDocumentStatus.SUCCESS ||
            !fields.detailScreen || fields.identityBlockingNumericConflict || fields.types.isNullOrEmpty() ||
            fields.candyRead.status != FieldReadStatus.READ || fields.candyRead.candidateCount < 2 ||
            !fields.powerUpRead.supportsActionRowGeometry() ||
            !fields.evolveRead.supportsActionRowGeometry() ||
            fields.cpRead.status == FieldReadStatus.CONFLICT ||
            fields.hpRead.status == FieldReadStatus.CONFLICT
        ) return false
        val family = fields.candyRead.value?.trim()?.takeIf { it.isNotEmpty() } ?: return false
        if (!family.equals(fields.candy?.trim(), ignoreCase = true)) return false
        when (fields.powerUpRead.status) {
            FieldReadStatus.READ -> {
                val cost = fields.powerUpRead.value ?: return false
                if (cost <= 0 || cost != fields.powerUpCost) return false
            }
            FieldReadStatus.CONFLICT -> if (fields.powerUpCost != null) return false
            else -> return false
        }

        val lines = layout.lines
        fun valid(rect: Rect?) = rect != null && rect.left >= 0 && rect.top >= 0 &&
            rect.right > rect.left && rect.bottom > rect.top &&
            rect.right <= width && rect.bottom <= height
        // Invalid bounding boxes may not establish geometry, even when other labels exist.
        if (lines.any { it.bounds != null && !valid(it.bounds) }) return false
        fun exact(text: String): Rect? = lines.filter { it.text.trim().equals(text, true) }
            .singleOrNull()?.bounds?.takeIf(::valid)
        val weight = exact("WEIGHT") ?: return false
        val size = exact("HEIGHT") ?: return false
        val powerUp = lines.filter { exactPowerUp.matches(it.text.trim()) }
            .singleOrNull()?.bounds?.takeIf(::valid) ?: return false
        val evolve = exact("EVOLVE") ?: return false
        if (powerUp.centerX() >= width / 2 || evolve.centerX() >= width / 2 ||
            powerUp.bottom >= evolve.top
        ) return false

        val candyLines = lines.filter { it.text.contains("CANDY", ignoreCase = true) }
        if (candyLines.size != 2) return false
        val labels = candyLines.map { line ->
            val match = candyLabel.matchEntire(line.text.trim()) ?: return false
            val claimed = match.groupValues[1].trim()
            if (!claimed.equals(family, ignoreCase = true)) return false
            Label(match.groupValues[2].isNotBlank(), line.bounds?.takeIf(::valid) ?: return false)
        }
        val explicitXl = labels.filter { it.xl }
        val ordinary = labels.filterNot { it.xl }
        val suffixes = lines.filter { it.text.trim().equals("XL", true) }
        val first: Rect
        val second: Rect
        if (ordinary.size == 1 && explicitXl.size == 1 && suffixes.isEmpty()) {
            first = ordinary.single().box
            second = explicitXl.single().box
        } else if (ordinary.size == 2 && explicitXl.isEmpty() && suffixes.size == 1) {
            val suffix = suffixes.single().bounds?.takeIf(::valid) ?: return false
            val parents = ordinary.filter { parent ->
                val rect = parent.box
                suffix.top >= rect.bottom && suffix.top - rect.bottom <= rect.height() &&
                    suffix.left >= rect.left && suffix.right <= rect.right &&
                    suffix.bottom < powerUp.top
            }
            if (parents.size != 1) return false
            val parent = parents.single().box
            first = ordinary.single { it.box !== parent }.box
            second = Rect(parent.left, parent.top, parent.right, suffix.bottom)
        } else return false

        val horizontal = first.right < second.left &&
            abs(first.centerY() - second.centerY()) <= maxOf(first.height(), second.height()) * 2
        val stacked = first.bottom < second.top
        if (!horizontal && !stacked) return false
        val inventoryTop = minOf(first.top, second.top)
        val inventoryBottom = maxOf(first.bottom, second.bottom)
        if (maxOf(weight.bottom, size.bottom) >= inventoryTop ||
            inventoryBottom >= powerUp.top ||
            powerUp.bottom >= evolve.top) return false

        // Do not accept arbitrary green/list labels: the source document also needs a
        // type label between the measured size columns, on their row, above inventory.
        val typeNames = fields.types.map { it.uppercase() }.toSet()
        val sizeRowHeight = maxOf(weight.height(), size.height())
        val typeWitness = lines.any { line ->
            val box = line.bounds ?: return@any false
            val tokens = line.text.trim().uppercase().split(Regex("[^A-Z]+"))
                .filter { it.isNotBlank() }
            tokens.isNotEmpty() && tokens.all { it in typeNames } &&
                box.centerX() > minOf(weight.centerX(), size.centerX()) &&
                box.centerX() < maxOf(weight.centerX(), size.centerX()) &&
                abs(box.centerY() - weight.centerY()) <= sizeRowHeight * 2 &&
                box.bottom < inventoryTop
        }
        return typeWitness
    }
}
