package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class MLKitOcrProvider(context: Context) {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val appContext = context.applicationContext

    suspend fun recognizeText(bitmap: Bitmap): String? = suspendCancellableCoroutine { continuation ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                if (continuation.isActive) {
                    continuation.resume(result.text.takeIf { it.isNotBlank() })
                }
            }
            .addOnFailureListener {
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
    }

    fun close() {
        recognizer.close()
    }
}
