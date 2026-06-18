package com.pokerarity.scanner.data.local

import android.content.Context
import android.util.Log
import com.pokerarity.scanner.data.local.db.AppDatabase
import java.io.File
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Manages data retention policies for scan history and telemetry data.
 * Automatically deletes old data to comply with GDPR and privacy requirements.
 * 
 * 🔴 SECURITY: Implements automated data minimization principle.
 */
class DataRetentionManager(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getInstance(context)
) {
    companion object {
        private const val TAG = "DataRetentionManager"
        
        /**
         * Default retention period: 30 days
         * Balances user privacy (quick deletion) with data usefulness
         */
        const val DEFAULT_RETENTION_DAYS = 30
        
        /**
         * Telemetry data retention: 30 days (GDPR compliant)
         * Server-side will also implement this
         */
        const val TELEMETRY_RETENTION_DAYS = 30
    }

    /**
     * Delete scan history older than specified days.
     * Called periodically (e.g., on app start) to clean up old data.
     */
    suspend fun deleteOldScans(retentionDays: Int = DEFAULT_RETENTION_DAYS): Int {
        return withContext(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 3600_000L)
                val deletedCount = database.scanHistoryDao().deleteOlderThan(cutoffTime)
                
                if (deletedCount > 0) {
                    Log.i(TAG, "Deleted $deletedCount scans older than $retentionDays days")
                }
                
                deletedCount
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete old scans", e)
                0
            }
        }
    }

    /**
     * Delete telemetry data older than retention period.
     * Should be called by TelemetryRepository periodically.
     */
    suspend fun deleteOldTelemetry(retentionDays: Int = TELEMETRY_RETENTION_DAYS): Int {
        return withContext(Dispatchers.IO) {
            try {
                val cutoffTime = System.currentTimeMillis() - (retentionDays * 24 * 3600_000L)
                val cutoffDate = Date(cutoffTime)
                val dao = database.telemetryUploadDao()
                val offlineDao = database.offlineTelemetryDao()
                val screenshotPaths = dao.getScreenshotPathsOlderThan(cutoffDate)
                val deletedUploads = dao.deleteOlderThan(cutoffDate)
                val deletedOffline = offlineDao.deleteOlderThan(cutoffDate)
                val deletedFiles = deleteTelemetryScreenshots(screenshotPaths)

                Log.i(
                    TAG,
                    "Telemetry retention policy enforced: uploads=$deletedUploads offline=$deletedOffline files=$deletedFiles keeping last $retentionDays days"
                )
                deletedUploads + deletedOffline
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete old telemetry", e)
                0
            }
        }
    }

    /**
     * Delete all scan history (user-initiated, e.g., in Settings)
     */
    suspend fun deleteAllScans(): Int {
        return withContext(Dispatchers.IO) {
            try {
                val totalCount = database.scanHistoryDao().count()
                database.scanHistoryDao().deleteOlderThan(System.currentTimeMillis() + 1)
                Log.i(TAG, "Deleted all $totalCount scans (user-initiated)")
                totalCount
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete all scans", e)
                0
            }
        }
    }

    private fun deleteTelemetryScreenshots(paths: List<String?>): Int {
        val telemetryCacheRoot = runCatching {
            File(context.cacheDir, "telemetry_uploads").canonicalFile
        }.getOrNull() ?: return 0

        return paths.count { path ->
            val file = path
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { runCatching { File(it).canonicalFile }.getOrNull() }
                ?: return@count false
            if (file.parentFile != telemetryCacheRoot || !file.isFile) return@count false
            runCatching { file.delete() }.getOrDefault(false)
        }
    }
}
