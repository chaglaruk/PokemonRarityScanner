package com.pokerarity.scanner.util.ocr

import android.graphics.Rect
import kotlin.math.abs

/** Fields follow visible labels and the HP bar; scrolling does not change their meaning. */
internal object AnchoredScreenText {
    data class Fields(
        val name: SpeciesNameDecision?, val nameRaw: String?, val cp: Int?, val hp: Pair<Int, Int>?,
        val candy: String?, val powerUpCost: Int?, val types: Set<String>?, val detailScreen: Boolean,
        val hpRect: Rect?, val nameRect: Rect?, val candyRect: Rect?, val costRect: Rect?,
        val numericConflict: Boolean = false
    )

    fun extract(layout: MLKitOcrProvider.Layout, parser: TextParser, width: Int, height: Int, bar: Rect?): Fields {
        val lines = layout.lines.filter { it.bounds != null }
        val hpLines = lines.filter { line ->
            line.text.contains("HP", true) && line.text.contains('/') &&
                line.bounds!!.centerX() in (width * .2).toInt()..(width * .8).toInt()
        }
        val hpValues = hpLines.mapNotNull { TextParseUtils.parseExactHPPair(it.text) }.distinct()
        val hp = hpValues.singleOrNull()
        val hpRect = hpLines.singleOrNull()?.bounds
        // A green button may resemble a thin bar at its curved edge. The HP label
        // must confirm a visual bar before that bar constrains unrelated text.
        val confirmedBar = bar?.takeIf { hpRect != null && abs(hpRect.centerY() - it.bottom) < height * .06 }
        val nameBottom = confirmedBar?.top ?: hpRect?.top?.minus((height * .015).toInt())
        val nameBand = nameBottom?.let { Rect((width * .12).toInt(), (it - height * .09).toInt().coerceAtLeast(0),
            (width * .88).toInt(), it) }
        val nameLines = lines.filter { line ->
            val r = line.bounds!!
            nameBand != null && r.centerY() in nameBand.top until nameBand.bottom &&
                r.centerX() in nameBand.left..nameBand.right
        }.sortedByDescending { it.bounds!!.height() }
        val nameLine = nameLines.firstOrNull { line ->
            val text = cleanNameLabel(line.text)
            !text.contains("LUCKY", true) && text.any(Char::isLetter) &&
                !text.contains("HP", true) && !text.contains("CP", true)
        }
        val name = nameLine?.let { parser.decideSpeciesName(cleanNameLabel(it.text)) }
        val cpCandidates = lines.filter { (nameBottom == null || it.bounds!!.bottom < nameBottom) }
            .mapNotNull { line ->
                Regex("(?i)\\bC\\s*P\\s*(\\d{2,4})(?!\\d)").find(line.text)?.groupValues?.get(1)?.toIntOrNull()
                    ?.takeIf { it in 10..9999 }
            }.distinct()

        val candyHits = mutableListOf<Pair<String, Rect>>()
        for (line in lines.filter { it.text.contains("CANDY", true) && (nameBottom == null || it.bounds!!.top > nameBottom) }) {
            var text = line.text
            if (text.trim().matches(Regex("(?i)CANDY(?:\\s+XL)?"))) {
                val r = line.bounds!!
                val above = lines.filter { it.bounds!!.bottom <= r.top && r.top - it.bounds.bottom < r.height() * 2 &&
                    abs(it.bounds.centerX() - r.centerX()) < width * .1 }.maxByOrNull { it.bounds!!.bottom }
                if (above != null) text = above.text + " " + text
            }
            for (match in Regex("(?i)(.+?)\\s+CANDY(?:\\s+XL)?(?:\\s+|$)").findAll(text)) {
                val decision = parser.decideSpeciesName(match.groupValues[1].trim())
                if (decision is SpeciesNameDecision.Accepted && decision.source != SpeciesNameAcceptanceSource.SAFE_FUZZY) {
                    candyHits += decision.species to line.bounds!!
                }
            }
        }
        val candy = candyHits.map { it.first }.distinct().singleOrNull()
        val powerUp = lines.singleOrNull { it.text.filter(Char::isLetter).equals("POWERUP", true) && it.bounds!!.centerX() < width / 2 }
        val costCandidates = powerUp?.bounds?.let { anchor ->
            layout.elements.filter { element ->
                val r = element.bounds
                r != null && r.left > width * .48 && r.left < width * .76 &&
                    abs(r.centerY() - anchor.centerY()) < maxOf(r.height(), anchor.height()) * .75
            }.mapNotNull { element ->
                element.text.takeIf { it.matches(Regex("\\d{1,3}(?:[, .]\\d{3})*|\\d{3,5}")) }
                    ?.filter(Char::isDigit)?.toIntOrNull()?.takeIf { it in 100..30000 }?.let { it to element.bounds!! }
            }
        }.orEmpty()
        val cost = costCandidates.map { it.first }.distinct().singleOrNull()
        val sizeLabels = lines.filter { it.text.trim().uppercase() in setOf("WEIGHT", "HEIGHT") }
        val candyTop = candyHits.minOfOrNull { it.second.top } ?: height
        val typeSets = lines.filter { line ->
            val r = line.bounds!!
            (nameBottom == null || r.top > nameBottom) && r.bottom < candyTop &&
                r.centerX() in (width * .3).toInt()..(width * .7).toInt() &&
                sizeLabels.any { abs(it.bounds!!.centerY() - r.centerY()) <= maxOf(r.height(), it.bounds.height()) * 2 }
        }.mapNotNull { line ->
            val text = line.text.trim().lowercase()
            // A dangling slash is evidence of an incomplete dual-type label.
            if (!text.matches(Regex("[a-z]+(?:(?:\\s*/\\s*|\\s+)[a-z]+)?"))) return@mapNotNull null
            val words = text.split(Regex("\\s*/\\s*|\\s+"))
            words.toSet().takeIf { it.size == words.size && RecognitionProfiles.TYPES.containsAll(words) }
        }.distinct()
        val types = typeSets.singleOrNull()
        val numericConflict = hpValues.size > 1 || hpLines.any { TextParseUtils.parseExactHPPair(it.text) == null } || cpCandidates.size > 1
        val detail = !numericConflict && (hp != null || types != null) && candy != null &&
            (powerUp != null || (hp != null && types != null))
        return Fields(name, nameLine?.text, cpCandidates.singleOrNull(), hp, candy, cost, types, detail,
            hpRect, nameBand, candyHits.firstOrNull()?.second, costCandidates.firstOrNull()?.second,
            numericConflict)
    }

    // The grey edit pencil is recognized as a slash at the end of the title.
    // Strip it only in the spatially anchored name label; generic text parsing stays strict.
    private fun cleanNameLabel(text: String): String = text.trim().removeSuffix("/").trim()
}
