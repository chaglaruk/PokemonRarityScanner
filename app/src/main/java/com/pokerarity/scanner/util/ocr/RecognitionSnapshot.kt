package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.util.Log
import androidx.annotation.VisibleForTesting
import org.json.JSONObject
import java.io.Reader

/**
 * Family/species relations used by recognition decisions (Phase 2D single authority).
 * Recognition consumers must depend on this interface, never on the legacy
 * `PokemonFamilyRegistry`, so legacy family data can never veto recognition.
 */
interface RecognitionFamilyIndex {
    fun isSameFamily(first: String?, second: String?): Boolean
    fun familyMembers(species: String?): List<String>
    fun familySize(species: String?): Int

    /** Fail-closed view used when the recognition snapshot is unavailable. */
    object EMPTY : RecognitionFamilyIndex {
        override fun isSameFamily(first: String?, second: String?): Boolean = false
        override fun familyMembers(species: String?): List<String> = emptyList()
        override fun familySize(species: String?): Int = 0
    }
}

/**
 * The one revisioned recognition-domain snapshot/facade (plan section 6.5).
 *
 * Immutable authority for canonical species, per-form recognition profiles, base stats,
 * types, family/candy relations, CP multipliers and supported ordinary evolution costs.
 * Rarity/event freshness stays outside. Loaded once per app from the pinned asset; a
 * corrupt or invalid asset fails closed for recognition authority and never falls back
 * to legacy family/base-stat data.
 */
internal class RecognitionSnapshot private constructor(
    val metadata: Metadata,
    val profiles: List<Profile>,
    val cpMultipliers: Map<Double, Double>,
    private val rowsBySpecies: Map<String, List<Profile>>,
    private val rowsByFamily: Map<String, List<Profile>>,
    private val rowsByCandy: Map<String, List<Profile>>
) : RecognitionFamilyIndex {

    /** Deterministic provenance of the loaded recognition data; no local paths/timestamps. */
    data class Metadata(
        val schemaVersion: Int,
        val sourceRevision: String,
        val sourceSha256: String,
        val namesSha256: String
    ) {
        /** Bounded auditable identity for diagnostics; no raw hashes repeated per frame. */
        val revisionId: String get() = "$schemaVersion:$sourceRevision"
    }

    /** Recognition-domain base stats; deliberately independent of rarity repository types. */
    data class ProfileStats(val atk: Int, val def: Int, val sta: Int)

    data class Profile(
        val species: String,
        val forms: Set<String>,
        val stats: ProfileStats,
        val types: Set<String>,
        val familyId: String,
        val candySpecies: String,
        /**
         * Supported ordinary evolution candy costs; null means the source representation
         * carries no ordinary evolution cost support for this row (unknown/unsupported),
         * which must never be read as "terminal evolution".
         */
        val evolutionCandyCosts: Set<Int>? = null
    )

    /** Supported canonical species, case-normalized keys with display casing preserved. */
    val canonicalSpecies: Set<String> = rowsBySpecies.keys

    fun containsSpecies(name: String): Boolean =
        rowsBySpecies.containsKey(name.trim().lowercase())

    fun forSpecies(species: String): List<Profile> = rowsBySpecies[species.lowercase()].orEmpty()

    fun forCandy(candySpecies: String): List<Profile> =
        rowsByCandy[candySpecies.lowercase()].orEmpty()

    fun forFamily(familyId: String): List<Profile> = rowsByFamily[familyId].orEmpty()

    override fun isSameFamily(first: String?, second: String?): Boolean {
        val firstFamily = first?.trim()?.lowercase()?.let(::familyIdOf)
        val secondFamily = second?.trim()?.lowercase()?.let(::familyIdOf)
        return firstFamily != null && secondFamily != null && firstFamily == secondFamily
    }

    /** Same-family member display names of the species' family, per the snapshot rows. */
    override fun familyMembers(species: String?): List<String> {
        val familyId = species?.trim()?.lowercase()?.let(::familyIdOf)
        return familyId?.let { id -> rowsByFamily[id].orEmpty().map { it.species }.distinct() }.orEmpty()
    }

    override fun familySize(species: String?): Int {
        val familyId = species?.trim()?.lowercase()?.let(::familyIdOf)
        // Species count, not profile-row count: multi-form families stay comparable
        // with the historical family semantics.
        return familyId?.let { id -> rowsByFamily[id].orEmpty().map { it.species }.distinct().size } ?: 0
    }

    private fun familyIdOf(normalizedSpecies: String): String? =
        rowsBySpecies[normalizedSpecies]?.firstOrNull()?.familyId

    companion object {
        /** Bump on any incompatible snapshot layout change. */
        const val SUPPORTED_SCHEMA_VERSION = 1
        private const val MAX_HALF_LEVEL_INDEX = 100
        private const val MAX_BASE_STAT = 1000
        private const val MAX_EVOLUTION_CANDY_COST = 1000

        val TYPES = setOf("normal", "fire", "water", "electric", "grass", "ice", "fighting", "poison", "ground",
            "flying", "psychic", "bug", "rock", "ghost", "dragon", "dark", "steel", "fairy")

        /**
         * Synthetic/test factory: builds the immutable indexes from rows without the
         * asset-level validation requirements (which apply to [load] only).
         */
        fun fromRows(
            profiles: List<Profile>,
            cpMultipliers: Map<Double, Double>,
            metadata: Metadata = Metadata(0, "", "", "")
        ): RecognitionSnapshot = RecognitionSnapshot(
            metadata = metadata,
            profiles = profiles.toList(),
            cpMultipliers = cpMultipliers.toMap(),
            rowsBySpecies = profiles.groupBy { it.species.lowercase() },
            rowsByFamily = profiles.groupBy { it.familyId },
            rowsByCandy = profiles.groupBy { it.candySpecies.lowercase() }
        )

        /** Strict asset load: any structural, domain or revision failure throws (fail closed). */
        fun load(reader: Reader): RecognitionSnapshot {
            val root = JSONObject(reader.readText())
            val schemaVersion = root.getInt("version")
            require(schemaVersion == SUPPORTED_SCHEMA_VERSION)
            val source = root.getJSONObject("source")
            val sourceRevision = source.getString("revision")
            val sourceSha256 = source.getString("sha256")
            val namesSha256 = source.getString("namesSha256")
            require(sourceRevision.matches(Regex("[a-f0-9]{40}")))
            require(sourceSha256.matches(Regex("[a-f0-9]{64}")))
            require(namesSha256.matches(Regex("[a-f0-9]{64}")))
            val multiplierData = root.getJSONObject("cpMultipliers")
            require(multiplierData.getInt("version") == 1)
            require(multiplierData.getString("method") == "float32_integer_rms_half")
            val multiplierValues = multiplierData.getJSONObject("values")
            val expectedLevels = (0..MAX_HALF_LEVEL_INDEX).map { 1.0 + it / 2.0 }
            require(multiplierValues.keys().asSequence().toSet() == expectedLevels.map { it.toString() }.toSet())
            val cpMultipliers = expectedLevels.associateWith { level ->
                val raw = multiplierValues[level.toString()]
                require(raw is Number)
                raw.toDouble().also { require(it.isFinite() && it > 0.0 && it < 1.0) }
            }
            require(cpMultipliers.values.zipWithNext().all { (a, b) -> a < b })
            val rows = root.getJSONArray("profiles")
            val profiles = (0 until rows.length()).map { index ->
                val row = rows.getJSONObject(index)
                val forms = row.getJSONArray("forms").let { array ->
                    (0 until array.length()).map { array.getString(it) }.toSet()
                }
                val types = row.getJSONArray("types").let { array ->
                    (0 until array.length()).map { array.getString(it) }.toSet()
                }
                val stats = ProfileStats(row.getInt("atk"), row.getInt("def"), row.getInt("sta"))
                val species = row.getString("species")
                val family = row.getString("familyId")
                val candy = row.getString("candySpecies")
                require(species.isNotBlank() && candy.isNotBlank() && family.startsWith("FAMILY_"))
                require(forms.isNotEmpty() && forms.none { it.isBlank() })
                require(types.size in 1..2 && TYPES.containsAll(types))
                require(listOf(stats.atk, stats.def, stats.sta).all { it in 1..MAX_BASE_STAT })
                val evolutionCosts = if (!row.has("evolutionCandyCosts") || row.isNull("evolutionCandyCosts")) null
                    else row.getJSONArray("evolutionCandyCosts").let { a -> (0 until a.length()).map { i ->
                        val value = a.get(i)
                        require(value is Int && value in 0..MAX_EVOLUTION_CANDY_COST)
                        value
                    }.toSet() }
                Profile(species, forms, stats, types, family, candy, evolutionCosts)
            }
            require(profiles.map { it.species }.distinct().size == root.getInt("speciesCount"))
            val metadata = Metadata(schemaVersion, sourceRevision, sourceSha256, namesSha256)
            return fromRows(profiles, cpMultipliers, metadata)
        }
    }
}

/**
 * Bounded single-owner access to the app-scoped [RecognitionSnapshot]. Loads once; a
 * failed load is remembered and stays failed (fail closed for recognition authority) —
 * never silently falling back to legacy family/base-stat data.
 */
internal object RecognitionSnapshotHolder {
    private const val TAG = "RecognitionSnapshot"
    private const val ASSET_PATH = "data/recognition_profiles.json"

    @Volatile
    private var cached: RecognitionSnapshot? = null

    @Volatile
    private var loadFailed = false

    fun getOrNull(context: Context): RecognitionSnapshot? =
        getOrNull(context) { ctx ->
            ctx.assets.open(ASSET_PATH).bufferedReader().use(RecognitionSnapshot::load)
        }

    /** Test seam: inject the asset loader (JVM tests have no packaged Android assets). */
    @VisibleForTesting
    internal fun getOrNull(context: Context, loader: (Context) -> RecognitionSnapshot?): RecognitionSnapshot? {
        cached?.let { return it }
        return synchronized(this) {
            when {
                cached != null -> cached
                loadFailed -> null
                else -> {
                    loadFailed = true
                    val loaded = runCatching { loader(context) }.getOrNull()
                    if (loaded != null) {
                        loadFailed = false
                        Log.i(TAG, "Recognition snapshot loaded: revision=${loaded.metadata.revisionId} " +
                            "species=${loaded.canonicalSpecies.size} rows=${loaded.profiles.size}")
                    } else {
                        Log.e(TAG, "Recognition snapshot failed to load; recognition stays fail-closed")
                    }
                    cached = loaded
                    cached
                }
            }
        }
    }

    /** Canonical species for the recognition parser; empty (fail closed) when unavailable. */
    fun canonicalSpecies(context: Context): List<String> =
        getOrNull(context)?.canonicalSpecies?.sorted().orEmpty()

    /** Bounded revision identifier for diagnostics; null when the snapshot is unavailable. */
    fun recognitionRevision(context: Context): String? =
        getOrNull(context)?.metadata?.revisionId

    /** Test seam: seed the holder with an explicitly loaded snapshot (bypasses assets). */
    @VisibleForTesting
    internal fun setForTest(snapshot: RecognitionSnapshot) {
        synchronized(this) {
            cached = snapshot
            loadFailed = false
        }
    }

    @VisibleForTesting
    internal fun reset() {
        cached = null
        loadFailed = false
    }
}
