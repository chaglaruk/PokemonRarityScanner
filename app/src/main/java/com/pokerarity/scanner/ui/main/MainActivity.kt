package com.pokerarity.scanner.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.TelemetryConfigPreferences
import com.pokerarity.scanner.data.local.TelemetryPreferences
import com.pokerarity.scanner.data.local.ScanUiPreferences
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.toUiPokemon
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.service.CaptureServiceStartup
import com.pokerarity.scanner.service.OverlayIntent
import com.pokerarity.scanner.service.OverlayManager
import com.pokerarity.scanner.service.OverlayService
import com.pokerarity.scanner.service.OverlayStateStore
import com.pokerarity.scanner.service.ScanStartupPolicy
import com.pokerarity.scanner.service.ScreenCaptureManager
import com.pokerarity.scanner.service.ScreenCaptureService
import com.pokerarity.scanner.ui.result.HistoryActivity
import com.pokerarity.scanner.ui.screens.CollectionScreen
import com.pokerarity.scanner.ui.screens.ScanResultScreen
import com.pokerarity.scanner.ui.share.ResultShareRenderer
import com.pokerarity.scanner.ui.theme.PokeRarityTheme
import com.pokerarity.scanner.ui.dialog.TelemetryConsentDialog
import com.pokerarity.scanner.ui.dialog.TelemetrySettingsDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_OPEN_TELEMETRY_SETTINGS = "extra_open_telemetry_settings"
        const val EXTRA_AUTO_START_SCAN = "extra_auto_start_scan"
    }

    private lateinit var repository: PokemonRepository
    private lateinit var telemetryPrefs: TelemetryPreferences
    private lateinit var telemetryConfigPrefs: TelemetryConfigPreferences
    private lateinit var scanUiPreferences: ScanUiPreferences
    private val overlayRunning = mutableStateOf(false)
    private val showConsentDialog = mutableStateOf(false)
    private val showTelemetrySettings = mutableStateOf(false)
    private var scannerLifecycleReceiverRegistered = false

    private val scannerLifecycleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ScreenCaptureService.ACTION_PROJECTION_STOPPED -> {
                    overlayRunning.value = false
                    OverlayStateStore.resetToIdle()
                }
                OverlayService.ACTION_OVERLAY_STATE_CHANGED -> {
                    val serviceRunning = intent.getBooleanExtra(OverlayService.EXTRA_OVERLAY_RUNNING, false)
                    overlayRunning.value = ScanStartupPolicy.isScannerSessionActive(
                        overlayRunning = serviceRunning,
                        projectionGranted = ScreenCaptureManager.isGranted
                    )
                    if (!overlayRunning.value) OverlayStateStore.resetToIdle()
                    if (intent.getBooleanExtra(OverlayService.EXTRA_OVERLAY_START_FAILED, false)) {
                        showToast("Overlay could not be started.")
                    }
                }
            }
        }
    }

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (OverlayManager.canDrawOverlays(this)) {
            if (ScreenCaptureManager.isGranted) {
                resumeOverlayForExistingProjection()
            } else {
                requestMediaProjection()
            }
        } else {
            showToast("Overlay permission required.")
        }
        refreshOverlayState()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (ScreenCaptureManager.handleResult(result)) {
            startCapture()
        } else {
            showToast("Screen capture permission denied.")
            refreshOverlayState()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestMediaProjection()
        } else {
            showToast("Notification permission is required to start scanning.")
            refreshOverlayState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = PokemonRepository(AppDatabase.getInstance(this))
        telemetryPrefs = TelemetryPreferences(this)
        telemetryConfigPrefs = TelemetryConfigPreferences(this)
        scanUiPreferences = ScanUiPreferences(this)
        handleStartupIntent(intent)
        
        // Check if user needs to see telemetry consent dialog
        if (!telemetryPrefs.hasSeenOnboarding) {
            showConsentDialog.value = true
        }
        
        refreshOverlayState()

        setContent {
            PokeRarityTheme(darkTheme = isSystemInDarkTheme()) {
                // Show consent dialog if needed
                if (showConsentDialog.value) {
                    TelemetryConsentDialog(
                        onAccept = {
                            telemetryPrefs.userConsent = true
                            telemetryPrefs.hasSeenOnboarding = true
                            showConsentDialog.value = false
                        },
                        onReject = {
                            telemetryPrefs.userConsent = false
                            ScanTelemetryCoordinator.getInstance(this).clearPendingAsync()
                            telemetryPrefs.hasSeenOnboarding = true
                            showConsentDialog.value = false
                        }
                    )
                }

                if (showTelemetrySettings.value) {
                    TelemetrySettingsDialog(
                        currentEnabled = telemetryPrefs.userConsent,
                        currentBaseUrl = telemetryConfigPrefs.baseUrl,
                        currentApiKey = telemetryConfigPrefs.apiKey,
                        currentAutoCopyEnabled = scanUiPreferences.autoCopyEnabled,
                        currentHapticsEnabled = scanUiPreferences.hapticsEnabled,
                        onDismiss = { showTelemetrySettings.value = false },
                        onSave = { enabled, baseUrl, apiKey, autoCopyEnabled, hapticsEnabled ->
                            telemetryPrefs.userConsent = enabled
                            if (!enabled) {
                                ScanTelemetryCoordinator.getInstance(this).clearPendingAsync()
                            }
                            telemetryPrefs.hasSeenOnboarding = true
                            telemetryConfigPrefs.baseUrl = baseUrl
                            telemetryConfigPrefs.apiKey = apiKey
                            scanUiPreferences.autoCopyEnabled = autoCopyEnabled
                            scanUiPreferences.hapticsEnabled = hapticsEnabled
                            showTelemetrySettings.value = false
                        }
                    )
                }
                 
                MainContent(
                    repository = repository,
                    isOverlayRunning = overlayRunning.value,
                    onScanClick = ::handleStartPressed,
                    onHistoryClick = { startActivity(Intent(this, HistoryActivity::class.java)) },
                    onSharePokemon = ::sharePokemon,
                    onTelemetrySettingsClick = { showTelemetrySettings.value = true },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayState()
        ScanTelemetryCoordinator.getInstance(this).flushPendingAsync()
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(ScreenCaptureService.ACTION_PROJECTION_STOPPED)
            addAction(OverlayService.ACTION_OVERLAY_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scannerLifecycleReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            ContextCompat.registerReceiver(
                this,
                scannerLifecycleReceiver,
                filter,
                ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION,
                null,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        }
        scannerLifecycleReceiverRegistered = true
    }

    override fun onStop() {
        if (scannerLifecycleReceiverRegistered) {
            unregisterReceiver(scannerLifecycleReceiver)
            scannerLifecycleReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStartupIntent(intent)
    }

    private fun handleStartPressed() {
        val overlayServiceRunning = OverlayManager.isOverlayRunning(this)
        when (ScanStartupPolicy.startAction(overlayServiceRunning, ScreenCaptureManager.isGranted)) {
            ScanStartupPolicy.StartAction.STOP -> {
                OverlayManager.stopOverlay(this)
                stopCapture()
                showToast("Overlay stopped.")
                refreshOverlayState()
                return
            }
            ScanStartupPolicy.StartAction.RESUME_OVERLAY -> {
                if (!OverlayManager.canDrawOverlays(this)) {
                    showToast("Overlay permission required.")
                    OverlayManager.requestOverlayPermission(overlayPermissionLauncher, this)
                } else {
                    resumeOverlayForExistingProjection()
                }
                return
            }
            ScanStartupPolicy.StartAction.REQUEST_PERMISSION -> {
                if (overlayServiceRunning) {
                    // Android stopped projection while leaving the overlay service alive.
                    // Retire that stale session before requesting a fresh user grant.
                    OverlayManager.stopOverlay(this)
                    stopCapture()
                }
            }
        }

        if (!OverlayManager.canDrawOverlays(this)) {
            showToast("Overlay permission required.")
            OverlayManager.requestOverlayPermission(overlayPermissionLauncher, this)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasNotificationPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!hasNotificationPermission) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }

        requestMediaProjection()
    }

    private fun resumeOverlayForExistingProjection() {
        runCatching {
            OverlayManager.startOverlay(this)
            OverlayStateStore.dispatch(OverlayIntent.StartScan)
        }.onSuccess {
            showToast("Scanner starting.")
        }.onFailure {
            showToast("Overlay could not be started.")
        }
        refreshOverlayState()
    }

    private fun requestMediaProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: return
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startCapture(autoCapture: Boolean = ScanStartupPolicy.autoCaptureForManualStart()) {
        val serviceIntent = ScreenCaptureManager.buildServiceIntent(this, autoCapture = autoCapture)
        if (serviceIntent != null) {
            val started = CaptureServiceStartup.runOrRollback(
                context = this,
                startCaptureService = { startForegroundService(serviceIntent) },
                startOverlay = { OverlayManager.startOverlay(this) }
            )

            if (started) {
                OverlayStateStore.dispatch(OverlayIntent.StartScan)
                showToast("Scan started.")
            } else {
                showToast("Scan service could not be started.")
            }
        } else {
            showToast("Projection permission is required to start scanning.")
        }
        refreshOverlayState()
    }

    private fun stopCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        ScreenCaptureManager.release()
        OverlayStateStore.dispatch(OverlayIntent.StopScan)
    }

    private fun refreshOverlayState() {
        overlayRunning.value = ScanStartupPolicy.isScannerSessionActive(
            overlayRunning = OverlayManager.isOverlayRunning(this),
            projectionGranted = ScreenCaptureManager.isGranted
        )
        if (!overlayRunning.value) {
            OverlayStateStore.resetToIdle()
        }
    }

    private fun handleStartupIntent(startupIntent: Intent?) {
        if (startupIntent == null) return
        if (startupIntent.getBooleanExtra(EXTRA_OPEN_TELEMETRY_SETTINGS, false)) {
            showTelemetrySettings.value = true
        }
        if (startupIntent.getBooleanExtra(EXTRA_AUTO_START_SCAN, false)) {
            // Ghost-scan guard: app launch must stay idle until the user explicitly starts a scan.
            startupIntent.removeExtra(EXTRA_AUTO_START_SCAN)
        }
    }

    private fun sharePokemon(pokemon: Pokemon) {
        val shareText = buildString {
            append(pokemon.name)
            append(" • ")
            append(pokemon.rarityTierLabel)
            append(" • Score ")
            append(pokemon.rarityScore)
            append(" • CP ")
            append(pokemon.cpDisplayText)
            pokemon.hp?.let {
                append(" • HP ")
                append(it)
            }
        }
        val imageUri = ResultShareRenderer.renderPokemonCardToImageUri(
            context = this,
            pokemon = pokemon,
            fileName = "collection_${pokemon.id}.png"
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, shareText)
            imageUri?.let {
                putExtra(Intent.EXTRA_STREAM, it)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            type = if (imageUri != null) "image/png" else "text/plain"
        }
        startActivity(Intent.createChooser(intent, "Share via"))
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}

@Composable
private fun MainContent(
    repository: PokemonRepository,
    isOverlayRunning: Boolean,
    onScanClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onSharePokemon: (Pokemon) -> Unit,
    onTelemetrySettingsClick: () -> Unit,
) {
    val navController = rememberNavController()
    val scans by repository.getAllScans().collectAsStateWithLifecycle(initialValue = emptyList())
    val pokemonList = scans.map { it.toUiPokemon() }

    NavHost(
        navController = navController,
        startDestination = "collection",
    ) {
        composable("collection") {
            CollectionScreen(
                pokemonList = pokemonList,
                isOverlayRunning = isOverlayRunning,
                onPokemonClick = { pokemon -> navController.navigate("detail/${pokemon.id}") },
                onScanClick = onScanClick,
                onHistoryClick = onHistoryClick,
                onHomeClick = {},
                onCollectionClick = {},
                onTelemetrySettingsClick = onTelemetrySettingsClick,
            )
        }

        composable(
            route = "detail/{pokemonId}",
            arguments = listOf(navArgument("pokemonId") { type = NavType.IntType }),
        ) { backStackEntry ->
            val pokemonId = backStackEntry.arguments?.getInt("pokemonId") ?: return@composable
            val pokemon = pokemonList.firstOrNull { it.id == pokemonId } ?: return@composable

            ScanResultScreen(
                pokemon = pokemon,
                onBack = { navController.popBackStack() },
                onShare = { onSharePokemon(pokemon) },
                onSave = {},
                onHistory = onHistoryClick,
                onScan = onScanClick,
                onHome = { navController.popBackStack("collection", false) },
                onCollection = { navController.popBackStack("collection", false) },
                onSettings = onTelemetrySettingsClick,
            )
        }
    }
}
