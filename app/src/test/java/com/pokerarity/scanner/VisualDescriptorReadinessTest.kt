package com.pokerarity.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VisualDescriptorReadinessTest {

    @Test
    fun runtimePrototypeModelHasDescriptorRecords() {
        val asset = listOf(
            File("app/src/main/assets/data/variant_classifier_model.json"),
            File("src/main/assets/data/variant_classifier_model.json")
        ).first { it.exists() }
        val json = asset.readText()
        val entryCount = Regex(""""entryCount"\s*:\s*(\d+)""").find(json)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
            ?: 0

        assertTrue(entryCount > 0)
        assertFalse(Regex(""""aHash"\s*:\s*"[0-9a-fA-F]+"""").find(json)?.value.orEmpty().isBlank())
        assertFalse(Regex(""""dHash"\s*:\s*"[0-9a-fA-F]+"""").find(json)?.value.orEmpty().isBlank())
        assertTrue(json.contains(""""bodyHist""""))
    }
}
