package com.pokerarity.scanner.ui.result

import android.content.Intent
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.resolveShinyTruth

/** Same integer key for old callers; absent CP has no numeric substitute. */
fun Intent.readScanCp(): Int? =
    if (hasExtra(ResultActivity.EXTRA_CP)) getIntExtra(ResultActivity.EXTRA_CP, 0).takeIf { it > 0 } else null

fun Intent.putScanCp(cp: Int?): Intent = apply {
    if (cp == null) removeExtra(ResultActivity.EXTRA_CP) else putExtra(ResultActivity.EXTRA_CP, cp)
}

/** New writers pair the legacy integer extra with an explicit presence bit so HP 0 stays observable. */
fun Intent.putScanHp(hp: Int?): Intent = apply {
    putExtra(ResultActivity.EXTRA_HP, hp?.takeIf { it >= 0 } ?: 0)
    putExtra(ResultActivity.EXTRA_HP_KNOWN, hp != null && hp >= 0)
}

/** Old intents used zero as the missing-value sentinel; retain that behavior when the typed bit is absent. */
fun Intent.readScanHp(): Int? = if (hasExtra(ResultActivity.EXTRA_HP_KNOWN)) {
    if (getBooleanExtra(ResultActivity.EXTRA_HP_KNOWN, false)) {
        getIntExtra(ResultActivity.EXTRA_HP, -1).takeIf { it >= 0 }
    } else {
        null
    }
} else {
    getIntExtra(ResultActivity.EXTRA_HP, 0).takeIf { it > 0 }
}

/** A typed absence replaces the previous value; a legacy omitted value leaves it untouched. */
fun Intent.readScanHpOr(existingHp: Int?): Int? =
    if (hasExtra(ResultActivity.EXTRA_HP_KNOWN)) readScanHp() else readScanHp() ?: existingHp

fun Intent.putScanShinyTruth(truth: RecognitionTruth?): Intent = apply {
    if (truth == null) removeExtra(ResultActivity.EXTRA_SHINY_TRUTH)
    else putExtra(ResultActivity.EXTRA_SHINY_TRUTH, truth.name)
}

fun Intent.readScanShinyTruth(): RecognitionTruth {
    val value = getStringExtra(ResultActivity.EXTRA_SHINY_TRUTH)
    if (hasExtra(ResultActivity.EXTRA_SHINY_TRUTH) && value == null) return RecognitionTruth.UNKNOWN
    return resolveShinyTruth(value, getBooleanExtra(ResultActivity.EXTRA_IS_SHINY, false))
}

fun Intent.readScanDecisionSupport(): ScanDecisionSupport? = ScanDecisionSupport(
    eventConfidenceCode = getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_CODE).orEmpty(),
    eventConfidenceLabel = getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_LABEL).orEmpty(),
    eventConfidenceDetail = getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_DETAIL).orEmpty(),
    scanConfidenceScore = getIntExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_SCORE, 0),
    scanConfidenceLabel = getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_LABEL).orEmpty(),
    scanConfidenceDetail = getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_DETAIL).orEmpty(),
    mismatchGuardTitle = getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_TITLE),
    mismatchGuardDetail = getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_DETAIL),
    recognitionSummary = getStringExtra(ResultActivity.EXTRA_RECOGNITION_SUMMARY)
).takeIf { it.hasVisibleUiContent() }
