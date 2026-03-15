package com.pokerarity.scanner.ui.debug

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import com.pokerarity.scanner.util.ocr.ScreenRegions

class DebugOverlayView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private val textPaint = Paint().apply {
        color = Color.RED
        textSize = 30f
        style = Paint.Style.FILL
    }

    private var screenWidth = 0
    private var screenHeight = 0

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        screenWidth = w
        screenHeight = h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        drawRegion(canvas, ScreenRegions.REGION_CP, "CP")
        drawRegion(canvas, ScreenRegions.REGION_NAME, "NAME")
        drawRegion(canvas, ScreenRegions.REGION_HP, "HP")
        drawRegion(canvas, ScreenRegions.REGION_STARDUST, "STARDUST")
        drawRegion(canvas, ScreenRegions.REGION_CANDY, "CANDY")
        drawRegion(canvas, ScreenRegions.REGION_DATE_BADGE, "DATE")
        
        // Arc Level bölgesi (tahmini)
        val arcCenterY = screenHeight * 0.402f
        val arcRadiusMin = screenWidth * 0.33f
        val arcRadiusMax = screenWidth * 0.37f
        paint.color = Color.CYAN
        canvas.drawCircle(screenWidth / 2f, arcCenterY, arcRadiusMin, paint)
        canvas.drawCircle(screenWidth / 2f, arcCenterY, arcRadiusMax, paint)
    }

    private fun drawRegion(canvas: Canvas, region: ScreenRegions.Region, label: String) {
        val left = region.leftPercent * screenWidth
        val top = region.topPercent * screenHeight
        val right = left + (region.widthPercent * screenWidth)
        val bottom = top + (region.heightPercent * screenHeight)
        
        paint.color = Color.RED
        canvas.drawRect(left, top, right, bottom, paint)
        canvas.drawText(label, left, top - 5f, textPaint)
    }
}
