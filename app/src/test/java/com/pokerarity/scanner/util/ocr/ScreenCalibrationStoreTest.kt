package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * Phase 2B: the calibration store persists and restores records, and every malformed or
 * structurally invalid entry fails closed and is cleared.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenCalibrationStoreTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = ScreenCalibrationStore(context)
    private val signature = requireNotNull(
        DisplayGeometrySignature.from(1080, 2340, 450, 900, 1950)
    )

    private fun record(
        revision: Int = CALIBRATION_SCHEMA_REVISION,
        sig: DisplayGeometrySignature = signature
    ) = ScreenCalibrationRecord(
        schemaRevision = revision,
        signature = sig,
        hpBar = requireNotNull(NormalizedRect.fromRect(Rect(226, 882, 674, 894), 900, 1950)),
        detailCard = NormalizedRect(0f, 0.334f, 1f, 0.7f),
        nameBand = NormalizedRect(0.12f, 0.362f, 0.88f, 0.452f),
        createdAtMs = 1_000L,
        lastValidatedAtMs = 2_000L,
        validationSuccesses = 3,
        consecutiveValidationFailures = 1
    )

    @Test
    fun calibrationPersistsAndRestores() {
        val original = record()
        store.save(original)

        val restored = store.load(signature.stableKey)

        assertNotNull(restored)
        restored!!
        assertEquals(original.schemaRevision, restored.schemaRevision)
        assertEquals(original.signature, restored.signature)
        assertEquals(original.validationSuccesses, restored.validationSuccesses)
        assertEquals(original.consecutiveValidationFailures, restored.consecutiveValidationFailures)
        assertEquals(original.createdAtMs, restored.createdAtMs)
        assertEquals(original.lastValidatedAtMs, restored.lastValidatedAtMs)
    }

    @Test
    fun roundTripGeometryIsStable() {
        val original = record()
        store.save(original)

        val restored = store.load(signature.stableKey)!!

        // Fixed-precision float round trip: restored geometry must reproduce the same
        // recognition-space rects exactly at the recorded frame dimensions.
        val dims = 900 to 1950
        fun ScreenCalibrationRecord.barAt() = requireNotNull(hpBar.toRect(dims.first, dims.second))
        fun ScreenCalibrationRecord.cardAt() = requireNotNull(detailCard.toRect(dims.first, dims.second))
        fun ScreenCalibrationRecord.bandAt() = requireNotNull(nameBand.toRect(dims.first, dims.second))
        assertEquals(original.barAt(), restored.barAt())
        assertEquals(original.cardAt(), restored.cardAt())
        assertEquals(original.bandAt(), restored.bandAt())
    }

    @Test
    fun recordSurvivesANewStoreInstanceOverTheSameFile() {
        val original = record()
        store.save(original)

        // A fresh store instance over the same prefs file must observe the same record
        // (fixed-precision float round trip compared in recognition space).
        val reopened = ScreenCalibrationStore(
            context.getSharedPreferences("screen_calibration_geometry", Context.MODE_PRIVATE)
        )
        val restored = reopened.load(signature.stableKey)!!

        assertEquals(original.signature, restored.signature)
        assertEquals(original.validationSuccesses, restored.validationSuccesses)
        assertEquals(original.consecutiveValidationFailures, restored.consecutiveValidationFailures)
        assertEquals(original.createdAtMs, restored.createdAtMs)
        assertEquals(original.lastValidatedAtMs, restored.lastValidatedAtMs)
        val dims = 900 to 1950
        fun ScreenCalibrationRecord.barAt() = requireNotNull(hpBar.toRect(dims.first, dims.second))
        fun ScreenCalibrationRecord.cardAt() = requireNotNull(detailCard.toRect(dims.first, dims.second))
        fun ScreenCalibrationRecord.bandAt() = requireNotNull(nameBand.toRect(dims.first, dims.second))
        assertEquals(original.barAt(), restored.barAt())
        assertEquals(original.cardAt(), restored.cardAt())
        assertEquals(original.bandAt(), restored.bandAt())
    }

    @Test
    fun corruptedEntryFailsSafelyAndIsCleared() {
        store.save(record())
        val prefs = context.getSharedPreferences("screen_calibration_geometry", Context.MODE_PRIVATE)
        prefs.edit().putString(store.entryKey(signature.stableKey), "not|a|calibration").commit()

        assertNull(store.load(signature.stableKey))
        assertNull("corrupt entry must be removed", prefs.getString(store.entryKey(signature.stableKey), null))
    }

    @Test
    fun truncatedEntryFailsSafely() {
        store.save(record())
        val prefs = context.getSharedPreferences("screen_calibration_geometry", Context.MODE_PRIVATE)
        prefs.edit().putString(store.entryKey(signature.stableKey), "v1|1080x2340").commit()

        assertNull(store.load(signature.stableKey))
    }

    @Test
    fun entriesAreIndependentPerSignature() {
        val other = requireNotNull(
            DisplayGeometrySignature.from(1440, 3120, 560, 900, 1950)
        )
        store.save(record())
        store.save(record(sig = other))

        assertEquals(signature.stableKey, store.load(signature.stableKey)!!.signature.stableKey)
        assertEquals(other.stableKey, store.load(other.stableKey)!!.signature.stableKey)
        store.clear(signature.stableKey)
        assertNull(store.load(signature.stableKey))
        assertNotNull(store.load(other.stableKey))
    }

    @Test
    fun clearRemovesTheEntry() {
        store.save(record())
        store.clear(signature.stableKey)
        assertNull(store.load(signature.stableKey))
    }

    @Test
    fun encodingIsDeterministicForTheSameRecord() {
        val first = ScreenCalibrationStore.encode(record())
        val second = ScreenCalibrationStore.encode(record())
        assertEquals(first, second)
        assertTrue(first.startsWith("v$CALIBRATION_SCHEMA_REVISION|${signature.stableKey}|"))
    }

    @Test
    fun encodingIsLocaleStableAndRoundTripsWithDecimalCommaLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val encoded = ScreenCalibrationStore.encode(record())
            val hpBarComponents = encoded.split("|")[2].split(",")

            assertEquals(4, hpBarComponents.size)
            assertTrue(hpBarComponents.all { it.contains(".") })

            store.save(record())
            assertNotNull(store.load(signature.stableKey))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun saveRefusesStructurallyInvalidRecords() {
        store.save(record().copy(hpBar = NormalizedRect(0.9f, 0.5f, 0.2f, 0.6f)))
    }
}
