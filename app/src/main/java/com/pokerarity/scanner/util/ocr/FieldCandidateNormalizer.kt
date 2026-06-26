package com.pokerarity.scanner.util.ocr

import java.util.Locale

object FieldCandidateNormalizer {

    data class Result(
        val normalizedText: String?,
        val parsedValue: String?,
        val status: String,
        val score: Float,
        val reason: String
    )

    fun normalizeCp(raw: String?): Result {
        val source = raw.orEmpty()
        val normalized = digitLike(source)
            .replace(Regex("""C\s*P""", RegexOption.IGNORE_CASE), "CP")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val digits = normalized.filter(Char::isDigit)
        val parsed = (TextParseUtils.parseCP(normalized) ?: TextParseUtils.parseCP("CP $digits"))
            ?.takeIf { it in 100..5500 }
        return numericResult(
            normalizedText = parsed?.toString() ?: digits.takeIf(::hasUsefulDigits),
            parsedValue = parsed?.toString(),
            raw = source,
            reasonPrefix = "cp_numeric"
        )
    }

    fun normalizeHp(raw: String?, cp: Int? = null): Result {
        val source = raw.orEmpty()
        val normalized = digitLike(source)
            .replace(Regex("""H\s*P""", RegexOption.IGNORE_CASE), "HP")
            .replace(Regex("""\s*/\s*"""), "/")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val pair = TextParseUtils.selectBestHPPairForCp(cp, normalized, source)
        val parsed = pair?.let { "${it.first}/${it.second}" }
        return numericResult(
            normalizedText = parsed ?: normalized.takeIf { it.any(Char::isDigit) },
            parsedValue = parsed,
            raw = source,
            reasonPrefix = "hp_pair"
        )
    }

    fun normalizeStardust(raw: String?, parser: (String) -> Int?): Result {
        val source = raw.orEmpty()
        val normalized = digitLike(source)
            .replace(Regex("""[^0-9,\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val compact = normalized.replace(",", "").replace(" ", "")
        val parsed = parser(source) ?: parser(normalized) ?: parser(compact)
        return numericResult(
            normalizedText = compact.takeIf(::hasUsefulDigits),
            parsedValue = parsed?.toString(),
            raw = source,
            reasonPrefix = "stardust_numeric"
        )
    }

    fun normalizeAppraisal(raw: String?): Result {
        val source = raw.orEmpty()
        val normalized = digitLike(source)
            .replace(Regex("""[^0-9]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val parsed = Regex("""\b\d{1,2}\b""").findAll(normalized)
            .mapNotNull { it.value.toIntOrNull() }
            .firstOrNull { it in 0..15 }
        return numericResult(
            normalizedText = parsed?.toString() ?: normalized.takeIf { it.any(Char::isDigit) },
            parsedValue = parsed?.toString(),
            raw = source,
            reasonPrefix = "appraisal_numeric"
        )
    }

    private fun digitLike(value: String): String =
        value.uppercase(Locale.US)
            .replace('O', '0')
            .replace('I', '1')
            .replace('L', '1')
            .replace('|', '1')
            .replace('S', '5')
            .replace('B', '8')
            .replace('Z', '2')

    private fun numericResult(
        normalizedText: String?,
        parsedValue: String?,
        raw: String,
        reasonPrefix: String
    ): Result {
        val rawHasText = raw.isNotBlank()
        val status = if (!parsedValue.isNullOrBlank()) "found" else "missing"
        val usefulChars = raw.count { it.isLetterOrDigit() || it == '/' || it == ',' }
        val digitChars = raw.count(Char::isDigit)
        val characterScore = if (usefulChars == 0) 0f else (digitChars.toFloat() / usefulChars).coerceIn(0f, 1f)
        val score = when {
            parsedValue != null -> 0.70f + (characterScore * 0.20f)
            rawHasText && !normalizedText.isNullOrBlank() -> 0.20f + (characterScore * 0.20f)
            else -> 0f
        }.coerceIn(0f, 1f)
        val reason = when {
            parsedValue != null -> "${reasonPrefix}_parsed"
            rawHasText -> "${reasonPrefix}_no_valid_parse"
            else -> "${reasonPrefix}_empty"
        }
        return Result(normalizedText, parsedValue, status, score, reason)
    }

    private fun hasUsefulDigits(value: String): Boolean =
        value.any { it in '1'..'9' }
}
