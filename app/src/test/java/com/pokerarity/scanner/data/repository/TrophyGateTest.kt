package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.EventMatchLevel
import com.pokerarity.scanner.data.model.catalog.CostumeRecord
import com.pokerarity.scanner.data.model.catalog.EventRecord
import com.pokerarity.scanner.data.model.catalog.SpecialSpeciesRecord
import com.pokerarity.scanner.data.model.catalog.VerificationStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrophyGateTest {

    @Test
    fun trophyRequiresMinimumScoreAndVerifiedMajorSignal() {
        assertFalse(TrophyGate.qualifies(input(score = 89, matchedEvent = inPersonEvent())))
        assertTrue(TrophyGate.qualifies(input(score = 90, matchedEvent = inPersonEvent())))
    }

    @Test
    fun highScoreWithoutMajorSignalDoesNotQualify() {
        assertFalse(TrophyGate.qualifies(input(score = 95)))
    }

    @Test
    fun shinyRetiredCostumeCountsAsMajorSignal() {
        val signals = TrophyGate.trophySignals(
            input(
                score = 90,
                isShiny = true,
                costume = CostumeRecord(
                    id = "party_hat",
                    species = "Pikachu",
                    costumeName = "Party Hat",
                    costumeType = "retired",
                    eventIds = emptyList(),
                    sourceLinks = listOf("https://example.com/source"),
                    verificationStatus = VerificationStatus.VERIFIED_COMMUNITY
                )
            )
        )

        assertTrue(signals.any { it.contains("retired costume", ignoreCase = true) })
    }

    private fun input(
        score: Int,
        isShiny: Boolean = false,
        matchedEvent: EventRecord? = null,
        costume: CostumeRecord? = null
    ) = TrophyGate.Input(
        totalScore = score,
        isShiny = isShiny,
        hasLocationCard = false,
        isLegendary = false,
        legacyScore = 0,
        eventMatchLevel = if (matchedEvent == null) EventMatchLevel.NONE else EventMatchLevel.EXACT,
        matchedEvent = matchedEvent,
        costumeRecord = costume,
        specialSpecies = SpecialSpeciesRecord(
            species = "Pikachu",
            category = "iconic_common",
            baseSpeciesScore = 3,
            isTradable = true,
            sourceLinks = emptyList(),
            verificationStatus = VerificationStatus.MANUAL_REVIEW_NEEDED
        )
    )

    private fun inPersonEvent() = EventRecord(
        id = "go_fest_2017_chicago",
        name = "Pokemon GO Fest 2017 Chicago",
        eventType = "in_person",
        startDate = "2017-07-22",
        endDate = "2017-07-22",
        costumeIds = emptyList(),
        featuredSpecies = listOf("Pikachu"),
        isFirstRelease = false,
        sourceLinks = listOf("https://example.com/source"),
        verificationStatus = VerificationStatus.VERIFIED_COMMUNITY
    )
}
