package com.pokerarity.scanner.data.model

data class ScanFeedbackPayload(
    val uploadId: String,
    val category: String,
    val notes: String? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
