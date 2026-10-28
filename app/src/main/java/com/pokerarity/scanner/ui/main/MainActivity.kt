package com.pokerarity.scanner.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.TelemetryConfigPreferences
import com.pokerarity.scanner.data.local.TelemetryPreferences
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.ScanUiPreferences
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.toUiPokemon
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.data.repository.RarityManifestLoader
import com.pokerarity.scanner.data.remote.ScanTelemetryCoordinator
import com.pokerarity.scanner.service.OverlayIntent
import com.pokerarity.scanner.service.OverlayManager
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
        const val EXTRA_OPEN_TELEMETRY_SETTINGS = "extra_open_telemetry_settings"
        const val EXTRA_AUTO_START_SCAN = "extra_auto_start_scan"
    }

    private val startupState = mutableStateOf<MainActivityStartupState?>(null)
    private val startupLoading = mutableStateOf(false)
    private val databaseRecoveryIssue = mutableStateOf<HistoryDatabaseRecoveryIssue?>(null)
    private val telemetrySettings = mutableStateOf(TelemetrySettingsSnapshot())
    private val overlayRunning = mutableStateOf(false)
    private val showConsentDialog = mutableStateOf(false)
    private val showTelemetrySettings = mutableStateOf(false)

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (OverlayManager.canDrawOverlays(this)) {
            requestMediaProjection()
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
        handleStartupIntent(intent)
        refreshOverlayState()
        initializeStartupState()

        setContent {
            PokeRarityTheme(darkTheme = isSystemInDarkTheme()) {
                val startup = startupState.value
                if (startup == null) {
                    val recoveryIssue = databaseRecoveryIssue.value
                    if (recoveryIssue != null) {
                        HistoryDatabaseRecoveryScreen(
                            issue = recoveryIssue,
                            onRetry = ::initializeStartupState
                        )
                    } else {
                        StartupDataScreen(
                            loading = startupLoading.value,
                            onRetry = ::initializeStartupState
                        )
                    }
                } else {
                    if (showConsentDialog.value) {
                        TelemetryConsentDialog(
                            onAccept = { updateTelemetryConsent(true) },
                            onReject = { updateTelemetryConsent(false) }
                        )
                    }

                    if (showTelemetrySettings.value) {
                        val settings = telemetrySettings.value
                        TelemetrySettingsDialog(
                            currentEnabled = settings.userConsent,
                            currentBaseUrl = settings.baseUrl,
                            currentApiKey = settings.apiKey,
                            currentAutoCopyEnabled = settings.autoCopyEnabled,
                            currentHapticsEnabled = settings.hapticsEnabled,
                            onDismiss = { showTelemetrySettings.value = false },
                            onSave = ::saveTelemetrySettings
                        )
                    }

                    MainContent(
                        repository = startup.repository,
                        isOverlayRunning = overlayRunning.value,
                        onScanClick = ::handleStartPressed,
                        onHistoryClick = { startActivity(Intent(this, HistoryActivity::class.java)) },
                        onSharePokemon = ::sharePokemon,
                        onTelemetrySettingsClick = { showTelemetrySettings.value = true },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayState()
        flushPendingTelemetryAsync()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleStartupIntent(intent)
    }

    private fun handleStartPressed() {
        if (OverlayManager.isOverlayRunning(this)) {
            OverlayManager.stopOverlay(this)
            stopCapture()
            showToast("Overlay stopped.")
            refreshOverlayState()
            return
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

    private fun requestMediaProjection() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: return
        mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    private fun startCapture(autoCapture: Boolean = ScanStartupPolicy.autoCaptureForManualStart()) {
        val serviceIntent = ScreenCaptureManager.buildServiceIntent(this, autoCapture = autoCapture)
        if (serviceIntent != null) {
            val started = runCatching {
                startForegroundService(serviceIntent)
                OverlayManager.startOverlay(this)
            }.isSuccess

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
        overlayRunning.value = OverlayManager.isOverlayRunning(this)
        if (!overlayRunning.value) {
            OverlayStateStore.resetToIdle()
        }
    }

    @Suppress("TooGenericExceptionCaught") // Secure storage and database providers expose different failure types.
    private fun initializeStartupState() {
        if (startupLoading.value) return
        startupLoading.value = true
        databaseRecoveryIssue.value = null

        lifecycleScope.launch {
            try {
                val state = withContext(Dispatchers.IO) {
                    val appContext = applicationContext
                    RarityManifestLoader.initialize(appContext)

                    val telemetryPreferences = TelemetryPreferences(appContext)
                    val telemetryConfigPreferences = TelemetryConfigPreferences(appContext)
                    val scanUiPreferences = ScanUiPreferences(appContext)
                    val repository = PokemonRepository(AppDatabase.getInstance(appContext))

                    MainActivityStartupState(
                        repository = repository,
                        telemetryPreferences = telemetryPreferences,
                        telemetryConfigPreferences = telemetryConfigPreferences,
                        scanUiPreferences = scanUiPreferences,
                        settings = TelemetrySettingsSnapshot(
                            userConsent = telemetryPreferences.userConsent,
                            hasSeenOnboarding = telemetryPreferences.hasSeenOnboarding,
                            baseUrl = telemetryConfigPreferences.baseUrl,
                            apiKey = telemetryConfigPreferences.apiKey,
                            autoCopyEnabled = scanUiPreferences.autoCopyEnabled,
                            hapticsEnabled = scanUiPreferences.hapticsEnabled
                        )
                    )
                }
                startupState.value = state
                databaseRecoveryIssue.value = null
                telemetrySettings.value = state.settings
                showConsentDialog.value = !state.settings.hasSeenOnboarding
                refreshOverlayState()
                flushPendingTelemetryAsync()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: UnsatisfiedLinkError) {
                databaseRecoveryIssue.value = HistoryDatabaseRecoveryIssue.from(failure)
                Log.w(TAG, "Startup data could not be prepared (SQLCipher native library unavailable)")
            } catch (failure: Exception) {
                databaseRecoveryIssue.value = HistoryDatabaseRecoveryIssue.from(failure)
                Log.w(TAG, "Startup data could not be prepared (${failure.javaClass.simpleName})")
            } finally {
                startupLoading.value = false
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // Encrypted preferences and the telemetry repository can fail independently.
    private fun updateTelemetryConsent(enabled: Boolean) {
        val state = startupState.value ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    state.telemetryPreferences.userConsent = enabled
                    state.telemetryPreferences.hasSeenOnboarding = true
                    if (!enabled) {
                        ScanTelemetryCoordinator.getInstance(applicationContext).clearPendingAsync()
                    }
                }
                telemetrySettings.value = telemetrySettings.value.copy(
                    userConsent = enabled,
                    hasSeenOnboarding = true
                )
                showConsentDialog.value = false
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Telemetry consent update failed (${failure.javaClass.simpleName})")
                showToast(getString(R.string.save_failed))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // Encrypted preference stores can fail independently.
    private fun saveTelemetrySettings(
        enabled: Boolean,
        baseUrl: String,
        apiKey: String,
        autoCopyEnabled: Boolean,
        hapticsEnabled: Boolean
    ) {
        val state = startupState.value ?: return
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    state.telemetryPreferences.userConsent = enabled
                    state.telemetryPreferences.hasSeenOnboarding = true
                    if (!enabled) {
                        ScanTelemetryCoordinator.getInstance(applicationContext).clearPendingAsync()
                    }
                    state.telemetryConfigPreferences.baseUrl = baseUrl
                    state.telemetryConfigPreferences.apiKey = apiKey
                    state.scanUiPreferences.autoCopyEnabled = autoCopyEnabled
                    state.scanUiPreferences.hapticsEnabled = hapticsEnabled
                }
                telemetrySettings.value = TelemetrySettingsSnapshot(
                    userConsent = enabled,
                    hasSeenOnboarding = true,
                    baseUrl = baseUrl,
                    apiKey = apiKey,
                    autoCopyEnabled = autoCopyEnabled,
                    hapticsEnabled = hapticsEnabled
                )
                showTelemetrySettings.value = false
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w(TAG, "Telemetry settings update failed (${failure.javaClass.simpleName})")
                showToast(getString(R.string.save_failed))
            }
        }
    }

    @Suppress("TooGenericExceptionCaught") // The coordinator initializes SQLCipher-backed storage lazily.
    private fun flushPendingTelemetryAsync() {
        if (startupState.value == null) return
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                ScanTelemetryCoordinator.getInstance(applicationContext).flushPendingAsync()
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Telemetry flush skipped (${error.javaClass.simpleName})")
            }
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

private data class MainActivityStartupState(
    val repository: PokemonRepository,
    val telemetryPreferences: TelemetryPreferences,
    val telemetryConfigPreferences: TelemetryConfigPreferences,
    val scanUiPreferences: ScanUiPreferences,
    val settings: TelemetrySettingsSnapshot
)

private data class TelemetrySettingsSnapshot(
    val userConsent: Boolean = false,
    val hasSeenOnboarding: Boolean = false,
    val baseUrl: String = "",
    val apiKey: String = "",
    val autoCopyEnabled: Boolean = false,
    val hapticsEnabled: Boolean = true
)

@Composable
@Suppress("FunctionNaming") // Compose UI functions follow the type-like naming convention.
private fun StartupDataScreen(loading: Boolean, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (loading) {
                CircularProgressIndicator()
                Text(stringResource(R.string.startup_loading_data))
            } else {
                Text(stringResource(R.string.startup_data_unavailable))
                Button(onClick = onRetry) {
                    Text(stringResource(R.string.startup_retry))
                }
            }
        }
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
        modifier = Modifier.testTag("main_content"),
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
