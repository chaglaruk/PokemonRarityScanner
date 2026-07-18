package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.util.ocr.SpeciesNameAcceptanceSource
import com.pokerarity.scanner.util.ocr.SpeciesNameDecision
import com.pokerarity.scanner.util.ocr.TextParser
import com.pokerarity.scanner.util.ocr.acceptedSpeciesOrNull
import com.pokerarity.scanner.util.ocr.decideDynamicOcrSpeciesName
import com.pokerarity.scanner.util.ocr.decideStaticOcrSpeciesName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SpeciesNameDecisionTest {
    private val parser = TextParser(ApplicationProvider.getApplicationContext<Context>())
    private val canonical = loadCanonicalSpecies().also(::injectCanonicalSpecies)

    @Test
    fun exactCanonicalAndReviewedNormalizationsAreAcceptedWithProvenance() {
        canonical.forEach { species ->
            assertAccepted(species, species, SpeciesNameAcceptanceSource.EXACT_CANONICAL)
        }
        listOf("1", "12", "123", "2024").forEach { suffix ->
            assertAccepted("Gyarados$suffix", "Gyarados", SpeciesNameAcceptanceSource.REVIEWED_ALIAS)
        }
        assertAccepted("Poryg0n", "Porygon", SpeciesNameAcceptanceSource.REVIEWED_ALIAS)
    }

    @Test
    fun canonicalNumericSuffixesResolveAcrossEverySelectablePath() {
        val suffixes = listOf("1", "12", "123", "2024")
        canonical.forEach { species ->
            suffixes.forEach { suffix ->
                val observation = species + suffix
                val decision = parser.decideSpeciesName(observation)
                assertTrue(
                    "Expected Accepted for $observation, got $decision",
                    decision is SpeciesNameDecision.Accepted
                )
                decision as SpeciesNameDecision.Accepted
                assertEquals(SpeciesNameAcceptanceSource.REVIEWED_ALIAS, decision.source)
                assertSpecies(observation, species, decision.species)
                assertSpecies(observation, species, parser.parseName(observation))
                assertSpecies(observation, species, parser.parseStrongSpeciesName(observation))
                val dynamic = parser.decideDynamicOcrSpeciesName(observation).acceptedSpeciesOrNull()
                val static = parser.decideStaticOcrSpeciesName(observation).acceptedSpeciesOrNull()
                assertSpecies(observation, species, dynamic)
                assertSpecies(observation, species, static)
            }
        }

        mapOf(
            "Nidoran-f2020" to "Nidoran-f",
            "Nidoran-m2020" to "Nidoran-m",
            "Porygon21" to "Porygon2",
            "Porygon212" to "Porygon2",
            "Porygon2123" to "Porygon2",
            "Porygon22024" to "Porygon2"
        ).forEach { (observation, species) -> assertAccepted(
            observation,
            species,
            SpeciesNameAcceptanceSource.REVIEWED_ALIAS
        ) }
    }

    @Test
    fun reviewedHoOhNormalizationsAreAccepted() {
        listOf("HoOh", "Ho Oh", "H0-0h").forEach { observation ->
            assertAccepted(observation, "Ho-Oh", SpeciesNameAcceptanceSource.REVIEWED_ALIAS)
        }
    }

    @Test
    fun ambiguousAndNicknameLikeInputsRemainUncertain() {
        listOf("Nidoran", "Nidorano", "Nidoranp", "Rocky", "Luna", "Sparky", "King", "Fluffy").forEach { observation ->
            assertTrue(
                "Expected Uncertain for $observation",
                parser.decideSpeciesName(observation) is SpeciesNameDecision.Uncertain
            )
        }
        assertAccepted("nidoran-f", "Nidoran-f", SpeciesNameAcceptanceSource.EXACT_CANONICAL)
        assertAccepted("nidoran-m", "Nidoran-m", SpeciesNameAcceptanceSource.EXACT_CANONICAL)
    }

    @Test
    fun onlyUniqueStructuredDistanceOneRecoveryIsAccepted() {
        assertAccepted("Poliwrat", "Poliwrath", SpeciesNameAcceptanceSource.SAFE_FUZZY)
        assertAccepted("metapo", "Metapod", SpeciesNameAcceptanceSource.SAFE_FUZZY)
        assertEquals("Poliwrath", parser.rankNameCandidates("Poliwrat").first().name)
        assertEquals("Metapod", parser.rankNameCandidates("metapo").first().name)
    }

    @Test
    fun noMatchAndUncertainNeverBecomeSelectedSpecies() {
        listOf("", "WEATHER BONUS", "CP 1500").forEach { observation ->
            val decision = parser.decideSpeciesName(observation)
            assertTrue("Expected NoMatch for $observation", decision is SpeciesNameDecision.NoMatch)
            assertEquals(null, decision.acceptedSpeciesOrNull())
        }
        assertEquals(null, parser.decideSpeciesName("Nidoran").acceptedSpeciesOrNull())
    }

    @Test
    fun collisionFilteredDeterministicCorpusHasZeroAcceptedWrongSpecies() {
        val transforms = listOf<(String) -> String>(
            { it.dropLast(1) },
            { it.dropLast(2) },
            { it.removeRange(it.length / 2, (it.length / 2) + 1) },
            { value ->
                val index = value.length - 2
                val replacement = when (val char = value[index]) {
                    in 'a'..'y', in 'A'..'Y' -> char + 1
                    'z' -> 'a'
                    'Z' -> 'A'
                    else -> char
                }
                value.replaceRange(index, index + 1, replacement.toString())
            },
            { value ->
                val left = (value.length - 1) / 2
                value.toCharArray().also { chars ->
                    val next = left + 1
                    val current = chars[left]
                    chars[left] = chars[next]
                    chars[next] = current
                }.concatToString()
            },
            { it + "2024" }
        )
        val canonicalByKey = canonical.associateBy(::collisionKey)
        val wrong = transforms.flatMap { transform ->
            val generated = canonical.map { source -> source to transform(source) }
            val collidingObservations = generated
                .filter { (_, observation) -> observation.length >= 3 }
                .groupBy { collisionKey(it.second) }
                .filterValues { values -> values.map(Pair<String, String>::first).distinct().size > 1 }
                .keys
            generated.mapNotNull { (source, observation) ->
                val key = collisionKey(observation)
                if (observation == source || observation.length < 3) return@mapNotNull null
                if (key in collidingObservations) return@mapNotNull null
                if (canonicalByKey[key]?.equals(source, ignoreCase = true) == false) return@mapNotNull null
                val selected = parser.decideSpeciesName(observation).acceptedSpeciesOrNull()
                "$source <- $observation -> $selected".takeIf {
                    selected != null && !selected.equals(source, ignoreCase = true)
                }
            }
        }
        assertTrue("Accepted wrong decisions: ${wrong.take(20)}", wrong.isEmpty())
    }

    private fun assertAccepted(observation: String, species: String, source: SpeciesNameAcceptanceSource) {
        val decision = parser.decideSpeciesName(observation)
        assertTrue("Expected Accepted for $observation, got $decision", decision is SpeciesNameDecision.Accepted)
        decision as SpeciesNameDecision.Accepted
        assertTrue("Expected $species, got ${decision.species}", species.equals(decision.species, ignoreCase = true))
        assertEquals(source, decision.source)
        assertTrue(species.equals(decision.acceptedSpeciesOrNull(), ignoreCase = true))
    }

    private fun assertSpecies(observation: String, expected: String, actual: String?) {
        assertTrue("Expected $expected for $observation, got $actual", expected.equals(actual, ignoreCase = true))
    }

    private fun loadCanonicalSpecies(): List<String> = speciesFile().reader().use { reader ->
        Gson().fromJson(reader, object : TypeToken<List<String>>() {}.type)
    }

    private fun injectCanonicalSpecies(species: List<String>) {
        TextParser::class.java.getDeclaredField("pokemonNames").apply { isAccessible = true }
            .set(parser, species.map { it.lowercase(Locale.ROOT) })
    }

    private fun speciesFile(): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            File(directory, "app/src/main/assets/data/pokemon_names.json").takeIf(File::isFile)?.let { return it }
            directory = directory.parentFile ?: return@repeat
        }
        error("pokemon_names.json not found")
    }

    private fun collisionKey(value: String): String = value.trim().lowercase(Locale.ROOT)
}
