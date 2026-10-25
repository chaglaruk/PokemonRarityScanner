package com.pokerarity.scanner.data.model

import com.google.gson.Gson

/** Compact, local-only serialization for the bounded RecognitionIdentity contract in History. */
object RecognitionIdentityHistoryCodec {
    private val gson = Gson()

    fun encode(identity: RecognitionIdentity?): String? = identity
        ?.takeIf(::hasValidFormState)
        ?.let { gson.toJson(it) }

    fun decode(encoded: String?): RecognitionIdentity? = encoded
        ?.takeIf { it.isNotBlank() }
        ?.let { raw ->
            runCatching {
                gson.fromJson(raw, RecognitionIdentity::class.java)?.takeIf(::hasValidFormState)
            }.getOrNull()
        }

    fun formPresentation(identity: RecognitionIdentity?): String? = identity
        ?.takeIf { it.speciesStatus == RecognitionSpeciesStatus.KNOWN }
        ?.let { value ->
        when (value.formStatus) {
            RecognitionFormStatus.KNOWN -> value.knownForm?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { "Form: $it" }
            RecognitionFormStatus.AMBIGUOUS -> value.ambiguousForms
                .map(String::trim)
                .filter(String::isNotEmpty)
                .distinct()
                .take(RecognitionIdentity.MAX_FORM_ALTERNATIVES)
                .takeIf { it.isNotEmpty() }
                ?.joinToString(" / ", prefix = "Possible forms: ")
            RecognitionFormStatus.UNKNOWN -> null
        }
    }

    private fun hasValidFormState(identity: RecognitionIdentity): Boolean = when (identity.formStatus) {
        RecognitionFormStatus.KNOWN -> identity.speciesStatus == RecognitionSpeciesStatus.KNOWN &&
            !identity.knownForm.isNullOrBlank() && identity.ambiguousForms.isEmpty()
        RecognitionFormStatus.AMBIGUOUS -> identity.speciesStatus == RecognitionSpeciesStatus.KNOWN &&
            identity.knownForm == null &&
            identity.ambiguousForms.isNotEmpty() &&
            identity.ambiguousForms.size <= RecognitionIdentity.MAX_FORM_ALTERNATIVES
        RecognitionFormStatus.UNKNOWN -> identity.knownForm == null && identity.ambiguousForms.isEmpty()
    }
}
