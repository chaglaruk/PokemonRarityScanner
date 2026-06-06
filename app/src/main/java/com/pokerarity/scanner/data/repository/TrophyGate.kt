package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.EventMatchLevel
import com.pokerarity.scanner.data.model.catalog.CostumeRecord
import com.pokerarity.scanner.data.model.catalog.EventRecord
import com.pokerarity.scanner.data.model.catalog.SpecialSpeciesRecord

object TrophyGate {
    data class Input(
        val totalScore: Int,
        val isShiny: Boolean,
        val hasLocationCard: Boolean,
        val isLegendary: Boolean,
        val legacyScore: Int,
        val eventMatchLevel: EventMatchLevel,
        val matchedEvent: EventRecord?,
        val costumeRecord: CostumeRecord?,
        val specialSpecies: SpecialSpeciesRecord?
    )

    fun trophySignals(input: Input): List<String> = buildList {
        val matchedEvent = input.matchedEvent
        if (input.eventMatchLevel == EventMatchLevel.EXACT && matchedEvent != null) {
            when (matchedEvent.eventType) {
                "in_person" -> add("verified in-person event provenance")
                "ticket_global" -> add("verified ticket-only event provenance")
            }
            if (input.legacyScore >= 8 && matchedEvent.isFirstRelease) {
                add("very old first-release event catch")
            }
        }

        if (input.isShiny && input.costumeRecord?.costumeType == "retired") {
            add("shiny retired costume")
        }
        if (input.hasLocationCard && input.isLegendary) {
            add("location card legendary")
        }
        if (
            input.specialSpecies?.category == "mythical_one_time" &&
            input.eventMatchLevel == EventMatchLevel.EXACT
        ) {
            add("one-time mythical event provenance")
        }
    }.distinct()

    fun qualifies(input: Input): Boolean =
        input.totalScore >= CollectionScoreConstants.TROPHY_MIN_SCORE &&
            trophySignals(input).isNotEmpty()
}
