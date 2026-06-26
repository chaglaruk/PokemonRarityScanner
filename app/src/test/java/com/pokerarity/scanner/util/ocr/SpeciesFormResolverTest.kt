package com.pokerarity.scanner.util.ocr

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.RarityCalculator
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
class SpeciesFormResolverTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val resolver = SpeciesFormResolver(context, RarityCalculator(context))
    private val refiner = SpeciesRefiner(context, RarityCalculator(context))

    @Test
    fun exactSpeciesMatchWins() {
        val result = resolver.resolve(pokemon(name = "Porygon", rawOcrText = "Name:Porygon|NameDynamic:missing|NameHC:not-run"))

        assertEquals("Porygon", result.species)
        assertTrue(result.confidence >= 0.85f)
        assertTrue(result.reasons.any { it.contains("exact", ignoreCase = true) })
    }

    @Test
    fun fuzzySpeciesMatchWorksForRealisticOcrNoise() {
        val result = resolver.resolve(pokemon(name = "Porygon", rawOcrText = "Name:Poryg0n|NameDynamic:missing|NameHC:not-run"))

        assertEquals("Porygon", result.species)
        assertTrue(result.confidence >= 0.50f)
    }

    @Test
    fun markerValuesNeverResolveAsSpecies() {
        listOf("missing", "not-run", "skipped", "RawText", "").forEach { marker ->
            val result = resolver.resolve(pokemon(rawOcrText = "Name:$marker|NameDynamic:$marker|NameHC:$marker"))

            assertNull("marker=$marker", result.species)
            assertTrue("marker=$marker", result.confidence < 0.50f)
        }
    }

    @Test
    fun numericStringsNeverResolveAsSpecies() {
        val result = resolver.resolve(pokemon(rawOcrText = "Name:1500|NameDynamic:2024|NameHC:120/120 HP"))

        assertNull(result.species)
        assertTrue(result.trace.evidenceMissing.contains("usable_name_text"))
    }

    @Test
    fun candyEvidenceCanRescueAmbiguousOcr() {
        val result = resolver.resolve(
            pokemon(
                name = "Unknown",
                realName = null,
                candyName = "Espeon",
                rawOcrText = "Name:Espe0n|NameDynamic:missing|NameHC:not-run|Candy:Espeon Candy"
            )
        )

        assertEquals("Espeon", result.species)
        assertTrue(result.trace.evidenceUsed.contains("candy_family"))
        assertTrue(result.confidence >= 0.70f)
    }

    @Test
    fun weakGarbageReturnsUnknownLowConfidence() {
        val result = resolver.resolve(pokemon(name = "Unknown", realName = null, rawOcrText = "Name:zzz qq|NameDynamic:missing"))

        assertNull(result.species)
        assertTrue(result.confidence < 0.50f)
    }

    @Test
    fun alternativeCandidatesAreRecorded() {
        val result = resolver.resolve(pokemon(name = "Espeon", rawOcrText = "Name:Espeon|NameDynamic:Espeon|NameHC:missing"))

        assertEquals("Espeon", result.species)
        assertTrue(result.trace.canonicalCandidates.isNotEmpty())
        assertTrue(result.trace.canonicalCandidates.any { it.species == "Espeon" && it.winner })
    }

    @Test
    fun speciesRefinerUsesResolverWithoutBreakingFallback() {
        val refined = refiner.refine(
            pokemon(
                name = "Unknown",
                realName = null,
                rawOcrText = "Name:missing|NameDynamic:Porygon|NameHC:not-run"
            )
        )

        assertEquals("Porygon", refined.name)
        assertEquals("Porygon", refined.realName)
        assertEquals("Porygon", refined.speciesResolverTrace?.winningSpecies)
    }

    @Test
    fun speciesRefinerDoesNotCreateHighConfidenceSpeciesFromWeakEvidence() {
        val refined = refiner.refine(
            pokemon(
                name = "Unknown",
                realName = null,
                rawOcrText = "Name:12345|NameDynamic:missing|NameHC:skipped"
            )
        )

        assertEquals("Unknown", refined.name)
        assertNull(refined.realName)
        assertTrue((refined.speciesResolverTrace?.confidence ?: 0f) < 0.50f)
    }

    private fun pokemon(
        name: String? = "Pikachu",
        realName: String? = name,
        candyName: String? = null,
        rawOcrText: String
    ): PokemonData = PokemonData(
        cp = null,
        hp = null,
        maxHp = null,
        name = name,
        realName = realName,
        candyName = candyName,
        megaEnergy = null,
        weight = null,
        height = null,
        stardust = null,
        caughtDate = null,
        rawOcrText = rawOcrText
    )
}
