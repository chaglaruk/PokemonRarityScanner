package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.Date

@Dao
interface OfflineTelemetryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: OfflineTelemetryEntity): Long

    @Query("SELECT COUNT(*) FROM offline_telemetry WHERE uploadId = :uploadId AND flushedAt IS NULL")
    suspend fun countPending(uploadId: String): Int

    @Query("UPDATE offline_telemetry SET flushedAt = :flushedAt WHERE flushedAt IS NULL")
    suspend fun markAllFlushed(flushedAt: Date = Date())
}
