package com.pokerarity.scanner.util.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.StringReader

/**
 * Phase 2D: the recognition snapshot is the single revisioned recognition-domain
 * authority. These tests pin its metadata, integrity, indexes and semantics.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecognitionSnapshotTest {

    private fun assetFile(name: String): File =
        listOf(File("src/main/assets/data/$name"), File("app/src/main/assets/data/$name"))
            .first { it.isFile }

    private val snapshot: RecognitionSnapshot by lazy {
        assetFile("recognition_profiles.json").reader().use(RecognitionSnapshot::load)
    }

    @Test
    fun snapshotRetainsFullRevisionProvenance() {
        val metadata = snapshot.metadata
        assertEquals(1, metadata.schemaVersion)
        assertEquals("8e227be44f288d34463e23bf04e9b564d3c16f79", metadata.sourceRevision)
        assertTrue(metadata.sourceSha256.matches(Regex("[a-f0-9]{64}")))
        assertTrue(metadata.namesSha256.matches(Regex("[a-f0-9]{64}")))
        assertEquals("1:8e227be44f288d34463e23bf04e9b564d3c16f79", metadata.revisionId)
    }

    @Test
    fun snapshotIntegrityMatchesItsDeclaredCounts() {
        assertEquals(1011, snapshot.canonicalSpecies.size)
        assertEquals(1228, snapshot.profiles.size)
        // Every canonical species has at least one profile and every profile is canonical.
        snapshot.profiles.forEach { row ->
            assertTrue(snapshot.containsSpecies(row.species))
            assertTrue(row.forms.isNotEmpty())
            assertTrue(row.types.size in 1..2 && RecognitionSnapshot.TYPES.containsAll(row.types))
            assertTrue(listOf(row.stats.atk, row.stats.def, row.stats.sta).all { it in 1..1000 })
            assertTrue(row.familyId.startsWith("FAMILY_"))
            assertTrue(snapshot.containsSpecies(row.candySpecies))
        }
        // Candy species resolve to canonical species rows (case-normalized).
        snapshot.profiles.map { it.candySpecies }.distinct().forEach { candy ->
            assertTrue("candy species must be canonical: $candy", snapshot.containsSpecies(candy))
        }
    }

    @Test
    fun cpmDomainIsCompleteHalfLevelAndStrictlyMonotonic() {
        val levels = snapshot.cpMultipliers.keys
        assertEquals((0..100).map { 1.0 + it / 2.0 }.toSet(), levels)
        assertTrue(snapshot.cpMultipliers.values.zipWithNext().all { (a, b) -> a < b })
        assertTrue(snapshot.cpMultipliers.getValue(1.0) < snapshot.cpMultipliers.getValue(51.0))
    }

    @Test
    fun familyIndexIsSymmetricAndRegistryIndependent() {
        val byFamily = snapshot.profiles.groupBy { it.familyId }
        byFamily.values.forEach { members ->
            val names = members.map { it.species }.distinct()
            names.forEach { a -> assertSymmetricWithPeers(a, names) }
            assertTrue(snapshot.familySize(names.first()) >= 1)
        }
        // A cross-family pair is never "same family".
        assertFalse(snapshot.isSameFamily("Bulbasaur", "Charmander"))
        assertFalse(snapshot.isSameFamily(null, "Bulbasaur"))
        assertFalse(snapshot.isSameFamily("Bulbasaur", ""))
    }

    @Test
    fun multiFormSpeciesPreserveAllProfileAlternatives() {
        // Real multi-form rows from the checked asset: alternatives stay candidates.
        val arceus = snapshot.forSpecies("Arceus")
        assertEquals(18, arceus.size)
        assertTrue(arceus.map { it.forms }.distinct().size >= 18)
        val rotom = snapshot.forSpecies("Rotom")
        assertEquals(6, rotom.size)
        assertTrue("form rows keep distinct stats", rotom.map { it.stats }.distinct().size > 1)
    }

    @Test
    fun ordinaryEvolutionSemanticsDistinguishKnownTerminalAndUnknown() {
        // Known ordinary cost, multiple supported costs, zero (trade) cost and
        // null (unknown/unsupported representation) are all preserved distinctly.
        val withCost = snapshot.profiles.first { it.evolutionCandyCosts?.isNotEmpty() == true }
        assertTrue(withCost.evolutionCandyCosts!!.all { it in 0..1000 })
        val multiCost = snapshot.profiles.filter { (it.evolutionCandyCosts?.size ?: 0) > 1 }
        assertTrue("branching evolutions keep multiple supported costs", multiCost.isNotEmpty())
        val zeroCost = snapshot.profiles.filter { it.evolutionCandyCosts?.contains(0) == true }
        assertTrue("trade-evolution representation (cost 0) is preserved", zeroCost.isNotEmpty())
        val unknown = snapshot.profiles.filter { it.evolutionCandyCosts == null }
        assertTrue("unknown/unsupported representation is preserved as null", unknown.isNotEmpty())
        assertNull(unknown.first().evolutionCandyCosts)
    }

    @Test
    fun loadFailsClosedOnCorruptOrInvalidAssets() {
        val validText = assetFile("recognition_profiles.json").readText()
        assertNotNull(StringReader(validText).use(RecognitionSnapshot::load))

        // Corrupt JSON
        assertLoadFails("{ not json")
        // Wrong schema version
        assertLoadFails(mutate { it.put("version", 2) })
        // Invalid revision / hash
        assertLoadFails(mutate { root -> root.getJSONObject("source").put("revision", "deadbeef") })
        assertLoadFails(mutate { root -> root.getJSONObject("source").put("sha256", "short") })
        assertLoadFails(mutate { root -> root.getJSONObject("source").remove("namesSha256") })
        // Incomplete CPM domain (missing half level)
        assertLoadFails(mutate { root -> root.getJSONObject("cpMultipliers").getJSONObject("values").remove("2.0") })
        // Declared species count mismatch
        assertLoadFails(mutate { root -> root.put("speciesCount", 999) })
        // Invalid profile row (out-of-domain stat)
        assertLoadFails(mutate { root ->
            root.getJSONArray("profiles").getJSONObject(0).put("atk", 5000)
        })
    }

    private fun mutate(transform: (org.json.JSONObject) -> Unit): String {
        val root = org.json.JSONObject(assetFile("recognition_profiles.json").readText())
        transform(root)
        return root.toString()
    }

    @Test
    fun loadOnceIndexedLookupCostIsBounded() {
        val parseStart = System.nanoTime()
        val loaded = assetFile("recognition_profiles.json").reader().use(RecognitionSnapshot::load)
        val parseMs = (System.nanoTime() - parseStart) / 1_000_000
        println("SNAPSHOT_PERF first load/parse ms: $parseMs")

        val lookupStart = System.nanoTime()
        val species = loaded.canonicalSpecies.sorted()
        var hits = 0
        repeat(10) {
            species.forEach { name ->
                if (loaded.isSameFamily(name, species.first())) hits++
                loaded.forCandy(loaded.forSpecies(name).first().candySpecies)
            }
        }
        val lookupMs = (System.nanoTime() - lookupStart) / 1_000_000
        println("SNAPSHOT_PERF 10x index sweep (10k+ lookups) ms: $lookupMs, hits=$hits")
        assertTrue("indexed lookups must stay bounded", lookupMs < 2_000)
        assertTrue(parseMs < 10_000)
    }

    private fun assertSymmetricWithPeers(species: String, peers: List<String>) {
        peers.filter { it != species }.forEach { peer ->
            assertTrue("same-family symmetry failed for $species/$peer", snapshot.isSameFamily(species, peer))
            assertTrue(snapshot.familyMembers(species).contains(peer))
            assertTrue(snapshot.familyMembers(peer).contains(species))
        }
    }

    private fun assertLoadFails(content: String) {
        try {
            StringReader(content).use(RecognitionSnapshot::load)
            throw AssertionError("expected the load to fail closed")
        } catch (expected: IllegalArgumentException) {
            // fail closed via require
        } catch (expected: org.json.JSONException) {
            // fail closed on structural corruption
        }
    }

    @Test
    fun holderCachesOneInstanceAndRemembersFailures() {
        RecognitionSnapshotHolder.reset()
        val loaded = mutableListOf<Int>()
        fun loader(failFirst: Boolean): (android.content.Context) -> RecognitionSnapshot? = { _ ->
            if (failFirst) {
                null
            } else {
                loaded += 1
                StringReader(assetFile("recognition_profiles.json").readText())
                    .use(RecognitionSnapshot::load)
            }
        }

        // A failed load is remembered: recognition stays fail-closed, no retry loop.
        val anyContext = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        assertNull(RecognitionSnapshotHolder.getOrNull(anyContext, loader(true)))
        assertNull(RecognitionSnapshotHolder.getOrNull(anyContext, loader(false)))
        assertEquals(0, loaded.size)

        // A successful load is cached; every later consumer shares the same instance.
        RecognitionSnapshotHolder.reset()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = RecognitionSnapshotHolder.getOrNull(context, loader(false))
        val second = RecognitionSnapshotHolder.getOrNull(context, loader(false))
        assertNotNull(first)
        assertSameInstance(first, second)
        assertEquals(1, loaded.size)
        assertEquals("1:8e227be44f288d34463e23bf04e9b564d3c16f79", first!!.metadata.revisionId)
        RecognitionSnapshotHolder.reset()
    }

    private fun assertSameInstance(first: Any?, second: Any?) {
        org.junit.Assert.assertTrue("expected the same cached snapshot instance", first === second)
    }

}
