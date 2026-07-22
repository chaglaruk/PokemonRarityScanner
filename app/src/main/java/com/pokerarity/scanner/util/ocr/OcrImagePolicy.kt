package com.pokerarity.scanner.util.ocr

private const val SCALE_PERCENT_BASE = 100L

internal enum class OcrPolicy(val code: String) {
    BASELINE_900_WIDTH("baseline_900_width"),
    NATIVE_CROP_FIRST("native_crop_first"),
    BOUNDED_CROP_UPSCALE("bounded_crop_upscale")
}

internal enum class OcrField(val code: String) {
    NAME("name"),
    CANDY("candy"),
    CP("cp"),
    HP("hp"),
    OTHER("other")
}

internal enum class OcrCropOrder(val code: String) {
    RESIZE_FULL_FRAME_THEN_CROP("resize_full_frame_then_crop"),
    CROP_NATIVE_THEN_PROCESS("crop_native_then_process"),
    CROP_NATIVE_THEN_BOUNDED_UPSCALE("crop_native_then_bounded_upscale")
}

internal data class OcrImageSize(val width: Int, val height: Int) {
    init {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
    }
}

/** Explicit caller-owned bounds; no production instance is provided. */
internal data class OcrUpscaleBounds(
    val maxScalePercent: Int,
    val maxOutputWidth: Int,
    val maxOutputHeight: Int
) {
    init {
        require(maxScalePercent.toLong() >= SCALE_PERCENT_BASE) { "Maximum scale must be at least native size" }
        require(maxOutputWidth > 0 && maxOutputHeight > 0) { "Output bounds must be positive" }
    }
}

internal data class OcrImagePolicyPlan(
    val requestedPolicy: OcrPolicy,
    val effectivePolicy: OcrPolicy,
    val field: OcrField,
    val sourceFrame: OcrImageSize,
    val effectiveFrame: OcrImageSize,
    val cropOrder: OcrCropOrder,
    val requiresExplicitUpscaleBounds: Boolean,
    val forcedBackToBaseline: Boolean,
    val reasonCodes: List<String>
)

internal object OcrImagePolicy {
    private const val BASELINE_WIDTH = 900

    fun plan(
        requestedPolicy: OcrPolicy,
        field: OcrField,
        sourceFrame: OcrImageSize,
        upscaleBounds: OcrUpscaleBounds? = null
    ): OcrImagePolicyPlan {
        val baseline = baselineDimensions(sourceFrame)
        return when (requestedPolicy) {
            OcrPolicy.BASELINE_900_WIDTH -> baselinePlan(
                requestedPolicy, field, sourceFrame, baseline, BaselineMetadata("baseline_selected")
            )
            OcrPolicy.NATIVE_CROP_FIRST -> when (field) {
                OcrField.NAME, OcrField.CANDY -> OcrImagePolicyPlan(
                    requestedPolicy, requestedPolicy, field, sourceFrame, sourceFrame,
                    OcrCropOrder.CROP_NATIVE_THEN_PROCESS, false, false, listOf("native_name_candy")
                )
                else -> lockedBaselinePlan(requestedPolicy, field, sourceFrame, baseline)
            }
            OcrPolicy.BOUNDED_CROP_UPSCALE -> when (field) {
                OcrField.NAME, OcrField.CANDY -> if (upscaleBounds == null) {
                    baselinePlan(
                        requestedPolicy, field, sourceFrame, baseline,
                        BaselineMetadata("bounded_configuration_required", requiresBounds = true, forced = true)
                    )
                } else {
                    OcrImagePolicyPlan(
                        requestedPolicy, requestedPolicy, field, sourceFrame, sourceFrame,
                        OcrCropOrder.CROP_NATIVE_THEN_BOUNDED_UPSCALE, true, false, listOf("bounded_name_candy")
                    )
                }
                else -> lockedBaselinePlan(requestedPolicy, field, sourceFrame, baseline)
            }
        }
    }

    fun baselineDimensions(sourceFrame: OcrImageSize): OcrImageSize =
        if (sourceFrame.width > BASELINE_WIDTH) {
            OcrImageSize(
                BASELINE_WIDTH,
                (sourceFrame.height * (BASELINE_WIDTH.toFloat() / sourceFrame.width)).toInt()
            )
        } else {
            sourceFrame
        }

    fun boundedCropDimensions(crop: OcrImageSize, bounds: OcrUpscaleBounds): OcrImageSize {
        val maxWidthPercent = (bounds.maxOutputWidth.toLong() * SCALE_PERCENT_BASE) / crop.width
        val maxHeightPercent = (bounds.maxOutputHeight.toLong() * SCALE_PERCENT_BASE) / crop.height
        val scalePercent = minOf(bounds.maxScalePercent.toLong(), maxWidthPercent, maxHeightPercent)
        require(scalePercent >= SCALE_PERCENT_BASE) { "Bounds must preserve the native crop" }
        val width = checkedDimension(crop.width.toLong() * scalePercent / SCALE_PERCENT_BASE)
        val height = checkedDimension(crop.height.toLong() * scalePercent / SCALE_PERCENT_BASE)
        return OcrImageSize(width, height)
    }

    private fun baselinePlan(
        requestedPolicy: OcrPolicy,
        field: OcrField,
        sourceFrame: OcrImageSize,
        baseline: OcrImageSize,
        metadata: BaselineMetadata
    ) = OcrImagePolicyPlan(
        requestedPolicy, OcrPolicy.BASELINE_900_WIDTH, field, sourceFrame, baseline,
        OcrCropOrder.RESIZE_FULL_FRAME_THEN_CROP,
        metadata.requiresBounds,
        metadata.forced,
        listOf(metadata.reason)
    )

    private fun lockedBaselinePlan(
        requestedPolicy: OcrPolicy,
        field: OcrField,
        sourceFrame: OcrImageSize,
        baseline: OcrImageSize
    ) = baselinePlan(
        requestedPolicy,
        field,
        sourceFrame,
        baseline,
        BaselineMetadata(
            if (field == OcrField.CP || field == OcrField.HP) {
                "numeric_field_baseline_locked"
            } else {
                "other_field_baseline_locked"
            },
            forced = true
        )
    )

    private fun checkedDimension(value: Long): Int {
        require(value in 1L..Int.MAX_VALUE.toLong()) { "Output dimension is out of range" }
        return value.toInt()
    }

    private data class BaselineMetadata(
        val reason: String,
        val requiresBounds: Boolean = false,
        val forced: Boolean = false
    )
}
