package com.pokerarity.scanner

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.data.model.PokemonData
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import com.pokerarity.scanner.data.repository.PokemonMoveRegistry
import com.pokerarity.scanner.data.repository.RarityCalculator
import com.pokerarity.scanner.util.ocr.FieldCandidateDiagnostic
import com.pokerarity.scanner.util.ocr.SpeciesRefiner
import com.pokerarity.scanner.util.ocr.SpeciesResolverTrace
import com.pokerarity.scanner.util.ocr.TextParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.ConscryptMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class SpeciesRefinerAuthorityTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    init {
        injectPokemonFamilies()
        injectPokemonMoves()
    }

    private val rarityCalculator = RarityCalculator(context).also(::injectBaseStats)
    private val refiner = SpeciesRefiner(context, rarityCalculator).also(::injectCanonicalSpecies)

    @Test
    fun exactCanonicalAndReviewedAliasRemainHardAuthorityWithUntrustedCandy() {
        listOf(
            AuthorityCase("Squirtle", "Squirtle", "Mankey", KEPT_EXACT, NAME_EXACT),
            AuthorityCase("Ho-Oh", "HoOh", "Squirtle", KEPT_REVIEWED, NAME_REVIEWED)
        ).forEach { case ->
            val refined = refiner.refine(pokemon(case.current, case.rawName, case.candy))

            assertSpecies(case.current, refined.realName)
            assertTrace(refined, case.current, case.reason, case.nameEvidence, CANDY_UNTRUSTED)
        }
    }

    @Test
    fun safeFuzzyIsSoftOnlyEvenWhenReliableCandyCorroboratesIt() {
        val refined = refiner.refine(
            pokemon("Poliwrath", "Poliwrat", "Poliwag"),
            listOf(reliableCandy("Poliwag"))
        )

        assertSpecies("Poliwrath", refined.realName)
        assertTrace(refined, "Poliwrath", KEPT_SAFE_FUZZY, NAME_SAFE_FUZZY, CANDY_RELIABLE)
        assertNoHardNameAuthority(refined.speciesResolverTrace)
    }

    @Test
    fun uncertainNoMatchAndConflictingAcceptedNamesNeverCreateHardAuthority() {
        val uncertain = refiner.refine(pokemon("Nidoran-f", "Nidoran"))
        val noMatch = refiner.refine(pokemon("Mankey", "WEATHER BONUS"))
        val conflicting = refiner.refine(
            pokemon("Squirtle", "Squirtle"),
            listOf(nameCandidate("NameDynamic", "HoOh"))
        )
        val reversedConflict = refiner.refine(
            pokemon("Squirtle", "HoOh"),
            listOf(nameCandidate("NameDynamic", "Squirtle"))
        )

        assertSpecies("Nidoran-f", uncertain.realName)
        assertTrace(uncertain, "Nidoran-f", KEPT_UNCERTAIN, NAME_UNCERTAIN)
        assertNoHardNameAuthority(uncertain.speciesResolverTrace)
        assertZeroConfidence(uncertain)

        assertSpecies("Mankey", noMatch.realName)
        assertTrace(noMatch, "Mankey", KEPT_NO_MATCH, NAME_NO_MATCH)
        assertNoHardNameAuthority(noMatch.speciesResolverTrace)
        assertZeroConfidence(noMatch)

        assertSpecies("Squirtle", conflicting.realName)
        assertTrace(conflicting, "Squirtle", KEPT_NAME_CONFLICT, NAME_CONFLICT)
        assertNoHardNameAuthority(conflicting.speciesResolverTrace)
        assertZeroConfidence(conflicting)
        assertSpecies("Squirtle", reversedConflict.realName)
        assertTrace(reversedConflict, "Squirtle", KEPT_NAME_CONFLICT, NAME_CONFLICT)
        assertEquals(conflictCodes(conflicting), conflictCodes(reversedConflict))
    }

    @Test
    fun candyReliabilityRequiresOneTrustedMatchingWinningCandidate() {
        val blank = refiner.refine(pokemon("Squirtle", "Squirtle"))
        assertTrace(blank, "Squirtle", KEPT_EXACT, NAME_EXACT)

        val unreliable = listOf(
            emptyList(),
            listOf(
                candyCandidate(
                    "Squirtle",
                    status = "missing",
                    winner = false,
                    reason = "loser:no_parse:candy_no_parse"
                )
            ),
            listOf(candyCandidate("Squirtle", status = "not-run")),
            listOf(candyCandidate("Mankey")),
            listOf(candyCandidate("Squirtle", source = "synthetic"))
        )
        unreliable.forEach { diagnostics ->
            val refined = refiner.refine(pokemon("Squirtle", "Squirtle", "Squirtle"), diagnostics)

            assertSpecies("Squirtle", refined.realName)
            assertTrace(refined, "Squirtle", KEPT_EXACT, NAME_EXACT, CANDY_UNTRUSTED)
        }

        val conflicted = refiner.refine(
            pokemon("Squirtle", "Squirtle", "Squirtle"),
            listOf(reliableCandy("Squirtle"), reliableCandy("Mankey", source = "candy_wide"))
        )
        assertSpecies("Squirtle", conflicted.realName)
        assertTrace(conflicted, "Squirtle", KEPT_CANDY_CONFLICT, NAME_EXACT, CANDY_CONFLICT)

        val reliable = refiner.refine(
            pokemon("Squirtle", "Squirtle", "Squirtle"),
            listOf(reliableCandy("Squirtle"))
        )
        assertSpecies("Squirtle", reliable.realName)
        assertTrace(reliable, "Squirtle", KEPT_EXACT, NAME_EXACT, CANDY_RELIABLE)
    }

    @Test
    fun uncertainCandyEvidenceCannotCreateUniqueOrFamilyFalsePositivesWithoutProfile() {
        listOf("Ditto", "Squirtle").forEach { candy ->
            val refined = refiner.refine(
                pokemon("Mankey", "Nidoran", candy),
                listOf(reliableCandy(candy))
            )

            assertSpecies("Mankey", refined.realName)
            assertTrace(refined, "Mankey", KEPT_INSUFFICIENT_PROFILE, NAME_UNCERTAIN, CANDY_RELIABLE)
            assertNoHardNameAuthority(refined.speciesResolverTrace)
        }
    }

    @Test
    fun uncertainCorrectOrWrongCandyWithoutSufficientProfileDoesNotReplace() {
        listOf("Nidoran-f", "Squirtle").forEach { candy ->
            val refined = refiner.refine(
                pokemon("Nidoran-f", "Nidoran", candy),
                listOf(reliableCandy(candy))
            )

            assertSpecies("Nidoran-f", refined.realName)
            assertTrace(refined, "Nidoran-f", KEPT_INSUFFICIENT_PROFILE, NAME_UNCERTAIN, CANDY_RELIABLE)
            assertNoHardNameAuthority(refined.speciesResolverTrace)
        }
    }

    @Test
    fun profileMismatchOnlyAllowsReplacementWithReliableCompatibleCandy() {
        val withoutReliableCandy = refiner.refine(profileMismatchPokemon())
        val withRawUntrustedCandy = refiner.refine(
            profileMismatchPokemon().copy(
                rawOcrText = "Name:Nidoran|NameHC:|Candy:Squirtle Candy|Bottom:"
            )
        )
        val withReliableCandy = refiner.refine(profileMismatchPokemon(), listOf(reliableCandy("Squirtle")))

        assertSpecies("Mankey", withoutReliableCandy.realName)
        assertTrace(
            withoutReliableCandy,
            "Mankey",
            KEPT_PROFILE_MISMATCH,
            NAME_UNCERTAIN,
            CANDY_UNTRUSTED,
            PROFILE_MISMATCH
        )
        assertTrue(
            trace(withoutReliableCandy).canonicalCandidates.none { candidate ->
                candidate.reasons.any { it in RESOLVER_CANDY_REASONS }
            }
        )
        assertSpecies("Mankey", withRawUntrustedCandy.realName)
        assertTrue(trace(withRawUntrustedCandy).evidenceUsed.none { it in RESOLVER_CANDY_REASONS })
        assertTrue(
            trace(withRawUntrustedCandy).canonicalCandidates.none { candidate ->
                candidate.reasons.any { it in RESOLVER_CANDY_REASONS }
            }
        )

        assertSpecies("Squirtle", withReliableCandy.realName)
        assertTrace(
            withReliableCandy,
            "Squirtle",
            REPLACED_RELIABLE_CANDY_PROFILE,
            NAME_UNCERTAIN,
            CANDY_RELIABLE,
            PROFILE_COMPATIBLE
        )
    }

    @Test
    fun blankAndNonblankUnreliableCandyHaveEquivalentNameAuthority() {
        val blank = refiner.refine(pokemon("Squirtle", "Squirtle"))
        val nonblank = refiner.refine(pokemon("Squirtle", "Squirtle", "Mankey"))

        assertSpecies(blank.realName, nonblank.realName)
        assertEquals(KEPT_EXACT, trace(blank).winnerReason)
        assertEquals(KEPT_EXACT, trace(nonblank).winnerReason)
        assertTrue(trace(blank).evidenceUsed.contains(NAME_EXACT))
        assertTrue(trace(nonblank).evidenceUsed.contains(NAME_EXACT))
        assertTrue(trace(nonblank).evidenceUsed.contains(CANDY_UNTRUSTED))
    }

    @Test
    fun sameFamilyDriftRemainsBlockedByReviewedNameAuthority() {
        val refined = refiner.refine(
            pokemon(
                current = "Slowpoke",
                rawName = "Slowpoke100",
                candy = "Slowpoke",
                profile = ObservedProfile(cp = 543, hp = 116, arcLevel = 1.0f)
            )
        )

        assertSpecies("Slowpoke", refined.realName)
        assertTrace(refined, "Slowpoke", KEPT_REVIEWED, NAME_REVIEWED, CANDY_UNTRUSTED)
    }

    @Test
    fun resolverProposalWithoutFinalAuthorityCannotReplaceOrBecomeAHardLock() {
        val refined = refiner.refine(pokemon("Mankey", "Nidoran"))

        assertSpecies("Mankey", refined.realName)
        assertTrace(
            refined,
            "Mankey",
            KEPT_UNCERTAIN,
            NAME_UNCERTAIN,
            RESOLVER_PROPOSAL_ONLY
        )
        assertNoHardNameAuthority(refined.speciesResolverTrace)
    }

    @Test
    fun moveCorroborationUsesMoveReasonAndReplacementConfidence() {
        val refined = refiner.refine(
            pokemon(
                current = "Mankey",
                rawName = "Mankey",
                profile = ObservedProfile(
                    cp = 3691,
                    hp = 201,
                    arcLevel = 1.0f,
                    weight = 1.1f,
                    height = 0.3f
                ),
                bottom = "Doom Desire"
            )
        )

        assertSpecies("Jirachi", refined.realName)
        assertTrace(refined, "Jirachi", REPLACED_MOVE_CORROBORATION, NAME_EXACT)
        val trace = trace(refined)
        assertTrue(trace.confidence > 0f)
        assertTrue(trace.confidence <= MAX_CORROBORATED_CONFIDENCE)
        assertEquals(trace.confidence, trace.canonicalCandidates.single { it.winner }.score, 0f)
    }

    @Test
    fun acceptedNameTriggerWinsWhenSingletonCandyAlsoSupportsReplacement() {
        val refined = refiner.refine(
            pokemon(
                current = "Mankey",
                rawName = "Squirtle",
                candy = "Ditto",
                profile = ObservedProfile(
                    cp = 940,
                    hp = 125,
                    arcLevel = 1.0f,
                    weight = 4.0f,
                    height = 0.3f
                )
            ),
            listOf(reliableCandy("Ditto"))
        )

        assertSpecies("Squirtle", refined.realName)
        assertTrace(refined, "Squirtle", REPLACED_ACCEPTED_NAME, NAME_EXACT, CANDY_RELIABLE)
        val trace = trace(refined)
        assertEquals(EXACT_NAME_CONFIDENCE, trace.confidence, 0f)
        assertEquals(trace.confidence, trace.canonicalCandidates.single { it.winner }.score, 0f)
    }

    @Test
    fun candyFamilyOverridePrecedesAcceptedNameWhenBothApply() {
        val refined = refiner.refine(
            pokemon(
                current = "Mankey",
                rawName = "Squirtle",
                candy = "Squirtle",
                profile = ObservedProfile(
                    cp = 597,
                    hp = 90,
                    arcLevel = 1.0f,
                    weight = 13.27f,
                    height = 0.56f
                )
            ),
            listOf(reliableCandy("Squirtle"))
        )

        assertSpecies("Squirtle", refined.realName)
        assertTrace(
            refined,
            "Squirtle",
            REPLACED_RELIABLE_CANDY_PROFILE,
            NAME_EXACT,
            CANDY_RELIABLE,
            PROFILE_COMPATIBLE
        )
    }

    private fun pokemon(
        current: String,
        rawName: String,
        candy: String? = null,
        profile: ObservedProfile = ObservedProfile(),
        bottom: String = ""
    ): PokemonData = PokemonData(
        cp = profile.cp,
        hp = profile.hp,
        maxHp = profile.hp,
        name = current,
        realName = current,
        candyName = candy,
        megaEnergy = null,
        weight = profile.weight,
        height = profile.height,
        stardust = null,
        arcLevel = profile.arcLevel,
        caughtDate = null,
        rawOcrText = "Name:$rawName|NameHC:|Bottom:$bottom"
    )

    private fun profileMismatchPokemon(): PokemonData = pokemon(
        current = "Mankey",
        rawName = "Nidoran",
        candy = "Squirtle",
        profile = ObservedProfile(
            cp = 597,
            hp = 90,
            arcLevel = 1.0f,
            weight = 13.27f,
            height = 0.56f
        )
    )

    private fun reliableCandy(value: String, source: String = "candy"): FieldCandidateDiagnostic =
        candyCandidate(value, source = source)

    private fun candyCandidate(
        value: String,
        source: String = "candy",
        status: String = "found",
        winner: Boolean = true,
        reason: String = "winner:candy_parser"
    ): FieldCandidateDiagnostic = FieldCandidateDiagnostic(
        field = "Candy",
        source = source,
        rawText = "$value Candy",
        parsedValue = value,
        status = status,
        normalizedText = "$value Candy",
        parserResult = value,
        winner = winner,
        reason = reason,
        selectedValue = value.takeIf { winner }
    )

    private fun nameCandidate(field: String, value: String): FieldCandidateDiagnostic = FieldCandidateDiagnostic(
        field = field,
        source = "dynamic_name_crop",
        rawText = value,
        parsedValue = value,
        status = "found",
        parserResult = value,
        winner = true,
        reason = "winner:name_parser",
        selectedValue = value
    )

    private fun assertTrace(
        pokemon: PokemonData,
        species: String,
        reason: String,
        vararg evidence: String
    ) {
        val trace = trace(pokemon)
        assertSpecies(species, trace.winningSpecies)
        assertEquals(reason, trace.winnerReason)
        evidence.forEach { token ->
            assertTrue("Missing trace evidence token: $token", trace.evidenceUsed.contains(token))
        }
    }

    private fun assertNoHardNameAuthority(trace: SpeciesResolverTrace?) {
        val evidence = requireNotNull(trace).evidenceUsed
        assertFalse(evidence.contains(NAME_EXACT))
        assertFalse(evidence.contains(NAME_REVIEWED))
    }

    private fun assertZeroConfidence(pokemon: PokemonData) {
        val trace = trace(pokemon)
        assertEquals(0f, trace.confidence, 0f)
        val winner = trace.canonicalCandidates.single { it.winner }
        assertEquals(trace.confidence, winner.score, 0f)
    }

    private fun conflictCodes(pokemon: PokemonData): List<String> =
        trace(pokemon).loserReasons.filter { it.startsWith(ACCEPTED_NAME_CONFLICT_PREFIX) }

    private fun trace(pokemon: PokemonData): SpeciesResolverTrace {
        assertNotNull(pokemon.speciesResolverTrace)
        return requireNotNull(pokemon.speciesResolverTrace)
    }

    private fun assertSpecies(expected: String?, actual: String?) {
        assertTrue("Expected $expected, got $actual", expected.equals(actual, ignoreCase = true))
    }

    private fun injectCanonicalSpecies(target: SpeciesRefiner) {
        val species = dataFile("pokemon_names.json").reader().use { reader ->
            Gson().fromJson<List<String>>(reader, object : TypeToken<List<String>>() {}.type)
        }.map { it.lowercase(Locale.ROOT) }
        val parser = SpeciesRefiner::class.java.getDeclaredField("textParser").apply { isAccessible = true }
            .get(target) as TextParser
        injectCanonicalSpecies(parser, species)
        val resolver = SpeciesRefiner::class.java.getDeclaredField("speciesFormResolver").apply { isAccessible = true }
            .get(target)
        val resolverParser = resolver.javaClass.getDeclaredField("textParser").apply { isAccessible = true }
            .get(resolver) as TextParser
        injectCanonicalSpecies(resolverParser, species)
    }

    private fun injectPokemonFamilies() {
        val data = dataFile("pokemon_families.json").reader().use { reader ->
            Gson().fromJson<FamilyData>(reader, FamilyData::class.java)
        }
        PokemonFamilyRegistry::class.java.getDeclaredField("speciesToFamily").apply { isAccessible = true }
            .set(PokemonFamilyRegistry, data.speciesToFamily.mapKeys { it.key.lowercase(Locale.ROOT) })
        PokemonFamilyRegistry::class.java.getDeclaredField("familyToSpecies").apply { isAccessible = true }
            .set(PokemonFamilyRegistry, data.families)
        PokemonFamilyRegistry::class.java.getDeclaredField("loaded").apply { isAccessible = true }
            .setBoolean(PokemonFamilyRegistry, true)
    }

    private fun injectBaseStats(calculator: RarityCalculator) {
        val stats = dataFile("pokemon_base_stats.json").reader().use { reader ->
            Gson().fromJson<Map<String, RarityCalculator.BaseStats>>(
                reader,
                object : TypeToken<Map<String, RarityCalculator.BaseStats>>() {}.type
            )
        }
        RarityCalculator::class.java.getDeclaredField("baseStats\$delegate").apply { isAccessible = true }
            .set(calculator, lazyOf(stats))
    }

    private fun injectPokemonMoves() {
        val data = dataFile("pokemon_moves.json").reader().use { reader ->
            Gson().fromJson<MoveData>(reader, MoveData::class.java)
        }
        val speciesToMoves = data.speciesToMoves.mapKeys { it.key.lowercase(Locale.ROOT) }
            .mapValues { (_, moves) -> moves.map(::normalizeToken).toSet() }
        val moveToSpecies = data.moveToSpecies.mapKeys { it.key.lowercase(Locale.ROOT) }
        PokemonMoveRegistry::class.java.getDeclaredField("speciesToMoves").apply { isAccessible = true }
            .set(PokemonMoveRegistry, speciesToMoves)
        PokemonMoveRegistry::class.java.getDeclaredField("moveToSpecies").apply { isAccessible = true }
            .set(PokemonMoveRegistry, moveToSpecies)
        PokemonMoveRegistry::class.java.getDeclaredField("moveKeysByLength").apply { isAccessible = true }
            .set(PokemonMoveRegistry, moveToSpecies.keys.sortedByDescending(String::length))
        PokemonMoveRegistry::class.java.getDeclaredField("loaded").apply { isAccessible = true }
            .setBoolean(PokemonMoveRegistry, true)
    }

    private fun injectCanonicalSpecies(parser: TextParser, species: List<String>) {
        TextParser::class.java.getDeclaredField("pokemonNames").apply { isAccessible = true }.set(parser, species)
    }

    private fun dataFile(name: String): File {
        var directory = File(System.getProperty("user.dir") ?: ".").absoluteFile
        repeat(6) {
            File(directory, "app/src/main/assets/data/$name").takeIf(File::isFile)?.let { return it }
            directory = directory.parentFile ?: return@repeat
        }
        error("$name not found")
    }

    private data class FamilyData(
        val speciesToFamily: Map<String, String>,
        val families: Map<String, List<String>>
    )

    private data class MoveData(
        val speciesToMoves: Map<String, List<String>>,
        val moveToSpecies: Map<String, List<String>>
    )

    private fun normalizeToken(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private data class AuthorityCase(
        val current: String,
        val rawName: String,
        val candy: String,
        val reason: String,
        val nameEvidence: String
    )

    private data class ObservedProfile(
        val cp: Int? = null,
        val hp: Int? = null,
        val arcLevel: Float? = null,
        val weight: Float? = null,
        val height: Float? = null
    )

    private companion object {
        const val KEPT_EXACT = "kept_exact_canonical"
        const val KEPT_REVIEWED = "kept_reviewed_alias"
        const val KEPT_SAFE_FUZZY = "kept_safe_fuzzy_with_corroboration"
        const val KEPT_UNCERTAIN = "kept_uncertain_name"
        const val KEPT_NO_MATCH = "kept_no_name_match"
        const val KEPT_NAME_CONFLICT = "kept_conflicting_accepted_names"
        const val KEPT_CANDY_CONFLICT = "kept_candy_conflicted"
        const val KEPT_INSUFFICIENT_PROFILE = "kept_insufficient_profile"
        const val KEPT_PROFILE_MISMATCH = "kept_profile_mismatch"
        const val KEPT_RESOLVER_PROPOSAL_ONLY = "kept_resolver_proposal_only"
        const val REPLACED_RELIABLE_CANDY_PROFILE = "replaced_reliable_candy_profile"
        const val REPLACED_ACCEPTED_NAME = "replaced_accepted_name"

        const val NAME_EXACT = "name_exact_canonical"
        const val NAME_REVIEWED = "name_reviewed_alias"
        const val NAME_SAFE_FUZZY = "name_safe_fuzzy"
        const val NAME_UNCERTAIN = "name_uncertain"
        const val NAME_NO_MATCH = "name_no_match"
        const val NAME_CONFLICT = "name_conflict"
        const val CANDY_RELIABLE = "candy_reliable"
        const val CANDY_UNTRUSTED = "candy_untrusted"
        const val CANDY_CONFLICT = "candy_conflict"
        const val PROFILE_COMPATIBLE = "profile_compatible"
        const val PROFILE_MISMATCH = "profile_mismatch"
        const val RESOLVER_PROPOSAL_ONLY = "resolver_proposal_only"
        const val ACCEPTED_NAME_CONFLICT_PREFIX = "accepted_name_conflict:"
        const val REPLACED_MOVE_CORROBORATION = "replaced_existing_move_corroboration"
        const val EXACT_NAME_CONFIDENCE = 0.90f
        const val MAX_CORROBORATED_CONFIDENCE = 0.65f
        val RESOLVER_CANDY_REASONS = setOf("candy_family", "unique_candy_species", "candy_species_hint")
    }
}
