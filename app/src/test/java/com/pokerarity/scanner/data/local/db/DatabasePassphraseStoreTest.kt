// Purpose: Verify database cleanup log labels do not expose local paths.
package com.pokerarity.scanner.data.local.db

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DatabasePassphraseStoreTest {
    @Test
    fun safeDatabaseArtifactLabelUsesOnlyFileName() {
        val label = DatabasePassphraseStore.safeDatabaseArtifactLabel(
            File("C:/Users/Player/AppData/Local/databases/pokerarity_db-wal")
        )

        assertEquals("pokerarity_db-wal", label)
        assertFalse(label.contains("Users", ignoreCase = true))
        assertFalse(label.contains("AppData", ignoreCase = true))
        assertFalse(label.contains("C:/", ignoreCase = true))
    }

    @Test
    fun safeDatabaseArtifactLabelFallsBackForBlankFileName() {
        assertEquals(
            "database-artifact",
            DatabasePassphraseStore.safeDatabaseArtifactLabel(File(""))
        )
    }
}
