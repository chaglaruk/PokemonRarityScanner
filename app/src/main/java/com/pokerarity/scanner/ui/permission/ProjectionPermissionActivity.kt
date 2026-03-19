package com.pokerarity.scanner.ui.permission

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.pokerarity.scanner.service.OverlayService
import com.pokerarity.scanner.service.ScreenCaptureManager

class ProjectionPermissionActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_AUTO_CAPTURE = "extra_auto_capture"
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (ScreenCaptureManager.handleResult(result)) {
            val serviceIntent = ScreenCaptureManager.buildServiceIntent(this)
            if (serviceIntent != null) {
                startForegroundService(serviceIntent)
            }
            if (intent.getBooleanExtra(EXTRA_AUTO_CAPTURE, false)) {
                sendBroadcast(Intent(OverlayService.ACTION_CAPTURE_REQUESTED).apply {
                    setPackage(packageName)
                })
            }
        } else {
            Toast.makeText(this, "Screen capture permission denied.", Toast.LENGTH_LONG).show()
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjectionLauncher.launch(mgr.createScreenCaptureIntent())
    }
}
