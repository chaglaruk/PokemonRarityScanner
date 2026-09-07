package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import com.pokerarity.scanner.data.model.PokemonData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
        processImageWithDiagnostics(bitmap, includeSecondaryFields).pokemon

    // Retain the caller contract: the single document already includes secondary
    // text, so a second set of per-field OCR calls is unnecessary.
    @Suppress("UNUSED_PARAMETER")
    suspend fun processImageWithDiagnostics(
        bitmap: Bitmap,
        includeSecondaryFields: Boolean = true,
        frameIndex: Int = 0,
        frameRole: String = "fast",
        estimatedCpCropQuality: Double? = null
    ): OcrFrameResult = withContext(Dispatchers.Default) {
        initialize()
        recognizer.recognize(bitmap, frameIndex, frameRole, estimatedCpCropQuality)
    }
}
