package com.handball.stattracker

import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Sync-Verhalten bei gleichzeitigen Eingaben (z. B. Live-Ticker während eines laufenden Syncs).
 * Der Netzwerkaufruf wird über [HandballRepository.syncTransport] ersetzt – kein echtes Supabase.
 */
class SyncConcurrencyTest {

    private val url = "https://example-project.supabase.co"
    private val key = "sb_publishable_test"

    private fun newRepo(): HandballRepository =
        HandballRepository(persistLocally = false).also {
            it.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        }

    @Test
    fun editsMadeWhileSyncIsRunningAreNotLost() = runBlocking {
        val repo = newRepo()
        val requestArrived = CompletableDeferred<Unit>()
        val releaseResponse = CompletableDeferred<Unit>()

        // Server antwortet langsam und schickt den Stand zurück, der beim Absenden galt
        repo.syncTransport = { _, _, sent ->
            requestArrived.complete(Unit)
            releaseResponse.await()
            sent
        }

        val sync = async(Dispatchers.Default) { repo.syncWithBackend(url, key) }
        withTimeout(5_000) { requestArrived.await() }

        // Während der Sync läuft, wird lokal etwas geändert (wie ein Tor im Ticker)
        repo.updateClubName("Während des Syncs geändert")

        releaseResponse.complete(Unit)
        sync.await()

        assertEquals("Während des Syncs geändert", repo.snapshot.value.ownClubName)
        assertTrue(repo.syncStatus.value.startsWith("Sync erfolgreich"), repo.syncStatus.value)
    }

    @Test
    fun serverResponseIsAppliedWhenNothingChangedLocally() = runBlocking {
        val repo = newRepo()
        repo.syncTransport = { _, _, sent: DatabaseSnapshot ->
            sent.copy(ownClubName = "Stand vom Server", lastUpdatedEpochMillis = sent.lastUpdatedEpochMillis + 1)
        }

        repo.syncWithBackend(url, key)

        assertEquals("Stand vom Server", repo.snapshot.value.ownClubName)
    }

    @Test
    fun onlyOneSyncRunsAtATime() = runBlocking {
        val repo = newRepo()
        var active = 0
        var maxActive = 0
        val lock = Mutex()
        repo.syncTransport = { _, _, sent ->
            lock.withLock { active++; maxActive = maxOf(maxActive, active) }
            delay(50)
            lock.withLock { active-- }
            sent
        }

        (1..4).map { async(Dispatchers.Default) { repo.syncWithBackend(url, key) } }.awaitAll()

        assertEquals(1, maxActive, "Syncs dürfen sich nicht überlappen")
    }

    @Test
    fun cancelledSyncDoesNotReportOffline() = runBlocking {
        val repo = newRepo()
        val requestArrived = CompletableDeferred<Unit>()
        repo.syncTransport = { _, _, _ ->
            requestArrived.complete(Unit)
            awaitCancellation()
        }

        val job = launch(Dispatchers.Default) { repo.syncWithBackend(url, key) }
        withTimeout(5_000) { requestArrived.await() }
        job.cancel()
        job.join()

        assertTrue(job.isCancelled)
        assertFalse(
            repo.syncStatus.value.startsWith("Offline-Modus"),
            "Ein abgebrochener Sync ist kein Netzwerkfehler, Status war: ${repo.syncStatus.value}"
        )
    }
}
