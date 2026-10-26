package com.pokerarity.scanner.util.ocr

import com.pokerarity.scanner.service.OwnedFrameHandoff
import com.pokerarity.scanner.util.perf.DebugScanTiming
import com.pokerarity.scanner.util.perf.ScanTimingStage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** A failed ML Kit task is not a successfully empty OCR document. Cancellation propagates. */
enum class OcrDocumentStatus { SUCCESS, EMPTY, FAILED }

internal interface OcrTextRecognitionClient {
    fun process(bitmap: Bitmap): Task<Text>
    fun close()
}

private class MlKitTextRecognitionClient : OcrTextRecognitionClient {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    override fun process(bitmap: Bitmap): Task<Text> =
        recognizer.process(InputImage.fromBitmap(bitmap, 0))

    override fun close() = recognizer.close()
}

class MLKitOcrProvider internal constructor(
    context: Context,
    private val recognizer: OcrTextRecognitionClient
) {

    constructor(context: Context) : this(context, MlKitTextRecognitionClient())

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

    suspend fun recognizeLayout(bitmap: Bitmap): Layout {
        val timing = DebugScanTiming.begin(ScanTimingStage.OCR_LAYOUT, bitmap.width, bitmap.height)
        try {
            val result = recognizeDocument(bitmap)
            if (result.failed) return Layout(emptyList(), emptyList(), OcrDocumentStatus.FAILED)
            val lines = result.value?.textBlocks.orEmpty().flatMap { it.lines }
            return Layout(
                lines.map { RecognizedBlock(it.text, it.boundingBox) },
                lines.flatMap { it.elements }.map { RecognizedBlock(it.text, it.boundingBox) },
                if (lines.isEmpty()) OcrDocumentStatus.EMPTY else OcrDocumentStatus.SUCCESS
            )
        } finally { timing?.close() }
    }

    private val taskLifecycle = OcrTaskLifecycle(closeRecognizer = { recognizer.close() })
    @Suppress("unused")
    private val appContext = context.applicationContext

    suspend fun recognizeText(bitmap: Bitmap): String? {
        val timing = DebugScanTiming.begin(ScanTimingStage.OCR_TEXT)
        try {
            return recognizeDocument(bitmap).value?.text?.takeIf { it.isNotBlank() }
        } finally { timing?.close() }
    }

    suspend fun recognizeBlocks(bitmap: Bitmap): List<RecognizedBlock> {
        val timing = DebugScanTiming.begin(ScanTimingStage.OCR_BLOCKS)
        try {
            val result = recognizeDocument(bitmap).value ?: return emptyList()
            return result.textBlocks.map { block ->
                RecognizedBlock(
                    text = block.text.orEmpty(),
                    bounds = block.boundingBox
                )
            }
        } finally { timing?.close() }
    }

    suspend fun warmUp() {
        val timing = DebugScanTiming.begin(ScanTimingStage.OCR_WARM_UP)
        try {
            val bitmap = Bitmap.createBitmap(WARM_UP_BITMAP_SIZE, WARM_UP_BITMAP_SIZE, Bitmap.Config.ARGB_8888)
            try {
                bitmap.eraseColor(Color.WHITE)
                recognizeDocument(bitmap)
            } finally {
                bitmap.recycle()
            }
        } finally { timing?.close() }
    }

    private suspend fun recognizeDocument(bitmap: Bitmap): OcrTaskResult<Text> {
        var input: OcrTaskLifecycle.BitmapInput? = null
        var ownedInput: OwnedFrameHandoff.Lease? = null
        var taskLease: OcrTaskLifecycle.Lease? = null
        return awaitOcrTask(
            createTask = {
                if (OwnedFrameHandoff.active()) {
                    taskLease = taskLifecycle.acquireTask()
                    val pixels = DebugScanTiming.measure(ScanTimingStage.OWNED_TASK_COPY) {
                        OwnedFrameHandoff.taskCopy(bitmap)
                    }
                    ownedInput = pixels
                    recognizer.process(pixels.pixels).also { task ->
                        OwnedFrameHandoff.trackNativeTask(task, pixels)
                    }
                } else {
                    val taskInput = taskLifecycle.copyInput(bitmap)
                    input = taskInput
                    recognizer.process(taskInput.bitmap)
                }
            },
            onTaskComplete = {
                val completedInput = input.also { input = null }
                val completedOwnedInput = ownedInput.also { ownedInput = null }
                val completedTaskLease = taskLease.also { taskLease = null }
                try {
                    completedInput?.close()
                } finally {
                    try {
                        completedOwnedInput?.close()
                    } finally {
                        completedTaskLease?.close()
                    }
                }
            }
        )
    }

    fun close() {
        taskLifecycle.close()
    }
}
