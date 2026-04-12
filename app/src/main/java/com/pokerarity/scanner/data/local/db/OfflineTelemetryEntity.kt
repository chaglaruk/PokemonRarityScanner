package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "offline_telemetry")
data class OfflineTelemetryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uploadId: String,
    val endpointUrl: String,
    val statusCode: Int? = null,
    val payloadJson: String,
    val createdAt: Date = Date(),
    val flushedAt: Date? = null
)
