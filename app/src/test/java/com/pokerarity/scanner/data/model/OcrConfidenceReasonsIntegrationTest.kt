package com.pokerarity.scanner.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Date

class OcrConfidenceReasonsIntegrationTest {

    @Test
    fun pokemonData_copy_preservesConfidenceReasons() {
        val builder = OcrConfidenceReasonsBuilder()
            .withCp(1234)
            .withHp(100, 100)
            .withCaughtDate(Date())
        val reasons = builder.build()

        val original = PokemonData(
            cp = 1234,
            hp = 100,
            maxHp = 100,
            name = "Test",
            realName = "Test",
            candyName = null,
            megaEnergy = null,
            weight = null,
            height = null,
            stardust = 200,
            caughtDate = Date(),
            ocrConfidenceReasons = reasons
        )

        val copied = original.copy(cp = 1235) // arbitrary mutation

        assertNotNull(copied.ocrConfidenceReasons)
        assertEquals(reasons, copied.ocrConfidenceReasons)
        assertEquals(1, copied.ocrConfidenceReasons?.forField(OcrField.CP)?.reasonCodes?.size)
    }
}
