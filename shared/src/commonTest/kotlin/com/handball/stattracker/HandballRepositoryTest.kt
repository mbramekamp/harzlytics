package com.handball.stattracker

import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HandballRepositoryTest {

    @Test
    fun testDemoSeedAndStandingsCalculation() {
        val repo = HandballRepository()
        repo.resetToDemoData()

        val state = repo.snapshot.value
        assertEquals(7, state.positions.size, "7 klassische Handball-Positionen müssen initialisiert sein")
        assertTrue(state.players.any { it.role == PlayerRole.COACH }, "Trainer müssen im Kader enthalten sein")
        assertTrue(state.players.any { it.role == PlayerRole.GOALKEEPER }, "Torhüter müssen im Kader enthalten sein")
        assertTrue(state.players.any { it.role == PlayerRole.FIELD_PLAYER }, "Feldspieler müssen im Kader enthalten sein")

        val standings = repo.computeStandings(seasonId = 1, state = state)
        assertTrue(standings.isNotEmpty(), "Ligatabelle darf nicht leer sein")

        val ownRow = standings.first { it.isOwnTeam }
        // In DemoDataSeed hat das eigene Team 2 Siege und 1 Unentschieden = 5:1 Punkte
        assertEquals(3, ownRow.gamesPlayed)
        assertEquals(2, ownRow.wins)
        assertEquals(1, ownRow.draws)
        assertEquals(0, ownRow.losses)
        assertEquals(5, ownRow.pointsPlus)
        assertEquals(1, ownRow.pointsMinus)
    }

    @Test
    fun testPlayerProfileAggregationAndCrud() {
        val repo = HandballRepository()
        repo.resetToDemoData()

        // Juri Knorr-Schmidt (playerId = 3) hat in 3 Spielen 9 + 8 + 10 = 27 Tore erzielt
        val stats = repo.computePlayerAggregatedStats(playerId = 3)
        assertEquals(3, stats.gamesAttended)
        assertEquals(27, stats.totalGoals)
        assertEquals(36, stats.totalShots)
        assertEquals(75, stats.shotAccuracyPercent)

        // Neuen Trainer anlegen (ohne Position & ohne Trikotnummer)
        val coach = repo.addPlayer(
            firstName = "Heiner",
            lastName = "Brand",
            email = "heiner@dhb.de",
            role = PlayerRole.COACH,
            positionId = null,
            jerseyHome = null,
            jerseyAway = null
        )
        assertEquals(PlayerRole.COACH, coach.role)
        assertEquals(null, coach.positionId)
        assertEquals(null, coach.jerseyNumberHomeKit)
    }
}
