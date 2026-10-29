package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pokerarity.scanner.util.ocr.SpeciesNameDecision
import com.pokerarity.scanner.util.ocr.TextParser
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SpeciesNameCorruptionStressTest {
    private val canonical = RecognitionSnapshotTestSupport.canonicalSpecies()
    private val parser = TextParser(ApplicationProvider.getApplicationContext<Context>(), canonical)

    @Test
    fun commonSingleCharacterOcrCorruptionsNeverAcceptAnotherCanonicalSpecies() {
        val exactCanonicalCollisions = linkedSetOf<String>()
        val corruptions = linkedMapOf(
            '0' to listOf('o', 'O'),
            'o' to listOf('0'),
            '1' to listOf('i', 'I', 'l', 'L'),
            'i' to listOf('1', 'l'),
            'l' to listOf('1', 'i'),
            '5' to listOf('s', 'S'),
            's' to listOf('5'),
            '8' to listOf('b', 'B'),
            'b' to listOf('8'),
            '2' to listOf('z', 'Z'),
            'z' to listOf('2')
        )
        val acceptedWrong = buildList {
            canonical.forEach { expected ->
                expected.forEachIndexed { index, original ->
                    corruptions[original.lowercaseChar()].orEmpty().forEach { replacement ->
                        val observation = expected.replaceRange(index, index + 1, replacement.toString())
                        val literalCanonical = canonical.firstOrNull {
                            it.equals(observation, ignoreCase = true)
                        }
                        if (literalCanonical != null &&
                            !literalCanonical.equals(expected, ignoreCase = true)
                        ) {
                            exactCanonicalCollisions += "$expected -> $observation -> $literalCanonical"
                        } else {
                            val decision = parser.decideSpeciesName(observation)
                            if (decision is SpeciesNameDecision.Accepted &&
                                !decision.species.equals(expected, ignoreCase = true)
                            ) {
                                add(
                                    "$expected <- $observation -> ${decision.species} (${decision.source})"
                                )
                            }
                        }
                    }
                }
            }
        }

        assertTrue(
            "Unexpected exact canonical collisions need explicit resolution: $exactCanonicalCollisions",
            exactCanonicalCollisions.isEmpty()
        )
        assertTrue("Accepted wrong OCR-corrupted species: ${acceptedWrong.take(20)}", acceptedWrong.isEmpty())
    }
}
