package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence

/** Bounded same-capture recovery for profiles contradicted by downscaled CP OCR. */
internal object NativeResolutionProfileRecovery {
    const val RECOVERY_REASON = "native_resolution_recovered_profile"

    const val BASELINE_OCR_MAX_WIDTH = 900
    private const val NATIVE_DETAIL_RETRY_MAX_PIXELS = 4_500_000L

    fun shouldAttemptNativeResolutionForUnresolvedIdentity(speciesEvidence: SpeciesEvidence): Boolean {
        val explicitConflict = speciesEvidence.authorityConflict ||
            speciesEvidence.authority == SpeciesAuthority.CONFLICT
        val fullyResolved = speciesEvidence.hasHardAuthority &&
            speciesEvidence.profileStatus == SpeciesProfileStatus.COMPATIBLE &&
            speciesEvidence.observationsAgree && !speciesEvidence.candidatesClose &&
            !speciesEvidence.selectedCanonicalSpecies.isNullOrBlank()
        return !explicitConflict && speciesEvidence.profileStatus != SpeciesProfileStatus.IMPOSSIBLE &&
            !fullyResolved
    }

    fun canUseNativeResolution(width: Int, height: Int): Boolean =
        width > BASELINE_OCR_MAX_WIDTH && height > 0 &&
            width.toLong() * height <= NATIVE_DETAIL_RETRY_MAX_PIXELS

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
            val resolutionPairIsValid = authoritative.recognitionWidth in 1..BASELINE_OCR_MAX_WIDTH &&
                detailed.recognitionWidth > authoritative.recognitionWidth &&
                detailed.recognitionWidth > BASELINE_OCR_MAX_WIDTH
            val evidenceAllowsRecovery = resolutionPairIsValid && hasIndependentNativeProfile(detailed) &&
                hasContradictoryFastProfile(authoritative)
            val sameCaptureChangedCp = authoritative.data.cp != detailed.data.cp &&
                authoritative.data.recognitionObservation?.frameIndex ==
                detailed.data.recognitionObservation?.frameIndex
            val burstSupportsRecovery = candidates.isNotEmpty() &&
                fastCpEvidenceConsistentlyContradicts(candidates, authoritative) &&
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

    private fun fastCpEvidenceConsistentlyContradicts(
        candidates: List<ScanFrameCandidate>,
        authoritative: ScanFrameCandidate
    ): Boolean {
        val contradictoryCp = authoritative.data.cp ?: return false
        return candidates.all { candidate ->
            candidate.data.cp == null || (
                candidate.data.cp == contradictoryCp &&
                    candidate.speciesEvidence.profileStatus == SpeciesProfileStatus.CONTRADICTORY
                )
        }
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
        val observations = candidate.data.recognitionObservation?.let { fastObservation ->
            detailed.data.recognitionObservation?.let { nativeObservation -> fastObservation to nativeObservation }
        } ?: return false
        val stableLabels = observations.first.hasStableDetailLabels(observations.second)
        val stableHp = candidate.data.hasSameHpWitness(detailed.data)
        val speciesAgrees = candidate.speciesEvidence.hasNoDifferentHardSpecies(species)
        return stableLabels && stableHp && speciesAgrees
    }
}

private fun RecognitionObservation.hasStableDetailLabels(other: RecognitionObservation): Boolean {
    val screenEvidenceIsUsable = detailScreen && other.detailScreen &&
        !identityBlockingNumericConflict && !other.identityBlockingNumericConflict
    val candyEvidenceMatches = candySpecies.equals(other.candySpecies, ignoreCase = true) &&
        !candySpecies.isNullOrBlank()
    val candidateTypes = types?.map { it.lowercase() }?.toSet()?.takeIf { it.isNotEmpty() }
    val detailedTypes = other.types?.map { it.lowercase() }?.toSet()?.takeIf { it.isNotEmpty() }
    val typesMatch = candidateTypes != null && candidateTypes == detailedTypes
    return screenEvidenceIsUsable && candyEvidenceMatches && typesMatch
}

private fun PokemonData.hasSameHpWitness(other: PokemonData): Boolean =
    hp != null && hp == other.hp && maxHp != null && maxHp == other.maxHp

private fun SpeciesEvidence.hasNoDifferentHardSpecies(species: String): Boolean =
    !hasHardAuthority || selectedCanonicalSpecies.equals(species, ignoreCase = true)
