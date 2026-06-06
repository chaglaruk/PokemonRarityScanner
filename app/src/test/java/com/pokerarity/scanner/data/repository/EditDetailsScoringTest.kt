package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.CollectionTier
import com.pokerarity.scanner.data.model.EditedScanDetails
import com.pokerarity.scanner.data.model.EventMatchLevel
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.Rarity
import com.pokerarity.scanner.data.model.RarityAnalysisItem
import com.pokerarity.scanner.data.model.ScoreAxis
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.model.catalog.CatalogVersion
import com.pokerarity.scanner.data.model.catalog.CollectionCatalog
import com.pokerarity.scanner.data.model.catalog.CostumeRecord
import com.pokerarity.scanner.data.model.catalog.EventRecord
import com.pokerarity.scanner.data.model.catalog.RegionalRecord
import com.pokerarity.scanner.data.model.catalog.SpecialSpeciesRecord
import com.pokerarity.scanner.data.model.catalog.VerificationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditDetailsScoringTest {

    private val engine = CollectionScoreEngine(
        speciesRarityLookup = { 0 },
        currentDateProvider = { date("2026-06-05") }
    )

    @Test
    fun manualReviewRecordsAreNotActiveCatalogChoices() {
        val options = EditDetailsScoring.catalogOptionsFor(catalog(), "Pikachu")

        val activeIds = options.all.map { it.id }
        assertTrue("pikachu_party_hat_2017" in activeIds)
        assertTrue("pokemon_day_2017" in activeIds)
        assertTrue("iconic_common" in activeIds)
        assertTrue("Pikachu" in activeIds)
        assertFalse("manual_costume" in activeIds)
        assertFalse("manual_event" in activeIds)
        assertFalse("manual_review" in activeIds)
        assertFalse("unknown_costume" in activeIds)
        assertFalse("unknown_event" in activeIds)
        assertFalse("unknown_status" in activeIds)
    }

    @Test
    fun selectedManualReviewRecordIsDroppedBeforeScoring() {
        val preview = EditDetailsScoring.preview(
            basePokemon = pokemon("Pidgey"),
            editedDetails = EditedScanDetails(
                species = "Pidgey",
                costumeId = "manual_costume",
                eventId = "manual_event",
                specialStatusOverride = "manual_review"
            ),
            catalog = catalog(
                costumes = listOf(costume("manual_costume", status = VerificationStatus.MANUAL_REVIEW_NEEDED)),
                events = listOf(event("manual_event", status = VerificationStatus.MANUAL_REVIEW_NEEDED)),
                specialSpecies = listOf(special("Pidgey", "manual_review", 20, VerificationStatus.MANUAL_REVIEW_NEEDED)),
                regionals = emptyList()
            ),
            engine = engine,
            currentDate = date("2026-06-05")
        )

        assertNull(preview.editedDetails.costumeId)
        assertNull(preview.editedDetails.eventId)
        assertNull(preview.editedDetails.specialStatusOverride)
        assertEquals(0, preview.result.totalScore)
        assertEquals(CollectionTier.COMMON, preview.result.tier)
    }

    @Test
    fun selectedEventOutsideCaughtDateWarnsAndDoesNotGrantEventProvenance() {
        val preview = EditDetailsScoring.preview(
            basePokemon = pokemon("Pikachu"),
            editedDetails = EditedScanDetails(
                species = "Pikachu",
                costumeId = "pikachu_party_hat_2017",
                eventId = "pokemon_day_2017",
                caughtDate = date("2020-01-01")
            ),
            catalog = catalog(regionals = emptyList()),
            engine = engine,
            currentDate = date("2026-06-05")
        )

        assertEquals(EventMatchLevel.NONE, preview.result.eventMatchLevel)
        assertEquals(0, preview.result.axis(ScoreAxis.COLLECTOR_CONTEXT).score)
        assertTrue(
            preview.pokemon.collectionDetails.any {
                it.title == "Event date mismatch" && it.isPositive.not()
            }
        )
    }

    @Test
    fun editedDetailsDoNotApplyToFutureScans() {
        val catalog = catalog(regionals = emptyList())
        val edited = EditDetailsScoring.preview(
            basePokemon = pokemon("Pikachu"),
            editedDetails = EditedScanDetails(
                species = "Pikachu",
                costumeId = "pikachu_party_hat_2017",
                isShiny = true
            ),
            catalog = catalog,
            engine = engine,
            currentDate = date("2026-06-05")
        )
        val futureUnedited = engine.calculate(
            pokemonData = com.pokerarity.scanner.data.model.PokemonData(
                cp = 500,
                hp = 50,
                maxHp = 50,
                name = "Pikachu",
                realName = "Pikachu",
                candyName = "Pikachu Candy",
                megaEnergy = null,
                weight = null,
                height = null,
                gender = null,
                stardust = null,
                caughtDate = null,
                rawOcrText = ""
            ),
            features = VisualFeatures(),
            catalog = catalog
        )

        assertTrue(edited.result.isEdited)
        assertTrue(edited.result.axis(ScoreAxis.VARIANT).score > 0)
        assertFalse(futureUnedited.isEdited)
        assertEquals(0, futureUnedited.axis(ScoreAxis.VARIANT).score)
    }

    private fun catalog(
        costumes: List<CostumeRecord> = listOf(
            costume("pikachu_party_hat_2017"),
            costume("manual_costume", status = VerificationStatus.MANUAL_REVIEW_NEEDED),
            costume("unknown_costume", status = VerificationStatus.UNKNOWN)
        ),
        events: List<EventRecord> = listOf(
            event("pokemon_day_2017"),
            event("manual_event", status = VerificationStatus.MANUAL_REVIEW_NEEDED),
            event("unknown_event", status = VerificationStatus.UNKNOWN)
        ),
        specialSpecies: List<SpecialSpeciesRecord> = listOf(
            special("Pikachu", "iconic_common", 3),
            special("Pikachu", "manual_review", 20, VerificationStatus.MANUAL_REVIEW_NEEDED),
            special("Pikachu", "unknown_status", 20, VerificationStatus.UNKNOWN)
        ),
        regionals: List<RegionalRecord> = listOf(
            regional("Pikachu"),
            regional("Pikachu", region = "Manual", status = VerificationStatus.MANUAL_REVIEW_NEEDED),
            regional("Pikachu", region = "Unknown", status = VerificationStatus.UNKNOWN)
        )
    ) = CollectionCatalog(
        version = CatalogVersion("test-catalog", "2026-06-05T00:00:00Z", 1),
        costumes = costumes,
        events = events,
        regionals = regionals,
        specialSpecies = specialSpecies,
        currentAvailability = emptyList(),
        metaDemand = emptyList()
    )

    private fun pokemon(name: String) = Pokemon(
        id = 0,
        sourceId = 0,
        name = name,
        cp = 500,
        hp = 50,
        rarityScore = 0,
        rarity = Rarity.COMMON,
        rarityTierCode = CollectionTier.COMMON.name,
        collectionScore = 0,
        collectionTierCode = CollectionTier.COMMON.name,
        type = "normal",
        displayDate = "Unknown",
        caughtDate = "Unknown",
        tags = emptyList(),
        analysis = listOf(RarityAnalysisItem("No score", null, false))
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
        sourceLinks = if (status == VerificationStatus.MANUAL_REVIEW_NEEDED) {
            emptyList()
        } else {
            listOf("https://pokemongolive.com/post/pokemonday2017/?hl=en")
        },
        verificationStatus = status
    )

    private fun event(
        id: String,
        status: VerificationStatus = VerificationStatus.VERIFIED_OFFICIAL
    ) = EventRecord(
        id = id,
        name = if (id == "pokemon_day_2017") "Pokemon Day 2017" else "Manual Event",
        eventType = "global",
        startDate = "2017-02-26",
        endDate = "2017-03-06",
        costumeIds = listOf("pikachu_party_hat_2017"),
        featuredSpecies = listOf("Pikachu"),
        isFirstRelease = true,
        sourceLinks = if (status == VerificationStatus.MANUAL_REVIEW_NEEDED) {
            emptyList()
        } else {
            listOf("https://pokemongolive.com/post/pokemonday2017/?hl=en")
        },
        verificationStatus = status
    )

    private fun regional(
        species: String,
        region: String = "Kanto",
        status: VerificationStatus = VerificationStatus.VERIFIED_COMMUNITY
    ) = RegionalRecord(
        species = species,
        region = region,
        isCurrentlyLocked = false,
        globalEventAccess = emptyList(),
        sourceLinks = if (status == VerificationStatus.MANUAL_REVIEW_NEEDED) {
            emptyList()
        } else {
            listOf("https://www.serebii.net/pokemongo/pokemon/025.shtml")
        },
        verificationStatus = status
    )

    private fun special(
        species: String,
        category: String,
        score: Int,
        status: VerificationStatus = VerificationStatus.VERIFIED_COMMUNITY
    ) = SpecialSpeciesRecord(
        species = species,
        category = category,
        baseSpeciesScore = score,
        isTradable = true,
        sourceLinks = if (status == VerificationStatus.MANUAL_REVIEW_NEEDED) {
            emptyList()
        } else {
            listOf("https://bulbapedia.bulbagarden.net/wiki/Pikachu_(Pokemon)")
        },
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
