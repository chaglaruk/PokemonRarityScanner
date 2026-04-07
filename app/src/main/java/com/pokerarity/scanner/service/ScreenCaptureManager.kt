package com.pokerarity.scanner.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.util.Log
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher

/**
 * Manages the [MediaProjection] lifecycle.
 *
 * Usage from an Activity:
 *  1. Register a launcher with [createLauncher].
 *  2. Call [requestProjection] to prompt the user.
 *  3. On result, call [handleResult] which stores the projection intent.
 *  4. Call [getProjection] to obtain the live [MediaProjection].
 *  5. Call [release] when done.
 */
object ScreenCaptureManager {

    private const val TAG = "ScreenCaptureManager"

    private var resultCode: Int = Activity.RESULT_CANCELED
    private var resultData: Intent? = null

    /** Stored projection – only one active at a time. */
    private var projection: MediaProjection? = null

    /** `true` after the user grants the projection prompt. */
    val isGranted: Boolean get() = resultData != null

    // ── API ──────────────────────────────────────────────────────────────

    /**
     * Launch the system cast / projection permission dialog.
     */
    fun requestProjection(launcher: ActivityResultLauncher<Intent>, context: Context) {
        val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(mgr.createScreenCaptureIntent())
    }

    /**
     * Call from the ActivityResultCallback.  Returns `true` if the user granted.
     */
    fun handleResult(result: ActivityResult): Boolean {
        return if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            resultCode = result.resultCode
            resultData = result.data
            Log.d(TAG, "Projection granted (resultCode=$resultCode, hasData=${resultData != null})")
            true
        } else {
            resultCode = Activity.RESULT_CANCELED
            resultData = null
            Log.w(TAG, "Projection denied/canceled (resultCode=${result.resultCode}, dataIsNull=${result.data == null})")
            false
        }
    }

    /**
     * Obtain or create a [MediaProjection] from the stored grant.
     * Returns `null` if [handleResult] was never called successfully.
     */
    fun getProjection(context: Context): MediaProjection? {
        if (resultData == null) return null
        if (projection == null) {
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = mgr.getMediaProjection(resultCode, resultData!!)
        }
        return projection
    }

    /**
     * Build the intent used to start [ScreenCaptureService].
     * The service needs the result code + data to create its own projection
     * (required on Android 14+, where the service must call startForeground
     * before obtaining the projection).
     */
    fun buildServiceIntent(context: Context, autoCapture: Boolean = false): Intent? {
        val data = resultData ?: return null
        // Defensive copy: some OEM builds can behave oddly if the original intent is reused.
        val dataCopy = Intent(data)
        Log.d(
            TAG,
            "Building ScreenCaptureService intent (resultCode=$resultCode, hasData=${resultData != null}, autoCapture=$autoCapture, dataExtras=${dataCopy.extras?.keySet()?.size ?: 0})"
        )
        return Intent(context, ScreenCaptureService::class.java).apply {
            putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, dataCopy)
            putExtra(ScreenCaptureService.EXTRA_AUTO_CAPTURE, autoCapture)
        }
    }

    /**
     * Release the projection and clear stored state.
     */
    fun release() {
        try { projection?.stop() } catch (_: Exception) { }
        projection = null
        resultData = null
        resultCode = Activity.RESULT_CANCELED
    }
}
