package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.ConstraintStatus
import com.pokerarity.scanner.util.ocr.EvaluationOutcome
import com.pokerarity.scanner.util.ocr.FamilySpeciesResolver
import com.pokerarity.scanner.util.ocr.RecognitionProfiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File

/**
 * Phase 1A trace contract: a reviewer must be able to answer "why was this
 * species accepted?" (or rejected) purely from the candidate/constraint trace.
 */
@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class FamilySpeciesResolverEvaluationTraceTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val profiles = listOf(
        File("src/main/assets/data/recognition_profiles.json"),
        File("app/src/main/assets/data/recognition_profiles.json")
    ).first { it.isFile }.reader().use(RecognitionProfiles::read)
    private val resolver = FamilySpeciesResolver(profiles, RarityCalculator(context))

    private fun pokemon(cp: Int?, hp: Int?, maxHp: Int? = hp) = PokemonData(
        cp = cp, hp = hp, maxHp = maxHp, name = "nickname", realName = "nickname", candyName = null,
        megaEnergy = null, weight = null, height = null, stardust = null, caughtDate = null)

    private fun obs(candy: String, powerUp: Int? = null, types: Set<String>? = null, evolve: Int? = null) =
        FamilySpeciesResolver.Observation(
            candySpecies = candy, exactCandyLabel = true, powerUpStardust = powerUp,
            anchoredPowerUpCost = powerUp != null, types = types, evolutionCandyCost = evolve)

    @Test
    fun weedleAcceptanceIsTraceableToItsConstraints() {
        val evaluation = resolver.resolveWithEvaluation(pokemon(150, 61), obs("Weedle", evolve = 12))

        assertEquals(EvaluationOutcome.UNIQUE_SUPPORTED, evaluation.outcome)
        assertEquals("Weedle", evaluation.acceptedSpecies)
        assertEquals("independent_family_profile", evaluation.acceptanceReason)
        // Initial pool: every Weedle-family form row.
        assertTrue(evaluation.initialCandidates.any { it.species == "Weedle" })
        assertTrue(evaluation.initialCandidates.any { it.species == "Kakuna" })
        // The evolve constraint eliminated Kakuna and matched Weedle.
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.observed)
        assertTrue(evolve.matched.any { it.species == "Weedle" })
        assertTrue(evolve.eliminated.any { it.species == "Kakuna" })
        // Unknown-metadata rows that were eliminated elsewhere never appear unresolved.
        val beedrillRows = evaluation.initialCandidates.filter { it.species == "Beedrill" }
        assertTrue(beedrillRows.isNotEmpty())
        assertTrue(beedrillRows.all { row -> evaluation.survivingCandidates.none { it == row } })
        // Survivors project to exactly the accepted species.
        assertEquals(listOf("Weedle"), evaluation.survivingCandidates.map { it.species }.distinct())
    }

    @Test
    fun unobservedConstraintsAreRecordedAsNotObserved() {
        val evaluation = resolver.resolveWithEvaluation(pokemon(150, 61), obs("Weedle", evolve = 12))
        val type = evaluation.evaluations.single { it.name == "complete_type" }
        assertFalse(type.observed)
        assertEquals(ConstraintStatus.NOT_OBSERVED, type.status)
        assertTrue(type.matched.isEmpty() && type.eliminated.isEmpty())
        val powerUp = evaluation.evaluations.single { it.name == "power_up_cost" }
        assertFalse(powerUp.observed)
        assertEquals(ConstraintStatus.NOT_OBSERVED, powerUp.status)
    }

    @Test
    fun unknownEvolveMetadataIsVisibleAsUnresolvedNeverMatched() {
        val template = profiles.forSpecies("Torchic").first()
        val known = template.copy(species = "Known", forms = setOf("Known"),
            candySpecies = "Synthetic", evolutionCandyCosts = setOf(50))
        val mystery = template.copy(species = "Mystery", forms = setOf("Mystery"),
            candySpecies = "Synthetic", evolutionCandyCosts = null)
        val synthetic = FamilySpeciesResolver(
            RecognitionProfiles(listOf(known, mystery), profiles.cpMultipliers), RarityCalculator(context))

        val evaluation = synthetic.resolveWithEvaluation(
            pokemon(null, null, maxHp = null), obs("Synthetic", types = setOf("fire"), evolve = 25))

        assertEquals(EvaluationOutcome.INSUFFICIENT_EVIDENCE, evaluation.outcome)
        assertNull(evaluation.acceptedSpecies)
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.eliminated.any { it.species == "Known" })
        assertTrue(evolve.unresolved.any { it.species == "Mystery" })
        assertFalse(evolve.matched.any { it.species == "Mystery" })
        // The surviving unknown-metadata row stays visible in the survivor list.
        assertTrue(evaluation.survivingCandidates.any { it.species == "Mystery" })
    }

    @Test
    fun contradictionNamesTheEliminatingConstraint() {
        val evaluation = resolver.resolveWithEvaluation(
            pokemon(null, null, maxHp = null), obs("Torchic", types = setOf("fire"), evolve = 50))
        assertEquals(EvaluationOutcome.CONTRADICTION, evaluation.outcome)
        assertNull(evaluation.acceptedSpecies)
        val type = evaluation.evaluations.single { it.name == "complete_type" }
        assertTrue(type.matched.any { it.species == "Torchic" })
        val evolve = evaluation.evaluations.single { it.name == "evolve_cost" }
        assertTrue(evolve.eliminated.any { it.species == "Torchic" })
        assertTrue(evaluation.survivingCandidates.isEmpty())
    }
}
