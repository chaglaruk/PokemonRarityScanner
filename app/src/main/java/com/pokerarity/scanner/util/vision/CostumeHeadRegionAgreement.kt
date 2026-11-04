package com.pokerarity.scanner.util.vision

/**
 * Conservatively recovers sparse costume matches when the whole-sprite score prefers a
 * normal reference but the accessory-region hash and species prototype select the same
 * costume identity.
 */
internal object CostumeHeadRegionAgreement {
    const val RESCUE_CONFIDENCE = 0.24f

    private const val MAX_COSTUME_SCORE = 0.37f
    private const val MIN_HEAD_PHASH_ADVANTAGE = 0.08f

    data class Decision(
        val accepted: Boolean,
        val reasonCode: String,
        val headPHashAdvantage: Float? = null
    )

    fun evaluate(
        details: CostumeSignatureStore.MatchDetails?,
        requestedSpecies: String?,
        classifier: VariantPrototypeClassifier.MatchResult?
    ): Decision = details?.let { availableDetails ->
        evaluateAvailableDetails(availableDetails, requestedSpecies, classifier)
    } ?: Decision(false, "costume_signature_details_missing")

    private fun evaluateAvailableDetails(
        details: CostumeSignatureStore.MatchDetails,
        requestedSpecies: String?,
        classifier: VariantPrototypeClassifier.MatchResult?
    ): Decision {
        val headAdvantage = details.headPHashAdvantage
        val rejection = signatureRejection(details)
            ?: headRejection(headAdvantage)
            ?: prototypeRejection(details, requestedSpecies, classifier, headAdvantage)
        return rejection ?: Decision(
            accepted = true,
            reasonCode = "head_region_signature_prototype_agreement",
            headPHashAdvantage = headAdvantage
        )
    }

    private fun signatureRejection(details: CostumeSignatureStore.MatchDetails): Decision? = when {
        details.matched -> Decision(false, "whole_sprite_signature_already_matched")
        details.denseVariantSpecies -> Decision(false, "dense_species_not_eligible")
        details.bestCostumeKey.isNullOrBlank() -> Decision(false, "costume_reference_missing")
        details.bestNormalKey.isNullOrBlank() -> Decision(false, "normal_reference_missing")
        !details.bestCostume.isFinite() || details.bestCostume > MAX_COSTUME_SCORE ->
            Decision(false, "costume_score_above_head_rescue_bound")
        !details.scoreGap.isFinite() || details.scoreGap >= 0f ->
            Decision(false, "whole_sprite_score_does_not_prefer_normal")
        else -> null
    }

    private fun headRejection(headAdvantage: Float?): Decision? = when {
        headAdvantage == null || !headAdvantage.isFinite() ->
            Decision(false, "head_reference_hash_unavailable")
        headAdvantage < MIN_HEAD_PHASH_ADVANTAGE ->
            Decision(false, "head_reference_advantage_below_bound", headAdvantage)
        else -> null
    }

    private fun prototypeRejection(
        details: CostumeSignatureStore.MatchDetails,
        requestedSpecies: String?,
        classifier: VariantPrototypeClassifier.MatchResult?,
        headAdvantage: Float?
    ): Decision? {
        val species = requestedSpecies?.trim()?.takeIf { it.isNotEmpty() }
        val match = classifier
        val reasonCode = when {
            species == null -> "requested_species_missing"
            match == null -> "species_prototype_missing"
            !match.species.equals(species, ignoreCase = true) || match.scope != "species" ->
                "species_prototype_scope_mismatch"
            !match.isCostumeLike || match.variantType != "costume" -> "species_prototype_not_costume"
            !match.spriteKey.equals(details.bestCostumeKey, ignoreCase = true) ->
                "species_prototype_costume_key_mismatch"
            else -> null
        }
        return reasonCode?.let { Decision(false, it, headAdvantage) }
    }
}
