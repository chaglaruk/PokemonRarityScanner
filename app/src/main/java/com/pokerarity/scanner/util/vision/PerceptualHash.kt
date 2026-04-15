package com.pokerarity.scanner.util.vision

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.cos
import kotlin.math.sqrt

object PerceptualHash {
    private const val SOURCE_SIZE = 16
    private const val HASH_SIZE = 8

    fun compute(bitmap: Bitmap): String {
        val scaled = Bitmap.createScaledBitmap(bitmap, SOURCE_SIZE, SOURCE_SIZE, true)
        val pixels = DoubleArray(SOURCE_SIZE * SOURCE_SIZE)
        for (y in 0 until SOURCE_SIZE) {
            for (x in 0 until SOURCE_SIZE) {
                val pixel = scaled.getPixel(x, y)
                pixels[y * SOURCE_SIZE + x] =
                    0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)
            }
        }
        scaled.recycle()

        val dct = DoubleArray(HASH_SIZE * HASH_SIZE)
        for (u in 0 until HASH_SIZE) {
            for (v in 0 until HASH_SIZE) {
                var sum = 0.0
                for (x in 0 until SOURCE_SIZE) {
                    for (y in 0 until SOURCE_SIZE) {
                        val pixel = pixels[y * SOURCE_SIZE + x]
                        sum += pixel *
                            cos(((2 * x + 1) * u * Math.PI) / (2.0 * SOURCE_SIZE)) *
                            cos(((2 * y + 1) * v * Math.PI) / (2.0 * SOURCE_SIZE))
                    }
                }
                val cu = if (u == 0) 1.0 / sqrt(2.0) else 1.0
                val cv = if (v == 0) 1.0 / sqrt(2.0) else 1.0
                dct[v * HASH_SIZE + u] = 0.25 * cu * cv * sum
            }
        }

        val coeffs = dct.toMutableList()
        val dc = coeffs.removeAt(0)
        val avg = coeffs.average()
        val bits = BooleanArray(HASH_SIZE * HASH_SIZE)
        bits[0] = dc >= avg
        for (index in coeffs.indices) {
            bits[index + 1] = coeffs[index] >= avg
        }
        return bitsToHex(bits)
    }

    private fun bitsToHex(bits: BooleanArray): String {
        val sb = StringBuilder(bits.size / 4)
        var index = 0
        while (index < bits.size) {
            var nibble = 0
            repeat(4) { bit ->
                nibble = (nibble shl 1) or if (bits[index + bit]) 1 else 0
            }
            sb.append(Integer.toHexString(nibble))
            index += 4
        }
        return sb.toString()
    }
}
