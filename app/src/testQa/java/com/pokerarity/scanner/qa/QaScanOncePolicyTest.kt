package com.pokerarity.scanner.qa

import android.os.Process
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QaScanOncePolicyTest {
    private val validSha = "0ac17ef26f271a8b5b009de50cec22f41d81ed83"

    private fun authorized(
        action: String? = QaScanOncePolicy.ACTION,
        sdkInt: Int = 36,
        senderAuthorized: Boolean = true,
        sourceSha: String = validSha,
        sessionActive: Boolean = true,
        coordinatorStopped: Boolean = false
    ): Boolean = QaScanOncePolicy.mayTrigger(
            action, sdkInt, senderAuthorized, sourceSha, sessionActive, coordinatorStopped
    )

    @Test
    fun senderAuthorizationAcceptsShellOrHiddenIdentityOnly() {
        assertTrue(QaScanOncePolicy.senderAuthorized(Process.SHELL_UID))
        // Android 16 hides the shell identity from the manifest receiver. Its
        // android.permission.DUMP requirement is asserted by the manifest test.
        assertTrue(QaScanOncePolicy.senderAuthorized(Process.INVALID_UID))
        assertFalse(QaScanOncePolicy.senderAuthorized(Process.SYSTEM_UID))
        assertFalse(QaScanOncePolicy.senderAuthorized(10567))
    }

    @Test
    fun onlyAuthorizedSenderWithActiveConsentedSessionAndPinnedQaBuildCanRequestScan() {
        assertTrue(authorized())
        assertFalse(authorized(senderAuthorized = false))
        assertFalse(authorized(sdkInt = 33))
        assertFalse(authorized(action = "unrelated.action"))
        assertFalse(authorized(sourceSha = ""))
        assertFalse(authorized(sourceSha = "not-a-sha"))
        assertFalse(authorized(sessionActive = false))
        assertFalse(authorized(coordinatorStopped = true))
    }
}
