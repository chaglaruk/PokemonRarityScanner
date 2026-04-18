package com.pokerarity.scanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.pokerarity.scanner.R
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.util.RateLimiter
import java.io.File
import java.io.FileOutputStream

/**
 * Foreground service that holds a [MediaProjection] and captures screenshots
 * on demand when an [OverlayService.ACTION_CAPTURE_REQUESTED] broadcast arrives.
 *
 * Android 14 / targetSdk 35 fix — two-phase foreground promotion:
 *
 *   Phase 1 — onCreate():
 *     startForeground(id, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
 *     No MediaProjection token exists yet; SPECIAL_USE requires no token.
 *     Manifest declares foregroundServiceType="specialUse|mediaProjection".
 *
 *   Phase 2 — setupProjection(), AFTER getMediaProjection() succeeds:
 *     startForeground(id, notification, FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
 *     Android now validates the token — promotion succeeds without SecurityException.
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_AUTO_CAPTURE = "extra_auto_capture"

        const val ACTION_SCREENSHOT_READY = "com.pokerarity.scanner.SCREENSHOT_READY"
        const val EXTRA_SCREENSHOT_PATHS = "extra_screenshot_paths"
        const val ACTION_PROJECTION_STOPPED = "com.pokerarity.scanner.PROJECTION_STOPPED"
        const val ACTION_PROJECTION_REQUIRED = "com.pokerarity.scanner.PROJECTION_REQUIRED"

        private const val CHANNEL_ID = "scanner_status_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VIRTUAL_DISPLAY_NAME = "PokeRarityCapture"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isCapturing = false
    private var projectionResultCode: Int = Activity.RESULT_CANCELED
    private var projectionResultData: Intent? = null
    private var isReinitializing = false
    private var pendingAutoCapture = false
    private val bitmapPool = BitmapPool(maxSize = 3)
    private var captureCounter = 0
    private var lastMemoryBytes = 0L
    private var pooledWidth = 0
    private var pooledHeight = 0
    
    // 🟡 SECURITY FIX: Rate limiting to prevent DOS attacks via broadcast spam
    private val captureRateLimiter = RateLimiter(maxRequestsPerMinute = 10)

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == OverlayService.ACTION_CAPTURE_REQUESTED) {
                Log.d(TAG, "captureReceiver: capture requested, ready=${mediaProjection != null && imageReader != null && virtualDisplay != null}, isCapturing=$isCapturing")
                // 🟡 SECURITY FIX: Rate limit capture requests to prevent DOS
                if (!captureRateLimiter.canProcess()) {
                    Log.w(TAG, "Capture request rate-limited (${captureRateLimiter.getRequestCount()}/min)")
                    return
                }
                captureSequence()
            }
        }
    }

    // ── Service lifecycle ────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        // Phase 1: Start foreground with SPECIAL_USE — no projection token needed.
        // Manifest declares both "specialUse|mediaProjection" so Android accepts this.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // API 29-33: no SPECIAL_USE constant yet, start without type
            startForeground(NOTIFICATION_ID, createNotification())
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        Log.d(TAG, "onCreate: foreground started (phase 1)")

        val filter = IntentFilter(OverlayService.ACTION_CAPTURE_REQUESTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(captureReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val autoCapture = intent.getBooleanExtra(EXTRA_AUTO_CAPTURE, false)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        Log.d(TAG, "onStartCommand: resultCode=$resultCode, hasResultData=${resultData != null}, autoCapture=$autoCapture")

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Missing projection data, stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        projectionResultCode = resultCode
        projectionResultData = Intent(resultData)
        pendingAutoCapture = autoCapture

        // Phase 2: Promote foreground type to MEDIA_PROJECTION BEFORE acquiring projection.
        // Android 14+ requires the service to already be in MEDIA_PROJECTION type when calling getMediaProjection().
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
                Log.d(TAG, "onStartCommand: promoted to MEDIA_PROJECTION type (phase 2)")
            } catch (e: Exception) {
                // 🟠 SECURITY FIX: Graceful degradation if phase 2 upgrade fails
                // Some devices may not support MEDIA_PROJECTION foreground type
                // Fall back to SPECIAL_USE and continue operation
                Log.w(TAG, "Phase 2 foreground upgrade failed: ${e.message}", e)
                try {
                    startForeground(
                        NOTIFICATION_ID,
                        createNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                    Log.d(TAG, "Fallback to SPECIAL_USE foreground type")
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Both foreground upgrades failed, service may be killed", fallbackEx)
                }
            }
        }

        if (mediaProjection == null || imageReader == null || virtualDisplay == null) {
            setupProjection(resultCode, resultData)
        } else {
            Log.d(TAG, "onStartCommand: projection already active")
            triggerAutoCaptureIfNeeded("existing_projection")
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) { }
        tearDown()
    }

    // ── MediaProjection setup ────────────────────────────────────────────

    private fun setupProjection(resultCode: Int, resultData: Intent) {
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = mgr.getMediaProjection(resultCode, resultData)
            mediaProjection = projection

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection stopped externally")
                    tearDown()
                }
            }, handler)

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            if (width != pooledWidth || height != pooledHeight) {
                bitmapPool.clear()
                pooledWidth = width
                pooledHeight = height
            }

            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

            virtualDisplay = projection.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null, handler
            )

            Log.d(TAG, "Projection ready: ${width}x${height} @ ${density}dpi")
            triggerAutoCaptureIfNeeded("setup_projection")
        } catch (e: Exception) {
            // 🟠 SECURITY FIX: Error handling for projection setup failures
            Log.e(TAG, "setupProjection failed", e)
            tearDown()
            notifyProjectionRequired()
        }
    }

    // ── Capture ──────────────────────────────────────────────────────────

    private fun captureSequence() {
        if (isReinitializing) return
        if (!ensureProjectionReady()) {
            Log.w(TAG, "captureSequence aborted: projection not ready")
            notifyProjectionRequired()
            return
        }
        if (isCapturing) return
        isCapturing = true

        val paths = mutableListOf<String>()
        val captureCount = 2
        val intervalMs = 80L

        fun doCapture(count: Int) {
            if (count <= 0) {
                isCapturing = false
                if (paths.isNotEmpty()) {
                    Log.d(TAG, "captureSequence complete: ${paths.size} frames ready")
                    sendBroadcast(Intent(ACTION_SCREENSHOT_READY).apply {
                        setPackage(packageName)
                        putStringArrayListExtra(EXTRA_SCREENSHOT_PATHS, ArrayList(paths))
                    })
                } else {
                    Log.e(TAG, "captureSequence complete: no frames captured")
                    broadcastError()
                }
                return
            }

            try {
                val image = imageReader?.acquireLatestImage()
                var bitmap: Bitmap? = null
                var croppedBitmap: Bitmap? = null
                try {
                    if (image != null) {
                        val plane = image.planes[0]
                        val buffer = plane.buffer
                        val pixelStride = plane.pixelStride
                        val rowStride = plane.rowStride
                        val rowPadding = rowStride - pixelStride * image.width

                        val paddedWidth = image.width + rowPadding / pixelStride
                        val sourceBitmap = bitmapPool.obtain(
                            paddedWidth,
                            image.height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap = sourceBitmap
                        buffer.rewind()
                        sourceBitmap.copyPixelsFromBuffer(buffer)

                        val targetBitmap = bitmapPool.obtain(
                            resources.displayMetrics.widthPixels,
                            resources.displayMetrics.heightPixels,
                            Bitmap.Config.ARGB_8888
                        )
                        croppedBitmap = targetBitmap
                        Canvas(targetBitmap).drawBitmap(sourceBitmap, 0f, 0f, null)

                        val file = File(cacheDir, "scan_${System.currentTimeMillis()}_${captureCount - count}.png")
                        FileOutputStream(file).use { out ->
                            targetBitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                        }
                        bitmapPool.release(targetBitmap)
                        croppedBitmap = null
                        bitmapPool.release(sourceBitmap)
                        bitmap = null
                        paths.add(file.absolutePath)
                        Log.d(TAG, "Frame ${captureCount - count} saved: ${file.name}")
                        captureCounter++
                        logMemoryIfNeeded()
                    }
                } finally {
                    croppedBitmap?.let(bitmapPool::release)
                    bitmap?.let(bitmapPool::release)
                    image?.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame capture failed", e)
            }

            handler.postDelayed({ doCapture(count - 1) }, intervalMs)
        }

        handler.postDelayed({ doCapture(captureCount) }, 20)
    }

    private fun broadcastError() {
        Log.e(TAG, "broadcastError: screenshot ready broadcast sent without paths")
        sendBroadcast(Intent(ACTION_SCREENSHOT_READY).apply {
            setPackage(packageName)
        })
    }

    private fun notifyProjectionStopped() {
        sendBroadcast(Intent(ACTION_PROJECTION_STOPPED).apply {
            setPackage(packageName)
        })
    }

    private fun notifyProjectionRequired() {
        Log.w(TAG, "notifyProjectionRequired: projection token is missing or invalid")
        sendBroadcast(Intent(ACTION_PROJECTION_REQUIRED).apply {
            setPackage(packageName)
        })
    }

    private fun triggerAutoCaptureIfNeeded(reason: String) {
        if (!pendingAutoCapture) return
        pendingAutoCapture = false
        Log.d(TAG, "triggerAutoCaptureIfNeeded: reason=$reason")
        handler.postDelayed({ captureSequence() }, 180L)
    }

    private fun ensureProjectionReady(): Boolean {
        if (mediaProjection != null && imageReader != null && virtualDisplay != null) return true
        if (isReinitializing) return false
        val data = projectionResultData ?: return false
        val code = projectionResultCode
        isReinitializing = true
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
            setupProjection(code, data)
            mediaProjection != null && imageReader != null && virtualDisplay != null
        } catch (e: Exception) {
            Log.e(TAG, "ensureProjectionReady failed", e)
            false
        } finally {
            isReinitializing = false
        }
    }

    // ── Teardown ─────────────────────────────────────────────────────────

    private fun tearDown() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        bitmapPool.clear()
        try { mediaProjection?.stop() } catch (_: Exception) { }
        mediaProjection = null
    }

    private fun logMemoryIfNeeded() {
        if (!BuildConfig.DEBUG || captureCounter % 10 != 0) return
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        Log.d(TAG, "Memory monitor: used=${used / 1024 / 1024}MB total=${runtime.totalMemory() / 1024 / 1024}MB")
        if (lastMemoryBytes > 0L && used - lastMemoryBytes > 24L * 1024L * 1024L) {
            System.gc()
            Log.d(TAG, "Memory monitor triggered debug GC")
        }
        lastMemoryBytes = used
    }

    // ── Notifications ────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PokeRarityScanner",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Scanner service is active"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val largeIcon = runCatching {
            ContextCompat.getDrawable(this, R.drawable.pokeball_overlay)?.toBitmap(96, 96)
        }.getOrNull()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PokeRarityScanner")
            .setContentText("Scanner active")
            .setSmallIcon(R.drawable.ic_pokeball)
            .setLargeIcon(largeIcon)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}
