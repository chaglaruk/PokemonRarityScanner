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
import com.pokerarity.scanner.util.ocr.OCRProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Animated splash screen.
 *
 * While the PokeBall animation plays (~1.5 s), the recognition stack warms up
 * so the first scan doesn't have to pay the full initialization cost.
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

        // Background: warm up the recognition stack
        lifecycleScope.launch {
            val initJob = launch(Dispatchers.IO) {
                val warmupProcessor = OCRProcessor(applicationContext)
                try {
                    warmupProcessor.ensureInitialized()
                } catch (e: Exception) {
                    // Non-fatal; OCRProcessor will retry when actually needed
                    android.util.Log.w("SplashActivity", "Recognition warmup failed", e)
                } finally {
                    warmupProcessor.release()
                }
            }

            // Minimum display time
            delay(1500)

            // Wait for init if it's still running
            initJob.join()

            // Navigate
            withContext(Dispatchers.Main) {
                startActivity(Intent(this@SplashActivity, MainActivity::class.java))
                finish()
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            }
        }
    }
}
