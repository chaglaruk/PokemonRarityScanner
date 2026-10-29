package com.pokerarity.scanner.data.model

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity

/** Only a typed FALSE is negative evidence. Legacy false and unknown encodings stay unknown. */
fun resolveShinyTruth(typedValue: String?, legacyPositive: Boolean): RecognitionTruth = when (typedValue) {
    null -> if (legacyPositive) RecognitionTruth.TRUE else RecognitionTruth.UNKNOWN
    "TRUE" -> RecognitionTruth.TRUE
    "FALSE" -> RecognitionTruth.FALSE
    else -> RecognitionTruth.UNKNOWN
}

val ScanHistoryEntity.recognizedShiny: RecognitionTruth
    get() = resolveShinyTruth(shinyTruth, isShiny)

val RecognitionTruth.displayLabel: String
    get() = when (this) {
        RecognitionTruth.TRUE -> "Shiny"
        RecognitionTruth.FALSE -> "Not shiny"
        RecognitionTruth.UNKNOWN -> "Shiny unknown"
    }

/** Nonpositive legacy values remain stored, but are not presented as measured CP. */
fun cpDisplayText(cp: Int?): String = cp?.takeIf { it > 0 }?.toString() ?: "Unknown"

/** HP is nonnegative; unlike CP, zero is a real observed value for a fainted Pokémon. */
fun formatHpDisplayText(hp: Int?, missingText: String): String = hp?.takeIf { it >= 0 }?.toString() ?: missingText
