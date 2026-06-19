package com.pokerarity.scanner.domain.collector

import com.pokerarity.scanner.data.local.db.CollectionEntryEntity
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VariantIdentityKey
import com.pokerarity.scanner.data.model.VisualFeatures
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

class CollectorIntelligenceServiceTest {

    class FakeCollectionLookup(
        var duplicateCount: Int = 0,
        var entries: List<CollectionEntryEntity> = emptyList()
    ) : CollectionContextLookup {
        override suspend fun countDuplicates(variantIdentityKey: String): Int {
            return duplicateCount
        }

        override suspend fun getEntriesByVariantKey(variantIdentityKey: String): List<CollectionEntryEntity> {
            return entries
        }
    }

    private fun createKey(bgType: String? = null) = VariantIdentityKey(
        dex = 1, formId = null, variantId = null,
        isShiny = false, isShadow = false, isPurified = false,
        isLucky = false, isCostume = false, backgroundType = bgType, backgroundLabel = null
    )

    private val commonScore = RarityScore(totalScore = 0, tier = RarityTier.COMMON, breakdown = emptyMap(), explanation = emptyList())

    @Test
    fun `context builder returns first-of-kind when duplicate count is 0`() = runBlocking {
        val fakeLookup = FakeCollectionLookup(duplicateCount = 0)
        val builder = CollectionContextBuilder(fakeLookup)
        
        val context = builder.buildContext(createKey(), null)
        assertTrue(context.isFirstOfKind)
        assertEquals(0, context.duplicateCountForVariantKey)
    }

    @Test
    fun `context builder returns duplicate when duplicate count greater than 0`() = runBlocking {
        val fakeLookup = FakeCollectionLookup(duplicateCount = 3)
        val builder = CollectionContextBuilder(fakeLookup)
        
        val context = builder.buildContext(createKey(), null)
        assertFalse(context.isFirstOfKind)
        assertEquals(3, context.duplicateCountForVariantKey)
    }

    @Test
    fun `service calls decision engine and returns KEEP for first-of-kind shiny`() = runBlocking {
        val fakeLookup = FakeCollectionLookup(duplicateCount = 0)
        val service = CollectorIntelligenceService(CollectionContextBuilder(fakeLookup))
        
        val decision = service.evaluateScan(
            variantKey = createKey().copy(isShiny = true),
            score = commonScore,
            features = VisualFeatures(isShiny = true),
            ivSolve = null,
            confidence = 0.9f
        )
        
        assertEquals(ScanAction.KEEP, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.FIRST_OF_KIND))
        assertTrue(decision.reasons.contains(RarityReason.SHINY))
    }

    @Test
    fun `service returns REVIEW for low confidence`() = runBlocking {
        val fakeLookup = FakeCollectionLookup(duplicateCount = 0)
        val service = CollectorIntelligenceService(CollectionContextBuilder(fakeLookup))
        
        val decision = service.evaluateScan(
            variantKey = createKey(),
            score = commonScore,
            features = null,
            ivSolve = null,
            confidence = 0.5f // Low confidence
        )
        
        assertEquals(ScanAction.REVIEW, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.LOW_CONFIDENCE_REVIEW))
    }

    @Test
    fun `service never returns TRANSFER_SAFE for XXL duplicate`() = runBlocking {
        val fakeLookup = FakeCollectionLookup(duplicateCount = 2)
        val service = CollectorIntelligenceService(CollectionContextBuilder(fakeLookup))
        
        val decision = service.evaluateScan(
            variantKey = createKey(),
            score = commonScore,
            features = VisualFeatures(isXXL = true),
            ivSolve = null,
            confidence = 0.9f
        )
        
        // From Phase 1B fix, XXL duplicate should be REVIEW
        assertEquals(ScanAction.REVIEW, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.XXL))
        assertTrue(decision.reasons.contains(RarityReason.DUPLICATE))
    }

    @Test
    fun `background types are carried through correctly`() = runBlocking {
        val fakeLookup = FakeCollectionLookup(duplicateCount = 0)
        val service = CollectorIntelligenceService(CollectionContextBuilder(fakeLookup))
        
        val decision = service.evaluateScan(
            variantKey = createKey(bgType = "LOCATION"),
            score = commonScore,
            features = null,
            ivSolve = null,
            confidence = 0.9f
        )
        
        assertTrue(decision.reasons.contains(RarityReason.LOCATION_BACKGROUND))
        assertEquals(ScanAction.KEEP, decision.action)
    }

    @Test
    fun `null or unknown variant key returns REVIEW`() = runBlocking {
        val fakeLookup = FakeCollectionLookup(duplicateCount = 0)
        val service = CollectorIntelligenceService(CollectionContextBuilder(fakeLookup))
        
        val decision = service.evaluateScan(
            variantKey = null,
            score = commonScore,
            features = null,
            ivSolve = null,
            confidence = 0.9f
        )
        
        assertEquals(ScanAction.REVIEW, decision.action)
        assertTrue(decision.reasons.contains(RarityReason.UNKNOWN_SPECIES_REVIEW))
    }
}
