package com.pokerarity.scanner.domain.collector

data class CollectorDecision(
    val action: ScanAction,
    val reasons: List<RarityReason>,
    val isReviewRequired: Boolean,
    val shortSummary: String
)
