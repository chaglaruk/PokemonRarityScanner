package com.pokerarity.scanner.data.local.db

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class PlaintextHistoryPreservationTest {
    @Test
    fun databaseOpenRefusesPlaintextWithoutDeletingHistoryOrSidecars() {
        val context = RuntimeEnvironment.getApplication()
        val databaseFile = context.getDatabasePath("pokerarity_db")
        databaseFile.parentFile?.mkdirs()
        val databaseBytes = "SQLite format 3\u0000preserved-history".toByteArray()
        val walFile = File("${databaseFile.absolutePath}-wal")
        val walBytes = byteArrayOf(7, 8, 9, 10)
        databaseFile.writeBytes(databaseBytes)
        walFile.writeBytes(walBytes)

        try {
            assertThrows(ExistingPlaintextHistoryMigrationRequiredException::class.java) {
                DatabasePassphraseStore.getOrCreate(context)
            }

            assertArrayEquals(databaseBytes, databaseFile.readBytes())
            assertArrayEquals(walBytes, walFile.readBytes())
        } finally {
            databaseFile.delete()
            walFile.delete()
            File("${databaseFile.absolutePath}-shm").delete()
            File("${databaseFile.absolutePath}-journal").delete()
        }
    }
}
