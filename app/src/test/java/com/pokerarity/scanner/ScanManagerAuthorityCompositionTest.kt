package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidenceReason
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File

/**
 * Phase 1D authority composition (plan 5.2/5.3): the structured family evaluator
 * (INDEPENDENT_PROFILE) and exact/reviewed textual authority must compose without
 * discarding either source. EVIDENCE UNAVAILABLE != EVIDENCE CONTRADICTS.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScanManagerAuthorityCompositionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = RarityCalculator(context).also { calculator ->
        // This project does not package Android assets in JVM tests. Load the real
        // production recognition profiles explicitly, as the feasibility suite does.
        val assetDir = listOf(File("src/main/assets/data"), File("app/src/main/assets/data"))
            .first { it.isDirectory }
        val profiles = File(assetDir, "recognition_profiles.json").reader()
            .use(com.pokerarity.scanner.util.ocr.RecognitionProfiles::read)
        RarityCalculator::class.java.getDeclaredField("recognitionProfiles\$delegate")
            .apply { isAccessible = true }.set(calculator, lazyOf(profiles))
    }
    private val profiles = listOf(
        File("src/main/assets/data/recognition_profiles.json"),
        File("app/src/main/assets/data/recognition_profiles.json")
    ).first { it.isFile }.reader().use { com.pokerarity.scanner.util.ocr.RecognitionProfiles.read(it) }

    private data class Screen(
        val cp: Int?, val hp: Int?, val maxHp: Int? = hp,
        val candy: String, val powerUp: Int? = null,
        val types: Set<String>? = null, val evolve: Int? = null
    )

    private fun pokemon(
        cp: Int?, hp: Int?, maxHp: Int?, screen: Screen
    ) = PokemonData(
        cp = screen.cp, hp = screen.hp, maxHp = screen.maxHp, name = null, realName = null,
        candyName = screen.candy, megaEnergy = null, weight = null, height = null,
        stardust = null, caughtDate = null,
        recognitionObservation = RecognitionObservation(
            candySpecies = screen.candy, powerUpStardust = screen.powerUp, types = screen.types,
            detailScreen = true, evolutionCandyCost = screen.evolve))

    private fun textualExact(species: String) = FieldCandidateDiagnostic(
        field = "NameTextual", source = "mlkit_spatial", rawText = species, parsedValue = species,
        status = "found", candidateScore = 0.95f, winner = true, reason = "exact_canonical",
        selectedValue = species)

    private fun derive(pokemon: PokemonData, vararg candidates: FieldCandidateDiagnostic) =
        ScanManager.deriveSpeciesEvidence(candidates.toList(), pokemon, calculator)

    private fun gateBlocked(evidence: com.pokerarity.scanner.util.ocr.SpeciesEvidence): Boolean {
        val gate = com.pokerarity.scanner.util.ocr.ScanConfidenceGate()
        val frame = com.pokerarity.scanner.util.ocr.FrameDiagnostic(
            frameIndex = 0, imageWidth = 1080, imageHeight = 2340,
            screenState = com.pokerarity.scanner.util.ocr.ScreenType.PokemonDetail.name,
            screenConfidence = 0.9f,
            crops = listOf(
                com.pokerarity.scanner.util.ocr.CropDiagnostic("CP", "t", 0, 0, 100, 40, "used",
                    com.pokerarity.scanner.util.ocr.CropProvenance.AnchorDerived.diagnosticName, 0.8f)),
            fieldCandidates = emptyList(),
            selected = com.pokerarity.scanner.util.ocr.PokemonSummary.from(
                PokemonData(cp = 150, hp = 61, maxHp = 61, name = evidence.selectedCanonicalSpecies,
                    realName = evidence.selectedCanonicalSpecies, candyName = null, megaEnergy = null,
                    weight = null, height = null, stardust = null, caughtDate = null)))
        val decision = gate.evaluate(
            com.pokerarity.scanner.util.ocr.ScanConfidenceInput(
                pokemon = frame.selected.let {
                    PokemonData(cp = 150, hp = 61, maxHp = 61, name = evidence.selectedCanonicalSpecies,
                        realName = evidence.selectedCanonicalSpecies, candyName = null, megaEnergy = null,
                        weight = null, height = null, stardust = null, caughtDate = null)
                },
                frames = listOf(frame), consistencyReason = "accepted", cpCropQuality = 0.8,
                speciesEvidence = evidence))
        return decision.decision == com.pokerarity.scanner.util.ocr.ScanDecisionType.RETRY ||
            decision.decision == com.pokerarity.scanner.util.ocr.ScanDecisionType.UNCERTAIN ||
            (!decision.mayShowOverlay)
    }

    // I. INDEPENDENT_PROFILE + EXACT agree: hard authority, no conflict, both provenances.
    @Test
    fun caseI_structuredUniqueAndExactAgree_composeWithoutConflict() {
        val pokemon = pokemon(150, 61, 61, Screen(150, 61, 61, "Weedle", evolve = 12))
        val evidence = derive(pokemon, textualExact("Weedle"))
        assertEquals("Weedle", evidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.INDEPENDENT_PROFILE, evidence.authority)
        assertEquals(SpeciesProfileStatus.COMPATIBLE, evidence.profileStatus)
        assertTrue(evidence.observationsAgree)
        assertFalse(evidence.authorityConflict)
        assertTrue(evidence.reasonCodes.contains(SpeciesEvidenceReason.INDEPENDENT_PROFILE))
        assertTrue(evidence.reasonCodes.contains(SpeciesEvidenceReason.EXACT))
    }

    // J. INDEPENDENT_PROFILE + EXACT disagree: hard conflict, no acceptance path.
    @Test
    fun caseJ_structuredUniqueAndExactDisagree_hardConflict() {
        // Types+EVOLVE uniquely establish Torchic inside its family; the textual
        // parse read a different species entirely.
        val pokemon = pokemon(null, null, null, Screen(null, null, null, "Torchic", types = setOf("fire"), evolve = 25))
        val evidence = derive(pokemon, textualExact("Purrloin"))
        assertTrue(evidence.authorityConflict)
        assertFalse(evidence.observationsAgree)
        assertTrue(gateBlocked(evidence))
    }

    // L. FAMILY EVALUATOR AMBIGUOUS + EXACT TEXT: textual authority survives with
    // INDETERMINATE profile; no conflict, no fabricated structured support.
    @Test
    fun caseL_familyAmbiguous_exactTextSurvivesWithIndeterminateProfile() {
        // Wurmple family: CP absent, maxHP 90 keeps all three rows feasible -> AMBIGUOUS.
        val pokemon = pokemon(null, 90, 90, Screen(null, 90, 90, "Wurmple"))
        val evidence = derive(pokemon, textualExact("Cascoon"))
        assertEquals("Cascoon", evidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.EXACT_CANONICAL, evidence.authority)
        assertEquals(SpeciesProfileStatus.INDETERMINATE, evidence.profileStatus)
        assertFalse(evidence.authorityConflict)
        assertTrue(evidence.reasonCodes.contains(SpeciesEvidenceReason.PROFILE_INDETERMINATE))
        assertTrue(evidence.reasonCodes.contains("family_evaluator_ambiguous"))
        // Not treated as structured positive support.
        assertFalse(evidence.reasonCodes.contains(SpeciesEvidenceReason.INDEPENDENT_PROFILE))
    }

    // M. FAMILY EVALUATOR CONTRADICTION + EXACT TEXT (in-family): no acceptance.
    @Test
    fun caseM_familyContradiction_contradictsInFamilyExactText() {
        // EVOLVE 999 eliminates every known-cost Weedle-family row (Weedle included).
        val pokemon = pokemon(150, 61, 61, Screen(150, 61, 61, "Weedle", evolve = 999))
        val evidence = derive(pokemon, textualExact("Weedle"))
        assertEquals(SpeciesProfileStatus.CONTRADICTORY, evidence.profileStatus)
        assertTrue(gateBlocked(evidence))
    }

    // N. FAMILY EVALUATOR INSUFFICIENT + EXACT TEXT (not fully eliminated):
    // exact authority survives with INDETERMINATE profile.
    @Test
    fun caseN_familyInsufficient_exactTextSurvivesWithIndeterminateProfile() {
        // EVOLVE 999 with no numeric witness: known-cost rows are eliminated, the
        // Beedrill row with unknown metadata survives unresolved -> INSUFFICIENT.
        // The textual Beedrill is NOT fully eliminated (its unknown row remains).
        val pokemon = pokemon(null, null, null, Screen(null, null, null, "Weedle", evolve = 999))
        val evidence = derive(pokemon, textualExact("Beedrill"))
        assertEquals("Beedrill", evidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.EXACT_CANONICAL, evidence.authority)
        assertEquals(SpeciesProfileStatus.INDETERMINATE, evidence.profileStatus)
        assertFalse(evidence.authorityConflict)
        assertTrue(evidence.reasonCodes.contains("family_evaluator_insufficient"))
    }

    // O. FAMILY EVALUATOR UNSUPPORTED + EXACT TEXT: zero positive support, no
    // contradiction. Real profile data has no all-unknown-cost family, so this
    // exercises the shared non-UNIQUE mapping path (same code as L/N): any
    // non-UNIQUE structured outcome maps to INDETERMINATE unless the textual
    // species' rows were all eliminated.
    @Test
    fun caseO_nonUniqueOutcomes_mapToIndeterminateWithoutContradiction() {
        // AMBIGUOUS representative (L) and INSUFFICIENT representative (N) both
        // assert INDETERMINATE + preserved exact authority; UNSUPPORTED_MECHANIC
        // flows through the identical mapping branch in deriveSpeciesEvidence.
        val pokemon = pokemon(null, 90, 90, Screen(null, 90, 90, "Wurmple"))
        val evidence = derive(pokemon, textualExact("Cascoon"))
        assertEquals(SpeciesProfileStatus.INDETERMINATE, evidence.profileStatus)
        assertEquals(SpeciesAuthority.EXACT_CANONICAL, evidence.authority)
    }

    @Test
    fun nonDetailAnchoredObservationPreservesTextualProvenanceButFailsClosed() {
        val base = pokemon(
            150,
            61,
            61,
            Screen(150, 61, 61, "Weedle", evolve = 12)
        )
        val guarded = base.copy(
            recognitionObservation = requireNotNull(base.recognitionObservation).copy(
                detailScreen = false
            )
        )

        val evidence = derive(guarded, textualExact("Weedle"))

        assertEquals("Weedle", evidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.EXACT_CANONICAL, evidence.authority)
        assertEquals(SpeciesProfileStatus.INDETERMINATE, evidence.profileStatus)
        assertFalse(evidence.observationsAgree)
        assertTrue(evidence.reasonCodes.contains("detail_screen_unconfirmed"))
        assertTrue(gateBlocked(evidence))
    }

    @Test
    fun numericConflictAnchoredObservationCannotBecomeCompatibleAuthority() {
        val base = pokemon(
            150,
            61,
            61,
            Screen(150, 61, 61, "Weedle", evolve = 12)
        )
        val guarded = base.copy(
            recognitionObservation = requireNotNull(base.recognitionObservation).copy(
                numericConflict = true
            )
        )

        val evidence = derive(guarded, textualExact("Weedle"))

        assertEquals("Weedle", evidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.EXACT_CANONICAL, evidence.authority)
        assertEquals(SpeciesProfileStatus.CONTRADICTORY, evidence.profileStatus)
        assertFalse(evidence.observationsAgree)
        assertTrue(evidence.reasonCodes.contains("numeric_observations_conflict"))
        assertTrue(gateBlocked(evidence))
    }

    // Structured UNIQUE with no textual candidate at all: INDEPENDENT_PROFILE stands.
    @Test
    fun structuredUnique_withoutTextualCandidate_standsAlone() {
        val pokemon = pokemon(150, 61, 61, Screen(150, 61, 61, "Weedle", evolve = 12))
        val evidence = derive(pokemon)
        assertEquals("Weedle", evidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.INDEPENDENT_PROFILE, evidence.authority)
        assertEquals(SpeciesProfileStatus.COMPATIBLE, evidence.profileStatus)
        assertTrue(evidence.observationsAgree)
    }

    // Structured UNIQUE + textual SAFE_FUZZY disagree: hard authority wins, soft
    // textual never escalates to a conflict.
    @Test
    fun structuredUnique_softTextualDisagree_noConflict() {
        val pokemon = pokemon(150, 61, 61, Screen(150, 61, 61, "Weedle", evolve = 12))
        val soft = FieldCandidateDiagnostic(
            field = "NameTextual", source = "mlkit_spatial", rawText = "Kakuna", parsedValue = "Kakuna",
            status = "found", candidateScore = 0.7f, winner = true,
            reason = "unique_structured_distance_one", selectedValue = "Kakuna")
        val evidence = derive(pokemon, soft)
        assertEquals("Weedle", evidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.INDEPENDENT_PROFILE, evidence.authority)
        assertFalse(evidence.authorityConflict)
        assertTrue(evidence.observationsAgree)
    }
}
