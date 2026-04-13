package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.pokerarity.scanner.data.model.PokemonData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

class OCRProcessor(private val context: Context) {

    private val textParser = TextParser(context)
    private val mlKitOcrProvider by lazy { MLKitOcrProvider(context) }

    @Volatile
    private var isInitialized = false

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        ImagePreprocessor.ensureOpenCvReady()
        isInitialized = true
        Log.d("OCRProcessor", "ML Kit OCR ready")
    }

    suspend fun ensureInitialized() = initialize()

    fun release() {
        mlKitOcrProvider.close()
        isInitialized = false
    }

    suspend fun processImage(bitmap: Bitmap, includeSecondaryFields: Boolean = true): PokemonData = withContext(Dispatchers.Default) {
        if (!isInitialized) initialize()

        val cpDeferred = async { recognizeCp(bitmap) }
        val hpDeferred = async { collectHpRaws(bitmap) }
        val nameDeferred = async { recognizeName(bitmap) }
        val dateDeferred = async { recognizeDate(bitmap, includeSecondaryFields) }

        val cpResult = cpDeferred.await()
        val hpResult = resolveHpResult(cpResult.value, hpDeferred.await())
        val nameResult = nameDeferred.await()
        val caughtDate = dateDeferred.await()

        val raw = buildString {
            append("CP:").append(cpResult.raw)
            append("|HP:").append(hpResult.raw)
            append("|NameDynamic:").append(nameResult.source)
            append("|Name:").append(nameResult.raw)
            caughtDate?.let { append("|Date:").append(it.time) }
        }

        PokemonData(
            cp = cpResult.value,
            hp = hpResult.value?.first,
            maxHp = hpResult.value?.second,
            name = nameResult.value,
            realName = nameResult.value,
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = null,
            arcLevel = null,
            caughtDate = caughtDate,
            rawOcrText = raw,
            fullVariantMatch = null,
            powerUpCandyCost = null,
            powerUpCandySource = null,
            powerUpStardustSource = null,
            appraisalAttack = null,
            appraisalDefense = null,
            appraisalStamina = null,
            appraisalConfidence = null,
            arcEstimatedLevel = null,
            arcSource = null,
            ocrDiagnosticsDir = null,
            ocrDiagnosticsFiles = emptyMap()
        )
    }

    private suspend fun recognizeCp(bitmap: Bitmap): OcrValue<Int> {
        val attempts = listOf(
            "cp_mask" to cropAndProcess(bitmap, ScreenRegions.REGION_CP) { ImagePreprocessor.processWhiteMask(it) },
            "cp_hc" to cropAndProcess(bitmap, ScreenRegions.REGION_CP) { ImagePreprocessor.processHighContrast(it) },
            "cp_adaptive" to cropAndProcess(bitmap, ScreenRegions.REGION_CP) { ImagePreprocessor.applyAdaptiveThresholding(it) }
        )
        for ((label, crop) in attempts) {
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            val parsed = textParser.parseCP(raw)
            if (parsed != null) return OcrValue(parsed, raw, label)
        }
        return OcrValue(null, "", "missing")
    }

    private suspend fun collectHpRaws(bitmap: Bitmap): List<String> {
        val raws = mutableListOf<String>()
        val attempts = listOf(
            ScreenRegions.REGION_HP,
            ScreenRegions.REGION_HP_ALT,
            ScreenRegions.REGION_HP_LOWER
        )
        attempts.forEachIndexed { _, region ->
            val crop = cropAndProcess(bitmap, region) { ImagePreprocessor.processHpText(it) }
            val raw = mlKitOcrProvider.recognizeText(crop).orEmpty()
            crop.recycle()
            raws += raw
        }
        return raws
    }

    private fun resolveHpResult(cp: Int?, raws: List<String>): OcrValue<Pair<Int, Int>> {
        val pair = TextParseUtils.selectBestHPPairForCp(cp, *raws.toTypedArray())
        return OcrValue(
            value = pair,
            raw = raws.joinToString(" || "),
            source = if (pair != null) "hp_cp_aware" else "missing"
        )
    }

    private suspend fun recognizeName(bitmap: Bitmap): OcrValue<String> {
        val blocks = mlKitOcrProvider.recognizeBlocks(bitmap)
        val dynamicCandidate = blocks
            .filter { block ->
                val bounds = block.bounds ?: return@filter false
                bounds.top < (bitmap.height * 0.58f).toInt() &&
                    bounds.centerX() in (bitmap.width * 0.12f).toInt()..(bitmap.width * 0.88f).toInt()
            }
            .mapNotNull { block ->
                val ranked = textParser.rankNameCandidates(block.text, limit = 1).firstOrNull() ?: return@mapNotNull null
                Triple(ranked.name, ranked.score, block.text)
            }
            .maxByOrNull { it.second }

        if (dynamicCandidate != null && dynamicCandidate.second >= 0.72) {
            return OcrValue(dynamicCandidate.first, dynamicCandidate.third, "mlkit_dynamic")
        }

        val fallbackCrop = cropAndProcess(bitmap, ScreenRegions.REGION_NAME) { ImagePreprocessor.processWhiteMask(it) }
        val fallbackRaw = mlKitOcrProvider.recognizeText(fallbackCrop).orEmpty()
        fallbackCrop.recycle()
        val fallbackParsed = textParser.parseName(fallbackRaw) ?: textParser.parseNameFromFullText(fallbackRaw)
        return OcrValue(fallbackParsed, fallbackRaw, if (fallbackParsed != null) "static_name_crop" else "missing")
    }

    private suspend fun recognizeDate(bitmap: Bitmap, includeSecondaryFields: Boolean): java.util.Date? {
        if (!includeSecondaryFields) return null
        val badgeRect = ImagePreprocessor.detectOrangeBadge(bitmap)
        val badgeRaw = if (badgeRect != null) {
            val crop = cropBitmap(bitmap, badgeRect)
            try {
                val processed = ImagePreprocessor.processDateBadge(crop)
                try {
                    mlKitOcrProvider.recognizeText(processed).orEmpty()
                } finally {
                    processed.recycle()
                }
            } finally {
                crop.recycle()
            }
        } else {
            val crop = cropAndProcess(bitmap, ScreenRegions.REGION_DATE_BADGE) { ImagePreprocessor.processDateBadge(it) }
            try {
                mlKitOcrProvider.recognizeText(crop).orEmpty()
            } finally {
                crop.recycle()
            }
        }
        return textParser.parseDate(badgeRaw)
    }

    private fun cropAndProcess(bitmap: Bitmap, region: ScreenRegions.Region, transform: (Bitmap) -> Bitmap): Bitmap {
        val crop = cropBitmap(bitmap, ScreenRegions.getRectForRegion(bitmap, region))
        val processed = transform(crop)
        if (processed !== crop) crop.recycle()
        return processed
    }

    private fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val safe = Rect(
            rect.left.coerceIn(0, bitmap.width - 1),
            rect.top.coerceIn(0, bitmap.height - 1),
            rect.right.coerceIn(1, bitmap.width),
            rect.bottom.coerceIn(1, bitmap.height)
        )
        val width = (safe.right - safe.left).coerceAtLeast(1)
        val height = (safe.bottom - safe.top).coerceAtLeast(1)
        return Bitmap.createBitmap(bitmap, safe.left, safe.top, width, height)
    }

    private data class OcrValue<T>(
        val value: T?,
        val raw: String,
        val source: String
    )
}
