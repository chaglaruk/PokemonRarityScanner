package com.pokerarity.scanner.service

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.FieldRead
import com.pokerarity.scanner.util.ocr.FieldReadStatus
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.DateParseUtils.toLocalDate
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence
import java.time.LocalDate
import java.util.Date

internal data class ScanFrameCandidate(
    val path: String,
    val data: PokemonData,
    val cpQuality: Double,
    val speciesEvidence: SpeciesEvidence = SpeciesEvidence.failClosed(),
    /** Same-source fast-frame recognition context, reused by the detailed pass. */
    val recognitionContext: RecognitionContext? = null,
    /** Original same-frame OCR candidates allow identity to be re-evaluated after fusion. */
    val fieldCandidates: List<FieldCandidateDiagnostic> = emptyList(),
    /** Width of the bitmap that produced this OCR candidate, not the source file width. */
    val recognitionWidth: Int = 0
)

internal data class AnchoredFrameSelection(
    val frame: ScanFrameCandidate,
    val speciesEvidence: SpeciesEvidence
)

private data class CaughtDateEvidence(val date: Date?, val localDate: LocalDate?)

internal object ScanFrameFusion {
    const val CP_QUALITY_MIN = 0.55
    private const val NATIVE_PROFILE_RECOVERY_REASON = "native_resolution_recovered_profile"
    private const val BASELINE_OCR_MAX_WIDTH = 900
    private const val NATIVE_DETAIL_RETRY_MAX_PIXELS = 4_500_000L

    /**
     * A selected title can remain unresolved because a downscaled numeric read
     * contradicts its profile. A native-resolution retry is only another observation;
     * it does not itself grant species authority. Explicit authority conflicts and
     * impossible profiles remain fail-closed.
     */
    fun shouldAttemptNativeResolutionForUnresolvedIdentity(speciesEvidence: SpeciesEvidence): Boolean {
        val explicitConflict = speciesEvidence.authorityConflict ||
            speciesEvidence.authority == SpeciesAuthority.CONFLICT

        val fullyResolved = speciesEvidence.hasHardAuthority &&
            speciesEvidence.profileStatus == SpeciesProfileStatus.COMPATIBLE &&
            speciesEvidence.observationsAgree &&
            !speciesEvidence.candidatesClose &&
            !speciesEvidence.selectedCanonicalSpecies.isNullOrBlank()
        return !explicitConflict &&
            speciesEvidence.profileStatus != SpeciesProfileStatus.IMPOSSIBLE &&
            !fullyResolved
    }

    /** Keep the exceptional native-size OCR pass within a predictable bitmap budget. */
    fun canUseNativeResolution(width: Int, height: Int): Boolean =
        width > BASELINE_OCR_MAX_WIDTH && height > 0 &&
            width.toLong() * height <= NATIVE_DETAIL_RETRY_MAX_PIXELS

    /**
     * Preserve an observed screen as a whole, while checking every readable screen
     * for explicit contradictions. An unresolved nickname cannot hide a change in
     * the independently observed candy family or numeric profile.
     */
    fun resolveAnchoredFrames(
        frames: List<ScanFrameCandidate>,
        authoritative: ScanFrameCandidate,
        detailed: ScanFrameCandidate? = null,
        evidenceWithoutActionCosts: (ScanFrameCandidate, Boolean, Boolean) -> SpeciesEvidence
    ): AnchoredFrameSelection? =
        if (authoritative.data.recognitionObservation == null) {
            null
        } else {
            val sameSourceDetailed = detailed?.takeIf {
                it.path == authoritative.path && it.data.recognitionObservation != null
            }
            val allFrames = (frames + authoritative + listOfNotNull(sameSourceDetailed)).distinct()
            val nativeProfileRecoveryFrames = sameSourceDetailed?.let {
                prepareNativeProfileRecoveryFrames(frames + authoritative, authoritative, it)
            }
            val framesForConflictCheck = nativeProfileRecoveryFrames ?: allFrames
            val conflictAuthority = if (nativeProfileRecoveryFrames != null) {
                sameSourceDetailed ?: authoritative
            } else {
                authoritative
            }
            preserveIdentityButConflictActionCosts(
                anchoredConflict(framesForConflictCheck, conflictAuthority, evidenceWithoutActionCosts)
                    ?: if (nativeProfileRecoveryFrames != null) {
                        val recoveredEvidence = conflictAuthority.speciesEvidence.copy(
                            reasonCodes = (conflictAuthority.speciesEvidence.reasonCodes +
                                NATIVE_PROFILE_RECOVERY_REASON).distinct()
                        )
                        AnchoredFrameSelection(conflictAuthority, recoveredEvidence)
                    } else {
                        selectAnchoredEvidence(authoritative, sameSourceDetailed)
                    },
                allFrames
            )
        }

    /**
     * A contradictory fast profile may be a CP OCR error after downscaling. Re-read
     * the same capture at native resolution, then use that independent profile only
     * when its family/type/HP witnesses agree with every available fast frame. The
     * low-resolution CP/profile vote is removed from identity fusion, while any
     * conflicting species authority or non-CP witness still blocks acceptance.
     */
    private fun prepareNativeProfileRecoveryFrames(
        fastFrames: List<ScanFrameCandidate>,
        authoritative: ScanFrameCandidate,
        detailed: ScanFrameCandidate
    ): List<ScanFrameCandidate>? {
        if (!hasNativeProfileRecoveryEvidence(authoritative, detailed)) return null
        val species = detailed.speciesEvidence.selectedCanonicalSpecies?.takeIf { it.isNotBlank() } ?: return null
        if (authoritative.data.cp == detailed.data.cp ||
            !sameStableNonCpIdentity(authoritative, detailed, species) ||
            authoritative.data.recognitionObservation?.frameIndex !=
            detailed.data.recognitionObservation?.frameIndex
        ) return null
        val candidates = (fastFrames + authoritative).distinct()
        if (!fastBurstSupportsNativeProfile(candidates, authoritative, detailed, species)) return null

        return (candidates + detailed).distinct().map { frame ->
            if (frame.recognitionWidth <= authoritative.recognitionWidth) {
                frame.copy(
                    data = frame.data.copy(cp = null),
                    speciesEvidence = frame.speciesEvidence.withProfileStatus(SpeciesProfileStatus.INDETERMINATE)
                )
            } else {
                frame
            }
        }
    }

    private fun hasNativeProfileRecoveryEvidence(
        authoritative: ScanFrameCandidate,
        detailed: ScanFrameCandidate
    ): Boolean {
        if (authoritative.recognitionWidth <= 0 ||
            authoritative.recognitionWidth > BASELINE_OCR_MAX_WIDTH ||
            detailed.recognitionWidth <= authoritative.recognitionWidth ||
            detailed.recognitionWidth <= BASELINE_OCR_MAX_WIDTH
        ) return false

        val fastEvidence = authoritative.speciesEvidence
        val detailedEvidence = detailed.speciesEvidence
        return fastEvidence.profileStatus == SpeciesProfileStatus.CONTRADICTORY &&
            !fastEvidence.authorityConflict && fastEvidence.authority != SpeciesAuthority.CONFLICT &&
            detailedEvidence.authority == SpeciesAuthority.INDEPENDENT_PROFILE &&
            detailedEvidence.profileStatus == SpeciesProfileStatus.COMPATIBLE &&
            detailedEvidence.observationsAgree && !detailedEvidence.authorityConflict &&
            !detailedEvidence.candidatesClose &&
            SpeciesEvidenceReason.INDEPENDENT_PROFILE in detailedEvidence.reasonCodes
    }

    private fun fastBurstSupportsNativeProfile(
        candidates: List<ScanFrameCandidate>,
        authoritative: ScanFrameCandidate,
        detailed: ScanFrameCandidate,
        species: String
    ): Boolean {
        if (candidates.isEmpty() || candidates.any { frame ->
                frame.recognitionWidth > authoritative.recognitionWidth ||
                    !sameStableNonCpIdentity(frame, detailed, species) ||
                    frame.speciesEvidence.authorityConflict ||
                    frame.speciesEvidence.authority == SpeciesAuthority.CONFLICT ||
                    frame.speciesEvidence.profileStatus == SpeciesProfileStatus.IMPOSSIBLE ||
                    frame.speciesEvidence.candidatesClose ||
                    (frame.speciesEvidence.hasHardAuthority &&
                        !frame.speciesEvidence.selectedCanonicalSpecies.equals(species, ignoreCase = true))
            }
        ) return false

        val powerUpReadings = candidates.mapNotNull { it.data.recognitionObservation?.powerUpStardust }.distinct()
        if (powerUpReadings.size > 1 || candidates.any {
                it.data.recognitionObservation?.powerUpStardustLevelEvidence is StardustLevelEvidence.Conflict
            }
        ) return false
        val evolveReads = candidates.mapNotNull { it.data.recognitionObservation?.evolutionCandyCostRead }
        return evolveReads.none { it.status == FieldReadStatus.CONFLICT } &&
            evolveReads.filter { it.status == FieldReadStatus.READ }.mapNotNull { it.value }.distinct().size <= 1
    }

    private fun sameStableNonCpIdentity(
        candidate: ScanFrameCandidate,
        detailed: ScanFrameCandidate,
        species: String
    ): Boolean {
        val candidateObservation = candidate.data.recognitionObservation ?: return false
        val detailedObservation = detailed.data.recognitionObservation ?: return false
        return candidateObservation.detailScreen && detailedObservation.detailScreen &&
            !candidateObservation.identityBlockingNumericConflict &&
            !detailedObservation.identityBlockingNumericConflict &&
            candidateObservation.candySpecies.equals(detailedObservation.candySpecies, ignoreCase = true) &&
            !candidateObservation.candySpecies.isNullOrBlank() &&
            candidateObservation.types?.map { it.lowercase() }?.toSet()?.takeIf { it.isNotEmpty() } ==
                detailedObservation.types?.map { it.lowercase() }?.toSet()?.takeIf { it.isNotEmpty() } &&
            candidate.data.hp != null && candidate.data.hp == detailed.data.hp &&
            candidate.data.maxHp != null && candidate.data.maxHp == detailed.data.maxHp &&
            (!candidate.speciesEvidence.hasHardAuthority ||
                candidate.speciesEvidence.selectedCanonicalSpecies.equals(species, ignoreCase = true))
    }

    /** Conflicting action costs do not contradict identity or remain usable profile evidence. */
    private fun preserveIdentityButConflictActionCosts(
        selection: AnchoredFrameSelection,
        allFrames: List<ScanFrameCandidate>
    ): AnchoredFrameSelection {
        val observations = allFrames.mapNotNull { frame ->
            frame.data.recognitionObservation?.takeIf {
                it.detailScreen && !it.identityBlockingNumericConflict
            }
        }
        val distinctCosts = observations.mapNotNull { it.powerUpStardust }.distinct()
        val typedConflicts = observations.mapNotNull {
            it.powerUpStardustLevelEvidence as? StardustLevelEvidence.Conflict
        }
        val semanticEvolutionObservations = allFrames.mapNotNull { frame ->
            frame.data.recognitionObservation?.takeIf {
                it.detailScreen || (!it.candySpecies.isNullOrBlank() && !it.types.isNullOrEmpty())
            }
        }
        val evolveConflict = evolutionCostConflict(semanticEvolutionObservations)
        val hasPowerUpConflict = powerUpStardustConflict(observations)
        val conflictReasonCodes = if (distinctCosts.size > 1) {
            listOf("anchored_power_up_row", "multiple_frame_costs")
        } else {
            (listOf("anchored_power_up_row") + typedConflicts.flatMap { it.reasonCodes }).distinct()
        }
        val observation = selection.frame.data.recognitionObservation
        return if ((!hasPowerUpConflict && evolveConflict == null) || observation == null) {
            selection
        } else {
            val conflictedObservation = observation.copy(
                powerUpStardustLevelEvidence = if (hasPowerUpConflict) {
                    StardustLevelEvidence.Conflict(
                        candidateCount = maxOf(
                            2,
                            distinctCosts.size,
                            typedConflicts.maxOfOrNull { it.candidateCount } ?: 0
                        ),
                        reasonCodes = conflictReasonCodes
                    )
                } else {
                    observation.powerUpStardustLevelEvidence
                },
                evolutionCandyCost = if (evolveConflict != null) null else observation.evolutionCandyCost,
                evolutionCandyCostRead = evolveConflict ?: observation.evolutionCandyCostRead
            )
            selection.copy(
                frame = selection.frame.copy(
                    data = selection.frame.data.copy(recognitionObservation = conflictedObservation)
                )
            )
        }
    }

    /** A typed EVOLVE conflict, or different readable costs across semantic frames. */
    private fun evolutionCostConflict(observations: List<RecognitionObservation>): FieldRead<Int>? {
        val reads = observations.map { it.evolutionCandyCostRead }
        val distinctCosts = reads.filter { it.status == FieldReadStatus.READ }
            .mapNotNull { it.value }.distinct()
        val typedConflicts = reads.filter { it.status == FieldReadStatus.CONFLICT }
        if (distinctCosts.size < 2 && typedConflicts.isEmpty()) return null

        val reason = if (distinctCosts.size > 1) {
            "multiple_frame_costs"
        } else {
            typedConflicts.first().reasonCode
        }
        return FieldRead.conflict(
            reason,
            candidateCount = maxOf(
                2,
                distinctCosts.size,
                typedConflicts.maxOfOrNull { it.candidateCount } ?: 0
            )
        )
    }

    private fun powerUpStardustConflict(observations: List<RecognitionObservation>): Boolean =
        observations.mapNotNull { it.powerUpStardust }.distinct().size > 1 ||
            observations.any { it.powerUpStardustLevelEvidence is StardustLevelEvidence.Conflict }

    /** Malformed HP does not invalidate separately anchored candy/type labels. */
    private fun anchoredConflict(
        allFrames: List<ScanFrameCandidate>,
        authoritative: ScanFrameCandidate,
        evidenceWithoutActionCosts: (ScanFrameCandidate, Boolean, Boolean) -> SpeciesEvidence
    ): AnchoredFrameSelection? {
        val observed = allFrames.filter { frame ->
            frame.data.recognitionObservation?.let {
                it.detailScreen && !it.identityBlockingNumericConflict
            } == true
        }
        val semanticFrames = allFrames.filter { frame ->
            frame.data.recognitionObservation?.let {
                it.detailScreen || (!it.candySpecies.isNullOrBlank() && !it.types.isNullOrEmpty())
            } == true
        }
        val fieldConflict = anchoredFieldConflict(observed, semanticFrames)
        // A disputed EVOLVE cost is removed before final identity/profile evaluation.
        // Per-frame profile failures may have been caused only by one side of that
        // dispute, so they cannot veto the selected identity at this fusion stage.
        val evolutionConflict = evolutionCostConflict(semanticFrames.mapNotNull {
            it.data.recognitionObservation
        })
        val powerUpCostConflict = powerUpStardustConflict(observed.mapNotNull {
            it.data.recognitionObservation
        })
        val observedEvidence = observed.map { frame ->
            val evidence = if (evolutionConflict != null || powerUpCostConflict) {
                evidenceWithoutActionCosts(frame, evolutionConflict != null, powerUpCostConflict)
            } else {
                frame.speciesEvidence
            }
            frame to evidence
        }
        val negativeProfile = negativeAnchoredProfile(observedEvidence)
        val identityConflict = anchoredIdentityConflict(observedEvidence)
        val isolatedCpRecovery = if (!fieldConflict && !identityConflict) {
            recoverFromIsolatedCpProfileContradiction(observedEvidence)
        } else {
            null
        }
        return isolatedCpRecovery ?: if (!fieldConflict && !identityConflict && negativeProfile == null) {
            null
        } else {
            anchoredConflictSelection(authoritative, negativeProfile)
        }
    }

    /**
     * A single OCR-readable CP can be partially covered by the animated sprite and
     * contradict every profile, while another frame from the same screen has no CP
     * but independently supports one species through candy, type and HP evidence.
     * Keep that supported frame (and its missing CP) when all non-CP identity evidence
     * agrees. Never use an editable name as the independent support for this recovery.
     */
    private fun recoverFromIsolatedCpProfileContradiction(
        observedEvidence: List<Pair<ScanFrameCandidate, SpeciesEvidence>>
    ): AnchoredFrameSelection? = isolatedCpRecoveryCandidate(observedEvidence)?.let { (frame, evidence) ->
        AnchoredFrameSelection(frame, evidence)
    }

    private fun isolatedCpRecoveryCandidate(
        observedEvidence: List<Pair<ScanFrameCandidate, SpeciesEvidence>>
    ): Pair<ScanFrameCandidate, SpeciesEvidence>? {
        val supported = observedEvidence.filter { (_, evidence) -> evidence.supportsIsolatedCpRecovery() }
        val contradicted = observedEvidence.filter { (_, evidence) ->
            evidence.profileStatus == SpeciesProfileStatus.CONTRADICTORY
        }
        val species = supported.mapNotNull { it.second.selectedCanonicalSpecies }
            .distinctBy { it.lowercase() }
            .singleOrNull()
        val supportedWithoutCp = supported.singleOrNull { it.first.data.cp == null }
        val valid = observedEvidence.size >= 2 && supported.isNotEmpty() && contradicted.isNotEmpty() &&
            observedEvidence.all { (_, evidence) -> evidence.hasEligibleIsolatedCpProfileStatus() } &&
            species != null && supported.all { (_, evidence) ->
                evidence.selectedCanonicalSpecies.equals(species, ignoreCase = true)
            } && supportedWithoutCp != null && supported.all { it.first.data.cp == null } &&
            hasStableNonCpIdentity(observedEvidence.map { it.first }, species.orEmpty()) &&
            hasOnlyContradictedCpReadings(observedEvidence, contradicted.size)
        return supportedWithoutCp.takeIf { valid }
    }

    private fun SpeciesEvidence.supportsIsolatedCpRecovery(): Boolean =
        authority == SpeciesAuthority.INDEPENDENT_PROFILE &&
            profileStatus == SpeciesProfileStatus.COMPATIBLE &&
            observationsAgree && !authorityConflict && !candidatesClose &&
            !selectedCanonicalSpecies.isNullOrBlank()

    private fun SpeciesEvidence.hasEligibleIsolatedCpProfileStatus(): Boolean =
        !authorityConflict && profileStatus in setOf(
            SpeciesProfileStatus.COMPATIBLE,
            SpeciesProfileStatus.CONTRADICTORY
        )

    private fun hasStableNonCpIdentity(frames: List<ScanFrameCandidate>, species: String): Boolean {
        val observations = frames.mapNotNull { it.data.recognitionObservation }
        if (observations.size != frames.size || frames.any { frame ->
                val observation = frame.data.recognitionObservation!!
                !observation.detailScreen ||
                    !(frame.data.realName ?: frame.data.name).equals(species, ignoreCase = true)
            }
        ) return false

        return uniqueReadableValue(observations.map { it.candySpecies?.trim()?.lowercase() }) != null &&
            uniqueReadableValue(observations.map { it.types?.map(String::lowercase)?.toSet() }) != null &&
            uniqueReadableValue(frames.map { it.data.hp }) != null &&
            uniqueReadableValue(frames.map { it.data.maxHp }) != null &&
            observations.map { it.powerUpStardust }.distinct().size == 1 &&
            observations.map { it.evolutionCandyCostRead }.distinct().size == 1 &&
            observations.map { it.powerUpStardustLevelEvidence }.distinct().size == 1
    }

    private fun hasOnlyContradictedCpReadings(
        observedEvidence: List<Pair<ScanFrameCandidate, SpeciesEvidence>>,
        contradictedCount: Int
    ): Boolean {
        val cpReadings = observedEvidence.filter { it.first.data.cp != null }
        return contradictedCount > 0 && cpReadings.size == contradictedCount &&
            cpReadings.all { (_, evidence) ->
                evidence.profileStatus == SpeciesProfileStatus.CONTRADICTORY
            }
    }

    private fun <T> uniqueReadableValue(values: List<T?>): T? {
        val distinct = values.filterNotNull().distinct()
        return distinct.singleOrNull()?.takeIf { values.all { value -> value != null } }
    }

    private fun anchoredFieldConflict(
        observed: List<ScanFrameCandidate>,
        semanticFrames: List<ScanFrameCandidate>
    ): Boolean {
        fun <T> disagrees(values: List<T?>): Boolean = values.filterNotNull().distinct().size > 1
        return disagrees(semanticFrames.map { it.data.recognitionObservation?.candySpecies?.trim()?.lowercase() }) ||
            disagrees(semanticFrames.map { it.data.recognitionObservation?.types?.takeIf { types -> types.isNotEmpty() }
                ?.map { type -> type.lowercase() }?.toSet() }) ||
            disagrees(observed.map { it.data.cp }) ||
            disagrees(observed.map { it.data.maxHp })
    }

    private fun negativeAnchoredProfile(
        observedEvidence: List<Pair<ScanFrameCandidate, SpeciesEvidence>>
    ): SpeciesProfileStatus? = observedEvidence.firstNotNullOfOrNull { (_, evidence) ->
        evidence.profileStatus.takeIf {
            it == SpeciesProfileStatus.IMPOSSIBLE || it == SpeciesProfileStatus.CONTRADICTORY
        }
    }

    private fun anchoredIdentityConflict(
        observedEvidence: List<Pair<ScanFrameCandidate, SpeciesEvidence>>
    ): Boolean {
        val independentSpecies = observedEvidence.mapNotNull { (_, evidence) ->
            evidence.selectedCanonicalSpecies?.lowercase()?.takeIf {
                hasHardIdentityForConflictDetection(evidence)
            }
        }.distinct()
        val authorityConflict = observedEvidence.any { it.second.authorityConflict }
        return independentSpecies.size > 1 || authorityConflict
    }

    private fun anchoredConflictSelection(
        authoritative: ScanFrameCandidate,
        negativeProfile: SpeciesProfileStatus?
    ): AnchoredFrameSelection {
        val conflictProfile = if (negativeProfile == SpeciesProfileStatus.IMPOSSIBLE) {
            SpeciesProfileStatus.IMPOSSIBLE
        } else {
            SpeciesProfileStatus.CONTRADICTORY
        }
        val profileReason = if (conflictProfile == SpeciesProfileStatus.IMPOSSIBLE) {
            SpeciesEvidenceReason.PROFILE_IMPOSSIBLE
        } else {
            SpeciesEvidenceReason.PROFILE_CONTRADICTORY
        }
        return AnchoredFrameSelection(authoritative, SpeciesEvidence(
            selectedCanonicalSpecies = null,
            authority = SpeciesAuthority.CONFLICT,
            profileStatus = conflictProfile,
            reasonCodes = listOf(
                SpeciesEvidenceReason.AUTHORITY_CONFLICT,
                profileReason,
                if (negativeProfile != null) {
                    "anchored_frame_profile_conflict"
                } else {
                    "anchored_frame_observations_conflict"
                }
            ),
            observationsAgree = false,
            authorityConflict = true
        ))
    }

    private fun selectAnchoredEvidence(
        authoritative: ScanFrameCandidate,
        sameSourceDetailed: ScanFrameCandidate?
    ): AnchoredFrameSelection {
        val authoritativeEvidence = authoritative.speciesEvidence
        val detailed = sameSourceDetailed
            ?.takeIf { hasCompatibleAuthority(it.speciesEvidence) }
            ?.takeIf {
                !hasCompatibleAuthority(authoritativeEvidence) ||
                    hasStrictlyMoreObservedFields(it.data, authoritative.data)
            }
        return if (detailed != null) {
            AnchoredFrameSelection(detailed, detailed.speciesEvidence)
        } else {
            AnchoredFrameSelection(authoritative, authoritativeEvidence)
        }
    }

    private fun hasCompatibleAuthority(evidence: SpeciesEvidence): Boolean =
        evidence.hasHardAuthority && evidence.profileStatus == SpeciesProfileStatus.COMPATIBLE &&
            evidence.observationsAgree && !evidence.authorityConflict && !evidence.candidatesClose

    private fun hasHardIdentityForConflictDetection(evidence: SpeciesEvidence): Boolean =
        evidence.hasHardAuthority &&
            evidence.profileStatus in setOf(
                SpeciesProfileStatus.COMPATIBLE,
                SpeciesProfileStatus.INDETERMINATE
            ) &&
            evidence.observationsAgree &&
            !evidence.authorityConflict &&
            !evidence.candidatesClose &&
            !evidence.selectedCanonicalSpecies.isNullOrBlank()

    private fun hasStrictlyMoreObservedFields(candidate: PokemonData, baseline: PokemonData): Boolean {
        val candidateFields = observedIdentityFields(candidate)
        val baselineFields = observedIdentityFields(baseline)
        return candidateFields.containsAll(baselineFields) && candidateFields.size > baselineFields.size
    }

    private fun observedIdentityFields(pokemon: PokemonData): Set<String> = buildSet {
        if (pokemon.cp != null) add("cp")
        if (pokemon.maxHp != null) add("maximum_hp")
        if (!pokemon.recognitionObservation?.candySpecies.isNullOrBlank()) add("candy")
        if (!pokemon.recognitionObservation?.types.isNullOrEmpty()) add("types")
        if (pokemon.recognitionObservation?.powerUpStardust != null) add("power_up_cost")
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
            (pokemon.caughtDate == null && pokemon.caughtDateLocalDate == null) ||
            topTextConfidence < 0.86 ||
            cpQuality < CP_QUALITY_MIN
        return needsDetailed
    }

    fun shouldRunDetailedPass(
        pokemon: PokemonData,
        cpQuality: Double,
        speciesEvidence: SpeciesEvidence
    ): Boolean {
        val independentCompatible =
            pokemon.recognitionObservation != null &&
                speciesEvidence.authority == SpeciesAuthority.INDEPENDENT_PROFILE &&
                speciesEvidence.profileStatus == SpeciesProfileStatus.COMPATIBLE
        return when {
            independentCompatible -> false
            detailedPassReasons(speciesEvidence).isNotEmpty() -> true
            else -> pokemon.cp == null ||
                pokemon.cp <= 0 ||
                isUnknownSpecies(pokemon.name) ||
                (pokemon.hp == null && pokemon.maxHp == null) ||
                (pokemon.caughtDate == null && pokemon.caughtDateLocalDate == null) ||
                cpQuality < CP_QUALITY_MIN
        }
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
        val caughtDate = asCaughtDateEvidence(authoritative)
            ?: mostFrequent(frames.map { asCaughtDateEvidence(it.data) })
            ?: asCaughtDateEvidence(detailed)
        val arcLevel = medianArcLevel(frames)
        val consensusName = consensusSpeciesField(frames) { it.name }
        val consensusRealName = consensusSpeciesField(frames) { it.realName }
        val cp = selectLegacyCp(
            frames = frames,
            authoritative = authoritative,
            detailed = detailed,
            validCpList = validCpList,
            bestCpQuality = bestCpQuality
        )

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
            caughtDate = caughtDate?.date,
            caughtDateLocalDate = caughtDate?.localDate,
            rawOcrText = mergeRawOcrText(authoritative.rawOcrText, detailed.rawOcrText),
            powerUpCandyCost = powerUpCandyCost ?: detailed.powerUpCandyCost ?: authoritative.powerUpCandyCost,
            powerUpCandySource = powerUpCandySource ?: detailed.powerUpCandySource ?: authoritative.powerUpCandySource,
            powerUpStardustSource = powerUpStardustSource ?: detailed.powerUpStardustSource ?: authoritative.powerUpStardustSource
        )
    }

    private fun medianArcLevel(frames: List<ScanFrameCandidate>): Float? {
        val values = frames.mapNotNull { it.data.arcLevel }.sorted()
        return values.takeIf { it.isNotEmpty() }?.let { it[it.size / 2] }
    }

    private fun consensusSpeciesField(
        frames: List<ScanFrameCandidate>,
        selector: (PokemonData) -> String?
    ): String? = mostFrequent(
        frames.map { selector(it.data) }.map { it.takeUnless(::isUnknownSpecies) }
    )

    private fun selectLegacyCp(
        frames: List<ScanFrameCandidate>,
        authoritative: PokemonData,
        detailed: PokemonData,
        validCpList: List<Int>,
        bestCpQuality: Double
    ): Int? {
        val consensusCp = mostFrequent(
            frames
                .filter { it.cpQuality >= CP_QUALITY_MIN }
                .map { it.data.cp }
        )
        val keepAuthoritativeCp = authoritative.cp != null &&
            bestCpQuality >= CP_QUALITY_MIN &&
            validCpList.contains(authoritative.cp)
        return when {
            keepAuthoritativeCp -> authoritative.cp
            consensusCp != null -> consensusCp
            detailed.cp != null && validCpList.contains(detailed.cp) -> detailed.cp
            else -> authoritative.cp ?: detailed.cp
        }
    }

    private fun frameScore(frame: ScanFrameCandidate): Int {
        return scoreFor(frame.data) + (frame.cpQuality * 20.0).toInt()
    }

    private fun hasHighConfidenceShape(frame: ScanFrameCandidate): Boolean {
        val cpVal = frame.data.cp ?: 0
        val hasSupportSignal = frame.data.hp != null || frame.data.arcLevel != null ||
            frame.data.caughtDate != null || frame.data.caughtDateLocalDate != null
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
        if (data.caughtDate != null || data.caughtDateLocalDate != null) score += 10
        return score
    }

    private fun asCaughtDateEvidence(data: PokemonData): CaughtDateEvidence? {
        if (data.caughtDate == null && data.caughtDateLocalDate == null) return null
        return CaughtDateEvidence(
            date = data.caughtDate,
            localDate = data.caughtDateLocalDate ?: data.caughtDate?.toLocalDate()
        )
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
