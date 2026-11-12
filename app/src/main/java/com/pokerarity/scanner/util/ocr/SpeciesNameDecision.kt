package com.pokerarity.scanner.util.ocr

private const val MAX_REPLAY_DIAGNOSTIC_REASON_CODES = 4

private const val EXACT_SELECTION_FLOOR = 0.90f
private const val REVIEWED_SELECTION_FLOOR = 0.75f
private const val SAFE_FUZZY_SELECTION_FLOOR = 0.60f
private const val SELECTION_EVIDENCE_RANGE = 0.05f

enum class SpeciesNameAcceptanceSource {
    EXACT_CANONICAL,
    REVIEWED_ALIAS,
    SAFE_FUZZY
}

data class SpeciesNameDecisionDiagnostics(
    val reasonCodes: List<String>,
    val topCandidate: TextParser.NameCandidate? = null,
    val runnerUp: TextParser.NameCandidate? = null
)

sealed interface SpeciesNameDecision {
    val diagnostics: SpeciesNameDecisionDiagnostics

    data class Accepted(
        val species: String,
        val source: SpeciesNameAcceptanceSource,
        override val diagnostics: SpeciesNameDecisionDiagnostics
    ) : SpeciesNameDecision

    data class Uncertain(
        val candidates: List<TextParser.NameCandidate>,
        override val diagnostics: SpeciesNameDecisionDiagnostics
    ) : SpeciesNameDecision

    data class NoMatch(
        override val diagnostics: SpeciesNameDecisionDiagnostics
    ) : SpeciesNameDecision
}

fun SpeciesNameDecision.acceptedSpeciesOrNull(): String? =
    (this as? SpeciesNameDecision.Accepted)?.species

/** Privacy-safe parser trace for local replay reports; never includes observed OCR text. */
internal fun SpeciesNameDecision.replaySafeDiagnosticCode(): String {
    val resolution = when (this) {
        is SpeciesNameDecision.Accepted -> "accepted"
        is SpeciesNameDecision.Uncertain -> "uncertain"
        is SpeciesNameDecision.NoMatch -> "no_match"
    }
    val source = (this as? SpeciesNameDecision.Accepted)?.source?.name
    val reasons = diagnostics.reasonCodes
        .asSequence()
        .filter { it.matches(Regex("[a-z][a-z0-9_]{0,63}")) }
        .distinct()
        .take(MAX_REPLAY_DIAGNOSTIC_REASON_CODES)
        .joinToString("+")
        .takeIf { it.isNotBlank() }
    return listOfNotNull(resolution, source, reasons).joinToString(":")
}

internal fun TextParser.decideDynamicOcrSpeciesName(rawText: String): SpeciesNameDecision =
    decideSpeciesName(rawText)

internal fun TextParser.decideStaticOcrSpeciesName(rawText: String): SpeciesNameDecision =
    decideSpeciesName(rawText)

internal fun SpeciesNameDecision.acceptedSelectionScore(evidence: Float): Float {
    val accepted = this as? SpeciesNameDecision.Accepted ?: return 0f
    val bandFloor = when (accepted.source) {
        SpeciesNameAcceptanceSource.EXACT_CANONICAL -> EXACT_SELECTION_FLOOR
        SpeciesNameAcceptanceSource.REVIEWED_ALIAS -> REVIEWED_SELECTION_FLOOR
        SpeciesNameAcceptanceSource.SAFE_FUZZY -> SAFE_FUZZY_SELECTION_FLOOR
    }
    return bandFloor + evidence.coerceIn(0f, 1f) * SELECTION_EVIDENCE_RANGE
}

internal fun SpeciesNameDecision.status(): String = when (this) {
    is SpeciesNameDecision.Accepted -> "found"
    is SpeciesNameDecision.Uncertain -> "uncertain"
    is SpeciesNameDecision.NoMatch -> "missing"
}
