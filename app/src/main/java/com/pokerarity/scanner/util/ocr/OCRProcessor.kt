package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import com.pokerarity.scanner.data.model.PokemonData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class OCRProcessor(private val context: Context) {

    private val textParser = TextParser(context)
    private var isInitialized = false
    private var tess: TessBaseAPI? = null

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isInitialized) return@withContext
        try {
            val dataPath = context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
            val tessDataDir = File(dataPath, "tessdata")
            if (!tessDataDir.exists()) tessDataDir.mkdirs()
            val trainedDataFile = File(tessDataDir, "eng.traineddata")
            if (!trainedDataFile.exists()) {
                context.assets.open("tessdata/eng.traineddata").use { i ->
                    FileOutputStream(trainedDataFile).use { o -> i.copyTo(o) }
                }
            }
            val t = TessBaseAPI()
            if (t.init(dataPath, "eng")) {
                tess = t
                isInitialized = true
                android.util.Log.d("OCRProcessor", "Tesseract OK")
            } else {
                android.util.Log.e("OCRProcessor", "Tesseract init FAILED")
            }
        } catch (e: Exception) {
            android.util.Log.e("OCRProcessor", "init error", e)
        }
    }

    suspend fun ensureInitialized() = initialize()

    fun release() { tess?.end(); tess = null; isInitialized = false }

    suspend fun processImage(bitmap: Bitmap): PokemonData = withContext(Dispatchers.Default) {
        if (!isInitialized || tess == null) initialize()
        val t0 = System.currentTimeMillis()
        val procWM = ImagePreprocessor.processWhiteMask(bitmap)  // beyaz metin maskeleme
        // Beyaz kart uzerindeki gri metin (Candy) icin yuksek kontrast
        val procHC = ImagePreprocessor.processHighContrast(bitmap)

        // 1. Bolge bazli OCR
        // CP: hareketli arkaplan, beyaz metin - WHITE MASK kullan
        val cpRaw    = regionBlock(procWM, ScreenRegions.REGION_CP,    "CP",    "CP0123456789 ")
        var cpParsed = textParser.parseCP(cpRaw)

        // CP Fallback: Eger whiteMask basarisizsa (Dragonite kafasi vb.) HC dene
        if (cpParsed == null) {
            val cpRawHC = regionBlock(procHC, ScreenRegions.REGION_CP, "CP_HC", "CP0123456789 ")
            cpParsed = textParser.parseCP(cpRawHC)
        }

        // HP: beyaz kart, gri metin - HC
        val hpRaw    = region(procHC, ScreenRegions.REGION_HP,     "HP",    "HP0123456789/ ")
        val hpParsed = textParser.parseHPPair(hpRaw)

        // Candy: BIRINCIL tur kaynagi - beyaz kart, gri metin - HC
        val candyRaw = region(procHC, ScreenRegions.REGION_CANDY,  "Candy", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
        // Kullanici ismi: gradient arkaplan, beyaz metin - WHITE MASK
        val nameRaw  = region(procWM, ScreenRegions.REGION_NAME,   "Name",  "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
        // Name fallback: HC
        val nameFallbackRaw = if (textParser.parseName(nameRaw) == null) 
            region(procHC, ScreenRegions.REGION_NAME, "NameHC", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
        else ""
        
        // Tarih rozeti: turuncu rozet, sadece rakam
        val dynamicBadgeRect = ImagePreprocessor.detectOrangeBadge(bitmap)
        val badgeRaw = if (dynamicBadgeRect != null) {
            regionFromRect(procWM, dynamicBadgeRect, "BadgeDynamic", "0123456789/. \n")
        } else {
            region(procWM, ScreenRegions.REGION_DATE_BADGE, "BadgeFixed", "0123456789/. \n")
        }
        val bottomRaw = region(procHC, ScreenRegions.REGION_DATE_BOTTOM, "Bottom", "")

        // 2. Candy fallback: biraz daha genis bolge (tam isabetle gelmezse)
        val candyFallbackRaw = if (textParser.parseCandyName(candyRaw) == null)
            region(procHC, ScreenRegions.REGION_CANDY_WIDE, "CandyWide", "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz ")
        else ""

        // 3. Weight/Height/Stardust bolge bazli
        val weightRaw   = region(procHC, ScreenRegions.REGION_WEIGHT,   "Weight", "")
        val heightRaw   = region(procHC, ScreenRegions.REGION_HEIGHT,   "Height", "")
        
        // Stardust (Power Up cost) - Safe processing to prevent crash
        val stardustRaw = try {
            val stardustBitmap = ImagePreprocessor.cropRegion(bitmap, ScreenRegions.REGION_STARDUST)
            if (stardustBitmap != null) {
                val stardustClean = ImagePreprocessor.processStardust(stardustBitmap)
                val raw = tess?.let {
                    it.setImage(stardustClean)
                    it.utF8Text
                } ?: ""
                stardustBitmap.recycle()
                stardustClean.recycle()
                raw
            } else ""
        } catch (e: Exception) {
            Log.e("OCRProcessor", "Stardust processing failed", e)
            ""
        }

        // 4. Arc Level (Matematiksel CP dogrulama icin)
        val arcLevel = ImagePreprocessor.detectArcLevel(bitmap)

        // 5. Parse

        // Candy = birincil tur kaynagi
        val candyName = textParser.parseCandyName(candyRaw)
            ?: textParser.parseCandyName(candyFallbackRaw)

        // Kullanici ismi
        val nameParsed = textParser.parseName(nameRaw) ?: textParser.parseName(nameFallbackRaw)
        val displayName = candyName ?: nameParsed ?: "Unknown"
        val realName = candyName ?: nameParsed

        // Cinsiyet (İsim bölgesinden)
        val genderParsed = textParser.parseGender(nameRaw) ?: textParser.parseGender(nameFallbackRaw)

        // Boyut Etiketi (Weight/Height bölgesinden)
        val sizeTag = textParser.parseSizeTag(weightRaw) ?: textParser.parseSizeTag(heightRaw)

        // Tarih: once rozet, sonra alt bolge
        val dateParsed = textParser.parseDate(badgeRaw)
            ?: textParser.parseBottomDate(bottomRaw)
            ?: textParser.parseDate(bottomRaw)

        val elapsed = System.currentTimeMillis() - t0
        android.util.Log.d("OCRProcessor", """
|=== OCR $elapsed ms ===
|CP raw='$cpRaw' -> ${cpParsed}
|HP raw='$hpRaw' -> $hpParsed
|Candy raw='$candyRaw' -> $candyName
|Name raw='$nameRaw'
|NameHC raw='$nameFallbackRaw'
|DisplayName -> $displayName
|Badge raw='$badgeRaw'
|Bottom raw='$bottomRaw'
|Date -> $dateParsed
|RealName -> $realName
|NameRaw -> $nameRaw
|NameFallback -> $nameFallbackRaw
        """.trimMargin())

        PokemonData(
            cp        = cpParsed,
            hp        = hpParsed?.first,
            maxHp     = hpParsed?.second,
            name      = displayName,
            realName  = realName,
            candyName = candyName,
            megaEnergy = textParser.parseMegaEnergy(candyFallbackRaw),
            weight     = textParser.parseWeight(weightRaw),
            height     = textParser.parseHeight(heightRaw),
            gender     = genderParsed,
            stardust   = textParser.parseStardust(stardustRaw),
            arcLevel   = arcLevel,
            caughtDate = dateParsed,
            rawOcrText = "CP:$cpRaw|HP:$hpRaw|Candy:$candyRaw|Name:$nameRaw|NameHC:$nameFallbackRaw|Badge:$badgeRaw|BadgeType:${if (dynamicBadgeRect != null) "Dynamic" else "Fixed"}|SizeTag:$sizeTag"
        )
    }

    private fun region(bitmap: Bitmap, r: ScreenRegions.Region, label: String, wl: String? = null): String {
        val t = tess ?: return ""
        val cropped = try { ImagePreprocessor.cropRegion(bitmap, r) }
        catch (e: Exception) { android.util.Log.e("OCRProcessor","crop $label",e); return "" }
        return synchronized(t) {
            t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_LINE)
            t.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, wl ?: "")
            t.setImage(cropped)
            val txt = t.utF8Text?.trim() ?: ""
            android.util.Log.d("OCRProcessor", "[$label] '$txt'")
            txt
        }
    }
    // PSM_SINGLE_BLOCK: CP gibi tek buyuk metin blogunda daha iyi sonuc verir
    private fun regionBlock(bitmap: Bitmap, r: ScreenRegions.Region, label: String, wl: String? = null): String {
        val t = tess ?: return ""
        val cropped = try { ImagePreprocessor.cropRegion(bitmap, r) }
        catch (e: Exception) { android.util.Log.e("OCRProcessor","crop " + label, e); return "" }
        return synchronized(t) {
            t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
            t.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, wl ?: "")
            t.setImage(cropped)
            val txt = t.utF8Text?.trim() ?: ""
            android.util.Log.d("OCRProcessor", "[" + label + "] '" + txt + "'")
            txt
        }
    }

    private fun regionFromRect(bitmap: Bitmap, rect: android.graphics.Rect, label: String, wl: String? = null): String {
        val t = tess ?: return ""
        val cropped = try {
            val safeLeft = rect.left.coerceIn(0, bitmap.width - 1)
            val safeTop = rect.top.coerceIn(0, bitmap.height - 1)
            val safeWidth = rect.width().coerceAtMost(bitmap.width - safeLeft)
            val safeHeight = rect.height().coerceAtMost(bitmap.height - safeTop)
            if (safeWidth > 0 && safeHeight > 0)
                Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
            else null
        } catch (e: Exception) {
            android.util.Log.e("OCRProcessor", "crop rect $label", e)
            null
        } ?: return ""

        return synchronized(t) {
            t.setPageSegMode(TessBaseAPI.PageSegMode.PSM_SINGLE_BLOCK)
            t.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, wl ?: "")
            t.setImage(cropped)
            val txt = t.utF8Text?.trim() ?: ""
            android.util.Log.d("OCRProcessor", "[$label] '$txt'")
            txt
        }
    }
}