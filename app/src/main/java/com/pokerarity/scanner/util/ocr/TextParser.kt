package com.pokerarity.scanner.util.ocr

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.util.Date
import kotlin.math.abs
import kotlin.math.min

class TextParser(context: Context) {
    private val pokemonNames: List<String> = loadPokemonNames(context)

    data class NameCandidate(
        val name: String,
        val score: Double,
        val distance: Int
    )

    fun parseCP(text: String): Int? = TextParseUtils.parseCP(text)

    fun parseHPPair(vararg texts: String): Pair<Int, Int>? = TextParseUtils.parseHPPair(*texts)

    fun parseDate(allText: String): java.util.Date? {
        val result = TextParseUtils.parseDate(allText)
        android.util.Log.d("TextParser", "parseDate '$allText' -> $result")
        return result
    }

    fun parseDate(vararg texts: String): java.util.Date? {
        texts.forEach { text ->
            parseDate(text)?.let { return it }
        }
        return null
    }



    fun parseBottomDate(text: String): java.util.Date? {
        if (text.isBlank()) return null
        val clean = text.lowercase().trim()
        
        // Format: "London, United Kingdom 22/03/2023" veya "March 22, 2023"
        // Sadece rakamsal olana odaklanalim (Oyun ingilizce ama rakam daha stabil)
        val m = Regex("""(\d{1,2})[/\s\.](\d{1,2})[/\s\.]\b(201[6-9]|202[0-6])\b""").find(clean)
        if (m != null) {
            val v1 = m.groupValues[1].toInt()
            val v2 = m.groupValues[2].toInt()
            val year = m.groupValues[3].toInt()
            val day = if (v1 > 12) v1 else v2
            val mon = if (v1 > 12) v2 else v1
            if (mon in 1..12 && day in 1..31) return TextParseUtils.makeDate(year, mon - 1, day)
        }
        
        // Ä°ngilizce Ay Ä°simleri Fallback (Sadece Bottom iÃ§in)
        val monthMap = mapOf("january" to 0,"jan" to 0,"february" to 1,"feb" to 1,
            "march" to 2,"mar" to 2,"april" to 3,"apr" to 3,"may" to 4,
            "june" to 5,"jun" to 5,"july" to 6,"jul" to 6,"august" to 7,"aug" to 7,
            "september" to 8,"sep" to 8,"october" to 9,"oct" to 9,
            "november" to 10,"nov" to 10,"december" to 11,"dec" to 11)
        
        for ((name, idx) in monthMap) {
            if (clean.contains(name)) {
                val yearMatch = Regex("""\b(201[6-9]|202[0-6])\b""").find(clean)
                val dayMatch = Regex("""\b(\d{1,2})\b""").find(clean.replace(name, ""))
                if (yearMatch != null && dayMatch != null) {
                    return TextParseUtils.makeDate(yearMatch.groupValues[1].toInt(), idx, dayMatch.groupValues[1].toInt())
                }
            }
        }
        
        return null
    }

    fun parseCandyName(text: String): String? {
        return parseCandyName(listOf(text), allowLoose = false)
    }

    fun parseCandyName(vararg texts: String): String? {
        return parseCandyName(texts.toList(), allowLoose = false)
    }

    fun parseCandyNameLoose(vararg texts: String): String? {
        return parseCandyName(texts.toList(), allowLoose = true)
    }

    private fun parseCandyName(texts: List<String>, allowLoose: Boolean): String? {
        val nonBlank = texts.map { it.trim() }.filter { it.isNotBlank() }
        if (nonBlank.isEmpty()) return null

        nonBlank.forEach { rawText ->
            val upper = rawText.uppercase().trim()
            val m = Regex("""([A-Z][A-Z\s\-]{1,20}?)\s+(?:CANDY|CNDY|CANOY|CAN[D0]Y|CANY|CANDYX[L1I]?)""").find(upper)
            if (m != null) {
                val raw = m.groupValues[1].trim()
                parseName(raw)?.let {
                    android.util.Log.d("TextParser", "CandyName '$raw' -> '$it'")
                    return it
                }
            }
        }

        if (!allowLoose) return null

        val looseEligible = nonBlank.filter { containsCandyTokenHint(it) }
        if (looseEligible.isEmpty()) return null

        val candidates = mutableListOf<NameCandidate>()
        looseEligible.forEach { rawText ->
            val normalized = normalizeCandyInput(rawText) ?: return@forEach
            candidates += rankNameCandidates(normalized, limit = 3)
            if (normalized.contains(' ')) {
                normalized.split(Regex("\\s+"))
                    .filter { it.length in 4..12 }
                    .forEach { token -> candidates += rankNameCandidates(token, limit = 2) }
            }
        }

        val best = candidates
            .groupBy { it.name }
            .map { (name, hits) ->
                val topScore = hits.maxOf { it.score }
                val boost = if (hits.size >= 2) 0.04 else 0.0
                NameCandidate(name, (topScore + boost).coerceIn(0.0, 1.0), hits.minOf { it.distance })
            }
            .sortedWith(compareByDescending<NameCandidate> { it.score }.thenBy { it.distance })

        val top = best.firstOrNull() ?: return null
        val runnerUp = best.getOrNull(1)
        if (top.distance <= 2 && (top.score >= 0.90 || (top.score >= 0.86 && (runnerUp == null || top.score - runnerUp.score >= 0.12)))) {
            android.util.Log.d("TextParser", "CandyName loose '${looseEligible.joinToString(" || ")}' -> '${top.name}' (${top.score})")
            return top.name
        }

        return null
    }

    private fun normalizeCandyInput(text: String): String? {
        if (text.isBlank()) return null
        val upper = text.uppercase()
            .replace('0', 'O')
            .replace('1', 'I')
            .replace('5', 'S')
            .replace('8', 'B')
        val stripped = upper
            .replace(Regex("""\b(?:CANDY|CNDY|CANOY|CAN[D0]Y|CANY|XL)\b"""), " ")
            .replace(Regex("[^A-Z\\s\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        return stripped.takeIf { it.length >= 3 }
    }

    private fun containsCandyTokenHint(text: String): Boolean {
        val upper = text.uppercase()
        return Regex("""CANDY|CNDY|CANOY|CAN[D0]Y|CANY|CNQDY|NQDY|IIDY""").containsMatchIn(upper)
    }





    fun parseNameFromFullText(fullText: String): String? {
        if (fullText.isBlank()) return null
        val blocked = Regex("CP|HP|STARDUST|CANDY|POWER|EVOLVE|MEGA|WEIGHT|HEIGHT|NORMAL|TRAINER|BATTLES|GYMS|RAIDS|XL|XS|PURIFIED|SHADOW|LUCKY|TRANSFER|FAVORITE|APPRAISE|WEATHER|BONUS|BUDDY|EGGS", RegexOption.IGNORE_CASE)
        for (line in fullText.split("\n").map { it.trim() }.filter { it.length >= 3 }) {
            if (blocked.containsMatchIn(line)) continue
            if (line.contains("/") || line.contains("kg", true)) continue
            val clean = line.replace(Regex("\\d+$"), "").trim()
            if (clean.length >= 3) parseName(clean)?.let { return it }
            parseName(line)?.let { return it }
        }
        return null
    }

    fun parseName(ocrText: String): String? {
        rankNameCandidates(ocrText, limit = 1).firstOrNull()?.let { ranked ->
            // Keep fast-ranked output only when confidence is clearly high.
            // Borderline noisy inputs should still flow through token-level logic.
            if (ranked.score >= 0.90) return ranked.name
        }
        if (ocrText.isBlank()) return null
        val clean = ocrText
            .replace('0', 'O')
            .replace('1', 'I')
            .replace('5', 'S')
            .replace('8', 'B')
            .replace(Regex("[^A-Za-z\\s\\-\\.]"), "")
            .trim()
            .lowercase()
        if (clean.length < 3) return null
        val compact = clean.replace(Regex("\\s+"), "")

        if (compact.length >= 3) {
            matchOcrAlias(compact)?.let { return it }
            pokemonNames.find { it == compact }?.let { return it.replaceFirstChar { c -> c.uppercase() } }
        }
        
        // Exact match check
        pokemonNames.find { it == clean }?.let { return it.replaceFirstChar { c -> c.uppercase() } }
        
        // Token based search
        val rawTokens = clean.split(Regex("\\s+")).filter { it.isNotBlank() }
        val tokens = if (compact.length >= 3 && !rawTokens.contains(compact)) rawTokens + compact else rawTokens
        for (token in tokens) {
            if (token.length < 3) continue
            matchOcrAlias(token)?.let { return it }
            pokemonNames.find { it == token }?.let { return it.replaceFirstChar { c -> c.uppercase() } }
            
            // Fuzzy match for token
            var tb: String? = null; var td = Int.MAX_VALUE
            val candidatePool = if (token.length >= 5) {
                val prefix3 = token.take(3)
                val prefix2 = token.take(2)
                // OCR 3. harfi bozabilir (orn: Zapdos -> Zandosas) 
                // Bu yuzden prefix2 VE prefix3 havuzlarini birlestiriyoruz
                val prefix3Filtered = pokemonNames.filter { it.startsWith(prefix3) }
                val prefix2Filtered = pokemonNames.filter { it.startsWith(prefix2) }
                val merged = (prefix3Filtered + prefix2Filtered).distinct()
                if (merged.isNotEmpty()) merged else pokemonNames
            } else {
                pokemonNames
            }
            for (name in candidatePool) {
                // Uzunluk farkÄ± toleransÄ± artÄ±rÄ±ldÄ± (Ã–rn: Esneonloo(9) vs Espeon(6))
                val strongPrefix = sharesStrongTokenPrefix(token, name)
                if (abs(name.length - token.length) > 3 && !strongPrefix) continue
                val d = levenshtein(token, name)
                val maxD = when {
                    name.length <= 4 -> 1 
                    name.length <= 6 -> 3 
                    name.length <= 9 -> 3 
                    else -> 5
                }
                val dynamicMaxD = if (strongPrefix) maxD + 2 else maxD
                if (token.length >= 7 && d >= 4 && !strongPrefix) continue
                if (d <= dynamicMaxD && (d < td || (d == td && name.length > (tb?.length ?: 0)))) {
                    td = d
                    tb = name
                }
            }
            tb?.let { 
                android.util.Log.d("TextParser", "Token match: '$token' -> '$it' (d=$td)")
                return it.replaceFirstChar { c -> c.uppercase() } 
            }
        }
        
        // Global fuzzy fallback
        var best: String? = null; var bd = Int.MAX_VALUE
        for (name in pokemonNames) {
            if (abs(name.length - clean.length) > 5) continue
            val d = levenshtein(clean, name)
            val maxD = when {
                name.length <= 5 -> 2
                name.length <= 8 -> 3  // Zapdos case: "zandosas"→"zangoose" was distance 3, now requires distance <= 3 but prefers exact prefixes
                else -> 4  // Reduce from 5 to 4 for longer names
            }
            if (d < bd && d <= maxD) { bd = d; best = name }
        }
        
        // Log clean text if no match found
        if (best == null) {
            android.util.Log.d("TextParser", "No name match for clean text: '$clean'")
        }
        
        best?.let { android.util.Log.d("TextParser", "Global match: '$clean' -> '$it' (d=$bd)") }
        return best?.replaceFirstChar { it.uppercase() }
    }

    fun parseStrongSpeciesName(ocrText: String): String? {
        if (ocrText.isBlank()) return null
        val clean = normalizeNameInput(ocrText) ?: return null
        val compact = clean.replace(Regex("\\s+"), "")
        if (compact.length >= 3) {
            matchOcrAlias(compact)?.let { return it.replaceFirstChar { c -> c.uppercase() } }
            pokemonNames.find { it == compact }?.let { return it.replaceFirstChar { c -> c.uppercase() } }
        }
        pokemonNames.find { it == clean }?.let { return it.replaceFirstChar { c -> c.uppercase() } }
        return null
    }

    fun rankNameCandidates(
        ocrText: String,
        limit: Int = 5,
        restrictTo: Collection<String>? = null
    ): List<NameCandidate> {
        val clean = normalizeNameInput(ocrText) ?: return emptyList()
        val compact = clean.replace(Regex("\\s+"), "")

        if (compact.length >= 3) {
            matchOcrAlias(compact)?.let {
                return listOf(NameCandidate(it, 1.0, 0))
            }
        }

        val candidatePool = (restrictTo?.map { it.lowercase() } ?: pokemonNames)
            .distinct()
            .filter { it.length >= 3 }
        val observations = buildObservations(clean, compact)
        val scored = candidatePool.mapNotNull { candidate ->
            scoreCandidate(candidate, observations)
        }.sortedWith(
            compareByDescending<NameCandidate> { it.score }
                .thenBy { it.distance }
                .thenBy { it.name.length }
        )

        return scored.take(limit).map {
            it.copy(name = it.name.replaceFirstChar { c -> c.uppercase() })
        }
    }

    fun findNamesWithPrefix(prefix: String, limit: Int = 8): List<String> {
        val normalized = prefix.lowercase().replace(Regex("[^a-z0-9]"), "")
        if (normalized.length < 3) return emptyList()
        return pokemonNames
            .filter { it.startsWith(normalized) && it.length > normalized.length }
            .take(limit)
            .map { it.replaceFirstChar { c -> c.uppercase() } }
    }

    fun parseMegaEnergy(text: String): Int? =
        Regex("""(\d+)\s*MEGA\s*ENERGY""").find(text.uppercase())?.groupValues?.get(1)?.toIntOrNull()

    fun parseWeight(text: String): Float? =
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*kg""").find(text.lowercase())?.groupValues?.get(1)?.toFloatOrNull()

    fun parseHeight(text: String): Float? =
        Regex("""([0-9]+(?:\.[0-9]+)?)\s*m\b""").find(text.lowercase())?.groupValues?.get(1)?.toFloatOrNull()

    fun parseLuckyLabel(vararg texts: String): Boolean {
        val normalized = texts
            .map { it.uppercase() }
            .map { it.replace('0', 'O').replace('1', 'I').replace('5', 'S') }
            .map { it.replace(Regex("[^A-Z]"), "") }
            .filter { it.isNotBlank() }

        if (normalized.any { text ->
            text.contains("LUCKY") && (
                text.contains("POKEMON") ||
                    text.contains("POKEM0N") ||
                    text.contains("POKEM") ||
                    text.contains("POKEMGN")
                )
        }) {
            return true
        }

        val joined = normalized.joinToString("")
        if (joined.length >= 8) {
            val compact = joined.take(18)
            if (commonPrefixLength(compact, "LUCKYPOKEMON") >= 3 && levenshtein(compact, "LUCKYPOKEMON") <= 8) {
                return true
            }
        }

        val tokenCandidates = texts
            .flatMap { it.uppercase().split(Regex("\\s+")) }
            .map { it.replace('0', 'O').replace('1', 'I').replace('5', 'S') }
            .map { it.replace(Regex("[^A-Z]"), "") }
            .filter { it.length >= 3 }

        val hasLuckyToken = tokenCandidates.any { token ->
            token.startsWith("LUC") ||
                commonPrefixLength(token, "LUCKY") >= 3 ||
                levenshtein(token, "LUCKY") <= 3
        }
        val hasPokemonToken = tokenCandidates.any { token ->
            token.contains("POK") ||
                token.endsWith("MON") ||
                commonPrefixLength(token, "POKEMON") >= 3 ||
                levenshtein(token, "POKEMON") <= 5
        }
        if (hasLuckyToken && hasPokemonToken) {
            return true
        }

        return normalized.any { token ->
            token.length in 10..18 &&
                token.startsWith("LUC") &&
                (token.endsWith("ON") || token.endsWith("MON") || token.contains("MON")) &&
                levenshtein(token, "LUCKYPOKEMON") <= 10
        }
    }

    fun parseGender(text: String): String? {
        val clean = text.lowercase()
        return when {
            clean.contains("â™‚") || clean.contains("(m)") -> "Male"
            clean.contains("â™€") || clean.contains("(f)") -> "Female"
            // OCR sometimes reads â™‚ as 'o' or '6' and â™€ as 'p' or '9' near the name
            else -> null
        }
    }

    fun parseSizeTag(text: String): String? {
        val upper = text.uppercase()
        return when {
            upper.contains("XXL") -> "XXL"
            upper.contains("XXS") -> "XXS"
            upper.contains(" XL ") || upper.startsWith("XL ") || upper.endsWith(" XL") -> "XL"
            upper.contains(" XS ") || upper.startsWith("XS ") || upper.endsWith(" XS") -> "XS"
            else -> null
        }
    }

    /**
     * Power Up (GÃ¼Ã§lendirme) maliyetini ayrÄ±ÅŸtÄ±rÄ±r.
     * OCR gÃ¼rÃ¼ltÃ¼sÃ¼nÃ¼ temizlemek iÃ§in virgÃ¼lleri ve rakam dÄ±ÅŸÄ± karakterleri atar.
     */
    fun parseStardust(text: String): Int? {
        if (text.isBlank()) return null
        val candidates = extractNumericCandidates(text)
            .filter { isValidStardust(it.value) }
            .sortedWith(compareBy<NumericCandidate> { surroundingDigitNoise(text, it) }.thenByDescending { it.value.toString().length })
        val best = candidates.firstOrNull() ?: return null
        return best.value.takeIf { surroundingDigitNoise(text, best) <= 2 }
    }

    fun parsePowerUpCandyCost(text: String): Int? {
        if (text.isBlank()) return null
        fun tokenFor(candidate: NumericCandidate): String = text.substring(candidate.start, candidate.end + 1)
        fun leadingZeroPenalty(candidate: NumericCandidate): Int {
            val token = tokenFor(candidate).replace(",", "").trim()
            return if (token.length > 1 && token.startsWith("0")) token.length else 0
        }
        return extractNumericCandidates(text)
            .filter { VALID_CANDY_COSTS.contains(it.value) }
            .sortedWith(
                compareBy<NumericCandidate> { surroundingDigitNoise(text, it) }
                    .thenBy { leadingZeroPenalty(it) }
                    .thenBy { (it.end - it.start + 1).coerceAtLeast(1) }
                    .thenByDescending { it.end }
                    .thenBy { it.value }
            )
            .firstOrNull {
                val noise = surroundingDigitNoise(text, it)
                noise <= 4 || (leadingZeroPenalty(it) > 0 && it.value <= 4)
            }
            ?.value
    }

    fun parsePowerUpCandyCost(vararg texts: String): Int? {
        texts.forEach { text ->
            parsePowerUpCandyCost(text)?.let { return it }
        }
        return null
    }

    fun parsePowerUpStardust(vararg texts: String): Int? {
        texts.forEach { text ->
            parseStardust(text)?.let { return it }
        }
        return null
    }

    fun parsePowerUpCostPair(vararg texts: String): Pair<Int, Int?>? {
        texts.forEach { text ->
            parsePowerUpCostPair(text, strict = false)?.let { return it }
        }
        return null
    }

    fun parsePowerUpCostPairStrict(vararg texts: String): Pair<Int, Int?>? {
        texts.forEach { text ->
            parsePowerUpCostPair(text, strict = true)?.let { return it }
        }
        return null
    }

    private fun parsePowerUpCostPair(text: String, strict: Boolean): Pair<Int, Int?>? {
        if (text.isBlank()) return null
        val numbers = extractNumericCandidates(text)
        if (numbers.isEmpty()) return null

        var bestPair: Pair<Int, Int> = -1 to Int.MAX_VALUE
        var bestValues: Pair<Int, Int>? = null

        numbers.forEachIndexed { stardustIndex, dustCandidate ->
            if (!isValidStardust(dustCandidate.value)) return@forEachIndexed
            numbers.drop(stardustIndex + 1).forEach { candyCandidate ->
                if (!VALID_CANDY_COSTS.contains(candyCandidate.value)) return@forEach
                if (!isCompatibleDustCandyPair(dustCandidate.value, candyCandidate.value)) return@forEach
                val gap = candyCandidate.start - dustCandidate.end - 1
                if (gap !in 0..10) return@forEach
                val trailingDigits = text.substring(candyCandidate.end + 1).count(Char::isDigit)
                if (strict && trailingDigits > 1) return@forEach
                val score = gap * 10 + trailingDigits
                if (bestValues == null || score < bestPair.second) {
                    bestPair = dustCandidate.value to score
                    bestValues = dustCandidate.value to candyCandidate.value
                }
            }
        }

        if (bestValues != null) return bestValues
        val repaired = parseNoisyMergedPowerUpPair(text)
        if (repaired != null) return repaired
        if (strict) return null

        val distinctValues = numbers.map { it.value }.distinct()
        val singleDust = if (distinctValues.size == 1) {
            distinctValues.singleOrNull(::isValidStardust)
        } else {
            null
        }

        return singleDust?.let { it to null }
    }

    private fun parseNoisyMergedPowerUpPair(text: String): Pair<Int, Int?>? {
        val rawTokens = Regex("""\d[\d,]*""").findAll(
            text.uppercase()
                .replace('O', '0')
                .replace('I', '1')
                .replace('L', '1')
                .replace('S', '5')
                .replace('B', '8')
        ).map { it.value.replace(",", "") }.toList()
        if (rawTokens.size < 2) return null

        for (i in rawTokens.indices) {
            val dustToken = rawTokens[i]
            val repairedDust = repairedStardustToken(dustToken) ?: continue
            for (j in (i + 1)..minOf(i + 3, rawTokens.lastIndex)) {
                val candyToken = rawTokens[j]
                val repairedCandy = repairedCandyToken(candyToken) ?: continue
                val noiseTokens = rawTokens.subList(i + 1, j)
                if (noiseTokens.all(::isIgnorableNumericNoiseToken) && isCompatibleDustCandyPair(repairedDust, repairedCandy)) {
                    return repairedDust to repairedCandy
                }
            }
        }

        for (i in 0 until rawTokens.lastIndex) {
            val dustToken = rawTokens[i]
            val candyToken = rawTokens[i + 1]
            val repairedDust = repairedStardustToken(dustToken) ?: continue
            val repairedCandy = repairedCandyToken(candyToken) ?: continue
            if (isCompatibleDustCandyPair(repairedDust, repairedCandy)) {
                return repairedDust to repairedCandy
            }
        }
        return null
    }

    private fun repairedStardustToken(token: String): Int? {
        if (token.isBlank()) return null
        token.toIntOrNull()?.takeIf(::isValidStardust)?.let { return it }
        if (token.length >= 4) {
            token.drop(1).toIntOrNull()?.takeIf(::isValidStardust)?.let { return it }
            token.take(token.length - 1).toIntOrNull()?.takeIf(::isValidStardust)?.let { return it }
        }
        return null
    }

    private fun repairedCandyToken(token: String): Int? {
        if (token.isBlank()) return null
        token.toIntOrNull()?.takeIf { VALID_CANDY_COSTS.contains(it) }?.let { return it }
        if (token.length >= 2) {
            token.takeLast(1).toIntOrNull()?.takeIf { VALID_CANDY_COSTS.contains(it) }?.let { return it }
            token.takeLast(2).toIntOrNull()?.takeIf { VALID_CANDY_COSTS.contains(it) }?.let { return it }
        }
        return null
    }

    private fun isIgnorableNumericNoiseToken(token: String): Boolean {
        val normalized = token.replace(",", "")
        if (normalized.isBlank()) return true
        if (normalized.length <= 2) return true
        if (normalized.length == 3 && normalized.all { it == normalized.first() }) return true
        return repairedCandyToken(normalized) == null && repairedStardustToken(normalized) == null
    }

    private fun isCompatibleDustCandyPair(dust: Int, candy: Int): Boolean {
        return COMPATIBLE_COST_PAIRS.contains(dust to candy)
    }

    private val REGULAR_STARDUST_COSTS = listOf(
        15000, 14000, 13000, 12000, 11000, 10000,
        9000, 8000, 7000, 6000, 5000, 4500, 4000,
        3500, 3000, 2500, 2200, 1900, 1600, 1300,
        1000, 800, 600, 400, 200
    )
    private val validListDescending = buildSet {
        addAll(REGULAR_STARDUST_COSTS)
        REGULAR_STARDUST_COSTS.forEach { cost ->
            add(cost / 2)
            add((cost * 0.9).toInt())
            add((cost * 0.9).toInt() + 1)
            add((cost * 1.2).toInt())
            add((cost * 1.2).toInt() + 1)
        }
    }.sortedDescending()
    private val VALID_CANDY_COSTS = setOf(1, 2, 3, 4, 6, 8, 10, 12, 15, 17, 20)
    private val REGULAR_DUST_TO_CANDY = listOf(
        200 to 1, 400 to 1, 600 to 1, 800 to 1, 1000 to 1,
        1300 to 2, 1600 to 2, 1900 to 2, 2200 to 2, 2500 to 2,
        3000 to 3, 3500 to 3, 4000 to 3, 4000 to 4,
        4500 to 4, 5000 to 4,
        6000 to 6,
        7000 to 8,
        8000 to 10,
        9000 to 12,
        10000 to 15, 10000 to 10,
        11000 to 10, 11000 to 12,
        12000 to 12, 12000 to 15,
        13000 to 15, 13000 to 17,
        14000 to 17, 14000 to 20,
        15000 to 20
    )
    private val COMPATIBLE_COST_PAIRS = buildSet {
        REGULAR_DUST_TO_CANDY.forEach { (dust, candy) ->
            add(dust to candy)
            add((dust / 2) to candy)
            scaledCostSet(dust, 1.2).forEach { scaledDust ->
                scaledCostSet(candy, 1.2).forEach { scaledCandy -> add(scaledDust to scaledCandy) }
            }
            scaledCostSet(dust, 0.9).forEach { scaledDust ->
                scaledCostSet(candy, 0.9).forEach { scaledCandy -> add(scaledDust to scaledCandy) }
            }
        }
    }

    private data class NumericCandidate(
        val value: Int,
        val start: Int,
        val end: Int
    )

    private fun isValidStardust(v: Int): Boolean {
        return validListDescending.contains(v)
    }

    private fun scaledCostSet(base: Int, multiplier: Double): Set<Int> {
        val raw = base * multiplier
        return setOf(kotlin.math.floor(raw).toInt(), kotlin.math.round(raw).toInt(), kotlin.math.ceil(raw).toInt())
            .filter { it > 0 }
            .toSet()
    }

    private fun extractNumericCandidates(text: String): List<NumericCandidate> {
        if (text.isBlank()) return emptyList()
        val normalized = text.uppercase()
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
            .replace('S', '5')
            .replace('B', '8')
        val tokenRegex = Regex("""\d[\d,]*""")
        val matches = tokenRegex.findAll(normalized).toList()
        if (matches.isEmpty()) return emptyList()

        val candidates = mutableListOf<NumericCandidate>()
        matches.forEach { match ->
            parseNumericToken(match.value)?.let { value ->
                candidates += NumericCandidate(value = value, start = match.range.first, end = match.range.last)
            }
        }

        for (i in 0 until matches.lastIndex) {
            val first = matches[i]
            val second = matches[i + 1]
            val gap = normalized.substring(first.range.last + 1, second.range.first)
            if (!gap.all { it == ' ' || it == ',' }) continue
            val merged = mergeThousandsTokens(first.value, second.value) ?: continue
            candidates += NumericCandidate(
                value = merged,
                start = first.range.first,
                end = second.range.last
            )
        }

        return candidates
            .distinctBy { Triple(it.value, it.start, it.end) }
            .sortedWith(compareBy<NumericCandidate> { it.start }.thenByDescending { it.value.toString().length })
    }

    private fun parseNumericToken(token: String): Int? {
        val digits = token.replace(",", "")
        return digits.toIntOrNull()
    }

    private fun mergeThousandsTokens(first: String, second: String): Int? {
        val firstDigits = first.replace(",", "")
        val secondDigits = second.replace(",", "")
        if (firstDigits.isEmpty() || secondDigits.length != 3) return null
        if (firstDigits.length !in 1..2) return null
        return (firstDigits + secondDigits).toIntOrNull()
    }

    private fun surroundingDigitNoise(text: String, candidate: NumericCandidate): Int {
        val before = text.take(candidate.start).count(Char::isDigit)
        val after = text.drop(candidate.end + 1).count(Char::isDigit)
        return before + after
    }

    private fun loadPokemonNames(context: Context): List<String> {
        return try {
            val names = Gson().fromJson<List<String>>(
                InputStreamReader(context.assets.open("data/pokemon_names.json")),
                object : TypeToken<List<String>>() {}.type
            )
            names.map { it.lowercase() }
        } catch (e: Exception) {
            android.util.Log.e("TextParser","Failed to load Pokemon names", e)
            runCatching {
                val fallback = java.io.File("app/src/main/assets/data/pokemon_names.json")
                if (fallback.exists()) {
                    Gson().fromJson<List<String>>(
                        fallback.reader(),
                        object : TypeToken<List<String>>() {}.type
                    ).map { it.lowercase() }
                } else {
                    listOf("porygon", "espeon", "gyarados")
                }
            }.getOrDefault(listOf("porygon", "espeon", "gyarados"))
        }
    }

    private fun normalizeNameInput(ocrText: String): String? {
        if (ocrText.isBlank()) return null
        val clean = ocrText
            .replace('0', 'O')
            .replace('1', 'I')
            .replace('5', 'S')
            .replace('8', 'B')
            .replace(Regex("[^A-Za-z\\s\\-\\.]"), "")
            .trim()
            .lowercase()
        return clean.takeIf { it.length >= 3 }
    }

    private fun buildObservations(clean: String, compact: String): List<String> {
        return buildList {
            add(clean)
            if (compact.length >= 3) add(compact)
            clean.split(Regex("\\s+"))
                .filter { it.length >= 3 }
                .forEach { add(it) }
        }.flatMap { observation ->
            listOf(observation) + buildOcrConfusionVariants(observation)
        }.distinct()
    }

    private fun buildOcrConfusionVariants(observation: String): List<String> {
        if (observation.length < 5) return emptyList()
        val variants = linkedSetOf<String>()

        fun addSingleReplacementVariants(source: String, from: Char, to: Char) {
            source.forEachIndexed { index, char ->
                if (char != from) return@forEachIndexed
                val replaced = buildString(source.length) {
                    append(source, 0, index)
                    append(to)
                    append(source, index + 1, source.length)
                }
                variants += replaced
            }
        }

        if ('i' in observation) {
            addSingleReplacementVariants(observation, 'i', 'l')
            variants += observation.replace('i', 'l')
        }
        if ('l' in observation) {
            addSingleReplacementVariants(observation, 'l', 'i')
            variants += observation.replace('l', 'i')
        }

        return variants
            .filter { it.length >= 3 && it != observation }
            .take(6)
    }

    private fun scoreCandidate(candidate: String, observations: List<String>): NameCandidate? {
        var bestScore = Double.NEGATIVE_INFINITY
        var bestDistance = Int.MAX_VALUE

        for (observation in observations) {
            if (observation.length < 3) continue
            val distance = levenshtein(observation, candidate)
            val maxDistance = when {
                candidate.length <= 5 -> 2
                candidate.length <= 8 -> 4
                else -> 5
            }
            if (distance > maxDistance) continue

            val prefixLength = commonPrefixLength(observation, candidate)
            if (observation.length >= 7 && distance >= 4 && prefixLength < 2) continue
            if (observation.length >= 6 && observation.first() != candidate.first() && prefixLength < 2 && distance >= 3) continue

            val lengthPenalty = abs(observation.length - candidate.length).toDouble() / maxOf(observation.length, candidate.length).toDouble()
            val distancePenalty = distance.toDouble() / maxOf(observation.length, candidate.length).toDouble()
            val prefixBonus = min(prefixLength, 4) * 0.06
            val firstCharBonus = if (observation.firstOrNull() == candidate.firstOrNull()) 0.08 else 0.0
            val containsBonus = if (candidate.contains(observation.take(min(4, observation.length))) || observation.contains(candidate.take(min(4, candidate.length)))) 0.08 else 0.0
            val score = (1.0 - distancePenalty - 0.25 * lengthPenalty + prefixBonus + firstCharBonus + containsBonus)

            if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                bestScore = score
                bestDistance = distance
            }
        }

        if (bestDistance == Int.MAX_VALUE || bestScore < 0.35) return null
        return NameCandidate(candidate, bestScore.coerceIn(0.0, 1.0), bestDistance)
    }

    private val ocrAliasPatterns = listOf(
        Regex("^swa[qg]?b{2,}$") to "swablu",
        Regex("^sauirtle[a-z]*$") to "squirtle",
        Regex("^squirtl[ea][a-z]*$") to "squirtle",
        Regex("^squirtie[a-z]*$") to "squirtle",
        Regex("^zandos[a-z]*$") to "zapdos",
        Regex("^sno[a-z]{8,}$") to "snorlax"
    )

    private fun matchOcrAlias(compact: String): String? {
        if (compact.startsWith("swa") && compact.length in 6..9 && compact.contains('q')) {
            android.util.Log.d("TextParser", "Alias match: '$compact' -> 'swablu'")
            return "Swablu"
        }
        if (compact.startsWith("s") && compact.contains("uirtl")) {
            android.util.Log.d("TextParser", "Alias match: '$compact' -> 'squirtle'")
            return "Squirtle"
        }
        for ((regex, name) in ocrAliasPatterns) {
            if (regex.matches(compact)) {
                android.util.Log.d("TextParser", "Alias match: '$compact' -> '$name'")
                return name.replaceFirstChar { it.uppercase() }
            }
        }
        return null
    }

    private fun sharesStrongTokenPrefix(token: String, name: String): Boolean {
        if (token.length < 3 || name.length < 3) return false
        return token.take(3) == name.take(3)
    }

    private fun commonPrefixLength(first: String, second: String): Int {
        val maxLength = min(first.length, second.length)
        for (index in 0 until maxLength) {
            if (first[index] != second[index]) return index
        }
        return maxLength
    }

    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val l0 = lhs.length; val l1 = rhs.length
        var cost = IntArray(l0); var nc = IntArray(l0)
        for (i in 0 until l0) cost[i] = i
        for (j in 1 until l1) {
            nc[0] = j
            for (i in 1 until l0) {
                val m = if (lhs[i-1] == rhs[j-1]) 0 else 1
                nc[i] = min(min(cost[i]+1, nc[i-1]+1), cost[i-1]+m)
            }
            val s = cost; cost = nc; nc = s
        }
        return cost[l0-1]
    }
}

