package com.pokerarity.scanner.data.model

data class ScanDecisionSupport(
    val eventConfidenceCode: String,
    val eventConfidenceLabel: String,
    val eventConfidenceDetail: String,
    val scanConfidenceScore: Int,
    val scanConfidenceLabel: String,
    val scanConfidenceDetail: String,
    val mismatchGuardTitle: String? = null,
    val mismatchGuardDetail: String? = null,
    val whyNotExact: String? = null,
    val recognitionSummary: String? = null
) {
    fun hasVisibleUiContent(): Boolean =
        eventConfidenceLabel.isNotBlank() ||
            eventConfidenceDetail.isNotBlank() ||
            scanConfidenceLabel.isNotBlank() ||
            scanConfidenceDetail.isNotBlank() ||
        !mismatchGuardTitle.isNullOrBlank() ||
            !mismatchGuardDetail.isNullOrBlank() ||
            !whyNotExact.isNullOrBlank() ||
            !recognitionSummary.isNullOrBlank()
}

/** Historical rows cannot infer review decisions from rarity/CP/shiny evidence. */
fun com.pokerarity.scanner.data.local.db.ScanHistoryEntity.toScanDecisionSupport(): ScanDecisionSupport? {
    val label = scanConfidenceLabel.orEmpty()
    val detail = scanConfidenceDetail.orEmpty()
    val eventCode = eventConfidenceCode.orEmpty()
    val eventLabel = eventConfidenceLabel.orEmpty()
    val eventDetail = eventConfidenceDetail.orEmpty()
    val mismatchTitle = mismatchGuardTitle
    val mismatchDetail = mismatchGuardDetail
    val summary = recognitionSummary
    if (listOf(
            label,
            detail,
            eventCode,
            eventLabel,
            eventDetail,
            mismatchTitle.orEmpty(),
            mismatchDetail.orEmpty(),
            summary.orEmpty()
        ).none(String::isNotBlank)
    ) return null
    return ScanDecisionSupport(
        eventConfidenceCode = eventCode,
        eventConfidenceLabel = eventLabel,
        eventConfidenceDetail = eventDetail,
        scanConfidenceScore = scanConfidenceScore ?: 0,
        scanConfidenceLabel = label,
        scanConfidenceDetail = detail,
        mismatchGuardTitle = mismatchTitle,
        mismatchGuardDetail = mismatchDetail,
        recognitionSummary = summary
    )
}
