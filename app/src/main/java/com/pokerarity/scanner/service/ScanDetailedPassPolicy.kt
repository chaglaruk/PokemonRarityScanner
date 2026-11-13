package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus

/** Early-exit and detailed-pass policy separated from frame merge mechanics. */
internal object ScanDetailedPassPolicy {
    private const val MIN_VALID_CP = 1
    private const val MIN_TOP_TEXT_CONFIDENCE = 0.86

    private val hardAuthorities = setOf(
        SpeciesAuthority.INDEPENDENT_PROFILE,
        SpeciesAuthority.EXACT_CANONICAL,
        SpeciesAuthority.REVIEWED_ALIAS
    )

    fun isHighConfidence(frames: List<ScanFrameCandidate>): Boolean {
        val current = frames.lastOrNull()
        val currentEvidenceIsStrong = current?.let {
            hasHighConfidenceEvidence(it) && hasHighConfidenceShape(it)
        } == true
        val species = current?.speciesEvidence?.selectedCanonicalSpecies
        val cp = current?.data?.cp
        val repeatedStrongEvidence = species != null && cp != null && frames.count {
            it.speciesEvidence.selectedCanonicalSpecies.equals(species, ignoreCase = true) &&
                it.data.cp == cp &&
                hasHighConfidenceEvidence(it) &&
                hasHighConfidenceShape(it)
        } >= 2
        return earlyExitBlockReasons(frames).isEmpty() && currentEvidenceIsStrong && repeatedStrongEvidence
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
        if (authorityBlocked) add(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY)
        if (speciesEvidence.candidatesClose) add(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_MARGIN)
        if (speciesEvidence.profileStatus != SpeciesProfileStatus.COMPATIBLE) {
            add(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_PROFILE)
        }
        if (isNotEmpty()) add(SpeciesEvidenceReason.DETAILED_PASS_REQUESTED)
    }

    fun shouldRunDetailedPass(
        pokemon: PokemonData,
        cpQuality: Double,
        topTextConfidence: Double
    ): Boolean = pokemon.cp == null || pokemon.cp <= 0 ||
        isUnknownSpecies(pokemon.name) ||
        (pokemon.hp == null && pokemon.maxHp == null) ||
        (pokemon.caughtDate == null && pokemon.caughtDateLocalDate == null) ||
        topTextConfidence < MIN_TOP_TEXT_CONFIDENCE ||
        cpQuality < ScanFrameFusion.CP_QUALITY_MIN

    fun shouldRunDetailedPass(
        pokemon: PokemonData,
        cpQuality: Double,
        speciesEvidence: SpeciesEvidence
    ): Boolean {
        val independentCompatible = pokemon.recognitionObservation != null &&
            speciesEvidence.authority == SpeciesAuthority.INDEPENDENT_PROFILE &&
            speciesEvidence.profileStatus == SpeciesProfileStatus.COMPATIBLE
        return when {
            independentCompatible -> false
            detailedPassReasons(speciesEvidence).isNotEmpty() -> true
            else -> pokemon.cp == null || pokemon.cp < MIN_VALID_CP ||
                isUnknownSpecies(pokemon.name) ||
                (pokemon.hp == null && pokemon.maxHp == null) ||
                (pokemon.caughtDate == null && pokemon.caughtDateLocalDate == null) ||
                cpQuality < ScanFrameFusion.CP_QUALITY_MIN
        }
    }

    private fun hasHighConfidenceEvidence(frame: ScanFrameCandidate): Boolean {
        val evidence = frame.speciesEvidence
        return evidence.authority in hardAuthorities &&
            evidence.profileStatus == SpeciesProfileStatus.COMPATIBLE &&
            evidence.observationsAgree && !evidence.authorityConflict &&
            !evidence.candidatesClose && !evidence.selectedCanonicalSpecies.isNullOrBlank()
    }

    private fun hasHighConfidenceShape(frame: ScanFrameCandidate): Boolean {
        val cpVal = frame.data.cp ?: 0
        val hasSupportSignal = frame.data.hp != null || frame.data.arcLevel != null ||
            frame.data.caughtDate != null || frame.data.caughtDateLocalDate != null
        return cpVal >= 100 && speciesName(frame.data) != null &&
            frame.cpQuality >= ScanFrameFusion.CP_QUALITY_MIN && hasSupportSignal
    }

    private fun speciesName(data: PokemonData): String? =
        data.realName.takeUnless(::isUnknownSpecies) ?: data.name.takeUnless(::isUnknownSpecies)

    private fun isUnknownSpecies(value: String?): Boolean =
        value.isNullOrBlank() || value.equals("Unknown", ignoreCase = true)
}
