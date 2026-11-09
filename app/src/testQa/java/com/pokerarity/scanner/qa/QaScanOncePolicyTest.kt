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
        senderUid: Int = Process.SHELL_UID,
        sourceSha: String = validSha,
        sessionActive: Boolean = true,
        coordinatorStopped: Boolean = false
    ): Boolean = QaScanOncePolicy.mayTrigger(
        action, sdkInt, senderUid, sourceSha, sessionActive, coordinatorStopped
    )

    @Test
    fun onlyShellWithActiveConsentedSessionAndPinnedQaBuildCanRequestScan() {
        assertTrue(authorized())
        assertFalse(authorized(senderUid = Process.SYSTEM_UID))
        assertFalse(authorized(senderUid = Process.INVALID_UID))
        assertFalse(authorized(senderUid = 10567))
        assertFalse(authorized(sdkInt = 33))
        assertFalse(authorized(action = "unrelated.action"))
        assertFalse(authorized(sourceSha = ""))
        assertFalse(authorized(sourceSha = "not-a-sha"))
        assertFalse(authorized(sessionActive = false))
        assertFalse(authorized(coordinatorStopped = true))
    }
}
