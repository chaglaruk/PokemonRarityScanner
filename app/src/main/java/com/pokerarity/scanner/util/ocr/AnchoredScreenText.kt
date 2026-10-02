package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import kotlin.math.abs

/** Fields follow visible labels and the HP bar; scrolling does not change their meaning. */
internal object AnchoredScreenText {
    private const val HP_CENTER_LEFT_RATIO = 0.2
    private const val HP_CENTER_RIGHT_RATIO = 0.8
    private const val HP_BAR_ALIGNMENT_RATIO = 0.06
    private const val NAME_TOP_OFFSET_RATIO = 0.09
    private const val NAME_BOTTOM_OFFSET_RATIO = 0.015
    private const val NAME_LEFT_RATIO = 0.12
    private const val NAME_RIGHT_RATIO = 0.88
    private const val CANDY_ABOVE_HEIGHT_MULTIPLIER = 2
    private const val CANDY_CENTER_TOLERANCE_RATIO = 0.1
    private const val COST_LEFT_MIN_RATIO = 0.48
    private const val COST_LEFT_MAX_RATIO = 0.76
    private const val ACTION_VERTICAL_TOLERANCE = 0.75
    private const val MIN_POWER_UP_COST = 100
    private const val MAX_POWER_UP_COST = 30_000
    private const val EVOLVE_MAX_DISTANCE_RATIO = 0.2
    private const val EVOLVE_COST_LEFT_MIN_RATIO = 0.6
    private const val EVOLVE_COST_RIGHT_MAX_RATIO = 0.9
    private const val MAX_EVOLUTION_CANDY_COST = 1_000
    private const val TYPE_CENTER_LEFT_RATIO = 0.3
    private const val TYPE_CENTER_RIGHT_RATIO = 0.7
    private const val TYPE_SIZE_ROW_HEIGHT_MULTIPLIER = 2

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
        val evolutionCandyCost: Int? = null
    )

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
        val hits: List<Pair<String, Rect>>
    )

    private data class ActionEvidence(
        val powerUp: MLKitOcrProvider.RecognizedBlock?,
        val powerUpCost: Int?,
        val costRect: Rect?,
        val evolutionCandyCost: Int?
    )

    fun extract(
        layout: MLKitOcrProvider.Layout,
        parser: TextParser,
        width: Int,
        height: Int,
        bar: Rect?
    ): Fields {
        val lines = layout.lines.filter { it.bounds != null }
        val hpEvidence = findHpEvidence(lines, width)
        val nameEvidence = findNameEvidence(lines, parser, width, height, bar, hpEvidence)
        val cpCandidates = findCpCandidates(lines, nameEvidence.bottom)
        val candyEvidence = findCandyEvidence(lines, parser, width, nameEvidence.bottom)
        val actionEvidence = findActionEvidence(layout, lines, width, height)
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
            evolutionCandyCost = actionEvidence.evolutionCandyCost
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

    private fun findNameEvidence(
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        parser: TextParser,
        width: Int,
        height: Int,
        bar: Rect?,
        hpEvidence: HpEvidence
    ): NameEvidence {
        val confirmedBar = bar?.takeIf { candidate ->
            val hpRect = hpEvidence.rect
            hpRect != null &&
                abs(hpRect.centerY() - candidate.bottom) < height * HP_BAR_ALIGNMENT_RATIO
        }
        val nameBottom = confirmedBar?.top
            ?: hpEvidence.rect?.top?.minus((height * NAME_BOTTOM_OFFSET_RATIO).toInt())
        val nameBand = nameBottom?.let { bottom ->
            Rect(
                (width * NAME_LEFT_RATIO).toInt(),
                (bottom - height * NAME_TOP_OFFSET_RATIO).toInt().coerceAtLeast(0),
                (width * NAME_RIGHT_RATIO).toInt(),
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
        nameBottom: Int?
    ): CandyEvidence {
        val hits = buildList {
            lines.filter { line ->
                line.text.contains("CANDY", true) &&
                    (nameBottom == null || line.bounds!!.top > nameBottom)
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
        return CandyEvidence(
            species = hits.map { it.first }.distinct().singleOrNull(),
            hits = hits
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
        height: Int
    ): ActionEvidence {
        val powerUp = lines.singleOrNull { line ->
            line.text.filter(Char::isLetter).equals("POWERUP", true) &&
                line.bounds!!.centerX() < width / 2
        }
        val costCandidates = powerUp?.bounds?.let { anchor ->
            findPowerUpCostCandidates(layout.elements, anchor, width)
        }.orEmpty()
        return ActionEvidence(
            powerUp = powerUp,
            powerUpCost = costCandidates.map { it.first }.distinct().singleOrNull(),
            costRect = costCandidates.firstOrNull()?.second,
            evolutionCandyCost = findEvolutionCandyCost(layout.elements, lines, powerUp, width, height)
        )
    }

    private fun findPowerUpCostCandidates(
        elements: List<MLKitOcrProvider.RecognizedBlock>,
        anchor: Rect,
        width: Int
    ): List<Pair<Int, Rect>> = elements.filter { element ->
        val rect = element.bounds
        rect != null &&
            rect.left > width * COST_LEFT_MIN_RATIO &&
            rect.left < width * COST_LEFT_MAX_RATIO &&
            verticallyAligned(rect, anchor)
    }.mapNotNull { element ->
        element.text
            .takeIf { it.matches(Regex("\\d{1,3}(?:[, .]\\d{3})*|\\d{3,5}")) }
            ?.filter(Char::isDigit)
            ?.toIntOrNull()
            ?.takeIf { it in MIN_POWER_UP_COST..MAX_POWER_UP_COST }
            ?.let { it to element.bounds!! }
    }

    private fun findEvolutionCandyCost(
        elements: List<MLKitOcrProvider.RecognizedBlock>,
        lines: List<MLKitOcrProvider.RecognizedBlock>,
        powerUp: MLKitOcrProvider.RecognizedBlock?,
        width: Int,
        height: Int
    ): Int? {
        val powerBounds = powerUp?.bounds
        val evolveAnchor = powerBounds?.let { power ->
            lines.singleOrNull { line ->
                val rect = line.bounds!!
                line.text.trim().equals("EVOLVE", true) &&
                    rect.centerX() < width / 2 &&
                    rect.top > power.bottom &&
                    rect.top - power.bottom < height * EVOLVE_MAX_DISTANCE_RATIO
            }?.bounds
        }
        return evolveAnchor?.let { anchor ->
            val tokens = elements.filter { element ->
                val rect = element.bounds
                rect != null &&
                    rect.left > width * EVOLVE_COST_LEFT_MIN_RATIO &&
                    rect.right < width * EVOLVE_COST_RIGHT_MAX_RATIO &&
                    verticallyAligned(rect, anchor) &&
                    element.text.any(Char::isDigit)
            }
            val costs = tokens.mapNotNull { token ->
                token.text
                    .takeIf { it.matches(Regex("[0-9]{1,4}")) }
                    ?.toIntOrNull()
                    ?.takeIf { it in 0..MAX_EVOLUTION_CANDY_COST }
            }
            costs
                .takeIf { tokens.isNotEmpty() && it.size == tokens.size }
                ?.distinct()
                ?.singleOrNull()
        }
    }

    private fun verticallyAligned(rect: Rect, anchor: Rect): Boolean =
        abs(rect.centerY() - anchor.centerY()) <
            maxOf(rect.height(), anchor.height()) * ACTION_VERTICAL_TOLERANCE

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
            it.size == words.size && RecognitionProfiles.TYPES.containsAll(words)
        }
    }

    // The grey edit pencil is recognized as a slash at the end of the title.
    // Strip it only in the spatially anchored name label; generic text parsing stays strict.
    private fun cleanNameLabel(text: String): String = text.trim().removeSuffix("/").trim()
}
