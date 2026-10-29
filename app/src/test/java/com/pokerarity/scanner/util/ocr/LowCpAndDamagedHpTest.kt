package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LowCpAndDamagedHpTest {
    @Test fun explicitLowCpIsSupported() {
        for (cp in listOf(10, 25, 99, 100)) {
            assertEquals(cp.toString(), FieldCandidateNormalizer.normalizeCp("CP $cp").parsedValue)
        }
    }

    @Test fun damagedAndFaintedHpPreserveMaximum() {
        for (current in listOf(0, 1, 9, 10, 51)) {
            assertEquals(current to 51, TextParseUtils.parseExactHPPair("$current / 51 HP"))
        }
    }

    @Test fun exactHpParserNormalizesDigitConfusablesWithoutAcceptingMalformedPairs() {
        val valid = mapOf(
            "9O / 9O HP" to (90 to 90),
            "I0 / I0 HP" to (10 to 10),
            "l0 / l0 HP" to (10 to 10),
            "5B / 5B HP" to (58 to 58),
            "B0 / B0 HP" to (80 to 80),
            "Z0 / Z0 HP" to (20 to 20),
            "|0 / |0 HP" to (10 to 10)
        )
        valid.forEach { (text, expected) ->
            assertEquals(text, expected, TextParseUtils.parseExactHPPair(text))
        }

        for (text in listOf("80 / ?? HP", "10 / 1000 HP", "80 / 80 HP 7")) {
            assertNull(text, TextParseUtils.parseExactHPPair(text))
        }
    }

    @Test fun invalidExplicitHpIsNotRepairedIntoAnotherPair() {
        for (text in listOf("52 / 51 HP", "0 / 0 HP", "7227 / 227 HP", "51/51 HP 60/60 HP")) {
            assertNull(TextParseUtils.parseExactHPPair(text))
        }
    }
}
