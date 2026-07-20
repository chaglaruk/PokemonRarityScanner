package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.PokemonData

enum class ScanDecisionType {
    ACCEPT,
    ACCEPT_LOW_CONFIDENCE,
    RETRY,
    UNCERTAIN,
    REJECT_NOT_POKEMON_SCREEN
}

enum class ScanDecisionSeverity {
    INFO,
    WARNING,
    ERROR
}

data class ScanDecision(
    val decision: ScanDecisionType,
    val confidence: Float,
    val severity: ScanDecisionSeverity,
    val userSafeReason: String,
    val developerReasons: List<String>,
    val evidenceUsed: List<String>,
    val evidenceMissing: List<String>,
    val recommendedNextAction: String,
    val retryEligible: Boolean,
    val mayShowOverlay: Boolean,
    val maySaveScan: Boolean,
    val collectionSafe: Boolean
)

internal enum class SpeciesAuthority {
    EXACT_CANONICAL,
    REVIEWED_ALIAS,
    SAFE_FUZZY,
    UNCERTAIN,
    NO_MATCH,
    CONFLICT
}

internal enum class SpeciesProfileStatus {
    COMPATIBLE,
    MISSING,
    CONTRADICTORY,
    IMPOSSIBLE,
    INDETERMINATE
}

internal object SpeciesEvidenceReason {
    const val EXACT = "species_exact_authority"
    const val REVIEWED_ALIAS = "species_reviewed_alias_authority"
    const val SAFE_FUZZY = "species_safe_fuzzy_soft_only"
    const val UNCERTAIN = "species_uncertain"
    const val NO_MATCH = "species_no_match"
    const val AUTHORITY_CONFLICT = "species_authority_conflict"
    const val CANDIDATES_CLOSE = "species_candidates_close"
    const val PROFILE_COMPATIBLE = "species_profile_compatible"
    const val PROFILE_MISSING = "species_profile_missing"
    const val PROFILE_CONTRADICTORY = "species_profile_contradictory"
    const val PROFILE_IMPOSSIBLE = "species_profile_impossible"
    const val PROFILE_INDETERMINATE = "species_profile_indeterminate"
    const val CROSS_FAMILY_CONFLICT = "species_cross_family_conflict"
    const val EARLY_EXIT_BLOCKED_AUTHORITY = "early_exit_blocked_species_authority"
    const val EARLY_EXIT_BLOCKED_MARGIN = "early_exit_blocked_candidate_margin"
    const val EARLY_EXIT_BLOCKED_PROFILE = "early_exit_blocked_profile"
    const val DETAILED_PASS_REQUESTED = "detailed_pass_requested_species_evidence"
}

internal data class SpeciesEvidence(
    val selectedCanonicalSpecies: String?,
    val authority: SpeciesAuthority,
    val profileStatus: SpeciesProfileStatus,
    val reasonCodes: List<String>,
    val observationsAgree: Boolean,
    val authorityConflict: Boolean,
    val topCandidateScore: Float? = null,
    val runnerUpScore: Float? = null,
    val candidatesClose: Boolean = false
) {
    val hasHardAuthority: Boolean
        get() = authority == SpeciesAuthority.EXACT_CANONICAL || authority == SpeciesAuthority.REVIEWED_ALIAS

    fun withProfileStatus(status: SpeciesProfileStatus): SpeciesEvidence = copy(
        profileStatus = status,
        reasonCodes = reasonCodes.filterNot { it in profileReasons } + profileReason(status)
    )

    companion object {
        fun failClosed(profileStatus: SpeciesProfileStatus = SpeciesProfileStatus.INDETERMINATE): SpeciesEvidence =
            SpeciesEvidence(
                selectedCanonicalSpecies = null,
                authority = SpeciesAuthority.NO_MATCH,
                profileStatus = profileStatus,
                reasonCodes = listOf(SpeciesEvidenceReason.NO_MATCH, profileReason(profileStatus)),
                observationsAgree = false,
                authorityConflict = false
            )

        fun fromFieldCandidates(
            candidates: List<FieldCandidateDiagnostic>,
            profileStatus: SpeciesProfileStatus = SpeciesProfileStatus.INDETERMINATE
        ): SpeciesEvidence {
            val names = candidates.filter { it.field in nameFields }
            val accepted = names.mapNotNull { candidate -> acceptedName(candidate) }
            val acceptedSpecies = accepted.map { it.first }.distinctBy { it.lowercase() }
            val conflict = acceptedSpecies.size > 1
            val authority = resolveFieldAuthority(accepted, names, conflict)
            val scores = aggregateCandidateScores(names)
            val top = scores.getOrNull(0)
            val runnerUp = scores.getOrNull(1)
            val close = top != null && runnerUp != null && top - runnerUp <= CANDIDATE_CLOSE_MARGIN
            val reasons = linkedSetOf(authorityReason(authority), profileReason(profileStatus)).apply {
                if (close) add(SpeciesEvidenceReason.CANDIDATES_CLOSE)
            }
            return SpeciesEvidence(
                selectedCanonicalSpecies = acceptedSpecies.singleOrNull(),
                authority = authority,
                profileStatus = profileStatus,
                reasonCodes = reasons.toList(),
                observationsAgree = acceptedSpecies.size == 1,
                authorityConflict = conflict,
                topCandidateScore = top,
                runnerUpScore = runnerUp,
                candidatesClose = close
            )
        }

        private fun acceptedName(candidate: FieldCandidateDiagnostic): Pair<String, SpeciesAuthority>? {
            val usable = candidate.winner && candidate.status.lowercase() in acceptedStatuses
            if (!usable) return null
            return run {
                val species = candidate.selectedValue ?: candidate.parsedValue ?: return@run null
                val authority = authorityFrom(candidate.reason) ?: return@run null
                species to authority
            }
        }

        private fun resolveFieldAuthority(
            accepted: List<Pair<String, SpeciesAuthority>>,
            names: List<FieldCandidateDiagnostic>,
            conflict: Boolean
        ): SpeciesAuthority = when {
            conflict -> SpeciesAuthority.CONFLICT
            accepted.any { it.second == SpeciesAuthority.EXACT_CANONICAL } -> SpeciesAuthority.EXACT_CANONICAL
            accepted.any { it.second == SpeciesAuthority.REVIEWED_ALIAS } -> SpeciesAuthority.REVIEWED_ALIAS
            accepted.any { it.second == SpeciesAuthority.SAFE_FUZZY } -> SpeciesAuthority.SAFE_FUZZY
            names.any { it.status.equals("uncertain", ignoreCase = true) } -> SpeciesAuthority.UNCERTAIN
            else -> SpeciesAuthority.NO_MATCH
        }

        private fun aggregateCandidateScores(names: List<FieldCandidateDiagnostic>): List<Float> =
            names
                .mapNotNull { candidate ->
                    val species = candidate.selectedValue ?: candidate.parsedValue ?: return@mapNotNull null
                    candidate.candidateScore?.let { species.lowercase() to it }
                }
                .groupBy({ it.first }, { it.second })
                .values
                .mapNotNull { it.maxOrNull() }
                .sortedDescending()

        private fun authorityFrom(reason: String?): SpeciesAuthority? {
            val tokens = reason.orEmpty().split(',', ':').map(String::trim).toSet()
            return when {
                "exact_canonical" in tokens -> SpeciesAuthority.EXACT_CANONICAL
                tokens.any { it in reviewedReasons } -> SpeciesAuthority.REVIEWED_ALIAS
                "unique_structured_distance_one" in tokens -> SpeciesAuthority.SAFE_FUZZY
                else -> null
            }
        }

        private fun authorityReason(authority: SpeciesAuthority): String = when (authority) {
            SpeciesAuthority.EXACT_CANONICAL -> SpeciesEvidenceReason.EXACT
            SpeciesAuthority.REVIEWED_ALIAS -> SpeciesEvidenceReason.REVIEWED_ALIAS
            SpeciesAuthority.SAFE_FUZZY -> SpeciesEvidenceReason.SAFE_FUZZY
            SpeciesAuthority.UNCERTAIN -> SpeciesEvidenceReason.UNCERTAIN
            SpeciesAuthority.NO_MATCH -> SpeciesEvidenceReason.NO_MATCH
            SpeciesAuthority.CONFLICT -> SpeciesEvidenceReason.AUTHORITY_CONFLICT
        }

        private fun profileReason(profile: SpeciesProfileStatus): String = when (profile) {
            SpeciesProfileStatus.COMPATIBLE -> SpeciesEvidenceReason.PROFILE_COMPATIBLE
            SpeciesProfileStatus.MISSING -> SpeciesEvidenceReason.PROFILE_MISSING
            SpeciesProfileStatus.CONTRADICTORY -> SpeciesEvidenceReason.PROFILE_CONTRADICTORY
            SpeciesProfileStatus.IMPOSSIBLE -> SpeciesEvidenceReason.PROFILE_IMPOSSIBLE
            SpeciesProfileStatus.INDETERMINATE -> SpeciesEvidenceReason.PROFILE_INDETERMINATE
        }

        private const val CANDIDATE_CLOSE_MARGIN = 0.08f
        private val nameFields = setOf("Name", "NameDynamic", "NameHC")
        private val acceptedStatuses = setOf("found", "accepted")
        private val profileReasons = setOf(
            SpeciesEvidenceReason.PROFILE_COMPATIBLE,
            SpeciesEvidenceReason.PROFILE_MISSING,
            SpeciesEvidenceReason.PROFILE_CONTRADICTORY,
            SpeciesEvidenceReason.PROFILE_IMPOSSIBLE,
            SpeciesEvidenceReason.PROFILE_INDETERMINATE
        )
        private val reviewedReasons = setOf(
            "reviewed_numeric_suffix",
            "reviewed_ui_suffix",
            "reviewed_normalization",
            "reviewed_alias"
        )
    }
}

internal data class ScanConfidenceInput(
    val pokemon: PokemonData,
    val frames: List<FrameDiagnostic> = emptyList(),
    val consistencyReason: String? = null,
    val consistencyRequestedRetry: Boolean = false,
    val cpCropQuality: Double? = null,
    val visualSummary: VariantVisualSummary? = null,
    val speciesEvidence: SpeciesEvidence = SpeciesEvidence.failClosed()
)

internal class ScanConfidenceGate {

    fun evaluate(input: ScanConfidenceInput): ScanDecision {
        val pokemon = input.pokemon
        val fieldCandidates = input.frames.flatMap { it.fieldCandidates }
        val bestFrame = input.frames.maxByOrNull { it.screenConfidence ?: 0f }
        val screenType = bestFrame?.screenState ?: "Unknown"
        val screenConfidence = bestFrame?.screenConfidence ?: 0f
        val developerReasons = linkedSetOf<String>()
        val evidenceUsed = linkedSetOf<String>()
        val evidenceMissing = linkedSetOf<String>()
        var score = 0f
        var conflict = false

        val hasName = hasUsefulName(pokemon) || foundField(fieldCandidates, "Name", "NameDynamic", "NameHC")
        val hasCp = pokemon.cp?.let { it in 10..5500 } == true || foundField(fieldCandidates, "CP")
        val hasHp = pokemon.hp?.let { it in 10..999 } == true ||
            pokemon.maxHp?.let { it in 10..999 } == true ||
            foundField(fieldCandidates, "HP")
        val hasRawText = fieldCandidates.any {
            it.field.equals("RawText", ignoreCase = true) && it.status.equals("found", ignoreCase = true)
        } || pokemon.rawOcrText.isNotBlank()
        val rawTextOnly = hasRawText && !hasName && !hasCp && !hasHp

        when (screenType) {
            ScreenType.StorageList.name -> {
                evidenceUsed += "screen_state"
                developerReasons += "screen_storage_list"
                return decision(
                    type = ScanDecisionType.REJECT_NOT_POKEMON_SCREEN,
                    confidence = 0.18f,
                    developerReasons = developerReasons,
                    evidenceUsed = evidenceUsed,
                    evidenceMissing = evidenceMissing + "pokemon_detail_screen",
                    userReason = "This does not look like a Pokemon detail scan."
                )
            }
            ScreenType.Transition.name -> {
                evidenceUsed += "screen_state"
                developerReasons += "screen_transition"
                return decision(
                    type = ScanDecisionType.RETRY,
                    confidence = 0.22f,
                    developerReasons = developerReasons,
                    evidenceUsed = evidenceUsed,
                    evidenceMissing = evidenceMissing + "stable_screen",
                    userReason = "The screen appears to be changing; retrying the scan."
                )
            }
            ScreenType.PokemonDetail.name,
            ScreenType.PokemonDetailScrolled.name,
            ScreenType.Appraisal.name -> {
                evidenceUsed += "screen_state"
                developerReasons += when (screenType) {
                    ScreenType.PokemonDetail.name -> "screen_pokemon_detail"
                    ScreenType.PokemonDetailScrolled.name -> "screen_pokemon_detail_scrolled"
                    else -> "screen_appraisal"
                }
            }
            ScreenType.Encounter.name -> {
                evidenceUsed += "screen_state"
                developerReasons += "screen_encounter_not_detail"
            }
            else -> {
                evidenceMissing += "screen_state"
                developerReasons += "screen_unknown"
            }
        }

        val speciesEvidence = input.speciesEvidence
        val selectedSpecies = selectedSpecies(pokemon)
        developerReasons += speciesEvidence.reasonCodes
        val authorityMismatch = !speciesEvidence.hasHardAuthority ||
            selectedSpecies.isNullOrBlank() ||
            speciesEvidence.selectedCanonicalSpecies.isNullOrBlank() ||
            !selectedSpecies.equals(speciesEvidence.selectedCanonicalSpecies, ignoreCase = true)
        val profileBlocked = speciesEvidence.profileStatus != SpeciesProfileStatus.COMPATIBLE
        val marginBlocked = speciesEvidence.candidatesClose
        val conflictBlocked = speciesEvidence.authorityConflict || !speciesEvidence.observationsAgree ||
            input.consistencyReason == SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT
        val blocked = authorityMismatch || profileBlocked || marginBlocked || conflictBlocked
        if (blocked) {
            val authorityBlocked = authorityMismatch || conflictBlocked
            if (authorityBlocked) developerReasons += SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY
            if (marginBlocked) developerReasons += SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_MARGIN
            if (profileBlocked) developerReasons += SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_PROFILE
            val type = if (speciesEvidence.authority == SpeciesAuthority.NO_MATCH) {
                ScanDecisionType.RETRY
            } else {
                ScanDecisionType.UNCERTAIN
            }
            return decision(
                type = type,
                confidence = 0f,
                developerReasons = developerReasons,
                evidenceUsed = evidenceUsed,
                evidenceMissing = evidenceMissing + "hard_species_authority",
                userReason = userReason(type)
            )
        }

        score += when (screenType) {
            ScreenType.PokemonDetail.name,
            ScreenType.PokemonDetailScrolled.name,
            ScreenType.Appraisal.name -> if (screenConfidence >= 0.70f) 0.22f else 0.12f
            ScreenType.Encounter.name -> if (screenConfidence >= 0.70f) 0.10f else 0.05f
            else -> 0f
        }

        val coreCrops = input.frames.flatMap { it.crops }
            .filter { it.field in setOf("CP", "HP", "Name", "NameDynamic") }
        val anchorCrops = coreCrops.count { it.provenance == CropProvenance.AnchorDerived.diagnosticName }
        val fallbackCrops = coreCrops.count { it.provenance == CropProvenance.LegacyFallback.diagnosticName }
        val availableCropConfidence = coreCrops.mapNotNull { it.confidence }.filter { it > 0f }
        if (anchorCrops > 0) {
            evidenceUsed += "anchor_geometry"
            score += (anchorCrops * 0.03f).coerceAtMost(0.12f)
        }
        if (fallbackCrops > 0) {
            evidenceUsed += "legacy_geometry_fallback"
            score -= 0.04f
            developerReasons += "geometry_legacy_fallback"
        }
        if (availableCropConfidence.isNotEmpty()) {
            val averageCropConfidence = availableCropConfidence.average().toFloat()
            evidenceUsed += "crop_confidence"
            score += when {
                averageCropConfidence >= 0.62f -> 0.08f
                averageCropConfidence >= 0.42f -> 0.03f
                else -> 0f
            }
            developerReasons += when {
                averageCropConfidence >= 0.62f -> "crop_confidence_high"
                averageCropConfidence >= 0.42f -> "crop_confidence_medium"
                else -> "crop_confidence_low"
            }
        } else {
            evidenceMissing += "crop_confidence"
        }

        score += fieldScore(fieldCandidates, "CP", hasCp, 0.16f, evidenceUsed, evidenceMissing)
        score += fieldScore(fieldCandidates, "HP", hasHp, 0.12f, evidenceUsed, evidenceMissing)
        score += fieldScore(fieldCandidates, "Name", hasName, 0.20f, evidenceUsed, evidenceMissing)
        score += fieldScore(fieldCandidates, "Candy", !pokemon.candyName.isNullOrBlank(), 0.04f, evidenceUsed, evidenceMissing)
        score += fieldScore(fieldCandidates, "Date", pokemon.caughtDate != null, 0.03f, evidenceUsed, evidenceMissing)
        score += fieldScore(fieldCandidates, "Stardust", pokemon.stardust != null, 0.04f, evidenceUsed, evidenceMissing)
        score += fieldScore(fieldCandidates, "SizeTag", foundField(fieldCandidates, "SizeTag"), 0.02f, evidenceUsed, evidenceMissing)
        score += fieldScore(fieldCandidates, "LuckyDetected", foundField(fieldCandidates, "LuckyDetected"), 0.02f, evidenceUsed, evidenceMissing)

        val hasAppraisal = pokemon.appraisalAttack != null || pokemon.appraisalDefense != null || pokemon.appraisalStamina != null
        if (hasAppraisal && screenType in appraisalCompatibleScreens) {
            evidenceUsed += "appraisal_fields"
            score += 0.08f
        } else if (hasAppraisal) {
            developerReasons += "appraisal_ignored_for_screen"
        } else {
            evidenceMissing += "appraisal_fields"
        }

        val resolverTrace = pokemon.speciesResolverTrace
        if (resolverTrace != null) {
            evidenceUsed += "species_resolver"
            val resolvedSpecies = resolverTrace.winningSpecies
            val selectedSpecies = selectedSpecies(pokemon)
            val resolverConfidence = resolverTrace.confidence.coerceIn(0f, 1f)
            if (!resolvedSpecies.isNullOrBlank() && !selectedSpecies.isNullOrBlank() &&
                !resolvedSpecies.equals(selectedSpecies, ignoreCase = true)
            ) {
                conflict = true
                score -= 0.18f
                developerReasons += SpeciesEvidenceReason.AUTHORITY_CONFLICT
            } else {
                score += when {
                    resolverConfidence >= 0.75f -> 0.18f
                    resolverConfidence >= 0.50f -> 0.12f
                    resolverConfidence > 0f -> 0.05f
                    else -> 0f
                }
                developerReasons += "resolver_support"
            }
            val alternatives = resolverTrace.canonicalCandidates.sortedByDescending { it.score }
            if (alternatives.size >= 2 && alternatives[0].score - alternatives[1].score <= 0.08f) {
                conflict = true
                score -= 0.10f
                developerReasons += SpeciesEvidenceReason.CANDIDATES_CLOSE
            }
        } else {
            evidenceMissing += "species_resolver"
        }

        input.consistencyReason?.let { reason ->
            if (reason == "accepted") {
                evidenceUsed += "consistency_gate"
                score += 0.06f
            } else {
                evidenceUsed += "consistency_gate"
                developerReasons += "consistency_gate_blocked"
                score += if (reason.startsWith("fallback") || reason.startsWith("corrected") || reason.startsWith("restored")) 0.02f else -0.12f
                if (reason.contains("conflict", ignoreCase = true) || reason.contains("unknown", ignoreCase = true)) {
                    conflict = true
                }
            }
        } ?: run {
            evidenceMissing += "consistency_gate"
        }
        if (input.consistencyRequestedRetry) {
            conflict = true
            developerReasons += "consistency_requested_retry"
        }

        val frameSpecies = input.frames.mapNotNull { selectedSpecies(it.selected) }.distinctBy { it.lowercase() }
        val frameCps = input.frames.mapNotNull { it.selected.cp }.distinct()
        if (input.frames.size >= 2) {
            evidenceUsed += "frame_fusion"
            when {
                frameSpecies.size <= 1 && frameCps.size <= 1 -> score += 0.05f
                frameSpecies.size > 1 || frameCps.size > 2 -> {
                    conflict = true
                    score -= 0.10f
                    developerReasons += "frame_candidate_conflict"
                }
            }
        } else {
            evidenceMissing += "multi_frame_agreement"
        }

        input.cpCropQuality?.let { quality ->
            evidenceUsed += "cp_crop_quality"
            score += when {
                quality >= 0.70 -> 0.06f
                quality >= 0.55 -> 0.04f
                quality < 0.40 -> -0.05f
                else -> 0f
            }
            developerReasons += when {
                quality >= 0.70 -> "cp_crop_quality_high"
                quality >= 0.55 -> "cp_crop_quality_adequate"
                else -> "cp_crop_quality_low"
            }
        } ?: run {
            evidenceMissing += "cp_crop_quality"
        }

        input.visualSummary?.let { visual ->
            evidenceUsed += "visual_support"
            if (visual.confidence >= 0.60f && screenType in detailScreens) {
                score += 0.03f
            }
            val selectedSpecies = selectedSpecies(pokemon)
            val visualSpecies = visual.classifierSpecies ?: visual.fullVariantSpecies
            if (!selectedSpecies.isNullOrBlank() && !visualSpecies.isNullOrBlank() &&
                !selectedSpecies.equals(visualSpecies, ignoreCase = true) &&
                (visual.classifierConfidence ?: visual.confidence) >= 0.68f
            ) {
                conflict = true
                score -= 0.12f
                developerReasons += "visual_species_conflict"
            }
        } ?: run {
            evidenceMissing += "visual_support"
        }

        if (rawTextOnly) {
            developerReasons += "raw_text_only"
            evidenceMissing += "structured_fields"
        }
        if (!hasName) evidenceMissing += "usable_name"
        if (!hasCp) evidenceMissing += "cp"
        if (!hasHp) evidenceMissing += "hp"

        val normalizedScore = score.coerceIn(0f, 1f)
        val detailScreen = screenType in detailScreens
        val strongCore = detailScreen && hasName && (hasCp || hasHp) && !rawTextOnly && !conflict
        val lowCore = detailScreen && hasName && !rawTextOnly && !conflict
        val type = when {
            input.consistencyRequestedRetry -> ScanDecisionType.RETRY
            rawTextOnly -> if (screenType == ScreenType.Unknown.name) ScanDecisionType.RETRY else ScanDecisionType.UNCERTAIN
            screenType == ScreenType.Unknown.name && (!hasName || normalizedScore < 0.50f) -> ScanDecisionType.RETRY
            conflict -> ScanDecisionType.UNCERTAIN
            normalizedScore >= 0.76f && strongCore -> ScanDecisionType.ACCEPT
            normalizedScore >= 0.56f && lowCore -> ScanDecisionType.ACCEPT_LOW_CONFIDENCE
            normalizedScore < 0.42f -> ScanDecisionType.RETRY
            else -> ScanDecisionType.UNCERTAIN
        }

        return decision(
            type = type,
            confidence = normalizedScore,
            developerReasons = developerReasons,
            evidenceUsed = evidenceUsed,
            evidenceMissing = evidenceMissing,
            userReason = userReason(type)
        )
    }

    private fun fieldScore(
        candidates: List<FieldCandidateDiagnostic>,
        field: String,
        present: Boolean,
        weight: Float,
        evidenceUsed: MutableSet<String>,
        evidenceMissing: MutableSet<String>
    ): Float {
        if (!present) {
            evidenceMissing += field.lowercase()
            return 0f
        }
        evidenceUsed += field.lowercase()
        val candidateScore = bestCandidateScore(candidates, field)
        return weight * candidateScore.coerceAtLeast(0.65f)
    }

    private fun decision(
        type: ScanDecisionType,
        confidence: Float,
        developerReasons: Iterable<String>,
        evidenceUsed: Iterable<String>,
        evidenceMissing: Iterable<String>,
        userReason: String
    ): ScanDecision {
        val retryEligible = type == ScanDecisionType.RETRY
        return ScanDecision(
            decision = type,
            confidence = confidence.coerceIn(0f, 1f),
            severity = when (type) {
                ScanDecisionType.ACCEPT -> ScanDecisionSeverity.INFO
                ScanDecisionType.ACCEPT_LOW_CONFIDENCE,
                ScanDecisionType.RETRY,
                ScanDecisionType.UNCERTAIN -> ScanDecisionSeverity.WARNING
                ScanDecisionType.REJECT_NOT_POKEMON_SCREEN -> ScanDecisionSeverity.ERROR
            },
            userSafeReason = userReason,
            developerReasons = developerReasons.distinct(),
            evidenceUsed = evidenceUsed.distinct(),
            evidenceMissing = evidenceMissing.distinct(),
            recommendedNextAction = when (type) {
                ScanDecisionType.ACCEPT -> "show_result"
                ScanDecisionType.ACCEPT_LOW_CONFIDENCE -> "show_result_with_review"
                ScanDecisionType.RETRY -> "retry_capture"
                ScanDecisionType.UNCERTAIN -> "ask_user_to_retry"
                ScanDecisionType.REJECT_NOT_POKEMON_SCREEN -> "ignore_screen"
            },
            retryEligible = retryEligible,
            mayShowOverlay = type == ScanDecisionType.ACCEPT || type == ScanDecisionType.ACCEPT_LOW_CONFIDENCE,
            maySaveScan = type == ScanDecisionType.ACCEPT || type == ScanDecisionType.ACCEPT_LOW_CONFIDENCE,
            collectionSafe = type == ScanDecisionType.ACCEPT
        )
    }

    private fun userReason(type: ScanDecisionType): String =
        when (type) {
            ScanDecisionType.ACCEPT -> "Pokemon detail scan accepted."
            ScanDecisionType.ACCEPT_LOW_CONFIDENCE -> "Pokemon scan accepted with limited supporting evidence."
            ScanDecisionType.RETRY -> "Scan evidence is weak; retrying."
            ScanDecisionType.UNCERTAIN -> "Scan evidence is uncertain; try again from a stable Pokemon detail screen."
            ScanDecisionType.REJECT_NOT_POKEMON_SCREEN -> "This does not look like a Pokemon detail scan."
        }

    private fun foundField(candidates: List<FieldCandidateDiagnostic>, vararg fields: String): Boolean {
        val targets = fields.map { it.lowercase() }.toSet()
        return candidates.any { candidate ->
            val field = candidate.field.lowercase()
            candidate.field.lowercase() in targets &&
                candidate.status.equals("found", ignoreCase = true) &&
                !isMarkerOrNonName(
                    candidate.selectedValue
                        ?: candidate.parsedValue
                        ?: candidate.normalizedText
                        ?: candidate.rawText,
                    numericAllowed = field in numericFields
                )
        }
    }

    private fun bestCandidateScore(candidates: List<FieldCandidateDiagnostic>, field: String): Float =
        candidates
            .filter { it.field.equals(field, ignoreCase = true) && it.status.equals("found", ignoreCase = true) }
            .maxOfOrNull { (it.candidateScore ?: if (it.winner) 0.85f else 0.65f).coerceIn(0f, 1f) }
            ?: 0.80f

    private fun hasUsefulName(pokemon: PokemonData): Boolean =
        !selectedSpecies(pokemon).isNullOrBlank()

    private fun selectedSpecies(pokemon: PokemonData): String? =
        selectedSpecies(
            PokemonSummary(
                cp = pokemon.cp,
                hp = pokemon.hp,
                maxHp = pokemon.maxHp,
                name = pokemon.name,
                realName = pokemon.realName,
                candyName = pokemon.candyName,
                stardust = pokemon.stardust,
                arcLevel = pokemon.arcLevel,
                caughtDateEpochMs = pokemon.caughtDate?.time,
                appraisalAttack = pokemon.appraisalAttack,
                appraisalDefense = pokemon.appraisalDefense,
                appraisalStamina = pokemon.appraisalStamina
            )
        )

    private fun selectedSpecies(summary: PokemonSummary): String? =
        listOf(summary.realName, summary.name)
            .firstOrNull { !isMarkerOrNonName(it) && !it.equals("Unknown", ignoreCase = true) }

    private fun isMarkerOrNonName(value: String?, numericAllowed: Boolean = false): Boolean {
        val compact = value?.trim()?.lowercase()?.replace(Regex("[^a-z0-9]"), "").orEmpty()
        if (compact.isBlank()) return true
        if (compact in markerValues) return true
        if (!numericAllowed && compact.all(Char::isDigit)) return true
        if ((compact.startsWith("cp") || compact.startsWith("hp")) && compact.any(Char::isDigit)) return true
        if (compact.contains("stardust") || compact.contains("candy") || compact.contains("powerup")) return true
        return false
    }

    private companion object {
        val detailScreens = setOf(
            ScreenType.PokemonDetail.name,
            ScreenType.PokemonDetailScrolled.name,
            ScreenType.Appraisal.name
        )
        val appraisalCompatibleScreens = detailScreens + ScreenType.Encounter.name
        val markerValues = setOf("missing", "notrun", "skipped", "rawtext", "present", "unknown")
        val numericFields = setOf(
            "cp",
            "hp",
            "stardust",
            "appraisalattack",
            "appraisaldefense",
            "appraisalstamina"
        )
    }
}
