package com.pokerarity.scanner.service

import com.pokerarity.scanner.util.DateParseUtils
import com.pokerarity.scanner.util.DateParseUtils.toLocalDate
import com.pokerarity.scanner.data.model.PokemonData
import java.time.LocalDate
import java.util.Date

internal data class CaughtDatePresentation(
    val displayText: String,
    val isoDate: String?,
    val localDate: LocalDate?
) {
    companion object {
        fun fromPokemonData(pokemon: PokemonData): CaughtDatePresentation =
            from(pokemon.caughtDate, pokemon.caughtDateLocalDate)

        fun from(caughtDate: Date?, capturedLocalDate: LocalDate? = null): CaughtDatePresentation {
            val localDate = capturedLocalDate ?: caughtDate?.toLocalDate()
            return CaughtDatePresentation(
                displayText = localDate?.format(DateParseUtils.MMM_DD_YYYY_FORMATTER) ?: "Unknown",
                isoDate = localDate?.toString(),
                localDate = localDate
            )
        }
    }
}
