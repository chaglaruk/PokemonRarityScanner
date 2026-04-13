package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.cos
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
class ArcPointAnalyzerTest {

    @Test
    fun detect_readsSyntheticArcDotAndMapsItToLevel() {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height * 0.402f
        val radius = bitmap.width * 0.35f
        val level = 30.0
        val progress = (level - 1.0) / 49.0
        val startAngle = 192.0
        val endAngle = -12.0
        val angleDeg = startAngle + (endAngle - startAngle) * progress
        val angleRad = Math.toRadians(angleDeg)
        val dotX = centerX + radius * cos(angleRad).toFloat()
        val dotY = centerY - radius * sin(angleRad).toFloat()
        canvas.drawCircle(dotX, dotY, 14f, paint)

        val result = ArcPointAnalyzer.detect(bitmap)

        assertNotNull(result)
        assertTrue(result!!.estimatedLevel in 1.0..50.0)
        assertTrue(result.confidence > 0f)
    }

    @Test
    fun detect_returnsLowConfidenceWhenOnlyScatteredWhiteNoiseExists() {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val centerX = bitmap.width / 2f
        val centerY = bitmap.height * 0.402f
        val radius = bitmap.width * 0.35f

        repeat(12) { i ->
            val angleRad = Math.toRadians(120.0 + i * 2.0)
            val dotX = centerX + radius * cos(angleRad).toFloat()
            val dotY = centerY - radius * sin(angleRad).toFloat()
            canvas.drawCircle(dotX, dotY, 2f, paint)
        }

        val result = ArcPointAnalyzer.detect(bitmap)

        assertNotNull(result)
        assertTrue("Scattered arc noise should not score as high confidence", result!!.confidence < 0.7f)
    }
}
