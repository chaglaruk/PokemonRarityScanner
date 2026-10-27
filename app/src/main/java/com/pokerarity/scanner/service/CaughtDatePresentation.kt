package com.pokerarity.scanner.service

import com.pokerarity.scanner.util.DateParseUtils
import com.pokerarity.scanner.util.DateParseUtils.toLocalDate
import java.time.LocalDate
import java.util.Date

internal data class CaughtDatePresentation(
    val displayText: String,
    val isoDate: String?,
    val localDate: LocalDate?
) {
    companion object {
        fun from(caughtDate: Date?): CaughtDatePresentation {
            val localDate = caughtDate?.toLocalDate()
            return CaughtDatePresentation(
                displayText = localDate?.format(DateParseUtils.MMM_DD_YYYY_FORMATTER) ?: "Unknown",
                isoDate = localDate?.toString(),
                localDate = localDate
            )
        }
    }
}
