package com.pokerarity.scanner

import com.pokerarity.scanner.service.Phase2AuthorityReason
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.resolvePhase2AuthorityGate
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanManagerPhase2AuthorityTest {

    @Test
    fun exactCanonical_matchingSpecies_noRetry_mayRunAndApply() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.EXACT_CANONICAL
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = false
        )
        assertTrue(gate.mayRunSpeciesScopedPhase2)
        assertTrue(gate.mayApplyPhase2)
        assertEquals("Pikachu", gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.EXACT_CANONICAL, gate.reason)
    }

    @Test
    fun reviewedAlias_matchingSpecies_noRetry_mayRunAndApply() {
        val evidence = evidence(
            selected = "Ho-Oh",
            authority = SpeciesAuthority.REVIEWED_ALIAS
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Ho-Oh",
            retryRequested = false
        )
        assertTrue(gate.mayRunSpeciesScopedPhase2)
        assertTrue(gate.mayApplyPhase2)
        assertEquals("Ho-Oh", gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.REVIEWED_ALIAS, gate.reason)
    }

    @Test
    fun safeFuzzy_matchingSpecies_observationsAgree_candidatesNotClose_noRetry_mayRunAndApply() {
        val evidence = evidence(
            selected = "Poliwrath",
            authority = SpeciesAuthority.SAFE_FUZZY,
            observationsAgree = true,
            candidatesClose = false
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Poliwrath",
            retryRequested = false
        )
        assertTrue(gate.mayRunSpeciesScopedPhase2)
        assertTrue(gate.mayApplyPhase2)
        assertEquals("Poliwrath", gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.SAFE_FUZZY, gate.reason)
    }

    @Test
    fun retryTrue_withOtherwiseExactAuthority_isDiagnosticsOnlyAndRetryWins() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.EXACT_CANONICAL
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = true
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.RETRY, gate.reason)
    }

    @Test
    fun authorityConflict_isDiagnosticsOnly() {
        val evidence = evidence(
            selected = null,
            authority = SpeciesAuthority.CONFLICT
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.CONFLICT, gate.reason)
    }

    @Test
    fun authorityConflictTrue_withOtherwiseExactAuthority_isDiagnosticsOnly() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.EXACT_CANONICAL,
            authorityConflict = true
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.CONFLICT, gate.reason)
    }

    @Test
    fun uncertain_isDiagnosticsOnly() {
        val evidence = evidence(
            selected = "Nidoran",
            authority = SpeciesAuthority.UNCERTAIN
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Nidoran",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.UNCERTAIN, gate.reason)
    }

    @Test
    fun noMatch_isDiagnosticsOnly() {
        val evidence = evidence(
            selected = null,
            authority = SpeciesAuthority.NO_MATCH
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.NO_MATCH, gate.reason)
    }

    @Test
    fun selectedCanonicalSpeciesNull_returnsMissingAuthority() {
        val evidence = evidence(
            selected = null,
            authority = SpeciesAuthority.EXACT_CANONICAL
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.MISSING_AUTHORITY, gate.reason)
    }

    @Test
    fun selectedCanonicalSpeciesBlank_returnsMissingAuthority() {
        val evidence = evidence(
            selected = "   ",
            authority = SpeciesAuthority.EXACT_CANONICAL
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.MISSING_AUTHORITY, gate.reason)
    }

    @Test
    fun candidateSpeciesNull_returnsMissingAuthority() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.EXACT_CANONICAL
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = null,
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.MISSING_AUTHORITY, gate.reason)
    }

    @Test
    fun candidateSpeciesUnknown_caseInsensitive_returnsMissingAuthority() {
        listOf("Unknown", "unknown", "UNKNOWN").forEach { unknownCandidate ->
            val evidence = evidence(
                selected = "Pikachu",
                authority = SpeciesAuthority.EXACT_CANONICAL
            )
            val gate = resolvePhase2AuthorityGate(
                speciesEvidence = evidence,
                candidateSpecies = unknownCandidate,
                retryRequested = false
            )
            assertFalse(gate.mayRunSpeciesScopedPhase2)
            assertFalse(gate.mayApplyPhase2)
            assertNull(gate.acceptedSpecies)
            assertEquals(Phase2AuthorityReason.MISSING_AUTHORITY, gate.reason)
        }
    }

    @Test
    fun nonblankCandidateSpecies_withFailClosedEvidence_doesNotBecomeAcceptedAuthority() {
        val evidence = SpeciesEvidence.failClosed(SpeciesProfileStatus.COMPATIBLE)
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Charizard",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.NO_MATCH, gate.reason)
    }

    @Test
    fun selectedEvidenceSpecies_andCandidateSpeciesDiffer_returnsSpeciesMismatch() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.EXACT_CANONICAL
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Raichu",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.SPECIES_MISMATCH, gate.reason)
    }

    @Test
    fun authorityConflictPlusRetry_retryPrecedenceIsDeterministic() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.CONFLICT,
            authorityConflict = true
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = true
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.RETRY, gate.reason)

        val underlyingGate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = false
        )
        assertEquals(Phase2AuthorityReason.CONFLICT, underlyingGate.reason)
    }

    @Test
    fun candidatesCloseTrue_withOtherwiseAcceptedAuthority_returnsUncertain() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.EXACT_CANONICAL,
            candidatesClose = true
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.UNCERTAIN, gate.reason)
    }

    @Test
    fun observationsAgreeFalse_withOtherwiseAcceptedAuthority_returnsUncertain() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.EXACT_CANONICAL,
            observationsAgree = false
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "Pikachu",
            retryRequested = false
        )
        assertFalse(gate.mayRunSpeciesScopedPhase2)
        assertFalse(gate.mayApplyPhase2)
        assertNull(gate.acceptedSpecies)
        assertEquals(Phase2AuthorityReason.UNCERTAIN, gate.reason)
    }

    @Test
    fun repeatedEvaluation_producesEqualPhase2AuthorityGateValues() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.EXACT_CANONICAL
        )
        val first = resolvePhase2AuthorityGate(evidence, "Pikachu", false)
        val second = ScanManager.resolvePhase2AuthorityGate(evidence, "Pikachu", false)
        assertEquals(first, second)
    }

    @Test
    fun acceptedSpecies_preservesCanonicalCasingFromSpeciesEvidence() {
        val evidence = evidence(
            selected = "Pikachu",
            authority = SpeciesAuthority.EXACT_CANONICAL
        )
        val gate = resolvePhase2AuthorityGate(
            speciesEvidence = evidence,
            candidateSpecies = "pikachu",
            retryRequested = false
        )
        assertTrue(gate.mayRunSpeciesScopedPhase2)
        assertTrue(gate.mayApplyPhase2)
        assertEquals("Pikachu", gate.acceptedSpecies)
    }

    @Test
    fun allBlockedCases_returnNullAcceptedSpecies() {
        val blockedCases = listOf(
            resolvePhase2AuthorityGate(evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL), "Pikachu", true),
            resolvePhase2AuthorityGate(evidence(null, SpeciesAuthority.CONFLICT), "Pikachu", false),
            resolvePhase2AuthorityGate(
                evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, authorityConflict = true),
                "Pikachu",
                false
            ),
            resolvePhase2AuthorityGate(evidence("Pikachu", SpeciesAuthority.UNCERTAIN), "Pikachu", false),
            resolvePhase2AuthorityGate(evidence(null, SpeciesAuthority.NO_MATCH), "Pikachu", false),
            resolvePhase2AuthorityGate(evidence(null, SpeciesAuthority.EXACT_CANONICAL), "Pikachu", false),
            resolvePhase2AuthorityGate(evidence("   ", SpeciesAuthority.EXACT_CANONICAL), "Pikachu", false),
            resolvePhase2AuthorityGate(evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL), null, false),
            resolvePhase2AuthorityGate(evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL), "Unknown", false),
            resolvePhase2AuthorityGate(SpeciesEvidence.failClosed(), "Charizard", false),
            resolvePhase2AuthorityGate(evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL), "Raichu", false),
            resolvePhase2AuthorityGate(
                evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, candidatesClose = true),
                "Pikachu",
                false
            ),
            resolvePhase2AuthorityGate(
                evidence("Pikachu", SpeciesAuthority.EXACT_CANONICAL, observationsAgree = false),
                "Pikachu",
                false
            )
        )

        blockedCases.forEach { gate ->
            assertFalse(gate.mayRunSpeciesScopedPhase2)
            assertFalse(gate.mayApplyPhase2)
            assertNull(gate.acceptedSpecies)
        }
    }

    private fun evidence(
        selected: String?,
        authority: SpeciesAuthority,
        observationsAgree: Boolean = true,
        authorityConflict: Boolean = false,
        candidatesClose: Boolean = false
    ): SpeciesEvidence = SpeciesEvidence(
        selectedCanonicalSpecies = selected,
        authority = authority,
        profileStatus = SpeciesProfileStatus.COMPATIBLE,
        reasonCodes = emptyList(),
        observationsAgree = observationsAgree,
        authorityConflict = authorityConflict,
        candidatesClose = candidatesClose
    )
}
