package com.pokerarity.scanner.util.ocr

import android.graphics.Rect

private const val SPLIT_THOUSANDS_MAX_GAP_RATIO = 0.3f
private const val SPLIT_THOUSANDS_MIN_OVERLAP_RATIO = 0.5f
private val THOUSANDS_PREFIX = Regex("""(?:[1-9]|[12]\d|30)""")
private val THOUSANDS_SUFFIX = Regex("""\d{3}""")

internal data class ParsedPowerUpCostToken(
    val text: String,
    val token: RowToken
)

/** Rejoins only tightly adjacent digit groups on the anchored POWER UP cost row. */
internal fun mergeSplitPowerUpCostTokens(
    tokens: List<ParsedPowerUpCostToken>,
    minimumCost: Int,
    maximumCost: Int
): List<RowToken> {
    val ordered = tokens.sortedBy { it.token.rect.left }
    val result = ArrayList<RowToken>(ordered.size)
    var index = 0
    while (index < ordered.size) {
        val first = ordered[index]
        val next = ordered.getOrNull(index + 1)
        val firstRect = first.token.rect
        val nextRect = next?.token?.rect
        val sharedHeight = nextRect?.let { minOf(firstRect.height(), it.height()) } ?: 0
        val verticalOverlap = nextRect?.let {
            minOf(firstRect.bottom, it.bottom) - maxOf(firstRect.top, it.top)
        } ?: 0
        val gap = nextRect?.left?.minus(firstRect.right) ?: -1
        val canMerge = next != null &&
            THOUSANDS_PREFIX.matches(first.text) &&
            THOUSANDS_SUFFIX.matches(next.text) &&
            sharedHeight > 0 &&
            verticalOverlap >= sharedHeight * SPLIT_THOUSANDS_MIN_OVERLAP_RATIO &&
            gap >= 0 && gap <= sharedHeight * SPLIT_THOUSANDS_MAX_GAP_RATIO
        if (canMerge) {
            val second = requireNotNull(next)
            val rect = Rect(
                minOf(first.token.rect.left, second.token.rect.left),
                minOf(first.token.rect.top, second.token.rect.top),
                maxOf(first.token.rect.right, second.token.rect.right),
                maxOf(first.token.rect.bottom, second.token.rect.bottom)
            )
            val value = (first.text + second.text).toIntOrNull()
                ?.takeIf { it in minimumCost..maximumCost }
            result += RowToken(rect, numeric = true, value = value)
            index += 2
        } else {
            result += first.token
            index++
        }
    }
    return result
}
