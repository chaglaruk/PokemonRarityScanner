package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import kotlin.math.abs

/** Fields follow visible labels and the HP bar; scrolling does not change their meaning. */
internal object AnchoredScreenText {
    private const val HP_CENTER_LEFT_RATIO = 0.2
    private const val HP_CENTER_RIGHT_RATIO = 0.8
    private const val HP_BAR_ALIGNMENT_RATIO = 0.06

    // Name-band ratios are shared with the Phase 2B calibration builder so persisted
    // derived rects mirror exactly what the extractor derives from the bar anchor.
    internal const val NAME_TOP_OFFSET_RATIO = 0.09f
    private const val NAME_BOTTOM_OFFSET_RATIO = 0.015
    internal const val NAME_LEFT_RATIO = 0.12f
    internal const val NAME_RIGHT_RATIO = 0.88f

    private const val CANDY_ABOVE_HEIGHT_MULTIPLIER = 2
    private const val CANDY_CENTER_TOLERANCE_RATIO = 0.1

    data class Fields(
        val name: SpeciesNameDecision?,
        val nameRaw: String?,
        val cp: Int?,
        val hp: Pair<Int, Int>?,
        val candy: String?,
        val powerUpCost: Int?,
        val types: Set<String>?,
        val detailScreen: Boolean,
        val hpRect: Rect?,
        val nameRect: Rect?,
        val candyRect: Rect?,
        val costRect: Rect?,
        val numericConflict: Boolean = false,
        val evolutionCandyCost: Int? = null,
        // Phase 2C structured reads: the nullable values above stay authoritative for
        // compatibility; these retain WHY a value is absent.
        val cpRead: FieldRead<Int> = FieldRead.missing("not_evaluated"),
        val hpRead: FieldRead<Pair<Int, Int>> = FieldRead.missing("not_evaluated"),
        val candyRead: FieldRead<String> = FieldRead.missing("not_evaluated"),
        val powerUpRead: FieldRead<Int> = FieldRead.missing("not_evaluated"),
        val evolveRead: FieldRead<Int> = FieldRead.missing("not_evaluated")
    )

    fun extract(
        layout: MLKitOcrProvider.Layout,
        parser: TextParser,
        width: Int,
        height: Int,
        context: ExtractionContext = ExtractionContext()
    ): Fields {
        val lines = layout.lines.filter { it.bounds != null }
        val bar = context.bar
        val hpEvidence = findHpEvidence(lines, width)
        val nameEvidence = findNameEvidence(lines, parser, NameGeometry(width, height, bar), hpEvidence)
        val cpCandidates = findCpCandidates(lines, nameEvidence.bottom)
        val candyEvidence = findCandyEvidence(lines, parser, width, nameEvidence.bottom, context)
        val actionEvidence = findActionEvidence(layout, lines, width, context)
        val types = findTypes(lines, width, height, nameEvidence.bottom, candyEvidence.hits)

        val numericConflict = hpEvidence.values.size > 1 ||
            hpEvidence.lines.any { TextParseUtils.parseExactHPPair(it.text) == null } ||
            cpCandidates.size > 1
        val detail = !numericConflict &&
            (hpEvidence.hp != null || types != null) &&
            candyEvidence.species != null &&
            (actionEvidence.powerUp != null || (hpEvidence.hp != null && types != null))

        return Fields(
            name = nameEvidence.decision,
            nameRaw = nameEvidence.rawText,
            cp = cpCandidates.singleOrNull(),
            hp = hpEvidence.hp,
            candy = candyEvidence.species,
            powerUpCost = actionEvidence.powerUpCost,
            types = types,
            detailScreen = detail,
            hpRect = hpEvidence.rect,
            nameRect = nameEvidence.band,
            candyRect = candyEvidence.hits.firstOrNull()?.second,
            costRect = actionEvidence.costRect,
            numericConflict = numericConflict,
            evolutionCandyCost = actionEvidence.evolutionCandyCost,
            cpRead = cpRead(cpCandidates),
            hpRead = hpRead(hpEvidence),
            candyRead = candyRead(candyEvidence),
            powerUpRead = actionEvidence.powerUpRead,
            evolveRead = actionEvidence.evolveRead
        )
    }

    private fun findHpEvidence(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        width: Int
    ): HpEvidence {
        val centerRange = (width * HP_CENTER_LEFT_RATIO).toInt()..
            (width * HP_CENTER_RIGHT_RATIO).toInt()
        val hpLines = lines.filter { line ->
            line.text.contains("HP", true) &&
                line.text.contains('/') &&
                line.bounds!!.centerX() in centerRange
        }
        val values = hpLines.mapNotNull { TextParseUtils.parseExactHPPair(it.text) }.distinct()
        return HpEvidence(
            hp = values.singleOrNull(),
            rect = hpLines.singleOrNull()?.bounds,
            lines = hpLines,
            values = values
        )
    }

    private data class NameGeometry(val width: Int, val height: Int, val bar: Rect?)

    private fun findNameEvidence(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        parser: TextParser,
        geometry: NameGeometry,
        hpEvidence: HpEvidence
    ): NameEvidence {
        val confirmedBar = geometry.bar?.takeIf { candidate ->
            val hpRect = hpEvidence.rect
            hpRect != null &&
                abs(hpRect.centerY() - candidate.bottom) < geometry.height * HP_BAR_ALIGNMENT_RATIO
        }
        val nameBottom = confirmedBar?.top
            ?: hpEvidence.rect?.top?.minus((geometry.height * NAME_BOTTOM_OFFSET_RATIO).toInt())
        val nameBand = nameBottom?.let { bottom ->
            Rect(
                (geometry.width * NAME_LEFT_RATIO).toInt(),
                (bottom - geometry.height * NAME_TOP_OFFSET_RATIO).toInt().coerceAtLeast(0),
                (geometry.width * NAME_RIGHT_RATIO).toInt(),
                bottom
            )
        }
        val nameLine = lines
            .filter { line -> isInsideNameBand(line, nameBand) }
            .sortedByDescending { it.bounds!!.height() }
            .firstOrNull(::isUsableNameLine)

        return NameEvidence(
            decision = nameLine?.let { parser.decideSpeciesName(cleanNameLabel(it.text)) },
            rawText = nameLine?.text,
            band = nameBand,
            bottom = nameBottom
        )
    }

    private fun findCpCandidates(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        nameBottom: Int?
    ): List<Int> = lines
        .filter { line -> nameBottom == null || line.bounds!!.bottom < nameBottom }
        .mapNotNull { line ->
            Regex("(?i)\\bC\\s*P\\s*(\\d{2,4})(?!\\d)")
                .find(line.text)
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
                ?.takeIf { it in FieldCandidateNormalizer.MIN_SUPPORTED_CP..FieldCandidateNormalizer.MAX_SUPPORTED_CP }
        }
        .distinct()

    private fun findCandyEvidence(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        parser: TextParser,
        width: Int,
        nameBottom: Int?,
        context: ExtractionContext
    ): CandyEvidence {
        // Upper bound for the candy row: the HP-bar-derived name bottom when available,
        // otherwise the Phase 2B detail-card top (geometry layer) so overlay or inventory
        // text above the card can never be scanned as candy.
        val upperBound = nameBottom ?: context.detailCardTop
        fun inCandyBand(line: MLKitOcrProvider.RecognizedBlock): Boolean =
            upperBound == null || line.bounds!!.top > upperBound

        val hits = buildList {
            lines.filter { line ->
                line.text.contains("CANDY", true) && inCandyBand(line)
            }.forEach { line ->
                val text = candyTextWithAlignedSpecies(lines, line, width)
                Regex("(?i)(.+?)\\s+CANDY(?:\\s+XL)?(?:\\s+|$)").findAll(text).forEach { match ->
                    val decision = parser.decideSpeciesName(match.groupValues[1].trim())
                    if (decision is SpeciesNameDecision.Accepted &&
                        decision.source != SpeciesNameAcceptanceSource.SAFE_FUZZY
                    ) {
                        add(decision.species to line.bounds!!)
                    }
                }
            }
        }
        val candyTextSeen = lines.any { line ->
            inCandyBand(line) && line.text.uppercase().contains("ANDY")
        }
        return CandyEvidence(
            species = hits.map { it.first }.distinct().singleOrNull(),
            hits = hits,
            candyTextSeen = candyTextSeen
        )
    }

    private fun candyTextWithAlignedSpecies(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        candyLine: MLKitOcrProvider.RecognizedBlock,
        width: Int
    ): String {
        val standaloneCandy = candyLine.text.trim().matches(Regex("(?i)CANDY(?:\\s+XL)?"))
        val rect = candyLine.bounds
        return if (!standaloneCandy || rect == null) {
            candyLine.text
        } else {
            val above = lines
                .filter { line ->
                    val candidate = line.bounds
                    candidate != null &&
                        candidate.bottom <= rect.top &&
                        rect.top - candidate.bottom < rect.height() * CANDY_ABOVE_HEIGHT_MULTIPLIER &&
                        abs(candidate.centerX() - rect.centerX()) < width * CANDY_CENTER_TOLERANCE_RATIO
                }
                .maxByOrNull { it.bounds!!.bottom }
            if (above == null) candyLine.text else above.text + " " + candyLine.text
        }
    }

    private fun findActionEvidence(
        layout: MLKitOcrProvider.Layout,
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        width: Int,
        context: ExtractionContext
    ): ActionEvidence {
        val powerUp = powerUpEvidence(layout, lines, width, context)
        val evolve = evolveEvidence(layout, lines, width, context)
        return ActionEvidence(
            powerUp = powerUp.anchor,
            powerUpCost = powerUp.cost.value,
            costRect = powerUp.cost.rect,
            evolutionCandyCost = evolve.cost.read.value,
            powerUpRead = powerUp.cost.read,
            evolveRead = evolve.cost.read
        )
    }

    private fun findTypes(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        width: Int,
        height: Int,
        nameBottom: Int?,
        candyHits: List<Pair<String, Rect>>
    ): Set<String>? {
        val sizeLabels = lines.filter { it.text.trim().uppercase() in setOf("WEIGHT", "HEIGHT") }
        val candyTop = candyHits.minOfOrNull { it.second.top } ?: height
        val centerRange = (width * TYPE_CENTER_LEFT_RATIO).toInt()..
            (width * TYPE_CENTER_RIGHT_RATIO).toInt()
        val typeSets = lines
            .filter { line ->
                val rect = line.bounds!!
                (nameBottom == null || rect.top > nameBottom) &&
                    rect.bottom < candyTop &&
                    rect.centerX() in centerRange &&
                    alignedWithSizeRow(rect, sizeLabels)
            }
            .mapNotNull(::parseCompleteTypeSet)
            .distinct()
        return typeSets.singleOrNull()
    }
}

private const val TYPE_SIZE_ROW_HEIGHT_MULTIPLIER = 2
private const val TYPE_CENTER_LEFT_RATIO = 0.3
private const val TYPE_CENTER_RIGHT_RATIO = 0.7

private data class HpEvidence(
    val hp: Pair<Int, Int>?,
    val rect: Rect?,
    val lines: List<MLKitOcrProvider.RecognizedBlock>,
    val values: List<Pair<Int, Int>>
)

private data class NameEvidence(
    val decision: SpeciesNameDecision?,
    val rawText: String?,
    val band: Rect?,
    val bottom: Int?
)

private data class CandyEvidence(
    val species: String?,
    val hits: List<Pair<String, Rect>>,
    val candyTextSeen: Boolean
)

private data class ActionEvidence(
    val powerUp: MLKitOcrProvider.RecognizedBlock?,
    val powerUpCost: Int?,
    val costRect: Rect?,
    val evolutionCandyCost: Int?,
    val powerUpRead: FieldRead<Int>,
    val evolveRead: FieldRead<Int>
)

private fun cpRead(candidates: List<Int>): FieldRead<Int> = when {
    candidates.size > 1 -> FieldRead.conflict("multiple_cp_candidates", candidates.size)
    candidates.size == 1 -> FieldRead.read(candidates.single())
    else -> FieldRead.missing("no_cp_candidate")
}

private fun hpRead(evidence: HpEvidence): FieldRead<Pair<Int, Int>> = when {
    evidence.values.size > 1 -> FieldRead.conflict("multiple_hp_pairs", evidence.values.size)
    evidence.hp != null -> FieldRead.read(evidence.hp)
    evidence.lines.isNotEmpty() -> FieldRead.unreadable("malformed_hp_pair", evidence.lines.size)
    else -> FieldRead.missing("no_hp_label")
}

private fun candyRead(evidence: CandyEvidence): FieldRead<String> {
    val families = evidence.hits.map { it.first }.distinct()
    return when {
        families.size > 1 -> FieldRead.conflict("multiple_candy_families", families.size)
        families.size == 1 -> FieldRead.read(families.single(), evidence.hits.size)
        evidence.candyTextSeen -> FieldRead.unreadable("candy_label_unreadable")
        else -> FieldRead.missing("no_candy_label")
    }
}

// The grey edit pencil is recognized as a slash at the end of the title.
// Strip it only in the spatially anchored name label; generic text parsing stays strict.
private fun cleanNameLabel(text: String): String = text.trim().removeSuffix("/").trim()

private fun isInsideNameBand(
    line: MLKitOcrProvider.RecognizedBlock,
    band: Rect?
): Boolean {
    val rect = line.bounds ?: return false
    return band != null &&
        rect.centerY() in band.top until band.bottom &&
        rect.centerX() in band.left..band.right
}

private fun isUsableNameLine(line: MLKitOcrProvider.RecognizedBlock): Boolean {
    val text = cleanNameLabel(line.text)
    return !text.contains("LUCKY", true) &&
        text.any(Char::isLetter) &&
        !text.contains("HP", true) &&
        !text.contains("CP", true)
}

private fun alignedWithSizeRow(
    rect: Rect,
    sizeLabels: List<MLKitOcrProvider.RecognizedBlock>
): Boolean = sizeLabels.any { label ->
    val labelRect = label.bounds!!
    abs(labelRect.centerY() - rect.centerY()) <=
        maxOf(rect.height(), labelRect.height()) * TYPE_SIZE_ROW_HEIGHT_MULTIPLIER
}

private fun parseCompleteTypeSet(line: MLKitOcrProvider.RecognizedBlock): Set<String>? {
    val text = line.text.trim().lowercase()
    if (!text.matches(Regex("[a-z]+(?:(?:\\s*/\\s*|\\s+)[a-z]+)?"))) return null
    val words = text.split(Regex("\\s*/\\s*|\\s+"))
    return words.toSet().takeIf {
        it.size == words.size && RecognitionSnapshot.TYPES.containsAll(words)
    }
}
