package com.handball.stattracker

import com.handball.stattracker.config.SupabaseConfig
import com.handball.stattracker.model.GameLogEventType
import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.model.RankingSortCriterion
import com.handball.stattracker.repository.HandballRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertTrue(stats.totalPlusMinus > 0, "Plus/Minus muss aggregiert werden")
        assertTrue(stats.totalSecondsPlayed > 0, "Einsatzzeit muss aggregiert werden")

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
        assertTrue(comparisons.any { it.metricKey == "plus_minus_pg" }, "Plus/Minus-Vergleich muss enthalten sein")

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

    @Test
    fun testActiveOnCourtBenchLockSubstitutionsAndPlusMinus() {
        val repo = HandballRepository()
        repo.resetToDemoData()

        // In Spiel 1 sind 7 Spieler aktiv auf dem Feld (darunter #15 Knorr-Schmidt = playerId 3, playerGameId 3)
        // und #20 Witzke-Hartmann (playerId 13, playerGameId 30) sitzt auf der Bank (isOnCourt = false).
        val initialGame1Pgs = repo.snapshot.value.playerGames.filter { it.gameId == 1 }
        val activeOnCourt = initialGame1Pgs.filter { it.isOnCourt }
        assertEquals(7, activeOnCourt.size, "Es müssen genau 7 aktive Spieler auf dem Feld stehen")

        // 1. Bank-Spieler (playerGameId = 30, isOnCourt = false) darf keine Tore verbuchen
        val benchStatsBefore = repo.snapshot.value.fieldPlayerStats.first { it.playerGameId == 30 }.goalsScored
        repo.updateFieldPlayerStats(playerGameId = 30, autoSyncTeamScore = true) {
            it.copy(goalsScored = it.goalsScored + 1, goalesAttempted = it.goalesAttempted + 1)
        }
        val benchStatsAfter = repo.snapshot.value.fieldPlayerStats.first { it.playerGameId == 30 }.goalsScored
        assertEquals(benchStatsBefore, benchStatsAfter, "Bank-Spieler sind inaktiv und dürfen keine Statistiken ändern")

        // 2. Aktiver Spieler auf dem Feld (playerGameId = 3, isOnCourt = true) erzielt ein Tor -> alle 7 Spieler auf dem Feld erhalten +1 Plus/Minus
        val pmBeforeByPg = activeOnCourt.associate { it.playerGameId to it.plusMinus }
        repo.updateFieldPlayerStats(playerGameId = 3, autoSyncTeamScore = true) {
            it.copy(goalsScored = it.goalsScored + 1, goalesAttempted = it.goalesAttempted + 1)
        }
        val afterGoalPgs = repo.snapshot.value.playerGames.filter { it.gameId == 1 && it.isOnCourt }
        afterGoalPgs.forEach { pg ->
            assertEquals((pmBeforeByPg[pg.playerGameId] ?: 0) + 1, pg.plusMinus, "Alle 7 aktiven Spieler auf dem Feld müssen +1 auf +/- erhalten")
        }

        // 3. 2-Klick-Wechsel: Bank-Spieler #20 (playerId 13) wird für Feldspieler #15 (playerId 3) eingewechselt
        val logsBefore = repo.getGameLogs(1).size
        val subSuccess = repo.substitutePlayer(gameId = 1, playerInId = 13, playerOutId = 3)
        assertTrue(subSuccess, "1-zu-1 Wechsel zwischen Bank- und Feldspieler muss erfolgreich sein")

        val pgInAfter = repo.snapshot.value.playerGames.first { it.gameId == 1 && it.playerId == 13 }
        val pgOutAfter = repo.snapshot.value.playerGames.first { it.gameId == 1 && it.playerId == 3 }
        assertTrue(pgInAfter.isOnCourt, "Eingewechselter Spieler muss jetzt auf dem Feld sein")
        assertFalse(pgOutAfter.isOnCourt, "Ausgewechselter Spieler muss jetzt auf der Bank sein")

        val latestLog = repo.getGameLogs(1).first()
        assertEquals(logsBefore + 1, repo.getGameLogs(1).size)
        assertEquals(GameLogEventType.SUBSTITUTION, latestLog.eventType)
        assertEquals(13, latestLog.primaryPlayerId)
        assertEquals(3, latestLog.secondaryPlayerId)

        // 4. Regel-Prüfung (6 Feldspieler + 1 Torwart ODER 7 Feldspieler):
        // Aktuell stehen 6 Feldspieler + 1 Torwart (playerId = 1) auf dem Feld.
        // Zweiter Torwart auf der Bank (playerId = 2) darf NICHT für einen Feldspieler (playerId = 4) eingewechselt werden (5 Feld + 2 TW verboten!)
        assertFalse(
            repo.canSubstituteOrEnterCourt(gameId = 1, playerInId = 2, playerOutId = 4),
            "Zweiter Torwart darf nicht für einen Feldspieler eingewechselt werden, solange bereits 1 TW auf dem Feld steht"
        )
        assertFalse(
            repo.substitutePlayer(gameId = 1, playerInId = 2, playerOutId = 4),
            "Ungültiger Wechsel (2 Torhüter gleichzeitig) muss abgelehnt werden"
        )

        // Torwart-gegen-Torwart-Wechsel (playerId = 2 rein für playerId = 1) ist erlaubt -> weiterhin 6 Feld + 1 TW
        assertTrue(repo.substitutePlayer(gameId = 1, playerInId = 2, playerOutId = 1))

        // Feldspieler von der Bank (playerId = 9) darf für den aktiven Torwart (playerId = 2) eingewechselt werden -> 7 Feldspieler + 0 TW (Empty Net)!
        assertTrue(
            repo.substitutePlayer(gameId = 1, playerInId = 9, playerOutId = 2),
            "Wechsel von 6 Feld + 1 TW auf 7 Feldspieler (Empty Net) muss erlaubt sein"
        )
        val activeAfterEmptyNet = repo.snapshot.value.playerGames
            .filter { it.gameId == 1 && it.isOnCourt }
            .mapNotNull { pg -> repo.snapshot.value.players.find { it.playerId == pg.playerId } }
        assertEquals(7, activeAfterEmptyNet.count { it.role == PlayerRole.FIELD_PLAYER }, "Es müssen jetzt 7 Feldspieler aktiv sein")
        assertEquals(0, activeAfterEmptyNet.count { it.role == PlayerRole.GOALKEEPER }, "Es darf jetzt kein Torwart auf dem Feld stehen")

        // Torwart (playerId = 1) darf bei 7 Feldspielern wieder für einen Feldspieler (playerId = 9) eingewechselt werden -> zurück auf 6 Feld + 1 TW!
        assertTrue(
            repo.substitutePlayer(gameId = 1, playerInId = 1, playerOutId = 9),
            "Wechsel von 7 Feldspielern zurück auf 6 Feld + 1 Torwart muss erlaubt sein"
        )

        // 5. 2-Minuten-Strafe gegen einen aktiven Spieler (playerId = 5, playerGameId = 5) versetzt ihn automatisch auf die Bank
        repo.setGameClockSeconds(gameId = 1, targetSeconds = 1200)
        val pg5Before = repo.snapshot.value.playerGames.first { it.playerGameId == 5 }
        assertTrue(pg5Before.isOnCourt)
        repo.updatePlayerDisciplinary(
            playerGameId = 5,
            twoMinuteSuspensions = pg5Before.twoMinuteSuspensions + 1,
            yellowCard = pg5Before.yellowCard,
            redCard = false,
            blueCard = false
        )
        val pg5After = repo.snapshot.value.playerGames.first { it.playerGameId == 5 }
        assertFalse(pg5After.isOnCourt, "2-Minuten-Strafe muss den Spieler automatisch auf die Bank versetzen")
        assertEquals(120, pg5After.remainingSuspensionSeconds(1200), "2-Minuten-Countdown muss auf 120s stehen")

        // Auch in Unterzahl (5 Feld + 1 TW = 6 Spieler auf dem Feld) darf kein 2. Torwart (playerId = 2) direkt aufs Feld!
        assertFalse(
            repo.canSubstituteOrEnterCourt(gameId = 1, playerInId = 2, playerOutId = null),
            "Auch bei < 7 Spielern auf dem Feld darf kein 2. Torwart direkt aufs Feld"
        )
    }

    @Test
    fun testSupabaseEnvConfigAndLiveSync() = runBlocking {
        val repo = HandballRepository()
        repo.resetToDemoData()

        if (SupabaseConfig.hasConfiguredKeys() && SupabaseConfig.resolvedProjectUrl().isNotBlank()) {
            assertTrue(
                SupabaseConfig.resolvedApiKey().startsWith("sb_publishable_"),
                "Client muss den sicheren sb_publishable_ Key bevorzugen"
            )
            repo.syncWithBackend()
            assertTrue(
                repo.syncStatus.value.startsWith("Sync erfolgreich"),
                "Supabase Sync sollte erfolgreich sein, Status war: ${repo.syncStatus.value}"
            )
        }
    }
}
