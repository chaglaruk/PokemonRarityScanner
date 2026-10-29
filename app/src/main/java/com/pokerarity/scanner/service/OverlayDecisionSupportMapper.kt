package com.pokerarity.scanner.service

import android.content.Intent
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.ui.result.ResultActivity

/** Maps the result intent's typed review fields into the overlay's presentation model. */
internal object OverlayDecisionSupportMapper {
    fun fromIntent(intent: Intent): ScanDecisionSupport? = ScanDecisionSupport(
        eventConfidenceCode = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_CODE).orEmpty(),
        eventConfidenceLabel = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_LABEL).orEmpty(),
        eventConfidenceDetail = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_DETAIL).orEmpty(),
        scanConfidenceScore = intent.getIntExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_SCORE, 0),
        scanConfidenceLabel = intent.getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_LABEL).orEmpty(),
        scanConfidenceDetail = intent.getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_DETAIL).orEmpty(),
        mismatchGuardTitle = intent.getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_TITLE),
        mismatchGuardDetail = intent.getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_DETAIL),
        whyNotExact = intent.getStringExtra(ResultActivity.EXTRA_WHY_NOT_EXACT),
        recognitionSummary = intent.getStringExtra(ResultActivity.EXTRA_RECOGNITION_SUMMARY)
    ).takeIf { it.hasVisibleUiContent() }
}
