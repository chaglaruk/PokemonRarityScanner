package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrImagePolicyTest {

    @Test
    fun baselineUsesTheExisting900WidthFormulaForWideFrames() {
        assertEquals(OcrImageSize(900, 1950), baseline(OcrImageSize(1080, 2340)))
        assertEquals(OcrImageSize(900, 1950), baseline(OcrImageSize(1440, 3120)))
    }

    @Test
    fun baselineLeavesFramesAtOrBelow900WideUnchangedForEveryField() {
        OcrField.entries.forEach { field ->
            assertEquals(OcrImageSize(900, 1950), baseline(OcrImageSize(900, 1950), field))
            assertEquals(OcrImageSize(720, 1600), baseline(OcrImageSize(720, 1600), field))
        }
    }

    @Test
    fun nativeCropFirstIsLimitedToNameAndCandy() {
        listOf(OcrField.NAME, OcrField.CANDY).forEach { field ->
            val plan = OcrImagePolicy.plan(OcrPolicy.NATIVE_CROP_FIRST, field, OcrImageSize(1440, 3120))
            assertEquals(OcrPolicy.NATIVE_CROP_FIRST, plan.effectivePolicy)
            assertEquals(OcrCropOrder.CROP_NATIVE_THEN_PROCESS, plan.cropOrder)
            assertEquals(OcrImageSize(1440, 3120), plan.effectiveFrame)
            assertTrue(plan.reasonCodes.contains("native_name_candy"))
        }
    }

    @Test
    fun nativeCropFirstKeepsNumericFieldsBaselineLocked() {
        listOf(OcrField.CP, OcrField.HP).forEach { field ->
            val plan = OcrImagePolicy.plan(OcrPolicy.NATIVE_CROP_FIRST, field, OcrImageSize(1440, 3120))
            assertEquals(OcrPolicy.BASELINE_900_WIDTH, plan.effectivePolicy)
            assertTrue(plan.forcedBackToBaseline)
            assertTrue(plan.reasonCodes.contains("numeric_field_baseline_locked"))
        }
    }

    @Test
    fun boundedPolicyRequiresExplicitConfigurationForNameAndCandy() {
        listOf(OcrField.NAME, OcrField.CANDY).forEach { field ->
            val plan = OcrImagePolicy.plan(OcrPolicy.BOUNDED_CROP_UPSCALE, field, OcrImageSize(1080, 2340))
            assertEquals(OcrPolicy.BASELINE_900_WIDTH, plan.effectivePolicy)
            assertTrue(plan.requiresExplicitUpscaleBounds)
            assertTrue(plan.forcedBackToBaseline)
            assertTrue(plan.reasonCodes.contains("bounded_configuration_required"))
        }
    }

    @Test
    fun boundedPolicyKeepsNumericFieldsBaselineLocked() {
        listOf(OcrField.CP, OcrField.HP).forEach { field ->
            val plan = OcrImagePolicy.plan(OcrPolicy.BOUNDED_CROP_UPSCALE, field, OcrImageSize(1440, 3120))
            assertEquals(OcrPolicy.BASELINE_900_WIDTH, plan.effectivePolicy)
            assertTrue(plan.reasonCodes.contains("numeric_field_baseline_locked"))
        }
    }

    @Test
    fun explicitTestOnlyBoundsRespectScaleAndOutputCaps() {
        val bounds = OcrUpscaleBounds(maxScalePercent = 250, maxOutputWidth = 250, maxOutputHeight = 60)
        assertEquals(OcrImageSize(240, 60), OcrImagePolicy.boundedCropDimensions(OcrImageSize(120, 30), bounds))
    }

    @Test
    fun invalidDimensionsAndBoundsAreRejectedAndPlanningIsDeterministic() {
        assertThrows(IllegalArgumentException::class.java) { OcrImageSize(0, 1) }
        assertThrows(IllegalArgumentException::class.java) { OcrUpscaleBounds(99, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { OcrUpscaleBounds(100, 0, 1) }
        assertThrows(IllegalArgumentException::class.java) { OcrUpscaleBounds(100, 1, 0) }
        assertThrows(IllegalArgumentException::class.java) {
            OcrImagePolicy.boundedCropDimensions(OcrImageSize(120, 30), OcrUpscaleBounds(100, 119, 30))
        }

        val first = OcrImagePolicy.plan(OcrPolicy.NATIVE_CROP_FIRST, OcrField.NAME, OcrImageSize(1080, 2340))
        val second = OcrImagePolicy.plan(OcrPolicy.NATIVE_CROP_FIRST, OcrField.NAME, OcrImageSize(1080, 2340))
        assertEquals(first, second)
        assertTrue(
            OcrImagePolicy.plan(
                OcrPolicy.BOUNDED_CROP_UPSCALE,
                OcrField.NAME,
                OcrImageSize(1080, 2340)
            ).requiresExplicitUpscaleBounds
        )
    }

    @Test
    fun reasonCodesAreStable() {
        assertEquals(
            listOf("baseline_selected"),
            baselinePlan(OcrImageSize(1080, 2340), OcrField.OTHER).reasonCodes
        )
        assertEquals(
            listOf("other_field_baseline_locked"),
            OcrImagePolicy.plan(OcrPolicy.NATIVE_CROP_FIRST, OcrField.OTHER, OcrImageSize(1080, 2340)).reasonCodes
        )
        assertEquals(
            listOf("bounded_name_candy"),
            OcrImagePolicy.plan(
                OcrPolicy.BOUNDED_CROP_UPSCALE,
                OcrField.NAME,
                OcrImageSize(1080, 2340),
                OcrUpscaleBounds(200, 400, 400)
            ).reasonCodes
        )
    }

    private fun baseline(size: OcrImageSize, field: OcrField = OcrField.NAME): OcrImageSize =
        baselinePlan(size, field).effectiveFrame

    private fun baselinePlan(size: OcrImageSize, field: OcrField): OcrImagePolicyPlan =
        OcrImagePolicy.plan(OcrPolicy.BASELINE_900_WIDTH, field, size)
}
