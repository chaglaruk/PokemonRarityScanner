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

class MLKitOcrProvider(context: Context) {

    data class RecognizedBlock(
        val text: String,
        val bounds: Rect?
    )

    data class Layout(val lines: List<RecognizedBlock>, val elements: List<RecognizedBlock>)

    suspend fun recognizeLayout(bitmap: Bitmap): Layout {
        val text = recognizeDocument(bitmap) ?: return Layout(emptyList(), emptyList())
        val lines = text.textBlocks.flatMap { it.lines }
        return Layout(lines.map { RecognizedBlock(it.text, it.boundingBox) },
            lines.flatMap { it.elements }.map { RecognizedBlock(it.text, it.boundingBox) })
    }

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    @Suppress("unused")
    private val appContext = context.applicationContext

    suspend fun recognizeText(bitmap: Bitmap): String? {
        return recognizeDocument(bitmap)?.text?.takeIf { it.isNotBlank() }
    }

    suspend fun recognizeBlocks(bitmap: Bitmap): List<RecognizedBlock> {
        val result = recognizeDocument(bitmap) ?: return emptyList()
        return result.textBlocks.map { block ->
            RecognizedBlock(
                text = block.text.orEmpty(),
                bounds = block.boundingBox
            )
        }
    }

    suspend fun warmUp() {
        val bitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.WHITE)
            recognizeDocument(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognizeDocument(bitmap: Bitmap): Text? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result)
            }
            .addOnFailureListener {
                if (continuation.isActive) continuation.resume(null)
            }
    }

    fun close() {
        recognizer.close()
    }
}
