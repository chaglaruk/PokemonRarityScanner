package com.pokerarity.scanner.data.local.db

import java.util.UUID

/** Namespaces persisted passphrases used only by isolated database validation fixtures. */
internal object HistoryValidationPassphrasePreferences {
    fun nameFor(runId: UUID): String = "${DatabasePassphraseStore.PREFS_NAME}.validation.$runId"

    fun isValidName(name: String): Boolean {
        val prefix = "${DatabasePassphraseStore.PREFS_NAME}.validation."
        if (!name.startsWith(prefix)) return false
        val runId = name.removePrefix(prefix)
        return runCatching { UUID.fromString(runId).toString() == runId }.getOrDefault(false)
    }
}
