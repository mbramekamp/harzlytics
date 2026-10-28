package com.handball.stattracker

import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.model.RankingSortCriterion
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

    @Test
    fun testPerformanceGraphsPositionComparisonAndRanking() {
        val repo = HandballRepository()
        repo.resetToDemoData()

        // 1. Chronologische Performance-Serie für Juri Knorr-Schmidt (playerId = 3, Position RM = 4)
        val series = repo.getPlayerPerformanceSeries(playerId = 3)
        assertEquals(3, series.size, "Juri Knorr-Schmidt hat 3 Spiele in der Zeitreihe")
        assertTrue(series.first().gameDate <= series.last().gameDate, "Zeitreihe muss chronologisch aufsteigend sein")
        assertTrue(series.all { it.performanceScore > 0.0 }, "Performance-Score muss berechnet sein")

        // 2. Positions-Vergleich (RM hat 2 Spieler: playerId = 3 und playerId = 13)
        val comparisons = repo.computePositionMetricComparisons(playerId = 3)
        assertTrue(comparisons.isNotEmpty(), "Positionsvergleiche müssen vorhanden sein")
        val goalsComp = comparisons.first { it.metricKey == "goals_pg" }
        assertEquals(2, goalsComp.totalPlayersInPosition)
        assertEquals(1, goalsComp.rankInPosition, "Juri Knorr-Schmidt (9.0 Tore/Sp.) ist #1 auf RM")
        assertTrue(goalsComp.playerValue > goalsComp.positionAverage)

        // 3. 6-Achsen-Radar-Werte prüfen
        val radar = repo.computeRadarAxesForPlayer(playerId = 3)
        assertEquals(6, radar.size, "Radar-Diagramm muss 6 Achsen liefern")

        // 4. Trainer-Spieler-Ranking auf Position RL (positionId = 3: Julian Köster-Lang vs. Sebastian Heymann-Berg)
        val rlRanking = repo.computePlayerRankings(
            positionIdFilter = 3,
            sortCriterion = RankingSortCriterion.PERFORMANCE_INDEX
        )
        assertEquals(2, rlRanking.size)
        assertEquals(1, rlRanking[0].rank)
        assertEquals(2, rlRanking[1].rank)
        assertTrue(rlRanking[0].performanceScore >= rlRanking[1].performanceScore)
    }
}
