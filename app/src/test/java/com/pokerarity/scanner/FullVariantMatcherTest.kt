package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.util.vision.FullVariantMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FullVariantMatcherTest {

    @Test
    fun prefersSameSpeciesCandidateOverFamilyRescue() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Raichu",
            candidates = listOf(
                candidate(
                    species = "Pikachu",
                    spriteKey = "025_00_01",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Holiday 2016",
                    matchScore = 0.35f,
                    source = "classifier_species"
                ),
                candidate(
                    species = "Raichu",
                    spriteKey = "026_00_01",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Holiday 2016",
                    matchScore = 0.39f,
                    source = "authoritative_species_date"
                )
            )
        )

        assertEquals("Raichu", match.finalSpecies)
        assertEquals("026_00_01", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertEquals("Holiday 2016", match.resolvedEventLabel)
        assertEquals("derived_authoritative", match.explanationMode)
    }

    @Test
    fun windowlessExactSameSpeciesCostumeDoesNotBeatDateDerivedEvent() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Pikachu",
            candidates = listOf(
                candidate(
                    species = "Pikachu",
                    spriteKey = "025_00_FFLYING_03",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Pikachu Flying 03",
                    matchScore = 0.561f,
                    source = "classifier_species_authoritative_remap",
                    classifierConfidence = 0.439f
                ),
                candidate(
                    species = "Pikachu",
                    spriteKey = "025_00_FWCS_2024",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Pokemon 2024 Pokemon World Championships Celebration",
                    matchScore = 0f,
                    rescueKind = "species_date_match",
                    source = "authoritative_species_date",
                    classifierConfidence = 1f
                )
            )
        )

        assertEquals("025_00_FWCS_2024", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertEquals("Pokemon 2024 Pokemon World Championships Celebration", match.resolvedEventLabel)
        assertEquals("derived_authoritative", match.explanationMode)
    }

    @Test
    fun weakRescueDoesNotGetExactExplanationMode() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Pikachu",
            candidates = listOf(
                candidate(
                    species = "Pikachu",
                    spriteKey = "025_00_12",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "2022 World Championships Celebration",
                    matchScore = 0.35f,
                    rescueKind = "family_costume_rescue",
                    source = "classifier_species"
                )
            )
        )

        assertTrue(match.resolvedCostume)
        assertFalse(match.explanationMode == "exact_authoritative")
    }

    @Test
    fun closeSecondaryNonBaseDoesNotBeatBaseByItself() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Slowpoke",
            candidates = listOf(
                candidate(
                    species = "Slowpoke",
                    spriteKey = "079_00_shiny",
                    variantClass = "base",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.437f,
                    source = "classifier_species"
                ),
                candidate(
                    species = "Slowpoke",
                    spriteKey = "079_31",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = null,
                    matchScore = 0.541f,
                    rescueKind = "species_secondary_non_base",
                    source = "classifier_species_secondary_non_base"
                )
            )
        )

        assertEquals("079_00_shiny", match.finalSpriteKey)
        assertFalse(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
    }

    @Test
    fun strongSecondaryCostumeNearTieCanBeatBaseCandidate() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Gengar",
            candidates = listOf(
                candidate(
                    species = "Gengar",
                    spriteKey = "094_00_shiny",
                    variantClass = "base",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.433f,
                    source = "classifier_species",
                    isShiny = true,
                    classifierConfidence = 0.421f
                ),
                candidate(
                    species = "Gengar",
                    spriteKey = "094_00_11",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = null,
                    matchScore = 0.479f,
                    rescueKind = "species_secondary_non_base",
                    source = "classifier_species_secondary_non_base",
                    classifierConfidence = 0.421f
                )
            )
        )

        assertEquals("094_00_11", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
    }

    @Test
    fun authoritativeFamilyCostumeSupportCanBeatBaseCandidate() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Slowpoke",
            candidates = listOf(
                candidate(
                    species = "Slowpoke",
                    spriteKey = "079_00_shiny",
                    variantClass = "base",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.437f,
                    source = "classifier_species"
                ),
                candidate(
                    species = "Slowpoke",
                    spriteKey = "079_31",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = null,
                    matchScore = 0.395f,
                    rescueKind = "family_global_costume_support",
                    source = "authoritative_family_costume_support"
                )
            )
        )

        assertEquals("079_31", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
    }

    @Test
    fun classifierSpeciesCostumeCanBeatWeakerGlobalBaseCandidate() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Pikachu",
            candidates = listOf(
                candidate(
                    species = "Pikachu",
                    spriteKey = "025_00",
                    variantClass = "base",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.330f,
                    source = "classifier_global",
                    classifierConfidence = 0.344f
                ),
                candidate(
                    species = "Pikachu",
                    spriteKey = "025_00_23",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = null,
                    matchScore = 0.486f,
                    source = "classifier_species",
                    classifierConfidence = 0.520f
                )
            )
        )

        assertEquals("025_00_23", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
    }

    @Test
    fun lowConfidenceClassifierCostumeDoesNotResolveCostume() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Vaporeon",
            candidates = listOf(
                candidate(
                    species = "Vaporeon",
                    spriteKey = "134_00_07",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = null,
                    matchScore = 0.447f,
                    source = "classifier_species",
                    classifierConfidence = 0.388f
                )
            )
        )

        assertFalse(match.resolvedCostume)
        assertEquals("base", match.resolvedVariantClass)
        assertEquals("generic_species_only", match.explanationMode)
    }

    @Test
    fun nearThresholdClassifierCostumeStillResolvesCostume() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Lapras",
            candidates = listOf(
                candidate(
                    species = "Lapras",
                    spriteKey = "131_00_CSPRING_2023_MYSTIC",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Water Festival",
                    matchScore = 0.429f,
                    source = "classifier_species",
                    classifierConfidence = 0.4015f
                )
            )
        )

        assertTrue(match.resolvedCostume)
        assertEquals("costume", match.resolvedVariantClass)
    }

    @Test
    fun weakGenericUnresolvedCostumeDoesNotResolveCostume() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Slowpoke",
            candidates = listOf(
                candidate(
                    species = "Slowpoke",
                    spriteKey = "079_00_CPI_NOEVOLVE_shiny",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Cpi No evolve",
                    matchScore = 0.447f,
                    source = "classifier_species",
                    isShiny = true,
                    classifierConfidence = 0.408f
                )
            )
        )

        assertFalse(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
        assertEquals("base", match.resolvedVariantClass)
        assertEquals("generic_species_only", match.explanationMode)
    }

    @Test
    fun strongerGenericUnresolvedCostumeStillResolvesCostume() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Slowpoke",
            candidates = listOf(
                candidate(
                    species = "Slowpoke",
                    spriteKey = "079_00_CPI_NOEVOLVE_shiny",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Cpi No evolve",
                    matchScore = 0.413f,
                    source = "classifier_species",
                    isShiny = true,
                    classifierConfidence = 0.443f
                )
            )
        )

        assertTrue(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
        assertEquals("costume", match.resolvedVariantClass)
    }

    @Test
    fun classifierCostumeShinyBelowThresholdDoesNotResolveShiny() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Slowpoke",
            candidates = listOf(
                candidate(
                    species = "Slowpoke",
                    spriteKey = "079_00_CPI_NOEVOLVE_shiny",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = null,
                    matchScore = 0.447f,
                    source = "classifier_species",
                    isShiny = true,
                    classifierConfidence = 0.443f
                )
            )
        )

        assertEquals("079_00_CPI_NOEVOLVE_shiny", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
    }

    @Test
    fun classifierFormShinyBelowThresholdDoesNotResolveShiny() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Farfetch'd",
            candidates = listOf(
                candidate(
                    species = "Farfetch'd",
                    spriteKey = "083_00_FGALARIAN_shiny",
                    variantClass = "form",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.403f,
                    source = "classifier_species",
                    isShiny = true,
                    classifierConfidence = 0.520f,
                    rescueKind = "exact_non_base_consensus"
                )
            )
        )

        assertEquals("083_00_FGALARIAN_shiny", match.finalSpriteKey)
        assertEquals("form", match.resolvedVariantClass)
        assertFalse(match.resolvedShiny)
    }

    @Test
    fun classifierBaseShinyDoesNotResolveShinyByItself() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Raikou",
            candidates = listOf(
                candidate(
                    species = "Raikou",
                    spriteKey = "243_00_shiny",
                    variantClass = "base",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.394f,
                    source = "classifier_global",
                    isShiny = true,
                    classifierConfidence = 0.755f
                )
            )
        )

        assertFalse(match.resolvedShiny)
        assertEquals("base", match.resolvedVariantClass)
        assertEquals("generic_species_only", match.explanationMode)
    }

    @Test
    fun liveSpeciesEventSupportCanBeatBaseCandidate() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Absol",
            candidates = listOf(
                candidate(
                    species = "Absol",
                    spriteKey = "359_00",
                    variantClass = "base",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.463f,
                    source = "classifier_species",
                    classifierConfidence = 0.676f
                ),
                candidate(
                    species = "Absol",
                    spriteKey = "359_00",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Fashion Raid Day",
                    matchScore = 0.40f,
                    rescueKind = "active_species_live_event",
                    source = "authoritative_live_species_event",
                    classifierConfidence = 1f
                )
            )
        )

        assertTrue(match.resolvedCostume)
        assertEquals("costume", match.resolvedVariantClass)
        assertEquals("Fashion Raid Day", match.resolvedEventLabel)
        assertEquals("derived_authoritative", match.explanationMode)
    }

    @Test
    fun liveSpeciesEventSupportPreservesClassifierShinyCostumePeer() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Butterfree",
            candidates = listOf(
                candidate(
                    species = "Butterfree",
                    spriteKey = "012_01_shiny",
                    variantClass = "form",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.60f,
                    source = "classifier_species",
                    isShiny = true,
                    classifierConfidence = 0.52f,
                    rescueKind = "exact_non_base_consensus"
                ),
                candidate(
                    species = "Butterfree",
                    spriteKey = "012_01",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Fashion Raid Day",
                    matchScore = 0.40f,
                    rescueKind = "active_species_live_event",
                    source = "authoritative_live_species_event",
                    classifierConfidence = 1f
                )
            )
        )

        assertEquals("012_01_shiny", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertTrue(match.resolvedShiny)
        assertEquals("Fashion Raid Day", match.resolvedEventLabel)
        assertEquals("derived_authoritative", match.explanationMode)
    }

    @Test
    fun speciesDateSupportPreservesClassifierShinyCostumePeer() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Butterfree",
            candidates = listOf(
                candidate(
                    species = "Butterfree",
                    spriteKey = "012_00_FGIGANTAMAX_shiny",
                    variantClass = "form",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.489f,
                    source = "classifier_species",
                    isShiny = true,
                    classifierConfidence = 0.52f,
                    rescueKind = "exact_non_base_consensus"
                ),
                candidate(
                    species = "Butterfree",
                    spriteKey = "012_00_FASHION_2021_NOEVOLVE",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Fashion Week 2022",
                    matchScore = 0.0f,
                    rescueKind = "species_date_match",
                    source = "authoritative_species_date",
                    classifierConfidence = 1f
                )
            )
        )

        assertEquals("012_00_FGIGANTAMAX_shiny", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertTrue(match.resolvedShiny)
        assertEquals("Fashion Week 2022", match.resolvedEventLabel)
        assertEquals("derived_authoritative", match.explanationMode)
    }

    @Test
    fun familyAuthoritativeRemapCanBeatWrongSiblingBaseChoice() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Cottonee",
            candidates = listOf(
                candidate(
                    species = "Whimsicott",
                    spriteKey = "547_00_CSPRING_2024_shiny",
                    variantClass = "form",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.504f,
                    source = "classifier_species",
                    isShiny = true,
                    classifierConfidence = 0.4005f
                ),
                candidate(
                    species = "Cottonee",
                    spriteKey = "546_00_SPRING_2024_shiny",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Spring into Spring 2024",
                    matchScore = 0.504f,
                    rescueKind = "family_variant_token_remap",
                    source = "classifier_family_authoritative_remap",
                    isShiny = true,
                    classifierConfidence = 0.4005f
                )
            )
        )

        assertEquals("546_00_SPRING_2024_shiny", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
        assertNull(match.resolvedEventLabel)
    }

    @Test
    fun speciesAuthoritativeRemapCanBeatBaseCandidate() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Slakoth",
            candidates = listOf(
                candidate(
                    species = "Slakoth",
                    spriteKey = "287_00",
                    variantClass = "base",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.459f,
                    source = "classifier_species",
                    classifierConfidence = 0.431f
                ),
                candidate(
                    species = "Slakoth",
                    spriteKey = "287_00_SUMMER_2024",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Scorching Steps 2024",
                    matchScore = 0.480f,
                    source = "classifier_species_authoritative_remap",
                    classifierConfidence = 0.431f
                )
            )
        )

        assertEquals("287_00_SUMMER_2024", match.finalSpriteKey)
        assertTrue(match.resolvedCostume)
        assertFalse(match.resolvedShiny)
        assertNull(match.resolvedEventLabel)
    }

    @Test
    fun globalAuthoritativeFormRemapDoesNotResolveShinyBelowStricterGate() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Farfetch'd",
            candidates = listOf(
                candidate(
                    species = "Farfetch'd",
                    spriteKey = "083_00_FGALARIAN_shiny",
                    variantClass = "form",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.403f,
                    source = "classifier_global_authoritative_remap",
                    isShiny = true,
                    classifierConfidence = 0.643f
                )
            )
        )

        assertEquals("083_00_FGALARIAN_shiny", match.finalSpriteKey)
        assertEquals("form", match.resolvedVariantClass)
        assertFalse(match.resolvedShiny)
    }

    @Test
    fun sameSpeciesAuthoritativeFormRemapPreservesConsensusShinyAtModerateConfidence() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Spinda",
            candidates = listOf(
                candidate(
                    species = "Spinda",
                    spriteKey = "327_00_F02_shiny",
                    variantClass = "form",
                    isCostumeLike = false,
                    eventLabel = null,
                    matchScore = 0.440f,
                    source = "classifier_species_authoritative_remap",
                    isShiny = true,
                    classifierConfidence = 0.520f,
                    rescueKind = "exact_non_base_consensus"
                )
            )
        )

        assertEquals("327_00_F02_shiny", match.finalSpriteKey)
        assertEquals("form", match.resolvedVariantClass)
        assertTrue(match.resolvedShiny)
    }

    @Test
    fun speculativeExactNonBaseCostumeRemapFallsBackToBaseWithoutConcreteEventWindow() {
        val match = FullVariantMatcher.match(
            finalSpecies = "Nidoqueen",
            candidates = listOf(
                candidate(
                    species = "Nidoqueen",
                    spriteKey = "031_00_CROYAL_NOEVOLVE",
                    variantClass = "costume",
                    isCostumeLike = true,
                    eventLabel = "Croyal No evolve",
                    matchScore = 0.474f,
                    source = "classifier_species_authoritative_remap",
                    classifierConfidence = 0.520f,
                    rescueKind = "exact_non_base_consensus"
                )
            )
        )

        assertEquals("031_00_CROYAL_NOEVOLVE", match.finalSpriteKey)
        assertEquals("base", match.resolvedVariantClass)
        assertFalse(match.resolvedCostume)
        assertNull(match.resolvedEventLabel)
        assertEquals("generic_species_only", match.explanationMode)
    }

    private fun candidate(
        species: String,
        spriteKey: String,
        variantClass: String,
        isCostumeLike: Boolean,
        eventLabel: String?,
        matchScore: Float,
        rescueKind: String? = null,
        source: String,
        isShiny: Boolean = false,
        classifierConfidence: Float = 1f
    ) = FullVariantCandidate(
        species = species,
        spriteKey = spriteKey,
        variantClass = variantClass,
        isShiny = isShiny,
        isCostumeLike = isCostumeLike,
        eventLabel = eventLabel,
        eventStart = null,
        eventEnd = null,
        matchScore = matchScore,
        rescueKind = rescueKind,
        source = source,
        classifierConfidence = classifierConfidence
    )
}
