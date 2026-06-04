package com.pokerarity.scanner.ui.permission

import android.app.AlertDialog
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pokerarity.scanner.R
import com.pokerarity.scanner.service.ScreenCaptureManager

class ProjectionPermissionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTO_CAPTURE = "extra_auto_capture"
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val autoCapture = intent.getBooleanExtra(EXTRA_AUTO_CAPTURE, false)
        if (ScreenCaptureManager.handleResult(result)) {
            val serviceIntent = ScreenCaptureManager.buildServiceIntent(this, autoCapture = autoCapture)
            if (serviceIntent != null) {
                startForegroundService(serviceIntent)
            }
        } else {
            Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showScreenCaptureRationale()
    }

    private fun showScreenCaptureRationale() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.screen_capture_permission_title))
            .setMessage(getString(R.string.screen_capture_permission_message))
            .setPositiveButton(getString(R.string.continue_to_permission)) { _, _ ->
                launchMediaProjectionPermission()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnCancelListener { finish() }
            .show()
    }

    private fun launchMediaProjectionPermission() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as? MediaProjectionManager ?: return
        mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
    }
}
