package com.pokerarity.scanner.util.ocr

private const val HP_CANDIDATE_CHARACTER_CLASS = "[-0-9OILSBZ?.,:;!|+_\\s]"
private val hpPairSequencePattern = Regex(
    "(?i)^\\s*$HP_CANDIDATE_CHARACTER_CLASS*?/\\s*$HP_CANDIDATE_CHARACTER_CLASS*?\\s*HP" +
        "(?:\\s+$HP_CANDIDATE_CHARACTER_CLASS*?/\\s*$HP_CANDIDATE_CHARACTER_CLASS*?\\s*HP" +
        "(?:\\s+$HP_CANDIDATE_CHARACTER_CLASS*?/\\s*$HP_CANDIDATE_CHARACTER_CLASS*?\\s*HP)*" +
        "[\\s.,!?;:]*|\\s*)$"
)
private val hpPairCandidatePattern = Regex(
    "(?i)($HP_CANDIDATE_CHARACTER_CLASS*?)/\\s*($HP_CANDIDATE_CHARACTER_CLASS*?)\\s*HP\\b"
)

internal fun parseHpCandidates(text: String): List<Pair<Int, Int>?> {
    if (!hpPairSequencePattern.matches(text)) {
        return listOf(TextParseUtils.parseExactHPPair(text))
    }
    return hpPairCandidatePattern.findAll(text).map { candidate ->
        TextParseUtils.parseExactHPPair(
            "${candidate.groupValues[1].trim()} / ${candidate.groupValues[2].trim()} HP"
        )
    }.toList()
}
