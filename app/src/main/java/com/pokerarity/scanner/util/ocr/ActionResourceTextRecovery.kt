package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs

/** Re-read visible numeric pixels when OCR merged a resource glyph and the cost.
 * No character-to-digit substitution, inventory substitution or hidden-digit inference.
 */
@Suppress("MagicNumber")
internal object ActionResourceTextRecovery {
    private val merged = Regex("[A-Za-z]{1,2}([0-9]{1,4})")

    suspend fun recover(
        bitmap: Bitmap,
        layout: MLKitOcrProvider.Layout,
        nameBand: Rect?,
        recognize: suspend (Bitmap) -> MLKitOcrProvider.Layout
    ): MLKitOcrProvider.Layout {
        val lines = layout.lines.filter { it.bounds != null }
        val anchor = evolveEvidence(layout, lines, bitmap.width, ExtractionContext(nameBand = nameBand))
            .anchor?.bounds ?: return layout
        val candidates = layout.elements.filter { token ->
            mergedCandidate(token, anchor, bitmap.width)
        }
        // Ambiguous rows remain blocking, and the number of extra OCR calls is bounded.
        val token = candidates.singleOrNull()
        val recovered = token?.let { recoverToken(bitmap, layout, it, recognize) }
        return if (recovered == null) layout else
            layout.copy(elements = layout.elements.map { if (it == token) recovered else it })
    }

    private fun mergedCandidate(token: MLKitOcrProvider.RecognizedBlock, anchor: Rect, width: Int): Boolean {
        val rect = token.bounds ?: return false
        val textShape = merged.matches(token.text.trim()) && rect.width() >= rect.height() * 2
        val inRow = rect.left > anchor.right && rect.right <= anchor.left + width * 0.68 &&
            abs(rect.centerY() - anchor.centerY()) < maxOf(rect.height(), anchor.height()) * 0.75
        return textShape && inRow
    }

    private suspend fun recoverToken(
        bitmap: Bitmap,
        layout: MLKitOcrProvider.Layout,
        token: MLKitOcrProvider.RecognizedBlock,
        recognize: suspend (Bitmap) -> MLKitOcrProvider.Layout
    ): MLKitOcrProvider.RecognizedBlock? {
        val original = requireNotNull(token.bounds)
        val margin = maxOf(2, original.height() / 8)
        val number = numericPixels(bitmap, original, margin) ?: return null
        val suffix = requireNotNull(merged.matchEntire(token.text.trim())).groupValues[1]
        val candidate = MLKitOcrProvider.RecognizedBlock(suffix, number)
        val crop = Rect(number).apply { inset(-margin, -margin) }
        val visibleCandy = clearCandyCost(bitmap, layout, candidate)
        return if (visibleCandy && Rect(0, 0, bitmap.width, bitmap.height).contains(crop)) {
            independentRead(bitmap, crop, candidate, recognize)
        } else null
    }

    private fun numericPixels(bitmap: Bitmap, original: Rect, margin: Int): Rect? {
        // A candy glyph can be taller than the digit box returned for a merged
        // token. Inspect the complete glyph without widening the numeric row.
        val box = Rect(original).apply { inset(-margin, -original.height() / 2) }
        if (!Rect(0, 0, bitmap.width, bitmap.height).contains(box)) return null
        val pieces = ResourceGlyphDescriptor.foregroundComponents(bitmap, box)
        val glyph = pieces.firstOrNull()
        val interior = Rect(box).apply { inset(1, 1) }
        val completeGlyph = glyph != null && interior.contains(glyph)
        val completeNumber = pieces.drop(1).all { interior.contains(it) }
        val completeComponents = completeGlyph && completeNumber
        return if (pieces.size >= 2 && completeComponents) {
            Rect(pieces[1]).apply { pieces.drop(2).forEach { union(it) } }
                .takeIf { it.left - requireNotNull(glyph).right >= margin }
        } else null
    }

    private fun clearCandyCost(bitmap: Bitmap, layout: MLKitOcrProvider.Layout,
        candidate: MLKitOcrProvider.RecognizedBlock): Boolean {
        val witness = ActionResourceMatcher.observe(bitmap, layout.copy(elements = layout.elements + candidate))
            .singleOrNull { it.bounds == candidate.bounds }
        return witness?.kind == ActionResourceKind.CANDY && witness.role == ActionResourceRole.COST &&
            witness.amountClear
    }

    private suspend fun independentRead(bitmap: Bitmap, crop: Rect,
        candidate: MLKitOcrProvider.RecognizedBlock,
        recognize: suspend (Bitmap) -> MLKitOcrProvider.Layout): MLKitOcrProvider.RecognizedBlock? {
        val pixels = Bitmap.createBitmap(bitmap, crop.left, crop.top, crop.width(), crop.height())
        return try {
            val result = recognize(pixels).elements.singleOrNull()
                ?.takeIf { it.text.trim().matches(Regex("[0-9]{1,4}")) }
            candidate.takeIf { result?.text?.trim() == candidate.text }
        } finally { if (pixels !== bitmap) pixels.recycle() }
    }
}
