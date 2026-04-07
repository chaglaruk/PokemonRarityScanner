package com.pokerarity.scanner.ui.main

import android.Manifest
import android.content.Intent
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
import com.pokerarity.scanner.data.model.Pokemon
import com.pokerarity.scanner.data.model.toUiPokemon
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.service.OverlayManager
import com.pokerarity.scanner.service.ScreenCaptureManager
import com.pokerarity.scanner.service.ScreenCaptureService
import com.pokerarity.scanner.ui.screens.CollectionScreen
import com.pokerarity.scanner.ui.screens.ScanResultScreen
import com.pokerarity.scanner.ui.share.ResultShareRenderer
import com.pokerarity.scanner.ui.theme.PokeRarityTheme
import com.pokerarity.scanner.ui.dialog.TelemetryConsentDialog
import com.pokerarity.scanner.ui.dialog.TelemetrySettingsDialog
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private lateinit var repository: PokemonRepository
    private lateinit var telemetryPrefs: TelemetryPreferences
    private lateinit var telemetryConfigPrefs: TelemetryConfigPreferences
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
        repository = PokemonRepository(AppDatabase.getInstance(this))
        telemetryPrefs = TelemetryPreferences(this)
        telemetryConfigPrefs = TelemetryConfigPreferences(this)
        
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
                            telemetryPrefs.consentTimestamp = System.currentTimeMillis()
                            telemetryPrefs.hasSeenOnboarding = true
                            showConsentDialog.value = false
                        },
                        onReject = {
                            telemetryPrefs.userConsent = false
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
                        onDismiss = { showTelemetrySettings.value = false },
                        onSave = { enabled, baseUrl, apiKey ->
                            telemetryPrefs.userConsent = enabled
                            telemetryPrefs.consentTimestamp = System.currentTimeMillis()
                            telemetryPrefs.hasSeenOnboarding = true
                            telemetryConfigPrefs.baseUrl = baseUrl
                            telemetryConfigPrefs.apiKey = apiKey
                            showTelemetrySettings.value = false
                        }
                    )
                }
                 
                MainContent(
                    repository = repository,
                    isOverlayRunning = overlayRunning.value,
                    onScanClick = ::handleStartPressed,
                    onSharePokemon = ::sharePokemon,
                    onTelemetrySettingsClick = { showTelemetrySettings.value = true },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshOverlayState()
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
        if (ScreenCaptureManager.isGranted) {
            startCapture()
        } else {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(manager.createScreenCaptureIntent())
        }
    }

    private fun startCapture() {
        val serviceIntent = ScreenCaptureManager.buildServiceIntent(this, autoCapture = true)
        if (serviceIntent != null) {
            startForegroundService(serviceIntent)
            showToast("Scan started.")
        } else {
            showToast("Projection permission is required to start scanning.")
        }
        OverlayManager.startOverlay(this)
        refreshOverlayState()
    }

    private fun stopCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        ScreenCaptureManager.release()
    }

    private fun refreshOverlayState() {
        overlayRunning.value = OverlayManager.isOverlayRunning(this)
    }

    private fun sharePokemon(pokemon: Pokemon) {
        val shareText = buildString {
            append(pokemon.name)
            append(" • ")
            append(pokemon.rarityTierLabel)
            append(" • Score ")
            append(pokemon.rarityScore)
            append(" • CP ")
            append(pokemon.cp)
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
            )
        }
    }
}
