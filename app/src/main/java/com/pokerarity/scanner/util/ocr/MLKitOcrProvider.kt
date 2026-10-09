package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** A failed ML Kit task is not a successfully empty OCR document. Cancellation propagates. */
enum class OcrDocumentStatus { SUCCESS, EMPTY, FAILED }

class MLKitOcrProvider(context: Context) {

    private companion object {
        const val WARM_UP_BITMAP_SIZE = 32
    }

    data class RecognizedBlock(
        val text: String,
        val bounds: Rect?
    )

    data class Layout(
        val lines: List<RecognizedBlock>,
        val elements: List<RecognizedBlock>,
        val documentStatus: OcrDocumentStatus = OcrDocumentStatus.SUCCESS
    )

    private data class DocumentResult(val text: Text?, val failed: Boolean)

    suspend fun recognizeLayout(bitmap: Bitmap): Layout {
        val result = recognizeDocument(bitmap)
        if (result.failed) return Layout(emptyList(), emptyList(), OcrDocumentStatus.FAILED)
        val lines = result.text?.textBlocks.orEmpty().flatMap { it.lines }
        return Layout(
            lines.map { RecognizedBlock(it.text, it.boundingBox) },
            lines.flatMap { it.elements }.map { RecognizedBlock(it.text, it.boundingBox) },
            if (lines.isEmpty()) OcrDocumentStatus.EMPTY else OcrDocumentStatus.SUCCESS
        )
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Suppress("unused")
    private val appContext = context.applicationContext

    suspend fun recognizeText(bitmap: Bitmap): String? {
        return recognizeDocument(bitmap).text?.text?.takeIf { it.isNotBlank() }
    }

    suspend fun recognizeBlocks(bitmap: Bitmap): List<RecognizedBlock> {
        val result = recognizeDocument(bitmap).text ?: return emptyList()
        return result.textBlocks.map { block ->
            RecognizedBlock(
                text = block.text.orEmpty(),
                bounds = block.boundingBox
            )
        }
    }

    suspend fun warmUp() {
        val bitmap = Bitmap.createBitmap(WARM_UP_BITMAP_SIZE, WARM_UP_BITMAP_SIZE, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            recognizeDocument(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognizeDocument(bitmap: Bitmap): DocumentResult = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(DocumentResult(result, failed = false))
            }
            .addOnFailureListener {
                // Never persist the exception message: it may contain local paths or provider internals.
                if (continuation.isActive) continuation.resume(DocumentResult(null, failed = true))
            }
    }

    fun close() {
        recognizer.close()
    }
}
