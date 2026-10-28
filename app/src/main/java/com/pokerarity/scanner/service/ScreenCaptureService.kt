package com.pokerarity.scanner.service

import com.pokerarity.scanner.util.perf.DebugScanTiming
import com.pokerarity.scanner.util.perf.ScanTimingStage

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.pokerarity.scanner.R
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.ui.main.MainActivity
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
        const val EXTRA_OWNED_FRAMES = "extra_owned_frames"
        const val ACTION_PROJECTION_STOPPED = "com.pokerarity.scanner.PROJECTION_STOPPED"
        const val ACTION_PROJECTION_REQUIRED = "com.pokerarity.scanner.PROJECTION_REQUIRED"
        const val ACTION_STOP_SCANNER = "com.pokerarity.scanner.STOP_SCANNER"
        const val INTERNAL_BROADCAST_PERMISSION = "com.pokerarity.scanner.permission.INTERNAL_BROADCAST"

        private const val CHANNEL_ID = "scanner_status_channel"
        private const val NOTIFICATION_ID = 1001
        private const val VIRTUAL_DISPLAY_NAME = "PokeRarityCapture"
        private const val CAPTURE_FRAME_COUNT = 2
        private const val CAPTURE_INTERVAL_MS = 80L
        private const val CAPTURE_INITIAL_DELAY_MS = 20L
        private const val PNG_QUALITY = 85
        private const val OWNED_PENDING_TIMEOUT_MS = 60_000L
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private val handler = Handler(Looper.getMainLooper())
    @Volatile private var isCapturing = false
    private var activeOwnership: ScanRequestToken? = null
    // Receiver, capture callbacks and teardown are confined to the main looper.
    // Keep the pending slot reserved until its posted drain actually starts.
    private var pendingCapture = false
    private var pendingCaptureSince = 0L
    // Ownership of the one reserved pending capture; first accepted pending identity wins.
    private var pendingOwnership: ScanRequestToken? = null
    // The coordinator permits only one live active logical request, so one completed
    // capture token is enough to make retransmission idempotent during its OCR lifetime.
    private var completedCaptureOwnership: ScanRequestToken? = null
    private var captureGeneration = 0L
    private var captureSequenceId = 0L
    private var projectionResultCode: Int = Activity.RESULT_CANCELED
    private var projectionResultData: Intent? = null
    @Volatile private var isReinitializing = false
    private var pendingAutoCapture = false
    private val bitmapPool = BitmapPool(maxSize = 3)
    private var captureCounter = 0
    private var lastMemoryBytes = 0L
    private var pooledWidth = 0
    private var pooledHeight = 0
    private var contentWidth = 0
    private var contentHeight = 0
    private var nativeDrainPosted = false
    
    // 🟡 SECURITY FIX: Rate limiting to prevent DOS attacks via broadcast spam
    private val captureRateLimiter = RateLimiter(maxRequestsPerMinute = 10)

    private val captureReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == OverlayService.ACTION_CAPTURE_REQUESTED) {
                Log.d(TAG, "captureReceiver: capture requested, ready=${mediaProjection != null && imageReader != null && virtualDisplay != null}, isCapturing=$isCapturing")
                val ownership = intent.parseOwnership()
                when {
                    ownership != null && isAlreadyOwnedCapture(ownership) -> {
                        // Coalesced taps may rebroadcast their surviving token. Once that
                        // token already owns a capture slot or completed its frame capture,
                        // the duplicate must not consume quota, recapture, or fail live OCR.
                        Log.d(TAG, "captureReceiver: duplicate owned request ignored")
                    }
                    ownership != null && !ScanRequests.coordinator.isLiveRequest(ownership) -> {
                        // Superseded or completed broadcasts must not spend a rate-limit
                        // permit that belongs to a newer live request queued behind them.
                        Log.d(TAG, "captureReceiver: stale owned request ignored")
                    }
                    !captureRateLimiter.canProcess("ScreenCaptureService") -> {
                        // 🟡 SECURITY FIX: Rate limit capture requests to prevent DOS.
                        // Production USER/RETRY/AUTO requests already own a logical token before
                        // reaching the service. If rate limiting refuses that owned request,
                        // terminalize it so request ownership can never remain live indefinitely.
                        // Truly bare legacy/test broadcasts have no logical request yet and are
                        // therefore still rejected before synthesis.
                        Log.w(TAG, "Capture request rate-limited (${captureRateLimiter.getRequestCount()}/min)")
                        terminalizeOwnedCaptureRefusal(intent, "rate_limited")
                    }
                    else -> resolveCaptureOwnership(intent)?.let(::captureSequence)
                }
            }
        }
    }

    private fun isAlreadyOwnedCapture(ownership: ScanRequestToken): Boolean {
        val alreadyCaptured = ownership == activeOwnership ||
            ownership == pendingOwnership ||
            ownership == completedCaptureOwnership
        return alreadyCaptured && ScanRequests.coordinator.isLiveRequest(ownership)
    }

    /**
     * Phase 2F: resolve (or, for bare internal broadcasts, synthesize) the request
     * ownership for this capture. Null means the capture is refused: either the scanner
     * is stopped or the request was superseded — stale capture traffic must never run.
     */
    private fun resolveCaptureOwnership(intent: Intent): ScanRequestToken? {
        // Phase 2F bounded legacy adapter: production broadcasts always carry
        // request-ownership metadata; a bare internal broadcast (tests/legacy) is given
        // ownership here so no capture can ever run unowned.
        val ownership = when {
            intent.hasAnyOwnershipExtras() -> intent.parseOwnership()
            else -> acceptSynthesizedOwnership()
        }
        val refusalReason = when {
            ownership == null -> "malformed ownership metadata or scanner stopped"
            !ScanRequests.coordinator.isLiveRequest(ownership) -> "request superseded"
            else -> null
        }
        if (refusalReason != null) {
            Log.w(TAG, "captureReceiver: capture refused ($refusalReason)")
            return null
        }
        return ownership
    }

    private fun acceptSynthesizedOwnership(): ScanRequestToken? =
        when (val acceptance = ScanRequests.coordinator.acceptRequest(RequestOrigin.USER)) {
            is RequestAcceptance.Accepted -> acceptance.token.also(DebugScanTiming::requestAccepted)
            is RequestAcceptance.Coalesced -> acceptance.survivingToken
            RequestAcceptance.RejectedStopped -> null
        }

    /**
     * Capture-service refusal after ownership already exists must still end the logical
     * request explicitly. Bare legacy/test broadcasts are intentionally ignored here
     * because their ownership has not been synthesized yet.
     */
    private fun terminalizeOwnedCaptureRefusal(intent: Intent, reason: String) {
        val ownership = intent.parseOwnership() ?: return
        if (ScanRequests.coordinator.claimTerminal(ownership, TerminalOutcome.FINAL_FAILURE)) {
            if (pendingOwnership == ownership) {
                pendingCapture = false
                pendingOwnership = null
            }
            Log.w(
                TAG,
                "Capture request terminalized before capture: reason=$reason " +
                    "requestId=${ownership.requestId} attempt=${ownership.attemptId}"
            )
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
            ContextCompat.registerReceiver(
                this,
                captureReceiver,
                filter,
                INTERNAL_BROADCAST_PERMISSION,
                null,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent ?: return START_NOT_STICKY

        if (intent.action == ACTION_STOP_SCANNER) {
            stopService(Intent(this, OverlayService::class.java))
            OverlayStateStore.dispatch(OverlayIntent.StopScan)
            // Phase 2F: scanner stop invalidates outstanding ownership before teardown.
            ScanRequests.coordinator.onScannerLifecycle(started = false)
            tearDown()
            stopSelf()
            return START_NOT_STICKY
        }

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
            clearProjectionGrant()
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
                // Fail closed when media projection foreground promotion is unavailable.
                Log.e(TAG, "MEDIA_PROJECTION foreground promotion failed; requiring fresh projection permission.", e)
                clearProjectionGrant()
                notifyProjectionRequired()
                stopSelf()
                return START_NOT_STICKY
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
        try { unregisterReceiver(captureReceiver) } catch (_: Exception) { Log.w(TAG, "captureReceiver not registered during destroy") }
        tearDown()
    }

    // ── MediaProjection setup ────────────────────────────────────────────

    private fun setupProjection(resultCode: Int, resultData: Intent) {
        try {
            val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: return
            val projection = mgr.getMediaProjection(resultCode, resultData)
            mediaProjection = projection

            projection.registerCallback(object : MediaProjection.Callback() {
                override fun onCapturedContentResize(width: Int, height: Int) {
                    if (mediaProjection !== projection || width <= 0 || height <= 0) return
                    val changed = contentWidth > 0 && (width != contentWidth || height != contentHeight)
                    contentWidth = width
                    contentHeight = height
                    if (changed && OwnedFrameHandoff.active()) {
                        ScanRequests.coordinator.onProjectionInvalidated()
                        OwnedFrameHandoff.invalidate()
                    }
                }
                override fun onStop() {
                    if (mediaProjection !== projection) return
                    Log.d(TAG, "MediaProjection stopped externally")
                    tearDown()
                }
            }, handler)

            val metrics = resources.displayMetrics
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            if (width != pooledWidth || height != pooledHeight) {
                OwnedFrameHandoff.invalidate()
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

    private fun captureSequence(ownership: ScanRequestToken) {
        if (!ScanRequests.coordinator.isLiveRequest(ownership)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Stale capture request ignored")
            return
        }
        val transportBusy = ownedTransportBusy()
        if (pendingCapture && pendingOwnership?.let(ScanRequests.coordinator::isLiveRequest) != true) {
            pendingCapture = false
            pendingOwnership = null
        }

        if (isCapturing || pendingCapture || transportBusy) {
            if (pendingCapture) {
                if (BuildConfig.DEBUG) Log.d(TAG, "Capture request coalesced: sequence=$captureSequenceId")
            } else if (ScanRequests.coordinator.isLiveRequest(ownership)) {
                pendingCapture = true
                pendingCaptureSince = SystemClock.elapsedRealtime()
                DebugScanTiming.withOwner(ownership) { DebugScanTiming.mark(ScanTimingStage.CAPTURE_DEFERRED) }
                pendingOwnership = ownership
                if (BuildConfig.DEBUG) Log.d(TAG, "Capture request deferred: sequence=$captureSequenceId")
            }
            val ownerIsLive = ScanRequests.coordinator.isLiveRequest(ownership)
            if (shouldScheduleNativeDrain(transportBusy, isCapturing, ownerIsLive)) {
                scheduleNativeDrain()
            }
            return
        }
        startCaptureSequence(deferred = false, ownership = ownership)
    }

    private data class CaptureContext(
        val reader: ImageReader?,
        val generation: Long,
        val sequenceId: Long,
        val startedAt: Long,
        val paths: MutableList<String>,
        val ownership: ScanRequestToken
    )

    private fun startCaptureSequence(deferred: Boolean, ownership: ScanRequestToken, deferredWaitMs: Long = 0L) {
        if (!ScanRequests.coordinator.isLiveRequest(ownership)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Stale capture request ignored before projection check")
            return
        }
        if (isReinitializing) {
            Log.w(TAG, "captureSequence aborted: projection reinitialization already in progress")
            ScanRequests.coordinator.claimTerminal(ownership, TerminalOutcome.FINAL_FAILURE)
            return
        }
        if (!ensureProjectionReady()) {
            Log.w(TAG, "captureSequence aborted: projection not ready")
            // setupProjection failure may already have invalidated the epoch via
            // clearProjectionGrant(). If ownership is still live (for example, no
            // projection grant existed yet), end it explicitly instead of leaving a
            // logical request permanently active.
            if (ScanRequests.coordinator.isLiveRequest(ownership)) {
                ScanRequests.coordinator.claimTerminal(ownership, TerminalOutcome.FINAL_FAILURE)
                notifyProjectionRequired()
            }
        } else if (!ScanRequests.coordinator.isLiveRequest(ownership)) {
            if (BuildConfig.DEBUG) Log.d(TAG, "Stale capture request ignored after projection check")
        } else {
            isCapturing = true
            activeOwnership = ownership
            val context = CaptureContext(
                reader = imageReader,
                generation = captureGeneration,
                sequenceId = ++captureSequenceId,
                startedAt = SystemClock.elapsedRealtime(),
                paths = mutableListOf(),
                ownership = ownership
            )
            DebugScanTiming.withOwner(ownership, context.sequenceId) {
                DebugScanTiming.mark(ScanTimingStage.CAPTURE_START)
            }
            logCaptureStart(context, deferred, deferredWaitMs)
            if (deferred) {
                drainDeferredCaptureBuffer(context.reader)
            }
            scheduleCapture(context, CAPTURE_FRAME_COUNT, CAPTURE_INITIAL_DELAY_MS)
        }
    }

    private fun logCaptureStart(
        context: CaptureContext,
        deferred: Boolean,
        deferredWaitMs: Long
    ) {
        if (!BuildConfig.DEBUG) return
        Log.d(TAG, "Capture started: sequence=${context.sequenceId} deferred=$deferred")
        if (deferred) {
            Log.d(
                TAG,
                "Deferred capture started: sequence=${context.sequenceId} waitMs=$deferredWaitMs"
            )
        }
    }

    private fun drainDeferredCaptureBuffer(reader: ImageReader?) {
        try {
            reader?.acquireLatestImage()?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Deferred capture buffer drain failed", e)
        }
    }

    private fun scheduleCapture(
        context: CaptureContext,
        remaining: Int,
        delayMs: Long = CAPTURE_INTERVAL_MS
    ) {
        handler.postDelayed(
            { captureNextFrame(context, remaining) },
            delayMs
        )
    }

    private fun captureNextFrame(context: CaptureContext, remaining: Int) {
        if (context.generation != captureGeneration) return
        if (remaining <= 0) {
            finishCaptureSequence(context)
            return
        }

        try {
            DebugScanTiming.withOwner(context.ownership, context.sequenceId, CAPTURE_FRAME_COUNT - remaining) {
                captureLatestFrame(
                reader = context.reader,
                sequenceId = context.sequenceId,
                frameIndex = CAPTURE_FRAME_COUNT - remaining,
                ownership = context.ownership
            )?.let(context.paths::add) }
        } catch (e: Exception) {
            Log.e(TAG, "Frame capture failed", e)
        }

        scheduleCapture(context, remaining - 1)
    }

    private fun captureLatestFrame(
        reader: ImageReader?,
        sequenceId: Long,
        frameIndex: Int,
        ownership: ScanRequestToken
    ): String? {
        val image = DebugScanTiming.measure(ScanTimingStage.FRAME_ACQUIRE) {
            reader?.acquireLatestImage() } ?: return null
        DebugScanTiming.mark(ScanTimingStage.FRAME_ACQUIRED)
        var sourceBitmap: Bitmap? = null
        var targetBitmap: Bitmap? = null
        try {
            if (BuildConfig.DEBUG) {
                Log.d(
                    TAG,
                    "Capture frame acquired: sequence=$sequenceId " +
                        "index=$frameIndex imageTimestampNs=${image.timestamp}"
                )
            }
            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val paddedWidth = image.width + rowPadding / pixelStride

            sourceBitmap = bitmapPool.obtain(
                paddedWidth,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            buffer.rewind()
            DebugScanTiming.measure(ScanTimingStage.FRAME_COPY) { sourceBitmap.copyPixelsFromBuffer(buffer) }

            targetBitmap = bitmapPool.obtain(
                resources.displayMetrics.widthPixels,
                resources.displayMetrics.heightPixels,
                Bitmap.Config.ARGB_8888
            )
            Canvas(targetBitmap).drawBitmap(sourceBitmap, 0f, 0f, null)

            val stamp = System.currentTimeMillis()
            val name = if (OwnedFrameHandoff.active()) {
                "scan_${ownership.projectionEpoch}-${ownership.requestId}-${ownership.attemptId}-$sequenceId" +
                    "-${stamp}_$frameIndex.png"
            } else "scan_${stamp}_$frameIndex.png"
            val file = File(cacheDir, name)
            val transportPath = if (OwnedFrameHandoff.active()) {
                DebugScanTiming.measure(ScanTimingStage.OWNED_FRAME_COPY) {
                    OwnedFrameHandoff.capture(targetBitmap, file, ownership, sequenceId, frameIndex)
                }
            } else {
                DebugScanTiming.measure(ScanTimingStage.PNG_STORAGE) { FileOutputStream(file).use { out ->
                    targetBitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
                } }
                file.absolutePath
            }
            DebugScanTiming.mark(ScanTimingStage.FRAME_READY)
            captureCounter++; logMemoryIfNeeded()
            Log.d(TAG, "Frame $frameIndex saved: ${file.name}")
            return transportPath
        } finally {
            targetBitmap?.let(bitmapPool::release)
            sourceBitmap?.let(bitmapPool::release)
            image.close()
        }
    }

    private fun finishCaptureSequence(context: CaptureContext) {
        completedCaptureOwnership = context.ownership
        try {
            if (context.paths.isNotEmpty()) {
                Log.d(TAG, "captureSequence complete: ${context.paths.size} frames ready")
                DebugScanTiming.withOwner(context.ownership, context.sequenceId) {
                    DebugScanTiming.mark(ScanTimingStage.CAPTURE_READY) }
                sendBroadcast(
                    Intent(ACTION_SCREENSHOT_READY).apply {
                        setPackage(packageName)
                        putStringArrayListExtra(EXTRA_SCREENSHOT_PATHS, ArrayList(context.paths))
                        putExtra(EXTRA_OWNED_FRAMES, OwnedFrameHandoff.hasPaths(context.paths))
                        // Phase 2F: end-to-end ownership envelope for ScanManager.
                        putOwnershipExtras(context.ownership)
                        putCaptureSequenceExtra(context.sequenceId)
                    },
                    INTERNAL_BROADCAST_PERMISSION
                )
            } else {
                Log.e(TAG, "captureSequence complete: no frames captured")
                broadcastError(context)
            }
        } finally {
            releaseCaptureOwnership(context)
        }
    }

    private fun releaseCaptureOwnership(context: CaptureContext) {
        if (activeOwnership == context.ownership) activeOwnership = null
        isCapturing = false
        if (BuildConfig.DEBUG) {
            val durationMs = SystemClock.elapsedRealtime() - context.startedAt
            Log.d(
                TAG,
                "Capture ownership released: sequence=${context.sequenceId} durationMs=$durationMs"
            )
        }
        if (pendingCapture) {
            handler.post { startPendingCaptureIfCurrent(context.generation) }
        }
    }

    private fun startPendingCaptureIfCurrent(generation: Long) {
        if (generation != captureGeneration || !pendingCapture || isCapturing) return
        if (ownedTransportBusy()) scheduleNativeDrain() else drainPendingCapture()
    }

    private fun drainPendingCapture() {
        val waitMs = SystemClock.elapsedRealtime() - pendingCaptureSince
        val ownership = pendingOwnership
        pendingCapture = false
        pendingOwnership = null
        if (ownership == null) {
            Log.w(TAG, "Deferred capture drained without ownership; refusing unowned capture")
            return
        }
        if (!ScanRequests.coordinator.isLiveRequest(ownership)) {
            Log.w(TAG, "Deferred capture drained after request ownership ended; refusing stale capture")
            return
        }
        startCaptureSequence(deferred = true, ownership = ownership, deferredWaitMs = waitMs)
    }
    private fun ownedTransportBusy(): Boolean = OwnedFrameHandoff.active() &&
        !OwnedFrameHandoff.hasCapacityForAttempt(resources.displayMetrics.widthPixels,
            resources.displayMetrics.heightPixels)

    /** Reuse the existing single pending slot instead of allocating extra frames under pressure. */
    private fun scheduleNativeDrain() {
        if (nativeDrainPosted) return
        nativeDrainPosted = true
        val generation = captureGeneration
        handler.postDelayed({
            nativeDrainPosted = false
            if (generation != captureGeneration || !pendingCapture) return@postDelayed
            if (SystemClock.elapsedRealtime() - pendingCaptureSince >= OWNED_PENDING_TIMEOUT_MS) {
                pendingOwnership?.let {
                    ScanRequests.coordinator.claimTerminal(it, TerminalOutcome.FINAL_FAILURE)
                }
                pendingCapture = false
                pendingOwnership = null
            } else startPendingCaptureIfCurrent(generation)
        }, CAPTURE_INTERVAL_MS)
    }

    private fun broadcastError(context: CaptureContext) {
        Log.e(TAG, "broadcastError: screenshot ready broadcast sent without paths")
        sendBroadcast(Intent(ACTION_SCREENSHOT_READY).apply {
            setPackage(packageName)
            // The failed capture still belongs to its logical request so ScanManager can
            // retry/fail under the same ownership instead of leaving it dangling.
            putOwnershipExtras(context.ownership)
            putCaptureSequenceExtra(context.sequenceId)
        }, INTERNAL_BROADCAST_PERMISSION)
    }

    private fun notifyProjectionStopped() {
        sendBroadcast(Intent(ACTION_PROJECTION_STOPPED).apply {
            setPackage(packageName)
        }, INTERNAL_BROADCAST_PERMISSION)
    }

    private fun notifyProjectionRequired() {
        Log.w(TAG, "notifyProjectionRequired: projection token is missing or invalid")
        sendBroadcast(Intent(ACTION_PROJECTION_REQUIRED).apply {
            setPackage(packageName)
        }, INTERNAL_BROADCAST_PERMISSION)
    }

    private fun triggerAutoCaptureIfNeeded(reason: String) {
        if (!pendingAutoCapture) return
        pendingAutoCapture = false
        // Phase 2F: auto capture is an owned logical request (origin AUTO), never an
        // unowned special path. Refused fail-closed when the scanner is stopped.
        val acceptance = ScanRequests.coordinator.acceptRequest(RequestOrigin.AUTO)
        val ownership = when (acceptance) {
            is RequestAcceptance.Accepted -> acceptance.token
            is RequestAcceptance.Coalesced -> acceptance.survivingToken
            RequestAcceptance.RejectedStopped -> {
                Log.w(TAG, "triggerAutoCaptureIfNeeded: scanner stopped; auto capture refused")
                return
            }
        }
        if (acceptance is RequestAcceptance.Accepted) DebugScanTiming.requestAccepted(ownership)
        Log.d(TAG, "triggerAutoCaptureIfNeeded: reason=$reason requestId=${ownership.requestId}")
        handler.postDelayed({ captureSequence(ownership) }, 180L)
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
            clearProjectionGrant()
            false
        } finally {
            isReinitializing = false
        }
    }

    // ── Teardown ─────────────────────────────────────────────────────────

    private fun tearDown() {
        clearProjectionGrant()
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
            Log.w(TAG, "virtualDisplay.release failed during tearDown")
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
            Log.w(TAG, "imageReader.close failed during tearDown")
        }
        imageReader = null
        bitmapPool.clear()
        val projection = mediaProjection
        mediaProjection = null
        try {
            projection?.stop()
        } catch (_: Exception) {
            Log.w(TAG, "mediaProjection.stop failed during tearDown")
        }
    }

    private fun clearProjectionGrant() {
        if (pendingCapture && BuildConfig.DEBUG) Log.d(TAG, "Deferred capture cleared: projection grant cleared")
        captureGeneration++
        OwnedFrameHandoff.invalidate()
        handler.removeCallbacksAndMessages(null)
        nativeDrainPosted = false
        contentWidth = 0
        contentHeight = 0
        pendingCapture = false
        pendingCaptureSince = 0L
        pendingOwnership = null
        activeOwnership = null
        completedCaptureOwnership = null
        isCapturing = false
        // Phase 2F: the projection/session epoch advances; live logical requests end as
        // PROJECTION_INVALIDATED and old capture callbacks/results can no longer validate.
        ScanRequests.coordinator.onProjectionInvalidated()
        projectionResultCode = Activity.RESULT_CANCELED
        projectionResultData = null
        pendingAutoCapture = false
        ScreenCaptureManager.clearGrant()
    }

    private fun logMemoryIfNeeded() {
        if (!BuildConfig.DEBUG || captureCounter % 10 != 0) return
        val runtime = Runtime.getRuntime()
        val used = runtime.totalMemory() - runtime.freeMemory()
        Log.d(TAG, "Memory monitor: used=${used / 1024 / 1024}MB total=${runtime.totalMemory() / 1024 / 1024}MB")
        if (lastMemoryBytes > 0L && used - lastMemoryBytes > 24L * 1024L * 1024L) {
            Log.d(TAG, "Memory monitor detected growth >24MB")
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
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            flags
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, ScreenCaptureService::class.java).setAction(ACTION_STOP_SCANNER),
            flags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PokeRarityScanner")
            .setContentText("Scanner active - tap Stop to exit")
            .setSmallIcon(R.drawable.ic_pokeball)
            .setLargeIcon(largeIcon)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_pokeball, "Stop", stopIntent)
            .build()
    }
}

private fun shouldScheduleNativeDrain(transportBusy: Boolean, capturing: Boolean, ownerIsLive: Boolean): Boolean =
    transportBusy && !capturing && ownerIsLive
