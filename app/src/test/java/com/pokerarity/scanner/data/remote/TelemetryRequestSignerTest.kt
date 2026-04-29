// Purpose: Verify deterministic telemetry request signing.
package com.pokerarity.scanner.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryRequestSignerTest {
    @Test
    fun buildSignatureFields_isDeterministicForFixedInputs() {
        val fields = TelemetryRequestSigner.buildSignatureFields(
            apiKey = "secret",
            payload = """{"species":"Raichu"}""",
            timestampMs = 1234L,
            nonce = "nonce"
        )

        assertEquals("1234", fields["client_timestamp_ms"])
        assertEquals("nonce", fields["client_nonce"])
        assertEquals(
            "94a23161911fe454ce49a18e26dd118567c7f8f03cae1a72423834ece83f932f",
            fields["payload_sha256"]
        )
        assertEquals(
            "4b6c08617697141b186d15f940a07b79e4de580983cd94d9a30b24fdddcb5a1e",
            fields["client_signature"]
        )
    }

    @Test
    fun buildSignatureFields_skipsBlankSecret() {
        assertTrue(TelemetryRequestSigner.buildSignatureFields("", "payload").isEmpty())
    }
}
