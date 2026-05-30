// Purpose: Format debug log values without exposing local file paths.
package com.pokerarity.scanner.service

internal object SafeDebugLogValue {
    fun localFileReference(path: String?): String {
        return if (path.isNullOrBlank()) "absent" else "present"
    }
}
