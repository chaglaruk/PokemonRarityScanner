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
        val repeatedSpecies = repeatedValues(frames.mapNotNull { speciesName(it.data) })
        val speciesBacked = frames.filter { speciesName(it.data) in repeatedSpecies }.ifEmpty { frames }
        val repeatedCp = repeatedValues(speciesBacked.mapNotNull { it.data.cp })
        val cpBacked = speciesBacked.filter { it.data.cp in repeatedCp }.ifEmpty { speciesBacked }
        return cpBacked.maxByOrNull { frameScore(it) }
    }

    fun validCpCandidates(frames: List<ScanFrameCandidate>): List<Int> {
        return frames
            .filter { it.cpQuality >= CP_QUALITY_MIN }
            .mapNotNull { it.data.cp }
    }

    fun isHighConfidence(frames: List<ScanFrameCandidate>): Boolean {
        val current = frames.lastOrNull() ?: return false
        if (!hasHighConfidenceShape(current)) return false
        val species = speciesName(current.data) ?: return false
        val cp = current.data.cp ?: return false
        return frames.count {
            speciesName(it.data) == species &&
                it.data.cp == cp &&
                hasHighConfidenceShape(it)
        } >= 2
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
        if (topTextConfidence < 0.86) return true
        if (cpQuality < CP_QUALITY_MIN) return true
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

    private fun hasHighConfidenceShape(frame: ScanFrameCandidate): Boolean {
        val cpVal = frame.data.cp ?: 0
        val hasSupportSignal = frame.data.hp != null || frame.data.arcLevel != null || frame.data.caughtDate != null
        return cpVal >= 100 &&
            speciesName(frame.data) != null &&
            frame.cpQuality >= CP_QUALITY_MIN &&
            hasSupportSignal
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

    private fun <T> repeatedValues(values: List<T>): Set<T> {
        return values.groupingBy { it }.eachCount().filterValues { it >= 2 }.keys
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

    private fun speciesName(data: PokemonData): String? {
        return data.realName.takeUnless(::isUnknownSpecies)
            ?: data.name.takeUnless(::isUnknownSpecies)
    }
}
