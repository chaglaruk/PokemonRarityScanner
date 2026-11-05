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

/** Bounded score summary used by local QA replay; never contains OCR text or image pixels. */
internal data class ActionResourceMatchView(
    val bestKind: ActionResourceKind?,
    val bestScore: Double?,
    val runnerUpKind: ActionResourceKind?,
    val runnerUpScore: Double?,
    val margin: Double?
)

internal data class ActionResourceCandidateTrace(
    val bounds: Rect,
    val inventoryReferenceKind: ActionResourceKind?,
    val direct: ActionResourceMatchView,
    val intrinsic: ActionResourceMatchView,
    val selectedKind: ActionResourceKind?,
    val selectedRole: ActionResourceRole,
    val amountClear: Boolean,
    val reasonCode: String
)

internal data class ActionResourceTrace(
    val status: OcrDocumentStatus,
    val numericCandidateCount: Int,
    val referenceCount: Int,
    val candidates: List<ActionResourceCandidateTrace>
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
    private const val MAX_DIAGNOSTIC_CANDIDATES = 12
    private val amountPattern = Regex("[0-9]+(?:,[0-9]{3})*")
    private data class Reference(val amount: Rect, val kind: ActionResourceKind, val glyph: DoubleArray,
        val intrinsic: DoubleArray?)

    fun observe(bitmap: Bitmap, layout: MLKitOcrProvider.Layout): List<ActionResourceWitness> {
        if (layout.documentStatus != OcrDocumentStatus.SUCCESS) return emptyList()
        val numbers = layout.elements.filter { it.bounds != null && amountPattern.matches(it.text.trim()) }
        val references = references(bitmap, layout, numbers)
        return numbers.map { number ->
            val rect = number.bounds!!
            val inventory = references.firstOrNull { it.amount == rect }
            val glyph = ResourceGlyphDescriptor.descriptor(bitmap, rect)
            val intrinsic = ResourceGlyphDescriptor.intrinsicDescriptor(bitmap, rect)
            // Both views retain the same threshold and runner-up margin. Strong
            // disagreement is blocking; these are views of one same-frame witness.
            val kind = listOfNotNull(match(glyph, references, false), match(intrinsic, references, true))
                .distinct().singleOrNull()
            ActionResourceWitness(Rect(rect), inventory?.kind ?: kind ?: ActionResourceKind.UNKNOWN,
                if (inventory != null) ActionResourceRole.INVENTORY else if (kind != null) ActionResourceRole.COST
                else ActionResourceRole.UNKNOWN, ResourceGlyphDescriptor.amountClear(bitmap, rect))
        }.distinctBy { it.bounds }
    }

    /**
     * Exposes only bounded same-frame resource scores for local QA. The normal scan path
     * does not call this method; no OCR text, file path, or pixel data is returned.
     */
    fun diagnose(bitmap: Bitmap, layout: MLKitOcrProvider.Layout): ActionResourceTrace {
        if (layout.documentStatus != OcrDocumentStatus.SUCCESS) {
            return ActionResourceTrace(layout.documentStatus, 0, 0, emptyList())
        }
        val numbers = layout.elements.filter { it.bounds != null && amountPattern.matches(it.text.trim()) }
        val references = references(bitmap, layout, numbers)
        val traces = numbers.take(MAX_DIAGNOSTIC_CANDIDATES).map { number ->
            val rect = number.bounds!!
            val inventory = references.firstOrNull { it.amount == rect }
            val directScores = rankedScores(ResourceGlyphDescriptor.descriptor(bitmap, rect), references, false)
            val intrinsicScores = rankedScores(ResourceGlyphDescriptor.intrinsicDescriptor(bitmap, rect), references, true)
            val directKind = selectedKind(directScores)
            val intrinsicKind = selectedKind(intrinsicScores)
            val kind = listOfNotNull(directKind, intrinsicKind).distinct().singleOrNull()
            val clear = ResourceGlyphDescriptor.amountClear(bitmap, rect)
            val role = if (inventory != null) ActionResourceRole.INVENTORY
                else if (kind != null) ActionResourceRole.COST else ActionResourceRole.UNKNOWN
            val reason = when {
                inventory != null -> "inventory_reference"
                directKind != null && intrinsicKind != null && directKind != intrinsicKind -> "descriptor_views_disagree"
                kind != null && !clear -> "cost_match_amount_occluded"
                kind != null -> "cost_match"
                directScores.isEmpty() && intrinsicScores.isEmpty() -> "no_reference_scores"
                else -> "below_similarity_or_margin"
            }
            ActionResourceCandidateTrace(
                bounds = Rect(rect),
                inventoryReferenceKind = inventory?.kind,
                direct = matchView(directScores),
                intrinsic = matchView(intrinsicScores),
                selectedKind = inventory?.kind ?: kind ?: ActionResourceKind.UNKNOWN,
                selectedRole = role,
                amountClear = clear,
                reasonCode = reason
            )
        }
        return ActionResourceTrace(layout.documentStatus, numbers.size, references.size, traces)
    }

    private fun match(glyph: DoubleArray?, references: List<Reference>, intrinsic: Boolean): ActionResourceKind? {
        return selectedKind(rankedScores(glyph, references, intrinsic))
    }

    private fun rankedScores(
        glyph: DoubleArray?, references: List<Reference>, intrinsic: Boolean
    ): List<Pair<ActionResourceKind, Double>> {
        if (glyph == null) return emptyList()
        return references.mapNotNull { reference ->
            val vector = if (intrinsic) reference.intrinsic else reference.glyph
            if (vector == null) null else reference.kind to
                glyph.indices.sumOf { index -> glyph[index] * vector[index] }
        }.groupBy { it.first }.map { (kind, scores) -> kind to scores.maxOf { it.second } }
            .sortedByDescending { it.second }
    }

    private fun selectedKind(scores: List<Pair<ActionResourceKind, Double>>): ActionResourceKind? =
        scores.firstOrNull()?.takeIf { it.second >= MIN_SIMILARITY &&
            it.second - (scores.getOrNull(1)?.second ?: 0.0) >= MIN_MARGIN }?.first

    private fun matchView(scores: List<Pair<ActionResourceKind, Double>>): ActionResourceMatchView {
        val best = scores.getOrNull(0)
        val runnerUp = scores.getOrNull(1)
        return ActionResourceMatchView(best?.first, best?.second, runnerUp?.first, runnerUp?.second,
            if (best == null) null else best.second - (runnerUp?.second ?: 0.0))
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
            val kind = inventoryKind(label, lines, candyTop, powerUpTop) ?: return@flatMap emptyList()
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
                ResourceGlyphDescriptor.descriptor(bitmap, amount)?.let { Reference(Rect(amount), typed, it,
                    ResourceGlyphDescriptor.intrinsicDescriptor(bitmap, amount)) }
            }
        }.groupBy { it.amount }.map { (_, candidates) ->
            val kinds = candidates.map { it.kind }.distinct()
            candidates.first().copy(kind = kinds.singleOrNull() ?: ActionResourceKind.UNKNOWN)
        }
    }

    private fun inventoryKind(
        label: MLKitOcrProvider.RecognizedBlock,
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        candyTop: Int?, powerUpTop: Int?
    ): ActionResourceKind? {
        val text = label.text.trim().uppercase()
        val rect = label.bounds!!
        if (text in setOf("XL", "ENERGY")) return null // Continuations never create item references.
        return when {
            text.contains("CANDY") -> ActionResourceKind.CANDY
            text == "STARDUST" -> ActionResourceKind.STARDUST
            text.contains("MEGA ENERGY") || wrappedMegaEnergy(label, lines) -> ActionResourceKind.MEGA_ENERGY
            candyTop != null && powerUpTop != null && rect.top > candyTop && rect.bottom < powerUpTop &&
                text.matches(Regex("[A-Z]+(?: [A-Z]+)*")) -> ActionResourceKind.SPECIAL_ITEM
            else -> null
        }
    }

    private fun wrappedMegaEnergy(label: MLKitOcrProvider.RecognizedBlock,
        lines: List<MLKitOcrProvider.RecognizedBlock>): Boolean {
        val rect = requireNotNull(label.bounds)
        if (!label.text.trim().endsWith("MEGA", true)) return false
        return lines.any { continuation ->
            val bounds = requireNotNull(continuation.bounds)
            val near = bounds.top >= rect.bottom && bounds.top - rect.bottom < rect.height() * 2
            near && continuation.text.trim().equals("ENERGY", true) && bounds.centerX() in rect.left..rect.right
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
