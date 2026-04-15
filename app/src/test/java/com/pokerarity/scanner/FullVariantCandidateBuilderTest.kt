package com.pokerarity.scanner

import com.pokerarity.scanner.data.model.AuthoritativeVariantEntry
import com.pokerarity.scanner.data.model.FullVariantCandidate
import com.pokerarity.scanner.data.model.GlobalRarityLegacyEntry
import com.pokerarity.scanner.data.model.HistoricalEventAppearance
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.util.vision.FullVariantCandidateBuilder
import com.pokerarity.scanner.util.vision.VariantPrototypeClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class FullVariantCandidateBuilderTest {
    private val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    @Test
    fun buildsCandidatesFromClassifierAndAuthoritativeSpeciesDateFallback() {
        val pokemon = PokemonData(
            cp = 635,
            hp = null,
            maxHp = null,
            name = "Raichu",
            realName = "Raichu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2017-01-03"),
            rawOcrText = "VariantClassifierSpriteKey:025_00_01|VariantClassifierConfidence:0.520"
        )

        val speciesMatch = match(
            species = "Pikachu",
            spriteKey = "025_00_01",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.52f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Raichu",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Raichu" to listOf(
                    authoritative(
                        species = "Raichu",
                        spriteKey = "026_00_01",
                        variantLabel = "Festive hat costume",
                        eventLabel = "Holiday 2016",
                        eventStart = "2016-12-12",
                        eventEnd = "2017-01-03"
                    )
                )
            )
        )

        val classifierCandidate = candidates.first { it.source == "classifier_species" }
        assertEquals("Pikachu", classifierCandidate.species)
        assertEquals("025_00_01", classifierCandidate.spriteKey)

        val fallbackCandidate = candidates.first { it.source == "authoritative_species_date" }
        assertEquals("Raichu", fallbackCandidate.species)
        assertEquals("Holiday 2016", fallbackCandidate.eventLabel)
        assertTrue(fallbackCandidate.isCostumeLike)
    }

    @Test
    fun blocksAuthoritativeDateCandidateWhenCaughtDateIsOutsideEventWindow() {
        val pokemon = PokemonData(
            cp = 635,
            hp = null,
            maxHp = null,
            name = "Raichu",
            realName = "Raichu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2017-01-11"),
            rawOcrText = ""
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Raichu",
            globalMatch = null,
            speciesMatch = null,
            authoritativeBySpecies = mapOf(
                "Raichu" to listOf(
                    authoritative(
                        species = "Raichu",
                        spriteKey = "026_00_01",
                        variantLabel = "Festive hat costume",
                        eventLabel = "Holiday 2016",
                        eventStart = "2016-12-12",
                        eventEnd = "2017-01-03"
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "authoritative_species_date" })
    }

    @Test
    fun rejectsClassifierSpeciesCandidateWhenCaughtDateIsAfterAuthoritativeEventWindow() {
        val pokemon = PokemonData(
            cp = 382,
            hp = null,
            maxHp = null,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2024-01-07"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Pikachu",
            spriteKey = "025_00_12",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.46f,
            score = 0.35f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Pikachu",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Pikachu" to listOf(
                    AuthoritativeVariantEntry(
                        species = "Pikachu",
                        dex = 25,
                        formId = "00",
                        variantId = "12",
                        spriteKey = "025_00_12",
                        variantClass = "costume",
                        isShiny = false,
                        isCostumeLike = true,
                        variantLabel = "World Championships costume",
                        eventLabel = "2022 World Championships Celebration",
                        eventStart = "2022-08-18",
                        eventEnd = "2022-08-23"
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "classifier_species" && it.spriteKey == "025_00_12" })
        assertFalse(candidates.any { it.source == "classifier_species_authoritative_remap" && it.spriteKey == "025_00_12" })
        assertFalse(candidates.any { it.source == "authoritative_species_date" && it.spriteKey == "025_00_12" })
    }

    @Test
    fun rejectsClassifierSpeciesCandidateWhenLocalSpriteYearDoesNotMatchCaughtYear() {
        val pokemon = PokemonData(
            cp = 1039,
            hp = null,
            maxHp = null,
            name = "Blastoise",
            realName = "Blastoise",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2018-08-07"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Squirtle",
            spriteKey = "007_00_CSPRING_2020_NOEVOLVE",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.52f,
            score = 0.35f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Blastoise",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = emptyMap()
        )

        assertFalse(candidates.any { it.source == "classifier_species" && it.spriteKey == "007_00_CSPRING_2020_NOEVOLVE" })
    }

    @Test
    fun rejectsClassifierSpeciesCandidateWhenTokenOnlyYearIsFarAfterCaughtDate() {
        val pokemon = PokemonData(
            cp = 514,
            hp = null,
            maxHp = null,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2022-02-27"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Pikachu",
            spriteKey = "025_00_DIWALI_2024",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.456f,
            score = 0.348f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Pikachu",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = emptyMap()
        )

        assertFalse(candidates.any { it.source == "classifier_species" && it.spriteKey == "025_00_DIWALI_2024" })
    }

    @Test
    fun addsSpeciesSecondaryNonBaseCandidateWhenClassifierCarriesCloseCostumeAlternative() {
        val pokemon = PokemonData(
            cp = 685,
            hp = null,
            maxHp = null,
            name = "Slowpoke",
            realName = "Slowpoke",
            candyName = "Slowpoke",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2021-09-01"),
            rawOcrText = ""
        )

        val speciesMatch = VariantPrototypeClassifier.MatchResult(
            species = "Slowpoke",
            assetKey = "079_00",
            spriteKey = "079_00_shiny",
            variantType = "base",
            isShiny = true,
            isCostumeLike = false,
            scope = "species",
            score = 0.437f,
            confidence = 0.395f,
            speciesMargin = 0.0f,
            variantMargin = 0.0f,
            bestNonBaseScore = 0.541f,
            bestNonBaseSpecies = "Slowpoke",
            bestNonBaseAssetKey = "079_31",
            bestNonBaseSpriteKey = "079_31",
            bestNonBaseVariantType = "costume",
            bestNonBaseIsShiny = false,
            bestNonBaseIsCostumeLike = true,
            topSpecies = emptyList()
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Slowpoke",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = emptyMap()
        )

        val secondary = candidates.first { it.source == "classifier_species_secondary_non_base" }
        assertEquals("Slowpoke", secondary.species)
        assertEquals("079_31", secondary.spriteKey)
        assertEquals("costume", secondary.variantClass)
        assertTrue(secondary.isCostumeLike)
    }

    @Test
    fun weakSecondaryNonBaseSignalDoesNotAddAuthoritativeDateRescueCandidate() {
        val pokemon = PokemonData(
            cp = 685,
            hp = null,
            maxHp = null,
            name = "Slowpoke",
            realName = "Slowpoke",
            candyName = "Slowpoke",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2021-09-01"),
            rawOcrText = ""
        )

        val speciesMatch = VariantPrototypeClassifier.MatchResult(
            species = "Slowpoke",
            assetKey = "079_00",
            spriteKey = "079_00_shiny",
            variantType = "base",
            isShiny = true,
            isCostumeLike = false,
            scope = "species",
            score = 0.437f,
            confidence = 0.395f,
            speciesMargin = 0.0f,
            variantMargin = 0.0f,
            bestNonBaseScore = 0.541f,
            bestNonBaseSpecies = "Slowpoke",
            bestNonBaseAssetKey = "079_31",
            bestNonBaseSpriteKey = "079_31",
            bestNonBaseVariantType = "costume",
            bestNonBaseIsShiny = false,
            bestNonBaseIsCostumeLike = true,
            topSpecies = emptyList()
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Slowpoke",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Slowpoke" to listOf(
                    authoritative(
                        species = "Slowpoke",
                        spriteKey = "080_51",
                        variantLabel = "2021 glasses costume",
                        eventLabel = "Fashion Week 2021",
                        eventStart = "2021-09-21",
                        eventEnd = "2021-09-28",
                        historicalEvents = listOf(
                            HistoricalEventAppearance("Slowpoke 2021", "2021-08-20", "2021-09-05")
                        )
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "authoritative_species_date" })
    }

    @Test
    fun doesNotAddSecondaryNonBaseCandidateWhenPrimarySpeciesMatchIsAlreadyNonBase() {
        val pokemon = PokemonData(
            cp = 269,
            hp = null,
            maxHp = null,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = "Pikachu",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2025-10-27"),
            rawOcrText = ""
        )

        val speciesMatch = VariantPrototypeClassifier.MatchResult(
            species = "Pikachu",
            assetKey = "025_00_23",
            spriteKey = "025_00_23",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            scope = "species",
            score = 0.379f,
            confidence = 0.52f,
            speciesMargin = 0.0f,
            variantMargin = 0.005f,
            bestNonBaseScore = 0.379f,
            bestNonBaseSpecies = "Pikachu",
            bestNonBaseAssetKey = "025_00_23",
            bestNonBaseSpriteKey = "025_00_23",
            bestNonBaseVariantType = "costume",
            bestNonBaseIsShiny = false,
            bestNonBaseIsCostumeLike = true,
            topSpecies = emptyList()
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Pikachu",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = emptyMap()
        )

        assertFalse(candidates.any { it.source == "classifier_species_secondary_non_base" })
    }

    @Test
    fun dropsClassifierCostumeCandidateWhenCaughtDatePredatesKnownEventStart() {
        val pokemon = PokemonData(
            cp = 3093,
            hp = 227,
            maxHp = 227,
            name = "Vaporeon",
            realName = "Vaporeon",
            candyName = "Eevee",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2017-11-02"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Vaporeon",
            spriteKey = "134_00_CNOVEMBER_2018",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.52f,
            score = 0.415f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Vaporeon",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Vaporeon" to listOf(
                    authoritative(
                        species = "Vaporeon",
                        spriteKey = "134_00_CNOVEMBER_2018",
                        variantLabel = "November 2018 costume",
                        eventLabel = "November 2018",
                        eventStart = "2018-11-01",
                        eventEnd = "2018-11-30"
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "classifier_species" })
    }

    @Test
    fun dropsAuthoritativeRemapCandidateWhenCaughtDateMatchesNoHistoricalAppearance() {
        val pokemon = PokemonData(
            cp = 500,
            hp = 100,
            maxHp = 100,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = "Pikachu",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2021-10-09"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Pikachu",
            spriteKey = "025_00_NOVEMBER_2018",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.52f,
            score = 0.42f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Pikachu",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Pikachu" to listOf(
                    authoritative(
                        species = "Pikachu",
                        spriteKey = "025_00_NOVEMBER_2018",
                        variantLabel = "Flower crown costume",
                        eventLabel = "Spring into Spring",
                        eventStart = "2025-04-09",
                        eventEnd = "2025-04-14",
                        historicalEvents = listOf(
                            HistoricalEventAppearance("Pokémon Day 2019", "2019-02-26", "2019-02-28"),
                            HistoricalEventAppearance("Spring 2021", "2021-04-04", "2021-04-08")
                        )
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "classifier_species_authoritative_remap" })
    }

    @Test
    fun dropsClassifierCostumeCandidateWhenOnlyYearHintMakesCatchImpossible() {
        val pokemon = PokemonData(
            cp = 2793,
            hp = 253,
            maxHp = 253,
            name = "Snorlax",
            realName = "Snorlax",
            candyName = "Snorlax",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2017-12-09"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Snorlax",
            spriteKey = "143_00_GOFEST_2022_NOEVOLVE",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.556f,
            score = 0.399f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Snorlax",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Snorlax" to listOf(
                    authoritative(
                        species = "Snorlax",
                        spriteKey = "143_00_GOFEST_2022_NOEVOLVE",
                        variantLabel = "Wildarea 2024 costume",
                        eventLabel = "Snorlax Wildarea 2024",
                        eventStart = null,
                        eventEnd = null
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "classifier_species" })
    }

    @Test
    fun dropsClassifierCostumeCandidateWhenSpriteKeyYearPredatesCatch() {
        val pokemon = PokemonData(
            cp = 3093,
            hp = 227,
            maxHp = 227,
            name = "Vaporeon",
            realName = "Vaporeon",
            candyName = "Eevee",
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2017-11-02"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Vaporeon",
            spriteKey = "134_00_CNOVEMBER_2018",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.52f,
            score = 0.415f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Vaporeon",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = emptyMap()
        )

        assertFalse(candidates.any { it.source == "classifier_species" })
    }

    @Test
    fun addsActiveLiveSpeciesEventSupportWhenSpeciesHasNoNonBaseAsset() {
        val pokemon = PokemonData(
            cp = 647,
            hp = 72,
            maxHp = 72,
            name = "Absol",
            realName = "Absol",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Absol",
            spriteKey = "359_00_FALL_2022_NOEVOLVE",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.78f,
            score = 0.41f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Absol",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = emptyMap(),
            globalLegacyBySpecies = mapOf(
                "Absol" to listOf(
                    GlobalRarityLegacyEntry(
                        species = "Absol",
                        dex = 359,
                        formId = "00",
                        spriteKey = "359_00",
                        variantClass = "base",
                        isShiny = false,
                        isCostumeLike = false,
                        activeEventLabel = "Fashion Raid Day",
                        activeEventStart = "2026-04-04",
                        activeEventEnd = "2026-04-04"
                    )
                )
            )
        )

        val liveSupport = candidates.first { it.source == "authoritative_live_species_event" }
        assertEquals("Absol", liveSupport.species)
        assertEquals("Fashion Raid Day", liveSupport.eventLabel)
        assertTrue(liveSupport.isCostumeLike)
        assertEquals("costume", liveSupport.variantClass)
    }

    @Test
    fun keepsClassifierCostumeCandidateWhenCatchMatchesHistoricalAppearance() {
        val pokemon = PokemonData(
            cp = 621,
            hp = 76,
            maxHp = 76,
            name = "Hoothoot",
            realName = "Hoothoot",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2023-01-01"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Hoothoot",
            spriteKey = "163_00_JAN_2022_NOEVOLVE_shiny",
            variantType = "costume",
            isShiny = true,
            isCostumeLike = true,
            confidence = 0.4303f,
            score = 0.435f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Hoothoot",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Hoothoot" to listOf(
                    authoritative(
                        species = "Hoothoot",
                        spriteKey = "163_00_JAN_2022_NOEVOLVE_shiny",
                        variantLabel = "New Year's outfit costume",
                        eventLabel = "New Year's 2026",
                        eventStart = "2025-12-31",
                        eventEnd = "2026-01-04",
                        historicalEvents = listOf(
                            HistoricalEventAppearance("New Year's 2023", "2022-12-31", "2023-01-04"),
                            HistoricalEventAppearance("New Year's 2022", "2021-12-31", "2022-01-04")
                        )
                    )
                )
            )
        )

        assertTrue(candidates.any { it.source == "classifier_species" && it.spriteKey == "163_00_JAN_2022_NOEVOLVE_shiny" })
        val dateCandidate = candidates.first { it.source == "authoritative_species_date" }
        assertEquals("New Year's 2023", dateCandidate.eventLabel)
    }

    @Test
    fun authoritativeSemanticsOverrideTurnsFormSpriteIntoCostumeCandidate() {
        val pokemon = PokemonData(
            cp = 777,
            hp = 99,
            maxHp = 99,
            name = "Cubone",
            realName = "Cubone",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2023-11-01"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Cubone",
            spriteKey = "104_00_FALL_2023",
            variantType = "form",
            isShiny = false,
            isCostumeLike = false,
            confidence = 0.398f,
            score = 0.433f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Cubone",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Cubone" to listOf(
                    AuthoritativeVariantEntry(
                        species = "Cubone",
                        dex = 104,
                        formId = "00",
                        variantId = "FALL_2023",
                        spriteKey = "104_00_FALL_2023",
                        variantClass = "costume",
                        isShiny = false,
                        isCostumeLike = true,
                        variantLabel = "Cempasúchil crown costume",
                        eventLabel = "Día de Muertos 2023",
                        eventStart = "2023-11-01",
                        eventEnd = "2023-11-02"
                    )
                )
            )
        )

        val classifierCandidate = candidates.first { it.source == "classifier_species" }
        assertEquals("costume", classifierCandidate.variantClass)
        assertTrue(classifierCandidate.isCostumeLike)
    }

    @Test
    fun addsFamilyAuthoritativeRemapCandidateWhenClassifierChoosesSiblingVariantToken() {
        val pokemon = PokemonData(
            cp = 201,
            hp = null,
            maxHp = null,
            name = "Cottonee",
            realName = "Cottonee",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2024-03-21"),
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Whimsicott",
            spriteKey = "547_00_CSPRING_2024_shiny",
            variantType = "form",
            isShiny = true,
            isCostumeLike = false,
            confidence = 0.4005f,
            score = 0.504f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Cottonee",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Cottonee" to listOf(
                    AuthoritativeVariantEntry(
                        species = "Cottonee",
                        dex = 546,
                        formId = "00",
                        variantId = "SPRING_2024",
                        spriteKey = "546_00_SPRING_2024_shiny",
                        variantClass = "costume",
                        isShiny = true,
                        isCostumeLike = true,
                        variantLabel = "Spring 2024 flower crown costume",
                        eventLabel = "Spring into Spring 2024",
                        eventStart = "2024-03-19",
                        eventEnd = "2024-03-25"
                    )
                )
            )
        )

        val remap = candidates.first { it.source == "classifier_family_authoritative_remap" }
        assertEquals("Cottonee", remap.species)
        assertEquals("546_00_SPRING_2024_shiny", remap.spriteKey)
        assertTrue(remap.isCostumeLike)
        assertTrue(remap.isShiny)
        assertEquals("Spring into Spring 2024", remap.eventLabel)
    }

    @Test
    fun addsSpeciesAuthoritativeRemapCandidateWhenClassifierSecondaryUsesLocalCToken() {
        val pokemon = PokemonData(
            cp = 399,
            hp = null,
            maxHp = null,
            name = "Slakoth",
            realName = "Slakoth",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = iso.parse("2024-08-06"),
            rawOcrText = ""
        )

        val speciesMatch = VariantPrototypeClassifier.MatchResult(
            species = "Slakoth",
            assetKey = "287_00",
            spriteKey = "287_00",
            variantType = "base",
            isShiny = false,
            isCostumeLike = false,
            scope = "species",
            score = 0.459f,
            confidence = 0.431f,
            speciesMargin = 0.0f,
            variantMargin = 0.0f,
            bestNonBaseScore = 0.480f,
            bestNonBaseSpecies = "Slakoth",
            bestNonBaseAssetKey = "287_00_CSUMMER_2024",
            bestNonBaseSpriteKey = "287_00_CSUMMER_2024",
            bestNonBaseVariantType = "costume",
            bestNonBaseIsShiny = false,
            bestNonBaseIsCostumeLike = true,
            topSpecies = emptyList()
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Slakoth",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Slakoth" to listOf(
                    AuthoritativeVariantEntry(
                        species = "Slakoth",
                        dex = 287,
                        formId = "00",
                        variantId = "SUMMER_2024",
                        spriteKey = "287_00_SUMMER_2024",
                        variantClass = "costume",
                        isShiny = false,
                        isCostumeLike = true,
                        variantLabel = "Summer 2024 visor costume",
                        eventLabel = "Scorching Steps 2024",
                        eventStart = "2024-08-02",
                        eventEnd = "2024-08-12"
                    )
                )
            )
        )

        val remap = candidates.first { it.source == "classifier_species_secondary_authoritative_remap" }
        assertEquals("Slakoth", remap.species)
        assertEquals("287_00_SUMMER_2024", remap.spriteKey)
        assertTrue(remap.isCostumeLike)
        assertEquals("Scorching Steps 2024", remap.eventLabel)
    }

    @Test
    fun doesNotAddActiveLiveSpeciesEventWithoutSameSpeciesNonBaseClassifierSupport() {
        val pokemon = PokemonData(
            cp = 1796,
            hp = 118,
            maxHp = 118,
            name = "Absol",
            realName = "Absol",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = ""
        )

        val speciesMatch = VariantPrototypeClassifier.MatchResult(
            species = "Absol",
            assetKey = "359_00",
            spriteKey = "359_00",
            variantType = "base",
            isShiny = false,
            isCostumeLike = false,
            scope = "species",
            score = 0.419f,
            confidence = 0.454f,
            speciesMargin = 0.0f,
            variantMargin = 0.019f,
            topSpecies = emptyList()
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Absol",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = emptyMap(),
            globalLegacyBySpecies = mapOf(
                "Absol" to listOf(
                    GlobalRarityLegacyEntry(
                        species = "Absol",
                        dex = 359,
                        formId = "00",
                        spriteKey = "359_00_FALL_2022_NOEVOLVE",
                        variantClass = "costume",
                        isShiny = false,
                        isCostumeLike = true,
                        activeEventLabel = "Fashion Raid Day",
                        activeEventStart = "2026-04-01",
                        activeEventEnd = "2026-04-10"
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "authoritative_live_species_event" })
    }

    @Test
    fun dropsWeakGlobalAuthoritativeRemapWhenCaughtDateMissing() {
        val pokemon = PokemonData(
            cp = 666,
            hp = 89,
            maxHp = 89,
            name = "Dratini",
            realName = "Dratini",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = ""
        )

        val globalMatch = match(
            species = "Dratini",
            spriteKey = "147_01",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.53f,
            score = 0.59f,
            scope = "global"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Dratini",
            globalMatch = globalMatch,
            speciesMatch = null,
            authoritativeBySpecies = mapOf(
                "Dratini" to listOf(
                    AuthoritativeVariantEntry(
                        species = "Dratini",
                        dex = 147,
                        formId = "01",
                        variantId = "COSTUME",
                        spriteKey = "147_01",
                        variantClass = "costume",
                        isShiny = false,
                        isCostumeLike = true,
                        variantLabel = "Costume",
                        eventLabel = "Community Day Classic: Dratini",
                        eventStart = "2024-11-10",
                        eventEnd = "2024-11-10"
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "classifier_global_authoritative_remap" && it.spriteKey == "147_01" })
    }

    @Test
    fun weakSameSpeciesNonBaseSignalDoesNotCreateActiveLiveEventCandidate() {
        val pokemon = PokemonData(
            cp = 441,
            hp = 62,
            maxHp = 62,
            name = "Torchic",
            realName = "Torchic",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Torchic",
            spriteKey = "255_00_EVENT",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.52f,
            score = 0.44f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Torchic",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = emptyMap(),
            globalLegacyBySpecies = mapOf(
                "Torchic" to listOf(
                    GlobalRarityLegacyEntry(
                        species = "Torchic",
                        dex = 255,
                        formId = "00",
                        spriteKey = "255_00",
                        variantClass = "base",
                        isShiny = false,
                        isCostumeLike = false,
                        activeEventLabel = "Spring Celebration",
                        activeEventStart = "2026-04-14",
                        activeEventEnd = "2026-04-16"
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "authoritative_live_species_event" })
    }

    @Test
    fun weakSameSpeciesAuthoritativeRemapWithoutCaughtDateIsDropped() {
        val pokemon = PokemonData(
            cp = 511,
            hp = 85,
            maxHp = 85,
            name = "Pikachu",
            realName = "Pikachu",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = 1.0f,
            caughtDate = null,
            rawOcrText = ""
        )

        val speciesMatch = match(
            species = "Pikachu",
            spriteKey = "025_00_FPOPSTAR",
            variantType = "costume",
            isShiny = false,
            isCostumeLike = true,
            confidence = 0.52f,
            score = 0.48f,
            scope = "species"
        )

        val candidates = FullVariantCandidateBuilder.build(
            pokemon = pokemon,
            finalSpecies = "Pikachu",
            globalMatch = null,
            speciesMatch = speciesMatch,
            authoritativeBySpecies = mapOf(
                "Pikachu" to listOf(
                    AuthoritativeVariantEntry(
                        species = "Pikachu",
                        dex = 25,
                        formId = "00",
                        variantId = "POPSTAR",
                        spriteKey = "025_00_POPSTAR",
                        variantClass = "costume",
                        isShiny = false,
                        isCostumeLike = true,
                        variantLabel = "Pop Star costume",
                        eventLabel = null,
                        eventStart = null,
                        eventEnd = null
                    )
                )
            )
        )

        assertFalse(candidates.any { it.source == "classifier_species_authoritative_remap" && it.spriteKey == "025_00_POPSTAR" })
    }

    private fun match(
        species: String,
        spriteKey: String,
        variantType: String,
        isShiny: Boolean,
        isCostumeLike: Boolean,
        confidence: Float,
        score: Float = 0.35f,
        scope: String
    ) = VariantPrototypeClassifier.MatchResult(
        species = species,
        assetKey = spriteKey.removeSuffix("_shiny"),
        spriteKey = spriteKey,
        variantType = variantType,
        isShiny = isShiny,
        isCostumeLike = isCostumeLike,
        scope = scope,
        score = score,
        confidence = confidence,
        speciesMargin = 0.1f,
        variantMargin = 0.05f,
        topSpecies = emptyList()
    )

    private fun authoritative(
        species: String,
        spriteKey: String,
        variantLabel: String,
        eventLabel: String,
        eventStart: String?,
        eventEnd: String?,
        historicalEvents: List<HistoricalEventAppearance> = emptyList()
    ) = AuthoritativeVariantEntry(
        species = species,
        dex = 26,
        formId = "00",
        variantId = "01",
        spriteKey = spriteKey,
        variantClass = "costume",
        isShiny = false,
        isCostumeLike = true,
        variantLabel = variantLabel,
        eventLabel = eventLabel,
        eventStart = eventStart,
        eventEnd = eventEnd,
        historicalEvents = historicalEvents
    )
}
