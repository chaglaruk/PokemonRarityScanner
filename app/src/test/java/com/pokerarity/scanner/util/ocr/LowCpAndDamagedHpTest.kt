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

    @Test fun invalidExplicitHpIsNotRepairedIntoAnotherPair() {
        for (text in listOf("52 / 51 HP", "0 / 0 HP", "7227 / 227 HP", "51/51 HP 60/60 HP")) {
            assertNull(TextParseUtils.parseExactHPPair(text))
        }
    }
}
