package com.pokerarity.scanner

import com.pokerarity.scanner.service.ScreenCaptureService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaApplicationIdentityTest {
    @Test
    fun qaVariantScopesItsInternalBroadcastPermissionToItsPackage() {
        assertTrue(
            BuildConfig.APPLICATION_ID == "com.pokerarity.scanner.sprint6qa" ||
                BuildConfig.APPLICATION_ID.startsWith("com.pokerarity.scanner.sprint6qa."),
        )
        assertEquals(
            "${BuildConfig.APPLICATION_ID}.permission.INTERNAL_BROADCAST",
            ScreenCaptureService.INTERNAL_BROADCAST_PERMISSION,
        )
    }

    @Test
    fun qaVariantDoesNotReadOrDownloadRemoteMetadataOverrides() {
        assertFalse(BuildConfig.REMOTE_METADATA_SYNC_ENABLED)
    }

    @Test
    fun diagnosticsAreEnabledOnlyForTheDedicatedDiagnosticQaIdentity() {
        val legacyDiagnosticIdentity = BuildConfig.APPLICATION_ID.endsWith(".sprint6qa.diag1")
        val exactFrameDiagnosticIdentity = BuildConfig.APPLICATION_ID.endsWith(".sprint6qa.diag2") ||
            BuildConfig.APPLICATION_ID.endsWith(".sprint6qa.diag3") ||
            BuildConfig.APPLICATION_ID.endsWith(".sprint6qa.diag4")
        val dedicatedIdentity = legacyDiagnosticIdentity || exactFrameDiagnosticIdentity
        assertEquals(dedicatedIdentity, BuildConfig.QA_VARIANT_DIAGNOSTICS)
        if (BuildConfig.QA_EXACT_FRAME_CAPTURE_BUILD) assertTrue(exactFrameDiagnosticIdentity)
        if (dedicatedIdentity) {
            assertTrue(BuildConfig.QA_SOURCE_SHA.matches(Regex("[a-fA-F0-9]{40}")))
        } else {
            assertEquals("", BuildConfig.QA_SOURCE_SHA)
        }
        if (!exactFrameDiagnosticIdentity) assertFalse(BuildConfig.QA_EXACT_FRAME_CAPTURE_BUILD)

        assertTrue(
            com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.enabled(
                isDebug = true,
                qaFlag = true,
                applicationId = "com.pokerarity.scanner.sprint6qa.diag1",
                sourceSha = "a".repeat(40)
            )
        )
        assertTrue(
            com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.enabled(
                isDebug = true,
                qaFlag = true,
                applicationId = "com.pokerarity.scanner.sprint6qa.diag2",
                sourceSha = "b".repeat(40)
            )
        )
        assertTrue(
            com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.enabled(
                isDebug = true,
                qaFlag = true,
                applicationId = "com.pokerarity.scanner.sprint6qa.diag3",
                sourceSha = "b".repeat(40)
            )
        )
        assertTrue(
            com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.enabled(
                isDebug = true,
                qaFlag = true,
                applicationId = "com.pokerarity.scanner.sprint6qa.diag4",
                sourceSha = "b".repeat(40)
            )
        )
        assertFalse(
            com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.enabled(
                isDebug = false,
                qaFlag = true,
                applicationId = "com.pokerarity.scanner.sprint6qa.diag1",
                sourceSha = "a".repeat(40)
            )
        )
        assertFalse(
            com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.enabled(
                isDebug = true,
                qaFlag = true,
                applicationId = "com.pokerarity.scanner",
                sourceSha = "a".repeat(40)
            )
        )
        assertFalse(
            com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.enabled(
                isDebug = true,
                qaFlag = true,
                applicationId = "com.pokerarity.scanner.sprint6qa.diag1",
                sourceSha = "not-a-commit"
            )
        )

        assertTrue(com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.exactFrameCaptureEnabled(
            isDebug = true,
            qaFlag = true,
            applicationId = "com.pokerarity.scanner.sprint6qa.diag2",
            sourceSha = "c".repeat(40),
            captureBuildFlag = true,
            runtimeOptIn = true
        ))
        assertTrue(com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.exactFrameCaptureEnabled(
            isDebug = true,
            qaFlag = true,
            applicationId = "com.pokerarity.scanner.sprint6qa.diag3",
            sourceSha = "c".repeat(40),
            captureBuildFlag = true,
            runtimeOptIn = true
        ))
        assertTrue(com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.exactFrameCaptureEnabled(
            isDebug = true,
            qaFlag = true,
            applicationId = "com.pokerarity.scanner.sprint6qa.diag4",
            sourceSha = "c".repeat(40),
            captureBuildFlag = true,
            runtimeOptIn = true
        ))
        assertFalse(com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.exactFrameCaptureEnabled(
            isDebug = true,
            qaFlag = true,
            applicationId = "com.pokerarity.scanner.sprint6qa.diag1",
            sourceSha = "c".repeat(40),
            captureBuildFlag = true,
            runtimeOptIn = true
        ))
        assertFalse(com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.exactFrameCaptureEnabled(
            isDebug = false,
            qaFlag = true,
            applicationId = "com.pokerarity.scanner.sprint6qa.diag2",
            sourceSha = "c".repeat(40),
            captureBuildFlag = true,
            runtimeOptIn = true
        ))
        assertFalse(com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.exactFrameCaptureEnabled(
            isDebug = true,
            qaFlag = true,
            applicationId = "com.pokerarity.scanner.sprint6qa.diag2",
            sourceSha = "c".repeat(40),
            captureBuildFlag = true,
            runtimeOptIn = false
        ))
        assertFalse(com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.exactFrameCaptureEnabled(
            isDebug = true,
            qaFlag = true,
            applicationId = "com.pokerarity.scanner",
            sourceSha = "c".repeat(40),
            captureBuildFlag = true,
            runtimeOptIn = true
        ))
    }
}
