package com.pokerarity.scanner.data.repository

import kotlin.math.max
import kotlin.math.min

object CollectionScoreConstants {
    const val BASE_SPECIES_MAX = 20
    const val AVAILABILITY_MAX = 22
    const val VARIANT_MAX = 22
    const val LEGACY_MAX = 15
    const val COLLECTOR_CONTEXT_MAX = 13
    const val META_MAX = 8
    const val TOTAL_MAX = 100

    const val SHINY_BONUS = 10
    const val LUCKY_BONUS = 4
    const val SHADOW_BONUS = 12
    const val PURIFIED_BONUS = 2
    const val LOCATION_CARD_BONUS = 12
    const val COSTUME_RECURRING = 6
    const val COSTUME_NOTABLE = 8
    const val COSTUME_NOT_AVAILABLE = 11
    const val COSTUME_RETIRED = 14
    const val REGIONAL_FORM = 6
    const val SPECIAL_FORM = 8

    const val COMBO_SHINY_COSTUME = 5
    const val COMBO_SHINY_RETIRED_COSTUME = 8
    const val COMBO_COSTUME_LOCATION = 4
    const val COMBO_SHADOW_LEGENDARY = 5
    const val COMBO_REGIONAL_EVENT = 4

    const val EXACT_EVENT_MATCH = 4
    const val FIRST_RELEASE_CATCH = 6
    const val TICKET_HISTORICAL = 8
    const val IN_PERSON_HISTORICAL = 10
    const val SPECIAL_RESEARCH_PROVENANCE = 8
    const val REGIONAL_AREA_KNOWN = 3
    const val RARE_GENDER = 2
    const val XXL_XXS = 2
    const val BACKGROUND_EVENT_MATCH = 6

    const val TROPHY_MIN_SCORE = 90

    val META_DEMAND = mapOf(
        "none" to 0,
        "some" to 2,
        "strong" to 4,
        "top_tier" to 6,
        "exceptional" to 8
    )

    fun legacyPoints(caughtYear: Int, currentYear: Int): Int =
        min(LEGACY_MAX, max(0, currentYear - caughtYear))
}
