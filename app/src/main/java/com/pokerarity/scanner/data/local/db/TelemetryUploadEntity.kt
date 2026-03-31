package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "telemetry_uploads")
data class TelemetryUploadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uploadId: String,
    val createdAt: Date = Date(),
    val status: String = STATUS_PENDING,
    val attempts: Int = 0,
    val lastError: String? = null,
    val uploadedAt: Date? = null,
    val payloadJson: String,
    val screenshotPath: String? = null
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_UPLOADED = "UPLOADED"
        const val STATUS_FAILED = "FAILED"
    }
}
