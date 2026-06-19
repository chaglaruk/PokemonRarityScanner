package com.pokerarity.scanner.domain.collector

import com.pokerarity.scanner.data.model.IvSolveDetails
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VariantIdentityKey
import com.pokerarity.scanner.data.model.VisualFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class CollectorDecisionEngineTest {

    private val commonScore = RarityScore(
        totalScore = 0,
        tier = RarityTier.COMMON,
        breakdown = emptyMap(),
        explanation = emptyList()
    )

    private val legendaryScore = RarityScore(
        totalScore = 80,
        tier = RarityTier.LEGENDARY,
        breakdown = emptyMap(),
        explanation = emptyList()
    )

    private fun createKey(bgType: String? = null) = VariantIdentityKey(
        dex = 1,
        formId = "BASE",
        variantId = "NONE",
        isShiny = false,
        isShadow = false,
        isPurified = false,
        isLucky = false,
        isCostume = false,
        backgroundType = bgType,
        backgroundLabel = null
    )

    @Test
    fun `first of kind shiny costume special background is KEEP with correct reasons`() {
        val key = createKey(bgType = "special")
        val context = CollectionContext(isFirstOfKind = true)
        val features = VisualFeatures(isShiny = true, hasCostume = true)
        
        val decision = CollectionDecisionEngine.decide(key, context, commonScore, features, null, 1.0f)
        
        assertEquals(ScanAction.KEEP, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.FIRST_OF_KIND))
        assertTrue(decision.reasons.contains(RarityReason.SHINY))
        assertTrue(decision.reasons.contains(RarityReason.COSTUME))
        assertTrue(decision.reasons.contains(RarityReason.SPECIAL_BACKGROUND))
    }

    @Test
    fun `duplicate shiny is TRADE`() {
        val key = createKey()
        val context = CollectionContext(duplicateCountForVariantKey = 1, isFirstOfKind = false)
        val features = VisualFeatures(isShiny = true)
        
        val decision = CollectionDecisionEngine.decide(key, context, commonScore, features, null, 1.0f)
        
        assertEquals(ScanAction.TRADE, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.DUPLICATE))
        assertTrue(decision.reasons.contains(RarityReason.SHINY))
    }

    @Test
    fun `duplicate common high confidence no notable flags is TRANSFER_SAFE`() {
        val key = createKey()
        val context = CollectionContext(duplicateCountForVariantKey = 5, isFirstOfKind = false)
        
        val decision = CollectionDecisionEngine.decide(key, context, commonScore, null, null, 1.0f)
        
        assertEquals(ScanAction.TRANSFER_SAFE, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.DUPLICATE))
        assertFalse(decision.reasons.contains(RarityReason.HIGH_IV))
    }

    @Test
    fun `low confidence is REVIEW regardless of duplication`() {
        val key = createKey()
        val context = CollectionContext(duplicateCountForVariantKey = 5, isFirstOfKind = false)
        
        val decision = CollectionDecisionEngine.decide(key, context, commonScore, null, null, 0.5f)
        
        assertEquals(ScanAction.REVIEW, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.LOW_CONFIDENCE_REVIEW))
    }

    @Test
    fun `perfect IV hundo is KEEP`() {
        val key = createKey()
        val context = CollectionContext(duplicateCountForVariantKey = 2) // it's a duplicate
        val iv = IvSolveDetails(ivExact = 100)
        
        val decision = CollectionDecisionEngine.decide(key, context, commonScore, null, iv, 1.0f)
        
        assertEquals(ScanAction.KEEP, decision.action) // Should be KEEP because it's perfect
        assertTrue(decision.reasons.contains(RarityReason.PERFECT_IV))
    }

    @Test
    fun `nundo is KEEP`() {
        val key = createKey()
        val context = CollectionContext(duplicateCountForVariantKey = 1)
        val iv = IvSolveDetails(ivExact = 0)
        
        val decision = CollectionDecisionEngine.decide(key, context, commonScore, null, iv, 1.0f)
        
        assertEquals(ScanAction.KEEP, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.NUNDO))
    }

    @Test
    fun `legendary is never TRANSFER_SAFE even if duplicate`() {
        val key = createKey()
        val context = CollectionContext(duplicateCountForVariantKey = 3)
        
        val decision = CollectionDecisionEngine.decide(key, context, legendaryScore, null, null, 1.0f)
        
        assertEquals(ScanAction.TRADE, decision.action) // Duplicate valuable collectible -> TRADE
        assertTrue(decision.reasons.contains(RarityReason.LEGENDARY_OR_MYTHICAL))
    }

    @Test
    fun `XXL creates reason but does not prevent transfer if otherwise common`() {
        val key = createKey()
        val context = CollectionContext(duplicateCountForVariantKey = 2)
        val features = VisualFeatures(isXXL = true)
        
        val decision = CollectionDecisionEngine.decide(key, context, commonScore, features, null, 1.0f)
        
        assertEquals(ScanAction.TRANSFER_SAFE, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.XXL))
        assertTrue(decision.reasons.contains(RarityReason.DUPLICATE))
    }

    @Test
    fun `location background produces distinct reason from special background`() {
        val keyLoc = createKey(bgType = "location")
        val contextLoc = CollectionContext(isFirstOfKind = true)
        val decisionLoc = CollectionDecisionEngine.decide(keyLoc, contextLoc, commonScore, null, null, 1.0f)
        
        assertTrue(decisionLoc.reasons.contains(RarityReason.LOCATION_BACKGROUND))
        assertFalse(decisionLoc.reasons.contains(RarityReason.SPECIAL_BACKGROUND))

        val keySpec = createKey(bgType = "safari")
        val decisionSpec = CollectionDecisionEngine.decide(keySpec, contextLoc, commonScore, null, null, 1.0f)
        
        assertTrue(decisionSpec.reasons.contains(RarityReason.SPECIAL_BACKGROUND))
        assertFalse(decisionSpec.reasons.contains(RarityReason.LOCATION_BACKGROUND))
    }
}
