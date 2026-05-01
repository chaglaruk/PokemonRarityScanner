// Purpose: Run the on-device Phase 2 screenshot prototype classifier.
package com.pokerarity.scanner.util.vision

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.gson.Gson
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

class Phase2VariantClassifier(
    private val context: Context,
    private val gson: Gson = Gson()
) {
    companion object {
        private const val TAG = "Phase2VariantClassifier"
        private const val ASSET_PATH = "data/variant_phase2_model.json"
        private const val RGB_SOBEL_FEATURE_MODE = "rgb_sobel_v2"
        private const val HUE_HISTOGRAM_BINS = 24
        private const val COLOR_GRID_SIZE = 8
    }

    data class Prediction(
        val target: String,
        val predictedValue: Boolean,
        val confidence: Float,
        val margin: Float,
        val positiveScore: Float,
        val negativeScore: Float,
        val positiveCount: Int,
        val negativeCount: Int,
        val passedThreshold: Boolean
    )

    data class Result(
        val species: String,
        val supportedTargets: List<String>,
        val predictions: List<Prediction>,
        val appliedTargets: List<String>,
        val minConfidence: Float,
        val minMargin: Float,
        val modelType: String
    )

    private data class Payload(
        val modelType: String? = null,
        val image: ImageConfig? = null,
        val appThresholds: Thresholds? = null,
        val targets: List<String>? = null,
        val supportedSpecies: Map<String, List<String>>? = null,
        val speciesModels: Map<String, SpeciesModel>? = null
    )

    private data class ImageConfig(
        val size: Int? = null,
        val featureMode: String? = null,
        val screenshotCrop: CropConfig? = null
    )

    private data class CropConfig(
        val left: Float? = null,
        val top: Float? = null,
        val width: Float? = null,
        val height: Float? = null
    )

    private data class Thresholds(
        val minConfidence: Float? = null,
        val minMargin: Float? = null,
        val requirePositivePrediction: Boolean? = null,
        val targetThresholds: Map<String, TargetThreshold>? = null
    )

    private data class TargetThreshold(
        val minConfidence: Float? = null,
        val minMargin: Float? = null,
        val requirePositivePrediction: Boolean? = null
    )

    private data class SpeciesModel(
        val targets: Map<String, TargetModel>? = null
    )

    private data class TargetModel(
        val supported: Boolean = false,
        val positiveCount: Int = 0,
        val negativeCount: Int = 0,
        val positivePrototype: List<Float>? = null,
        val negativePrototype: List<Float>? = null
    )

    private val loaded = AtomicBoolean(false)
    @Volatile private var payload: Payload? = null

    fun classify(bitmap: Bitmap, species: String?): Result? {
        val speciesName = species?.trim().takeUnless { it.isNullOrBlank() } ?: return null
        ensureLoaded()
        val activePayload = payload ?: return null
        val targetNames = activePayload.supportedSpecies?.get(speciesName).orEmpty()
        if (targetNames.isEmpty()) return null

        val imageSize = activePayload.image?.size ?: 32
        val crop = activePayload.image?.screenshotCrop ?: CropConfig(0.15f, 0.18f, 0.7f, 0.5f)
        val featureMode = activePayload.image?.featureMode ?: "sobel"
        val vector = runCatching {
            extractFeatureVector(bitmap, imageSize, crop, featureMode)
        }.getOrElse { error ->
            Log.w(TAG, "Feature extraction failed for species=$speciesName", error)
            return null
        }

        val defaultMinConfidence = activePayload.appThresholds?.minConfidence ?: 0.9f
        val defaultMinMargin = activePayload.appThresholds?.minMargin ?: 0.2f
        val defaultRequirePositive = activePayload.appThresholds?.requirePositivePrediction ?: false
        val predictions = targetNames.mapNotNull { target ->
            val targetModel = activePayload.speciesModels?.get(speciesName)?.targets?.get(target) ?: return@mapNotNull null
            if (!targetModel.supported) return@mapNotNull null
            val positive = targetModel.positivePrototype?.toFloatArray() ?: return@mapNotNull null
            val positiveScore = cosineSimilarity(vector, positive)
            val negative = targetModel.negativePrototype?.toFloatArray()
            val negativeScore = negative?.let { cosineSimilarity(vector, it) } ?: 0f
            val oneClass = negative == null || targetModel.negativeCount <= 0
            val margin = if (oneClass) positiveScore else positiveScore - negativeScore
            val confidence = if (oneClass) positiveScore.coerceIn(0f, 1f) else confidenceFromMargin(margin)
            val targetThreshold = activePayload.appThresholds?.targetThresholds?.get(target)
            val minConfidence = targetThreshold?.minConfidence ?: defaultMinConfidence
            val minMargin = targetThreshold?.minMargin ?: defaultMinMargin
            val requirePositive = targetThreshold?.requirePositivePrediction ?: defaultRequirePositive
            val predictedValue = margin >= 0f
            val effectiveMinConfidence = if (oneClass) max(minConfidence, 0.86f) else minConfidence
            Prediction(
                target = target,
                predictedValue = predictedValue,
                confidence = confidence,
                margin = margin,
                positiveScore = positiveScore,
                negativeScore = negativeScore,
                positiveCount = targetModel.positiveCount,
                negativeCount = targetModel.negativeCount,
                passedThreshold =
                    confidence >= effectiveMinConfidence &&
                    abs(margin) >= minMargin &&
                    (!requirePositive || predictedValue)
            )
        }.sortedBy { it.target }

        return Result(
            species = speciesName,
            supportedTargets = targetNames.sorted(),
            predictions = predictions,
            appliedTargets = predictions.filter { it.passedThreshold }.map { it.target },
            minConfidence = defaultMinConfidence,
            minMargin = defaultMinMargin,
            modelType = activePayload.modelType ?: "species_conditioned_variant_prototype_v1"
        )
    }

    private fun ensureLoaded() {
        if (loaded.get()) return
        synchronized(this) {
            if (loaded.get()) return
            payload = runCatching {
                context.assets.open(ASSET_PATH).bufferedReader().use {
                    gson.fromJson(it, Payload::class.java)
                }
            }.onFailure { error ->
                Log.w(TAG, "Phase 2 model load failed", error)
            }.getOrNull()
            loaded.set(true)
        }
    }

    private fun extractFeatureVector(
        bitmap: Bitmap,
        imageSize: Int,
        crop: CropConfig,
        featureMode: String
    ): FloatArray {
        val left = ((bitmap.width * (crop.left ?: 0.15f)).roundToInt()).coerceIn(0, bitmap.width - 1)
        val top = ((bitmap.height * (crop.top ?: 0.18f)).roundToInt()).coerceIn(0, bitmap.height - 1)
        var width = ((bitmap.width * (crop.width ?: 0.7f)).roundToInt()).coerceIn(1, bitmap.width)
        var height = ((bitmap.height * (crop.height ?: 0.5f)).roundToInt()).coerceIn(1, bitmap.height)
        if (left + width > bitmap.width) width = bitmap.width - left
        if (top + height > bitmap.height) height = bitmap.height - top

        val cropped = Bitmap.createBitmap(bitmap, left, top, width, height)
        val scaled = Bitmap.createScaledBitmap(cropped, imageSize, imageSize, true)
        if (scaled !== cropped) cropped.recycle()

        val pixels = IntArray(imageSize * imageSize)
        scaled.getPixels(pixels, 0, imageSize, 0, 0, imageSize, imageSize)
        scaled.recycle()

        val grayscale = FloatArray(pixels.size)
        val colorSums = FloatArray(COLOR_GRID_SIZE * COLOR_GRID_SIZE * 4)
        val hueHistogram = FloatArray(HUE_HISTOGRAM_BINS)
        for (index in pixels.indices) {
            val pixel = pixels[index]
            val red = (pixel shr 16) and 0xFF
            val green = (pixel shr 8) and 0xFF
            val blue = pixel and 0xFF
            val x = index % imageSize
            val y = index / imageSize
            val gridX = ((x * COLOR_GRID_SIZE) / imageSize).coerceIn(0, COLOR_GRID_SIZE - 1)
            val gridY = ((y * COLOR_GRID_SIZE) / imageSize).coerceIn(0, COLOR_GRID_SIZE - 1)
            val cellIndex = (gridY * COLOR_GRID_SIZE + gridX) * 4
            colorSums[cellIndex] += red / 255f
            colorSums[cellIndex + 1] += green / 255f
            colorSums[cellIndex + 2] += blue / 255f
            colorSums[cellIndex + 3] += 1f
            grayscale[index] = (0.299f * red + 0.587f * green + 0.114f * blue) / 255f
            addHueSample(hueHistogram, red, green, blue)
        }

        val sobel = FloatArray(grayscale.size)
        fun at(x: Int, y: Int): Float = grayscale[y * imageSize + x]
        for (y in 1 until imageSize - 1) {
            for (x in 1 until imageSize - 1) {
                val gx =
                    -at(x - 1, y - 1) + at(x + 1, y - 1) +
                    -2f * at(x - 1, y) + 2f * at(x + 1, y) +
                    -at(x - 1, y + 1) + at(x + 1, y + 1)
                val gy =
                    at(x - 1, y - 1) + 2f * at(x, y - 1) + at(x + 1, y - 1) -
                    at(x - 1, y + 1) - 2f * at(x, y + 1) - at(x + 1, y + 1)
                sobel[y * imageSize + x] = sqrt(gx * gx + gy * gy)
            }
        }
        if (featureMode == RGB_SOBEL_FEATURE_MODE) {
            return l2Normalize(colorGrid(colorSums) + sobel + scaledHueHistogram(hueHistogram))
        }
        return l2Normalize(sobel)
    }

    private fun colorGrid(colorSums: FloatArray): FloatArray {
        val output = FloatArray(COLOR_GRID_SIZE * COLOR_GRID_SIZE * 3)
        var outputIndex = 0
        for (cell in 0 until COLOR_GRID_SIZE * COLOR_GRID_SIZE) {
            val sourceIndex = cell * 4
            val count = max(1f, colorSums[sourceIndex + 3])
            output[outputIndex++] = colorSums[sourceIndex] / count
            output[outputIndex++] = colorSums[sourceIndex + 1] / count
            output[outputIndex++] = colorSums[sourceIndex + 2] / count
        }
        return output
    }

    private fun addHueSample(histogram: FloatArray, red: Int, green: Int, blue: Int) {
        val rf = red / 255f
        val gf = green / 255f
        val bf = blue / 255f
        val maximum = max(rf, max(gf, bf))
        val minimum = min(rf, min(gf, bf))
        val delta = maximum - minimum
        if (maximum <= 0f || delta <= 0f) return
        val saturation = delta / maximum
        if (saturation < 0.12f || maximum < 0.12f) return
        val hue = when (maximum) {
            rf -> (60f * ((gf - bf) / delta) + 360f) % 360f
            gf -> 60f * ((bf - rf) / delta) + 120f
            else -> 60f * ((rf - gf) / delta) + 240f
        }
        val bin = ((hue / 360f) * HUE_HISTOGRAM_BINS).toInt().coerceIn(0, HUE_HISTOGRAM_BINS - 1)
        histogram[bin] += saturation * maximum
    }

    private fun scaledHueHistogram(histogram: FloatArray): FloatArray {
        val total = histogram.sum()
        if (total <= 0f) return histogram
        return FloatArray(histogram.size) { index -> (histogram[index] / total) * 4f }
    }

    private fun l2Normalize(values: FloatArray): FloatArray {
        var sumSquares = 0f
        for (value in values) sumSquares += value * value
        val norm = sqrt(sumSquares)
        if (!norm.isFinite() || norm == 0f) return FloatArray(values.size)
        return FloatArray(values.size) { index -> values[index] / norm }
    }

    private fun cosineSimilarity(left: FloatArray, right: FloatArray): Float {
        val limit = min(left.size, right.size)
        var score = 0f
        for (index in 0 until limit) {
            score += left[index] * right[index]
        }
        return score
    }

    private fun confidenceFromMargin(margin: Float): Float {
        return max(0f, min(1f, 0.5f + abs(margin) / 2f))
    }
}
