package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.Date

@Dao
interface TelemetryUploadDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TelemetryUploadEntity): Long

    @Query(
        """
        SELECT * FROM telemetry_uploads
        WHERE status IN (:pendingStatus, :failedStatus)
        ORDER BY createdAt ASC
        LIMIT :limit
        """
    )
    suspend fun getPending(
        limit: Int,
        pendingStatus: String = TelemetryUploadEntity.STATUS_PENDING,
        failedStatus: String = TelemetryUploadEntity.STATUS_FAILED
    ): List<TelemetryUploadEntity>

    @Query(
        """
        UPDATE telemetry_uploads
        SET attempts = :attempts,
            lastError = :lastError,
            status = :status
        WHERE id = :id
        """
    )
    suspend fun markFailed(id: Long, attempts: Int, lastError: String?, status: String = TelemetryUploadEntity.STATUS_FAILED)

    @Query(
        """
        UPDATE telemetry_uploads
        SET status = :status,
            lastError = :lastError
        WHERE id = :id
        """
    )
    suspend fun markBlocked(id: Long, lastError: String?, status: String = TelemetryUploadEntity.STATUS_BLOCKED)

    @Query(
        """
        UPDATE telemetry_uploads
        SET status = :status,
            uploadedAt = :uploadedAt,
            lastError = NULL
        WHERE id = :id
        """
    )
    suspend fun markUploaded(id: Long, uploadedAt: Date, status: String = TelemetryUploadEntity.STATUS_UPLOADED)

    @Query(
        """
        UPDATE telemetry_uploads
        SET status = :pendingStatus
        WHERE status = :blockedStatus
        """
    )
    suspend fun unblockBlocked(
        pendingStatus: String = TelemetryUploadEntity.STATUS_PENDING,
        blockedStatus: String = TelemetryUploadEntity.STATUS_BLOCKED
    )

    @Query("DELETE FROM telemetry_uploads WHERE id = :id")
    suspend fun deleteById(id: Long)
}
