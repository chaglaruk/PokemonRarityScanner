package com.pokerarity.scanner

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.util.ocr.AdjacentGenderSymbolDetector
import com.pokerarity.scanner.util.ocr.AnchoredScreenText
import com.pokerarity.scanner.util.ocr.ExtractionContext
import com.pokerarity.scanner.util.ocr.MLKitOcrProvider
import com.pokerarity.scanner.util.ocr.NameCropOcrRecovery
import com.pokerarity.scanner.util.ocr.NameCropOcrRecovery.Request
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.VisualGenderMarker
import com.pokerarity.scanner.util.ocr.acceptedSpeciesOrNull
import java.io.File
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class NameCropOcrRecoveryGenderTest {
    private val parser = TextParser(ApplicationProvider.getApplicationContext<Context>()).also { parser ->
        val names = speciesFile().reader().use { reader ->
            Gson().fromJson<List<String>>(reader, object : TypeToken<List<String>>() {}.type)
        }
        TextParser::class.java.getDeclaredField("pokemonNames").apply { isAccessible = true }
            .set(parser, names.map { it.lowercase(Locale.ROOT) })
    }

    @Test
    fun visualMaleMarkerRecoversNidoranWhenOcrDropsTheTitleGlyph() = runBlocking {
        val result = recover(VisualGenderMarker.Male, drawMarker = ::drawMaleMarker)

        assertEquals("Nidoran-m", result.fields.name?.acceptedSpeciesOrNull())
        assertEquals("recovered", result.diagnostic?.status)
        assertTrue(result.diagnostic?.reasons.orEmpty().contains("visual_gender_marker_male"))
    }

    @Test
    fun visualFemaleMarkerRecoversNidoranWhenOcrDropsTheTitleGlyph() = runBlocking {
        val result = recover(VisualGenderMarker.Female, drawMarker = ::drawFemaleMarker)

        assertEquals("Nidoran-f", result.fields.name?.acceptedSpeciesOrNull())
        assertEquals("recovered", result.diagnostic?.status)
        assertTrue(result.diagnostic?.reasons.orEmpty().contains("visual_gender_marker_female"))
    }

    @Test
    fun missingOrPencilLikeMarkerKeepsNidoranUncertain() = runBlocking {
        val missing = recover(expectedMarker = null, drawMarker = { _, _ -> })
        val pencil = recover(expectedMarker = null, drawMarker = ::drawPencilLikeStroke)

        assertNull(missing.fields.name?.acceptedSpeciesOrNull())
        assertNull(pencil.fields.name?.acceptedSpeciesOrNull())
        assertEquals("unresolved", missing.diagnostic?.status)
        assertEquals("unresolved", pencil.diagnostic?.status)
    }

    private suspend fun recover(
        expectedMarker: VisualGenderMarker?,
        drawMarker: (Canvas, Rect) -> Unit
    ): NameCropOcrRecovery.Result {
        val titleBounds = Rect(TITLE_LEFT, TITLE_TOP, TITLE_RIGHT, TITLE_BOTTOM)
        val bitmap = Bitmap.createBitmap(FRAME_WIDTH, FRAME_HEIGHT, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            drawMarker(canvas, titleBounds)

            val lines = listOf(
                block("CP 424", 430, 210, 650, 260),
                block("Nidoran", TITLE_LEFT, TITLE_TOP, TITLE_RIGHT, TITLE_BOTTOM),
                block("80 / 80 HP", 400, 790, 680, 830),
                block("POISON", 435, 935, 645, 965),
                block("WEIGHT", 100, 985, 290, 1020),
                block("HEIGHT", 790, 985, 980, 1020),
                block("POWER UP", 130, 1370, 410, 1420)
            )
            val cost = block("1,000", 590, 1370, 720, 1420)
            val layout = MLKitOcrProvider.Layout(lines, listOf(cost))
            val bar = Rect(310, 745, 770, 758)
            val context = ExtractionContext(bar = bar, nameBand = Rect(250, 600, 830, 800))
            val fields = AnchoredScreenText.extract(layout, parser, FRAME_WIDTH, FRAME_HEIGHT, context)
            assertMarkerDetection(bitmap, titleBounds, fields.nameTextBounds, context.nameBand, expectedMarker)
            assertTrue("Fixture must reach the bounded name fallback", NameCropOcrRecovery.shouldAttempt(fields))

            val crop = requireNotNull(fields.nameRect)
            val cropTitle = Rect(
                TITLE_LEFT - crop.left,
                TITLE_TOP - crop.top,
                TITLE_RIGHT - crop.left,
                TITLE_BOTTOM - crop.top
            )
            return NameCropOcrRecovery.recover(
                Request(
                    bitmap = bitmap,
                    layout = layout,
                    fields = fields,
                    parser = parser,
                    extractionContext = context,
                    recognizeLayout = {
                        MLKitOcrProvider.Layout(
                            lines = listOf(
                                MLKitOcrProvider.RecognizedBlock("Nidoran", cropTitle)
                            ),
                            elements = emptyList()
                        )
                    }
                )
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertMarkerDetection(
        bitmap: Bitmap,
        drawnTitleBounds: Rect,
        extractedTitleBounds: Rect?,
        nameBand: Rect?,
        expectedMarker: VisualGenderMarker?
    ) {
        assertEquals(
            "The raster detector must classify the drawn marker",
            expectedMarker,
            AdjacentGenderSymbolDetector.detect(bitmap, drawnTitleBounds, nameBand)
        )
        assertEquals(
            "The extracted title bounds must preserve marker position",
            expectedMarker,
            AdjacentGenderSymbolDetector.detect(bitmap, extractedTitleBounds, nameBand)
        )
    }

    private fun drawMaleMarker(canvas: Canvas, title: Rect) {
        val height = title.height().toFloat()
        val left = title.right.toFloat()
        val top = title.top.toFloat()
        val paint = markerPaint(height)
        val centerX = left + height * 0.26f
        val centerY = top + height * 0.68f
        val radius = height * 0.19f
        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.drawLine(centerX + radius * 0.62f, centerY - radius * 0.62f,
            left + height * 0.84f, top + height * 0.10f, paint)
        canvas.drawLine(left + height * 0.84f, top + height * 0.10f,
            left + height * 0.56f, top + height * 0.10f, paint)
        canvas.drawLine(left + height * 0.84f, top + height * 0.10f,
            left + height * 0.84f, top + height * 0.39f, paint)
    }

    private fun drawFemaleMarker(canvas: Canvas, title: Rect) {
        val height = title.height().toFloat()
        val left = title.right.toFloat()
        val top = title.top.toFloat()
        val paint = markerPaint(height)
        val centerX = left + height * 0.47f
        val centerY = top + height * 0.29f
        val radius = height * 0.20f
        canvas.drawCircle(centerX, centerY, radius, paint)
        canvas.drawLine(centerX, centerY + radius, centerX, top + height * 0.83f, paint)
        canvas.drawLine(left + height * 0.29f, top + height * 0.83f,
            left + height * 0.65f, top + height * 0.83f, paint)
    }

    private fun drawPencilLikeStroke(canvas: Canvas, title: Rect) {
        val height = title.height().toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARKER_COLOR
            strokeWidth = height * 0.08f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(title.right + height * 0.05f, title.bottom.toFloat(),
            title.right + height * 0.72f, title.top.toFloat(), paint)
    }

    private fun markerPaint(height: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MARKER_COLOR
        style = Paint.Style.STROKE
        strokeWidth = height * 0.075f
        strokeCap = Paint.Cap.ROUND
    }

    private fun block(text: String, left: Int, top: Int, right: Int, bottom: Int) =
        MLKitOcrProvider.RecognizedBlock(text, Rect(left, top, right, bottom))

    private fun speciesFile(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            File(directory, "app/src/main/assets/data/pokemon_names.json")
                .takeIf(File::isFile)?.let { return it }
            directory = directory.parentFile ?: return@repeat
        }
        error("pokemon_names.json not found")
    }

    private companion object {
        const val FRAME_WIDTH = 1080
        const val FRAME_HEIGHT = 2340
        const val TITLE_LEFT = 320
        const val TITLE_TOP = 660
        const val TITLE_RIGHT = 550
        const val TITLE_BOTTOM = 725
        const val MARKER_COLOR = 0xff477070.toInt()
    }
}
