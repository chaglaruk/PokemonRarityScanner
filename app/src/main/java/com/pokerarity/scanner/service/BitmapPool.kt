package com.pokerarity.scanner.service

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.util.ArrayDeque

class BitmapPool(private val maxSize: Int = 3) {

    private data class Entry(
        val bitmap: Bitmap
    )

    private val entries = ArrayDeque<Entry>()

    @Synchronized
    fun obtain(width: Int, height: Int, config: Bitmap.Config = Bitmap.Config.ARGB_8888): Bitmap {
        val iterator = entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val candidate = entry.bitmap
            if (!candidate.isRecycled && candidate.config == config && candidate.allocationByteCount >= width * height * 4) {
                iterator.remove()
                runCatching { candidate.reconfigure(width, height, config) }
                    .onFailure { Log.w("BitmapPool", "reconfigure failed, allocating fresh bitmap", it) }
                if (candidate.width == width && candidate.height == height && candidate.config == config) {
                    candidate.eraseColor(0)
                    return candidate
                }
            }
        }
        return Bitmap.createBitmap(width, height, config)
    }

    @Synchronized
    fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        if (!bitmap.isMutable) {
            bitmap.recycle()
            return
        }
        if (runCatching { bitmap.eraseColor(0) }.isFailure) {
            bitmap.recycle()
            return
        }
        entries.addFirst(Entry(bitmap))
        while (entries.size > maxSize) {
            entries.removeLast().bitmap.recycle()
        }
    }

    @Synchronized
    fun clear() {
        while (entries.isNotEmpty()) {
            entries.removeFirst().bitmap.recycle()
        }
    }

    @Synchronized
    fun decodeFile(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val reusable = obtain(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inMutable = true
            inBitmap = reusable
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return runCatching {
            BitmapFactory.decodeFile(path, options)
        }.getOrElse {
            release(reusable)
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply {
                inMutable = true
                inPreferredConfig = Bitmap.Config.ARGB_8888
            })
        }
    }
}
