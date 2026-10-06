package com.pokerarity.scanner.util.ocr

import android.content.Context
import android.graphics.Rect
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2B calibration health: build, validate, bounded drift, repeated-failure
 * invalidation, seeding eligibility and recovery, all against the persisted store.
 * Geometry fixtures mirror the measured S25 corpus layout at 900x1950 recognition space.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ScreenCalibrationManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val signature = requireNotNull(
        DisplayGeometrySignature.from(1080, 2340, 450, 900, 1950)
    )
    private val frameBar = Rect(226, 882, 674, 894)
    private val frameCard = Rect(0, 651, 900, 1365)
    private val frameCardNormalized = requireNotNull(
        NormalizedRect.fromRect(frameCard, 900, 1950)
    )

    private lateinit var store: ScreenCalibrationStore
    private lateinit var manager: ScreenCalibrationManager

    @Before
    fun setUp() {
        store = ScreenCalibrationStore(context)
        context.getSharedPreferences("screen_calibration_geometry", Context.MODE_PRIVATE)
            .edit().clear().commit()
        manager = ScreenCalibrationManager(store)
    }

    private fun geometry(liveBar: Rect? = frameBar, card: NormalizedRect? = frameCardNormalized) =
        FrameGeometry(liveBarRect = liveBar, detailCard = card, frameWidth = 900, frameHeight = 1950)

    private fun coldMiss() = manager.resolveForFrame(signature, geometry(liveBar = null))

    private fun observe(pre: FrameResolution, bar: Rect? = frameBar) =
        manager.onFrameGeometryObserved(signature, pre, geometry(liveBar = bar))

    private fun persistedRecord(): ScreenCalibrationRecord? = store.load(signature.stableKey)

    // ── cold pass / autoconfig ──────────────────────────────────────────

    @Test
    fun coldFrameWithCoherentEvidenceBuildsAndPersistsCalibration() {
        val resolution = observe(coldMiss())

        assertEquals(CalibrationResolution.REBUILT, resolution.resolution)
        val persisted = persistedRecord()
        assertNotNull("a rebuilt calibration must be persisted", persisted)
        persisted!!
        assertEquals(226, requireNotNull(persisted.hpBar.toRect(900, 1950)).left)
        assertTrue(persisted.validationSuccesses >= 1)
        assertTrue(persisted.nameBand.bottom <= persisted.hpBar.top + 1e-4f)
    }

    @Test
    fun coldFrameWithoutBarEvidenceStaysUnavailable() {
        val resolution = observe(coldMiss(), bar = null)

        assertEquals(CalibrationResolution.UNAVAILABLE, resolution.resolution)
        assertNull(persistedRecord())
    }

    @Test
    fun coldFrameWithBarOutsideDetailCardDoesNotFabricateCalibration() {
        val pre = coldMiss()
        val barAboveCard = Rect(226, 100, 674, 112)

        val resolution = manager.onFrameGeometryObserved(
            signature, pre, geometry(liveBar = barAboveCard)
        )

        assertEquals(CalibrationResolution.UNAVAILABLE, resolution.resolution)
        assertNull(persistedRecord())
    }

    // ── warm pass / validation ──────────────────────────────────────────

    @Test
    fun warmFrameWithMatchingAnchorValidatesHealthy() {
        observe(coldMiss())
        val before = persistedRecord()!!

        val resolution = observe(manager.resolveForFrame(signature, geometry(liveBar = null)))

        assertEquals(CalibrationResolution.HIT_VALIDATED, resolution.resolution)
        val after = persistedRecord()!!
        assertEquals(before.validationSuccesses + 1, after.validationSuccesses)
        assertEquals(0, after.consecutiveValidationFailures)
    }

    @Test
    fun boundedDriftDegradesButDoesNotDestroyCalibration() {
        observe(coldMiss())
        val driftedBar = Rect(180, 882, 478, 894) // measured mid-animation frame: Δleft ≈ 0.051w

        val resolution = observe(
            manager.resolveForFrame(signature, geometry(liveBar = null)),
            bar = driftedBar
        )

        assertEquals(CalibrationResolution.HIT_DEGRADED, resolution.resolution)
        val after = persistedRecord()!!
        assertEquals(1, after.consecutiveValidationFailures)
        assertEquals(
            "bounded drift must not silently rewrite trusted geometry",
            226,
            requireNotNull(after.hpBar.toRect(900, 1950)).left
        )
        assertTrue(resolution.reasonCodes.contains("bar_left_drift"))
    }

    @Test
    fun repeatedValidationFailuresInvalidateTheCalibration() {
        observe(coldMiss())
        val driftedBar = Rect(180, 882, 478, 894)
        repeat(CalibrationTolerances.REBUILD_AFTER_CONSECUTIVE_FAILURES) { attempt ->
            val resolution = observe(
                manager.resolveForFrame(signature, geometry(liveBar = null)),
                bar = driftedBar
            )
            if (attempt < CalibrationTolerances.REBUILD_AFTER_CONSECUTIVE_FAILURES - 1) {
                assertEquals(CalibrationResolution.HIT_DEGRADED, resolution.resolution)
            } else {
                assertEquals(CalibrationResolution.INVALIDATED, resolution.resolution)
            }
        }
        assertNull("repeated failures must invalidate, not retry stale geometry forever", persistedRecord())
    }

    @Test
    fun majorDriftInvalidatesImmediately() {
        observe(coldMiss())
        // Far displaced but still inside the detail card: a coherent new layout.
        val displacedBar = Rect(500, 700, 800, 712)

        val resolution = observe(
            manager.resolveForFrame(signature, geometry(liveBar = null)),
            bar = displacedBar
        )

        assertEquals(CalibrationResolution.INVALIDATED_REBUILT, resolution.resolution)
        val after = persistedRecord()
        assertNotNull(after)
        assertEquals(
            "the rebuild must adopt the current layout, not the stale one",
            500,
            requireNotNull(requireNotNull(after).hpBar.toRect(900, 1950)).left
        )
    }

    @Test
    fun newHealthyCalibrationRecoversAfterInvalidation() {
        observe(coldMiss())
        val driftedBar = Rect(180, 882, 478, 894)
        repeat(CalibrationTolerances.REBUILD_AFTER_CONSECUTIVE_FAILURES) {
            observe(manager.resolveForFrame(signature, geometry(liveBar = null)), bar = driftedBar)
        }
        assertNull(persistedRecord())

        val rebuilt = observe(coldMiss(), bar = frameBar)
        assertEquals(CalibrationResolution.REBUILT, rebuilt.resolution)
        val warm = observe(manager.resolveForFrame(signature, geometry(liveBar = null)))
        assertEquals(CalibrationResolution.HIT_VALIDATED, warm.resolution)
        assertEquals(226, requireNotNull(requireNotNull(persistedRecord()).hpBar.toRect(900, 1950)).left)
    }

    @Test
    fun validationWithoutScrollStateConfirmationChangesNoCounters() {
        observe(coldMiss())
        val before = persistedRecord()!!
        val shiftedCard = NormalizedRect(0f, frameCardNormalized.top + 0.10f, 1f, 0.8f)

        val resolution = manager.onFrameGeometryObserved(
            signature,
            manager.resolveForFrame(signature, geometry(liveBar = null, card = shiftedCard)),
            geometry(liveBar = frameBar, card = shiftedCard)
        )

        assertEquals(CalibrationResolution.NOT_APPLICABLE, resolution.resolution)
        val after = persistedRecord()!!
        assertEquals(before.validationSuccesses, after.validationSuccesses)
        assertEquals(before.consecutiveValidationFailures, after.consecutiveValidationFailures)
    }

    // ── seeding ─────────────────────────────────────────────────────────

    @Test
    fun matchingScrollStateOffersTheCalibratedBarAsSeed() {
        observe(coldMiss())

        val pre = manager.resolveForFrame(signature, geometry(liveBar = null))

        assertEquals(frameBar, pre.seededBarRect)
        assertNotNull(pre.toHint())
        assertEquals(signature.stableKey, pre.toHint()!!.signatureKey)
    }

    @Test
    fun differentScrollStateOffersNoSeed() {
        observe(coldMiss())
        val scrolledCard = NormalizedRect(0f, frameCardNormalized.top + 0.05f, 1f, 0.75f)

        val pre = manager.resolveForFrame(signature, geometry(liveBar = null, card = scrolledCard))

        assertNull(pre.seededBarRect)
        assertNull(pre.toHint())
    }

    @Test
    fun seededFallbackConsumesCalibrationWithoutHealthPenalty() {
        observe(coldMiss())
        val before = persistedRecord()!!
        val pre = manager.resolveForFrame(signature, geometry(liveBar = null))

        val resolution = observe(pre, bar = null)

        assertEquals(CalibrationResolution.HIT_SEEDED, resolution.resolution)
        val after = persistedRecord()!!
        assertEquals(before.validationSuccesses, after.validationSuccesses)
        assertEquals(before.consecutiveValidationFailures, after.consecutiveValidationFailures)
    }

    @Test
    fun warmFrameWithNoLiveBarAndNoSeedLeavesHealthUntouched() {
        observe(coldMiss())
        val before = persistedRecord()!!
        // A frame at an unconfirmed scroll state: no seed eligibility and no judgeable bar.
        val shiftedCard = NormalizedRect(0f, frameCardNormalized.top + 0.10f, 1f, 0.8f)

        val resolution = observe(
            manager.resolveForFrame(signature, geometry(liveBar = null, card = shiftedCard)),
            bar = null
        )

        assertEquals(CalibrationResolution.NOT_APPLICABLE, resolution.resolution)
        assertEquals(before, persistedRecord())
    }

    // ── schema / corruption invalidation ────────────────────────────────

    @Test
    fun schemaRevisionMismatchInvalidatesThePersistedRecord() {
        store.save(record(revision = CALIBRATION_SCHEMA_REVISION + 1))

        val pre = manager.resolveForFrame(signature, geometry(liveBar = null))

        assertEquals(CalibrationResolution.SCHEMA_INVALIDATED, pre.resolution)
        assertNull("stale-schema record must be cleared, not kept", persistedRecord())
    }

    @Test
    fun staleSchemaRecordCanBeRebuiltFromCoherentEvidence() {
        store.save(record(revision = CALIBRATION_SCHEMA_REVISION + 1))

        val resolution = observe(manager.resolveForFrame(signature, geometry(liveBar = null)))

        assertEquals(CalibrationResolution.REBUILT, resolution.resolution)
        assertEquals(CALIBRATION_SCHEMA_REVISION, persistedRecord()!!.schemaRevision)
    }

    @Test
    fun missingRecordResolvesAsUnavailable() {
        val pre = manager.resolveForFrame(signature, geometry(liveBar = null))
        assertEquals(CalibrationResolution.UNAVAILABLE, pre.resolution)
        assertNull(pre.toHint())
    }

    private fun record(revision: Int): ScreenCalibrationRecord = ScreenCalibrationRecord(
        schemaRevision = revision,
        signature = signature,
        hpBar = requireNotNull(NormalizedRect.fromRect(frameBar, 900, 1950)),
        detailCard = frameCardNormalized,
        nameBand = NormalizedRect(0.12f, 0.362f, 0.88f, 0.452f),
        createdAtMs = 1L,
        lastValidatedAtMs = 1L,
        validationSuccesses = 1,
        consecutiveValidationFailures = 0
    )
}
