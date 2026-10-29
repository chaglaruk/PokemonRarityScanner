package com.pokerarity.scanner.service

import android.graphics.PixelFormat
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.Log
import android.util.Size

internal class ProjectionCaptureResizeException(cause: Exception) :
    Exception("Projection capture output could not be resized", cause)

internal data class ProjectionCaptureOutput(
    val projection: MediaProjection?,
    val display: VirtualDisplay?,
    val reader: ImageReader?
)

internal data class ActiveProjectionCapture(
    val reader: ImageReader?,
    val ownership: ScanRequestToken?
)

internal data class ProjectionCaptureResizeRequest(
    val expectedProjection: MediaProjection,
    val currentOutput: ProjectionCaptureOutput,
    val activeCapture: ActiveProjectionCapture?,
    val size: Size,
    val densityDpi: Int
)

internal data class ProjectionCaptureResizeCallbacks(
    val onReaderReplaced: (ImageReader) -> Unit,
    val onActiveCaptureInvalidated: (ScanRequestToken) -> Unit,
    val onFailure: (Exception) -> Unit
)

/** Owns output reconfiguration and readers retained by in-flight capture contexts. */
internal class ProjectionCaptureOutputManager {
    private companion object {
        const val TAG = "ProjectionCaptureOutput"
    }

    private val retiredReaders = mutableSetOf<ImageReader>()

    fun resizeOutput(request: ProjectionCaptureResizeRequest, callbacks: ProjectionCaptureResizeCallbacks) {
        if (shouldResize(request)) resize(request, callbacks)
    }

    private fun shouldResize(request: ProjectionCaptureResizeRequest): Boolean {
        val output = request.currentOutput
        val reader = output.reader
        return output.projection === request.expectedProjection &&
            request.size.width > 0 && request.size.height > 0 &&
            output.display != null && reader != null &&
            (reader.width != request.size.width || reader.height != request.size.height)
    }

    private fun resize(request: ProjectionCaptureResizeRequest, callbacks: ProjectionCaptureResizeCallbacks) {
        val output = request.currentOutput
        val previousReader = requireNotNull(output.reader)
        val virtualDisplay = requireNotNull(output.display)
        val replacement = try {
            replaceReader(
                virtualDisplay,
                previousReader,
                request.size.width,
                request.size.height,
                request.densityDpi
            )
        } catch (failure: ProjectionCaptureResizeException) {
            callbacks.onFailure(failure)
            null
        }

        replacement?.let {
            callbacks.onReaderReplaced(it)
            request.activeCapture?.ownership?.let(callbacks.onActiveCaptureInvalidated)
            retire(previousReader, request.activeCapture?.reader).forEach(::closeReader)
        }
    }

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

    fun closeRetiredReaders(inUseReader: ImageReader?) {
        releaseUnused(inUseReader).forEach(::closeReader)
    }

    fun drain(currentReader: ImageReader?): List<ImageReader> =
        (retiredReaders.toList() + listOfNotNull(currentReader)).distinct().also {
            retiredReaders.clear()
        }

    fun drainAndClose(currentReader: ImageReader?) {
        drain(currentReader).forEach(::closeReader)
    }

    private fun closeReader(reader: ImageReader) {
        try {
            reader.close()
        } catch (_: Exception) {
            Log.w(TAG, "ImageReader close failed")
        }
    }
}
