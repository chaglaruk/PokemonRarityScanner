package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.AnchoredFrameSelection
import com.pokerarity.scanner.service.ScanFrameCandidate
import com.pokerarity.scanner.service.ScanFrameFusion
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.service.detailedScanFrameCandidate
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.FieldRead
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.OcrFrameResult
import com.pokerarity.scanner.util.ocr.PokemonSummary
import com.pokerarity.scanner.util.ocr.RecognitionObservation
import com.pokerarity.scanner.util.ocr.SameSpeciesFormEvidence
import com.pokerarity.scanner.util.ocr.SpeciesAuthority
import com.pokerarity.scanner.util.ocr.SpeciesEvidence
import com.pokerarity.scanner.util.ocr.SpeciesProfileStatus
import com.pokerarity.scanner.util.ocr.StardustLevelEvidence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class DetailedCpProfileRecoveryTest {
    private val calculator = RarityCalculator(ApplicationProvider.getApplicationContext<Context>()).also {
        RecognitionSnapshotTestSupport.seedHolder()
    }

    @Test
    fun sameCaptureCpDisagreementUsesExclusiveCpFreeEvidenceAndKeepsCpUnknown() {
        val fast = moltresCandidate(cp = 51, path = "moltres", frameIndex = 0)
        val staleCpFormEvidence = SameSpeciesFormEvidence(
            species = "Moltres",
            snapshotRevision = "test_snapshot",
            alternatives = listOf("Galarian Form"),
            reasonCode = "same_species_profile_witness"
        )
        val detailedObservation = checkNotNull(fast.data.recognitionObservation)
            .copy(formEvidence = staleCpFormEvidence)
        val detailedPokemon = fast.data.copy(cp = 151, recognitionObservation = detailedObservation)
        val detailed = detailedScanFrameCandidate(
            fast,
            OcrFrameResult(
                detailedPokemon,
                diagnostic(detailedPokemon, fast.fieldCandidates, width = 1080)
            ),
            calculator
        )

        assertNull(detailed.data.cp)
        assertEquals("Moltres", detailed.speciesEvidence.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.INDEPENDENT_PROFILE, detailed.speciesEvidence.authority)
        assertEquals(SpeciesProfileStatus.COMPATIBLE, detailed.speciesEvidence.profileStatus)
        assertTrue(detailed.speciesEvidence.reasonCodes.contains(
            "cp_profile_contradiction_recovered_from_independent_non_cp_evidence"
        ))
        assertEquals(detailedPokemon.recognitionIdentity, detailed.data.recognitionIdentity)
        assertEquals(detailedPokemon.variantDecisionTrace, detailed.data.variantDecisionTrace)
        assertNull(detailed.data.recognitionObservation?.formEvidence)

        val selected = resolve(listOf(fast), fast, detailed)
        assertEquals("Moltres", selected.speciesEvidence.selectedCanonicalSpecies)
        assertEquals(detailed.path, selected.frame.path)
        assertNull(selected.frame.data.cp)
        assertNull(selected.frame.data.recognitionObservation?.formEvidence)
    }

    @Test
    fun matchingContradictoryCpReadsDoNotTriggerProfileRecovery() {
        val fast = moltresCandidate(
            cp = 90,
            path = "spritz-ee",
            frameIndex = 0,
            profile = SpeciesCandidateProfile(
                types = setOf("fairy"),
                species = "Spritzee",
                maxHp = 136
            )
        )
        assertEquals(SpeciesProfileStatus.CONTRADICTORY, fast.speciesEvidence.profileStatus)
        val detailedPokemon = fast.data.copy(cp = 90)
        val detailed = detailedScanFrameCandidate(
            fast,
            OcrFrameResult(
                detailedPokemon,
                diagnostic(detailedPokemon, fast.fieldCandidates, width = 1080)
            ),
            calculator
        )

        assertEquals(90, detailed.data.cp)
        assertFalse(detailed.speciesEvidence.reasonCodes.contains(
            "cp_profile_contradiction_recovered_from_independent_non_cp_evidence"
        ))
        assertConflict(resolve(listOf(fast), fast, detailed))
    }

    @Test
    fun stableCpContradictionWithCostOnlySupportRemainsUnresolved() {
        val base = moltresCandidate(
            cp = 90,
            path = "spritzee-cost-support",
            frameIndex = 0,
            profile = SpeciesCandidateProfile(
                types = setOf("fairy"),
                species = "Spritzee",
                maxHp = 136
            )
        )
        val typedLevels = StardustLevelEvidence.Levels(
            levels = listOf(25.0, 25.5, 26.0, 26.5, 35.0, 35.5, 36.0, 36.5),
            minLevel = 25.0,
            maxLevel = 36.5,
            modifiersUsed = listOf(
                com.pokerarity.scanner.util.ocr.PowerUpCostModifier.NORMAL,
                com.pokerarity.scanner.util.ocr.PowerUpCostModifier.LUCKY
            ),
            interpretations = listOf("tier12_normal", "tier17_lucky"),
            reasonCodes = listOf("test_stable_cost_evidence")
        )
        val stableObservation = checkNotNull(base.data.recognitionObservation).copy(
            powerUpStardust = 4000,
            powerUpStardustLevelEvidence = typedLevels,
            evolutionCandyCost = 50,
            evolutionCandyCostRead = FieldRead.read(50)
        )
        val fast = withObservation(base, stableObservation)
        assertEquals(SpeciesProfileStatus.CONTRADICTORY, fast.speciesEvidence.profileStatus)
        val detailedPokemon = fast.data.copy(cp = 90, recognitionObservation = stableObservation)
        val cpFreePokemon = detailedPokemon.copy(cp = null)
        val withCosts = ScanManager.deriveSpeciesEvidence(fast.fieldCandidates, cpFreePokemon, calculator)
        val withoutCosts = ScanManager.deriveSpeciesEvidenceWithoutActionCosts(
            fast.fieldCandidates,
            cpFreePokemon,
            calculator,
            excludeEvolutionCost = true,
            excludePowerUpCost = true
        )
        assertEquals("Spritzee", withCosts.selectedCanonicalSpecies)
        assertEquals(SpeciesAuthority.INDEPENDENT_PROFILE, withCosts.authority)
        assertEquals(SpeciesProfileStatus.COMPATIBLE, withCosts.profileStatus)
        assertFalse(withoutCosts.authority == SpeciesAuthority.INDEPENDENT_PROFILE &&
            withoutCosts.profileStatus == SpeciesProfileStatus.COMPATIBLE)

        val detailed = detailedScanFrameCandidate(
            fast,
            OcrFrameResult(
                detailedPokemon,
                diagnostic(detailedPokemon, fast.fieldCandidates, width = 1080)
            ),
            calculator
        )

        assertEquals(90, detailed.data.cp)
        assertFalse(detailed.speciesEvidence.reasonCodes.contains(
            "cp_profile_contradiction_recovered_from_independent_non_cp_evidence"
        ))
        assertConflict(resolve(listOf(fast), fast, detailed))
    }

    @Test
    fun actionCostDisagreementBlocksCpDisagreementRecovery() {
        val base = moltresCandidate(cp = 51, path = "moltres-cost-conflict", frameIndex = 0)
        val baseObservation = checkNotNull(base.data.recognitionObservation)
        val conflictingReads = listOf(
            baseObservation.copy(powerUpStardust = 1000) to
                baseObservation.copy(powerUpStardust = 1300),
            baseObservation.copy(
                evolutionCandyCost = 50,
                evolutionCandyCostRead = FieldRead.read(50)
            ) to baseObservation.copy(
                evolutionCandyCost = 100,
                evolutionCandyCostRead = FieldRead.read(100)
            )
        )

        conflictingReads.forEach { (fastObservation, detailedObservation) ->
            val fast = withObservation(base, fastObservation)
            val detailedPokemon = fast.data.copy(
                cp = 151,
                recognitionObservation = detailedObservation
            )
            val detailed = detailedScanFrameCandidate(
                fast,
                OcrFrameResult(
                    detailedPokemon,
                    diagnostic(detailedPokemon, fast.fieldCandidates, width = 1080)
                ),
                calculator
            )

            assertEquals(151, detailed.data.cp)
            assertFalse(detailed.speciesEvidence.reasonCodes.contains(
                "cp_profile_contradiction_recovered_from_independent_non_cp_evidence"
            ))
            assertConflict(resolve(listOf(fast), fast, detailed))
        }
    }

    @Test
    fun cpDisagreementWithConflictingTypeEvidenceCannotRecover() {
        val fast = moltresCandidate(
            cp = 51,
            path = "moltres-type-conflict",
            frameIndex = 0,
            profile = SpeciesCandidateProfile(types = setOf("fire", "flying"))
        )
        val detailedObservation = checkNotNull(fast.data.recognitionObservation)
            .copy(types = setOf("dark", "flying"))
        val detailedPokemon = fast.data.copy(cp = 151, recognitionObservation = detailedObservation)
        val detailed = detailedScanFrameCandidate(
            fast,
            OcrFrameResult(
                detailedPokemon,
                diagnostic(detailedPokemon, fast.fieldCandidates, width = 1080)
            ),
            calculator
        )

        assertEquals(151, detailed.data.cp)
        assertFalse(detailed.speciesEvidence.reasonCodes.contains(
            "cp_profile_contradiction_recovered_from_independent_non_cp_evidence"
        ))
        assertConflict(resolve(listOf(fast), fast, detailed))
    }


    private fun resolve(
        frames: List<ScanFrameCandidate>,
        first: ScanFrameCandidate,
        detailed: ScanFrameCandidate? = null
    ): AnchoredFrameSelection = checkNotNull(ScanFrameFusion.resolveAnchoredFrames(
        frames, first, detailed
    ) { frame, excludeEvolutionCost, excludePowerUpCost ->
        ScanManager.deriveSpeciesEvidenceWithoutActionCosts(
            frame.fieldCandidates,
            frame.data,
            calculator,
            excludeEvolutionCost = excludeEvolutionCost,
            excludePowerUpCost = excludePowerUpCost
        )
    })

    private fun assertConflict(selected: AnchoredFrameSelection) {
        assertEquals(SpeciesAuthority.CONFLICT, selected.speciesEvidence.authority)
        assertEquals(SpeciesProfileStatus.CONTRADICTORY, selected.speciesEvidence.profileStatus)
        assertTrue(selected.speciesEvidence.authorityConflict)
        assertFalse(selected.speciesEvidence.hasHardAuthority)
    }

    private fun moltresCandidate(
        cp: Int?,
        path: String,
        frameIndex: Int,
        profile: SpeciesCandidateProfile = SpeciesCandidateProfile()
    ): ScanFrameCandidate {
        val pokemon = PokemonData(
            cp = cp,
            hp = profile.maxHp,
            maxHp = profile.maxHp,
            name = profile.species,
            realName = profile.species,
            candyName = profile.species,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            caughtDate = null,
            recognitionObservation = RecognitionObservation(
                candySpecies = profile.species,
                powerUpStardust = null,
                types = profile.types,
                detailScreen = true,
                frameIndex = frameIndex
            )
        )
        val text = FieldCandidateDiagnostic(
            field = "NameTextual",
            source = "mlkit_spatial",
            rawText = profile.species,
            parsedValue = profile.species,
            status = "found",
            candidateScore = 0.95f,
            winner = true,
            reason = "exact_canonical",
            selectedValue = profile.species
        )
        val evidence = ScanManager.deriveSpeciesEvidence(listOf(text), pokemon, calculator)
        return ScanFrameCandidate(
            path,
            pokemon,
            if (cp == null) 0.0 else 0.9,
            evidence,
            fieldCandidates = listOf(text),
            recognitionWidth = 900
        )
    }

    private fun diagnostic(
        pokemon: PokemonData,
        fields: List<FieldCandidateDiagnostic>,
        width: Int
    ) = FrameDiagnostic(
        frameIndex = 0,
        imageWidth = width,
        imageHeight = 2340,
        fieldCandidates = fields,
        selected = PokemonSummary.from(pokemon)
    )

    private fun withObservation(
        frame: ScanFrameCandidate,
        observation: RecognitionObservation
    ): ScanFrameCandidate {
        val pokemon = frame.data.copy(recognitionObservation = observation)
        return frame.copy(
            data = pokemon,
            speciesEvidence = ScanManager.deriveSpeciesEvidence(frame.fieldCandidates, pokemon, calculator)
        )
    }

    private data class SpeciesCandidateProfile(
        val species: String = "Moltres",
        val types: Set<String> = setOf("fire", "flying"),
        val maxHp: Int = 130
    )
}
