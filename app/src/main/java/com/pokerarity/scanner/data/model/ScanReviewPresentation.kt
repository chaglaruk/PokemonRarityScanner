package com.pokerarity.scanner.data.model

import com.pokerarity.scanner.util.ocr.ScanDecisionType
import java.util.Locale
import kotlin.math.roundToInt

/** Stable, locale-neutral codes stored with scan history; never treat rarity completeness as OCR authority. */
object ScanReviewCodes {
    const val ACCEPTED = "scan.accepted"
    const val REVIEW = "scan.review"
    const val DETAILS_PREFIX = "fields:"
    const val CP_PRESENT = "cp_present"
    const val CP_MISSING = "cp_missing"
    const val HP_PRESENT = "hp_present"
    const val HP_MISSING = "hp_missing"
    const val DATE_PRESENT = "date_present"
    const val DATE_MISSING = "date_missing"
}

private const val PERCENT = 100

data class ScanReviewMetadata(val score: Int, val labelCode: String, val detailCode: String)

data class ScanReviewPresentation(val title: String, val label: String, val detail: String)

/**
 * The final recognition gate owns the label and numeric score.
 * CP, HP and date only describe available data and cannot upgrade that decision.
 */
fun PokemonData.scanReviewMetadata(): ScanReviewMetadata? =
    scanDecision?.takeIf { it.mayShowOverlay && it.maySaveScan }?.let { gate ->
        gate.decision.reviewLabelCode()?.let { labelCode ->
            ScanReviewMetadata(
                score = (gate.confidence * PERCENT).roundToInt().coerceIn(0, PERCENT),
                labelCode = labelCode,
                detailCode = ScanReviewCodes.DETAILS_PREFIX + reviewFieldCodes()
            )
        }
    }

private fun ScanDecisionType.reviewLabelCode(): String? = when (this) {
    ScanDecisionType.ACCEPT -> ScanReviewCodes.ACCEPTED
    ScanDecisionType.ACCEPT_LOW_CONFIDENCE -> ScanReviewCodes.REVIEW
    else -> null
}

private fun PokemonData.reviewFieldCodes(): String = listOf(
    if ((cp ?: 0) > 0) ScanReviewCodes.CP_PRESENT else ScanReviewCodes.CP_MISSING,
    if ((hp ?: 0) > 0 || (maxHp ?: 0) > 0) ScanReviewCodes.HP_PRESENT else ScanReviewCodes.HP_MISSING,
    if (caughtDate != null) ScanReviewCodes.DATE_PRESENT else ScanReviewCodes.DATE_MISSING
).joinToString(",")

/** Render using the current locale, including History records saved under a different locale. */
fun ScanDecisionSupport.scanReviewPresentation(locale: Locale = Locale.getDefault()): ScanReviewPresentation {
    val turkish = locale.language.equals("tr", ignoreCase = true)
    val title = if (turkish) "Tanıma güveni" else "Recognition confidence"
    val label = when (scanConfidenceLabel) {
        ScanReviewCodes.ACCEPTED -> if (turkish) "Tarama kabul edildi" else "Scan accepted"
        ScanReviewCodes.REVIEW -> if (turkish) "Kontrol önerilir" else "Review recommended"
        else -> scanConfidenceLabel // Legacy string values remain readable.
    }
    val detail = if (scanConfidenceDetail.startsWith(ScanReviewCodes.DETAILS_PREFIX)) {
        val fields = scanConfidenceDetail.removePrefix(ScanReviewCodes.DETAILS_PREFIX).split(",")
        val readable = fields.mapNotNull { field -> localizedReviewField(field, turkish) }
        if (readable.isEmpty()) "" else "${scanConfidenceScore.coerceIn(0, PERCENT)}% - ${readable.joinToString(", ")}"
    } else {
        scanConfidenceDetail
    }
    return ScanReviewPresentation(title, label, detail)
}

private fun localizedReviewField(field: String, turkish: Boolean): String? {
    return when (field) {
                ScanReviewCodes.CP_PRESENT -> if (turkish) "CP mevcut" else "CP available"
                ScanReviewCodes.CP_MISSING -> if (turkish) "CP eksik" else "CP missing"
                ScanReviewCodes.HP_PRESENT -> if (turkish) "HP mevcut" else "HP available"
                ScanReviewCodes.HP_MISSING -> if (turkish) "HP eksik" else "HP missing"
                ScanReviewCodes.DATE_PRESENT -> if (turkish) "tarih mevcut" else "date available"
                ScanReviewCodes.DATE_MISSING -> if (turkish) "tarih eksik" else "date missing"
                else -> null
    }
}
