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

data class ScanConfidenceInput(
    val pokemon: PokemonData,
    val frames: List<FrameDiagnostic> = emptyList(),
    val consistencyReason: String? = null,
    val consistencyRequestedRetry: Boolean = false,
    val cpCropQuality: Double? = null,
    val visualSummary: VariantVisualSummary? = null
)

class ScanConfidenceGate {

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
                score += if (screenConfidence >= 0.70f) 0.22f else 0.12f
                developerReasons += "screen_${screenType}:${"%.2f".format(screenConfidence)}"
            }
            ScreenType.Encounter.name -> {
                evidenceUsed += "screen_state"
                score += if (screenConfidence >= 0.70f) 0.10f else 0.05f
                developerReasons += "screen_encounter_not_detail"
            }
            else -> {
                evidenceMissing += "screen_state"
                developerReasons += "screen_unknown:${"%.2f".format(screenConfidence)}"
            }
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
            developerReasons += "crop_confidence:${"%.2f".format(averageCropConfidence)}"
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
            developerReasons += "appraisal_ignored_for_screen:$screenType"
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
                developerReasons += "resolver_species_conflict:$resolvedSpecies!=$selectedSpecies"
            } else {
                score += when {
                    resolverConfidence >= 0.75f -> 0.18f
                    resolverConfidence >= 0.50f -> 0.12f
                    resolverConfidence > 0f -> 0.05f
                    else -> 0f
                }
                developerReasons += "resolver_confidence:${"%.2f".format(resolverConfidence)}"
            }
            val alternatives = resolverTrace.canonicalCandidates.sortedByDescending { it.score }
            if (alternatives.size >= 2 && alternatives[0].score - alternatives[1].score <= 0.08f) {
                conflict = true
                score -= 0.10f
                developerReasons += "resolver_candidates_close:${alternatives[0].species}:${alternatives[1].species}"
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
                developerReasons += "consistency_gate:$reason"
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
            developerReasons += "cp_crop_quality:${"%.2f".format(quality)}"
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
                developerReasons += "visual_species_conflict:$visualSpecies!=$selectedSpecies"
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
