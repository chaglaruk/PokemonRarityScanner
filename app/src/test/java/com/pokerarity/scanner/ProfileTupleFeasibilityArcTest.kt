package com.pokerarity.scanner

import com.pokerarity.scanner.util.ocr.ArcLevelEvidence
import com.pokerarity.scanner.util.ocr.ProfileTupleFeasibility
import com.pokerarity.scanner.util.ocr.ProfileTupleFeasibility.ArcIntersection
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 3B/3C seam: trusted typed arc evidence may only NARROW existing legal witnesses.
 * Best Buddy disposition: the arc's underlying-vs-effective semantics are not
 * established, so a window keeps BOTH interpretations alive. Arc evidence can never
 * resurrect an impossible tuple, and Unknown/Unsupported leaves the tuple set unchanged.
 */
@Suppress("MagicNumber")
class ProfileTupleFeasibilityArcTest {

    private val domain = mapOf(
        29.0 to 0.8353000283241272,
        29.5 to 0.8378037559315699,
        30.0 to 0.8403000235557556,
        30.5 to 0.8428037290347484,
        31.0 to 0.845300018787384
    )
    private val stats = RecognitionSnapshot.ProfileStats(atk = 100, def = 100, sta = 55)

    private fun witnesses(cp: Int = 656, maxHp: Int = 50) =
        ProfileTupleFeasibility.legalWitnesses(stats, cp, maxHp, domain)

    private fun underlyingOf(result: ArcIntersection.Constrained) =
        result.witnesses.map { it.underlyingLevel }.distinct().sorted()

    // -- narrowing -----------------------------------------------------------------------

    @Test
    fun trustedArcRangeNarrowsTheTupleSet() {
        val all = witnesses()
        assertTrue(all.isNotEmpty())
        val narrowed = ProfileTupleFeasibility.constrainToArcLevels(
            all, ArcLevelEvidence.Range(29.0, 29.5, listOf("arc_mapping_applied")))
            as ArcIntersection.Constrained
        // The window keeps the normal witness (underlying 29.0) AND the buddy pair
        // (underlying 29.0, effective 30.0); the effective-31.0 row is dropped.
        assertEquals(listOf(29.0), underlyingOf(narrowed))
        assertTrue(narrowed.witnesses.size < all.size)
        assertTrue(narrowed.witnesses.any { it.bestBuddyOffset && it.effectiveLevel == 30.0 })
    }

    @Test
    fun arcWindowKeepingBothBestBuddyInterpretations() {
        // A window that only contains effective level 30.0 must KEEP the buddy witness
        // (underlying 29.0, effective 30.0) — the arc semantics are undecided.
        val narrowed = ProfileTupleFeasibility.constrainToArcLevels(
            witnesses(), ArcLevelEvidence.Range(30.0, 30.0, listOf("arc_mapping_applied")))
            as ArcIntersection.Constrained
        assertTrue(narrowed.witnesses.any { it.bestBuddyOffset && it.underlyingLevel == 29.0 })
        assertTrue(narrowed.witnesses.any { !it.bestBuddyOffset && it.underlyingLevel == 30.0 })
    }

    @Test
    fun arcAlternativesPreserveMultipleLegalTuples() {
        val narrowed = ProfileTupleFeasibility.constrainToArcLevels(
            witnesses(),
            ArcLevelEvidence.Alternatives(
                listOf(28.9..29.1, 30.4..30.6), listOf("arc_mapping_ambiguous")))
            as ArcIntersection.Constrained
        val underlying = underlyingOf(narrowed)
        assertTrue(29.0 in underlying || 30.5 in underlying)
        assertTrue(underlying.isNotEmpty())
        assertFalse(underlying.contains(31.0))
    }

    // -- states that never constrain ------------------------------------------------------

    @Test
    fun arcUnknownAndUnsupportedLeaveTheTupleSetUnchanged() {
        val all = witnesses()
        listOf(
            null,
            ArcLevelEvidence.Unknown(null, listOf("arc_level_mapping_unestablished")),
            ArcLevelEvidence.Unsupported(listOf("arc_geometry_untrusted"))
        ).forEach { arc ->
            val result = ProfileTupleFeasibility.constrainToArcLevels(all, arc)
            assertTrue("arc=$arc", result is ArcIntersection.NotConstraining)
        }
    }

    @Test
    fun arcConflictStaysFailClosed() {
        val result = ProfileTupleFeasibility.constrainToArcLevels(
            witnesses(),
            ArcLevelEvidence.Conflict(listOf("arc_observation_conflict")))
        assertTrue(result is ArcIntersection.Conflict)
        assertTrue((result as ArcIntersection.Conflict).reasonCodes.contains("arc_observation_conflict"))
    }

    // -- no resurrection -----------------------------------------------------------------

    @Test
    fun arcEvidenceCannotResurrectAnImpossibleTupleSet() {
        // No legal witnesses at all (CP impossible for every level): a friendly arc
        // window must not manufacture any.
        val impossible = ProfileTupleFeasibility.legalWitnesses(stats, cp = 99_999, maxHp = 50, cpMultipliers = domain)
        assertTrue(impossible.isEmpty())
        val result = ProfileTupleFeasibility.constrainToArcLevels(
            impossible, ArcLevelEvidence.Range(29.0, 31.0, listOf("arc_mapping_applied")))
            as ArcIntersection.Constrained
        assertTrue(result.witnesses.isEmpty())
    }

    @Test
    fun arcWindowDisjointFromEveryWitnessContradicts() {
        val result = ProfileTupleFeasibility.constrainToArcLevels(
            witnesses(), ArcLevelEvidence.Range(50.0, 51.0, listOf("arc_mapping_applied")))
            as ArcIntersection.Constrained
        assertTrue(result.witnesses.isEmpty())
    }
}
