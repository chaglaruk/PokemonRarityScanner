package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppraisalBarAnalyzerTest {

    @Test
    fun analyze_recoversIntegerIvBarsFromSyntheticAppraisal() {
        val bitmap = Bitmap.createBitmap(1080, 2340, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.BLACK)
        val cardPaint = Paint().apply { color = Color.WHITE }
        val fillPaint = Paint().apply { color = Color.rgb(255, 153, 51) }
        val barTop = 1500
        val barLeft = 200
        val barWidth = 680
        val barHeight = 28
        val gap = 86

        canvas.drawRect(Rect(120, 1400, 980, 1820), cardPaint)

        fun drawBar(index: Int, value: Int) {
            val top = barTop + index * gap
            val right = barLeft + ((barWidth * (value / 15f)).toInt())
            canvas.drawRect(Rect(barLeft, top, right, top + barHeight), fillPaint)
        }

        drawBar(0, 15)
        drawBar(1, 8)
        drawBar(2, 3)

        val result = AppraisalBarAnalyzer.analyze(bitmap)

        assertNotNull(result)
        assertEquals(15, result!!.attack)
        assertEquals(8, result.defense)
        assertEquals(3, result.stamina)
    }
}
