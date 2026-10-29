package com.pokerarity.scanner.ui.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.pokerarity.scanner.BuildConfig
import com.pokerarity.scanner.R
import com.pokerarity.scanner.databinding.ActivitySplashBinding
import com.pokerarity.scanner.ui.main.MainActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Animated splash screen.
 *
 * Keeps the launch presentation visible briefly, then opens the scanner.
 */
@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Start PokeBall scale-up animation
        val scaleUp = AnimationUtils.loadAnimation(this, R.anim.scale_up)
        binding.ivSplashBall.startAnimation(scaleUp)
        binding.tvSplashVersion.text = getString(R.string.splash_version_format, BuildConfig.VERSION_NAME)

        // Keep a short minimum display time without blocking launch on recognition setup.
        lifecycleScope.launch {
            showSplashAfterMinimumPresentation {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }
}

internal suspend fun showSplashAfterMinimumPresentation(onReady: () -> Unit) {
    delay(MINIMUM_SPLASH_PRESENTATION_MILLIS)
    onReady()
}

private const val MINIMUM_SPLASH_PRESENTATION_MILLIS = 1_500L
