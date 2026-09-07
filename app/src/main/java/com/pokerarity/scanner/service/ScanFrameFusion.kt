package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus

internal data class ScanFrameCandidate(
    val path: String,
    val data: PokemonData,
    val cpQuality: Double,
    val speciesEvidence: SpeciesEvidence = SpeciesEvidence.failClosed()
)

internal data class AnchoredFrameSelection(
    val frame: ScanFrameCandidate,
    val speciesEvidence: SpeciesEvidence
)

internal object ScanFrameFusion {
    const val CP_QUALITY_MIN = 0.55

    /**
     * Preserve an observed screen as a whole, while checking every readable screen
     * for explicit contradictions. An unresolved nickname cannot hide a change in
     * the independently observed candy family or numeric profile.
     */
    fun resolveAnchoredFrames(
        frames: List<ScanFrameCandidate>,
        authoritative: ScanFrameCandidate,
        detailed: ScanFrameCandidate? = null,
        deriveEvidence: (PokemonData) -> SpeciesEvidence
    ): AnchoredFrameSelection? {
        if (authoritative.data.recognitionObservation == null) return null
        val sameSourceDetailed = detailed?.takeIf {
            it.path == authoritative.path && it.data.recognitionObservation != null
        }
        val allFrames = (frames + authoritative + listOfNotNull(sameSourceDetailed)).distinct()
        val observed = allFrames.filter { frame ->
            frame.data.recognitionObservation?.let { it.detailScreen && !it.numericConflict } == true
        }
        // Malformed HP does not invalidate separately anchored candy/type labels.
        val semanticFrames = allFrames.filter { frame ->
            frame.data.recognitionObservation?.let {
                it.detailScreen || (!it.candySpecies.isNullOrBlank() && !it.types.isNullOrEmpty())
            } == true
        }
        fun <T> disagrees(values: List<T?>): Boolean = values.filterNotNull().distinct().size > 1
        val fieldConflict = disagrees(semanticFrames.map { it.data.recognitionObservation?.candySpecies?.trim()?.lowercase() }) ||
            disagrees(semanticFrames.map { it.data.recognitionObservation?.types?.takeIf { types -> types.isNotEmpty() }
                ?.map { type -> type.lowercase() }?.toSet() }) ||
            disagrees(observed.map { it.data.cp }) ||
            disagrees(observed.map { it.data.maxHp }) ||
            disagrees(observed.map { it.data.recognitionObservation?.powerUpStardust })
        val observedEvidence = observed.map { it to deriveEvidence(it.data) }
        val independentSpecies = observedEvidence.mapNotNull { (_, evidence) ->
            evidence.selectedCanonicalSpecies?.lowercase()?.takeIf { hasCompatibleAuthority(evidence) }
        }.distinct()
        if (fieldConflict || independentSpecies.size > 1 || observedEvidence.any { it.second.authorityConflict }) {
            return AnchoredFrameSelection(authoritative, SpeciesEvidence(
                selectedCanonicalSpecies = null,
                authority = SpeciesAuthority.CONFLICT,
                profileStatus = SpeciesProfileStatus.CONTRADICTORY,
                reasonCodes = listOf(SpeciesEvidenceReason.AUTHORITY_CONFLICT, "anchored_frame_observations_conflict"),
                observationsAgree = false,
                authorityConflict = true
            ))
        }
        val authoritativeEvidence = deriveEvidence(authoritative.data)
        val detailedEvidence = sameSourceDetailed?.let { deriveEvidence(it.data) }
        val chooseDetailed = sameSourceDetailed != null && detailedEvidence != null &&
            hasCompatibleAuthority(detailedEvidence) &&
            (!hasCompatibleAuthority(authoritativeEvidence) ||
                hasStrictlyMoreObservedFields(sameSourceDetailed.data, authoritative.data))
        val selected = if (chooseDetailed) sameSourceDetailed!! else authoritative
        return AnchoredFrameSelection(selected, if (chooseDetailed) detailedEvidence!! else authoritativeEvidence)
    }

    private fun hasCompatibleAuthority(evidence: SpeciesEvidence): Boolean =
        evidence.hasHardAuthority && evidence.profileStatus == SpeciesProfileStatus.COMPATIBLE &&
            evidence.observationsAgree && !evidence.authorityConflict && !evidence.candidatesClose

    private fun hasStrictlyMoreObservedFields(candidate: PokemonData, baseline: PokemonData): Boolean {
        fun fields(pokemon: PokemonData): Set<String> = buildSet {
            if (pokemon.cp != null) add("cp")
            if (pokemon.maxHp != null) add("maximum_hp")
            if (!pokemon.recognitionObservation?.candySpecies.isNullOrBlank()) add("candy")
            if (!pokemon.recognitionObservation?.types.isNullOrEmpty()) add("types")
            if (pokemon.recognitionObservation?.powerUpStardust != null) add("power_up_cost")
        }
        val candidateFields = fields(candidate)
        val baselineFields = fields(baseline)
        return candidateFields.containsAll(baselineFields) && candidateFields.size > baselineFields.size
    }

    fun selectBestFrame(frames: List<ScanFrameCandidate>): ScanFrameCandidate? {
        frames.filter { it.speciesEvidence.authority == SpeciesAuthority.INDEPENDENT_PROFILE &&
            it.speciesEvidence.profileStatus == SpeciesProfileStatus.COMPATIBLE }
            .maxByOrNull { frameScore(it) }?.let { return it }
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
        if (earlyExitBlockReasons(frames).isNotEmpty()) return false
        val current = frames.lastOrNull() ?: return false
        if (!hasHighConfidenceEvidence(current) || !hasHighConfidenceShape(current)) return false
        val species = current.speciesEvidence.selectedCanonicalSpecies ?: return false
        val cp = current.data.cp ?: return false
        return frames.count {
            it.speciesEvidence.selectedCanonicalSpecies.equals(species, ignoreCase = true) &&
                it.data.cp == cp &&
                hasHighConfidenceEvidence(it) &&
                hasHighConfidenceShape(it)
        } >= 2
    }

    fun earlyExitBlockReasons(frames: List<ScanFrameCandidate>): List<String> = buildList {
        val selected = frames.mapNotNull { it.speciesEvidence.selectedCanonicalSpecies }
            .distinctBy { it.lowercase() }
        val hasAuthorityConflict = frames.any {
            !it.speciesEvidence.hasHardAuthority ||
                !it.speciesEvidence.observationsAgree ||
                it.speciesEvidence.authorityConflict ||
                it.speciesEvidence.authority == SpeciesAuthority.CONFLICT
        }
        if (selected.size > 1 || hasAuthorityConflict) {
            add(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY)
        }
        if (frames.any { it.speciesEvidence.candidatesClose }) {
            add(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_MARGIN)
        }
        if (frames.any { it.speciesEvidence.profileStatus != SpeciesProfileStatus.COMPATIBLE }) {
            add(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_PROFILE)
        }
    }

    fun detailedPassReasons(speciesEvidence: SpeciesEvidence): List<String> = buildList {
        val authorityBlocked = !speciesEvidence.hasHardAuthority ||
            !speciesEvidence.observationsAgree ||
            speciesEvidence.authorityConflict ||
            speciesEvidence.authority == SpeciesAuthority.CONFLICT
        if (authorityBlocked) {
            add(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY)
        }
        if (speciesEvidence.candidatesClose) add(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_MARGIN)
        if (speciesEvidence.profileStatus != SpeciesProfileStatus.COMPATIBLE) {
            add(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_PROFILE)
        }
        if (isNotEmpty()) add(SpeciesEvidenceReason.DETAILED_PASS_REQUESTED)
    }

    private fun hasHighConfidenceEvidence(frame: ScanFrameCandidate): Boolean {
        val evidence = frame.speciesEvidence
        return evidence.authority in hardAuthorities &&
            evidence.profileStatus == SpeciesProfileStatus.COMPATIBLE &&
            evidence.observationsAgree &&
            !evidence.authorityConflict &&
            !evidence.candidatesClose &&
            !evidence.selectedCanonicalSpecies.isNullOrBlank()
    }

    fun shouldRunDetailedPass(
        pokemon: PokemonData,
        cpQuality: Double,
        topTextConfidence: Double
    ): Boolean {
        val needsDetailed = pokemon.cp == null || pokemon.cp <= 0 ||
            isUnknownSpecies(pokemon.name) ||
            (pokemon.hp == null && pokemon.maxHp == null) ||
            pokemon.caughtDate == null ||
            topTextConfidence < 0.86 ||
            cpQuality < CP_QUALITY_MIN
        return needsDetailed
    }

    fun shouldRunDetailedPass(
        pokemon: PokemonData,
        cpQuality: Double,
        speciesEvidence: SpeciesEvidence
    ): Boolean {
        if (pokemon.recognitionObservation != null && speciesEvidence.authority == SpeciesAuthority.INDEPENDENT_PROFILE &&
            speciesEvidence.profileStatus == SpeciesProfileStatus.COMPATIBLE) return false
        if (detailedPassReasons(speciesEvidence).isNotEmpty()) return true
        val needsDetailed = pokemon.cp == null || pokemon.cp <= 0 ||
            isUnknownSpecies(pokemon.name) ||
            (pokemon.hp == null && pokemon.maxHp == null) ||
            pokemon.caughtDate == null ||
            cpQuality < CP_QUALITY_MIN
        return needsDetailed
    }

    fun fuse(
        frames: List<ScanFrameCandidate>,
        authoritative: PokemonData,
        detailed: PokemonData,
        validCpList: List<Int>,
        bestCpQuality: Double
    ): PokemonData {
        if (authoritative.recognitionObservation != null || detailed.recognitionObservation != null) {
            // CP, maximum HP, candy, type and cost must describe a single screen.
            // Voting these independently can construct a plausible Pokemon that was never seen.
            return authoritative
        }
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

    private val hardAuthorities = setOf(
        SpeciesAuthority.INDEPENDENT_PROFILE,
        SpeciesAuthority.EXACT_CANONICAL,
        SpeciesAuthority.REVIEWED_ALIAS
    )
}
