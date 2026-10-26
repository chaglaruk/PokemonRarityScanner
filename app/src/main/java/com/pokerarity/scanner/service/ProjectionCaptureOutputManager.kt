package com.pokerarity.scanner.service

import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.ImageReader

internal class ProjectionCaptureResizeException(cause: Exception) :
    Exception("Projection capture output could not be resized", cause)

/** Owns output reconfiguration and readers retained by in-flight capture contexts. */
internal class ProjectionCaptureOutputManager {
    private val retiredReaders = mutableSetOf<ImageReader>()

    /** The old surface remains attached if reconfiguration fails; callers then tear down. */
    @Suppress("TooGenericExceptionCaught")
    fun replaceReader(
        display: VirtualDisplay,
        previousReader: ImageReader,
        width: Int,
        height: Int,
        densityDpi: Int
    ): ImageReader {
        val replacement = try {
            ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        } catch (failure: Exception) {
            throw ProjectionCaptureResizeException(failure)
        }

        try {
            display.resize(width, height, densityDpi)
            display.setSurface(replacement.surface)
        } catch (failure: Exception) {
            runCatching {
                display.resize(previousReader.width, previousReader.height, densityDpi)
                display.setSurface(previousReader.surface)
            }
            runCatching { replacement.close() }
            throw ProjectionCaptureResizeException(failure)
        }
        return replacement
    }

    /** Returns readers that are detached and no longer held by an active context. */
    fun retire(reader: ImageReader, inUseReader: ImageReader?): List<ImageReader> =
        if (reader === inUseReader) {
            retiredReaders.add(reader)
            emptyList()
        } else {
            listOf(reader)
        }

    fun releaseUnused(inUseReader: ImageReader?): List<ImageReader> {
        val unused = retiredReaders.filter { it !== inUseReader }
        retiredReaders.removeAll(unused.toSet())
        return unused
    }

    fun drain(currentReader: ImageReader?): List<ImageReader> =
        (retiredReaders.toList() + listOfNotNull(currentReader)).distinct().also {
            retiredReaders.clear()
        }
}
