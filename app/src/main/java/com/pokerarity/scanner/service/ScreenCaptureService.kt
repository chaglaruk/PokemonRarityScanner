package com.pokerarity.scanner.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
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
import com.pokerarity.scanner.R
import java.io.File
import java.io.FileOutputStream

/**
 * Foreground service that holds a [MediaProjection] and captures screenshots
 * on demand when an [OverlayService.ACTION_CAPTURE_REQUESTED] broadcast arrives.
 *
 * Start with extras [EXTRA_RESULT_CODE] and [EXTRA_RESULT_DATA] obtained from
 * [ScreenCaptureManager.buildServiceIntent].
 */
class ScreenCaptureService : Service() {

    companion object {
        private const val TAG = "ScreenCaptureService"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"

        /** Broadcast sent after a screenshot sequence is ready. */
        const val ACTION_SCREENSHOT_READY = "com.pokerarity.scanner.SCREENSHOT_READY"
        const val EXTRA_SCREENSHOT_PATHS = "extra_screenshot_paths"

        private const val CHANNEL_ID = "capture_channel"
        private const val NOTIFICATION_ID = 1002
        private const val VIRTUAL_DISPLAY_NAME = "PokeRarityCapture"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isCapturing = false

    // ── BroadcastReceiver that listens for the overlay button tap ─────────

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == OverlayService.ACTION_CAPTURE_REQUESTED) {
                captureSequence()
            }
        }
    }

    // ── Service lifecycle ────────────────────────────────────────────────

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        // Register for capture requests from OverlayService
        val filter = IntentFilter(OverlayService.ACTION_CAPTURE_REQUESTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(captureReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val resultData: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }

        Log.d(TAG, "onStartCommand: resultCode=$resultCode, hasResultData=${resultData != null}, extras=${intent.extras?.keySet()?.joinToString()}")

        if (resultCode != Activity.RESULT_OK || resultData == null) {
            Log.e(TAG, "Missing projection data, stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        setupProjection(resultCode, resultData)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) { }
        tearDown()
    }

    // ── MediaProjection setup ────────────────────────────────────────────

    private fun setupProjection(resultCode: Int, resultData: Intent) {
        val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mgr.getMediaProjection(resultCode, resultData)

        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                tearDown()
            }
        }, handler)

        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME,
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null, handler
        )

        Log.d(TAG, "Projection ready: ${width}x${height} @ ${density}dpi")
    }

    // ── Capture ──────────────────────────────────────────────────────────

    private fun captureSequence() {
        if (isCapturing) return
        isCapturing = true

        val paths = mutableListOf<String>()
        val captureCount = 4
        val intervalMs = 150L

        fun doCapture(count: Int) {
            if (count <= 0) {
                isCapturing = false
                if (paths.isNotEmpty()) {
                    sendBroadcast(Intent(ACTION_SCREENSHOT_READY).apply {
                        setPackage(packageName)
                        putStringArrayListExtra(EXTRA_SCREENSHOT_PATHS, ArrayList(paths))
                    })
                } else {
                    broadcastError()
                }
                return
            }

            try {
                val image = imageReader?.acquireLatestImage()
                if (image != null) {
                    val plane = image.planes[0]
                    val buffer = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride = plane.rowStride
                    val rowPadding = rowStride - pixelStride * image.width

                    val bitmap = Bitmap.createBitmap(
                        image.width + rowPadding / pixelStride,
                        image.height,
                        Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    image.close()

                    val croppedBitmap = Bitmap.createBitmap(
                        bitmap, 0, 0,
                        resources.displayMetrics.widthPixels,
                        resources.displayMetrics.heightPixels
                    )
                    if (croppedBitmap != bitmap) bitmap.recycle()

                    val file = File(cacheDir, "scan_${System.currentTimeMillis()}_${captureCount - count}.png")
                    FileOutputStream(file).use { out ->
                        croppedBitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
                    }
                    croppedBitmap.recycle()
                    paths.add(file.absolutePath)
                    Log.d(TAG, "Frame ${captureCount - count} saved: ${file.name}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Frame capture failed", e)
            }

            handler.postDelayed({ doCapture(count - 1) }, intervalMs)
        }

        // Start capture after a small initial delay
        handler.postDelayed({ doCapture(captureCount) }, 50)
    }

    private fun broadcastError() {
        sendBroadcast(Intent(ACTION_SCREENSHOT_READY).apply {
            setPackage(packageName)
        })
    }

    // ── Teardown ─────────────────────────────────────────────────────────

    private fun tearDown() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        try { mediaProjection?.stop() } catch (_: Exception) { }
        mediaProjection = null
    }

    // ── Notifications ────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active while screen capture is running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PokeRarityScanner")
            .setContentText("Screen capture active")
            .setSmallIcon(R.drawable.ic_pokeball)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
