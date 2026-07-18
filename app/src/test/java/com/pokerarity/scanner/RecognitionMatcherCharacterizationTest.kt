package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
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
class RecognitionMatcherCharacterizationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val parser = TextParser(context)
    private val gson: Gson = GsonBuilder().serializeNulls().setPrettyPrinting().create()

    @Test
    fun currentRecognitionComparesWithCheckedInPr01Baseline() {
        val canonical = loadCanonicalSpecies()
        assertEquals(1011, canonical.size)
        assertTrue(canonical.none(String::isBlank))
        assertEquals(canonical.size, canonical.distinct().size)
        assertEquals(canonical, loadCanonicalSpecies())
        injectCanonicalSpecies(canonical)

        val report = buildReport(canonical)
        val json = gson.toJson(report) + "\n"
        val output = reportFile().apply {
            parentFile.mkdirs()
            writeText(json)
        }
        val expected = javaClass.classLoader
            .getResourceAsStream(BASELINE_RESOURCE)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Missing $BASELINE_RESOURCE; current deterministic report: ${output.absolutePath}")

        report.families.forEach { family ->
            family.paths.forEach { (path, counts) ->
                println("RECOGNITION ${family.name} $path ${counts.summaryLine()}")
            }
        }
        println("RECOGNITION dynamicStaticDisagreements=${report.dynamicStaticDisagreement.disagreements}")

        val baseline = gson.fromJson(expected, RecognitionReport::class.java)
        assertEquals(PR01_BASELINE_SOURCE_SHA, baseline.sourceMainSha)
        val approved = javaClass.classLoader
            .getResourceAsStream(APPROVED_COUNTS_RESOURCE)
            ?.bufferedReader()
            ?.use { gson.fromJson(it, ApprovedRecognitionSummary::class.java) }
            ?: error("Missing $APPROVED_COUNTS_RESOURCE")
        assertEquals(SOURCE_MAIN_SHA, approved.sourceMainSha)
        assertApprovedCounts(report, baseline, approved)
        val exactCanonical = report.families.single { it.name == "exact_canonical" }
        assertEquals(1011, exactCanonical.paths.getValue("parseName").acceptedCorrect)
        assertEquals(0, report.dynamicStaticDisagreement.disagreements)
    }

    private fun assertApprovedCounts(
        report: RecognitionReport,
        baseline: RecognitionReport,
        approved: ApprovedRecognitionSummary
    ) {
        report.families.forEach { family ->
            val before = baseline.families.single { it.name == family.name }
            val expectedFamily = approved.families.single { it.name == family.name }
            SELECTABLE_PATHS.forEach { path ->
                val beforeCounts = before.paths.getValue(path)
                val afterCounts = family.paths.getValue(path)
                val expectedCounts = if (path == "parseStrongSpeciesName") {
                    expectedFamily.strong
                } else {
                    expectedFamily.decision
                }
                println(
                    "PR01_COMPARISON ${family.name} $path " +
                        "beforeWrong=${beforeCounts.acceptedWrong} afterWrong=${afterCounts.acceptedWrong} " +
                        "beforeCorrect=${beforeCounts.acceptedCorrect} afterCorrect=${afterCounts.acceptedCorrect} " +
                        "afterUncertain=${afterCounts.uncertainNoMatch}"
                )
                assertEquals("Accepted wrong: ${family.name} $path", 0, afterCounts.acceptedWrong)
                assertEquals(
                    "Accepted correct: ${family.name} $path",
                    expectedCounts.acceptedCorrect,
                    afterCounts.acceptedCorrect
                )
                assertEquals(
                    "Uncertain/no match: ${family.name} $path",
                    expectedCounts.uncertainNoMatch,
                    afterCounts.uncertainNoMatch
                )
            }
        }
    }

    @Suppress("LongMethod")
    private fun buildReport(canonical: List<String>): RecognitionReport {
        val specs = familySpecs()
        val families = specs.map { prepareFamily(canonical, it) }
        val adversarial = adversarialObservations()
        val observations = buildSet {
            families.flatMapTo(this) { family -> family.evaluated.map(CandidateObservation::observation) }
            addAll(adversarial)
        }
        val selections = observations.sorted().associateWith(::selectAllPaths)
        val wrongExamples = mutableListOf<WrongExample>()

        val familyReports = families.map { family ->
            val pathCounts = linkedMapOf<String, OutcomeCounts>()
            PATHS.forEach { path ->
                var correct = 0
                var wrong = 0
                var uncertain = 0
                family.evaluated.forEach { candidate ->
                    val selected = selections.getValue(candidate.observation).get(path)
                    when {
                        selected == null -> uncertain++
                        selected.equals(candidate.source, ignoreCase = true) -> correct++
                        else -> {
                            wrong++
                            wrongExamples += WrongExample(
                                family = family.name,
                                path = path,
                                source = candidate.source,
                                observation = candidate.observation,
                                selected = selected
                            )
                        }
                    }
                }
                pathCounts[path] = OutcomeCounts(
                    generated = family.generated,
                    evaluated = family.evaluated.size,
                    acceptedCorrect = correct,
                    acceptedWrong = wrong,
                    uncertainNoMatch = uncertain
                )
            }
            FamilyReport(family.name, family.generated, family.evaluated.size, family.exclusions, pathCounts)
        }

        val adversarialResults = adversarial.map { observation ->
            AdversarialResult(observation, selections.getValue(observation))
        }
        val disagreements = buildList {
            families.forEach { family ->
                family.evaluated.forEach { candidate ->
                    val result = selections.getValue(candidate.observation)
                    if (result.ocrDynamicAdapter != result.ocrStaticAdapter) {
                        add(
                            DisagreementExample(
                                family = family.name,
                                source = candidate.source,
                                observation = candidate.observation,
                                dynamic = result.ocrDynamicAdapter,
                                static = result.ocrStaticAdapter
                            )
                        )
                    }
                }
            }
            adversarial.forEach { observation ->
                val result = selections.getValue(observation)
                if (result.ocrDynamicAdapter != result.ocrStaticAdapter) {
                    add(
                        DisagreementExample(
                            "adversarial",
                            null,
                            observation,
                            result.ocrDynamicAdapter,
                            result.ocrStaticAdapter
                        )
                    )
                }
            }
        }.sortedWith(
            compareBy<DisagreementExample> { it.family }
                .thenBy { it.source }
                .thenBy { it.observation }
        )

        val fixture = ScanFixtureIntegrity.scan()
        return RecognitionReport(
            schemaVersion = 1,
            sourceMainSha = SOURCE_MAIN_SHA,
            canonicalSpeciesCount = canonical.size,
            methodology = Methodology(
                minimumObservationLength = 3,
                canonicalSource = "app/src/main/assets/data/pokemon_names.json; injected into the test " +
                    "TextParser because this JVM task does not expose main assets through Robolectric",
                generatedDefinition = "one deterministic transform attempt per canonical source; " +
                    "evaluated excludes the recorded reasons",
                collisionPrecedence = listOf(
                    "unchanged",
                    "below-production-minimum",
                    "different-canonical",
                    "multi-source"
                ),
                mutationFamilies = specs.associateTo(linkedMapOf()) { it.name to it.methodology },
                paths = linkedMapOf(
                    "parseName" to "TextParser.parseName",
                    "parseStrongSpeciesName" to "TextParser.parseStrongSpeciesName",
                    "rankNameCandidatesFirst" to "TextParser.rankNameCandidates(...).firstOrNull()",
                    "ocrDynamicAdapter" to "test-only selection-policy mirror: shared species-name decision",
                    "ocrStaticAdapter" to "test-only selection-policy mirror: shared species-name decision"
                ),
                adapterLimit = "Selection-policy characterization only; " +
                    "not a full ML Kit image benchmark."
            ),
            exclusions = families.map(PreparedFamily::exclusions)
                .fold(ExclusionCounts()) { total, next -> total + next },
            families = familyReports,
            wrongExamples = wrongExamples
                .sortedWith(
                    compareBy<WrongExample> { it.family }
                        .thenBy { it.path }
                        .thenBy { it.source }
                        .thenBy { it.observation }
                )
                .take(100),
            adversarial = adversarialResults,
            dynamicStaticDisagreement = DisagreementSummary(
                compared = families.sumOf { it.evaluated.size } + adversarial.size,
                disagreements = disagreements.size,
                examples = disagreements.take(50)
            ),
            fixtureIntegrity = FixtureSummary(
                manifestUtf8Bom = fixture.manifestUtf8Bom,
                totalManifestEntries = fixture.total,
                labeled = fixture.labeled,
                unlabeled = fixture.unlabeled,
                strict = fixture.strict,
                corrupt = fixture.corrupt,
                missing = fixture.missing,
                decodeFailures = fixture.decodeFailures
            )
        )
    }

    private fun prepareFamily(canonical: List<String>, spec: FamilySpec): PreparedFamily {
        val canonicalKeys = canonical.associateBy(::collisionKey)
        val candidates = canonical.map { CandidateObservation(it, spec.transform(it)) }
        val eligibleForCollision = candidates.filter { candidate ->
            (spec.allowUnchanged || candidate.observation != candidate.source) &&
                isProductionLength(candidate.observation)
        }
        val multiSourceKeys = eligibleForCollision
            .groupBy { collisionKey(it.observation) }
            .filterValues { values -> values.map(CandidateObservation::source).distinct().size > 1 }
            .keys
        var unchanged = 0
        var belowMinimum = 0
        var canonicalCollision = 0
        var multiSourceCollision = 0
        val evaluated = mutableListOf<CandidateObservation>()

        candidates.forEach { candidate ->
            val key = collisionKey(candidate.observation)
            when {
                !spec.allowUnchanged && candidate.observation == candidate.source -> unchanged++
                !isProductionLength(candidate.observation) -> belowMinimum++
                canonicalKeys[key]?.let {
                    !it.equals(candidate.source, ignoreCase = true)
                } == true -> canonicalCollision++
                key in multiSourceKeys -> multiSourceCollision++
                else -> evaluated += candidate
            }
        }

        return PreparedFamily(
            name = spec.name,
            generated = candidates.size,
            evaluated = evaluated.sortedWith(compareBy<CandidateObservation> { it.source }.thenBy { it.observation }),
            exclusions = ExclusionCounts(canonicalCollision, multiSourceCollision, unchanged, belowMinimum)
        )
    }

    private fun selectAllPaths(observation: String): PathSelections {
        val ranked = parser.rankNameCandidates(observation, limit = 1).firstOrNull()
        val parseName = parser.parseName(observation)
        val strong = parser.parseStrongSpeciesName(observation)
        val dynamic = parser.decideDynamicOcrSpeciesName(observation).acceptedSpeciesOrNull()
        val static = parser.decideStaticOcrSpeciesName(observation).acceptedSpeciesOrNull()
        return PathSelections(parseName, strong, ranked?.name, dynamic, static)
    }

    private fun familySpecs(): List<FamilySpec> = listOf(
        FamilySpec("exact_canonical", "unchanged canonical asset value", allowUnchanged = true) { it },
        FamilySpec("final_character_truncation", "remove the final character") { it.dropLast(1) },
        FamilySpec("final_two_character_truncation", "remove the final two characters") { it.dropLast(2) },
        FamilySpec("internal_single_character_deletion", "remove index length/2") {
            it.removeRange(it.length / 2, (it.length / 2) + 1)
        },
        FamilySpec(
            "single_character_substitution",
            "replace index length-2 with the next ASCII alphabetic character; " +
                "non-ASCII/non-letter targets are unchanged"
        ) {
            val index = it.length - 2
            val replacement = nextAsciiLetter(it[index]) ?: return@FamilySpec it
            it.replaceRange(index, index + 1, replacement.toString())
        },
        FamilySpec("adjacent_transposition", "swap indexes (length-1)/2 and +1 when unequal") {
            val left = (it.length - 1) / 2
            if (it[left] == it[left + 1]) return@FamilySpec it
            it.toCharArray().also { chars ->
                val value = chars[left]
                chars[left] = chars[left + 1]
                chars[left + 1] = value
            }.concatToString()
        },
        FamilySpec("punctuation_spacing_removal", "remove every non-alphanumeric character") {
            it.filter(Char::isLetterOrDigit)
        },
        FamilySpec(
            "ocr_glyph_confusion",
            "replace first eligible character using o->0, i->1, l->1, s->5, b->8"
        ) {
            val index = it.indexOfFirst { char -> char.lowercaseChar() in GLYPH_CONFUSIONS }
            if (index < 0) {
                it
            } else {
                val replacement = GLYPH_CONFUSIONS.getValue(it[index].lowercaseChar())
                it.replaceRange(index, index + 1, replacement.toString())
            }
        },
        FamilySpec("one_digit_suffix", "append 1") { it + "1" },
        FamilySpec("two_digit_suffix", "append 12") { it + "12" },
        FamilySpec("three_digit_suffix", "append 123") { it + "123" },
        FamilySpec("four_digit_year_suffix", "append 2024") { it + "2024" }
    )

    private fun adversarialObservations(): List<String> = listOf(
        "Nidoran", "nidoran-f", "nidoran-m", "Nidorano", "Nidoranp",
        "HoOh", "Ho Oh", "H0-0h", "Poliwrat", "metapo", "Rocky", "Luna",
        "Sparky", "King", "Fluffy", "meow", "par", "glo", "Eevee2020",
        "Machamp2017", "Poryg0n", "Espe0n", "Gyarados100", "Slowpoke100"
    )

    private fun loadCanonicalSpecies(): List<String> = runtimeSpeciesFile().reader().use { reader ->
        Gson().fromJson(reader, object : TypeToken<List<String>>() {}.type)
    }

    private fun injectCanonicalSpecies(canonical: List<String>) {
        val field = TextParser::class.java.getDeclaredField("pokemonNames").apply { isAccessible = true }
        val normalized = canonical.map { it.lowercase(Locale.ROOT) }
        field.set(parser, normalized)
        @Suppress("UNCHECKED_CAST")
        assertEquals(normalized, field.get(parser) as List<String>)
    }

    private fun reportFile(): File {
        return File(findRepoRoot(), "app/build/reports/recognition/recognition_measurement_baseline.json")
    }

    private fun runtimeSpeciesFile(): File =
        File(findRepoRoot(), "app/src/main/assets/data/pokemon_names.json")

    private fun findRepoRoot(): File {
        var dir = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            if (File(dir, "app/src/main/assets/data/pokemon_names.json").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        error("Repository root containing pokemon_names.json was not found")
    }

    private fun collisionKey(value: String): String = value.trim().lowercase(Locale.ROOT)

    private fun isProductionLength(value: String): Boolean = value
        .replace(Regex("[^A-Za-z0-9\\s\\-\\.]"), "")
        .trim()
        .length >= 3

    private fun nextAsciiLetter(value: Char): Char? = when (value) {
        in 'a'..'y', in 'A'..'Y' -> value + 1
        'z' -> 'a'
        'Z' -> 'A'
        else -> null
    }

    companion object {
        private const val SOURCE_MAIN_SHA = "982ad7676876592681dca92bf6a49a1902611ae5"
        private const val PR01_BASELINE_SOURCE_SHA = "4f8dcc8afdb705301e328370ea7be973b444998f"
        private const val BASELINE_RESOURCE = "recognition/recognition_measurement_baseline.json"
        private const val APPROVED_COUNTS_RESOURCE = "recognition/recognition_pr02_approved_counts.json"
        private val PATHS = listOf(
            "parseName", "parseStrongSpeciesName", "rankNameCandidatesFirst", "ocrDynamicAdapter", "ocrStaticAdapter"
        )
        private val SELECTABLE_PATHS = PATHS - "rankNameCandidatesFirst"
        private val GLYPH_CONFUSIONS = mapOf('o' to '0', 'i' to '1', 'l' to '1', 's' to '5', 'b' to '8')
    }
}

private data class FamilySpec(
    val name: String,
    val methodology: String,
    val allowUnchanged: Boolean = false,
    val transform: (String) -> String
)

private data class ApprovedRecognitionSummary(
    val sourceMainSha: String,
    val families: List<ApprovedFamilyCounts>
)

private data class ApprovedFamilyCounts(
    val name: String,
    val decision: ApprovedOutcomeCounts,
    val strong: ApprovedOutcomeCounts
)

private data class ApprovedOutcomeCounts(
    val acceptedCorrect: Int,
    val uncertainNoMatch: Int
)

private data class CandidateObservation(val source: String, val observation: String)

private data class PreparedFamily(
    val name: String,
    val generated: Int,
    val evaluated: List<CandidateObservation>,
    val exclusions: ExclusionCounts
)

private data class RecognitionReport(
    val schemaVersion: Int,
    val sourceMainSha: String,
    val canonicalSpeciesCount: Int,
    val methodology: Methodology,
    val exclusions: ExclusionCounts,
    val families: List<FamilyReport>,
    val wrongExamples: List<WrongExample>,
    val adversarial: List<AdversarialResult>,
    val dynamicStaticDisagreement: DisagreementSummary,
    val fixtureIntegrity: FixtureSummary
)

private data class Methodology(
    val minimumObservationLength: Int,
    val canonicalSource: String,
    val generatedDefinition: String,
    val collisionPrecedence: List<String>,
    val mutationFamilies: Map<String, String>,
    val paths: Map<String, String>,
    val adapterLimit: String
)

private data class FamilyReport(
    val name: String,
    val generated: Int,
    val evaluated: Int,
    val exclusions: ExclusionCounts,
    val paths: Map<String, OutcomeCounts>
)

private data class ExclusionCounts(
    val canonicalCollision: Int = 0,
    val multiSourceCollision: Int = 0,
    val unchanged: Int = 0,
    val belowProductionMinimum: Int = 0
) {
    operator fun plus(other: ExclusionCounts): ExclusionCounts = ExclusionCounts(
        canonicalCollision + other.canonicalCollision,
        multiSourceCollision + other.multiSourceCollision,
        unchanged + other.unchanged,
        belowProductionMinimum + other.belowProductionMinimum
    )
}

private data class OutcomeCounts(
    val generated: Int,
    val evaluated: Int,
    val acceptedCorrect: Int,
    val acceptedWrong: Int,
    val uncertainNoMatch: Int
) {
    fun summaryLine(): String =
        "generated=$generated evaluated=$evaluated correct=$acceptedCorrect " +
            "wrong=$acceptedWrong uncertain=$uncertainNoMatch"
}

private data class PathSelections(
    val parseName: String?,
    val parseStrongSpeciesName: String?,
    val rankNameCandidatesFirst: String?,
    val ocrDynamicAdapter: String?,
    val ocrStaticAdapter: String?
) {
    fun get(path: String): String? = when (path) {
        "parseName" -> parseName
        "parseStrongSpeciesName" -> parseStrongSpeciesName
        "rankNameCandidatesFirst" -> rankNameCandidatesFirst
        "ocrDynamicAdapter" -> ocrDynamicAdapter
        "ocrStaticAdapter" -> ocrStaticAdapter
        else -> error("Unknown path: $path")
    }
}

private data class WrongExample(
    val family: String,
    val path: String,
    val source: String,
    val observation: String,
    val selected: String
)

private data class AdversarialResult(val observation: String, val paths: PathSelections)

private data class DisagreementSummary(
    val compared: Int,
    val disagreements: Int,
    val examples: List<DisagreementExample>
)

private data class DisagreementExample(
    val family: String,
    val source: String?,
    val observation: String,
    val dynamic: String?,
    val static: String?
)

private data class FixtureSummary(
    val manifestUtf8Bom: Boolean,
    val totalManifestEntries: Int,
    val labeled: Int,
    val unlabeled: Int,
    val strict: Int,
    val corrupt: Int,
    val missing: Int,
    val decodeFailures: Int
)
