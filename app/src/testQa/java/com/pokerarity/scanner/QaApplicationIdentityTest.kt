package com.pokerarity.scanner

import com.pokerarity.scanner.service.ScreenCaptureService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
        val dedicatedIdentity = BuildConfig.APPLICATION_ID.endsWith(".sprint6qa.diag1")
        assertEquals(dedicatedIdentity, BuildConfig.QA_VARIANT_DIAGNOSTICS)
        if (dedicatedIdentity) {
            assertTrue(BuildConfig.QA_SOURCE_SHA.matches(Regex("[a-fA-F0-9]{40}")))
        } else {
            assertEquals("", BuildConfig.QA_SOURCE_SHA)
        }

        assertTrue(
            com.pokerarity.scanner.util.vision.QaVariantDiagnosticPolicy.enabled(
                isDebug = true,
                qaFlag = true,
                applicationId = "com.pokerarity.scanner.sprint6qa.diag1",
                sourceSha = "a".repeat(40)
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
    }
}
