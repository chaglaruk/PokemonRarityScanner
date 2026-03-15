# PokeRarityScanner Full Code Review


## // File: app/build.gradle.kts

``kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("kotlin-kapt")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.pokerarity.scanner"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.pokerarity.scanner"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    // Android Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")

    // Lifecycle & ViewModel
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.8.7")
    implementation("androidx.activity:activity-ktx:1.9.3")

    // Room Database
    implementation("androidx.room:room-runtime:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // Tesseract OCR
    implementation("com.rmtheis:tess-two:9.1.0")

    // Image Processing
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // Gson for JSON
    implementation("com.google.code.gson:gson:2.11.0")

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.54")
    kapt("com.google.dagger:hilt-compiler:2.54")

    // Testing
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}

// Allow references to generated code
kapt {
    correctErrorTypes = true
}

``


## // File: app/src/main/AndroidManifest.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Permissions -->
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission
        android:name="android.permission.WRITE_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />
    <uses-permission
        android:name="android.permission.READ_EXTERNAL_STORAGE"
        android:maxSdkVersion="28" />

    <application
        android:name=".PokeRarityApp"
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@style/Theme.PokeRarityScanner"
        tools:targetApi="34">

        <activity
            android:name=".ui.main.MainActivity"
            android:exported="true"
            android:theme="@style/Theme.PokeRarityScanner">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".service.OverlayService"
            android:foregroundServiceType="mediaProjection"
            android:exported="false" />

        <activity
            android:name=".ui.result.ResultActivity"
            android:theme="@style/Theme.PokeRarityScanner"
            android:exported="false" />

        <activity
            android:name=".ui.result.HistoryActivity"
            android:theme="@style/Theme.PokeRarityScanner"
            android:exported="false" />

    </application>

</manifest>

``


## // File: app/src/main/java/com/pokerarity/scanner/ui/main/MainActivity.kt

``kotlin
package com.pokerarity.scanner.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.databinding.ActivityMainBinding
import com.pokerarity.scanner.service.OverlayManager
import com.pokerarity.scanner.ui.result.HistoryActivity
import com.pokerarity.scanner.ui.result.ResultActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var adapter: ScanHistoryAdapter
    private lateinit var repository: PokemonRepository

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (OverlayManager.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PokemonRepository(AppDatabase.getInstance(this))

        setupUI()
        setupRecyclerView()
        observeData()
    }

    private fun setupUI() {
        binding.btnStartScan.setOnClickListener {
            handleOverlayStart()
        }

        binding.tvViewAll.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }

        updateButtonState()
    }

    private fun setupRecyclerView() {
        adapter = ScanHistoryAdapter { scan ->
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_POKEMON_NAME, scan.pokemonName)
                putExtra(ResultActivity.EXTRA_CP, scan.cp ?: 0)
                putExtra(ResultActivity.EXTRA_HP, scan.hp ?: 0)
                putExtra(ResultActivity.EXTRA_SCORE, scan.rarityScore)
                putExtra(ResultActivity.EXTRA_TIER, scan.rarityTier)
                putExtra(ResultActivity.EXTRA_IS_SHINY, scan.isShiny)
                putExtra(ResultActivity.EXTRA_IS_SHADOW, scan.isShadow)
                putExtra(ResultActivity.EXTRA_IS_LUCKY, scan.isLucky)
                putExtra(ResultActivity.EXTRA_HAS_COSTUME, scan.hasCostume)
            }
            startActivity(intent)
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

                // Update stats
                binding.tvScanCount.text = scans.size.toString()
                binding.tvRareCount.text = scans.count { it.rarityScore >= 60 }.toString()
                binding.tvShinyCount.text = scans.count { it.isShiny }.toString()
            }
        }
    }

    private fun handleOverlayStart() {
        if (OverlayManager.isOverlayRunning(this)) {
            OverlayManager.stopOverlay(this)
            Toast.makeText(this, R.string.overlay_stopped, Toast.LENGTH_SHORT).show()
            updateButtonState()
            return
        }

        if (OverlayManager.canDrawOverlays(this)) {
            startOverlayService()
        } else {
            Toast.makeText(this, R.string.overlay_permission_required, Toast.LENGTH_LONG).show()
            OverlayManager.requestOverlayPermission(overlayPermissionLauncher, this)
        }
    }

    private fun startOverlayService() {
        OverlayManager.startOverlay(this)
        Toast.makeText(this, R.string.overlay_started, Toast.LENGTH_SHORT).show()
        updateButtonState()
    }

    private fun updateButtonState() {
        val isRunning = OverlayManager.isOverlayRunning(this)
        binding.btnStartScan.text = if (isRunning) {
            getString(R.string.stop_overlay)
        } else {
            getString(R.string.start_overlay)
        }
    }

    override fun onResume() {
        super.onResume()
        updateButtonState()
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/PokeRarityApp.kt

``kotlin
package com.pokerarity.scanner

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class PokeRarityApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialize app-level dependencies here
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureService.kt

*(File not created yet or not found)*


## // File: app/src/main/java/com/pokerarity/scanner/service/ScreenCaptureManager.kt

*(File not created yet or not found)*


## // File: app/src/main/res/layout/activity_main.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<androidx.coordinatorlayout.widget.CoordinatorLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_dark"
    tools:context=".ui.main.MainActivity">

    <!-- AppBar with gradient -->
    <com.google.android.material.appbar.AppBarLayout
        android:id="@+id/appBarLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@drawable/bg_appbar_gradient"
        app:elevation="0dp">

        <com.google.android.material.appbar.MaterialToolbar
            android:id="@+id/toolbar"
            android:layout_width="match_parent"
            android:layout_height="?attr/actionBarSize"
            app:title="@string/app_name"
            app:titleTextColor="@color/white"
            app:titleTextAppearance="@style/TextAppearance.MaterialComponents.Headline6" />

    </com.google.android.material.appbar.AppBarLayout>

    <androidx.core.widget.NestedScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        app:layout_behavior="@string/appbar_scrolling_view_behavior">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:padding="16dp">

            <!-- Hero Section -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="24dp">

                <ImageView
                    android:id="@+id/ivPokeballHero"
                    android:layout_width="80dp"
                    android:layout_height="80dp"
                    android:src="@drawable/pokeball_overlay"
                    android:contentDescription="@string/app_name" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="12dp"
                    android:text="@string/subtitle"
                    android:textColor="@color/text_secondary"
                    android:textSize="14sp"
                    android:gravity="center" />

            </LinearLayout>

            <!-- Start Overlay Button -->
            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnStartScan"
                android:layout_width="match_parent"
                android:layout_height="64dp"
                android:layout_marginStart="24dp"
                android:layout_marginEnd="24dp"
                android:text="@string/start_overlay"
                android:textSize="18sp"
                android:textStyle="bold"
                android:textColor="@color/white"
                android:backgroundTint="@color/pokeball_red"
                app:cornerRadius="32dp"
                app:icon="@drawable/ic_pokeball"
                app:iconGravity="textStart"
                app:iconTint="@color/white"
                app:iconSize="24dp" />

            <!-- Stats Row -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:orientation="horizontal"
                android:weightSum="3">

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:background="@drawable/bg_card"
                    android:gravity="center"
                    android:orientation="vertical"
                    android:padding="12dp"
                    android:layout_marginEnd="4dp">

                    <TextView
                        android:id="@+id/tvScanCount"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="0"
                        android:textColor="@color/accent_gold"
                        android:textSize="24sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/scans"
                        android:textColor="@color/text_secondary"
                        android:textSize="11sp" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:background="@drawable/bg_card"
                    android:gravity="center"
                    android:orientation="vertical"
                    android:padding="12dp"
                    android:layout_marginStart="4dp"
                    android:layout_marginEnd="4dp">

                    <TextView
                        android:id="@+id/tvRareCount"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="0"
                        android:textColor="@color/tier_legendary"
                        android:textSize="24sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/rare_finds"
                        android:textColor="@color/text_secondary"
                        android:textSize="11sp" />
                </LinearLayout>

                <LinearLayout
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:background="@drawable/bg_card"
                    android:gravity="center"
                    android:orientation="vertical"
                    android:padding="12dp"
                    android:layout_marginStart="4dp">

                    <TextView
                        android:id="@+id/tvShinyCount"
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="0"
                        android:textColor="@color/shiny_star"
                        android:textSize="24sp"
                        android:textStyle="bold" />

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="@string/shinies"
                        android:textColor="@color/text_secondary"
                        android:textSize="11sp" />
                </LinearLayout>
            </LinearLayout>

            <!-- Recent Scans Header -->
            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="24dp"
                android:orientation="horizontal"
                android:gravity="center_vertical">

                <TextView
                    android:layout_width="0dp"
                    android:layout_height="wrap_content"
                    android:layout_weight="1"
                    android:text="@string/recent_scans"
                    android:textColor="@color/text_primary"
                    android:textSize="18sp"
                    android:textStyle="bold" />

                <TextView
                    android:id="@+id/tvViewAll"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/view_all"
                    android:textColor="@color/accent_teal"
                    android:textSize="14sp"
                    android:padding="8dp" />
            </LinearLayout>

            <!-- Recent Scans RecyclerView -->
            <androidx.recyclerview.widget.RecyclerView
                android:id="@+id/rvRecentScans"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:nestedScrollingEnabled="false"
                tools:listitem="@layout/item_scan_history" />

            <!-- Empty State -->
            <LinearLayout
                android:id="@+id/layoutEmpty"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="32dp"
                android:visibility="gone">

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/no_scans_yet"
                    android:textColor="@color/text_hint"
                    android:textSize="16sp" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/tap_start"
                    android:textColor="@color/text_hint"
                    android:textSize="13sp" />
            </LinearLayout>

        </LinearLayout>

    </androidx.core.widget.NestedScrollView>

</androidx.coordinatorlayout.widget.CoordinatorLayout>

``


## // File: app/src/main/java/com/pokerarity/scanner/service/OverlayService.kt

``kotlin
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
        const val ACTION_CAPTURE_REQUESTED = "com.pokerarity.scanner.CAPTURE_REQUESTED"
        private const val CHANNEL_ID = "overlay_channel"
        private const val NOTIFICATION_ID = 1001
        private const val LONG_PRESS_DELAY = 500L
        private const val CLICK_THRESHOLD_DP = 10
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayView: View
    private var closeView: View? = null

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
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addOverlayView()
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
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_button, null)
        setupTouchListener(params)
        windowManager.addView(overlayView, params)
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

``


## // File: app/src/main/java/com/pokerarity/scanner/service/OverlayManager.kt

``kotlin
package com.pokerarity.scanner.service

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

/**
 * Manages the overlay service lifecycle and permission handling.
 *
 * Usage:
 *   1. Check [canDrawOverlays] before starting.
 *   2. If false, call [requestOverlayPermission] with an ActivityResultLauncher.
 *   3. Once granted, call [startOverlay] to show the floating PokeBall.
 *   4. Call [stopOverlay] to remove it.
 */
object OverlayManager {

    /**
     * Check if the app has SYSTEM_ALERT_WINDOW permission.
     */
    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    /**
     * Open system settings to request overlay permission.
     * The result is delivered to the [launcher] callback.
     */
    fun requestOverlayPermission(launcher: ActivityResultLauncher<Intent>, context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        launcher.launch(intent)
    }

    /**
     * Start the [OverlayService] as a foreground service.
     * Requires overlay permission to already be granted.
     */
    fun startOverlay(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.startForegroundService(intent)
    }

    /**
     * Stop the [OverlayService] and remove the overlay.
     */
    fun stopOverlay(context: Context) {
        val intent = Intent(context, OverlayService::class.java)
        context.stopService(intent)
    }

    /**
     * Check if the [OverlayService] is currently running.
     */
    @Suppress("DEPRECATION")
    fun isOverlayRunning(context: Context): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (OverlayService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

``


## // File: app/src/main/res/layout/overlay_button.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="60dp"
    android:layout_height="60dp"
    android:padding="4dp">

    <ImageView
        android:id="@+id/ivPokeball"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:src="@drawable/pokeball_overlay"
        android:contentDescription="@string/overlay_button_desc"
        android:elevation="8dp" />

</FrameLayout>

``


## // File: app/src/main/java/com/pokerarity/scanner/receiver/CaptureReceiver.kt

*(File not created yet or not found)*


## // File: app/src/main/java/com/pokerarity/scanner/util/ocr/OCRProcessor.kt

``kotlin
package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import com.pokerarity.scanner.data.model.PokemonData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Main OCR processor using Tesseract (tess-two).
 * Handles parallel extraction of multiple regions from a single screenshot.
 */
class OCRProcessor(private val context: Context) {

    private val textParser = TextParser(context)
    private var isInitialized = false

    /**
     * Initialize Tesseract data by copying it from assets to internal storage.
     * This takes time, so it runs in Dispatchers.IO.
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext

        val tessDataDir = File(context.filesDir, "tessdata")
        if (!tessDataDir.exists()) {
            tessDataDir.mkdirs()
        }

        val trainedDataFile = File(tessDataDir, "eng.traineddata")
        if (!trainedDataFile.exists()) {
            try {
                context.assets.open("tessdata/eng.traineddata").use { input ->
                    FileOutputStream(trainedDataFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext
            }
        }
        isInitialized = true
    }

    /**
     * Process a full screenshot bitmap.
     * Runs preprocessing (grayscale, contrast) once, then crops and runs Tesseract on each region in parallel.
     */
    suspend fun processImage(bitmap: Bitmap): PokemonData = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            initialize() // Lazy init fallback
        }

        // 1. Preprocess the entire image once
        val processedBitmap = ImagePreprocessor.process(bitmap)

        // 2. Start parallel extraction jobs
        // TessBaseAPI is not thread-safe, so we create an instance per coroutine job
        val cpJob = async { extractRegion(processedBitmap, ScreenRegions.REGION_CP, true) }
        val hpJob = async { extractRegion(processedBitmap, ScreenRegions.REGION_HP, true) }
        val nameJob = async { extractRegion(processedBitmap, ScreenRegions.REGION_NAME, false) }
        val dateJob = async { extractRegion(processedBitmap, ScreenRegions.REGION_DATE, false) }

        // 3. Wait for all OCR results
        val cpText = cpJob.await()
        val hpText = hpJob.await()
        val nameText = nameJob.await()
        val dateText = dateJob.await()

        // 4. Parse the extracted text into typed data
        val cpParsed = textParser.parseCP(cpText)
        val hpParsed = textParser.parseHP(hpText)
        val nameParsed = textParser.parseName(nameText)
        val dateParsed = textParser.parseDate(dateText)

        // Compile raw text for debugging
        val rawOcrText = """
            CP: $cpText
            HP: $hpText
            Name: $nameText
            Date: $dateText
        """.trimIndent()

        PokemonData(
            cp = cpParsed,
            hp = hpParsed,
            name = nameParsed,
            caughtDate = dateParsed,
            rawOcrText = rawOcrText
        )
    }

    /**
     * Creates a temporary TessBaseAPI instance to extract text from a cropped region.
     */
    private fun extractRegion(
        sourceBitmap: Bitmap,
        region: ScreenRegions.Region,
        numericOnly: Boolean
    ): String {
        // Crop the specific region
        val regionRect = ScreenRegions.getRectForRegion(sourceBitmap, region)
        // Ensure valid rect dimensions
        val safeWidth = regionRect.width().coerceAtMost(sourceBitmap.width - regionRect.left)
        val safeHeight = regionRect.height().coerceAtMost(sourceBitmap.height - regionRect.top)
        
        if (safeWidth <= 0 || safeHeight <= 0) return ""

        val croppedBitmap = Bitmap.createBitmap(
            sourceBitmap,
            regionRect.left,
            regionRect.top,
            safeWidth,
            safeHeight
        )

        // Run OCR
        val tess = TessBaseAPI()
        // Path must point to the parent directory of 'tessdata/'
        val dataPath = context.filesDir.absolutePath
        tess.init(dataPath, "eng")

        if (numericOnly) {
            tess.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789CPHP/")
        }

        tess.setImage(croppedBitmap)
        val result = tess.utF8Text ?: ""
        
        tess.end() // Required to free native memory
        croppedBitmap.recycle()
        return result.trim()
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/util/ocr/ScreenRegions.kt

``kotlin
package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect

/**
 * Defines the standard percentage-based bounding boxes for OCR extraction.
 * Percentages are based on a 720p normalized aspect ratio screen.
 */
object ScreenRegions {

    data class Region(
        val topPercent: Float,
        val leftPercent: Float,
        val widthPercent: Float,
        val heightPercent: Float
    )

    // Rough percentages for Pokemon stats on standard Android aspect ratio
    val REGION_CP = Region(0.08f, 0.05f, 0.35f, 0.10f)
    val REGION_HP = Region(0.08f, 0.60f, 0.35f, 0.10f)
    val REGION_NAME = Region(0.18f, 0.20f, 0.60f, 0.08f)
    val REGION_DATE = Region(0.75f, 0.25f, 0.50f, 0.06f)

    /**
     * Convert percentage-based region into absolute pixel coordinates for cropping.
     */
    fun getRectForRegion(bitmap: Bitmap, region: Region): Rect {
        val width = bitmap.width
        val height = bitmap.height

        val left = (width * region.leftPercent).toInt()
        val top = (height * region.topPercent).toInt()
        val right = left + (width * region.widthPercent).toInt()
        val bottom = top + (height * region.heightPercent).toInt()

        // Clamp to bitmap boundaries
        return Rect(
            left.coerceIn(0, width),
            top.coerceIn(0, height),
            right.coerceIn(0, width),
            bottom.coerceIn(0, height)
        )
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/data/model/PokemonData.kt

``kotlin
package com.pokerarity.scanner.data.model

import java.util.Date

/**
 * Data extracted from a Pokemon GO screenshot.
 */
data class PokemonData(
    val cp: Int?,
    val hp: Int?,
    val name: String?,
    val caughtDate: Date?,
    val rawOcrText: String = ""
)

``


## // File: app/src/main/java/com/pokerarity/scanner/util/ocr/ImagePreprocessor.kt

``kotlin
package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImagePreprocessor {

    /**
     * Prepares an image file for OCR.
     */
    fun loadAndPreprocess(imagePath: String): Bitmap? {
        val rawBitmap = BitmapFactory.decodeFile(imagePath) ?: return null
        return process(rawBitmap)
    }

    /**
     * Standardizes image size and colors for better OCR accuracy.
     */
    fun process(bitmap: Bitmap): Bitmap {
        val resized = resizeForOcr(bitmap)
        return enhanceForOcr(resized)
    }

    /**
     * Extracts a specific region from the preprocessed bitmap.
     */
    fun cropRegion(bitmap: Bitmap, region: ScreenRegions.Region): Bitmap {
        val rect = ScreenRegions.getRectForRegion(bitmap, region)
        // Ensure rect is valid before cropping
        val safeWidth = rect.width().coerceAtMost(bitmap.width - rect.left)
        val safeHeight = rect.height().coerceAtMost(bitmap.height - rect.top)
        
        return if (safeWidth > 0 && safeHeight > 0) {
            Bitmap.createBitmap(bitmap, rect.left, rect.top, safeWidth, safeHeight)
        } else {
            bitmap // Fallback if region is invalid
        }
    }

    /**
     * Scale to a standard 720p width maintaining aspect ratio. 
     * Tesseract works best with text that is ~30px high, so upscaling/downscaling to a known baseline helps.
     */
    private fun resizeForOcr(bitmap: Bitmap): Bitmap {
        val targetWidth = 720
        if (bitmap.width == targetWidth) return bitmap

        val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
        val targetHeight = (bitmap.height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    /**
     * Convert to grayscale and drop contrast/brightness to make white text pop on dark backgrounds.
     */
    private fun enhanceForOcr(bitmap: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()

        // 1. Grayscale matrix
        val grayScaleMatrix = ColorMatrix().apply { setSaturation(0f) }

        // 2. High contrast matrix for removing anti-aliasing artifacts
        val contrast = 1.5f // 1.0 is normal, higher is more contrast
        val brightness = -30f // Lower brightness to push grays to black
        
        val contrastMatrix = ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, brightness,
                0f, contrast, 0f, 0f, brightness,
                0f, 0f, contrast, 0f, brightness,
                0f, 0f, 0f, 1f, 0f
            )
        )

        contrastMatrix.preConcat(grayScaleMatrix)
        paint.colorFilter = ColorMatrixColorFilter(contrastMatrix)
        
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/util/ocr/TextParser.kt

``kotlin
package com.pokerarity.scanner.util.ocr

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min

/**
 * Parses raw OCR text into typed data (Int, String, Date) using Regex and Fuzzy Matching.
 */
class TextParser(context: Context) {

    private val pokemonNames: List<String> = loadPokemonNames(context)

    /**
     * Extracts CP value from text (e.g., "CP 1500", "CP1 500").
     */
    fun parseCP(text: String): Int? {
        // Remove spaces inside numbers, but allow space after CP
        val cleanText = text.replace(Regex("(?<=\\d)\\s+(?=\\d)"), "").uppercase()
        val match = Regex("C\\s*P\\s*(\\d+)").find(cleanText)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Extracts HP value from text (e.g., "HP 120/120" -> 120).
     */
    fun parseHP(text: String): Int? {
        val cleanText = text.uppercase()
        val match = Regex("H\\s*P\\s*(\\d+)(?:\\s*/\\s*(\\d+))?").find(cleanText)
        return match?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * Extracts caught date from text (e.g., "Caught on 08/15/2021").
     */
    fun parseDate(text: String): Date? {
        val formats = listOf(
            "MM/dd/yyyy",
            "dd/MM/yyyy",
            "yyyy/MM/dd",
            "MM-dd-yyyy",
            "dd-MM-yyyy"
        )
        // Extract anything that looks like a date string
        val match = Regex("(\\d{2,4}[/\\-]\\d{2}[/\\-]\\d{2,4})").find(text)
        val dateString = match?.groupValues?.get(1) ?: return null

        for (format in formats) {
            try {
                val sdf = SimpleDateFormat(format, Locale.US)
                sdf.isLenient = false
                return sdf.parse(dateString)
            } catch (e: Exception) {
                // Ignore and try next format
            }
        }
        return null
    }

    /**
     * Finds the closest matching Pokemon name using Levenshtein distance.
     * Ignore case and non-alphabetic characters.
     */
    fun parseName(ocrText: String): String? {
        val cleanInput = ocrText.replace(Regex("[^A-Za-z]"), "").lowercase().trim()
        if (cleanInput.isEmpty()) return null

        var bestMatch: String? = null
        var minDistance = Int.MAX_VALUE

        // Iterate through all names to find the closest match
        for (name in pokemonNames) {
            val cleanName = name.replace(Regex("[^A-Za-z]"), "").lowercase()
            val distance = levenshtein(cleanInput, cleanName)

            // Exact match
            if (distance == 0) return name

            // Allow up to 3 typos for a match to still be considered
            if (distance < minDistance && distance <= 3) {
                minDistance = distance
                bestMatch = name
            }
        }

        return bestMatch
    }

    /**
     * Helper to load names from assets.
     */
    private fun loadPokemonNames(context: Context): List<String> {
        return try {
            val inputStream = context.assets.open("data/pokemon_names.json")
            val reader = InputStreamReader(inputStream)
            val type = object : TypeToken<List<String>>() {}.type
            Gson().fromJson(reader, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Calculates Levenshtein distance between two strings.
     */
    private fun levenshtein(lhs: CharSequence, rhs: CharSequence): Int {
        val len0 = lhs.length + 1
        val len1 = rhs.length + 1

        var cost = IntArray(len0)
        var newcost = IntArray(len0)

        for (i in 0 until len0) cost[i] = i

        for (j in 1 until len1) {
            newcost[0] = j
            for (i in 1 until len0) {
                val match = if (lhs[i - 1] == rhs[j - 1]) 0 else 1
                val costReplace = cost[i - 1] + match
                val costInsert = cost[i] + 1
                val costDelete = newcost[i - 1] + 1
                newcost[i] = min(min(costInsert, costDelete), costReplace)
            }
            val swap = cost
            cost = newcost
            newcost = swap
        }
        return cost[len0 - 1]
    }
}

``


## // File: app/src/main/assets/data/pokemon_names.json

``json
[
  "Bulbasaur", "Ivysaur", "Venusaur", "Charmander", "Charmeleon", "Charizard", "Squirtle", "Wartortle", "Blastoise", "Caterpie", "Metapod", "Butterfree", "Weedle", "Kakuna", "Beedrill", "Pidgey", "Pidgeotto", "Pidgeot", "Rattata", "Raticate", "Spearow", "Fearow", "Ekans", "Arbok", "Pikachu", "Raichu", "Sandshrew", "Sandslash", "Nidoran♀", "Nidorina", "Nidoqueen", "Nidoran♂", "Nidorino", "Nidoking", "Clefairy", "Clefable", "Vulpix", "Ninetales", "Jigglypuff", "Wigglytuff", "Zubat", "Golbat", "Oddish", "Gloom", "Vileplume", "Paras", "Parasect", "Venonat", "Venomoth", "Diglett", "Dugtrio", "Meowth", "Persian", "Psyduck", "Golduck", "Mankey", "Primeape", "Growlithe", "Arcanine", "Poliwag", "Poliwhirl", "Poliwrath", "Abra", "Kadabra", "Alakazam", "Machop", "Machoke", "Machamp", "Bellsprout", "Weepinbell", "Victreebel", "Tentacool", "Tentacruel", "Geodude", "Graveler", "Golem", "Ponyta", "Rapidash", "Slowpoke", "Slowbro", "Magnemite", "Magneton", "Farfetch'd", "Doduo", "Dodrio", "Seel", "Dewgong", "Grimer", "Muk", "Shellder", "Cloyster", "Gastly", "Haunter", "Gengar", "Onix", "Drowzee", "Hypno", "Krabby", "Kingler", "Voltorb", "Electrode", "Exeggcute", "Exeggutor", "Cubone", "Marowak", "Hitmonlee", "Hitmonchan", "Lickitung", "Koffing", "Weezing", "Rhyhorn", "Rhydon", "Chansey", "Tangela", "Kangaskhan", "Horsea", "Seadra", "Goldeen", "Seaking", "Staryu", "Starmie", "Mr. Mime", "Scyther", "Jynx", "Electabuzz", "Magmar", "Pinsir", "Tauros", "Magikarp", "Gyarados", "Lapras", "Ditto", "Eevee", "Vaporeon", "Jolteon", "Flareon", "Porygon", "Omanyte", "Omastar", "Kabuto", "Kabutops", "Aerodactyl", "Snorlax", "Articuno", "Zapdos", "Moltres", "Dratini", "Dragonair", "Dragonite", "Mewtwo", "Mew",
  "Chikorita", "Bayleef", "Meganium", "Cyndaquil", "Quilava", "Typhlosion", "Totodile", "Croconaw", "Feraligatr", "Sentret", "Furret", "Hoothoot", "Noctowl", "Ledyba", "Ledian", "Spinarak", "Ariados", "Crobat", "Chinchou", "Lanturn", "Pichu", "Cleffa", "Igglybuff", "Togepi", "Togetic", "Natu", "Xatu", "Mareep", "Flaaffy", "Ampharos", "Bellossom", "Marill", "Azumarill", "Sudowoodo", "Politoed", "Hoppip", "Skiploom", "Jumpluff", "Aipom", "Sunkern", "Sunflora", "Yanma", "Wooper", "Quagsire", "Espeon", "Umbreon", "Murkrow", "Slowking", "Misdreavus", "Unown", "Wobbuffet", "Girafarig", "Pineco", "Forretress", "Dunsparce", "Gligar", "Steelix", "Snubbull", "Granbull", "Qwilfish", "Scizor", "Shuckle", "Heracross", "Sneasel", "Teddiursa", "Ursaring", "Slugma", "Magcargo", "Swinub", "Piloswine", "Corsola", "Remoraid", "Octillery", "Delibird", "Mantine", "Skarmory", "Houndour", "Houndoom", "Kingdra", "Phanpy", "Donphan", "Porygon2", "Stantler", "Smeargle", "Tyrogue", "Hitmontop", "Smoochum", "Elekid", "Magby", "Miltank", "Blissey", "Raikou", "Entei", "Suicune", "Larvitar", "Pupitar", "Tyranitar", "Lugia", "Ho-Oh", "Celebi",
  "Treecko", "Grovyle", "Sceptile", "Torchic", "Combusken", "Blaziken", "Mudkip", "Marshtomp", "Swampert", "Poochyena", "Mightyena", "Zigzagoon", "Linoone", "Wurmple", "Silcoon", "Beautifly", "Cascoon", "Dustox", "Lotad", "Lombre", "Ludicolo", "Seedot", "Nuzleaf", "Shiftry", "Taillow", "Swellow", "Wingull", "Pelipper", "Ralts", "Kirlia", "Gardevoir", "Surskit", "Masquerain", "Shroomish", "Breloom", "Slakoth", "Vigoroth", "Slaking", "Nincada", "Ninjask", "Shedinja", "Whismur", "Loudred", "Exploud", "Makuhita", "Hariyama", "Azurill", "Nosepass", "Skitty", "Delcatty", "Sableye", "Mawile", "Aron", "Lairon", "Aggron", "Meditite", "Medicham", "Electrike", "Manectric", "Plusle", "Minun", "Volbeat", "Illumise", "Roselia", "Gulpin", "Swalot", "Carvanha", "Sharpedo", "Wailmer", "Wailord", "Numel", "Camerupt", "Torkoal", "Spoink", "Grumpig", "Spinda", "Trapinch", "Vibrava", "Flygon", "Cacnea", "Cacturne", "Swablu", "Altaria", "Zangoose", "Seviper", "Lunatone", "Solrock", "Barboach", "Whiscash", "Corphish", "Crawdaunt", "Baltoy", "Claydol", "Lileep", "Cradily", "Anorith", "Armaldo", "Feebas", "Milotic", "Castform", "Kecleon", "Shuppet", "Banette", "Duskull", "Dusclops", "Tropius", "Chimecho", "Absol", "Wynaut", "Snorunt", "Glalie", "Spheal", "Sealeo", "Walrein", "Clamperl", "Huntail", "Gorebyss", "Relicanth", "Luvdisc", "Bagon", "Shelgon", "Salamence", "Beldum", "Metang", "Metagross", "Regirock", "Regice", "Registeel", "Latias", "Latios", "Kyogre", "Groudon", "Rayquaza", "Jirachi", "Deoxys"
]

``


## // File: app/src/main/java/com/pokerarity/scanner/util/vision/VisualFeatureDetector.kt

``kotlin
package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.data.model.VisualFeatures
import java.io.InputStreamReader
import kotlin.math.abs
import kotlin.math.min

/**
 * Detects visual features (Shiny, Shadow, Lucky, Costume) from a Pokemon GO screenshot
 * using native Android color analysis. No OpenCV dependency required.
 *
 * All analysis runs on a 360p downscaled bitmap for performance.
 */
class VisualFeatureDetector(private val context: Context) {

    /**
     * Reference hue data for normal vs shiny forms.
     */
    private data class ShinyReference(val normalHue: Int, val shinyHue: Int)

    private val shinyReferences: Map<String, ShinyReference> by lazy { loadShinyReferences() }

    // ──────────────────────────────────────────────────
    // HSV Constants for feature detection
    // ──────────────────────────────────────────────────

    /** Shadow Pokemon: purple aura around sprite */
    private val SHADOW_HUE_RANGE = 260..280
    private const val SHADOW_MIN_SATURATION = 0.50f
    private const val SHADOW_MIN_VALUE = 0.30f
    private const val SHADOW_THRESHOLD = 0.08f // 8% purple pixels in border = shadow

    /** Lucky Pokemon: golden/yellow background */
    private val LUCKY_HUE_RANGE = 45..65
    private const val LUCKY_MIN_SATURATION = 0.60f
    private const val LUCKY_MIN_VALUE = 0.70f
    private const val LUCKY_THRESHOLD = 0.15f // 15% yellow in background = lucky

    /** Hue difference threshold to consider a pokemon as shiny */
    private const val SHINY_HUE_DIFF_THRESHOLD = 20f

    /**
     * Run all detections and return combined results with a confidence score.
     */
    fun detect(bitmap: Bitmap, pokemonName: String? = null): VisualFeatures {
        val smallBitmap = ColorAnalyzer.downscaleForAnalysis(bitmap)

        val shinyResult = isShiny(smallBitmap, pokemonName)
        val shadowResult = isShadow(smallBitmap)
        val luckyResult = isLucky(smallBitmap)
        val costumeResult = hasCostume(smallBitmap, pokemonName)

        // Aggregate confidence from individual detections
        val confidenceScores = mutableListOf<Float>()
        if (shinyResult.first) confidenceScores.add(shinyResult.second)
        if (shadowResult.first) confidenceScores.add(shadowResult.second)
        if (luckyResult.first) confidenceScores.add(luckyResult.second)
        if (costumeResult.first) confidenceScores.add(costumeResult.second)

        val avgConfidence = if (confidenceScores.isNotEmpty()) {
            confidenceScores.average().toFloat()
        } else {
            1.0f // No special features = high confidence in "normal"
        }

        return VisualFeatures(
            isShiny = shinyResult.first,
            isShadow = shadowResult.first,
            isLucky = luckyResult.first,
            hasCostume = costumeResult.first,
            confidence = avgConfidence
        )
    }

    // ──────────────────────────────────────────────────
    // Shiny Detection
    // ──────────────────────────────────────────────────

    /**
     * Detect if the Pokemon is shiny by comparing the sprite's dominant hue
     * against the known reference for this species.
     *
     * @return Pair(isShiny, confidence)
     */
    fun isShiny(bitmap: Bitmap, pokemonName: String?): Pair<Boolean, Float> {
        if (pokemonName == null) return Pair(false, 0f)

        val reference = shinyReferences[pokemonName] ?: return Pair(false, 0f)

        val spriteRegion = ColorAnalyzer.getSpriteRegion(bitmap)
        val dominantHue = ColorAnalyzer.getDominantHue(bitmap, spriteRegion)

        // Distance from normal hue
        val distFromNormal = hueDistance(dominantHue, reference.normalHue.toFloat())
        // Distance from shiny hue
        val distFromShiny = hueDistance(dominantHue, reference.shinyHue.toFloat())

        // It's shiny if the hue is significantly closer to the shiny reference
        // AND the difference between normal and shiny hue is large enough to detect
        val normalShinyDist = hueDistance(
            reference.normalHue.toFloat(),
            reference.shinyHue.toFloat()
        )

        if (normalShinyDist < SHINY_HUE_DIFF_THRESHOLD) {
            // Normal and shiny are too similar to reliably detect via color alone
            return Pair(false, 0f)
        }

        val isShiny = distFromShiny < distFromNormal &&
                distFromShiny < SHINY_HUE_DIFF_THRESHOLD

        val confidence = if (isShiny) {
            // Higher confidence when farther from normal and closer to shiny
            val ratio = 1f - (distFromShiny / normalShinyDist)
            ratio.coerceIn(0.5f, 1.0f)
        } else {
            0f
        }

        return Pair(isShiny, confidence)
    }

    // ──────────────────────────────────────────────────
    // Shadow Detection
    // ──────────────────────────────────────────────────

    /**
     * Detect shadow Pokemon by looking for a purple aura around the sprite.
     * Shadow Pokemon have a distinctive purple haze (HSV: H=260-280) around
     * the border of their sprite.
     *
     * @return Pair(isShadow, confidence)
     */
    fun isShadow(bitmap: Bitmap): Pair<Boolean, Float> {
        val borderRegion = ColorAnalyzer.getSpriteBorderRegion(bitmap)

        val purplePercentage = ColorAnalyzer.getColorPercentage(
            bitmap,
            borderRegion,
            SHADOW_HUE_RANGE,
            SHADOW_MIN_SATURATION,
            SHADOW_MIN_VALUE
        )

        // Also check average brightness - shadows tend to be darker
        val avgBrightness = ColorAnalyzer.getAverageBrightness(bitmap, borderRegion)

        val isShadow = purplePercentage >= SHADOW_THRESHOLD && avgBrightness < 0.6f

        val confidence = if (isShadow) {
            min(purplePercentage / (SHADOW_THRESHOLD * 2), 1.0f)
        } else {
            0f
        }

        return Pair(isShadow, confidence)
    }

    // ──────────────────────────────────────────────────
    // Lucky Detection
    // ──────────────────────────────────────────────────

    /**
     * Detect lucky Pokemon by looking for a golden/yellow background.
     * Lucky Pokemon have a distinctive golden sparkle background
     * (HSV: H=45-65, S=60-100%, V=70-100%).
     *
     * @return Pair(isLucky, confidence)
     */
    fun isLucky(bitmap: Bitmap): Pair<Boolean, Float> {
        val bgRegion = ColorAnalyzer.getBackgroundRegion(bitmap)

        val yellowPercentage = ColorAnalyzer.getColorPercentage(
            bitmap,
            bgRegion,
            LUCKY_HUE_RANGE,
            LUCKY_MIN_SATURATION,
            LUCKY_MIN_VALUE
        )

        val isLucky = yellowPercentage >= LUCKY_THRESHOLD

        val confidence = if (isLucky) {
            min(yellowPercentage / (LUCKY_THRESHOLD * 2), 1.0f)
        } else {
            0f
        }

        return Pair(isLucky, confidence)
    }

    // ──────────────────────────────────────────────────
    // Costume Detection
    // ──────────────────────────────────────────────────

    /**
     * Simple costume detection by checking if the head region has
     * unexpected dominant colors compared to the base form.
     *
     * This is a rough heuristic — for a more robust implementation,
     * consider using pHash or template matching.
     *
     * @return Pair(hasCostume, confidence)
     */
    fun hasCostume(bitmap: Bitmap, pokemonName: String?): Pair<Boolean, Float> {
        if (pokemonName == null) return Pair(false, 0f)

        val reference = shinyReferences[pokemonName] ?: return Pair(false, 0f)

        // Check the "head" region (top portion of sprite area)
        val spriteRegion = ColorAnalyzer.getSpriteRegion(bitmap)
        val headRegion = android.graphics.Rect(
            spriteRegion.left,
            spriteRegion.top,
            spriteRegion.right,
            spriteRegion.top + spriteRegion.height() / 3
        )

        val headHue = ColorAnalyzer.getDominantHue(bitmap, headRegion)
        val normalHue = reference.normalHue.toFloat()
        val shinyHue = reference.shinyHue.toFloat()

        // If the head hue is far from both normal and shiny reference,
        // it might have a costume (hat, accessory, etc.)
        val distFromNormal = hueDistance(headHue, normalHue)
        val distFromShiny = hueDistance(headHue, shinyHue)

        val hasCostume = distFromNormal > 40f && distFromShiny > 40f

        val confidence = if (hasCostume) {
            val avgDist = (distFromNormal + distFromShiny) / 2f
            min(avgDist / 90f, 0.8f) // Cap at 0.8 — costume detection is inherently uncertain
        } else {
            0f
        }

        return Pair(hasCostume, confidence)
    }

    // ──────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────

    /**
     * Calculate the shortest distance between two hues on the color wheel (0-360).
     */
    private fun hueDistance(h1: Float, h2: Float): Float {
        val diff = abs(h1 - h2)
        return min(diff, 360f - diff)
    }

    /**
     * Load shiny reference hues from assets JSON.
     */
    private fun loadShinyReferences(): Map<String, ShinyReference> {
        return try {
            val input = context.assets.open("data/shiny_references.json")
            val reader = InputStreamReader(input)
            val type = object : TypeToken<Map<String, ShinyReference>>() {}.type
            Gson().fromJson(reader, type)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/util/vision/ColorAnalyzer.kt

``kotlin
package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import kotlin.math.roundToInt

/**
 * Low-level color analysis utilities for detecting visual features in Pokemon screenshots.
 * Uses native Android Bitmap pixel access — no OpenCV dependency needed.
 *
 * All analysis samples every [SAMPLE_STEP]th pixel for performance.
 */
object ColorAnalyzer {

    /** Sample every 4th pixel for speed. */
    private const val SAMPLE_STEP = 4

    /** Target width for analysis (360p for speed). */
    private const val ANALYSIS_WIDTH = 360

    /**
     * HSV color data for a sampled pixel.
     */
    data class HSVPixel(val h: Float, val s: Float, val v: Float)

    /**
     * Downscale a bitmap to [ANALYSIS_WIDTH] for faster pixel analysis.
     */
    fun downscaleForAnalysis(bitmap: Bitmap): Bitmap {
        if (bitmap.width <= ANALYSIS_WIDTH) return bitmap
        val ratio = ANALYSIS_WIDTH.toFloat() / bitmap.width
        val targetHeight = (bitmap.height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, ANALYSIS_WIDTH, targetHeight, true)
    }

    /**
     * Get the dominant hue (0-360) from a bitmap region.
     * Builds a histogram of hues (ignoring very dark or desaturated pixels)
     * and returns the peak bin.
     */
    fun getDominantHue(bitmap: Bitmap, region: Rect? = null): Float {
        val histogram = IntArray(360) // 1-degree bins
        val hsv = FloatArray(3)

        val left = region?.left ?: 0
        val top = region?.top ?: 0
        val right = region?.right ?: bitmap.width
        val bottom = region?.bottom ?: bitmap.height

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                // Only count pixels with enough saturation and brightness
                if (hsv[1] > 0.2f && hsv[2] > 0.2f) {
                    val hueBin = hsv[0].toInt().coerceIn(0, 359)
                    histogram[hueBin]++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        // Find peak hue
        var maxCount = 0
        var dominantHue = 0
        for (i in histogram.indices) {
            if (histogram[i] > maxCount) {
                maxCount = histogram[i]
                dominantHue = i
            }
        }
        return dominantHue.toFloat()
    }

    /**
     * Returns the percentage (0.0 - 1.0) of sampled pixels in a region
     * whose hue falls within [hueRange] and passes saturation/value thresholds.
     */
    fun getColorPercentage(
        bitmap: Bitmap,
        region: Rect? = null,
        hueRange: IntRange,
        minSaturation: Float = 0.3f,
        minValue: Float = 0.3f
    ): Float {
        val hsv = FloatArray(3)
        var total = 0
        var matching = 0

        val left = region?.left ?: 0
        val top = region?.top ?: 0
        val right = region?.right ?: bitmap.width
        val bottom = region?.bottom ?: bitmap.height

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                total++

                val hue = hsv[0].toInt()
                if (hue in hueRange && hsv[1] >= minSaturation && hsv[2] >= minValue) {
                    matching++
                }
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        return if (total > 0) matching.toFloat() / total else 0f
    }

    /**
     * Check if a specific color range exists in a region at a given threshold.
     */
    fun hasColorInRegion(
        bitmap: Bitmap,
        region: Rect,
        hueRange: IntRange,
        minSaturation: Float = 0.3f,
        minValue: Float = 0.3f,
        threshold: Float = 0.1f
    ): Boolean {
        return getColorPercentage(bitmap, region, hueRange, minSaturation, minValue) >= threshold
    }

    /**
     * Get the average brightness (V channel) of a region. Used for shadow detection.
     */
    fun getAverageBrightness(bitmap: Bitmap, region: Rect? = null): Float {
        val hsv = FloatArray(3)
        var total = 0
        var brightnessSum = 0f

        val left = region?.left ?: 0
        val top = region?.top ?: 0
        val right = region?.right ?: bitmap.width
        val bottom = region?.bottom ?: bitmap.height

        var y = top
        while (y < bottom) {
            var x = left
            while (x < right) {
                val pixel = bitmap.getPixel(x, y)
                Color.colorToHSV(pixel, hsv)
                brightnessSum += hsv[2]
                total++
                x += SAMPLE_STEP
            }
            y += SAMPLE_STEP
        }

        return if (total > 0) brightnessSum / total else 0f
    }

    /**
     * Get the sprite area rectangle (center 30% of screen).
     */
    fun getSpriteRegion(bitmap: Bitmap): Rect {
        val cx = bitmap.width / 2
        val cy = bitmap.height / 2
        val halfW = (bitmap.width * 0.15f).roundToInt()
        val halfH = (bitmap.height * 0.15f).roundToInt()
        return Rect(
            (cx - halfW).coerceAtLeast(0),
            (cy - halfH).coerceAtLeast(0),
            (cx + halfW).coerceAtMost(bitmap.width),
            (cy + halfH).coerceAtMost(bitmap.height)
        )
    }

    /**
     * Get the background region (area behind the Pokemon, excluding the sprite).
     * Uses a ring around the sprite center.
     */
    fun getBackgroundRegion(bitmap: Bitmap): Rect {
        val cx = bitmap.width / 2
        val cy = (bitmap.height * 0.35f).roundToInt() // Slightly above center
        val halfW = (bitmap.width * 0.40f).roundToInt()
        val halfH = (bitmap.height * 0.25f).roundToInt()
        return Rect(
            (cx - halfW).coerceAtLeast(0),
            (cy - halfH).coerceAtLeast(0),
            (cx + halfW).coerceAtMost(bitmap.width),
            (cy + halfH).coerceAtMost(bitmap.height)
        )
    }

    /**
     * Get the border region of the sprite area for aura/shadow detection.
     */
    fun getSpriteBorderRegion(bitmap: Bitmap): Rect {
        val sprite = getSpriteRegion(bitmap)
        val expand = (bitmap.width * 0.05f).roundToInt()
        return Rect(
            (sprite.left - expand).coerceAtLeast(0),
            (sprite.top - expand).coerceAtLeast(0),
            (sprite.right + expand).coerceAtMost(bitmap.width),
            (sprite.bottom + expand).coerceAtMost(bitmap.height)
        )
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/data/model/VisualFeatures.kt

``kotlin
package com.pokerarity.scanner.data.model

/**
 * Visual features detected from color analysis of a Pokemon screenshot.
 */
data class VisualFeatures(
    val isShiny: Boolean = false,
    val isShadow: Boolean = false,
    val isLucky: Boolean = false,
    val hasCostume: Boolean = false,
    val confidence: Float = 0f
)

``


## // File: app/src/main/assets/data/shiny_references.json

``json
{
  "Bulbasaur":   { "normalHue": 150, "shinyHue": 80 },
  "Ivysaur":     { "normalHue": 150, "shinyHue": 80 },
  "Venusaur":    { "normalHue": 150, "shinyHue": 80 },
  "Charmander":  { "normalHue": 25,  "shinyHue": 50 },
  "Charmeleon":  { "normalHue": 15,  "shinyHue": 50 },
  "Charizard":   { "normalHue": 25,  "shinyHue": 5 },
  "Squirtle":    { "normalHue": 210, "shinyHue": 240 },
  "Wartortle":   { "normalHue": 210, "shinyHue": 250 },
  "Blastoise":   { "normalHue": 210, "shinyHue": 250 },
  "Pikachu":     { "normalHue": 50,  "shinyHue": 35 },
  "Raichu":      { "normalHue": 35,  "shinyHue": 25 },
  "Jigglypuff":  { "normalHue": 330, "shinyHue": 30 },
  "Gengar":      { "normalHue": 270, "shinyHue": 210 },
  "Gyarados":    { "normalHue": 210, "shinyHue": 0 },
  "Magikarp":    { "normalHue": 25,  "shinyHue": 45 },
  "Dragonite":   { "normalHue": 30,  "shinyHue": 120 },
  "Mewtwo":      { "normalHue": 280, "shinyHue": 120 },
  "Eevee":       { "normalHue": 30,  "shinyHue": 200 },
  "Vaporeon":    { "normalHue": 200, "shinyHue": 280 },
  "Jolteon":     { "normalHue": 50,  "shinyHue": 120 },
  "Flareon":     { "normalHue": 25,  "shinyHue": 200 },
  "Snorlax":     { "normalHue": 180, "shinyHue": 220 },
  "Articuno":    { "normalHue": 200, "shinyHue": 190 },
  "Zapdos":      { "normalHue": 50,  "shinyHue": 30 },
  "Moltres":     { "normalHue": 40,  "shinyHue": 330 },
  "Dratini":     { "normalHue": 210, "shinyHue": 330 },
  "Dragonair":   { "normalHue": 210, "shinyHue": 330 },
  "Tyranitar":   { "normalHue": 90,  "shinyHue": 35 },
  "Lugia":       { "normalHue": 220, "shinyHue": 330 },
  "Ho-Oh":       { "normalHue": 25,  "shinyHue": 45 },
  "Rayquaza":    { "normalHue": 120, "shinyHue": 0 },
  "Metagross":   { "normalHue": 210, "shinyHue": 200 },
  "Salamence":   { "normalHue": 210, "shinyHue": 120 },
  "Gardevoir":   { "normalHue": 120, "shinyHue": 210 },
  "Blaziken":    { "normalHue": 10,  "shinyHue": 345 },
  "Swampert":    { "normalHue": 210, "shinyHue": 270 },
  "Sceptile":    { "normalHue": 120, "shinyHue": 150 },
  "Aggron":      { "normalHue": 200, "shinyHue": 120 }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/data/local/db/AppDatabase.kt

``kotlin
package com.pokerarity.scanner.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        PokemonEntity::class,
        EventEntity::class,
        ScanHistoryEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun pokemonDao(): PokemonDao
    abstract fun eventDao(): EventDao
    abstract fun scanHistoryDao(): ScanHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pokerarity_db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/data/local/db/PokemonDao.kt

``kotlin
package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PokemonDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(pokemon: PokemonEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pokemon: List<PokemonEntity>)

    @Query("SELECT * FROM pokemon WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): PokemonEntity?

    @Query("SELECT * FROM pokemon WHERE name LIKE '%' || :query || '%'")
    suspend fun searchByName(query: String): List<PokemonEntity>

    @Query("SELECT * FROM pokemon ORDER BY name ASC")
    fun getAll(): Flow<List<PokemonEntity>>

    @Query("SELECT COUNT(*) FROM pokemon")
    suspend fun count(): Int
}

``


## // File: app/src/main/java/com/pokerarity/scanner/data/local/db/EventDao.kt

``kotlin
package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import java.util.Date

@Dao
interface EventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: EventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(events: List<EventEntity>)

    @Query("SELECT * FROM events WHERE startDate <= :date AND endDate >= :date")
    suspend fun getEventsForDate(date: Date): List<EventEntity>

    @Query("SELECT * FROM events WHERE pokemonId = :pokemonId AND startDate <= :date AND endDate >= :date")
    suspend fun getEventsForPokemonOnDate(pokemonId: Long, date: Date): List<EventEntity>

    @Query("SELECT * FROM events ORDER BY startDate DESC")
    suspend fun getAll(): List<EventEntity>
}

``


## // File: app/src/main/java/com/pokerarity/scanner/data/local/db/ScanHistoryDao.kt

``kotlin
package com.pokerarity.scanner.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(scan: ScanHistoryEntity): Long

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC")
    fun getAll(): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<ScanHistoryEntity>>

    @Query("SELECT * FROM scan_history WHERE id = :id")
    suspend fun getById(id: Long): ScanHistoryEntity?

    @Query("SELECT * FROM scan_history WHERE pokemonName = :name ORDER BY timestamp DESC")
    suspend fun getByPokemonName(name: String): List<ScanHistoryEntity>

    @Query("DELETE FROM scan_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM scan_history")
    suspend fun count(): Int

    @Query("SELECT * FROM scan_history WHERE rarityScore >= :minScore ORDER BY rarityScore DESC")
    fun getByMinRarity(minScore: Int): Flow<List<ScanHistoryEntity>>
}

``


## // File: app/src/main/java/com/pokerarity/scanner/data/local/db/PokemonEntity.kt

``kotlin
package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a Pokemon species and its base rarity.
 */
@Entity(tableName = "pokemon")
data class PokemonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val baseRarity: Int // 0-25 scale
)

``


## // File: app/src/main/java/com/pokerarity/scanner/data/local/db/EventEntity.kt

``kotlin
package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Room entity representing a Pokemon GO event window.
 */
@Entity(
    tableName = "events",
    foreignKeys = [
        ForeignKey(
            entity = PokemonEntity::class,
            parentColumns = ["id"],
            childColumns = ["pokemonId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("pokemonId")]
)
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startDate: Date,
    val endDate: Date,
    val pokemonId: Long,
    val rarityWeight: Int, // 0-20 scale
    val isOneDayEvent: Boolean = false
)

``


## // File: app/src/main/java/com/pokerarity/scanner/data/local/db/ScanHistoryEntity.kt

``kotlin
package com.pokerarity.scanner.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

/**
 * Room entity storing a single scan result with all extracted data.
 */
@Entity(tableName = "scan_history")
data class ScanHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Date = Date(),

    // OCR results
    val pokemonName: String?,
    val cp: Int?,
    val hp: Int?,
    val caughtDate: Date?,
    val rawOcrText: String,

    // Visual features
    val isShiny: Boolean = false,
    val isShadow: Boolean = false,
    val isLucky: Boolean = false,
    val hasCostume: Boolean = false,

    // Rarity
    val rarityScore: Int = 0,
    val rarityTier: String = "COMMON"
)

``


## // File: app/src/main/java/com/pokerarity/scanner/data/model/RarityScore.kt

``kotlin
package com.pokerarity.scanner.data.model

/**
 * Rarity tier classification for a Pokemon.
 */
enum class RarityTier(val label: String, val minScore: Int) {
    COMMON("Common", 0),
    UNCOMMON("Uncommon", 20),
    RARE("Rare", 40),
    EPIC("Epic", 60),
    LEGENDARY("Legendary", 80);

    companion object {
        fun fromScore(score: Int): RarityTier {
            return entries.reversed().first { score >= it.minScore }
        }
    }
}

/**
 * Complete rarity assessment for a scanned Pokemon.
 *
 * @param totalScore Overall rarity score (0-100)
 * @param tier Human-readable category derived from totalScore
 * @param breakdown Points awarded per category
 * @param explanation Human-readable reasons for the score
 */
data class RarityScore(
    val totalScore: Int,
    val tier: RarityTier,
    val breakdown: Map<String, Int>,
    val explanation: List<String>
)

``


## // File: app/src/main/java/com/pokerarity/scanner/data/repository/RarityCalculator.kt

``kotlin
package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.data.model.VisualFeatures
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Calculates a 0-100 rarity score for a scanned Pokemon based on 5 weighted categories.
 *
 * | Category          | Max Points | Criteria                            |
 * |-------------------|-----------|--------------------------------------|
 * | Base Rarity       | 25        | Species rarity in the wild           |
 * | Time Rarity       | 30        | Days since caught (older = rarer)    |
 * | Event Rarity      | 20        | Was it caught during a limited event |
 * | Attributes        | 15        | Shiny, Lucky, Shadow, Costume        |
 * | Combination Bonus | 10        | Multiple rare attributes together    |
 */
object RarityCalculator {

    /**
     * Calculate the full rarity score for a Pokemon.
     *
     * @param pokemon  OCR-extracted data (name, CP, HP, caught date)
     * @param features Visual features detected via color analysis
     * @param baseRarity Base species rarity (0-25), from DB or default
     * @param eventWeight Event rarity weight (0-20), from DB or 0
     */
    fun calculate(
        pokemon: PokemonData,
        features: VisualFeatures,
        baseRarity: Int = 5,
        eventWeight: Int = 0
    ): RarityScore {
        val breakdown = mutableMapOf<String, Int>()
        val explanation = mutableListOf<String>()

        // ──────────────────────────────────
        // 1. Base Rarity (0-25)
        // ──────────────────────────────────
        val baseScore = baseRarity.coerceIn(0, 25)
        breakdown["Base"] = baseScore
        if (baseScore >= 20) {
            explanation.add("Extremely rare species (${pokemon.name ?: "Unknown"})")
        } else if (baseScore >= 10) {
            explanation.add("Uncommon species (${pokemon.name ?: "Unknown"})")
        }

        // ──────────────────────────────────
        // 2. Time Rarity (0-30)
        // ──────────────────────────────────
        val timeScore = calculateTimeRarity(pokemon.caughtDate)
        breakdown["Time"] = timeScore
        if (timeScore >= 20) {
            explanation.add("Caught over 2 years ago — vintage!")
        } else if (timeScore >= 10) {
            explanation.add("Caught over 1 year ago")
        }

        // ──────────────────────────────────
        // 3. Event Rarity (0-20)
        // ──────────────────────────────────
        val eventScore = eventWeight.coerceIn(0, 20)
        breakdown["Event"] = eventScore
        if (eventScore >= 15) {
            explanation.add("Caught during a rare limited event")
        } else if (eventScore >= 5) {
            explanation.add("Caught during an event")
        }

        // ──────────────────────────────────
        // 4. Attributes (0-15)
        // ──────────────────────────────────
        val attrScore = calculateAttributeScore(features)
        breakdown["Attributes"] = attrScore.first
        explanation.addAll(attrScore.second)

        // ──────────────────────────────────
        // 5. Combination Bonus (0-10)
        // ──────────────────────────────────
        val comboScore = calculateComboBonus(features)
        breakdown["Bonus"] = comboScore.first
        if (comboScore.second.isNotEmpty()) {
            explanation.addAll(comboScore.second)
        }

        // ──────────────────────────────────
        // Total
        // ──────────────────────────────────
        val totalScore = breakdown.values.sum().coerceIn(0, 100)
        val tier = RarityTier.fromScore(totalScore)

        return RarityScore(
            totalScore = totalScore,
            tier = tier,
            breakdown = breakdown,
            explanation = explanation
        )
    }

    /**
     * Time rarity: more points for older Pokemon.
     * Scale: 0 days = 0, 365 days = 15, 730+ days = 30.
     */
    private fun calculateTimeRarity(caughtDate: Date?): Int {
        if (caughtDate == null) return 0

        val daysSinceCaught = TimeUnit.MILLISECONDS.toDays(
            Date().time - caughtDate.time
        )

        return when {
            daysSinceCaught >= 1095 -> 30  // 3+ years
            daysSinceCaught >= 730 -> 25   // 2+ years
            daysSinceCaught >= 365 -> 15   // 1+ year
            daysSinceCaught >= 180 -> 10   // 6+ months
            daysSinceCaught >= 90 -> 5     // 3+ months
            else -> 0
        }
    }

    /**
     * Attribute score: points for each special visual feature detected.
     */
    private fun calculateAttributeScore(features: VisualFeatures): Pair<Int, List<String>> {
        var score = 0
        val reasons = mutableListOf<String>()

        if (features.isShiny) {
            score += 6
            reasons.add("✨ Shiny variant detected")
        }
        if (features.isShadow) {
            score += 4
            reasons.add("👤 Shadow Pokemon detected")
        }
        if (features.isLucky) {
            score += 3
            reasons.add("🍀 Lucky Pokemon detected")
        }
        if (features.hasCostume) {
            score += 2
            reasons.add("🎩 Costume/special form detected")
        }

        return Pair(min(score, 15), reasons)
    }

    /**
     * Combination bonus: extra points when multiple rare attributes appear together.
     * These combinations are extremely rare in Pokemon GO.
     */
    private fun calculateComboBonus(features: VisualFeatures): Pair<Int, List<String>> {
        var bonus = 0
        val reasons = mutableListOf<String>()

        val attrCount = listOf(
            features.isShiny,
            features.isShadow,
            features.isLucky,
            features.hasCostume
        ).count { it }

        when {
            attrCount >= 4 -> {
                bonus = 10
                reasons.add("🏆 ULTRA RARE: All 4 special attributes!")
            }
            attrCount == 3 -> {
                bonus = 8
                reasons.add("🔥 Triple combo: 3 special attributes!")
            }
            attrCount == 2 -> {
                bonus = 4
                reasons.add("⚡ Double combo: 2 special attributes")
            }
        }

        // Special known combos
        if (features.isShiny && features.isShadow) {
            bonus = min(bonus + 2, 10)
            reasons.add("💎 Shiny Shadow — one of the rarest combos!")
        }
        if (features.isShiny && features.isLucky) {
            bonus = min(bonus + 1, 10)
            reasons.add("🌟 Shiny Lucky — extremely rare trade result")
        }

        return Pair(min(bonus, 10), reasons)
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/data/repository/PokemonRepository.kt

``kotlin
package com.pokerarity.scanner.data.repository

import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.local.db.PokemonEntity
import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.model.RarityScore
import com.pokerarity.scanner.data.model.VisualFeatures
import kotlinx.coroutines.flow.Flow
import java.util.Date

/**
 * Central repository for Pokemon data, events, and scan history.
 * Serves as the single source of truth between the database and the UI/service layers.
 */
class PokemonRepository(private val database: AppDatabase) {

    private val pokemonDao = database.pokemonDao()
    private val eventDao = database.eventDao()
    private val scanHistoryDao = database.scanHistoryDao()

    // ──────────────────────────────────
    // Pokemon Lookup
    // ──────────────────────────────────

    suspend fun getPokemonByName(name: String): PokemonEntity? {
        return pokemonDao.getByName(name)
    }

    suspend fun searchPokemon(query: String): List<PokemonEntity> {
        return pokemonDao.searchByName(query)
    }

    fun getAllPokemon(): Flow<List<PokemonEntity>> {
        return pokemonDao.getAll()
    }

    suspend fun insertPokemon(pokemon: PokemonEntity): Long {
        return pokemonDao.insert(pokemon)
    }

    suspend fun insertAllPokemon(pokemon: List<PokemonEntity>) {
        pokemonDao.insertAll(pokemon)
    }

    // ──────────────────────────────────
    // Event Queries
    // ──────────────────────────────────

    /**
     * Get the event rarity weight for a Pokemon caught on a specific date.
     * Returns 0 if no event was active.
     */
    suspend fun getEventWeight(pokemonName: String, caughtDate: Date?): Int {
        if (caughtDate == null) return 0

        val pokemon = pokemonDao.getByName(pokemonName) ?: return 0
        val events = eventDao.getEventsForPokemonOnDate(pokemon.id, caughtDate)

        return events.maxOfOrNull { it.rarityWeight } ?: 0
    }

    // ──────────────────────────────────
    // Scan History
    // ──────────────────────────────────

    /**
     * Save a complete scan result to the database.
     */
    suspend fun saveScan(
        pokemonData: PokemonData,
        features: VisualFeatures,
        rarityScore: RarityScore
    ): Long {
        val entity = ScanHistoryEntity(
            pokemonName = pokemonData.name,
            cp = pokemonData.cp,
            hp = pokemonData.hp,
            caughtDate = pokemonData.caughtDate,
            rawOcrText = pokemonData.rawOcrText,
            isShiny = features.isShiny,
            isShadow = features.isShadow,
            isLucky = features.isLucky,
            hasCostume = features.hasCostume,
            rarityScore = rarityScore.totalScore,
            rarityTier = rarityScore.tier.name
        )
        return scanHistoryDao.insert(entity)
    }

    fun getAllScans(): Flow<List<ScanHistoryEntity>> {
        return scanHistoryDao.getAll()
    }

    fun getRecentScans(limit: Int = 20): Flow<List<ScanHistoryEntity>> {
        return scanHistoryDao.getRecent(limit)
    }

    suspend fun getScanById(id: Long): ScanHistoryEntity? {
        return scanHistoryDao.getById(id)
    }

    suspend fun deleteScan(id: Long) {
        scanHistoryDao.deleteById(id)
    }

    fun getRareScans(minScore: Int = 60): Flow<List<ScanHistoryEntity>> {
        return scanHistoryDao.getByMinRarity(minScore)
    }

    suspend fun getScanCount(): Int {
        return scanHistoryDao.count()
    }
}

``


## // File: app/src/main/assets/data/event_history.json

``json
[
  {
    "eventName": "Community Day: Charmander",
    "startDate": "2018-05-19",
    "endDate": "2018-05-19",
    "pokemonIds": ["Charmander", "Charmeleon", "Charizard"],
    "rarityWeight": 15,
    "isOneDayEvent": true
  },
  {
    "eventName": "Community Day: Larvitar",
    "startDate": "2018-06-16",
    "endDate": "2018-06-16",
    "pokemonIds": ["Larvitar", "Pupitar", "Tyranitar"],
    "rarityWeight": 18,
    "isOneDayEvent": true
  },
  {
    "eventName": "Community Day: Beldum",
    "startDate": "2018-10-21",
    "endDate": "2018-10-21",
    "pokemonIds": ["Beldum", "Metang", "Metagross"],
    "rarityWeight": 18,
    "isOneDayEvent": true
  },
  {
    "eventName": "Community Day: Ralts",
    "startDate": "2019-08-03",
    "endDate": "2019-08-03",
    "pokemonIds": ["Ralts", "Kirlia", "Gardevoir"],
    "rarityWeight": 14,
    "isOneDayEvent": true
  },
  {
    "eventName": "Pokemon GO Fest 2019",
    "startDate": "2019-06-13",
    "endDate": "2019-06-16",
    "pokemonIds": ["Jirachi", "Pachirisu", "Chatot", "Carnivine"],
    "rarityWeight": 20,
    "isOneDayEvent": false
  },
  {
    "eventName": "Pokemon GO Fest 2020",
    "startDate": "2020-07-25",
    "endDate": "2020-07-26",
    "pokemonIds": ["Victini", "Rotom", "Gible"],
    "rarityWeight": 20,
    "isOneDayEvent": false
  },
  {
    "eventName": "Community Day: Gible",
    "startDate": "2021-06-06",
    "endDate": "2021-06-06",
    "pokemonIds": ["Gible", "Gabite", "Garchomp"],
    "rarityWeight": 16,
    "isOneDayEvent": true
  },
  {
    "eventName": "Halloween Event 2021",
    "startDate": "2021-10-15",
    "endDate": "2021-10-31",
    "pokemonIds": ["Gengar", "Sableye", "Duskull", "Dusclops", "Banette", "Shuppet"],
    "rarityWeight": 8,
    "isOneDayEvent": false
  },
  {
    "eventName": "Community Day: Deino",
    "startDate": "2022-06-25",
    "endDate": "2022-06-25",
    "pokemonIds": ["Deino", "Zweilous", "Hydreigon"],
    "rarityWeight": 18,
    "isOneDayEvent": true
  },
  {
    "eventName": "Pokemon GO Fest 2023",
    "startDate": "2023-08-04",
    "endDate": "2023-08-06",
    "pokemonIds": ["Diancie", "Mega Rayquaza"],
    "rarityWeight": 20,
    "isOneDayEvent": false
  },
  {
    "eventName": "Community Day Classic: Dratini",
    "startDate": "2023-11-11",
    "endDate": "2023-11-11",
    "pokemonIds": ["Dratini", "Dragonair", "Dragonite"],
    "rarityWeight": 14,
    "isOneDayEvent": true
  },
  {
    "eventName": "Adventures Abound",
    "startDate": "2023-09-01",
    "endDate": "2023-12-01",
    "pokemonIds": ["Poliwag", "Bellsprout", "Tentacool", "Seel"],
    "rarityWeight": 4,
    "isOneDayEvent": false
  }
]

``


## // File: app/src/main/res/layout/activity_result.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_dark"
    tools:context=".ui.result.ResultActivity">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:padding="16dp">

        <!-- Back button -->
        <ImageButton
            android:id="@+id/btnBack"
            android:layout_width="48dp"
            android:layout_height="48dp"
            android:background="?attr/selectableItemBackgroundBorderless"
            android:src="@android:drawable/ic_menu_close_clear_cancel"
            android:contentDescription="@string/back"
            android:tint="@color/white" />

        <!-- Score Circle -->
        <FrameLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:gravity="center"
            android:layout_marginTop="16dp">

            <LinearLayout
                android:id="@+id/layoutScoreCircle"
                android:layout_width="180dp"
                android:layout_height="180dp"
                android:layout_gravity="center"
                android:background="@drawable/bg_score_circle"
                android:gravity="center"
                android:orientation="vertical">

                <TextView
                    android:id="@+id/tvScore"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="0"
                    android:textColor="@color/white"
                    android:textSize="56sp"
                    android:textStyle="bold"
                    android:fontFamily="sans-serif-black" />

                <TextView
                    android:id="@+id/tvTier"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="@string/common"
                    android:textColor="@color/tier_common"
                    android:textSize="14sp"
                    android:textStyle="bold"
                    android:textAllCaps="true" />

            </LinearLayout>
        </FrameLayout>

        <!-- Pokemon Name -->
        <TextView
            android:id="@+id/tvPokemonName"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:gravity="center"
            android:text="Unknown Pokemon"
            android:textColor="@color/text_primary"
            android:textSize="28sp"
            android:textStyle="bold" />

        <!-- CP / HP Row -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="8dp"
            android:gravity="center"
            android:orientation="horizontal">

            <TextView
                android:id="@+id/tvCP"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="CP —"
                android:textColor="@color/text_secondary"
                android:textSize="16sp" />

            <View
                android:layout_width="1dp"
                android:layout_height="16dp"
                android:layout_marginStart="16dp"
                android:layout_marginEnd="16dp"
                android:background="@color/text_hint" />

            <TextView
                android:id="@+id/tvHP"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="HP —"
                android:textColor="@color/text_secondary"
                android:textSize="16sp" />
        </LinearLayout>

        <!-- Attributes Row -->
        <LinearLayout
            android:id="@+id/layoutAttributes"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="20dp"
            android:gravity="center"
            android:orientation="horizontal">

            <LinearLayout
                android:id="@+id/layoutShiny"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp"
                android:visibility="gone">

                <FrameLayout
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:background="@drawable/bg_attribute_circle">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:gravity="center"
                        android:text="✨"
                        android:textSize="20sp" />
                </FrameLayout>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/shiny"
                    android:textColor="@color/shiny_star"
                    android:textSize="11sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/layoutShadow"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp"
                android:visibility="gone">

                <FrameLayout
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:background="@drawable/bg_attribute_circle">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:gravity="center"
                        android:text="👤"
                        android:textSize="20sp" />
                </FrameLayout>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/shadow"
                    android:textColor="@color/shadow_purple"
                    android:textSize="11sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/layoutLucky"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp"
                android:visibility="gone">

                <FrameLayout
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:background="@drawable/bg_attribute_circle">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:gravity="center"
                        android:text="🍀"
                        android:textSize="20sp" />
                </FrameLayout>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/lucky"
                    android:textColor="@color/lucky_green"
                    android:textSize="11sp" />
            </LinearLayout>

            <LinearLayout
                android:id="@+id/layoutCostume"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:gravity="center"
                android:orientation="vertical"
                android:padding="8dp"
                android:visibility="gone">

                <FrameLayout
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:background="@drawable/bg_attribute_circle">

                    <TextView
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:gravity="center"
                        android:text="🎩"
                        android:textSize="20sp" />
                </FrameLayout>

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginTop="4dp"
                    android:text="@string/costume"
                    android:textColor="@color/costume_pink"
                    android:textSize="11sp" />
            </LinearLayout>
        </LinearLayout>

        <!-- Why Is This Rare? -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:background="@drawable/bg_card"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView
                android:id="@+id/tvWhyRareHeader"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/why_rare"
                android:textColor="@color/accent_gold"
                android:textSize="16sp"
                android:textStyle="bold"
                android:drawablePadding="8dp" />

            <LinearLayout
                android:id="@+id/layoutExplanations"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="8dp"
                android:orientation="vertical" />
        </LinearLayout>

        <!-- Breakdown Chart -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:background="@drawable/bg_card"
            android:orientation="vertical"
            android:padding="16dp">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="@string/score_breakdown"
                android:textColor="@color/text_primary"
                android:textSize="16sp"
                android:textStyle="bold" />

            <LinearLayout
                android:id="@+id/layoutBreakdown"
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:layout_marginTop="12dp"
                android:orientation="vertical" />
        </LinearLayout>

        <!-- Action Buttons -->
        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="24dp"
            android:layout_marginBottom="16dp"
            android:orientation="horizontal"
            android:gravity="center">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnSave"
                android:layout_width="0dp"
                android:layout_height="56dp"
                android:layout_weight="1"
                android:layout_marginEnd="8dp"
                android:text="@string/save"
                android:textColor="@color/white"
                android:backgroundTint="@color/accent_teal"
                app:cornerRadius="28dp" />

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnShare"
                style="@style/Widget.MaterialComponents.Button.OutlinedButton"
                android:layout_width="0dp"
                android:layout_height="56dp"
                android:layout_weight="1"
                android:layout_marginStart="8dp"
                android:text="@string/share"
                android:textColor="@color/text_primary"
                app:cornerRadius="28dp"
                app:strokeColor="@color/text_hint"
                app:strokeWidth="1dp" />
        </LinearLayout>

    </LinearLayout>
</ScrollView>

``


## // File: app/src/main/res/layout/activity_history.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background_dark"
    android:orientation="vertical"
    tools:context=".ui.result.HistoryActivity">

    <!-- Toolbar -->
    <com.google.android.material.appbar.MaterialToolbar
        android:id="@+id/toolbar"
        android:layout_width="match_parent"
        android:layout_height="?attr/actionBarSize"
        android:background="@drawable/bg_appbar_gradient"
        app:title="@string/scan_history"
        app:titleTextColor="@color/white"
        app:navigationIcon="@android:drawable/ic_menu_close_clear_cancel"
        app:navigationIconTint="@color/white" />

    <!-- Filter Chips -->
    <HorizontalScrollView
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:scrollbars="none"
        android:paddingStart="16dp"
        android:paddingEnd="16dp"
        android:paddingTop="8dp"
        android:paddingBottom="8dp">

        <com.google.android.material.chip.ChipGroup
            android:id="@+id/chipGroup"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            app:singleSelection="true">

            <com.google.android.material.chip.Chip
                android:id="@+id/chipAll"
                style="@style/Widget.MaterialComponents.Chip.Choice"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/all"
                android:checked="true" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipRare"
                style="@style/Widget.MaterialComponents.Chip.Choice"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/rare_plus" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipShiny"
                style="@style/Widget.MaterialComponents.Chip.Choice"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/shiny" />

            <com.google.android.material.chip.Chip
                android:id="@+id/chipLegendary"
                style="@style/Widget.MaterialComponents.Chip.Choice"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="@string/legendary" />
        </com.google.android.material.chip.ChipGroup>
    </HorizontalScrollView>

    <!-- History List -->
    <androidx.recyclerview.widget.RecyclerView
        android:id="@+id/rvHistory"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:padding="8dp"
        android:clipToPadding="false"
        tools:listitem="@layout/item_scan_history" />

    <!-- Empty State -->
    <LinearLayout
        android:id="@+id/layoutEmpty"
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:gravity="center"
        android:orientation="vertical"
        android:visibility="gone">

        <TextView
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="@string/no_history"
            android:textColor="@color/text_hint"
            android:textSize="16sp" />
    </LinearLayout>

</LinearLayout>

``


## // File: app/src/main/res/layout/item_scan_history.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginStart="8dp"
    android:layout_marginEnd="8dp"
    android:layout_marginTop="4dp"
    android:layout_marginBottom="4dp"
    app:cardBackgroundColor="@color/background_card"
    app:cardCornerRadius="12dp"
    app:cardElevation="2dp"
    app:strokeWidth="0dp">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="horizontal"
        android:gravity="center_vertical"
        android:padding="12dp">

        <!-- Score Badge -->
        <FrameLayout
            android:layout_width="52dp"
            android:layout_height="52dp">

            <View
                android:id="@+id/viewScoreBg"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:background="@drawable/bg_score_circle" />

            <TextView
                android:id="@+id/tvItemScore"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:gravity="center"
                android:text="0"
                android:textColor="@color/white"
                android:textSize="18sp"
                android:textStyle="bold" />
        </FrameLayout>

        <!-- Pokemon Info -->
        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginStart="12dp"
            android:orientation="vertical">

            <TextView
                android:id="@+id/tvItemName"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:text="Pokemon"
                android:textColor="@color/text_primary"
                android:textSize="16sp"
                android:textStyle="bold"
                android:maxLines="1"
                android:ellipsize="end" />

            <LinearLayout
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:orientation="horizontal">

                <TextView
                    android:id="@+id/tvItemTier"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Common"
                    android:textColor="@color/tier_common"
                    android:textSize="12sp"
                    android:textAllCaps="true" />

                <TextView
                    android:id="@+id/tvItemAttributes"
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:layout_marginStart="8dp"
                    android:text=""
                    android:textSize="12sp" />
            </LinearLayout>

            <TextView
                android:id="@+id/tvItemDate"
                android:layout_width="wrap_content"
                android:layout_height="wrap_content"
                android:layout_marginTop="2dp"
                android:text="Just now"
                android:textColor="@color/text_hint"
                android:textSize="11sp" />
        </LinearLayout>

        <!-- CP -->
        <TextView
            android:id="@+id/tvItemCP"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:text="CP —"
            android:textColor="@color/text_secondary"
            android:textSize="13sp" />

    </LinearLayout>

</com.google.android.material.card.MaterialCardView>

``


## // File: app/src/main/res/layout/activity_splash.xml

*(File not created yet or not found)*


## // File: app/src/main/res/values/colors.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Pokemon Go Inspired Color Palette -->

    <!-- Primary Brand Colors -->
    <color name="pokemon_red">#FF0000</color>
    <color name="pokemon_yellow">#FFCB05</color>
    <color name="pokemon_blue">#3B4CCA</color>
    <color name="pokeball_red">#E3350D</color>
    <color name="pokeball_red_dark">#B52A0A</color>
    <color name="pokeball_red_light">#FF5733</color>

    <!-- Background Colors -->
    <color name="background_dark">#1A1A2E</color>
    <color name="background_card">#16213E</color>
    <color name="background_surface">#0F3460</color>
    <color name="background_gradient_start">#1A1A2E</color>
    <color name="background_gradient_end">#0F3460</color>

    <!-- Rarity Tier Colors -->
    <color name="tier_common">#9E9E9E</color>
    <color name="tier_uncommon">#4CAF50</color>
    <color name="tier_rare">#2196F3</color>
    <color name="tier_epic">#9C27B0</color>
    <color name="tier_legendary">#FFD700</color>

    <!-- Rarity Colors (aliases for backward compat) -->
    <color name="rarity_common">#A8A8A8</color>
    <color name="rarity_uncommon">#4CAF50</color>
    <color name="rarity_rare">#2196F3</color>
    <color name="rarity_ultra_rare">#9C27B0</color>
    <color name="rarity_legendary">#FFD700</color>
    <color name="rarity_mythic">#FF6F00</color>

    <!-- Accent Colors -->
    <color name="accent_gold">#FFD700</color>
    <color name="accent_teal">#00BFA6</color>
    <color name="accent_blue">#2196F3</color>

    <!-- Attribute Icons -->
    <color name="shiny_star">#FFD700</color>
    <color name="shadow_purple">#7B1FA2</color>
    <color name="lucky_green">#66BB6A</color>
    <color name="costume_pink">#E91E63</color>

    <!-- Text Colors -->
    <color name="text_primary">#FFFFFF</color>
    <color name="text_secondary">#B0BEC5</color>
    <color name="text_hint">#78909C</color>

    <!-- Standard -->
    <color name="white">#FFFFFF</color>
    <color name="black">#000000</color>
    <color name="transparent">#00000000</color>
    <color name="semi_transparent_black">#80000000</color>
</resources>

``


## // File: app/src/main/res/values/themes.xml

``xml
<?xml version="1.0" encoding="utf-8"?>
<resources>

    <style name="Theme.PokeRarityScanner" parent="Theme.Material3.Dark.NoActionBar">
        <!-- Primary brand color -->
        <item name="colorPrimary">@color/pokeball_red</item>
        <item name="colorPrimaryVariant">@color/pokeball_red_dark</item>
        <item name="colorOnPrimary">@color/white</item>

        <!-- Secondary brand color -->
        <item name="colorSecondary">@color/accent_teal</item>
        <item name="colorSecondaryVariant">@color/accent_blue</item>
        <item name="colorOnSecondary">@color/white</item>

        <!-- Background and surface -->
        <item name="android:colorBackground">@color/background_dark</item>
        <item name="colorSurface">@color/background_card</item>
        <item name="colorOnSurface">@color/text_primary</item>

        <!-- Status bar -->
        <item name="android:statusBarColor">@color/background_dark</item>
        <item name="android:navigationBarColor">@color/background_dark</item>
    </style>

</resources>

``


## // File: app/src/main/java/com/pokerarity/scanner/ui/result/ResultActivity.kt

``kotlin
package com.pokerarity.scanner.ui.result

import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.model.RarityTier
import com.pokerarity.scanner.databinding.ActivityResultBinding
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_POKEMON_NAME = "extra_pokemon_name"
        const val EXTRA_CP = "extra_cp"
        const val EXTRA_HP = "extra_hp"
        const val EXTRA_SCORE = "extra_score"
        const val EXTRA_TIER = "extra_tier"
        const val EXTRA_IS_SHINY = "extra_is_shiny"
        const val EXTRA_IS_SHADOW = "extra_is_shadow"
        const val EXTRA_IS_LUCKY = "extra_is_lucky"
        const val EXTRA_HAS_COSTUME = "extra_has_costume"
        const val EXTRA_EXPLANATIONS = "extra_explanations"
        const val EXTRA_BREAKDOWN_KEYS = "extra_breakdown_keys"
        const val EXTRA_BREAKDOWN_VALUES = "extra_breakdown_values"
    }

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        loadData()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener {
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show()
            // TODO: Save to database
        }
        binding.btnShare.setOnClickListener {
            // TODO: Share result
            Toast.makeText(this, R.string.share_coming_soon, Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadData() {
        val name = intent.getStringExtra(EXTRA_POKEMON_NAME) ?: "Unknown"
        val cp = intent.getIntExtra(EXTRA_CP, 0)
        val hp = intent.getIntExtra(EXTRA_HP, 0)
        val score = intent.getIntExtra(EXTRA_SCORE, 0)
        val tierName = intent.getStringExtra(EXTRA_TIER) ?: "COMMON"
        val isShiny = intent.getBooleanExtra(EXTRA_IS_SHINY, false)
        val isShadow = intent.getBooleanExtra(EXTRA_IS_SHADOW, false)
        val isLucky = intent.getBooleanExtra(EXTRA_IS_LUCKY, false)
        val hasCostume = intent.getBooleanExtra(EXTRA_HAS_COSTUME, false)
        val explanations = intent.getStringArrayListExtra(EXTRA_EXPLANATIONS) ?: arrayListOf()
        val breakdownKeys = intent.getStringArrayListExtra(EXTRA_BREAKDOWN_KEYS) ?: arrayListOf()
        val breakdownValues = intent.getIntegerArrayListExtra(EXTRA_BREAKDOWN_VALUES) ?: arrayListOf()

        val tier = try { RarityTier.valueOf(tierName) } catch (e: Exception) { RarityTier.COMMON }
        val tierColor = getTierColor(tier)

        // Name & Stats
        binding.tvPokemonName.text = name
        binding.tvCP.text = if (cp > 0) "CP $cp" else "CP —"
        binding.tvHP.text = if (hp > 0) "HP $hp" else "HP —"

        // Tier label
        binding.tvTier.text = tier.label
        binding.tvTier.setTextColor(tierColor)

        // Score circle border color
        val circleDrawable = binding.layoutScoreCircle.background as? GradientDrawable
        circleDrawable?.setStroke(
            (6 * resources.displayMetrics.density).toInt(),
            tierColor
        )

        // Animate score counter
        animateScore(score, tierColor)

        // Attributes
        binding.layoutShiny.visibility = if (isShiny) View.VISIBLE else View.GONE
        binding.layoutShadow.visibility = if (isShadow) View.VISIBLE else View.GONE
        binding.layoutLucky.visibility = if (isLucky) View.VISIBLE else View.GONE
        binding.layoutCostume.visibility = if (hasCostume) View.VISIBLE else View.GONE

        // Explanations
        populateExplanations(explanations)

        // Breakdown bars
        populateBreakdown(breakdownKeys, breakdownValues)

        // Pulse animation for legendary
        if (tier == RarityTier.LEGENDARY) {
            val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse)
            binding.layoutScoreCircle.startAnimation(pulse)
        }

        // Entrance animation
        val scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up)
        binding.layoutScoreCircle.startAnimation(scaleUp)
    }

    private fun animateScore(targetScore: Int, color: Int) {
        val animator = ValueAnimator.ofInt(0, targetScore)
        animator.duration = 1200
        animator.interpolator = OvershootInterpolator(1.05f)
        animator.addUpdateListener { anim ->
            binding.tvScore.text = (anim.animatedValue as Int).toString()
        }
        animator.start()

        binding.tvScore.setTextColor(color)
    }

    private fun populateExplanations(explanations: List<String>) {
        binding.layoutExplanations.removeAllViews()
        for (explanation in explanations) {
            val tv = TextView(this).apply {
                text = "• $explanation"
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 13f
                setPadding(0, 4, 0, 4)
            }
            binding.layoutExplanations.addView(tv)
        }
    }

    private fun populateBreakdown(keys: List<String>, values: List<Int>) {
        binding.layoutBreakdown.removeAllViews()

        val maxValues = mapOf(
            "Base" to 25, "Time" to 30, "Event" to 20,
            "Attributes" to 15, "Bonus" to 10
        )

        for (i in keys.indices) {
            val key = keys[i]
            val value = if (i < values.size) values[i] else 0
            val max = maxValues[key] ?: 25

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(0, 8, 0, 8)
            }

            val labelTv = TextView(this).apply {
                text = key
                setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
            }

            val bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                this.max = max
                progress = value
                layoutParams = LinearLayout.LayoutParams(
                    0, (8 * resources.displayMetrics.density).toInt(), 2f
                ).also { it.marginStart = (8 * resources.displayMetrics.density).toInt() }
                progressDrawable = ContextCompat.getDrawable(context, android.R.drawable.progress_horizontal)
            }

            val valueTv = TextView(this).apply {
                text = "$value/$max"
                setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                textSize = 13f
                setPadding((8 * resources.displayMetrics.density).toInt(), 0, 0, 0)
            }

            row.addView(labelTv)
            row.addView(bar)
            row.addView(valueTv)
            binding.layoutBreakdown.addView(row)
        }
    }

    private fun getTierColor(tier: RarityTier): Int {
        return ContextCompat.getColor(this, when (tier) {
            RarityTier.COMMON -> R.color.tier_common
            RarityTier.UNCOMMON -> R.color.tier_uncommon
            RarityTier.RARE -> R.color.tier_rare
            RarityTier.EPIC -> R.color.tier_epic
            RarityTier.LEGENDARY -> R.color.tier_legendary
        })
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/ui/result/HistoryActivity.kt

``kotlin
package com.pokerarity.scanner.ui.result

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.pokerarity.scanner.R
import com.pokerarity.scanner.data.local.db.AppDatabase
import com.pokerarity.scanner.data.repository.PokemonRepository
import com.pokerarity.scanner.databinding.ActivityHistoryBinding
import com.pokerarity.scanner.ui.main.ScanHistoryAdapter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private lateinit var adapter: ScanHistoryAdapter
    private lateinit var repository: PokemonRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = PokemonRepository(AppDatabase.getInstance(this))
        setupToolbar()
        setupRecyclerView()
        setupFilters()
        loadScans()
    }

    private fun setupToolbar() {
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        adapter = ScanHistoryAdapter { scan ->
            val intent = Intent(this, ResultActivity::class.java).apply {
                putExtra(ResultActivity.EXTRA_POKEMON_NAME, scan.pokemonName)
                putExtra(ResultActivity.EXTRA_CP, scan.cp ?: 0)
                putExtra(ResultActivity.EXTRA_HP, scan.hp ?: 0)
                putExtra(ResultActivity.EXTRA_SCORE, scan.rarityScore)
                putExtra(ResultActivity.EXTRA_TIER, scan.rarityTier)
                putExtra(ResultActivity.EXTRA_IS_SHINY, scan.isShiny)
                putExtra(ResultActivity.EXTRA_IS_SHADOW, scan.isShadow)
                putExtra(ResultActivity.EXTRA_IS_LUCKY, scan.isLucky)
                putExtra(ResultActivity.EXTRA_HAS_COSTUME, scan.hasCostume)
            }
            startActivity(intent)
        }
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = adapter
    }

    private fun setupFilters() {
        binding.chipGroup.setOnCheckedStateChangeListener { _, checkedIds ->
            when {
                checkedIds.contains(R.id.chipAll) -> loadScans()
                checkedIds.contains(R.id.chipRare) -> loadRareScans(40)
                checkedIds.contains(R.id.chipShiny) -> loadShinyScans()
                checkedIds.contains(R.id.chipLegendary) -> loadRareScans(80)
                else -> loadScans()
            }
        }
    }

    private fun loadScans() {
        lifecycleScope.launch {
            repository.getAllScans().collectLatest { scans ->
                adapter.submitList(scans)
                binding.layoutEmpty.visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
                binding.rvHistory.visibility = if (scans.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadRareScans(minScore: Int) {
        lifecycleScope.launch {
            repository.getRareScans(minScore).collectLatest { scans ->
                adapter.submitList(scans)
                binding.layoutEmpty.visibility = if (scans.isEmpty()) View.VISIBLE else View.GONE
                binding.rvHistory.visibility = if (scans.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun loadShinyScans() {
        lifecycleScope.launch {
            repository.getAllScans().collectLatest { scans ->
                val shinyScans = scans.filter { it.isShiny }
                adapter.submitList(shinyScans)
                binding.layoutEmpty.visibility = if (shinyScans.isEmpty()) View.VISIBLE else View.GONE
                binding.rvHistory.visibility = if (shinyScans.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }
}

``


## // File: app/src/main/java/com/pokerarity/scanner/ui/splash/SplashActivity.kt

*(File not created yet or not found)*


## // File: app/src/main/java/com/pokerarity/scanner/service/ScanManager.kt

*(File not created yet or not found)*


## // File: app/src/main/java/com/pokerarity/scanner/util/ScanErrorHandler.kt

*(File not created yet or not found)*


## // File: app/proguard-rules.pro

``pro
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renameSourceFileAttribute SourceFile

``


