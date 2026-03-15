package com.pokerarity.scanner.data.model

import java.util.Date

/**
 * Data extracted from a Pokemon GO screenshot.
 */
data class PokemonData(
    val cp: Int?,
    val hp: Int?,
    val maxHp: Int?,
    val name: String?,
    val realName: String?, // From candy name for verification
    val candyName: String?,
    val megaEnergy: Int?,
    val weight: Float?,
    val height: Float?,
    val gender: String? = null, // "Male", "Female", "Genderless"
    val stardust: Int?,
    val arcLevel: Float? = null, // % of arc filled (0.0 - 1.0)
    val caughtDate: Date?,
    val rawOcrText: String = ""
)
