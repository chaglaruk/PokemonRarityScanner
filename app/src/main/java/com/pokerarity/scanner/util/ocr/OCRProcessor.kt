package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import com.pokerarity.scanner.data.model.PokemonData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * One frame's recognition input: the recognition bitmap plus the per-frame metadata the
 * OCR step may consume (role, CP crop quality estimate, Phase 2B calibration fallback).
 */
data class FrameOcrRequest(
    val bitmap: Bitmap,
    val includeSecondaryFields: Boolean,
    val frameIndex: Int = 0,
    val frameRole: String = "fast",
    val estimatedCpCropQuality: Double? = null,
    val calibration: FrameCalibrationHint? = null
)

/** Local, spatial OCR. All visible fields share one document and one frame. */
class OCRProcessor(context: Context) {
    private val textParser = TextParser(context)
    private val provider by lazy { MLKitOcrProvider(context) }
    private val recognizer by lazy { AnchoredScreenRecognizer(context, provider, textParser) }
    private val initialization = Mutex()
    private var initialized = false

    suspend fun initialize() = initialization.withLock {
        if (!initialized) {
            provider.warmUp()
            initialized = true
        }
    }

    suspend fun ensureInitialized() = initialize()

    fun release() {
        provider.close()
        initialized = false
    }

    suspend fun processImage(bitmap: Bitmap, includeSecondaryFields: Boolean = true): PokemonData =
        processImageWithDiagnostics(FrameOcrRequest(bitmap, includeSecondaryFields)).pokemon

    // Retain the caller contract: the single document already includes secondary
    // text, so a second set of per-field OCR calls is unnecessary.
    suspend fun processImageWithDiagnostics(request: FrameOcrRequest): OcrFrameResult =
        withContext(Dispatchers.Default) {
            initialize()
            recognizer.recognize(
                bitmap = request.bitmap,
                frameIndex = request.frameIndex,
                role = request.frameRole,
                cpQuality = request.estimatedCpCropQuality,
                calibration = request.calibration
            )
        }
}
