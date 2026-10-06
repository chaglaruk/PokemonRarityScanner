package com.pokerarity.scanner

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.pokerarity.scanner.util.ocr.RecognitionSnapshot
import com.pokerarity.scanner.util.ocr.RecognitionSnapshotHolder
import java.io.File

/**
 * Phase 2D test support: JVM tests do not package Android assets, so the recognition
 * snapshot holder is seeded explicitly from the checked-in generated assets.
 */
internal object RecognitionSnapshotTestSupport {

    fun assetDir(): File =
        listOf(File("src/main/assets/data"), File("app/src/main/assets/data"))
            .first { it.isDirectory }

    fun loadRealSnapshot(): RecognitionSnapshot =
        File(assetDir(), "recognition_profiles.json").reader().use(RecognitionSnapshot::load)

    /** Seeds the process-wide holder with the real generated snapshot. */
    fun seedHolder(): RecognitionSnapshot {
        val snapshot = loadRealSnapshot()
        RecognitionSnapshotHolder.setForTest(snapshot)
        return snapshot
    }

    fun resetHolder() {
        RecognitionSnapshotHolder.reset()
    }

    /** Canonical species (display casing) for TextParser injection. */
    fun canonicalSpecies(): List<String> {
        val names = Gson().fromJson<List<String>>(
            File(assetDir(), "pokemon_names.json").reader(),
            object : TypeToken<List<String>>() {}.type
        )
        return names
    }
}
