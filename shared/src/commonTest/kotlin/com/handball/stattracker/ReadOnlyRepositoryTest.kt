package com.handball.stattracker

import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.repository.HandballRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Rolle SPIELER = reiner Lesezugriff: Änderungen am Repository werden verworfen. */
class ReadOnlyRepositoryTest {

    private fun readOnlyRepo() = HandballRepository(persistLocally = false, canEdit = false)

    @Test
    fun readOnlyRepositoryIgnoresMutations() {
        val repo = readOnlyRepo()
        val before = repo.snapshot.value

        repo.updateClubName("Gehackter Verein")
        repo.addPlayer("Max", "Muster", "max@club.de", PlayerRole.FIELD_PLAYER, positionId = 1, jerseyHome = 7, jerseyAway = 8)
        repo.addSeason("Oberliga", "2026-08-01", "2027-06-30")

        assertEquals(before, repo.snapshot.value, "Ein Spieler darf lokal nichts ändern")
    }

    @Test
    fun grantingEditRightsEnablesMutationsAgain() {
        val repo = readOnlyRepo()
        repo.updateClubName("Gesperrt")
        val originalName = repo.snapshot.value.ownClubName

        repo.setCanEdit(true)
        repo.updateClubName("Neuer Name")
        assertEquals("Neuer Name", repo.snapshot.value.ownClubName)

        repo.setCanEdit(false)
        repo.updateClubName("Wieder gesperrt")
        assertEquals("Neuer Name", repo.snapshot.value.ownClubName)
        assertTrue(originalName != "Gesperrt")
    }
}
