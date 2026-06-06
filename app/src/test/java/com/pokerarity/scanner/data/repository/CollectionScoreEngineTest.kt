package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.CollectionTier
import com.pokerarity.scanner.data.model.EditedScanDetails
import com.pokerarity.scanner.data.model.EventMatchLevel
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.ScoreAxis
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.model.catalog.CatalogVersion
import com.pokerarity.scanner.data.model.catalog.CollectionCatalog
import com.pokerarity.scanner.data.model.catalog.CostumeRecord
import com.pokerarity.scanner.data.model.catalog.CurrentAvailabilityRecord
import com.pokerarity.scanner.data.model.catalog.EventRecord
import com.pokerarity.scanner.data.model.catalog.MetaDemandRecord
import com.pokerarity.scanner.data.model.catalog.SpecialSpeciesRecord
import com.pokerarity.scanner.data.model.catalog.VerificationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionScoreEngineTest {

    private val engine = CollectionScoreEngine(
        speciesRarityLookup = { species ->
            when (species) {
                "Pidgey" -> 1
                "Pikachu" -> 4
                "Mewtwo" -> 25
                else -> 0
            }
        },
        currentDateProvider = { date("2026-06-05") }
    )

    @Test
    fun unknownSpeciesReturnsZeroCommonResult() {
        val result = engine.calculate(
            pokemonData = pokemon("Unknown"),
            features = VisualFeatures(),
            catalog = catalog()
        )

        assertEquals(0, result.totalScore)
        assertEquals(CollectionTier.COMMON, result.tier)
        assertEquals(EventMatchLevel.NONE, result.eventMatchLevel)
        assertTrue(result.axes.all { it.score == 0 })
    }

    @Test
    fun manualReviewRecordsDoNotContributeScoringSignals() {
        val result = engine.calculate(
            pokemonData = pokemon("Pidgey"),
            features = VisualFeatures(),
            catalog = catalog(
                costumes = listOf(
                    costume("manual_party", status = VerificationStatus.MANUAL_REVIEW_NEEDED)
                ),
                availability = listOf(
                    availability("Pidgey", score = 22, status = VerificationStatus.MANUAL_REVIEW_NEEDED)
                ),
                specialSpecies = listOf(
                    special("Pidgey", score = 20, status = VerificationStatus.MANUAL_REVIEW_NEEDED)
                ),
                metaDemand = listOf(
                    meta("Pidgey", score = 8, status = VerificationStatus.MANUAL_REVIEW_NEEDED)
                )
            ),
            editedDetails = EditedScanDetails(
                costumeId = "manual_party",
                specialStatusOverride = "legendary"
            )
        )

        assertEquals(1, result.totalScore)
        assertEquals(0, result.axis(ScoreAxis.VARIANT).score)
        assertEquals(0, result.axis(ScoreAxis.AVAILABILITY).score)
        assertEquals(0, result.axis(ScoreAxis.META).score)
    }

    @Test
    fun editedDetailsOverrideVisualSignalsPerRecord() {
        val result = engine.calculate(
            pokemonData = pokemon("Pikachu"),
            features = VisualFeatures(isShiny = false, isLucky = false, hasLocationCard = false),
            catalog = catalog(),
            editedDetails = EditedScanDetails(
                isShiny = true,
                isLucky = true,
                hasLocationCard = true
            )
        )

        assertTrue(result.isEdited)
        assertTrue(result.axis(ScoreAxis.VARIANT).details.contains("Shiny"))
        assertTrue(result.axis(ScoreAxis.VARIANT).details.contains("Lucky"))
        assertTrue(result.axis(ScoreAxis.VARIANT).details.contains("Location card"))
    }

    @Test
    fun exactEventDateAddsEventContext() {
        val result = engine.calculate(
            pokemonData = pokemon("Pikachu", caughtDate = date("2017-02-27")),
            features = VisualFeatures(isShiny = true),
            catalog = catalog(),
            editedDetails = EditedScanDetails(costumeId = "pikachu_party_hat_2017")
        )

        assertEquals(EventMatchLevel.EXACT, result.eventMatchLevel)
        assertEquals("Pokemon Day 2017", result.eventName)
        assertTrue(result.axis(ScoreAxis.COLLECTOR_CONTEXT).score > 0)
        assertTrue(result.axis(ScoreAxis.VARIANT).details.contains("Party Hat Pikachu"))
    }

    @Test
    fun selectedEventOutsideCaughtDateDoesNotClaimExactContext() {
        val result = engine.calculate(
            pokemonData = pokemon("Pikachu", caughtDate = date("2020-01-01")),
            features = VisualFeatures(),
            catalog = catalog(),
            editedDetails = EditedScanDetails(
                costumeId = "pikachu_party_hat_2017",
                eventId = "pokemon_day_2017"
            )
        )

        assertEquals(EventMatchLevel.NONE, result.eventMatchLevel)
        assertNotNull(result.eventDateMismatchMessage)
        assertEquals(0, result.axis(ScoreAxis.COLLECTOR_CONTEXT).score)
    }

    @Test
    fun currentAvailabilityPenaltyDoesNotApplyToHistoricalDatedCatchWithoutActiveEventWindow() {
        val result = engine.calculate(
            pokemonData = pokemon("Mewtwo", caughtDate = date("2020-01-01")),
            features = VisualFeatures(),
            catalog = catalog(
                specialSpecies = listOf(special("Mewtwo", category = "legendary", score = 18)),
                availability = listOf(
                    availability(
                        species = "Mewtwo",
                        score = 18,
                        currentPenalty = 4,
                        currentPenaltyReason = "Currently available in raids"
                    )
                )
            )
        )

        assertEquals(18, result.axis(ScoreAxis.AVAILABILITY).score)
        assertFalse(result.axis(ScoreAxis.AVAILABILITY).details.contains("Currently available in raids"))
    }

    private fun catalog(
        costumes: List<CostumeRecord> = listOf(costume("pikachu_party_hat_2017")),
        events: List<EventRecord> = listOf(pokemonDay2017()),
        specialSpecies: List<SpecialSpeciesRecord> = listOf(
            special("Pikachu", category = "iconic_common", score = 3),
            special("Mewtwo", category = "legendary", score = 18)
        ),
        availability: List<CurrentAvailabilityRecord> = emptyList(),
        metaDemand: List<MetaDemandRecord> = emptyList()
    ) = CollectionCatalog(
        version = CatalogVersion("test-catalog", "2026-06-05T00:00:00Z", 1),
        costumes = costumes,
        events = events,
        regionals = emptyList(),
        specialSpecies = specialSpecies,
        currentAvailability = availability,
        metaDemand = metaDemand
    )

    private fun pokemon(species: String, caughtDate: Date? = null) = PokemonData(
        cp = 500,
        hp = 50,
        maxHp = 50,
        name = species,
        realName = species,
        candyName = "$species Candy",
        megaEnergy = null,
        weight = null,
        height = null,
        gender = null,
        stardust = null,
        caughtDate = caughtDate,
        rawOcrText = ""
    )

    private fun costume(
        id: String,
        status: VerificationStatus = VerificationStatus.VERIFIED_OFFICIAL
    ) = CostumeRecord(
        id = id,
        species = "Pikachu",
        costumeName = "Party Hat Pikachu",
        costumeType = "retired",
        eventIds = listOf("pokemon_day_2017"),
        sourceLinks = listOf("https://pokemongolive.com/post/pokemonday2017/?hl=en"),
        verificationStatus = status
    )

    private fun pokemonDay2017() = EventRecord(
        id = "pokemon_day_2017",
        name = "Pokemon Day 2017",
        eventType = "global",
        startDate = "2017-02-26",
        endDate = "2017-03-06",
        costumeIds = listOf("pikachu_party_hat_2017"),
        featuredSpecies = listOf("Pikachu"),
        isFirstRelease = true,
        sourceLinks = listOf("https://pokemongolive.com/post/pokemonday2017/?hl=en"),
        verificationStatus = VerificationStatus.VERIFIED_OFFICIAL
    )

    private fun availability(
        species: String,
        score: Int,
        currentPenalty: Int? = null,
        currentPenaltyReason: String? = null,
        status: VerificationStatus = VerificationStatus.VERIFIED_COMMUNITY
    ) = CurrentAvailabilityRecord(
        species = species,
        costumeId = null,
        availabilityType = "raid_only",
        baseAvailabilityScore = score,
        currentPenalty = currentPenalty,
        currentPenaltyReason = currentPenaltyReason,
        activeEventId = null,
        sourceLinks = listOf("https://example.com/catalog-source"),
        verificationStatus = status
    )

    private fun special(
        species: String,
        category: String = "common",
        score: Int,
        status: VerificationStatus = VerificationStatus.VERIFIED_COMMUNITY
    ) = SpecialSpeciesRecord(
        species = species,
        category = category,
        baseSpeciesScore = score,
        isTradable = category != "mythical_one_time",
        sourceLinks = listOf("https://example.com/catalog-source"),
        verificationStatus = status
    )

    private fun meta(
        species: String,
        score: Int,
        status: VerificationStatus = VerificationStatus.VERIFIED_COMMUNITY
    ) = MetaDemandRecord(
        species = species,
        formId = null,
        demandLevel = "strong",
        metaScore = score,
        sourceLinks = listOf("https://example.com/catalog-source"),
        verificationStatus = status
    )

    private fun com.pokerarity.scanner.data.model.CollectionResult.axis(axis: ScoreAxis) =
        axes.first { it.axis == axis }

    private fun date(value: String): Date =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(value)!!
}
