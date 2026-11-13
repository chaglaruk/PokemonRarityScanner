package com.pokerarity.scanner.service

import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence

/** Bounded same-capture recovery for profiles contradicted by downscaled CP OCR. */
internal object NativeResolutionProfileRecovery {
    const val RECOVERY_REASON = "native_resolution_recovered_profile"

    private const val BASELINE_OCR_MAX_WIDTH = 900

    /**
     * Use the independent native profile only when the CP read changed and all fast
     * frames preserve the same candy, type, HP, and max HP evidence. The returned
     * fast-frame copies no longer let their contradictory low-resolution CP vote
     * veto identity; all other conflicts remain visible to frame fusion.
     */
    fun prepareFrames(
        fastFrames: List<ScanFrameCandidate>,
        authoritative: ScanFrameCandidate,
        detailed: ScanFrameCandidate
    ): List<ScanFrameCandidate>? {
        val species = detailed.speciesEvidence.selectedCanonicalSpecies?.takeIf { it.isNotBlank() }
        val candidates = (fastFrames + authoritative).distinct()
        val canRecover = species?.let { selectedSpecies ->
            val evidenceAllowsRecovery = hasValidResolutionPair(authoritative, detailed) &&
                hasIndependentNativeProfile(detailed) && hasContradictoryFastProfile(authoritative)
            val sameCaptureChangedCp = authoritative.data.cp != detailed.data.cp &&
                authoritative.data.recognitionObservation?.frameIndex ==
                detailed.data.recognitionObservation?.frameIndex
            val burstSupportsRecovery = candidates.isNotEmpty() &&
                candidates.all { fastCandidateAgrees(it, authoritative, detailed, selectedSpecies) } &&
                hasStableActionCosts(candidates)
            evidenceAllowsRecovery && sameCaptureChangedCp && burstSupportsRecovery
        } == true

        return if (canRecover) {
            (candidates + detailed).distinct().map { frame ->
                if (frame.recognitionWidth <= authoritative.recognitionWidth) {
                    frame.copy(
                        data = frame.data.copy(cp = null),
                        speciesEvidence = frame.speciesEvidence.withProfileStatus(SpeciesProfileStatus.INDETERMINATE)
                    )
                } else {
                    frame
                }
            }
        } else {
            null
        }
    }

    private fun hasValidResolutionPair(
        authoritative: ScanFrameCandidate,
        detailed: ScanFrameCandidate
    ): Boolean = authoritative.recognitionWidth in 1..BASELINE_OCR_MAX_WIDTH &&
        detailed.recognitionWidth > authoritative.recognitionWidth &&
        detailed.recognitionWidth > BASELINE_OCR_MAX_WIDTH

    private fun hasIndependentNativeProfile(detailed: ScanFrameCandidate): Boolean {
        val evidence = detailed.speciesEvidence
        val isIndependentCompatible = evidence.authority == SpeciesAuthority.INDEPENDENT_PROFILE &&
            evidence.profileStatus == SpeciesProfileStatus.COMPATIBLE &&
            evidence.observationsAgree && !evidence.authorityConflict && !evidence.candidatesClose
        return isIndependentCompatible &&
            SpeciesEvidenceReason.INDEPENDENT_PROFILE in evidence.reasonCodes
    }

    private fun hasContradictoryFastProfile(authoritative: ScanFrameCandidate): Boolean {
        val evidence = authoritative.speciesEvidence
        val isUnconflicted = !evidence.authorityConflict && evidence.authority != SpeciesAuthority.CONFLICT
        return isUnconflicted && evidence.profileStatus == SpeciesProfileStatus.CONTRADICTORY
    }

    private fun fastCandidateAgrees(
        candidate: ScanFrameCandidate,
        authoritative: ScanFrameCandidate,
        detailed: ScanFrameCandidate,
        species: String
    ): Boolean {
        val hasNoAuthorityOrProfileBlock = !candidate.speciesEvidence.authorityConflict &&
            candidate.speciesEvidence.authority != SpeciesAuthority.CONFLICT &&
            candidate.speciesEvidence.profileStatus != SpeciesProfileStatus.IMPOSSIBLE &&
            !candidate.speciesEvidence.candidatesClose
        val hasNoDifferentHardSpecies = !candidate.speciesEvidence.hasHardAuthority ||
            candidate.speciesEvidence.selectedCanonicalSpecies.equals(species, ignoreCase = true)
        return candidate.recognitionWidth <= authoritative.recognitionWidth &&
            hasNoAuthorityOrProfileBlock && hasNoDifferentHardSpecies &&
            sameStableNonCpIdentity(candidate, detailed, species)
    }

    private fun hasStableActionCosts(candidates: List<ScanFrameCandidate>): Boolean {
        val observations = candidates.mapNotNull { it.data.recognitionObservation }
        val powerUpReadings = observations.mapNotNull { it.powerUpStardust }.distinct()
        val hasTypedPowerUpConflict = observations.any {
            it.powerUpStardustLevelEvidence is StardustLevelEvidence.Conflict
        }
        val evolveReads = observations.map { it.evolutionCandyCostRead }
        val readableEvolveCosts = evolveReads.filter { it.status == FieldReadStatus.READ }
            .mapNotNull { it.value }.distinct()
        return powerUpReadings.size <= 1 && !hasTypedPowerUpConflict &&
            evolveReads.none { it.status == FieldReadStatus.CONFLICT } && readableEvolveCosts.size <= 1
    }

    private fun sameStableNonCpIdentity(
        candidate: ScanFrameCandidate,
        detailed: ScanFrameCandidate,
        species: String
    ): Boolean {
        val candidateObservation = candidate.data.recognitionObservation
        val detailedObservation = detailed.data.recognitionObservation
        if (candidateObservation == null || detailedObservation == null) return false

        val screenEvidenceIsUsable = candidateObservation.detailScreen && detailedObservation.detailScreen &&
            !candidateObservation.identityBlockingNumericConflict &&
            !detailedObservation.identityBlockingNumericConflict
        val candyEvidenceMatches = candidateObservation.candySpecies.equals(
            detailedObservation.candySpecies,
            ignoreCase = true
        ) && !candidateObservation.candySpecies.isNullOrBlank()
        val candidateTypes = candidateObservation.types?.map { it.lowercase() }?.toSet()?.takeIf { it.isNotEmpty() }
        val detailedTypes = detailedObservation.types?.map { it.lowercase() }?.toSet()?.takeIf { it.isNotEmpty() }
        val typesMatch = candidateTypes != null && candidateTypes == detailedTypes
        val hpEvidenceMatches = candidate.data.hp != null && candidate.data.hp == detailed.data.hp &&
            candidate.data.maxHp != null && candidate.data.maxHp == detailed.data.maxHp
        val candidateSpeciesMatches = !candidate.speciesEvidence.hasHardAuthority ||
            candidate.speciesEvidence.selectedCanonicalSpecies.equals(species, ignoreCase = true)
        val visibleIdentityIsStable = screenEvidenceIsUsable && candyEvidenceMatches && typesMatch
        val numericAndSpeciesEvidenceIsStable = hpEvidenceMatches && candidateSpeciesMatches
        return visibleIdentityIsStable && numericAndSpeciesEvidenceIsStable
    }
}
