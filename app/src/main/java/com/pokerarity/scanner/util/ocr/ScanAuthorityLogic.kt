package com.pokerarity.scanner.util.ocr

object ScanAuthorityLogic {

    fun shouldSkipGlobalClassifierForLockedOcr(
        currentSpecies: String?,
        parsedRawSpecies: String?,
        parsedFallbackSpecies: String?,
        candyName: String?
    ): Boolean {
        if (currentSpecies.isNullOrBlank() || currentSpecies.equals("Unknown", ignoreCase = true)) {
            return false
        }
        if (!candyName.isNullOrBlank()) {
            return false
        }
        return parsedRawSpecies.equals(currentSpecies, ignoreCase = true) ||
            parsedFallbackSpecies.equals(currentSpecies, ignoreCase = true)
    }
}
