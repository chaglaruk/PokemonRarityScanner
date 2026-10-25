// Purpose: Verify database cleanup log labels do not expose local paths.
package com.pokerarity.scanner.data.local.db

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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

    @Test
    fun missingPassphrasePreservesExistingHistoryDatabaseFiles() {
        val directory = Files.createTempDirectory("prs-db-passphrase").toFile()
        val databaseFile = File(directory, "pokerarity_db")
        val encryptedBytes = byteArrayOf(0x00, 0x12, 0x34, 0x56, 0x78)
        databaseFile.writeBytes(encryptedBytes)

        try {
            assertThrows(ExistingDatabasePassphraseUnavailableException::class.java) {
                DatabasePassphraseStore.resolvePassphrase(databaseFile, null) {
                    error("must not create a replacement key for an existing database")
                }
            }
            assertArrayEquals(encryptedBytes, databaseFile.readBytes())
        } finally {
            databaseFile.delete()
            directory.delete()
        }
    }

    @Test
    fun missingPassphraseDoesNotIgnoreAnOrphanedWalFile() {
        val directory = Files.createTempDirectory("prs-db-wal").toFile()
        val databaseFile = File(directory, "pokerarity_db")
        val walFile = File("${databaseFile.absolutePath}-wal")
        val walBytes = byteArrayOf(0x01, 0x23, 0x45)
        walFile.writeBytes(walBytes)

        try {
            assertThrows(ExistingDatabasePassphraseUnavailableException::class.java) {
                DatabasePassphraseStore.resolvePassphrase(databaseFile, null) {
                    error("must not create a replacement key when a database sidecar exists")
                }
            }
            assertArrayEquals(walBytes, walFile.readBytes())
        } finally {
            walFile.delete()
            directory.delete()
        }
    }

    @Test
    fun missingPassphraseCreatesKeyOnlyWhenDatabaseDoesNotExist() {
        val directory = Files.createTempDirectory("prs-db-empty").toFile()
        val databaseFile = File(directory, "pokerarity_db")
        val generated = byteArrayOf(1, 2, 3)

        try {
            assertArrayEquals(
                generated,
                DatabasePassphraseStore.resolvePassphrase(databaseFile, null) { generated }
            )
        } finally {
            directory.delete()
        }
    }
}
