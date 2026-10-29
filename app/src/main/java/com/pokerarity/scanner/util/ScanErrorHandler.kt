package com.pokerarity.scanner.util

import androidx.annotation.StringRes
import com.pokerarity.scanner.R

/**
 * Centralized error handling for the scan pipeline.
 *
 * Each [ScanError] carries a user-facing message and retry behavior.
 */
enum class ScanError(
    val userMessage: String,
    val isRetryable: Boolean,
    @StringRes val userMessageResourceId: Int? = null
) {
    CAPTURE_FAILED(
        "Screenshot failed. Make sure Pokemon GO is visible.",
        true
    ),
    CAPTURE_TIMEOUT(
        "Screenshot timed out. Please try again.",
        true
    ),
    PERMISSION_DENIED(
        "Screen capture permission is required. Please grant it.",
        false
    ),
    OCR_FAILED(
        "Couldn't read text from the screenshot. Try a clearer angle.",
        true
    ),
    OCR_INIT_FAILED(
        "OCR engine failed to load. Restarting.",
        false
    ),
    LOW_CONFIDENCE_RESULT(
        "Keep the Pokemon detail screen open; scroll until its name and HP/CP are visible, then retry.",
        true,
        R.string.scan_low_confidence_recovery
    ),
    NOT_POKEMON_SCREEN(
        "Open a Pokemon detail or appraisal screen before scanning.",
        false
    ),
    VISUAL_DETECTION_FAILED(
        "Visual feature detection failed.",
        true
    ),
    DATABASE_ERROR(
        "Failed to save scan. Storage may be full.",
        false
    ),
    UNKNOWN(
        "Something went wrong. Please try again.",
        true
    );

    companion object {
        const val MAX_RETRIES = 2
    }
}

sealed class ScanResult<out T> {
    data class Success<T>(val data: T) : ScanResult<T>()
    data class Failure(
        val error: ScanError,
        val cause: Throwable? = null,
        val retryCount: Int = 0
    ) : ScanResult<Nothing>() {
        fun canRetry(): Boolean = error.isRetryable && retryCount < ScanError.MAX_RETRIES
        fun nextRetry(): Failure = copy(retryCount = retryCount + 1)
    }
}
