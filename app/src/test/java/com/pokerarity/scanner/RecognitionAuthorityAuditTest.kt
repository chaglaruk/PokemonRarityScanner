package com.pokerarity.scanner

import com.google.gson.Gson
import com.pokerarity.scanner.data.repository.PokemonFamilyRegistry
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Phase 2D authority audit: characterizes the actual checked-in recognition snapshot
 * against the legacy family asset to prove why one authority is required, and proves a
 * real snapshot-only species is no longer vetoed by legacy family data.
 *
 * All examples are derived at runtime from the checked-in assets (no fabricated data).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecognitionAuthorityAuditTest {

    private fun dataFile(name: String): File =
        listOf(File("src/main/assets/data/$name"), File("app/src/main/assets/data/$name"))
            .first { it.isFile }

    private val snapshot: RecognitionSnapshot by lazy {
        dataFile("recognition_profiles.json").reader().use(RecognitionSnapshot::load)
    }
    private val legacy: Map<String, String> by lazy {
        val root = com.google.gson.JsonParser.parseString(dataFile("pokemon_families.json").readText()).asJsonObject
        root.getAsJsonObject("speciesToFamily").entrySet().associate { it.key to it.value.asString }
    }
    private val namesAsset: List<String> by lazy {
        Gson().fromJson<List<String>>(
            dataFile("pokemon_names.json").reader(),
            object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
        )
    }

    @Test
    fun snapshotOnlySpeciesAreEnumeratedFromTheActualAssets() {
        val legacySpecies = legacy.keys.map { it.lowercase() }.toSet()
        val snapshotSpecies = snapshot.canonicalSpecies
        val snapshotOnly = snapshotSpecies.filter { it !in legacySpecies }.sorted()
        val legacyOnly = legacySpecies.filter { legacyKey ->
            snapshot.profiles.none { it.species.lowercase() == legacyKey }
        }.sorted()

        // Measured on the checked-in assets (Phase 2D authority audit).
        assertEquals(1011, snapshotSpecies.size)
        assertEquals(998, legacySpecies.size)
        assertEquals(13, snapshotOnly.size)
        assertTrue(snapshotOnly.isEmpty() || snapshotOnly.first() == snapshotOnly.min())
        assertEquals(0, legacyOnly.size)
        // The audit evidence, printed for the record:
        println("AUDIT snapshot-only species (${snapshotOnly.size}): $snapshotOnly")
        println("AUDIT legacy-only species (${legacyOnly.size}): $legacyOnly")
    }

    @Test
    fun snapshotOnlySpeciesIsServedBySnapshotAndWouldBeBlindToLegacyFamilies() {
        val legacySpecies = legacy.keys.map { it.lowercase() }.toSet()
        val snapshotOnly = snapshot.canonicalSpecies
            .filter { it !in legacySpecies }
            .sorted()
            .first()
        // The snapshot knows the species AND its family; the legacy asset has no row at all.
        assertTrue(snapshot.containsSpecies(snapshotOnly))
        val familyMembers = snapshot.familyMembers(snapshotOnly)
        assertTrue("snapshot family index must serve a snapshot-only species", familyMembers.isNotEmpty())
        val otherMember = familyMembers.first { !it.equals(snapshotOnly, ignoreCase = true) }
        assertTrue(snapshot.isSameFamily(snapshotOnly, otherMember))
        assertFalse(legacy.containsKey(snapshotOnly))
        assertFalse(legacy.containsKey(snapshotOnly.lowercase()))
    }

    @Test
    fun legacyRegistryReturnsNothingForASnapshotOnlySpecies() {
        // The pre-migration authority would have failed these family queries outright.
        val context = ApplicationProvider.getApplicationContext<Context>()
        val legacySpecies = legacy.keys.map { it.lowercase() }.toSet()
        val snapshotOnly = snapshot.canonicalSpecies.filter { it !in legacySpecies }.sorted().first()
        assertTrue(PokemonFamilyRegistry.getFamilyMembers(context, snapshotOnly).isEmpty())
        assertEquals(0, PokemonFamilyRegistry.familySize(context, snapshotOnly))
    }

    @Test
    fun familyIdsAgreeForEverySpeciesPresentInBothAuthorities() {
        val disagreements = snapshot.profiles.mapNotNull { row ->
            val legacyId = legacy[row.species] ?: legacy[row.species.lowercase()]
            if (legacyId != null && legacyId != row.familyId) row.species to (row.familyId to legacyId) else null
        }
        assertTrue(
            "unexpected family-id disagreements: $disagreements",
            disagreements.isEmpty()
        )
    }

    @Test
    fun snapshotCanonicalSpeciesMatchesTheNamesSourceAsset() {
        val namesLower = namesAsset.map { it.lowercase() }.toSet()
        val snapshotLower = snapshot.canonicalSpecies.map { it.lowercase() }.toSet()
        assertEquals(namesAsset.size, 1011)
        assertEquals(namesLower, snapshotLower)
        assertTrue(snapshot.metadata.namesSha256.isNotBlank())
    }
}
