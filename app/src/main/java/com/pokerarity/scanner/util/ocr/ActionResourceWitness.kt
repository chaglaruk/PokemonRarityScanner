package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.sqrt

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
            val glyph = descriptor(bitmap, rect)
            val matches = references.mapNotNull { reference ->
                glyph?.let { reference.kind to similarity(it, reference.glyph) }
            }.groupBy { it.first }.map { (kind, scores) -> kind to scores.maxOf { it.second } }
                .sortedByDescending { it.second }
            val best = matches.firstOrNull()
            val kind = best?.takeIf { it.second >= MIN_SIMILARITY &&
                it.second - (matches.getOrNull(1)?.second ?: 0.0) >= MIN_MARGIN }?.first
            ActionResourceWitness(Rect(rect), inventory?.kind ?: kind ?: ActionResourceKind.UNKNOWN,
                if (inventory != null) ActionResourceRole.INVENTORY else if (kind != null) ActionResourceRole.COST
                else ActionResourceRole.UNKNOWN, amountClear(bitmap, rect))
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
        return lines.mapNotNull { label ->
            val rect = label.bounds!!
            val kind = inventoryKind(label, lines, candyTop, powerUpTop) ?: return@mapNotNull null
            val amount = numbers.filter { number ->
                val candidate = number.bounds!!
                candidate.bottom < rect.top && rect.top - candidate.bottom < rect.height() * 4 &&
                    candidate.centerX() in rect.left..rect.right
            }.maxByOrNull { it.bounds!!.bottom }?.bounds ?: return@mapNotNull null
            descriptor(bitmap, amount)?.let { Reference(Rect(amount), kind, it) }
        }.distinctBy { it.amount }
    }

    private fun inventoryKind(
        label: MLKitOcrProvider.RecognizedBlock,
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        candyTop: Int?, powerUpTop: Int?
    ): ActionResourceKind? {
        val text = label.text.trim().uppercase()
        val rect = label.bounds!!
        val xl = text.contains("XL") || lines.any { other ->
            val bounds = other.bounds!!
            other.text.trim().equals("XL", true) && bounds.top >= rect.bottom &&
                bounds.top - rect.bottom < rect.height() * 3 && bounds.centerX() in rect.left..rect.right
        }
        return when {
            text.contains("CANDY") -> if (xl) ActionResourceKind.CANDY_XL else ActionResourceKind.CANDY
            text == "STARDUST" -> ActionResourceKind.STARDUST
            text.contains("MEGA ENERGY") -> ActionResourceKind.MEGA_ENERGY
            candyTop != null && powerUpTop != null && rect.top > candyTop && rect.bottom < powerUpTop &&
                text.matches(Regex("[A-Z]+(?: [A-Z]+)*")) -> ActionResourceKind.SPECIAL_ITEM
            else -> null
        }
    }

    private fun descriptor(bitmap: Bitmap, amount: Rect): DoubleArray? {
        val height = amount.height()
        if (height < 2) return null
        val box = Rect(amount.left - height * 2, amount.centerY() - height,
            amount.left - height / 10, amount.centerY() + height)
        if (box.left < 0 || box.top < 0 || box.right > bitmap.width || box.bottom > bitmap.height) return null
        val background = background(bitmap, box)
        val foreground = Rect(box.right, box.bottom, box.left, box.top)
        for (y in box.top until box.bottom) for (x in box.left until box.right) {
            if (distance(bitmap.getPixel(x, y), background) > 1600) {
                foreground.left = minOf(foreground.left, x)
                foreground.top = minOf(foreground.top, y)
                foreground.right = maxOf(foreground.right, x + 1)
                foreground.bottom = maxOf(foreground.bottom, y + 1)
            }
        }
        if (foreground.isEmpty || foreground.left == box.left || foreground.top == box.top ||
            foreground.right == box.right || foreground.bottom == box.bottom) return null
        val values = DoubleArray(12 * 12 * 3)
        for (y in 0 until 12) for (x in 0 until 12) {
            val pixel = bitmap.getPixel(foreground.left + x * foreground.width() / 12,
                foreground.top + y * foreground.height() / 12)
            if (distance(pixel, background) > 1600) for (channel in 0..2) {
                values[(y * 12 + x) * 3 + channel] = (background[channel] - component(pixel, channel)).toDouble()
            }
        }
        val norm = sqrt(values.sumOf { it * it })
        return if (norm == 0.0) null else values.map { it / norm }.toDoubleArray()
    }

    private fun amountClear(bitmap: Bitmap, rect: Rect): Boolean {
        val clearance = Rect(rect.right, rect.top, rect.right + rect.height() / 2, rect.bottom)
        if (clearance.right > bitmap.width || clearance.bottom > bitmap.height || clearance.top < 0) return false
        // A saturated overlay immediately beside the last recognized digit can hide
        // trailing digits. Chevrons and ordinary action backgrounds are low-chroma.
        var occluded = 0
        for (y in clearance.top until clearance.bottom) for (x in clearance.left until clearance.right) {
            val pixel = bitmap.getPixel(x, y)
            val channels = (0..2).map { component(pixel, it) }
            if (channels.max() - channels.min() > 100) occluded++
        }
        return occluded * 10 < clearance.width() * clearance.height()
    }

    private fun background(bitmap: Bitmap, rect: Rect): IntArray {
        val corners = listOf(bitmap.getPixel(rect.left, rect.top), bitmap.getPixel(rect.right - 1, rect.top),
            bitmap.getPixel(rect.left, rect.bottom - 1), bitmap.getPixel(rect.right - 1, rect.bottom - 1))
        return IntArray(3) { channel -> corners.map { component(it, channel) }.sorted()[1] }
    }
    private fun component(pixel: Int, channel: Int): Int = (pixel shr ((2 - channel) * 8)) and 255
    private fun distance(pixel: Int, background: IntArray): Int = (0..2).sumOf { channel ->
        val delta = component(pixel, channel) - background[channel]
        delta * delta
    }
    private fun similarity(a: DoubleArray, b: DoubleArray): Double = a.indices.sumOf { a[it] * b[it] }
}
