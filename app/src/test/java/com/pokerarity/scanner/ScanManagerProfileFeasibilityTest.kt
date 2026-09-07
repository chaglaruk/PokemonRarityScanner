package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.service.ScanManager
import com.pokerarity.scanner.util.ocr.CropDiagnostic
import com.pokerarity.scanner.util.ocr.CropProvenance
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.FrameDiagnostic
import com.pokerarity.scanner.util.ocr.PokemonSummary
import com.pokerarity.scanner.util.ocr.ScanConfidenceGate
import com.pokerarity.scanner.util.ocr.ScanConfidenceInput
import com.pokerarity.scanner.util.ocr.ScanConsistencyGate
import com.pokerarity.scanner.util.ocr.ScanDecisionType
import com.pokerarity.scanner.util.ocr.ScreenType
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

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class ScanManagerProfileFeasibilityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val calculator = RarityCalculator(context).also { calculator ->
        // This project does not package Android assets in JVM tests. Load the real
        // production facts explicitly, as the existing refiner regression suite does.
        val assetDir = listOf(File("src/main/assets/data"), File("app/src/main/assets/data"))
            .first { it.isDirectory }
        val stats = Gson().fromJson<Map<String, RarityCalculator.BaseStats>>(
            File(assetDir, "pokemon_base_stats.json").readText(),
            object : TypeToken<Map<String, RarityCalculator.BaseStats>>() {}.type
        )
        require(stats.containsKey("Eevee") && stats.containsKey("Lapras"))
        RarityCalculator::class.java.getDeclaredField("baseStats\$delegate")
            .apply { isAccessible = true }.set(calculator, lazyOf(stats))
        val families = Gson().fromJson(File(assetDir, "pokemon_families.json").readText(), FamilyData::class.java)
        PokemonFamilyRegistry::class.java.getDeclaredField("speciesToFamily").apply { isAccessible = true }
            .set(PokemonFamilyRegistry, families.speciesToFamily.mapKeys { it.key.lowercase() })
        PokemonFamilyRegistry::class.java.getDeclaredField("familyToSpecies").apply { isAccessible = true }
            .set(PokemonFamilyRegistry, families.families)
        PokemonFamilyRegistry::class.java.getDeclaredField("loaded").apply { isAccessible = true }
            .setBoolean(PokemonFamilyRegistry, true)
    }

    private data class FamilyData(val speciesToFamily: Map<String, String>, val families: Map<String, List<String>>)

    @Test
    fun exactNameAndFeasibleCpMaximumHpPassRealGatesWithoutArc() {
        val pokemon = pokemon(species = "Lapras", cp = 1435, hp = 171)
        val fields = fields(pokemon)
        val evidence = ScanManager.deriveSpeciesEvidence(fields, pokemon, calculator)
        val consistency = ScanConsistencyGate(context, calculator).evaluate(pokemon, pokemon, evidence)
        val decision = ScanConfidenceGate().evaluate(
            ScanConfidenceInput(
                pokemon = consistency.pokemon,
                frames = listOf(frame(pokemon, fields, 0), frame(pokemon, fields, 1)),
                consistencyReason = consistency.reason,
                consistencyRequestedRetry = consistency.shouldRetry,
                cpCropQuality = 0.9,
                speciesEvidence = evidence
            )
        )

        assertEquals(SpeciesProfileStatus.COMPATIBLE, evidence.profileStatus)
        assertFalse(consistency.reason, consistency.shouldRetry)
        assertEquals(decision.developerReasons.toString(), ScanDecisionType.ACCEPT, decision.decision)
        assertTrue(decision.collectionSafe)
    }

    @Test
    fun damagedAndFaintedPokemonUseMaximumHp() {
        for (currentHp in listOf(0, 1, 40, 80)) {
            assertEquals(SpeciesProfileStatus.COMPATIBLE, profile(pokemon().copy(hp = currentHp)))
        }
    }

    @Test
    fun totalStardustIsNotTreatedAsPowerUpLevelConstraint() {
        assertEquals(SpeciesProfileStatus.COMPATIBLE, profile(pokemon().copy(stardust = 200)))
    }

    @Test
    fun minimumTenHpAndCpRemainFeasible() {
        assertEquals(
            SpeciesProfileStatus.COMPATIBLE,
            profile(pokemon(species = "Magikarp", cp = 10, hp = 10))
        )
    }

    @Test
    fun individuallyPossibleCpAndHpMustMatchSameLevelAndIvs() {
        assertEquals(
            SpeciesProfileStatus.CONTRADICTORY,
            profile(pokemon(cp = 424, hp = 20))
        )
    }

    @Test
    fun impossibleMaximumHpDoesNotBecomeCompatibleWhenArcIsMissing() {
        assertEquals(SpeciesProfileStatus.IMPOSSIBLE, profile(pokemon(hp = 999)))
    }

    @Test
    fun arcMustAgreeWithJointCpHpLevelsRatherThanUnrelatedHpMatch() {
        // Diglett's 55 HP can occur at level 25.5, but CP 682 with that HP only at level 49.
        val pokemon = pokemon(species = "Diglett", cp = 682, hp = 55)
        assertEquals(SpeciesProfileStatus.COMPATIBLE, profile(pokemon))
        assertEquals(SpeciesProfileStatus.CONTRADICTORY, profile(pokemon.copy(arcLevel = 0.5f)))
        assertEquals(SpeciesProfileStatus.COMPATIBLE, profile(pokemon.copy(arcLevel = 48f / 49f)))
    }

    @Test
    fun missingStatsAreIndeterminateRatherThanAnImpossibleSpeciesClaim() {
        assertEquals(SpeciesProfileStatus.INDETERMINATE, profile(pokemon(species = "MissingStats")))
    }

    @Test
    fun absentCoreEvidenceRemainsMissing() {
        for (pokemon in listOf(pokemon().copy(cp = null), pokemon().copy(maxHp = null))) {
            assertEquals(SpeciesProfileStatus.MISSING, profile(pokemon))
        }
    }

    @Test
    fun feasibleProfileDoesNotPromoteFuzzyNameAuthority() {
        val pokemon = pokemon()
        val evidence = ScanManager.deriveSpeciesEvidence(
            fields(pokemon).map {
                if (it.field == "Name") it.copy(reason = "winner:unique_structured_distance_one") else it
            },
            pokemon,
            calculator
        )
        val decision = ScanConfidenceGate().evaluate(ScanConfidenceInput(pokemon, speciesEvidence = evidence))

        assertEquals(SpeciesProfileStatus.COMPATIBLE, evidence.profileStatus)
        assertFalse(evidence.hasHardAuthority)
        assertTrue(decision.developerReasons.contains(SpeciesEvidenceReason.EARLY_EXIT_BLOCKED_AUTHORITY))
        assertFalse(decision.collectionSafe)
    }

    private fun profile(pokemon: PokemonData): SpeciesProfileStatus =
        ScanManager.deriveSpeciesEvidence(fields(pokemon), pokemon, calculator).profileStatus

    private fun fields(pokemon: PokemonData): List<FieldCandidateDiagnostic> =
        listOf("Name" to pokemon.name, "CP" to pokemon.cp?.toString(), "HP" to pokemon.maxHp?.toString())
            .map { (field, value) ->
                FieldCandidateDiagnostic(
                    field = field,
                    source = "profile_regression",
                    rawText = value,
                    parsedValue = value,
                    status = if (value == null) "missing" else "found",
                    candidateScore = 0.95f,
                    winner = value != null,
                    selectedValue = value,
                    reason = if (field == "Name") "winner:exact_canonical" else null
                )
            }

    private fun frame(pokemon: PokemonData, fields: List<FieldCandidateDiagnostic>, index: Int) =
        FrameDiagnostic(
            frameIndex = index,
            imageWidth = 1080,
            imageHeight = 2340,
            screenState = ScreenType.PokemonDetail.name,
            screenConfidence = 0.95f,
            crops = listOf("Name", "CP", "HP").map {
                CropDiagnostic(it, "profile_regression", 0, 0, 100, 40, "used", CropProvenance.AnchorDerived.diagnosticName, 0.9f)
            },
            fieldCandidates = fields,
            selected = PokemonSummary.from(pokemon)
        )

    private fun pokemon(species: String = "Eevee", cp: Int = 424, hp: Int = 80) = PokemonData(
        cp = cp,
        hp = hp,
        maxHp = hp,
        name = species,
        realName = species,
        candyName = species,
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = null,
        caughtDate = null
    )
}
