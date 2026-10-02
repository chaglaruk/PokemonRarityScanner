package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.data.model.PokemonData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1D characterization (plan 5.5): profile-evidence semantics and authority
 * composition at the gate. EVIDENCE UNAVAILABLE != EVIDENCE CONTRADICTS.
 *
 * Expected pre-fix failures: C and D (INDETERMINATE acts as a standalone veto).
 * All fail-closed cases must pass before and after.
 */
class ScanConfidenceGateProfileSemanticsTest {

    private val gate = ScanConfidenceGate()

    private fun strongPokemon(name: String = "Pikachu") = PokemonData(
        cp = 777, hp = 88, maxHp = 88, name = name, realName = name, candyName = null,
        megaEnergy = null, weight = null, height = null, stardust = null, caughtDate = null,
        speciesResolverTrace = SpeciesResolverTrace(
            canonicalCandidates = listOf(
                SpeciesCandidateDiagnostic(name, score = 0.92f, winner = true, reasons = listOf("exact_name_match"))
            ),
            winningSpecies = name, confidence = 0.92f, winnerReason = "exact_name_match",
            evidenceUsed = listOf("name_text")
        )
    )

    private fun evidence(
        authority: SpeciesAuthority,
        profileStatus: SpeciesProfileStatus,
        species: String = "Pikachu"
    ): SpeciesEvidence = SpeciesEvidence(
        selectedCanonicalSpecies = if (authority == SpeciesAuthority.NO_MATCH) null else species,
        authority = authority,
        profileStatus = profileStatus,
        reasonCodes = listOf(
            when (authority) {
                SpeciesAuthority.INDEPENDENT_PROFILE -> SpeciesEvidenceReason.INDEPENDENT_PROFILE
                SpeciesAuthority.EXACT_CANONICAL -> SpeciesEvidenceReason.EXACT
                SpeciesAuthority.REVIEWED_ALIAS -> SpeciesEvidenceReason.REVIEWED_ALIAS
                SpeciesAuthority.SAFE_FUZZY -> SpeciesEvidenceReason.SAFE_FUZZY
                SpeciesAuthority.UNCERTAIN -> SpeciesEvidenceReason.UNCERTAIN
                SpeciesAuthority.NO_MATCH -> SpeciesEvidenceReason.NO_MATCH
                SpeciesAuthority.CONFLICT -> SpeciesEvidenceReason.AUTHORITY_CONFLICT
            },
            when (profileStatus) {
                SpeciesProfileStatus.COMPATIBLE -> SpeciesEvidenceReason.PROFILE_COMPATIBLE
                SpeciesProfileStatus.MISSING -> SpeciesEvidenceReason.PROFILE_MISSING
                SpeciesProfileStatus.CONTRADICTORY -> SpeciesEvidenceReason.PROFILE_CONTRADICTORY
                SpeciesProfileStatus.IMPOSSIBLE -> SpeciesEvidenceReason.PROFILE_IMPOSSIBLE
                SpeciesProfileStatus.INDETERMINATE -> SpeciesEvidenceReason.PROFILE_INDETERMINATE
            }
        ),
        observationsAgree = authority != SpeciesAuthority.NO_MATCH,
        authorityConflict = false
    )

    private fun strongInput(evidence: SpeciesEvidence, consistencyReason: String? = "accepted"): ScanConfidenceInput {
        val pokemon = strongPokemon()
        val frame = FrameDiagnostic(
            frameIndex = 0, imageWidth = 1080, imageHeight = 2340,
            screenState = ScreenType.PokemonDetail.name, screenConfidence = 0.84f,
            crops = listOf(
                CropDiagnostic("CP", "t", 0, 0, 100, 40, "used",
                    CropProvenance.AnchorDerived.diagnosticName, 0.78f),
                CropDiagnostic("HP", "t", 0, 0, 100, 40, "used",
                    CropProvenance.AnchorDerived.diagnosticName, 0.78f),
                CropDiagnostic("Name", "t", 0, 0, 100, 40, "used",
                    CropProvenance.AnchorDerived.diagnosticName, 0.78f)
            ),
            fieldCandidates = listOf(
                FieldCandidateDiagnostic(
                    "CP", "t", "777", "777", "found",
                    candidateScore = 0.9f, winner = true, selectedValue = "777"),
                FieldCandidateDiagnostic(
                    "HP", "t", "88", "88", "found",
                    candidateScore = 0.9f, winner = true, selectedValue = "88"),
                FieldCandidateDiagnostic(
                    "Name", "t", "Pikachu", "Pikachu", "found",
                    candidateScore = 0.92f, winner = true, selectedValue = "Pikachu")
            ),
            selected = PokemonSummary.from(pokemon)
        )
        return ScanConfidenceInput(
            pokemon = pokemon,
            frames = listOf(frame),
            consistencyReason = consistencyReason,
            cpCropQuality = 0.82,
            visualSummary = VariantVisualSummary(
                isShiny = false, isShadow = false, isPurified = false, isLucky = false,
                hasCostume = false, hasSpecialForm = false, isXXS = false, isXXL = false,
                hasLocationCard = false, confidence = 0.70f, classifierSpecies = "Pikachu",
                classifierScope = "test", classifierConfidence = 0.70f,
                fullVariantSpecies = "Pikachu", fullVariantClass = null),
            speciesEvidence = evidence
        )
    }

    // A. INDEPENDENT_PROFILE + COMPATIBLE: existing accepted behavior unchanged.
    @Test
    fun caseA_independentProfileCompatible_accepts() {
        gate.evaluate(
            strongInput(evidence(
                SpeciesAuthority.INDEPENDENT_PROFILE, SpeciesProfileStatus.COMPATIBLE))
            )
        assertEquals(ScanDecisionType.ACCEPT, decision.decision)
        assertTrue(decision.mayShowOverlay)
    }

    // B. EXACT_CANONICAL + COMPATIBLE: existing accepted behavior unchanged.
    @Test
    fun caseB_exactCompatible_accepts() {
        gate.evaluate(
            strongInput(evidence(
                SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.COMPATIBLE))
            )
        assertEquals(ScanDecisionType.ACCEPT, decision.decision)
        assertTrue(decision.mayShowOverlay)
    }

    // C. EXACT_CANONICAL + INDETERMINATE: must enter ordinary scoring, not the
    // confidence-0 early exit; the profile state itself is no longer the veto.
    @Test
    fun caseC_exactIndeterminate_entersOrdinaryScoring() {
        gate.evaluate(
            strongInput(evidence(
                SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.INDETERMINATE))
            )
        assertTrue("confidence must not be zeroed by INDETERMINATE alone", decision.confidence > 0f)
        assertEquals(ScanDecisionType.ACCEPT, decision.decision)
        assertTrue(decision.developerReasons.contains(SpeciesEvidenceReason.PROFILE_INDETERMINATE))
        assertTrue(decision.evidenceMissing.contains("species_profile_fit"))
        assertFalse(decision.evidenceMissing.contains("hard_species_authority"))
    }

    // D. REVIEWED_ALIAS + INDETERMINATE: same semantics as C.
    @Test
    fun caseD_reviewedAliasIndeterminate_entersOrdinaryScoring() {
        gate.evaluate(
            strongInput(evidence(
                SpeciesAuthority.REVIEWED_ALIAS, SpeciesProfileStatus.INDETERMINATE))
            )
        assertTrue(decision.confidence > 0f)
        assertTrue(
            decision.decision == ScanDecisionType.ACCEPT || decision.decision == ScanDecisionType.ACCEPT_LOW_CONFIDENCE)
        assertTrue(decision.evidenceMissing.contains("species_profile_fit"))
    }

    // E. SAFE_FUZZY + INDETERMINATE: still fail-closed.
    @Test
    fun caseE_safeFuzzyIndeterminate_blocked() {
        gate.evaluate(
            strongInput(evidence(
                SpeciesAuthority.SAFE_FUZZY, SpeciesProfileStatus.INDETERMINATE))
            )
        assertEquals(0f, decision.confidence)
        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
        assertFalse(decision.mayShowOverlay)
    }

    // F. EXACT + MISSING: conservative/fail-closed in this slice.
    @Test
    fun caseF_exactMissing_blocked() {
        gate.evaluate(
            strongInput(evidence(
                SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.MISSING))
            )
        assertEquals(0f, decision.confidence)
        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
        assertTrue(decision.developerReasons.contains(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_PROFILE))
    }

    // G. EXACT + CONTRADICTORY: hard block.
    @Test
    fun caseG_exactContradictory_blocked() {
        gate.evaluate(
            strongInput(evidence(
                SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.CONTRADICTORY))
            )
        assertEquals(0f, decision.confidence)
        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
        assertTrue(decision.developerReasons.contains(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_PROFILE))
    }

    // H. EXACT + IMPOSSIBLE: hard block.
    @Test
    fun caseH_exactImpossible_blocked() {
        gate.evaluate(
            strongInput(evidence(
                SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.IMPOSSIBLE))
            )
        assertEquals(0f, decision.confidence)
        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
        assertTrue(decision.developerReasons.contains(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_PROFILE))
    }

    // P. candidates close: blocked.
    @Test
    fun caseP_candidatesClose_blocked() {
        val evidence = evidence(SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.COMPATIBLE)
            .copy(candidatesClose = true)
        val decision = gate.evaluate(strongInput(evidence))
        assertEquals(0f, decision.confidence)
        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
    }

    // Q. authority conflict: blocked.
    @Test
    fun caseQ_authorityConflict_blocked() {
        val evidence = evidence(SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.COMPATIBLE)
            .copy(authorityConflict = true)
        val decision = gate.evaluate(strongInput(evidence))
        assertEquals(0f, decision.confidence)
        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
    }

    // R. observations disagree: blocked.
    @Test
    fun caseR_observationsDisagree_blocked() {
        val evidence = evidence(SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.COMPATIBLE)
            .copy(observationsAgree = false)
        val decision = gate.evaluate(strongInput(evidence))
        assertEquals(0f, decision.confidence)
        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
    }

    // S. cross-family conflict: blocked.
    @Test
    fun caseS_crossFamilyConflict_blocked() {
        val decision = gate.evaluate(strongInput(
            evidence(SpeciesAuthority.EXACT_CANONICAL, SpeciesProfileStatus.COMPATIBLE),
            consistencyReason = SpeciesEvidenceReason.CROSS_FAMILY_CONFLICT))
        assertEquals(0f, decision.confidence)
        assertTrue(decision.decision != ScanDecisionType.ACCEPT)
    }

    // T. NO_MATCH: retry/fail-closed per existing policy.
    @Test
    fun caseT_noMatch_retries() {
        gate.evaluate(
            strongInput(evidence(
                SpeciesAuthority.NO_MATCH, SpeciesProfileStatus.INDETERMINATE))
            )
        assertEquals(ScanDecisionType.RETRY, decision.decision)
        assertEquals(0f, decision.confidence)
        assertFalse(decision.mayShowOverlay)
    }
}
