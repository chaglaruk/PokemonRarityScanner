package com.pokerarity.scanner.domain.collector

import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VariantIdentityKey
import com.pokerarity.scanner.data.model.VisualFeatures

object CollectionDecisionEngine {

    fun decide(
        variantKey: VariantIdentityKey?,
        context: CollectionContext,
        score: RarityScore?,
        features: VisualFeatures?,
        ivSolve: IvSolveDetails?,
        confidence: Float
    ): CollectorDecision {
        val reasons = RarityReasonEngine.generateReasons(variantKey, context, score, features, ivSolve, confidence)

        val isLowConfidence = reasons.contains(RarityReason.LOW_CONFIDENCE_REVIEW) || reasons.contains(RarityReason.UNKNOWN_SPECIES_REVIEW)
        val isUltraRare = reasons.contains(RarityReason.LEGENDARY_OR_MYTHICAL)
        val isShinyOrCostume = reasons.contains(RarityReason.SHINY) || reasons.contains(RarityReason.COSTUME)
        val hasSpecialBg = reasons.contains(RarityReason.LOCATION_BACKGROUND) || reasons.contains(RarityReason.SPECIAL_BACKGROUND)
        val isPerfectOrNundo = reasons.contains(RarityReason.PERFECT_IV) || reasons.contains(RarityReason.NUNDO)
        val isNotableIv = reasons.contains(RarityReason.HIGH_IV)

        val isFirstOfKind = reasons.contains(RarityReason.FIRST_OF_KIND)
        val isDuplicate = reasons.contains(RarityReason.DUPLICATE)
        val isXxlOrXxs = reasons.contains(RarityReason.XXL) || reasons.contains(RarityReason.XXS)

        val isValuableCollectible = isUltraRare || isShinyOrCostume || hasSpecialBg

        val action = when {
            // Priority 1: Low confidence or unknown
            isLowConfidence -> ScanAction.REVIEW

            // Priority 2: Valuable collectibles (Shiny, Costume, Special BG, Legendary)
            isValuableCollectible -> {
                if (isDuplicate) ScanAction.TRADE else ScanAction.KEEP
            }

            // Priority 3: Perfect IVs / Nundos -> KEEP
            isPerfectOrNundo -> ScanAction.KEEP

            // Priority 4: First of kind / collection gap
            isFirstOfKind -> ScanAction.KEEP

            // Priority 5: Duplicates
            isDuplicate -> {
                if (isNotableIv) {
                    ScanAction.TRADE // Notable IV but duplicate -> Trade
                } else if (isXxlOrXxs) {
                    ScanAction.REVIEW // Duplicate XXL/XXS -> Review (never TRANSFER_SAFE)
                } else {
                    ScanAction.TRANSFER_SAFE // Duplicate common, no notable flags -> Transfer
                }
            }

            // Fallback
            else -> ScanAction.REVIEW
        }

        val summary = buildSummary(action)

        return CollectorDecision(
            action = action,
            reasons = reasons,
            isReviewRequired = action == ScanAction.REVIEW,
            shortSummary = summary
        )
    }

    private fun buildSummary(action: ScanAction): String {
        return when (action) {
            ScanAction.KEEP -> "Keep this Pokemon."
            ScanAction.TRADE -> "Good candidate for trading."
            ScanAction.TRANSFER_SAFE -> "Safe to transfer."
            ScanAction.REVIEW -> "Review manually before deciding."
        }
    }
}
