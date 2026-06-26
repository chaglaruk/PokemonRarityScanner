package com.pokerarity.scanner.util.ocr

import android.graphics.Bitmap
import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenGeometryBuilderTest {

    private val builder = ScreenGeometryBuilder()

    @Test
    fun cropsStayInsideImageBoundsForCommonResolutions() {
        val sizes = listOf(
            1080 to 2400,
            1080 to 2340,
            1440 to 3120,
            720 to 1600
        )

        sizes.forEach { (width, height) ->
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val geometry = builder.build(bitmap)

            geometry.crops.values.mapNotNull { it.rect }.forEach { rect ->
                assertInside(width, height, rect)
            }
        }
    }

    @Test
    fun returnsNonEmptyLegacyFallbackCropsForLowConfidenceScreen() {
        val bitmap = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)

        val geometry = builder.build(bitmap)

        assertEquals(ScreenType.Unknown, geometry.classification.screenType)
        assertTrue(geometry.fallbackReasons.isNotEmpty())
        assertEquals(CropProvenance.LegacyFallback, geometry.crop(ScreenField.CP)?.provenance)
        assertEquals(CropProvenance.LegacyFallback, geometry.crop(ScreenField.HP)?.provenance)
        assertEquals(CropProvenance.LegacyFallback, geometry.crop(ScreenField.Name)?.provenance)
        assertInside(1080, 2400, requireNotNull(geometry.crop(ScreenField.CP)?.rect))
    }

    @Test
    fun recordsAnchorDerivedProvenanceForStrongDetailScreen() {
        val bitmap = ScreenClassifierTest.pokemonDetailBitmap(1080, 2400)

        val geometry = builder.build(bitmap)

        assertEquals(geometry.classification.toString(), ScreenType.PokemonDetail, geometry.classification.screenType)
        assertTrue(geometry.anchors.any { it.name == ScreenAnchorName.DetailCard })
        assertEquals(CropProvenance.AnchorDerived, geometry.crop(ScreenField.CP)?.provenance)
        assertEquals(CropProvenance.AnchorDerived, geometry.crop(ScreenField.Name)?.provenance)
        assertEquals(CropProvenance.AnchorDerived, geometry.crop(ScreenField.DynamicName)?.provenance)
        assertNotNull(geometry.crop(ScreenField.Arc)?.rect)
    }

    @Test
    fun marksUnavailableAppraisalBarsWithoutInvalidRects() {
        val bitmap = ScreenClassifierTest.pokemonDetailBitmap(1080, 2400)

        val geometry = builder.build(bitmap)

        val attack = requireNotNull(geometry.crop(ScreenField.AppraisalAttack))
        assertEquals(CropProvenance.NotAvailable, attack.provenance)
        assertEquals(null, attack.rect)
    }

    private fun assertInside(width: Int, height: Int, rect: Rect) {
        assertTrue("left >= 0: $rect", rect.left >= 0)
        assertTrue("top >= 0: $rect", rect.top >= 0)
        assertTrue("right <= width: $rect", rect.right <= width)
        assertTrue("bottom <= height: $rect", rect.bottom <= height)
        assertTrue("non-empty width: $rect", rect.width() > 0)
        assertTrue("non-empty height: $rect", rect.height() > 0)
    }
}
