package com.pokerarity.scanner.domain.collector

import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VariantIdentityKey
import com.pokerarity.scanner.data.model.VisualFeatures

object RarityReasonEngine {
    fun generateReasons(
        variantKey: VariantIdentityKey?,
        context: CollectionContext,
        score: RarityScore?,
        features: VisualFeatures?,
        ivSolve: IvSolveDetails?,
        confidence: Float
    ): List<RarityReason> {
        val reasons = mutableListOf<RarityReason>()

        // Uncertainty
        if (confidence < 0.8f) {
            reasons.add(RarityReason.LOW_CONFIDENCE_REVIEW)
        }
        if (variantKey == null) {
            reasons.add(RarityReason.UNKNOWN_SPECIES_REVIEW)
        }

        // Species/Tier
        if (score?.tier == RarityTier.LEGENDARY || score?.tier == RarityTier.MYTHICAL || score?.tier == RarityTier.GOD_TIER) {
            reasons.add(RarityReason.LEGENDARY_OR_MYTHICAL)
        }

        // Variants
        if (features?.isShiny == true) reasons.add(RarityReason.SHINY)
        if (features?.hasCostume == true) reasons.add(RarityReason.COSTUME)
        if (features?.isShadow == true) reasons.add(RarityReason.SHADOW)
        if (features?.isLucky == true) reasons.add(RarityReason.LUCKY)
        if (features?.isXXL == true) reasons.add(RarityReason.XXL)
        if (features?.isXXS == true) reasons.add(RarityReason.XXS)

        val bgType = variantKey?.backgroundType?.trim()
        if (!bgType.isNullOrBlank() && !bgType.equals("none", ignoreCase = true)) {
            if (bgType.equals("location", ignoreCase = true)) {
                reasons.add(RarityReason.LOCATION_BACKGROUND)
            } else {
                reasons.add(RarityReason.SPECIAL_BACKGROUND)
            }
        }

        // IV
        if (ivSolve != null) {
            if (ivSolve.ivExact == 100) {
                reasons.add(RarityReason.PERFECT_IV)
            } else if (ivSolve.ivExact == 0) {
                reasons.add(RarityReason.NUNDO)
            } else if ((ivSolve.ivMin ?: 0) >= 90 || (ivSolve.ivExact ?: 0) >= 90) {
                reasons.add(RarityReason.HIGH_IV)
            }
        }

        // Collection
        if (context.isFirstOfKind) {
            reasons.add(RarityReason.FIRST_OF_KIND)
        } else if (context.duplicateCountForVariantKey > 0) {
            reasons.add(RarityReason.DUPLICATE)
        }

        return reasons.distinct()
    }
}
