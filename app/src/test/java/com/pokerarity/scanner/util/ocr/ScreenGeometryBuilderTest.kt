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

    @Test
    fun bitmapOnlyBuildMatchesClassificationDrivenBuild() {
        val bitmap = ScreenClassifierTest.pokemonDetailBitmap(1080, 2400)

        val standalone = builder.build(bitmap)
        val classification = ScreenClassifier().classify(bitmap)

        assertEquals(standalone, builder.build(bitmap, classification))
    }

    @Test
    fun derivedNameBandMirrorsExtractorRatios() {
        // Measured corpus bar at 900x1950; the extractor's name band hangs below-left of
        // the bar top: left 0.12w, right 0.88w, top bar.top - 0.09h, bottom bar.top.
        val bar = Rect(226, 882, 674, 894)

        val band = requireNotNull(ScreenGeometryBuilder.deriveNameBand(bar, 900, 1950))

        assertEquals(Rect(108, 707, 792, 882), band)
        assertInside(900, 1950, band)
    }

    @Test
    fun derivedNameBandClampsToImageBounds() {
        val bar = Rect(226, 20, 674, 32) // near the top edge: band top would go negative

        val band = requireNotNull(ScreenGeometryBuilder.deriveNameBand(bar, 900, 1950))

        assertInside(900, 1950, band)
        assertEquals(bar.top, band.bottom)
        assertTrue(band.top < band.bottom)
    }

    @Test
    fun derivedNameBandFailsClosedOnInvalidInput() {
        assertEquals(null, ScreenGeometryBuilder.deriveNameBand(Rect(0, 0, 0, 0), 900, 1950))
        assertEquals(null, ScreenGeometryBuilder.deriveNameBand(Rect(226, 882, 674, 894), 0, 1950))
    }

    @Test
    fun detailCardRectExposesTheDetailAnchor() {
        val bitmap = ScreenClassifierTest.pokemonDetailBitmap(1080, 2400)

        val geometry = builder.build(bitmap)

        assertNotNull(geometry.detailCardRect)
        assertEquals(
            geometry.anchors.first { it.name == ScreenAnchorName.DetailCard }.rect,
            geometry.detailCardRect
        )
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
