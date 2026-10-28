package com.pokerarity.scanner.ui.result

import android.content.Intent
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.ScanReviewCodes

/** Typed field guidance distinguishes new observed zero from legacy zero-as-missing rows. */
internal fun Intent.putHistoryScanHp(scan: ScanHistoryEntity) {
    val typedHpPresent = scan.scanConfidenceDetail
        ?.takeIf { it.startsWith(ScanReviewCodes.DETAILS_PREFIX) }
        ?.removePrefix(ScanReviewCodes.DETAILS_PREFIX)
        ?.split(',')
        ?.contains(ScanReviewCodes.HP_PRESENT) == true
    if (scan.hp == 0 && !typedHpPresent) {
        // Keep the old zero sentinel and omit the presence bit. Result readers will retain
        // the existing History value on save while presenting the field as unknown.
        putExtra(ResultActivity.EXTRA_HP, 0)
    } else {
        putScanHp(scan.hp)
    }
}
