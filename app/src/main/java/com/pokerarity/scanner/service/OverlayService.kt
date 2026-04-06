package com.pokerarity.scanner.service

import android.animation.ValueAnimator
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.RarityAnalysisItem
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.ScanDecisionSupport
import com.pokerarity.scanner.data.model.IvSolveMode
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.data.model.buildAnalysisItems
import com.pokerarity.scanner.data.model.normalizeIvText
import com.pokerarity.scanner.data.model.pokemonFromScanExtras
import com.pokerarity.scanner.ui.overlay.ScanResultOverlayCard
import com.pokerarity.scanner.ui.result.ResultActivity
import com.pokerarity.scanner.ui.share.ResultShareRenderer
import com.pokerarity.scanner.ui.theme.PokeRarityTheme
import androidx.compose.foundation.isSystemInDarkTheme

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_CAPTURE_REQUESTED = "com.pokerarity.scanner.CAPTURE_REQUESTED"
        const val ACTION_SHOW_RESULT = "com.pokerarity.scanner.SHOW_RESULT"
        const val ACTION_DISMISS_RESULT = "com.pokerarity.scanner.DISMISS_RESULT"
        private const val CHANNEL_ID = "scanner_status_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_PRESS_DELAY = 500L
        private const val CLICK_THRESHOLD_DP = 10
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var closeView: View? = null
    private var debugOverlayView: View? = null
    private var resultOverlayView: View? = null

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isLongPress = false
    private val handler = Handler(Looper.getMainLooper())
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val serviceViewModelStore = ViewModelStore()

    private val projectionRequiredReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ScreenCaptureService.ACTION_PROJECTION_REQUIRED) {
                Log.w(TAG, "Projection required; launching permission flow")
                val permIntent = Intent(
                    this@OverlayService,
                    com.pokerarity.scanner.ui.permission.ProjectionPermissionActivity::class.java
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(com.pokerarity.scanner.ui.permission.ProjectionPermissionActivity.EXTRA_AUTO_CAPTURE, true)
                }
                startActivity(permIntent)
            }
        }
    }

    private val longPressRunnable = Runnable {
        isLongPress = true
        showCloseButton()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateRegistryController.savedStateRegistry

    override val viewModelStore: ViewModelStore
        get() = serviceViewModelStore

    override fun onCreate() {
        super.onCreate()
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
        Log.d(TAG, "onCreate")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlayView()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        val filter = IntentFilter(ScreenCaptureService.ACTION_PROJECTION_REQUIRED)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(projectionRequiredReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(projectionRequiredReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW_RESULT -> {
                showResultOverlay(intent)
                return START_STICKY
            }
            ACTION_DISMISS_RESULT -> {
                dismissResultOverlay()
                return START_STICKY
            }
        }

        val showDebug = intent?.getBooleanExtra("EXTRA_SHOW_DEBUG", false) ?: false
        if (showDebug) {
            addDebugOverlay()
        } else {
            removeDebugOverlay()
        }
        return START_STICKY
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
            } catch (_: Exception) {
            }
            debugOverlayView = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        serviceViewModelStore.clear()
        try {
            unregisterReceiver(projectionRequiredReceiver)
        } catch (_: Exception) {
        }
        dismissResultOverlay()
        if (::overlayView.isInitialized) {
            try {
                windowManager.removeView(overlayView)
            } catch (_: Exception) {
            }
        }
        removeCloseButton()
        removeDebugOverlay()
    }

    private fun addOverlayView() {
        
        val sizePx = (72 * resources.displayMetrics.density).toInt()
        val screenHeight = resources.displayMetrics.heightPixels
        val marginPx = (16 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = marginPx
            y = (screenHeight * 0.68f).toInt()
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

    private fun showResultOverlay(intent: Intent) {
        dismissResultOverlay()
        removeCloseButton()
        if (::overlayView.isInitialized) {
            overlayView.visibility = View.GONE
        }

        val composeView = createResultComposeView(intent)
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val sideMargin = ((screenWidth * 0.02f).toInt()).coerceAtLeast((resources.displayMetrics.density * 8).toInt())
        val bottomMargin = (resources.displayMetrics.density * 12).toInt()

        val params = WindowManager.LayoutParams(
            (screenWidth * 0.96f).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = sideMargin
            y = (screenHeight * 0.55f).toInt()
        }

        setupResultDrag(composeView, params)
        resultOverlayView = composeView
        try {
            windowManager.addView(composeView, params)
            composeView.post {
                val targetX = ((screenWidth - composeView.width) / 2).coerceAtLeast(0)
                val targetY = (screenHeight - composeView.height - bottomMargin).coerceAtLeast(0)
                params.x = targetX
                params.y = targetY
                runCatching { windowManager.updateViewLayout(composeView, params) }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add result overlay", e)
            resultOverlayView = null
            if (::overlayView.isInitialized) {
                overlayView.visibility = View.VISIBLE
            }
        }
    }

    private fun createResultComposeView(intent: Intent): ComposeView {
        val pokemon = buildOverlayPokemon(intent)
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setViewTreeViewModelStoreOwner(this@OverlayService)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                PokeRarityTheme(darkTheme = isSystemInDarkTheme()) {
                    ScanResultOverlayCard(
                        pokemon = pokemon,
                        onDismiss = { dismissResultOverlay() },
                        onShare = { shareResult(intent, pokemon) },
                        onSave = {
                            Toast.makeText(this@OverlayService, R.string.saved, Toast.LENGTH_SHORT).show()
                            dismissResultOverlay()
                        },
                        onFeedback = { category ->
                            submitFeedback(intent, category)
                        }
                    )
                }
            }
        }
    }

    private fun buildOverlayPokemon(intent: Intent): Pokemon {
        val basePokemon = pokemonFromScanExtras(
            name = intent.getStringExtra(ResultActivity.EXTRA_POKEMON_NAME).orEmpty(),
            cp = intent.getIntExtra(ResultActivity.EXTRA_CP, 0),
            hp = intent.getIntExtra(ResultActivity.EXTRA_HP, 0).takeIf { it > 0 },
            score = intent.getIntExtra(ResultActivity.EXTRA_SCORE, 0),
            tier = intent.getStringExtra(ResultActivity.EXTRA_TIER).orEmpty(),
            isShiny = intent.getBooleanExtra(ResultActivity.EXTRA_IS_SHINY, false),
            isLucky = intent.getBooleanExtra(ResultActivity.EXTRA_IS_LUCKY, false),
            hasCostume = intent.getBooleanExtra(ResultActivity.EXTRA_HAS_COSTUME, false),
            hasSpecialForm = intent.getBooleanExtra(ResultActivity.EXTRA_HAS_SPECIAL_FORM, false),
            isShadow = intent.getBooleanExtra(ResultActivity.EXTRA_IS_SHADOW, false),
            dateText = intent.getStringExtra(ResultActivity.EXTRA_DATE),
            ivText = normalizeIvText(intent.getStringExtra(ResultActivity.EXTRA_IV_ESTIMATE)) ?: "Hesaplanamadı",
            ivSolveMode = intent.getStringExtra(ResultActivity.EXTRA_IV_SOLVE_MODE)?.let {
                runCatching { IvSolveMode.valueOf(it) }.getOrNull()
            },
            ivSignalsUsed = intent.getStringArrayListExtra(ResultActivity.EXTRA_IV_SIGNALS).orEmpty(),
            hasArcSignal = intent.getBooleanExtra(ResultActivity.EXTRA_HAS_ARC, false),
            decisionSupport = parseDecisionSupport(intent),
            telemetryUploadId = intent.getStringExtra(ResultActivity.EXTRA_TELEMETRY_UPLOAD_ID),
        )
        return basePokemon.copy(
            analysis = buildOverlayAnalysis(intent, basePokemon.rarityScore)
        )
    }

    private fun parseDecisionSupport(intent: Intent): ScanDecisionSupport? {
        return ScanDecisionSupport(
            eventConfidenceCode = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_CODE).orEmpty(),
            eventConfidenceLabel = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_LABEL).orEmpty(),
            eventConfidenceDetail = intent.getStringExtra(ResultActivity.EXTRA_EVENT_CONFIDENCE_DETAIL).orEmpty(),
            scanConfidenceScore = intent.getIntExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_SCORE, 0),
            scanConfidenceLabel = intent.getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_LABEL).orEmpty(),
            scanConfidenceDetail = intent.getStringExtra(ResultActivity.EXTRA_SCAN_CONFIDENCE_DETAIL).orEmpty(),
            mismatchGuardTitle = intent.getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_TITLE),
            mismatchGuardDetail = intent.getStringExtra(ResultActivity.EXTRA_MISMATCH_GUARD_DETAIL),
            whyNotExact = intent.getStringExtra(ResultActivity.EXTRA_WHY_NOT_EXACT),
        ).takeIf { it.hasVisibleUiContent() }
    }

    private fun buildOverlayAnalysis(
        intent: Intent,
        fallbackScore: Int,
    ): List<RarityAnalysisItem> {
        val breakdownKeys = intent.getStringArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_KEYS).orEmpty()
        val breakdownValues = intent.getIntegerArrayListExtra(ResultActivity.EXTRA_BREAKDOWN_VALUES).orEmpty()
        val explanations = intent.getStringArrayListExtra(ResultActivity.EXTRA_EXPLANATIONS).orEmpty()
        return buildAnalysisItems(
            breakdownKeys = breakdownKeys,
            breakdownValues = breakdownValues,
            explanations = explanations,
            fallbackScore = fallbackScore,
        )
    }

    private fun shareResult(intent: Intent, pokemon: Pokemon) {
        val shareText = getString(
            R.string.share_result_text,
            pokemon.name,
            pokemon.rarityScore,
        )
        val imageUri = ResultShareRenderer.renderPokemonCardToImageUri(
            context = this,
            pokemon = pokemon,
            fileName = "scan_result_overlay.png"
        )
        dismissResultOverlay()
        val shareBaseIntent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            imageUri?.let {
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            type = if (imageUri != null) "image/png" else "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val shareIntent = Intent.createChooser(shareBaseIntent, getString(R.string.share_via)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(shareIntent)
    }

    private fun submitFeedback(intent: Intent, category: String) {
        val uploadId = intent.getStringExtra(ResultActivity.EXTRA_TELEMETRY_UPLOAD_ID)
        if (uploadId.isNullOrBlank()) {
            Toast.makeText(this, getString(R.string.feedback_unavailable), Toast.LENGTH_SHORT).show()
            return
        }
        ScanTelemetryCoordinator.getInstance(this).submitFeedback(uploadId, category)
        Toast.makeText(this, getString(R.string.feedback_sent, category), Toast.LENGTH_SHORT).show()
    }

    private fun createShareImageUri(): android.net.Uri? {
        return ResultShareRenderer.captureViewToImageUri(
            context = this,
            view = resultOverlayView,
            fileName = "scan_result_overlay.png"
        )
    }

    @Suppress("ClickableViewAccessibility")
    private fun setupResultDrag(handleView: View, params: WindowManager.LayoutParams) {
        handleView.setOnTouchListener(object : View.OnTouchListener {
            var startRawX = 0f
            var startRawY = 0f
            var startX = 0
            var startY = 0
            var isDragging = false
            val dragThreshold = 12 * resources.displayMetrics.density
            val topBlockedHeight = 64 * resources.displayMetrics.density
            val bottomBlockedHeight = 88 * resources.displayMetrics.density

            override fun onTouch(view: View, event: MotionEvent): Boolean {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        val maxDragY = (view.height - bottomBlockedHeight).coerceAtLeast(0f)
                        if (event.y < topBlockedHeight || event.y > maxDragY) return false
                        startRawX = event.rawX
                        startRawY = event.rawY
                        startX = params.x
                        startY = params.y
                        isDragging = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = event.rawX - startRawX
                        val dy = event.rawY - startRawY
                        if (!isDragging && dx * dx + dy * dy <= dragThreshold * dragThreshold) {
                            return true
                        }
                        isDragging = true
                        val nextX = startX + (event.rawX - startRawX).toInt()
                        val nextY = startY + (event.rawY - startRawY).toInt()
                        val maxX = (resources.displayMetrics.widthPixels - view.width).coerceAtLeast(0)
                        val maxY = (resources.displayMetrics.heightPixels - view.height).coerceAtLeast(0)
                        params.x = nextX.coerceIn(0, maxX)
                        params.y = nextY.coerceIn(0, maxY)
                        resultOverlayView?.let { windowManager.updateViewLayout(it, params) }
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        return isDragging
                    }
                }
                return false
            }
        })
    }

    private fun dismissResultOverlay() {
        resultOverlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        resultOverlayView = null
        if (::overlayView.isInitialized) {
            overlayView.visibility = View.VISIBLE
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

                    if (!moved && !isLongPress) {
                        onOverlayClicked()
                    }

                    if (!isLongPress) {
                        removeCloseButton()
                    }

                    if (moved) {
                        snapToEdge(params)
                    }
                    true
                }

                else -> false
            }
        }
    }

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
            } catch (_: Exception) {
            }
        }
        animator.start()
    }

    private fun onOverlayClicked() {
        overlayView.animate()
            .scaleX(0.8f).scaleY(0.8f)
            .setDuration(100)
            .withEndAction {
                overlayView.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(100)
                    .start()
            }.start()

        sendBroadcast(Intent(ACTION_CAPTURE_REQUESTED).apply {
            setPackage(packageName)
        })
    }

    /**
     * Check if the PokeRarityScanner app is currently in the foreground.
     * Used to prevent overlay attacks and phishing.
     * 🟡 SECURITY: Only show overlay when user is directly interacting with our app.
     */
    private fun isAppInForeground(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningAppProcesses = activityManager.runningAppProcesses ?: return false
            
            val myUid = android.os.Process.myUid()
            for (appProcess in runningAppProcesses) {
                if (appProcess.uid == myUid && 
                    appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check if app is in foreground", e)
            false  // Fail securely - don't show overlay if we can't verify
        }
    }

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
        closeView?.findViewById<View>(R.id.btnCloseOverlay)?.setOnClickListener {
            stopSelf()
        }
        closeView?.findViewById<View>(R.id.btnExitApp)?.setOnClickListener {
            exitApp()
        }
        windowManager.addView(closeView, closeParams)

        overlayView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    private fun removeCloseButton() {
        closeView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
            closeView = null
        }
    }

    private fun exitApp() {
        dismissResultOverlay()
        removeCloseButton()
        try {
            stopService(Intent(this, ScreenCaptureService::class.java))
        } catch (_: Exception) {
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= 24) {
                stopForeground(Service.STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        } catch (_: Exception) {
        }
        stopSelf()
        Handler(Looper.getMainLooper()).postDelayed({
            android.os.Process.killProcess(android.os.Process.myPid())
        }, 200)
    }

    private fun getTierColor(tier: RarityTier): Int {
        return when (tier) {
            RarityTier.COMMON -> ContextCompat.getColor(this, R.color.tier_common)
            RarityTier.UNCOMMON -> ContextCompat.getColor(this, R.color.tier_uncommon)
            RarityTier.RARE -> ContextCompat.getColor(this, R.color.tier_rare)
            RarityTier.EPIC -> ContextCompat.getColor(this, R.color.tier_epic)
            RarityTier.LEGENDARY -> ContextCompat.getColor(this, R.color.tier_legendary)
            RarityTier.MYTHICAL -> ContextCompat.getColor(this, R.color.tier_mythical)
            RarityTier.GOD_TIER -> ContextCompat.getColor(this, R.color.tier_god_tier)
        }
    }
}
