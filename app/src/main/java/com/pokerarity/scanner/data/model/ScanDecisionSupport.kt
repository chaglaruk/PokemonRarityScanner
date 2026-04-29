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
