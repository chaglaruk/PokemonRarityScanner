package com.pokerarity.scanner.util.ocr

import java.util.Date
import kotlin.math.abs
import kotlin.math.min

/**
 * Pure-logic text parsing utilities — no Android Context dependency.
 * Testable via JVM JUnit.
 */
object TextParseUtils {

    fun parseCP(text: String): Int? {
        if (text.isBlank()) return null

        val clean = text.uppercase().replace("O", "0").replace("I", "1").replace("S", "5").replace("B", "8")
        val allDigits = clean.replace(Regex("[^0-9]"), "")
        val hasExplicitCpAnchor = clean.contains("CP")
        
        // Try explicit "CP ####" pattern first
        val cpMatch = Regex("""CP\s*(\d{3,4})""").find(clean)
        if (cpMatch != null) return cpMatch.groupValues[1].toIntOrNull()

        // Standard 3-4 digit case
        if (allDigits.length in 3..4) {
            val num = allDigits.toIntOrNull()
            if (num != null && num in 100..5500 && num !in 2016..2026) return num
        }

        // Leading zero case: "03868" → 3868
        if (!hasExplicitCpAnchor && allDigits.length >= 5 && allDigits.startsWith("0")) {
            val stripped = allDigits.trimStart('0')
            if (stripped.length in 3..4) {
                stripped.toIntOrNull()?.let { value ->
                    if (value in 100..5500) return value
                }
            }
            if (stripped.length >= 4) {
                stripped.takeLast(4).toIntOrNull()?.let { value ->
                    if (value in 100..5500) return value
                }
            }
            if (stripped.length >= 3) {
                stripped.takeLast(3).toIntOrNull()?.let { value ->
                    if (value in 100..999) return value
                }
            }
        }

        // Enhanced multi-digit fallback: handle spaced/embedded CP like "1 311976 1" → extract 1976
        // Pattern matches: digit, 2-7 chars (digit or space), digit = 4-9 chars total
        val matches = Regex("""(\d[\d\s]{2,7}\d)""").findAll(clean)
        for (m in matches) {
            val compact = m.groupValues[1].replace(" ", "")
            if (compact.length !in 3..8) continue // Allow up to 8 digits for fallback extraction
            
            // Try exact match first when compact length is 3-4 digits
            if (compact.length in 3..4) {
                compact.toIntOrNull()?.let { v ->
                    if (v in 100..5500 && v !in 2016..2026) return v
                }
            }
            
            // Try last 4 digits for spaced patterns like "1 311976 1" → "311976" → "1976"
            if (compact.length >= 4) {
                compact.takeLast(4).toIntOrNull()?.let { v ->
                    if (v in 100..5500 && v !in 2016..2026) return v
                }
            }
            
            // Try last 3 digits as final fallback
            if (compact.length >= 3) {
                compact.takeLast(3).toIntOrNull()?.let { v ->
                    if (v in 100..999 && v !in 2016..2026) return v
                }
            }
        }

        return null
    }

    fun parseHPPair(vararg texts: String): Pair<Int, Int>? {
        val candidates = texts
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { raw ->
                raw.uppercase()
                    .replace("O", "0")
                    .replace("I", "1")
                    .replace("S", "5")
                    .replace(Regex("""/\s*[PFD](\d{1,2})"""), "/$1")
                    .replace(Regex("""(^|\s)[IP](\d{2,3})"""), "$1$2")
                    // Remove trailing garbage like "HP 7" in "174/174 HP 7"
                    .replace(Regex("""HP\s+\d+\s*$"""), "HP")
            }

        if (candidates.isEmpty()) return null

        // Enhanced: try pattern with aggressive leading digit trim (handles "7227 / 227" → "227 / 227")
        candidates.forEach { upper ->
            // Pattern allowing 2-5 leading digits and 2-3 trailing digits (for noise tolerance)
            val slashMatch = Regex("""(\d{2,5})\s*/\s*(\d{2,3})""").find(upper)
            if (slashMatch != null) {
                var currentRaw = slashMatch.groupValues[1]
                val maxRaw = slashMatch.groupValues[2].trimStart('0')
                val maxVal = maxRaw.toIntOrNull() ?: return@forEach
                
                // If currentRaw has extra leading digits, trim to match maxRaw length
                // Example: "7227" before "227" → try "227"
                if (currentRaw.length > maxRaw.length + 1) {
                    val trimmed = currentRaw.takeLast(maxRaw.length)
                    trimmed.toIntOrNull()?.let { v ->
                        if (isReasonableHpPair(v, maxVal)) return Pair(v, maxVal)
                    }
                    // Also try one digit more
                    val trimmed2 = currentRaw.takeLast(maxRaw.length + 1)
                    trimmed2.toIntOrNull()?.let { v ->
                        if (isReasonableHpPair(v, maxVal)) return Pair(v, maxVal)
                    }
                }
                
                // Try normal parse
                val currentVal = currentRaw.trimStart('0').toIntOrNull() ?: return@forEach
                if (isReasonableSlashHpPair(currentVal, maxVal)) {
                    return Pair(currentVal, maxVal)
                }
            }
        }

        candidates.forEach { upper ->
            val noisySlashMatch = Regex("""(\d{3,4})\s*/\s*(\d{2,3})""").find(upper)
            if (noisySlashMatch != null) {
                val currentRaw = noisySlashMatch.groupValues[1]
                val maxRaw = noisySlashMatch.groupValues[2]
                val repaired = repairNoisyHpPair(currentRaw, maxRaw)
                if (repaired != null) {
                    return repaired
                }
            }
        }

        candidates.forEach { upper ->
            val slashMatch = Regex("""(\d{2,3})\s*/\s*(\d{2,3})""").find(upper)
            if (slashMatch != null) {
                val cur = slashMatch.groupValues[1].toIntOrNull()
                val max = slashMatch.groupValues[2].toIntOrNull()
                if (isReasonableSlashHpPair(cur, max)) {
                    return Pair(cur!!, max!!)
                }
            }
        }

        candidates.forEach { upper ->
            if (!upper.contains("HP")) return@forEach
            val numbers = Regex("""\d{2,3}""").findAll(upper)
                .mapNotNull { it.value.toIntOrNull() }
                .filter { it in 10..999 }
                .toList()
            if (numbers.size >= 2) {
                for (index in 0 until numbers.lastIndex) {
                    val current = numbers[index]
                    val max = numbers[index + 1]
                    if (isReasonableHpPair(current, max)) {
                        return Pair(current, max)
                    }
                }
            }
        }

        candidates.forEach { upper ->
            if (!upper.contains("HP")) return@forEach

            val compactDigits = upper.replace(Regex("[^0-9]"), "")
            if (compactDigits.length in 4..6) {
                val mid = compactDigits.length / 2
                val first = compactDigits.substring(0, mid).toIntOrNull()
                val second = compactDigits.substring(mid).toIntOrNull()
                if (isReasonableHpPair(first, second)) {
                    return Pair(first!!, second!!)
                }
            }

            val hpOnly = Regex("""H\s*P\s*(\d{2,3})""").find(upper)
            if (hpOnly != null) {
                val value = hpOnly.groupValues[1].toIntOrNull()
                if (value != null && value in 10..999) {
                    return Pair(value, value)
                }
            }
        }

        return null
    }

    fun selectBestHPPair(vararg texts: String): Pair<Int, Int>? {
        data class Candidate(val pair: Pair<Int, Int>, val score: Int)

        val candidates = texts.mapNotNull { raw ->
            val pair = parseHPPair(raw) ?: return@mapNotNull null
            val normalized = raw.uppercase()
                .replace("O", "0")
                .replace("I", "1")
                .replace("S", "5")
            val digitCount = normalized.count(Char::isDigit)
            val pairDigits = pair.first.toString().length + pair.second.toString().length
            val extraDigits = (digitCount - pairDigits).coerceAtLeast(0)
            val exactSlash = Regex("""\b\d{2,3}\s*/\s*\d{2,3}\s*HP?\b""").containsMatchIn(normalized)
            val slashOnly = Regex("""\b\d{2,3}\s*/\s*\d{2,3}\b""").containsMatchIn(normalized)
            val hasHpToken = normalized.contains("HP")
            val equalityBonus = if (pair.first == pair.second) 10 else 0
            val consistencyPenalty = if (pair.second >= pair.first * 2 && pair.first >= 100) 12 else 0
            val base = when {
                exactSlash -> 90
                slashOnly && hasHpToken -> 70
                slashOnly -> 55
                hasHpToken -> 35
                else -> 20
            }
            Candidate(
                pair = pair,
                score = base + equalityBonus - (extraDigits * 8) - consistencyPenalty
            )
        }

        return candidates
            .groupBy { it.pair }
            .map { (pair, hits) ->
                val totalScore = hits.sumOf { it.score } + (hits.size - 1) * 45
                pair to totalScore
            }
            .sortedWith(
                compareByDescending<Pair<Pair<Int, Int>, Int>> { it.second }
                    .thenByDescending { it.first.first == it.first.second }
                    .thenBy { abs(it.first.second - it.first.first) }
            )
            .firstOrNull()
            ?.first
    }

    fun parseDate(allText: String): Date? {
        if (allText.isBlank()) return null

        val preClean = allText.uppercase()
            .replace("O", "0")
            .replace("Z", "2")
            .replace("I", "1")
            .replace("L", "1")
            .replace("S", "5")
            .replace("B", "8")

        val clean = preClean.replace(Regex("[^0-9/\\.]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val yearMatch = Regex("""\b(201[6-9]|202[0-6])\b""").find(clean) ?: return null
        val year = yearMatch.groupValues[1].toInt()

        val sepMatch = Regex("""(\d{1,2})[/.](\d{1,2})""").find(clean)
        if (sepMatch != null) {
            val v1 = sepMatch.groupValues[1].toInt()
            val v2 = sepMatch.groupValues[2].toInt()
            if ((v1 in 1..31 && v2 in 1..12) || (v1 in 1..12 && v2 in 1..31)) {
                val day = if (v1 > 12) v1 else v2
                val mon = if (v1 > 12) v2 else v1
                return makeDate(year, mon - 1, day)
            }
        }

        val rest = clean.replace(yearMatch.groupValues[1], " ").trim()
        val compactToken = Regex("""\b(\d{4,5})\b""").find(rest)?.groupValues?.get(1)
        if (compactToken != null) {
            parseCompactMonthDay(compactToken)?.let { (month, day) ->
                return makeDate(year, month - 1, day)
            }
        }
        val digits = Regex("""\b(\d{1,2})\b""").findAll(rest)
            .map { it.value.toInt() }
            .filter { it in 1..31 }
            .toList()

        if (digits.size >= 2) {
            val v1 = digits[0]
            val v2 = digits[1]
            if ((v1 in 1..31 && v2 in 1..12) || (v1 in 1..12 && v2 in 1..31)) {
                val day = if (v1 > 12) v1 else v2
                val mon = if (v1 > 12) v2 else v1
                return makeDate(year, mon - 1, day)
            }
        } else if (digits.size == 1) {
            val v = digits[0]
            if (v in 1..12) return makeDate(year, v - 1, 1)
        }

        return null
    }

    internal fun makeDate(year: Int, month: Int, day: Int): Date {
        val cal = java.util.Calendar.getInstance()
        cal.set(year.coerceIn(2016, 2026), month.coerceIn(0, 11), day.coerceIn(1, 31), 0, 0, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.time
    }

    internal fun parseCompactMonthDay(token: String): Pair<Int, Int>? {
        val normalized = token.filter { it.isDigit() }
        if (normalized.length !in 4..5) return null
        val candidates = buildList {
            if (normalized.length == 5) {
                add(normalized.take(2) + normalized.takeLast(2))
            }
            if (normalized.length >= 4) {
                add(normalized.take(4))
                add(normalized.takeLast(4))
            }
        }.distinct()
        candidates.forEach { value ->
            val first = value.take(2).toIntOrNull() ?: return@forEach
            val second = value.takeLast(2).toIntOrNull() ?: return@forEach
            when {
                first in 1..12 && second in 1..31 -> return first to second
                first in 13..31 && second in 1..12 -> return second to first
            }
        }
        return null
    }

    internal fun isReasonableHpPair(current: Int?, max: Int?): Boolean {
        if (current == null || max == null) return false
        if (current !in 10..500 || max !in 10..500) return false
        if (current > max) return false
        if (max > current * 2.2f) return false
        return true
    }

    internal fun isReasonableSlashHpPair(current: Int?, max: Int?): Boolean {
        if (current == null || max == null) return false
        if (current !in 10..500 || max !in 10..500) return false
        if (current > max) return false
        return true
    }

    private fun repairNoisyHpPair(currentRaw: String, maxRaw: String): Pair<Int, Int>? {
        val current = currentRaw.toIntOrNull() ?: return null
        val max = maxRaw.toIntOrNull() ?: return null
        if (isReasonableHpPair(current, max)) {
            return current to max
        }
        if (max !in 10..500) return null
        val normalizedCurrent = currentRaw.trimStart('0')
        val normalizedMax = maxRaw.trimStart('0')
        if (normalizedCurrent.length !in (normalizedMax.length + 1)..(normalizedMax.length + 2)) {
            return null
        }
        val containsMax = normalizedCurrent.startsWith(normalizedMax) || normalizedCurrent.endsWith(normalizedMax)
        if (!containsMax) {
            val canRecoverByRemovingOneDigit = normalizedCurrent.indices.any { index ->
                normalizedCurrent.removeRange(index, index + 1) == normalizedMax
            }
            if (!canRecoverByRemovingOneDigit) return null
        }
        return max to max
    }
}
