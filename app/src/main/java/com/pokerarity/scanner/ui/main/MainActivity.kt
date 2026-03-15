package com.pokerarity.scanner.ui.main

import android.Manifest
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.databinding.ActivityMainBinding
import com.pokerarity.scanner.service.OverlayManager
import com.pokerarity.scanner.PokeRarityApp
import com.pokerarity.scanner.service.ScreenCaptureManager
import com.pokerarity.scanner.service.ScreenCaptureService
import com.pokerarity.scanner.ui.result.HistoryActivity
import com.pokerarity.scanner.ui.result.ResultActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: ScanHistoryAdapter
    private lateinit var repository: PokemonRepository

    // ── Permission launchers ─────────────────────────────────────────────

    /** Overlay (SYSTEM_ALERT_WINDOW) permission result */
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (OverlayManager.canDrawOverlays(this)) {
            requestMediaProjection()
        } else {
            showToast(getString(R.string.overlay_permission_required))
        }
    }

    /** MediaProjection permission result */
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (ScreenCaptureManager.handleResult(result)) {
            startCapture()
        } else {
            showToast("Screen capture permission denied.")
            updateButtonState()
        }
    }

    /** Android 13+ notification permission result */
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            requestMediaProjection()
        } else {
            showToast("Notification permission is required to start scanning.")
            updateButtonState()
        }
    }

    
    // ── Lifecycle ────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PokemonRepository(AppDatabase.getInstance(this))

        setupUI()
        setupRecyclerView()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        updateButtonState()
    }

    // ── UI Setup ─────────────────────────────────────────────────────────

    private fun setupUI() {
        binding.btnStartScan.setOnClickListener { handleStartPressed() }
        binding.tvViewAll.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        
        binding.switchDebugOverlay.setOnCheckedChangeListener { _, isChecked ->
            if (OverlayManager.isOverlayRunning(this)) {
                val intent = Intent(this, com.pokerarity.scanner.service.OverlayService::class.java).apply {
                    putExtra("EXTRA_SHOW_DEBUG", isChecked)
                }
                startService(intent)
            }
        }
        updateButtonState()
    }

    private fun setupRecyclerView() {
        adapter = ScanHistoryAdapter { scan ->
            startActivity(Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_POKEMON_NAME, scan.pokemonName)
                putExtra(ResultActivity.EXTRA_CP, scan.cp ?: 0)
                putExtra(ResultActivity.EXTRA_HP, scan.hp ?: 0)
                putExtra(ResultActivity.EXTRA_SCORE, scan.rarityScore)
                putExtra(ResultActivity.EXTRA_TIER, scan.rarityTier)
                putExtra(ResultActivity.EXTRA_IS_SHINY, scan.isShiny)
                putExtra(ResultActivity.EXTRA_IS_SHADOW, scan.isShadow)
                putExtra(ResultActivity.EXTRA_IS_LUCKY, scan.isLucky)
                putExtra(ResultActivity.EXTRA_HAS_COSTUME, scan.hasCostume)
            })
        }
        binding.rvRecentScans.layoutManager = LinearLayoutManager(this)
        binding.rvRecentScans.adapter = adapter
    }

    private fun observeData() {
        lifecycleScope.launch {
            repository.getRecentScans(5).collectLatest { scans ->
                adapter.submitList(scans)
                binding.layoutEmpty.visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
                binding.rvRecentScans.visibility = if (scans.isEmpty()) View.GONE else View.VISIBLE
                binding.tvScanCount.text = scans.size.toString()
                binding.tvRareCount.text = scans.count { it.rarityScore >= 60 }.toString()
                binding.tvShinyCount.text = scans.count { it.isShiny }.toString()
            }
        }
    }

    // ── Button / Overlay logic ───────────────────────────────────────────

    private fun handleStartPressed() {
        // If currently running → stop everything
        if (OverlayManager.isOverlayRunning(this)) {
            OverlayManager.stopOverlay(this)
            stopCapture()
            showToast(getString(R.string.overlay_stopped))
            updateButtonState()
            return
        }

        // Step 1: ensure overlay permission
        if (!OverlayManager.canDrawOverlays(this)) {
            showToast(getString(R.string.overlay_permission_required))
            OverlayManager.requestOverlayPermission(overlayPermissionLauncher, this)
            return
        }

        // Step 2: ensure notification permission (Android 13+)
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

        
        // Step 3: ensure media projection
        requestMediaProjection()
    }

    private fun requestMediaProjection() {
        if (ScreenCaptureManager.isGranted) {
            startCapture()
        } else {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
        }
    }

    private fun startCapture() {
        // Start ScreenCaptureService
        val serviceIntent = ScreenCaptureManager.buildServiceIntent(this)
        if (serviceIntent != null) {
            startForegroundService(serviceIntent)
        }
        // Start floating overlay
        OverlayManager.startOverlay(this)
        showToast(getString(R.string.overlay_started))
        updateButtonState()
    }

    private fun stopCapture() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        ScreenCaptureManager.release()
    }

    private fun updateButtonState() {
        val isRunning = OverlayManager.isOverlayRunning(this)
        binding.btnStartScan.text = if (isRunning) getString(R.string.stop_overlay)
        else getString(R.string.start_overlay)
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
