package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData

internal data class ScanFrameCandidate(
    val path: String,
    val data: PokemonData,
    val cpQuality: Double
)

internal object ScanFrameFusion {
    const val CP_QUALITY_MIN = 0.55

    fun selectBestFrame(frames: List<ScanFrameCandidate>): ScanFrameCandidate? {
        return frames.maxByOrNull { frameScore(it) }
    }

    fun validCpCandidates(frames: List<ScanFrameCandidate>): List<Int> {
        return frames
            .filter { it.cpQuality >= CP_QUALITY_MIN }
            .mapNotNull { it.data.cp }
    }

    fun isHighConfidence(data: PokemonData, cpQuality: Double): Boolean {
        val cpVal = data.cp ?: 0
        val hasSupportSignal = data.hp != null || data.arcLevel != null || data.caughtDate != null
        return cpVal >= 100 && data.name != "Unknown" && cpQuality >= CP_QUALITY_MIN && hasSupportSignal
    }

    fun shouldRunDetailedPass(
        pokemon: PokemonData,
        cpQuality: Double,
        topTextConfidence: Double
    ): Boolean {
        if (pokemon.cp == null || pokemon.cp <= 0) return true
        if (isUnknownSpecies(pokemon.name)) return true
        if (pokemon.hp == null && pokemon.maxHp == null) return true
        if (pokemon.caughtDate == null) return true
        if (pokemon.candyName.isNullOrBlank() && topTextConfidence < 0.86) return true
        if (topTextConfidence < 0.86) return true
        if (cpQuality < CP_QUALITY_MIN) return true
        if (topTextConfidence < 0.78) return true
        return false
    }

    fun fuse(
        frames: List<ScanFrameCandidate>,
        authoritative: PokemonData,
        detailed: PokemonData,
        validCpList: List<Int>,
        bestCpQuality: Double
    ): PokemonData {
        val hpPair = mostFrequent(frames.map {
            val hp = it.data.hp
            val maxHp = it.data.maxHp
            if (hp == null && maxHp == null) null else (hp to maxHp)
        })
        val stardust = mostFrequent(frames.map { it.data.stardust })
        val powerUpCandyCost = mostFrequent(frames.map { it.data.powerUpCandyCost })
        val powerUpCandySource = mostFrequent(frames.map { it.data.powerUpCandySource })
        val powerUpStardustSource = mostFrequent(frames.map { it.data.powerUpStardustSource })
        val caughtDate = mostFrequent(frames.map { it.data.caughtDate })
        val arcValues = frames.mapNotNull { it.data.arcLevel }.sorted()
        val arcLevel = if (arcValues.isNotEmpty()) {
            arcValues[arcValues.size / 2]
        } else null
        val consensusName = mostFrequent(frames.map { it.data.name }.map { it.takeUnless(::isUnknownSpecies) })
        val consensusRealName = mostFrequent(frames.map { it.data.realName }.map { it.takeUnless(::isUnknownSpecies) })

        val consensusCp = mostFrequent(
            frames
                .filter { it.cpQuality >= CP_QUALITY_MIN }
                .map { it.data.cp }
        )
        val keepAuthoritativeCp = authoritative.cp != null &&
            bestCpQuality >= CP_QUALITY_MIN &&
            validCpList.contains(authoritative.cp)
        val cp = when {
            keepAuthoritativeCp -> authoritative.cp
            consensusCp != null -> consensusCp
            detailed.cp != null && validCpList.contains(detailed.cp) -> detailed.cp
            else -> authoritative.cp ?: detailed.cp
        }

        return authoritative.copy(
            cp = cp,
            hp = hpPair?.first ?: authoritative.hp ?: detailed.hp,
            maxHp = hpPair?.second ?: authoritative.maxHp ?: detailed.maxHp,
            stardust = stardust ?: detailed.stardust ?: authoritative.stardust,
            arcLevel = arcLevel ?: authoritative.arcLevel ?: detailed.arcLevel,
            name = authoritative.name.takeUnless(::isUnknownSpecies)
                ?: consensusName
                ?: detailed.name.takeUnless(::isUnknownSpecies)
                ?: authoritative.name,
            realName = authoritative.realName.takeUnless(::isUnknownSpecies)
                ?: consensusRealName
                ?: detailed.realName.takeUnless(::isUnknownSpecies)
                ?: authoritative.realName,
            candyName = detailed.candyName ?: authoritative.candyName,
            megaEnergy = detailed.megaEnergy ?: authoritative.megaEnergy,
            weight = detailed.weight ?: authoritative.weight,
            height = detailed.height ?: authoritative.height,
            gender = authoritative.gender ?: detailed.gender,
            caughtDate = authoritative.caughtDate ?: caughtDate ?: detailed.caughtDate,
            rawOcrText = mergeRawOcrText(authoritative.rawOcrText, detailed.rawOcrText),
            powerUpCandyCost = powerUpCandyCost ?: detailed.powerUpCandyCost ?: authoritative.powerUpCandyCost,
            powerUpCandySource = powerUpCandySource ?: detailed.powerUpCandySource ?: authoritative.powerUpCandySource,
            powerUpStardustSource = powerUpStardustSource ?: detailed.powerUpStardustSource ?: authoritative.powerUpStardustSource
        )
    }

    private fun frameScore(frame: ScanFrameCandidate): Int {
        return scoreFor(frame.data) + (frame.cpQuality * 20.0).toInt()
    }

    private fun scoreFor(data: PokemonData): Int {
        var score = 0
        val cpVal = data.cp ?: 0
        if (cpVal >= 100) score += 100
        else if (cpVal > 0) score += 50

        if (data.name != "Unknown") score += 30
        if (data.hp != null) score += 20
        if (data.arcLevel != null) score += 20
        if (data.caughtDate != null) score += 10
        return score
    }

    private fun <T> mostFrequent(values: List<T?>): T? {
        val counts = values.filterNotNull().groupingBy { it }.eachCount()
        return counts.entries.maxByOrNull { it.value }?.key
    }

    private fun mergeRawOcrText(primaryRaw: String, detailedRaw: String): String {
        val primaryFields = parseRawOcrFields(primaryRaw)
        val detailedFields = parseRawOcrFields(detailedRaw)
        val primaryPreferredKeys = setOf("CP", "HP", "HPWM", "HPClean", "HPBlock", "Name", "NameHC")
        val orderedKeys = linkedSetOf<String>().apply {
            addAll(primaryFields.keys)
            addAll(detailedFields.keys)
        }

        return orderedKeys.joinToString("|") { key ->
            val primaryValue = primaryFields[key].orEmpty()
            val detailedValue = detailedFields[key].orEmpty()
            val mergedValue = when {
                key in primaryPreferredKeys -> primaryValue.ifBlank { detailedValue }
                detailedValue.isNotBlank() -> detailedValue
                else -> primaryValue
            }
            "$key:$mergedValue"
        }
    }

    private fun parseRawOcrFields(raw: String): LinkedHashMap<String, String> {
        val result = linkedMapOf<String, String>()
        raw.split("|").forEach { part ->
            val separator = part.indexOf(':')
            if (separator <= 0) return@forEach
            val key = part.substring(0, separator)
            val value = part.substring(separator + 1)
            result[key] = value
        }
        return result
    }

    private fun isUnknownSpecies(value: String?): Boolean {
        return value.isNullOrBlank() || value.equals("Unknown", ignoreCase = true)
    }
}
