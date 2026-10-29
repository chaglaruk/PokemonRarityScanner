package com.pokerarity.scanner.util.ocr

private const val MIN_CP_DIGITS = 2
private const val MAX_CP_DIGITS = 4
private const val MIN_SPLIT_CP_PART_DIGITS = 1
private const val MAX_SPLIT_CP_PREFIX_DIGITS = MAX_CP_DIGITS - 1
private const val CP_ROW_MIN_VERTICAL_OVERLAP_RATIO = 0.5f
private const val CP_ROW_MAX_GAP_RATIO = 0.3f
private const val CP_SPLIT_MAX_GAP_RATIO = 0.12f
private val HP_FIELD_LABEL_PREFIX = Regex("(?i)^HP\\b")
private val MALFORMED_CP_SUFFIX_SEPARATORS = setOf(
    '/', '\\', ':', '-', '_', '+', '\u2013', '\u2014', '\u2212'
)
internal val CP_NUMERIC_TOKEN = Regex(
    """^[\p{javaWhitespace}\p{Zs}]*""" +
        """([0-9OIL|SBZ](?:[0-9OIL|SBZ]|[\p{javaWhitespace}\p{Zs},]+(?=[0-9OIL|SBZ]))*)""",
    RegexOption.IGNORE_CASE
)

/** This is an anchored, complete token; do not use permissive suffix repair here. */
internal fun parseCompleteAnchoredCpToken(token: String): Int? {
    val compact = normalizeCpDigits(token)
    return compact.takeIf { it.length in MIN_CP_DIGITS..MAX_CP_DIGITS && it.all(Char::isDigit) }
        ?.toIntOrNull()
        ?.takeIf { it in FieldCandidateNormalizer.MIN_SUPPORTED_CP..
            FieldCandidateNormalizer.MAX_SUPPORTED_CP }
}

internal fun hasCleanCpPrefixSuffix(followingText: String, token: MatchResult): Boolean =
    followingText.substring(token.range.last + 1).all { character ->
        character.isWhitespace() || Character.getType(character) == Character.SPACE_SEPARATOR.toInt()
    }

internal fun isSingleNonZeroCpDigit(token: String): Boolean {
    val compact = normalizeCpDigits(token)
    return compact.length == MIN_SPLIT_CP_PART_DIGITS && compact[0] in '1'..'9'
}

/** Keep each adjacent numeric read so conflicting blocks cannot be collapsed into one value. */
internal fun recoverAdjacentCpValues(
    labelLine: MLKitOcrProvider.RecognizedBlock,
    lines: List<MLKitOcrProvider.RecognizedBlock>,
    prefix: String? = null
): List<Int?> {
    val labelRect = labelLine.bounds ?: return emptyList()
    val candidates = lines.asSequence()
        .filter { it !== labelLine && it.bounds != null }
        .filter { candidate ->
            val rect = requireNotNull(candidate.bounds)
            val sharedHeight = minOf(labelRect.height(), rect.height())
            val verticalOverlap = minOf(labelRect.bottom, rect.bottom) -
                maxOf(labelRect.top, rect.top)
            val gap = rect.left - labelRect.right
            val maxGapRatio = if (prefix == null) CP_ROW_MAX_GAP_RATIO else CP_SPLIT_MAX_GAP_RATIO
            sharedHeight > 0 &&
                verticalOverlap >= sharedHeight * CP_ROW_MIN_VERTICAL_OVERLAP_RATIO &&
                gap >= 0 && gap <= sharedHeight * maxGapRatio
        }
        .mapNotNull { candidate ->
            CP_NUMERIC_TOKEN.matchEntire(candidate.text.trim())?.let { candidate to it }
        }
        .toList()
    val values = candidates.map { (_, token) ->
        val numericText = token.groupValues[1]
        val joined = if (prefix == null) numericText else combineSplitCpPrefix(prefix, numericText)
        joined?.let(::parseCompleteAnchoredCpToken)
    }
    return values
}

@Suppress("CyclomaticComplexMethod")
internal fun hasMalformedCpSuffix(followingText: String, token: MatchResult): Boolean {
    val suffixIndex = token.range.last + 1
    val suffix = followingText.getOrNull(suffixIndex)
    val separatorIndex = firstNonWhitespaceIndex(followingText, suffixIndex)
    val separator = separatorIndex?.let(followingText::get)
    val forbiddenSeparator = separator in MALFORMED_CP_SUFFIX_SEPARATORS
    val followingValueIndex = separatorIndex?.let {
        firstNonWhitespaceIndex(followingText, it + 1, skipPunctuationOrSymbol = true)
    }
    val separatorHasFollowingValue = followingValueIndex
        ?.let { followingText[it].isLetterOrDigit() } == true
    val followingValueIsFieldLabel = separator == ',' && followingValueIndex
        ?.let { HP_FIELD_LABEL_PREFIX.containsMatchIn(followingText.substring(it)) } == true
    val formatControlBeforeValue = (suffixIndex until (separatorIndex ?: followingText.length))
        .any { Character.getType(followingText[it]) == Character.FORMAT.toInt() }
    val invisibleSeparatorHidesDigit = formatControlBeforeValue &&
        separator?.let { it.isDigit() || it.uppercaseChar() in "0123456789OIL|SBZ" } == true
    val punctuationOrSymbolWithFollowingValue = separator
        ?.let {
            val type = Character.getType(it)
            type in Character.DASH_PUNCTUATION.toInt()..Character.FINAL_QUOTE_PUNCTUATION.toInt() &&
                separatorHasFollowingValue && !followingValueIsFieldLabel
        } == true

    return suffix?.isLetterOrDigit() == true || forbiddenSeparator ||
        invisibleSeparatorHidesDigit || punctuationOrSymbolWithFollowingValue
}

private fun combineSplitCpPrefix(prefix: String, suffix: String): String? {
    val prefixDigits = normalizeCpDigits(prefix)
    val suffixDigits = normalizeCpDigits(suffix)
    return (prefixDigits + suffixDigits).takeIf {
        prefixDigits.length in MIN_SPLIT_CP_PART_DIGITS..MAX_SPLIT_CP_PREFIX_DIGITS &&
            prefixDigits[0] in '1'..'9' &&
            suffixDigits.length in MIN_SPLIT_CP_PART_DIGITS..(MAX_CP_DIGITS - prefixDigits.length) &&
            suffixDigits.all(Char::isDigit)
    }
}

private fun normalizeCpDigits(token: String): String = token.uppercase(java.util.Locale.ROOT)
    .replace(Regex("[\\p{javaWhitespace}\\p{Zs},]"), "")
    .map { character ->
        when (character) {
            'O' -> '0'
            'I', 'L', '|' -> '1'
            'S' -> '5'
            'B' -> '8'
            'Z' -> '2'
            else -> character
        }
    }.joinToString("")

private fun firstNonWhitespaceIndex(
    value: String,
    startIndex: Int,
    skipPunctuationOrSymbol: Boolean = false
): Int? {
    for (index in startIndex until value.length) {
        val type = Character.getType(value[index])
        val isSpacingOrFormat = value[index].isWhitespace() ||
            type == Character.SPACE_SEPARATOR.toInt() || type == Character.FORMAT.toInt()
        val isPunctuationOrSymbol = skipPunctuationOrSymbol &&
            type in Character.DASH_PUNCTUATION.toInt()..Character.FINAL_QUOTE_PUNCTUATION.toInt()
        if (!isSpacingOrFormat && !isPunctuationOrSymbol) return index
    }
    return null
}
