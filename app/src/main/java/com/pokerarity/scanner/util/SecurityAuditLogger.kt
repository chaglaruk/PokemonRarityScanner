package com.pokerarity.scanner.util

import android.content.Context
import android.util.Log
import com.pokerarity.scanner.BuildConfig
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.util.Date
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Security audit logger for tracking security-relevant events.
 *
 * Events logged:
 * - Consent changes (opt-in/opt-out)
 * - Encryption failures
 * - Telemetry upload attempts
 * - Rate limit violations
 * - Database migration events
 * - Preference access errors
 *
 * Logs are stored in app-private storage and rotated daily.
 * Older than 7 days are automatically deleted.
 */
class SecurityAuditLogger private constructor(private val context: Context) {

    companion object {
        private const val TAG = "SecurityAudit"
        private const val LOG_DIR = "security_audit"
        private const val MAX_LOG_AGE_DAYS = 7
        private const val MAX_QUEUE_SIZE = 100
        internal const val MAX_LOG_VALUE_LENGTH = 120

        @Volatile
        private var instance: SecurityAuditLogger? = null

        fun getInstance(context: Context): SecurityAuditLogger {
            return instance ?: synchronized(this) {
                instance ?: SecurityAuditLogger(context.applicationContext).also {
                    instance = it
                    it.cleanupOldLogs()
                }
            }
        }

        internal fun sanitizeAuditValue(value: String?): String? {
            return value
                ?.replace(Regex("[\\r\\n\\t]+"), " ")
                ?.trim()
                ?.take(MAX_LOG_VALUE_LENGTH)
                ?.takeIf { it.isNotBlank() }
        }

        internal fun presenceSummary(label: String, value: String?): String {
            return "$label present: ${!value.isNullOrBlank()}"
        }
    }

    private val eventQueue = ConcurrentLinkedQueue<AuditEvent>()

    /**
     * Security event types for categorization
     */
    enum class EventType {
        CONSENT_CHANGED,
        TELEMETRY_UPLOAD_ATTEMPT,
        TELEMETRY_UPLOAD_SUCCESS,
        TELEMETRY_UPLOAD_FAILED,
        ENCRYPTION_INIT,
        ENCRYPTION_FAILED,
        DATABASE_MIGRATION,
        RATE_LIMIT_EXCEEDED,
        PREFERENCE_ACCESS_DENIED,
        INVALID_INPUT_DETECTED,
        OVERLAY_DETECTION,
        SERVICE_START,
        SERVICE_STOP
    }

    /**
     * Audit event data class
     */
    data class AuditEvent(
        val timestamp: Long = System.currentTimeMillis(),
        val type: EventType,
        val message: String,
        val details: String? = null,
        val success: Boolean = true
    )

    /**
     * Log a security event
     */
    fun log(type: EventType, message: String, details: String? = null, success: Boolean = true) {
        val safeMessage = sanitizeAuditValue(message) ?: "Security event"
        val safeDetails = sanitizeAuditValue(details)
        val event = AuditEvent(
            type = type,
            message = safeMessage,
            details = safeDetails,
            success = success
        )

        // Add to queue (bounded)
        if (eventQueue.size >= MAX_QUEUE_SIZE) {
            eventQueue.poll() // Remove oldest
        }
        eventQueue.offer(event)

        // Keep audit details out of release-visible logcat.
        if (BuildConfig.DEBUG) {
            val logLevel = if (success) Log.INFO else Log.WARN
            val detailsStr = safeDetails?.let { " | $it" } ?: ""
            Log.println(logLevel, TAG, "$safeMessage$detailsStr")
        }

        // Persist to app-private storage.
        persistEvent(event)
    }

    /**
     * Convenience methods for common security events
     */
    fun logConsentChange(enabled: Boolean, reason: String = "user_action") {
        log(
            EventType.CONSENT_CHANGED,
            "Telemetry consent changed: $enabled",
            presenceSummary("Reason", reason),
            success = true
        )
    }

    fun logTelemetryUploadAttempt(uploadId: String?) {
        log(
            EventType.TELEMETRY_UPLOAD_ATTEMPT,
            "Telemetry upload attempted",
            presenceSummary("UploadId", uploadId),
            success = true
        )
    }

    fun logTelemetryUploadSuccess(uploadId: String) {
        log(
            EventType.TELEMETRY_UPLOAD_SUCCESS,
            "Telemetry upload succeeded",
            presenceSummary("UploadId", uploadId),
            success = true
        )
    }

    fun logTelemetryUploadFailed(uploadId: String, error: String) {
        log(
            EventType.TELEMETRY_UPLOAD_FAILED,
            "Telemetry upload failed",
            "${presenceSummary("UploadId", uploadId)} | ${presenceSummary("Error", error)}",
            success = false
        )
    }

    fun logEncryptionInit(success: Boolean, details: String? = null) {
        log(
            if (success) EventType.ENCRYPTION_INIT else EventType.ENCRYPTION_FAILED,
            if (success) "Database encryption initialized" else "Database encryption failed",
            presenceSummary("Details", details),
            success = success
        )
    }

    fun logDatabaseMigration(fromVersion: Int, toVersion: Int) {
        log(
            EventType.DATABASE_MIGRATION,
            "Database migrated",
            "From v$fromVersion to v$toVersion",
            success = true
        )
    }

    fun logRateLimitExceeded(identifier: String) {
        log(
            EventType.RATE_LIMIT_EXCEEDED,
            "Rate limit exceeded",
            presenceSummary("Identifier", identifier),
            success = false
        )
    }

    fun logInvalidInput(field: String, reason: String) {
        log(
            EventType.INVALID_INPUT_DETECTED,
            "Invalid input detected",
            "${presenceSummary("Field", field)} | ${presenceSummary("Reason", reason)}",
            success = false
        )
    }

    fun logOverlayDetection(blocked: Boolean) {
        log(
            EventType.OVERLAY_DETECTION,
            if (blocked) "Overlay blocked - app not in foreground" else "Overlay allowed",
            success = blocked // Blocked is "success" from security perspective
        )
    }

    fun logServiceStart(serviceName: String) {
        log(
            EventType.SERVICE_START,
            "Service started",
            presenceSummary("Service", serviceName),
            success = true
        )
    }

    fun logServiceStop(serviceName: String) {
        log(
            EventType.SERVICE_STOP,
            "Service stopped",
            presenceSummary("Service", serviceName),
            success = true
        )
    }

    /**
     * Persist event to file
     */
    private fun persistEvent(event: AuditEvent) {
        try {
            val logDir = File(context.filesDir, LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            val date = DateParseUtils.formatDate(Date(event.timestamp), DateParseUtils.ISO_DATE_FORMATTER)
            val logFile = File(logDir, "security_$date.log")

            BufferedWriter(FileWriter(logFile, true)).use { writer ->
                val line = buildString {
                    append(DateParseUtils.formatDate(Date(event.timestamp), DateParseUtils.ISO_DATETIME_FORMATTER))
                    append(" | ")
                    append(event.type.name)
                    append(" | ")
                    append(if (event.success) "OK" else "FAIL")
                    append(" | ")
                    append(event.message)
                    event.details?.let { append(" | ").append(it) }
                }
                writer.write(line)
                writer.newLine()
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to persist audit event", e)
            }
        }
    }

    /**
     * Clean up logs older than MAX_LOG_AGE_DAYS
     */
    private fun cleanupOldLogs() {
        try {
            val logDir = File(context.filesDir, LOG_DIR)
            if (!logDir.exists()) return

            val cutoffTime = System.currentTimeMillis() - (MAX_LOG_AGE_DAYS * 24 * 60 * 60 * 1000L)

            logDir.listFiles()?.forEach { file ->
                try {
                    val fileName = file.nameWithoutExtension
                    val fileDate = DateParseUtils.parseIsoDate(fileName.substringAfter("security_"))
                    if (fileDate != null && fileDate.time < cutoffTime) {
                        file.delete()
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "Deleted old security log")
                        }
                    }
                } catch (_: Exception) {
                    // Ignore parse errors, skip file
                }
            }
        } catch (e: Exception) {
            if (BuildConfig.DEBUG) {
                Log.e(TAG, "Failed to cleanup old logs", e)
            }
        }
    }

    /**
     * Get recent events (for debugging)
     */
    fun getRecentEvents(count: Int = 20): List<AuditEvent> {
        return eventQueue.toList().takeLast(count)
    }
}
