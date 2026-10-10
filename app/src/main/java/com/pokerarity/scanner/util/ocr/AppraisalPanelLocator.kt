package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import kotlin.math.abs

/** Pixel layout only. Neither panel recognition nor a fill fraction supplies IV authority. */
internal data class AppraisalPanelLayout(val panel: Rect, val tracks: List<Rect>)

/** Three aligned tracks inside one bounded neutral card; all distances scale with the frame. */
@Suppress("MagicNumber")
internal object AppraisalPanelLocator {
    fun locate(bitmap: Bitmap): AppraisalPanelLayout? {
        if (bitmap.isRecycled || bitmap.width < 100 || bitmap.height < 100) return null
        val pixels = AppraisalFramePixels(bitmap)
        val bands = bands(pixels)
        val layouts = bands.windowed(3).mapNotNull { tracks -> layout(pixels, tracks) }
        return layouts.singleOrNull()
    }

    private fun bands(bitmap: AppraisalFramePixels): List<Rect> {
        val step = maxOf(1, bitmap.height / 1100)
        val bands = mutableListOf<Rect>()
        for (y in bitmap.height / 2 until bitmap.height * 95 / 100 step step) {
            for (run in rowRuns(bitmap, y)) {
                val previous = bands.lastOrNull { abs(it.bottom - y) <= step * 2 &&
                    abs(it.left - run.left) < bitmap.width / 40 &&
                    abs(it.right - run.right) < bitmap.width / 40 }
                if (previous == null) bands += run else previous.union(run)
            }
        }
        return bands.filter { it.height() >= bitmap.height * 0.003 &&
            it.height() <= bitmap.height * 0.022 }.sortedBy { it.top }
    }

    private fun rowRuns(bitmap: AppraisalFramePixels, y: Int): List<Rect> {
        val runs = mutableListOf<Rect>()
        var start = -1
        var last = -1
        var count = 0
        val gap = maxOf(2, bitmap.width / 90)
        fun endRun() {
            val span = last - start + 1
            val validSpan = span >= bitmap.width * 0.18 && span <= bitmap.width * 0.65
            if (start >= 0 && validSpan && count >= span * 0.85) runs += Rect(start, y, last + 1, y + 1)
            start = -1
            count = 0
        }
        for (x in 0 until bitmap.width) {
            if (AppraisalTrackPixels.track(bitmap.getPixel(x, y))) {
                if (start < 0) start = x
                last = x
                count++
            } else if (start >= 0 && x - last > gap) endRun()
        }
        endRun()
        return runs
    }

    private fun layout(bitmap: AppraisalFramePixels, tracks: List<Rect>): AppraisalPanelLayout? {
        val first = tracks[0]
        val gap = tracks[1].centerY() - first.centerY()
        val alignedGap = gap.toDouble() in (bitmap.height * 0.025)..(bitmap.height * 0.075) &&
            abs(tracks[2].centerY() - tracks[1].centerY() - gap) <= gap * 0.20
        val alignedEdges = tracks.all { matchingEdges(it, first, bitmap.width * 0.012) }
        val panels = tracks.mapNotNull { panelBounds(bitmap, it) }
        val panel = panels.firstOrNull()
        val samePanel = panel != null && panels.size == tracks.size && panels.all {
            matchingEdges(it, panel, bitmap.width * 0.012) && matchingVerticalEdges(it, panel, gap / 4)
        }
        val alignedTracks = alignedGap && alignedEdges
        val valid = alignedTracks && samePanel && tracks.any { hasFill(bitmap, it) }
        return if (valid) AppraisalPanelLayout(requireNotNull(panel), tracks.map(::Rect)) else null
    }

    private fun matchingEdges(a: Rect, b: Rect, tolerance: Double): Boolean =
        abs(a.left - b.left) <= tolerance && abs(a.right - b.right) <= tolerance

    private fun matchingVerticalEdges(a: Rect, b: Rect, tolerance: Int): Boolean =
        abs(a.top - b.top) <= tolerance && abs(a.bottom - b.bottom) <= tolerance

    private fun panelBounds(bitmap: AppraisalFramePixels, track: Rect): Rect? {
        val margin = maxOf(2, track.height() / 2)
        var left = track.left - margin
        var right = track.right + margin
        val y = track.centerY()
        val safeSides = left >= 1 && right < bitmap.width - 1
        val whiteSides = safeSides && AppraisalTrackPixels.white(bitmap.getPixel(left, y)) &&
            AppraisalTrackPixels.white(bitmap.getPixel(right, y))
        if (!whiteSides) return null
        while (left > 0 && AppraisalTrackPixels.white(bitmap.getPixel(left - 1, y))) left--
        while (right < bitmap.width && AppraisalTrackPixels.white(bitmap.getPixel(right, y))) right++
        val boundedWidth = left > 0 && right < bitmap.width && right - left <= bitmap.width * 0.75
        val paddedTrack = track.left - left >= margin && right - track.right >= margin
        return if (boundedWidth && paddedTrack) verticalBounds(bitmap, track, left, right) else null
    }

    private fun verticalBounds(bitmap: AppraisalFramePixels, track: Rect, left: Int, right: Int): Rect? {
        val x = left + (track.left - left) / 2
        var top = track.centerY()
        var bottom = track.centerY()
        while (top > 0 && AppraisalTrackPixels.white(bitmap.getPixel(x, top - 1))) top--
        while (bottom < bitmap.height && AppraisalTrackPixels.white(bitmap.getPixel(x, bottom))) bottom++
        val boundedHeight = top > bitmap.height / 2 && bottom < bitmap.height
        return Rect(left, top, right, bottom).takeIf { it.contains(track) && boundedHeight &&
            it.height() in (bitmap.height / 10)..(bitmap.height / 3) }
    }

    private fun hasFill(bitmap: AppraisalFramePixels, rect: Rect): Boolean =
        (rect.left until rect.right).any { AppraisalTrackPixels.filled(bitmap.getPixel(it, rect.centerY())) }

}

@Suppress("MagicNumber")
private object AppraisalTrackPixels {
    fun track(pixel: Int): Boolean {
        val r = pixel shr 16 and 255
        val g = pixel shr 8 and 255
        val b = pixel and 255
        return filled(pixel) || maxOf(r, g, b) - minOf(r, g, b) <= 12 &&
            minOf(r, g, b) >= 195 && maxOf(r, g, b) <= 240
    }

    fun filled(pixel: Int): Boolean {
        val r = pixel shr 16 and 255
        val g = pixel shr 8 and 255
        val b = pixel and 255
        return r > 210 && g >= 45 && r - maxOf(g, b) > 45
    }

    fun white(pixel: Int): Boolean {
        val r = pixel shr 16 and 255
        val g = pixel shr 8 and 255
        val b = pixel and 255
        return minOf(r, g, b) >= 245 && maxOf(r, g, b) - minOf(r, g, b) <= 12
    }
}

/** One bulk read per locator invocation avoids a JNI transition for every tested pixel. */
private class AppraisalFramePixels(bitmap: Bitmap) {
    val width = bitmap.width
    val height = bitmap.height
    private val pixels = IntArray(width * height).also {
        bitmap.getPixels(it, 0, width, 0, 0, width, height)
    }

    fun getPixel(x: Int, y: Int): Int = pixels[y * width + x]
}
