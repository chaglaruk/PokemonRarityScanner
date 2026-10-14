package com.pokerarity.scanner.ui.result

import android.content.Intent
import com.pokerarity.scanner.data.model.RecognitionTruth
import com.pokerarity.scanner.data.model.resolveShinyTruth

/** Same integer key for old callers; absent CP has no numeric substitute. */
fun Intent.readScanCp(): Int? =
    if (hasExtra(ResultActivity.EXTRA_CP)) getIntExtra(ResultActivity.EXTRA_CP, 0).takeIf { it > 0 } else null

fun Intent.putScanCp(cp: Int?): Intent = apply {
    if (cp == null) removeExtra(ResultActivity.EXTRA_CP) else putExtra(ResultActivity.EXTRA_CP, cp)
}

fun Intent.putScanShinyTruth(truth: RecognitionTruth?): Intent = apply {
    if (truth == null) removeExtra(ResultActivity.EXTRA_SHINY_TRUTH)
    else putExtra(ResultActivity.EXTRA_SHINY_TRUTH, truth.name)
}

fun Intent.readScanShinyTruth(): RecognitionTruth {
    val value = getStringExtra(ResultActivity.EXTRA_SHINY_TRUTH)
    if (hasExtra(ResultActivity.EXTRA_SHINY_TRUTH) && value == null) return RecognitionTruth.UNKNOWN
    return resolveShinyTruth(value, getBooleanExtra(ResultActivity.EXTRA_IS_SHINY, false))
}
