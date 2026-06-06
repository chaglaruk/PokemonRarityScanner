package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.EditedScanDetails
import com.pokerarity.scanner.data.model.EventMatchLevel
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.VisualFeatures
import com.pokerarity.scanner.data.model.catalog.CatalogVersion
import com.pokerarity.scanner.data.model.catalog.CollectionCatalog
import com.pokerarity.scanner.data.model.catalog.CostumeRecord
import com.pokerarity.scanner.data.model.catalog.EventRecord
import com.pokerarity.scanner.data.model.catalog.VerificationStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventMatchingTest {

    private val engine = CollectionScoreEngine(speciesRarityLookup = { 0 })

    @Test
    fun caughtDateInsideEventWindowIsExactMatch() {
        val result = engine.calculate(
            pokemonData = pokemon(caughtDate = date("2017-02-27")),
            features = VisualFeatures(),
            catalog = catalog(),
            editedDetails = EditedScanDetails(costumeId = "party_hat")
        )

        assertEquals(EventMatchLevel.EXACT, result.eventMatchLevel)
        assertEquals("Pokemon Day 2017", result.eventName)
        assertNull(result.eventDateMismatchMessage)
    }

    @Test
    fun missingCaughtDateIsPossibleButNotExact() {
        val result = engine.calculate(
            pokemonData = pokemon(caughtDate = null),
            features = VisualFeatures(),
            catalog = catalog(),
            editedDetails = EditedScanDetails(costumeId = "party_hat")
        )

        assertEquals(EventMatchLevel.POSSIBLE, result.eventMatchLevel)
        assertEquals("Pokemon Day 2017", result.eventName)
    }

    @Test
    fun manualReviewEventDoesNotMatch() {
        val result = engine.calculate(
            pokemonData = pokemon(caughtDate = date("2017-02-27")),
            features = VisualFeatures(),
            catalog = catalog(eventStatus = VerificationStatus.MANUAL_REVIEW_NEEDED),
            editedDetails = EditedScanDetails(costumeId = "party_hat")
        )

        assertEquals(EventMatchLevel.NONE, result.eventMatchLevel)
        assertNull(result.eventName)
    }

    private fun catalog(
        eventStatus: VerificationStatus = VerificationStatus.VERIFIED_OFFICIAL
    ) = CollectionCatalog(
        version = CatalogVersion("test", "2026-06-05T00:00:00Z", 1),
        costumes = listOf(
            CostumeRecord(
                id = "party_hat",
                species = "Pikachu",
                costumeName = "Party Hat",
                costumeType = "retired",
                eventIds = listOf("pokemon_day_2017"),
                sourceLinks = listOf("https://example.com/source"),
                verificationStatus = VerificationStatus.VERIFIED_OFFICIAL
            )
        ),
        events = listOf(
            EventRecord(
                id = "pokemon_day_2017",
                name = "Pokemon Day 2017",
                eventType = "global",
                startDate = "2017-02-26",
                endDate = "2017-03-06",
                costumeIds = listOf("party_hat"),
                featuredSpecies = listOf("Pikachu"),
                isFirstRelease = true,
                sourceLinks = listOf("https://example.com/source"),
                verificationStatus = eventStatus
            )
        ),
        regionals = emptyList(),
        specialSpecies = emptyList(),
        currentAvailability = emptyList(),
        metaDemand = emptyList()
    )

    private fun pokemon(caughtDate: Date?) = PokemonData(
        cp = null,
        hp = null,
        maxHp = null,
        name = "Pikachu",
        realName = "Pikachu",
        candyName = null,
        megaEnergy = null,
        weight = null,
        height = null,
        gender = null,
        stardust = null,
        caughtDate = caughtDate,
        rawOcrText = ""
    )

    private fun date(value: String): Date =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }.parse(value)!!
}
