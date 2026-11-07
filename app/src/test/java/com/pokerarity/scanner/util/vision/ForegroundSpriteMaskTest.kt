package com.pokerarity.scanner.util.vision

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundSpriteMaskTest {
    @Test
    fun segment_selectsCenteredModelAndDropsEdgeParticles() {
        val width = 64
        val height = 80
        val pixels = IntArray(width * height) { 0xFF765A92.toInt() }
        for (y in 4 until height step 12) {
            for (x in 3 until width step 13) {
                if ((x + y) % 3 == 0) pixels[y * width + x] = 0xFF52B8D0.toInt()
            }
        }
        for (y in 18 until 62) {
            for (x in 18 until 46) {
                val dx = (x - 32) / 14f
                val dy = (y - 40) / 22f
                if (dx * dx + dy * dy <= 1f) pixels[y * width + x] = 0xFF59A86B.toInt()
            }
        }
        for (y in 22 until 35) for (x in 25 until 39) {
            pixels[y * width + x] = 0xFFE8E7D8.toInt()
        }

        val result = ForegroundSpriteMask.segment(pixels, width, height)

        assertNotNull(result)
        val mask = requireNotNull(result).pixels
        val modelRetained = (25 until 55).sumOf { y -> (21 until 43).count { x -> mask[y * width + x] } }
        val edgeRetained = (0 until height).sumOf { y ->
            (0 until width).count { x -> (x < 6 || x >= width - 6 || y < 6 || y >= height - 6) && mask[y * width + x] }
        }
        assertTrue("centered model should be retained", modelRetained > 300)
        assertTrue("distant particles should be mostly excluded", edgeRetained < 80)
    }

    @Test
    fun segment_returnsUnsupportedForUniformBackground() {
        val pixels = IntArray(64 * 80) { 0xFF765A92.toInt() }

        assertNull(ForegroundSpriteMask.segment(pixels, 64, 80))
    }
}

class ShinyForegroundDecisionGateTest {
    @Test
    fun verifiesOnlyReferenceBackedSignatureOrColorPositives() {
        assertTrue(ShinyForegroundDecisionGate.shouldVerify(true, true, false, false, "multi_view_signature_consensus"))
        assertTrue(
            ShinyForegroundDecisionGate.shouldVerify(
                true,
                true,
                false,
                false,
                "strong_primary_signature_preserved"
            )
        )
        assertTrue(ShinyForegroundDecisionGate.shouldVerify(true, true, false, false, "supported_color_fallback"))
        assertFalse(ShinyForegroundDecisionGate.shouldVerify(true, true, true, false, "multi_view_signature_consensus"))
        assertFalse(ShinyForegroundDecisionGate.shouldVerify(true, true, false, true, "multi_view_signature_consensus"))
        assertFalse(ShinyForegroundDecisionGate.shouldVerify(true, false, false, false, "supported_color_fallback"))
        assertFalse(ShinyForegroundDecisionGate.shouldVerify(false, true, false, false, "supported_color_fallback"))
        assertFalse(
            ShinyForegroundDecisionGate.shouldVerify(
                true,
                true,
                false,
                false,
                "neutral_color_ambiguous_signature"
            )
        )
    }

    @Test
    fun vetoesOnlyWhenSupportedForegroundReferenceRejectsPositive() {
        assertTrue(
            ShinyForegroundDecisionGate.shouldRejectPositive(
                ForegroundShinyVerification(true, matched = false, reason = "shiny_reference_not_closer")
            )
        )
        assertFalse(
            ShinyForegroundDecisionGate.shouldRejectPositive(
                ForegroundShinyVerification(true, matched = true, reason = "signature_threshold_match")
            )
        )
        assertFalse(
            ShinyForegroundDecisionGate.shouldRejectPositive(
                ForegroundShinyVerification(false, reason = "foreground_mask_unavailable")
            )
        )
        assertFalse(ShinyForegroundDecisionGate.shouldRejectPositive(null))
    }

    @Test
    fun rejectsColorFallbackWhenForegroundVerificationCannotConfirmSprite() {
        val unavailable = ForegroundShinyVerification(false, reason = "foreground_mask_unavailable")
        val rejectedReference = ForegroundShinyVerification(
            true,
            matched = false,
            reason = "shiny_reference_not_closer"
        )
        val confirmed = ForegroundShinyVerification(
            true,
            matched = true,
            reason = "signature_threshold_match"
        )

        assertTrue(ShinyForegroundDecisionGate.shouldRejectPositive(unavailable, "supported_color_fallback"))
        assertTrue(
            ShinyForegroundDecisionGate.shouldRejectPositive(
                rejectedReference,
                "supported_color_fallback"
            )
        )
        assertFalse(ShinyForegroundDecisionGate.shouldRejectPositive(confirmed, "supported_color_fallback"))
        assertFalse(ShinyForegroundDecisionGate.shouldRejectPositive(unavailable, "strong_primary_signature_preserved"))
    }
}
