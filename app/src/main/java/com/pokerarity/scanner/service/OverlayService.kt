package com.pokerarity.scanner.service

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import androidx.core.app.NotificationCompat
import com.pokerarity.scanner.R

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_CAPTURE_REQUESTED = "com.pokerarity.scanner.CAPTURE_REQUESTED"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_PRESS_DELAY = 500L
        private const val CLICK_THRESHOLD_DP = 10
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var closeView: View? = null
    private var debugOverlayView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isLongPress = false
    private val handler = Handler(Looper.getMainLooper())

    private val longPressRunnable = Runnable {
        isLongPress = true
        showCloseButton()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        createNotificationChannel()
        try {
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            stopSelf()
            return
        }

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlayView()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val showDebug = intent?.getBooleanExtra("EXTRA_SHOW_DEBUG", false) ?: false
        if (showDebug) {
            addDebugOverlay()
        } else {
            removeDebugOverlay()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun addDebugOverlay() {
        if (debugOverlayView != null) return
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        debugOverlayView = com.pokerarity.scanner.ui.debug.DebugOverlayView(this)
        windowManager.addView(debugOverlayView, params)
    }

    private fun removeDebugOverlay() {
        debugOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) { }
            debugOverlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) { }
        }
        removeCloseButton()
    }

    private fun addOverlayView() {
        val sizePx = (72 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        Log.d(TAG, "addOverlayView: type=${params.type} flags=${params.flags} x=${params.x} y=${params.y}")

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)
        setupTouchListener(params)
        try {
            windowManager.addView(overlayView, params)
            Log.d(TAG, "windowManager.addView OK")

            overlayView.post {
                Log.d(
                    TAG,
                    "overlayView attached=${overlayView.isAttachedToWindow} size=${overlayView.width}x${overlayView.height} alpha=${overlayView.alpha} shown=${overlayView.isShown}"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "windowManager.addView failed", e)
            stopSelf()
        }
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupTouchListener(params: WindowManager.LayoutParams) {
        val clickThreshold = CLICK_THRESHOLD_DP * resources.displayMetrics.density

        overlayView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isLongPress = false
                    handler.postDelayed(longPressRunnable, LONG_PRESS_DELAY)
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    // Cancel long press if user starts dragging
                    if (dx * dx + dy * dy > clickThreshold * clickThreshold) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                    params.x = initialX + dx.toInt()
                    params.y = initialY + dy.toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    val moved = dx * dx + dy * dy > clickThreshold * clickThreshold

                    // Tap detected (no drag, no long press)
                    if (!moved && !isLongPress) {
                        onOverlayClicked()
                    }

                    // Hide close button if it wasn't a long press
                    if (!isLongPress) {
                        removeCloseButton()
                    }

                    // Snap to nearest screen edge
                    snapToEdge(params)
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Animate the overlay to the nearest horizontal screen edge.
     */
    private fun snapToEdge(params: WindowManager.LayoutParams) {
        val screenWidth = resources.displayMetrics.widthPixels
        val viewWidth = overlayView.measuredWidth
        val centerX = params.x + viewWidth / 2
        val targetX = if (centerX < screenWidth / 2) 0 else screenWidth - viewWidth

        val animator = ValueAnimator.ofInt(params.x, targetX)
        animator.duration = 300
        animator.interpolator = OvershootInterpolator(1.2f)
        animator.addUpdateListener { anim ->
            params.x = anim.animatedValue as Int
            try {
                windowManager.updateViewLayout(overlayView, params)
            } catch (_: Exception) { }
        }
        animator.start()
    }

    /**
     * Called when the user taps (not drags) the overlay button.
     * Plays a pulse animation and broadcasts a capture request.
     */
    private fun onOverlayClicked() {
        // Pulse animation feedback
        overlayView.animate()
            .scaleX(0.8f).scaleY(0.8f)
            .setDuration(100)
            .withEndAction {
                overlayView.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(100)
                    .start()
            }.start()

        // Broadcast capture request to ScreenCaptureManager (future FAZ)
        sendBroadcast(Intent(ACTION_CAPTURE_REQUESTED).apply {
            setPackage(packageName)
        })
    }

    /**
     * Show the close button at the bottom of the screen on long press.
     */
    private fun showCloseButton() {
        removeCloseButton()

        val closeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = 100
        }

        closeView = LayoutInflater.from(this).inflate(R.layout.overlay_close_button, null)
        closeView?.setOnClickListener {
            stopSelf()
        }
        windowManager.addView(closeView, closeParams)

        // Haptic feedback for long press
        overlayView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    /**
     * Remove the close button from the window.
     */
    private fun removeCloseButton() {
        closeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) { }
            closeView = null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Overlay Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "PokeRarityScanner overlay is active"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PokeRarityScanner")
            .setContentText("Overlay active — tap PokeBall to scan")
            .setSmallIcon(R.drawable.ic_pokeball)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
