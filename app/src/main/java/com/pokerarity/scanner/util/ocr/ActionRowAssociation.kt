package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import kotlin.math.abs

private const val MIN_POWER_UP_COST = 100
private const val MAX_POWER_UP_COST = 30_000
private const val MAX_EVOLUTION_CANDY_COST = 1_000

/**
 * Phase 2C action-row association bounds, measured on the preserved S25 corpus.
 * The cost token's right edge sits 0.44-0.54 of the frame width right of its action
 * label's left edge (span cap 0.68 gives headroom for observed layout shift). The
 * POWER UP row additionally carries the right-hand candy-XL inventory count at
 * x >= 0.80, so its cost tokens must start left of 0.76 of the frame width; the EVOLVE
 * row has no inventory column and relies on the label-anchored span. Generic layout
 * geometry, not Pokemon-specific tuning.
 */
private const val MAX_BUTTON_SPAN_RATIO = 0.68f
private const val POWER_UP_COST_LEFT_LIMIT_RATIO = 0.76f
private const val ACTION_VERTICAL_TOLERANCE = 0.75
private val MERGED_EVOLVE_COST = Regex("""(?i)^EVOLVE\s*([0-9]{1,4})$""")
private val NUMERIC_TOKEN = Regex("""\d{1,3}(?:[, .]\d{3})*|\d{3,5}""")

/** A token found on an anchored action row (Phase 2C action-row association). */
internal data class RowToken(
    val rect: Rect,
    val numeric: Boolean,
    val value: Int?
)

internal data class CostEvidence(
    val value: Int?,
    val rect: Rect?,
    val read: FieldRead<Int>
)

internal data class ActionAnchorEvidence(
    val anchor: MLKitOcrProvider.RecognizedBlock?,
    val cost: CostEvidence
)

private data class CostDomain(
    val minValue: Int,
    val maxValue: Int,
    val enforceInventoryGuard: Boolean
)

/** POWER UP evidence: the strict label anchor plus its own-row cost association. */
internal fun powerUpEvidence(
    layout: MLKitOcrProvider.Layout,
    lines: List<MLKitOcrProvider.RecognizedBlock>,
    width: Int,
    context: ExtractionContext
): ActionAnchorEvidence {
    val anchors = lines.filter { line ->
        line.text.filter(Char::isLetter).equals("POWERUP", true) &&
            line.bounds!!.centerX() < width / 2 &&
            !insideNameBand(line.bounds!!, context.nameBand)
    }
    return when (anchors.size) {
        0 -> ActionAnchorEvidence(null, CostEvidence(null, null, FieldRead.missing("action_not_detected")))
        1 -> {
            val anchor = anchors.single()
            ActionAnchorEvidence(
                anchor,
                costEvidence(
                    rowTokens(
                        layout.elements,
                        anchor,
                        width,
                        CostDomain(
                            minValue = MIN_POWER_UP_COST,
                            maxValue = MAX_POWER_UP_COST,
                            enforceInventoryGuard = true
                        )
                    )
                )
            )
        }
        else -> ActionAnchorEvidence(
            null,
            CostEvidence(null, null, FieldRead.conflict("multiple_power_up_actions", anchors.size))
        )
    }
}

/**
 * Ordinary EVOLVE evidence, anchored INDEPENDENTLY of POWER UP: a split or unreadable
 * POWER UP label must not hide a visible EVOLVE action (Phase 2C plan semantics).
 * A merged "EVOLVE <cost>" line contributes its inline cost; MEGA EVOLVE is a distinct,
 * unsupported mechanic; a title/nickname row is rejected by the geometry name-band guard.
 */
internal fun evolveEvidence(
    layout: MLKitOcrProvider.Layout,
    lines: List<MLKitOcrProvider.RecognizedBlock>,
    width: Int,
    context: ExtractionContext
): ActionAnchorEvidence {
    val megaEvolve = lines.any { line ->
        line.text.filter(Char::isLetter).equals("MEGAEVOLVE", true)
    }
    val anchors = lines.filter { line ->
        val rect = line.bounds!!
        (line.text.trim().equals("EVOLVE", true) || MERGED_EVOLVE_COST.matches(line.text.trim())) &&
            rect.centerX() < width / 2 &&
            !insideNameBand(rect, context.nameBand)
    }
    val read: FieldRead<Int> = when {
        megaEvolve && anchors.isEmpty() -> FieldRead.unsupported("mega_evolve_unsupported")
        anchors.size > 1 -> FieldRead.conflict("multiple_evolve_actions", anchors.size)
        anchors.isEmpty() -> FieldRead.missing("action_not_detected")
        else -> {
            val anchor = anchors.single()
            val inline = MERGED_EVOLVE_COST.matchEntire(anchor.text.trim())
                ?.groupValues?.get(1)?.toIntOrNull()
                ?.takeIf { it in 0..MAX_EVOLUTION_CANDY_COST }
            costEvidence(
                rowTokens(
                    layout.elements,
                    anchor,
                    width,
                    CostDomain(minValue = 0, maxValue = MAX_EVOLUTION_CANDY_COST, enforceInventoryGuard = false)
                ),
                inlineCandidate = inline
            ).read
        }
    }
    return ActionAnchorEvidence(anchors.singleOrNull(), CostEvidence(read.value, null, read))
}

internal fun insideNameBand(rect: Rect, nameBand: Rect?): Boolean = nameBand != null &&
    rect.centerY() >= nameBand.top && rect.centerY() <= nameBand.bottom

/**
 * Cost tokens on an anchored action row: vertically aligned with the label, starting
 * right of the label, inside the measured button span, matching a strict numeric form,
 * and inside the numeric domain. [CostDomain.enforceInventoryGuard] applies the absolute
 * left limit that keeps the right-hand inventory column off the POWER UP row.
 */
private fun rowTokens(
    elements: List<MLKitOcrProvider.RecognizedBlock>,
    anchor: MLKitOcrProvider.RecognizedBlock,
    width: Int,
    domain: CostDomain
): List<RowToken> {
    val anchorRect = anchor.bounds ?: return emptyList()
    val spanLimit = anchorRect.left + (width * MAX_BUTTON_SPAN_RATIO).toInt()
    val inventoryLimit = (width * POWER_UP_COST_LEFT_LIMIT_RATIO).toInt()
    return elements.mapNotNull { element ->
        val rect = element.bounds ?: return@mapNotNull null
        val aligned = abs(rect.centerY() - anchorRect.centerY()) <
            maxOf(rect.height(), anchorRect.height()) * ACTION_VERTICAL_TOLERANCE
        val inSpan = rect.right > anchorRect.right && rect.right <= spanLimit
        val leftOfInventory = !domain.enforceInventoryGuard || rect.left < inventoryLimit
        if (!aligned || !inSpan || !leftOfInventory) return@mapNotNull null
        val numeric = NUMERIC_TOKEN.matches(element.text.trim())
        val value = element.text
            .takeIf { NUMERIC_TOKEN.matches(it.trim()) }
            ?.filter(Char::isDigit)
            ?.toIntOrNull()
            ?.takeIf { it in domain.minValue..domain.maxValue }
        RowToken(rect, numeric, value)
    }
}

private fun costEvidence(tokens: List<RowToken>, inlineCandidate: Int? = null): CostEvidence {
    val values = (tokens.mapNotNull { it.value } + listOfNotNull(inlineCandidate)).distinct()
    val validRects = tokens.filter { it.value != null }.map { it.rect }
    val read = when {
        values.size > 1 -> FieldRead.conflict("multiple_distinct_costs", values.size)
        values.size == 1 -> FieldRead.read(values.single(), values.size)
        tokens.any { it.numeric } || inlineCandidate != null ->
            FieldRead.unreadable("cost_out_of_supported_domain", tokens.size)
        tokens.isNotEmpty() -> FieldRead.unreadable("cost_token_unreadable", tokens.size)
        else -> FieldRead.unreadable("cost_not_recognized")
    }
    return CostEvidence(read.value, validRects.firstOrNull(), read)
}
