// Purpose: Build signed telemetry request fields without exposing signing logic to callers.
package com.pokerarity.scanner.data.remote

import java.util.Locale
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object TelemetryRequestSigner {
    private const val HMAC_ALGORITHM = "HmacSHA256"

    fun buildSignatureFields(
        apiKey: String,
        payload: String,
        timestampMs: Long = System.currentTimeMillis(),
        nonce: String = UUID.randomUUID().toString()
    ): Map<String, String> {
        val secret = apiKey.trim()
        if (secret.isBlank()) return emptyMap()
        val payloadHash = sha256Hex(payload)
        val signingBase = "$timestampMs.$nonce.$payloadHash"
        return mapOf(
            "client_timestamp_ms" to timestampMs.toString(),
            "client_nonce" to nonce,
            "payload_sha256" to payloadHash,
            "client_signature" to hmacSha256Hex(secret, signingBase)
        )
    }

    private fun sha256Hex(value: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        return digest.digest(value.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun hmacSha256Hex(secret: String, value: String): String {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM))
        return mac.doFinal(value.toByteArray(Charsets.UTF_8)).toHex()
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(Locale.US, byte) }
}
