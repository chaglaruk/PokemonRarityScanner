package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.min

object SpriteSignature {

    private const val HASH_SIZE = 8
    private const val EDGE_SIZE = 48
    private const val EDGE_BINS = 8
    private const val TRIM_STEP = 2

    data class Signature(
        val aHash: String,
        val dHash: String,
        val edge: FloatArray
    )

    fun compute(bitmap: Bitmap): Signature {
        val scaled = ColorAnalyzer.downscaleForAnalysis(bitmap)
        val shouldRecycleScaled = scaled !== bitmap
        val masked = ColorAnalyzer.extractMaskedSprite(scaled)
        val signature = computeFromMaskedSprite(masked)
        masked.recycle()
        if (shouldRecycleScaled) scaled.recycle()
        return signature
    }

    fun computeFromMaskedSprite(maskedSprite: Bitmap): Signature {
        val trimmed = trimToContent(maskedSprite)
        val aHash = computeAHash(trimmed, HASH_SIZE)
        val dHash = computeDHash(trimmed, HASH_SIZE)
        val edge = computeEdgeHistogram(trimmed, EDGE_SIZE, EDGE_BINS)
        if (trimmed !== maskedSprite) trimmed.recycle()
        return Signature(aHash, dHash, edge)
    }

    fun hammingHex(a: String, b: String): Int {
        if (a.length != b.length) return 64
        var dist = 0
        for (i in a.indices) {
            val ai = hexNibble(a[i])
            val bi = hexNibble(b[i])
            dist += NIBBLE_BITS[ai xor bi]
        }
        return dist
    }

    fun edgeDistance(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 1f
        var sum = 0f
        for (i in a.indices) {
            sum += abs(a[i] - b[i])
        }
        return (sum / 2f).coerceIn(0f, 1f)
    }

    private fun trimToContent(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)

        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val p = pixels[y * w + x]
                val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
                if (lum > 18 || (Color.red(p) + Color.green(p) + Color.blue(p)) > 60) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
                x += TRIM_STEP
            }
            y += TRIM_STEP
        }

        if (maxX < 0 || maxY < 0) return bitmap
        val width = maxOf(1, maxX - minX + 1)
        val height = maxOf(1, maxY - minY + 1)
        if (width == w && height == h) return bitmap
        return Bitmap.createBitmap(bitmap, minX, minY, width, height)
    }

    private fun computeAHash(bitmap: Bitmap, size: Int): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val lums = IntArray(pixels.size)
        var sum = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            val lum = (0.299 * Color.red(p) + 0.587 * Color.green(p) + 0.114 * Color.blue(p)).toInt()
            lums[i] = lum
            sum += lum
        }
        val avg = sum / lums.size
        val bits = BooleanArray(lums.size) { idx -> lums[idx] > avg }
        scaled.recycle()
        return bitsToHex(bits)
    }

    private fun computeDHash(bitmap: Bitmap, size: Int): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, size + 1, size, true)
        val pixels = IntArray((size + 1) * size)
        scaled.getPixels(pixels, 0, size + 1, 0, 0, size + 1, size)
        val bits = BooleanArray(size * size)
        var idx = 0
        for (y in 0 until size) {
            val row = y * (size + 1)
            for (x in 0 until size) {
                val left = pixels[row + x]
                val right = pixels[row + x + 1]
                val l1 = (Color.red(left) + Color.green(left) + Color.blue(left)) / 3
                val l2 = (Color.red(right) + Color.green(right) + Color.blue(right)) / 3
                bits[idx++] = l1 < l2
            }
        }
        scaled.recycle()
        return bitsToHex(bits)
    }

    private fun computeEdgeHistogram(bitmap: Bitmap, size: Int, bins: Int): FloatArray {
        val scaled = Bitmap.createScaledBitmap(bitmap, size, size, true)
        val pixels = IntArray(size * size)
        scaled.getPixels(pixels, 0, size, 0, 0, size, size)
        val hist = IntArray(bins)
        var count = 0
        for (y in 1 until size - 1) {
            for (x in 1 until size - 1) {
                val idx = y * size + x
                val lumL = luminance(pixels[idx - 1])
                val lumR = luminance(pixels[idx + 1])
                val lumU = luminance(pixels[idx - size])
                val lumD = luminance(pixels[idx + size])
                val grad = min(255, abs(lumR - lumL) + abs(lumD - lumU))
                val bin = min(bins - 1, (grad * bins) / 256)
                hist[bin]++
                count++
            }
        }
        scaled.recycle()
        val out = FloatArray(bins)
        val denom = if (count == 0) 1 else count
        for (i in 0 until bins) {
            out[i] = hist[i].toFloat() / denom.toFloat()
        }
        return out
    }

    private fun luminance(pixel: Int): Int {
        return (0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)).toInt()
    }

    private fun bitsToHex(bits: BooleanArray): String {
        val sb = StringBuilder(bits.size / 4)
        var i = 0
        while (i < bits.size) {
            var value = 0
            for (j in 0 until 4) {
                value = (value shl 1) or if (bits[i + j]) 1 else 0
            }
            sb.append(Integer.toHexString(value))
            i += 4
        }
        return sb.toString()
    }

    private fun hexNibble(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c.code - '0'.code
            in 'a'..'f' -> 10 + (c.code - 'a'.code)
            in 'A'..'F' -> 10 + (c.code - 'A'.code)
            else -> 0
        }
    }

    private val NIBBLE_BITS = intArrayOf(
        0, 1, 1, 2, 1, 2, 2, 3,
        1, 2, 2, 3, 2, 3, 3, 4
    )
}
