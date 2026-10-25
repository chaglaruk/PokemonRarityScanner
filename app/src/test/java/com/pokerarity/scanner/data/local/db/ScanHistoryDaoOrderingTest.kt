package com.pokerarity.scanner.data.local.db

import androidx.room.Room
import com.pokerarity.scanner.service.RequestAcceptance
import com.pokerarity.scanner.service.RequestOrigin
import com.pokerarity.scanner.service.ScanRequestCoordinator
import com.pokerarity.scanner.service.TerminalOutcome
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

    @Test
    fun terminalOrderWinsWhenPersistenceCompletesInReverseOrder() = runBlocking {
        val coordinator = ScanRequestCoordinator(clockMillis = { 1_700_000_000_000L })
        val first = (coordinator.acceptRequest(RequestOrigin.USER) as RequestAcceptance.Accepted).token
        coordinator.claimTerminal(first, TerminalOutcome.SUCCESS_PUBLISHED)
        val firstTimestamp = coordinator.snapshot(first)!!.terminalTimestampMillis!!
        val second = (coordinator.acceptRequest(RequestOrigin.USER) as RequestAcceptance.Accepted).token
        coordinator.claimTerminal(second, TerminalOutcome.SUCCESS_PUBLISHED)
        val secondTimestamp = coordinator.snapshot(second)!!.terminalTimestampMillis!!

        // Save the newer result first to simulate asynchronous persistence completing out of order.
        val newerRow = dao.insert(history(java.util.Date(secondTimestamp), "Pikachu"))
        val olderRow = dao.insert(history(java.util.Date(firstTimestamp), "Pikachu"))

        assertEquals(listOf(newerRow, olderRow), dao.getAll().first().map { it.id })
        assertEquals(listOf(newerRow, olderRow), dao.getRecent(2).first().map { it.id })
        assertEquals(listOf(newerRow, olderRow), dao.getByPokemonName("Pikachu").map { it.id })
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
