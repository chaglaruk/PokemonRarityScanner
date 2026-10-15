package com.pokerarity.scanner.data.model

import com.pokerarity.scanner.data.local.db.ScanHistoryEntity
import com.pokerarity.scanner.util.ocr.ScanDecision
import com.pokerarity.scanner.util.ocr.ScanDecisionSeverity
import com.pokerarity.scanner.util.ocr.ScanDecisionType
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanDecisionSupportTest {
    @Test fun reviewOnlyGuidanceIsVisibleUiContent() {
        val review = ScanDecisionSupport("", "", "", 70, "Review recommended", "CP missing")
        assertTrue(review.hasVisibleUiContent())
    }

    @Test fun historicalAbsentGuidanceRemainsUnknown() {
        val legacy = ScanHistoryEntity(
            pokemonName = "Pikachu", cp = null, hp = null, caughtDate = null, rawOcrText = ""
        )
        assertNull(legacy.toScanDecisionSupport())
        assertNull(legacy.toUiPokemon().decisionSupport)
    }

    @Test fun persistedReviewSurvivesUiMappingWithoutChangingUnknownCpHp() {
        val row = ScanHistoryEntity(
            pokemonName = "Farfetch'd", cp = null, hp = null, caughtDate = null,
            rawOcrText = "", scanConfidenceScore = 70,
            scanConfidenceLabel = "Review recommended", scanConfidenceDetail = "CP missing, HP missing"
        )
        val ui = row.toUiPokemon()
        assertEquals(70, ui.decisionSupport?.scanConfidenceScore)
        assertEquals("Review recommended", ui.decisionSupport?.scanConfidenceLabel)
        assertEquals("Unknown", ui.cpDisplayText)
        assertNull(ui.hp)
        assertTrue(ui.decisionSupport!!.hasVisibleUiContent())
    }
    private fun pokemonWithGate(type: ScanDecisionType, confidence: Float): PokemonData = PokemonData(
        cp = 468, hp = 69, maxHp = 69, name = "Farfetch'd", realName = "Farfetch'd",
        candyName = null, megaEnergy = null, weight = null, height = null,
        stardust = null, caughtDate = Date(0L),
        scanDecision = ScanDecision(
            decision = type, confidence = confidence, severity = ScanDecisionSeverity.WARNING,
            userSafeReason = "", developerReasons = emptyList(), evidenceUsed = emptyList(),
            evidenceMissing = emptyList(), recommendedNextAction = "",
            retryEligible = false, mayShowOverlay = true, maySaveScan = true,
            collectionSafe = type == ScanDecisionType.ACCEPT
        )
    )

    @Test fun completeFieldsCannotOverrideLowConfidenceRecognitionGate() {
        val data = pokemonWithGate(ScanDecisionType.ACCEPT_LOW_CONFIDENCE, 0.63f)
        val metadata = data.scanReviewMetadata()!!
        assertEquals(63, metadata.score)
        assertEquals(ScanReviewCodes.REVIEW, metadata.labelCode)
        assertEquals("fields:cp_present,hp_present,date_present", metadata.detailCode)
        val persisted = ScanHistoryEntity(
            pokemonName = "Farfetch'd", cp = data.cp, hp = data.hp,
            caughtDate = data.caughtDate, rawOcrText = "",
            scanConfidenceScore = metadata.score,
            scanConfidenceLabel = metadata.labelCode,
            scanConfidenceDetail = metadata.detailCode
        )
        val ui = persisted.toUiPokemon()
        assertEquals("Review recommended",
            ui.decisionSupport!!.scanReviewPresentation(Locale.ENGLISH).label)
        assertEquals("Kontrol önerilir",
            ui.decisionSupport!!.scanReviewPresentation(Locale.forLanguageTag("tr-TR")).label)
    }

    @Test fun storedReasonCodesLocalizeAtPresentationTime() {
        val accepted = pokemonWithGate(ScanDecisionType.ACCEPT, 0.82f).scanReviewMetadata()!!
        assertEquals(ScanReviewCodes.ACCEPTED, accepted.labelCode)
        val support = ScanDecisionSupport("", "", "", accepted.score, accepted.labelCode, accepted.detailCode)
        val english = support.scanReviewPresentation(Locale.ENGLISH)
        val turkish = support.scanReviewPresentation(Locale.forLanguageTag("tr-TR"))
        assertEquals("Scan accepted", english.label)
        assertEquals("82% - CP available, HP available, date available", english.detail)
        assertEquals("Tarama kabul edildi", turkish.label)
        assertEquals("82% - CP mevcut, HP mevcut, tarih mevcut", turkish.detail)
        assertEquals("Tanıma güveni", turkish.title)
    }

    @Test fun missingGateDoesNotInventRecognitionConfidence() {
        val data = pokemonWithGate(ScanDecisionType.RETRY, 0.90f)
        assertNull(data.scanReviewMetadata())
        assertNull(data.copy(scanDecision = null).scanReviewMetadata())
    }
}
