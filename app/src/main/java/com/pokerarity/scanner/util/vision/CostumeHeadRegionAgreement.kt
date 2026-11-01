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
    ): Decision {
        if (details == null) return Decision(false, "costume_signature_details_missing")
        if (details.matched) return Decision(false, "whole_sprite_signature_already_matched")
        if (details.denseVariantSpecies) return Decision(false, "dense_species_not_eligible")
        if (details.bestCostumeKey.isNullOrBlank()) return Decision(false, "costume_reference_missing")
        if (details.bestNormalKey.isNullOrBlank()) return Decision(false, "normal_reference_missing")
        if (!details.bestCostume.isFinite() || details.bestCostume > MAX_COSTUME_SCORE) {
            return Decision(false, "costume_score_above_head_rescue_bound")
        }
        if (!details.scoreGap.isFinite() || details.scoreGap >= 0f) {
            return Decision(false, "whole_sprite_score_does_not_prefer_normal")
        }

        val headAdvantage = details.headPHashAdvantage
            ?.takeIf { it.isFinite() }
            ?: return Decision(false, "head_reference_hash_unavailable")
        if (headAdvantage < MIN_HEAD_PHASH_ADVANTAGE) {
            return Decision(false, "head_reference_advantage_below_bound", headAdvantage)
        }

        val species = requestedSpecies?.trim()?.takeIf { it.isNotEmpty() }
            ?: return Decision(false, "requested_species_missing", headAdvantage)
        if (classifier == null) return Decision(false, "species_prototype_missing", headAdvantage)
        if (!classifier.species.equals(species, ignoreCase = true) || classifier.scope != "species") {
            return Decision(false, "species_prototype_scope_mismatch", headAdvantage)
        }
        if (!classifier.isCostumeLike || classifier.variantType != "costume") {
            return Decision(false, "species_prototype_not_costume", headAdvantage)
        }
        if (!classifier.spriteKey.equals(details.bestCostumeKey, ignoreCase = true)) {
            return Decision(false, "species_prototype_costume_key_mismatch", headAdvantage)
        }

        return Decision(
            accepted = true,
            reasonCode = "head_region_signature_prototype_agreement",
            headPHashAdvantage = headAdvantage
        )
    }
}
