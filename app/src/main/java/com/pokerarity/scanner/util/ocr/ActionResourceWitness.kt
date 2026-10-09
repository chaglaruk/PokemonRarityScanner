package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect


enum class ActionResourceKind { CANDY, CANDY_XL, SPECIAL_ITEM, MEGA_ENERGY, STARDUST, UNKNOWN }
enum class ActionResourceRole { INVENTORY, COST, UNKNOWN }

data class ActionResourceWitness(
    val bounds: Rect,
    val kind: ActionResourceKind,
    val role: ActionResourceRole,
    val amountClear: Boolean
)

/**
 * Same-frame glyph correspondence to OCR-labelled inventory resources. No templates,
 * species-specific assets or external registry. Unknown/clipped glyphs remain unknown.
 * Initial development thresholds require broader independent validation before merge.
 */
@Suppress("MagicNumber")
internal object ActionResourceMatcher {
    private const val MIN_SIMILARITY = 0.95
    private const val MIN_MARGIN = 0.03
    private val amountPattern = Regex("[0-9]+(?:,[0-9]{3})*")
    private data class Reference(val amount: Rect, val kind: ActionResourceKind, val glyph: DoubleArray)

    fun observe(bitmap: Bitmap, layout: MLKitOcrProvider.Layout): List<ActionResourceWitness> {
        val numbers = layout.elements.filter { it.bounds != null && amountPattern.matches(it.text.trim()) }
        val references = references(bitmap, layout, numbers)
        return numbers.map { number ->
            val rect = number.bounds!!
            val inventory = references.firstOrNull { it.amount == rect }
            val glyph = ResourceGlyphDescriptor.descriptor(bitmap, rect)
            val matches = references.mapNotNull { reference ->
                glyph?.let { reference.kind to it.indices.sumOf { index -> it[index] * reference.glyph[index] } }
            }.groupBy { it.first }.map { (kind, scores) -> kind to scores.maxOf { it.second } }
                .sortedByDescending { it.second }
            val best = matches.firstOrNull()
            val kind = best?.takeIf { it.second >= MIN_SIMILARITY &&
                it.second - (matches.getOrNull(1)?.second ?: 0.0) >= MIN_MARGIN }?.first
            ActionResourceWitness(Rect(rect), inventory?.kind ?: kind ?: ActionResourceKind.UNKNOWN,
                if (inventory != null) ActionResourceRole.INVENTORY else if (kind != null) ActionResourceRole.COST
                else ActionResourceRole.UNKNOWN, ResourceGlyphDescriptor.amountClear(bitmap, rect))
        }.distinctBy { it.bounds }
    }

    private fun references(
        bitmap: Bitmap,
        layout: MLKitOcrProvider.Layout,
        numbers: List<MLKitOcrProvider.RecognizedBlock>
    ): List<Reference> {
        val lines = layout.lines.filter { it.bounds != null }
        val candyTop = lines.filter { it.text.contains("CANDY", true) }.minOfOrNull { it.bounds!!.top }
        val powerUpTop = lines.filter { it.text.filter(Char::isLetter).equals("POWERUP", true) }
            .minOfOrNull { it.bounds!!.top }
        return lines.flatMap { label ->
            val rect = label.bounds!!
            val kind = inventoryKind(label, candyTop, powerUpTop) ?: return@flatMap emptyList()
            val candidates = numbers.filter { number ->
                val candidate = number.bounds!!
                candidate.bottom < rect.top && rect.top - candidate.bottom < rect.height() * 4 &&
                    candidate.centerX() in rect.left..rect.right
            }
            // ML Kit may join two candy-column labels into one line. Keep both
            // inventory amounts, associating a wrapped XL token to its amount column.
            val selected = if (kind == ActionResourceKind.CANDY) candidates else
                listOfNotNull(candidates.maxByOrNull { it.bounds!!.bottom })
            selected.mapNotNull { number ->
                val amount = number.bounds!!
                val typed = if (kind == ActionResourceKind.CANDY) {
                    candyKind(label, amount, lines, selected.size)
                } else kind
                ResourceGlyphDescriptor.descriptor(bitmap, amount)?.let { Reference(Rect(amount), typed, it) }
            }
        }.distinctBy { it.amount }
    }

    private fun inventoryKind(
        label: MLKitOcrProvider.RecognizedBlock,
        candyTop: Int?, powerUpTop: Int?
    ): ActionResourceKind? {
        val text = label.text.trim().uppercase()
        val rect = label.bounds!!
        if (text == "XL") return null // Continuation of another column's label, never an item reference.
        return when {
            text.contains("CANDY") -> ActionResourceKind.CANDY
            text == "STARDUST" -> ActionResourceKind.STARDUST
            text.contains("MEGA ENERGY") -> ActionResourceKind.MEGA_ENERGY
            candyTop != null && powerUpTop != null && rect.top > candyTop && rect.bottom < powerUpTop &&
                text.matches(Regex("[A-Z]+(?: [A-Z]+)*")) -> ActionResourceKind.SPECIAL_ITEM
            else -> null
        }
    }

    private fun candyKind(label: MLKitOcrProvider.RecognizedBlock, amount: Rect,
        lines: List<MLKitOcrProvider.RecognizedBlock>, count: Int): ActionResourceKind {
        val rect = label.bounds!!
        val wrappedXl = lines.any { other ->
            val bounds = other.bounds!!
            other.text.trim().equals("XL", true) && bounds.top >= rect.bottom &&
                bounds.top - rect.bottom < rect.height() * 3 &&
                kotlin.math.abs(bounds.centerX() - amount.centerX()) <= amount.height() * 2
        }
        return when {
            wrappedXl || count == 1 && label.text.contains("XL", true) -> ActionResourceKind.CANDY_XL
            label.text.contains("XL", true) -> ActionResourceKind.UNKNOWN
            else -> ActionResourceKind.CANDY
        }
    }

}
