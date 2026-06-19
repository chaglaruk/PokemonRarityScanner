package com.pokerarity.scanner.domain.collector

import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VariantIdentityKey
import com.pokerarity.scanner.data.model.VisualFeatures

class CollectorIntelligenceService(
    private val contextBuilder: CollectionContextBuilder
) {
    suspend fun evaluateScan(
        variantKey: VariantIdentityKey?,
        score: RarityScore?,
        features: VisualFeatures?,
        ivSolve: IvSolveDetails?,
        confidence: Float
    ): CollectorDecision {
        val context = contextBuilder.buildContext(variantKey, features)
        
        return CollectionDecisionEngine.decide(
            variantKey = variantKey,
            context = context,
            score = score,
            features = features,
            ivSolve = ivSolve,
            confidence = confidence
        )
    }
}
