package com.pokerarity.scanner.data.local.db

import androidx.room.Room
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ScanHistoryDaoOrderingTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ScanHistoryDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.scanHistoryDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun equalCaptureTimestampsUseNewestInsertedRowAsStableTieBreaker() = runBlocking {
        val timestamp = java.util.Date(1_700_000_000_000L)
        val earlier = dao.insert(history(timestamp, pokemonName = "Pikachu"))
        val later = dao.insert(history(timestamp, pokemonName = "Pikachu"))

        assertEquals(listOf(later, earlier), dao.getAll().first().map { it.id })
        assertEquals(listOf(later, earlier), dao.getRecent(2).first().map { it.id })
        assertEquals(listOf(later, earlier), dao.getByPokemonName("Pikachu").map { it.id })
    }

    private fun history(timestamp: java.util.Date, pokemonName: String) = ScanHistoryEntity(
        timestamp = timestamp,
        pokemonName = pokemonName,
        cp = 475,
        hp = 52,
        caughtDate = null,
        rawOcrText = "",
    )
}
