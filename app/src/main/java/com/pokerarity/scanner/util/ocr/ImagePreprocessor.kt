package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

object ImagePreprocessor {

    fun loadAndPreprocess(imagePath: String): Bitmap? {
        val raw = android.graphics.BitmapFactory.decodeFile(imagePath) ?: return null
        return process(raw)
    }

    /**
     * Gradient/hareketli arka plan uzerindeki BEYAZ metin icin:
     * Sadece greyscale - beyaz metni korur, renkli arkaplan gri kalir.
     */
    fun process(bitmap: Bitmap): Bitmap {
        val targetWidth = minOf(bitmap.width, 900)
        val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
        val targetHeight = (bitmap.height * ratio).toInt()
        val resized = if (bitmap.width != targetWidth)
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        else bitmap

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val matrix = ColorMatrix()
        matrix.setSaturation(0f)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(resized, 0f, 0f, paint)
        if (resized != bitmap) resized.recycle()
        return output
    }

    /**
     * Beyaz kart uzerindeki GRI/KOYU metin icin yuksek kontrast.
     * (Candy, Stardust satirlari)
     */
    fun processHighContrast(bitmap: Bitmap): Bitmap {
        val targetWidth = minOf(bitmap.width, 900)
        val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
        val targetHeight = (bitmap.height * ratio).toInt()
        val resized = if (bitmap.width != targetWidth)
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        else bitmap

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        val contrast = 1.8f
        val brightness = -80f
        val matrix = ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, brightness,
            0f, contrast, 0f, 0f, brightness,
            0f, 0f, contrast, 0f, brightness,
            0f, 0f, 0f, 1f, 0f
        ))
        val grey = ColorMatrix()
        grey.setSaturation(0f)
        matrix.preConcat(grey)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(resized, 0f, 0f, paint)
        if (resized != bitmap) resized.recycle()
        return output
    }

    /**
     * Candy satiri icin daha sert ikili (binary) filtre.
     * Beyaz kart ustundeki koyu gri metni siyaha cevirir, geri kalani beyaz yapar.
     */
    fun processCandyText(bitmap: Bitmap): Bitmap {
        val targetWidth = minOf(bitmap.width, 900)
        val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
        val targetHeight = (bitmap.height * ratio).toInt()
        val resized = if (bitmap.width != targetWidth)
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        else bitmap

        val w = resized.width
        val h = resized.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = resized.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                val chroma = maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))
                // Chroma < 30: Pokemon govde renkleri (kanat, kuyruk vb.) genelde chroma > 30'dur.
                // Metin ise saf gri/siyah oldugundan chroma cok dusuktur.
                val isTextPixel = luminance < 176 || (luminance < 208 && chroma < 42)
                output.setPixel(x, y, if (isTextPixel) Color.BLACK else Color.WHITE)
            }
        }

        if (resized != bitmap) resized.recycle()
        return output
    }

    /**
     * Turuncu tarih rozetindeki beyaz yil/gun-ay metni icin ozel binary filtre.
     * Beyaz metni siyaha, turuncu zemini beyaza cevirir.
     */
    fun processDateBadge(bitmap: Bitmap): Bitmap {
        val targetWidth = minOf(bitmap.width, 900)
        val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
        val targetHeight = (bitmap.height * ratio).toInt()
        val resized = if (bitmap.width != targetWidth)
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        else bitmap

        val w = resized.width
        val h = resized.height
        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val pixel = resized.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                // Turuncu arka plan: Kırmızı yüksek, Yeşil orta, Mavi düşük
                // Geri kalan her şey (beyaz yazı, gri/siyah gölgeler) "siyah" yapılarak kalınlaştırılır.
                val isOrangeBg = r > 150 && g in 70..210 && b < 150 && (r - g) > 20 && (g - b) > 10
                output.setPixel(x, y, if (isOrangeBg) Color.WHITE else Color.BLACK)
            }
        }

        if (resized != bitmap) resized.recycle()
        return output
    }

    /**
     * Hareketli/renkli arka plan uzerindeki BEYAZ BOLD metin icin renk maskesi.
     * Pokemon GO CP, isim gibi metinler: RGB hepsi >200, renk farki <50 (beyaz/acik gri)
     * Arka plan: renkli (R,G,B farki buyuk) veya karanlik.
     *
     * Sonuc: beyaz metin -> siyah, her sey -> beyaz (Tesseract'in tercih ettigi format)
     * 3D animasyonlu arka plana karsi dayanikli.
     */
    fun processWhiteMask(bitmap: Bitmap): Bitmap {
        val targetWidth = minOf(bitmap.width, 900)
        val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
        val targetHeight = (bitmap.height * ratio).toInt()
        val resized = if (bitmap.width != targetWidth)
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        else bitmap

        val w = resized.width
        val h = resized.height
        val pixels = IntArray(w * h)
        resized.getPixels(pixels, 0, w, 0, 0, w, h)

        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            
            // Pokemon GO beyaz bold metin: RGB hepsi yuksek ve birbirine yakin
            // CP ve isim genelde en parlak beyazdir (>210)
            val isWhiteText = r > 210 && g > 210 && b > 210 &&
                              (maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))) < 30
            
            // Siyah outline da yakala (metin kenarlari) - Daha keskin sinir
            val isBlackOutline = r < 60 && g < 70 && b < 60
            
            out[i] = if (isWhiteText || isBlackOutline) Color.BLACK else Color.WHITE
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        if (resized != bitmap) resized.recycle()
        return result
    }

    /**
     * Daha siki beyaz metin maskesi - Pokemon govdelerinin (Lugia, Togekiss vb.)
     * parlak ama hafif renkli piksellerini reddetmek icin chroma < 18.
     * Name bolgesinde standart WM basarisiz oldugunda fallback olarak kullanilir.
     */
    fun processWhiteMaskStrict(bitmap: Bitmap): Bitmap {
        val targetWidth = minOf(bitmap.width, 900)
        val ratio = targetWidth.toFloat() / bitmap.width.toFloat()
        val targetHeight = (bitmap.height * ratio).toInt()
        val resized = if (bitmap.width != targetWidth)
            Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        else bitmap

        val w = resized.width
        val h = resized.height
        val pixels = IntArray(w * h)
        resized.getPixels(pixels, 0, w, 0, 0, w, h)

        val out = IntArray(w * h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF

            // Cok saf beyaz metin: RGB hepsi > 215 ve chroma < 18
            val isWhiteText = r > 215 && g > 215 && b > 215 &&
                              (maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))) < 18

            val isBlackOutline = r < 50 && g < 55 && b < 50

            out[i] = if (isWhiteText || isBlackOutline) Color.BLACK else Color.WHITE
        }

        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(out, 0, w, 0, 0, w, h)
        if (resized != bitmap) resized.recycle()
        return result
    }

    fun cropRegion(bitmap: Bitmap, region: ScreenRegions.Region): Bitmap {
        if (bitmap.isRecycled) {
            android.util.Log.e("ImagePreprocessor", "cropRegion: source bitmap is recycled")
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }
        val rect = ScreenRegions.getRectForRegion(bitmap, region)
        val safeLeft   = rect.left.coerceIn(0, bitmap.width - 1)
        val safeTop    = rect.top.coerceIn(0, bitmap.height - 1)
        val safeWidth  = rect.width().coerceAtMost(bitmap.width - safeLeft)
        val safeHeight = rect.height().coerceAtMost(bitmap.height - safeTop)
        return if (safeWidth > 0 && safeHeight > 0)
            Bitmap.createBitmap(bitmap, safeLeft, safeTop, safeWidth, safeHeight)
        else bitmap
    }

    /**
     * Dinamik turuncu rozet tespiti.
     * Ekranın sag orta bolgesinde (beyaz kartin ustu) turuncu pikselleri arar.
     */
    /**
     * Ekranın Pokemon modelini barındıran orta bölgesinden baskın rengi çıkarır.
     * Işık değişimlerinden etkilenmemek için basit bir piksel örnekleme (sampling) kullanır.
     */
    fun getDominantColor(bitmap: Bitmap): Int {
        val w = bitmap.width
        val h = bitmap.height
        
        // Pokemon Model Bölgesi: x=%25-75, y=%35-60 (Daha dar alan, sadece gövde)
        val left = (w * 0.25f).toInt()
        val right = (w * 0.75f).toInt()
        val top = (h * 0.35f).toInt()
        val bottom = (h * 0.60f).toInt()
        
        val counts = mutableMapOf<Int, Int>()
        
        for (y in top until bottom step 15) {
            for (x in left until right step 15) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                
                // Gri/Beyaz/Siyah filtreleme (Arkaplan ve yazı gürültüsü)
                val max = maxOf(r, maxOf(g, b))
                val min = minOf(r, minOf(g, b))
                if (max - min < 30) continue // Çok nötr/gri renkleri atla
                if (max > 230 || min < 40) continue // Çok parlak veya çok koyu renkleri atla
                
                // Renkleri grupla (quantization) - ışık farklarını azaltır
                val qr = (r / 20) * 20
                val qg = (g / 20) * 20
                val qb = (b / 20) * 20
                val qp = Color.rgb(qr, qg, qb)
                counts[qp] = (counts[qp] ?: 0) + 1
            }
        }
        
        val best = counts.maxByOrNull { it.value }?.key ?: Color.GRAY
        android.util.Log.d("ImagePreprocessor", "Dominant Color (V2): RGB(${Color.red(best)}, ${Color.green(best)}, ${Color.blue(best)})")
        return best
    }

    /**
     * Stardust bölgesi için özel filtre.
     * Yeşil/Renkli arka planı temizler, sadece koyu renkli (metin) pikselleri korur.
     */
    fun processStardust(bitmap: Bitmap): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return bitmap

        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)

        for (y in 0 until h) {
            for (x in 0 until w) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF

                val luminance = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                val chroma = maxOf(r, maxOf(g, b)) - minOf(r, minOf(g, b))
                val isTextPixel =
                    luminance < 150 ||
                        (luminance < 188 && chroma < 26) ||
                        (luminance < 208 && chroma < 42)

                out.setPixel(x, y, if (isTextPixel) Color.BLACK else Color.WHITE)
            }
        }
        return out
    }

    /**
     * Pokemon arkasındaki beyaz kemer (Arc) geometrisini analiz ederek doluluk oranını (% Level) bulur.
     */
    fun detectArcLevel(bitmap: Bitmap): Float? {
        val w = bitmap.width
        val h = bitmap.height
        
        // Kemer merkezi ve yaricapi (Genelde ekranin ortasinda, belli bir yukseklikte)
        // Referans 1080x2340: Merkez yaklasik (540, 950), Yaricap yaklasik 400
        val centerX = w / 2f
        val centerY = h * 0.402f
        val radiusMin = w * 0.33f
        val radiusMax = w * 0.37f
        
        val startAngle = 192.0 // Başlangıç açısı (Sol alt)
        val endAngle = -12.0   // Bitiş açısı (Sağ alt)
        val steps = 180        // Her 1 derece için bir adım
        val filled = BooleanArray(steps + 1)

        var foundAny = false
        var whitePixels = 0
        for (i in 0..steps) {
            val angleDeg = startAngle + (endAngle - startAngle) * (i.toDouble() / steps)
            val angleRad = Math.toRadians(angleDeg)
            
            var angleFilled = false
            for (r in (radiusMin.toInt()..radiusMax.toInt() step 1)) {
                val x = (centerX + r * Math.cos(angleRad)).toInt()
                val y = (centerY - r * Math.sin(angleRad)).toInt()
                
                if (x in 0 until w && y in 0 until h) {
                    val p = bitmap.getPixel(x, y)
                    val rVal = (p shr 16) and 0xFF
                    val gVal = (p shr 8) and 0xFF
                    val bVal = p and 0xFF
                    
                    // Beyaz kemer pikselleri: strict threshold to avoid clouds and grey unfilled arc
                    val brightness = (rVal + gVal + bVal) / 3
                    val maxDiff = maxOf(Math.abs(rVal - gVal), Math.abs(gVal - bVal), Math.abs(rVal - bVal))
                    val isWhite = brightness > 220 && maxDiff < 20
                    
                    if (isWhite) {
                        angleFilled = true
                        foundAny = true
                        whitePixels++
                        break
                    }
                }
            }
            
            filled[i] = angleFilled
        }
        
        if (!foundAny) {
            android.util.Log.d("ImagePreprocessor", "Arc detection: No white pixels found in arc region")
            return null
        }

        // Gap closing: tolerate larger gaps for screen variations
        val gapMax = 12
        var idx = 0
        while (idx <= steps) {
            if (filled[idx]) {
                idx++
                continue
            }
            val gapStart = idx
            while (idx <= steps && !filled[idx]) idx++
            val gapEnd = idx - 1
            val gapLen = gapEnd - gapStart + 1
            val leftTrue = gapStart - 1 >= 0 && filled[gapStart - 1]
            val rightTrue = idx <= steps && filled[idx]
            if (leftTrue && rightTrue && gapLen <= gapMax) {
                for (g in gapStart..gapEnd) {
                    filled[g] = true
                }
            }
        }

        val filledCount = filled.count { it }
        val fillRatio = filledCount.toFloat() / (steps + 1).toFloat()
        if (fillRatio < 0.05f) {
            android.util.Log.d("ImagePreprocessor", "Arc detection: Fill ratio too low: $fillRatio (need >=0.05)")
            return null
        }

        var lastFilled = -1
        for (i in 0..steps) {
            if (filled[i]) lastFilled = i
        }
        if (lastFilled < 0) return null

        val levelPercent = lastFilled.toFloat() / steps
        android.util.Log.d("ImagePreprocessor", "Arc Level: $levelPercent% (filled: $filledCount/$steps, ratio: $fillRatio, white_pixels: $whitePixels)")
        return levelPercent
    }

    /**
     * Ekranın sag orta bolgesinde (beyaz kartin ustu) turuncu pikselleri arar.
     */
    fun detectOrangeBadge(bitmap: Bitmap): android.graphics.Rect? {
        val w = bitmap.width
        val h = bitmap.height
        
        // Arama alani: x=%60-100, y=%25-55 (rozetin olabilecegi genis bolge)
        val searchLeft = (w * 0.60f).toInt()
        val searchRight = w
        val searchTop = (h * 0.25f).toInt()
        val searchBottom = (h * 0.55f).toInt()
        
        var minX = Int.MAX_VALUE; var maxX = -1
        var minY = Int.MAX_VALUE; var maxY = -1
        var count = 0
        
        for (y in searchTop until searchBottom step 2) {
            for (x in searchLeft until searchRight step 2) {
                val p = bitmap.getPixel(x, y)
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                
                // Pokemon GO Turuncu Rozet Filtresi (Daha esnek):
                // R:180-255, G:80-180, B:20-120
                val isOrange = r > 180 && g in 80..190 && b < 130 && (r - g) > 30 && (g - b) > 20
                
                if (isOrange) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                    count++
                }
            }
        }
        
        // Gurultu filtreleme: cok az piksel varsa rozet degildir
        if (count < 60) return null
        
        val bw = maxX - minX
        val bh = maxY - minY
        
        // Rozet boyut kontrolü (Mantıklı sınırlar: 40px - 500px genişlik)
        if (bw !in 40..500 || bh !in 15..200) return null
        
        // OCR icin biraz padding ekle
        return android.graphics.Rect(
            (minX - 8).coerceAtLeast(0),
            (minY - 8).coerceAtLeast(0),
            (maxX + 8).coerceAtMost(w),
            (maxY + 8).coerceAtMost(h)
        )
    }
}
