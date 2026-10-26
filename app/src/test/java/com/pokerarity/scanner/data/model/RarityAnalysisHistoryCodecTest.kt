package com.pokerarity.scanner.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RarityAnalysisHistoryCodecTest {
    @Test
    fun roundTripsUserFacingReasonsAndOrderedBreakdown() {
        val explanations = listOf(
            encodeExplanationItem("Older catch", "2019-07-12"),
            encodeExplanationItem("Species rarity", "Raichu")
        )
        val breakdown = linkedMapOf("older_catch" to 14, "species" to 40)

        val snapshot = RarityAnalysisHistoryCodec.decode(
            RarityAnalysisHistoryCodec.encode(explanations, breakdown)
        )

        assertEquals(explanations, snapshot?.explanations)
        assertEquals(breakdown, snapshot?.breakdown)
        assertEquals(breakdown.keys.toList(), snapshot?.breakdown?.keys?.toList())
    }

    @Test
    fun emptyMalformedAndOversizedSnapshotsAreIgnored() {
        assertNull(RarityAnalysisHistoryCodec.encode(emptyList(), emptyMap()))
        assertNull(RarityAnalysisHistoryCodec.decode("not-json"))
        assertNull(RarityAnalysisHistoryCodec.decode(" "))
        assertNull(RarityAnalysisHistoryCodec.decode("x".repeat(8_193)))
    }

    @Test
    fun encodingBoundsPersistedItemCountsAndTextLength() {
        val explanations = (0 until 20).map { "Reason $it: ${"x".repeat(300)}" }
        val breakdown = (0 until 20).associate { "axis_$it" to it }

        val snapshot = RarityAnalysisHistoryCodec.decode(
            RarityAnalysisHistoryCodec.encode(explanations, breakdown)
        )!!

        assertEquals(16, snapshot.explanations.size)
        assertEquals(16, snapshot.breakdown.size)
        assertTrue(snapshot.explanations.all { it.length <= 240 })
        assertTrue(snapshot.breakdown.keys.all { it.length <= 80 })
    }
}
