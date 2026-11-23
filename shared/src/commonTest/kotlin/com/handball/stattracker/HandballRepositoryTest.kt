package com.handball.stattracker

import com.handball.stattracker.config.SupabaseAuthSession
import com.handball.stattracker.config.SupabaseConfig
import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.model.FieldPlayerGameStats
import com.handball.stattracker.model.Game
import com.handball.stattracker.model.GameLogEventType
import com.handball.stattracker.model.OpponentTeam
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.PlayerGame
import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.model.RankingSortCriterion
import com.handball.stattracker.model.Season
import com.handball.stattracker.model.StartingSevenSlotType
import com.handball.stattracker.model.StartingSevenWeights
import com.handball.stattracker.model.SyncState
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HandballRepositoryTest {

    @Test
    fun testDemoSeedAndStandingsCalculation() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())

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
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())

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
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())

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
    fun testCourtEntryCandidatesForFreeSlot() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        // Spiel 1: 7 aktiv → kein freier Platz
        assertTrue(repo.courtEntryCandidates(gameId = 1).isEmpty())

        // Feldspieler 3 geht allein raus → 5 Feld + 1 TW: Feldspieler der Bank dürfen rein, der zweite TW nicht
        repo.movePlayerToBench(gameId = 1, playerOutId = 3)
        val candidates = repo.courtEntryCandidates(gameId = 1)
        assertTrue(candidates.containsAll(listOf(3, 9, 13)))
        assertFalse(2 in candidates, "Nie zwei Torhüter gleichzeitig")

        // Wer gesperrt ist, kommt nicht in die Auswahl
        repo.setGameClockSeconds(gameId = 1, targetSeconds = 1200)
        val pg5 = repo.snapshot.value.playerGames.first { it.gameId == 1 && it.playerId == 5 }
        repo.updatePlayerDisciplinary(pg5.playerGameId, pg5.twoMinuteSuspensions + 1, pg5.yellowCard, pg5.redCard, pg5.blueCard)
        assertFalse(5 in repo.courtEntryCandidates(gameId = 1))

        // Freien Platz besetzen
        assertTrue(repo.substitutePlayer(gameId = 1, playerInId = 13, playerOutId = null))
        assertFalse(13 in repo.courtEntryCandidates(gameId = 1))
    }

    @Test
    fun testSubstitutionOptionsForTappedPlayer() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        // Spiel 1: 6 Feld + TW (playerId 1) auf dem Feld; auf der Bank u. a. TW 2 sowie Feldspieler 9 und 13

        // Feldspieler auf dem Feld: Feldspieler von der Bank dürfen kommen, der zweite TW nicht (nie 2 TW)
        val forFieldPlayer = repo.substitutionOptions(gameId = 1, playerId = 3)!!
        assertTrue(forFieldPlayer.isOnCourt)
        assertTrue(forFieldPlayer.partnerIds.containsAll(listOf(9, 13)))
        assertFalse(2 in forFieldPlayer.partnerIds, "Zweiter Torwart darf nicht für einen Feldspieler kommen")
        assertTrue(forFieldPlayer.canActAlone, "Vom Feld darf man immer allein auf die Bank")

        // Torwart auf dem Feld: zuerst der andere TW, dann Feldspieler (7 Feldspieler ohne TW ist erlaubt)
        val forKeeper = repo.substitutionOptions(gameId = 1, playerId = 1)!!
        assertEquals(2, forKeeper.partnerIds.first(), "Gleiche Rolle steht oben")
        assertTrue(forKeeper.partnerIds.containsAll(listOf(9, 13)))

        // Torwart auf der Bank: nur gegen den aktiven TW, nicht allein aufs Feld (7 sind aktiv)
        val forBenchKeeper = repo.substitutionOptions(gameId = 1, playerId = 2)!!
        assertFalse(forBenchKeeper.isOnCourt)
        assertEquals(listOf(1), forBenchKeeper.partnerIds)
        assertFalse(forBenchKeeper.canActAlone)

        // Feldspieler auf der Bank: darf jeden der 7 ersetzen; allein aufs Feld erst, wenn einer fehlt
        val forBenchField = repo.substitutionOptions(gameId = 1, playerId = 13)!!
        assertEquals(7, forBenchField.partnerIds.size)
        assertFalse(forBenchField.canActAlone)
        repo.movePlayerToBench(gameId = 1, playerOutId = 3)
        assertTrue(repo.substitutionOptions(gameId = 1, playerId = 13)!!.canActAlone)

        // Zeitstrafe: gesperrter Bank-Spieler hat keine Wechsel-Möglichkeit
        repo.setGameClockSeconds(gameId = 1, targetSeconds = 1200)
        val pg5 = repo.snapshot.value.playerGames.first { it.gameId == 1 && it.playerId == 5 }
        repo.updatePlayerDisciplinary(pg5.playerGameId, pg5.twoMinuteSuspensions + 1, pg5.yellowCard, pg5.redCard, pg5.blueCard)
        assertFalse(repo.substitutionOptions(gameId = 1, playerId = 5)!!.hasAnyOption)

        // Spieler, der nicht im Spiel ist
        assertEquals(null, repo.substitutionOptions(gameId = 1, playerId = 9999))
    }

    @Test
    fun testActiveOnCourtBenchLockSubstitutionsAndPlusMinus() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())

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

    // --- PERFORMANCE-BASIERTE STARTSIEBEN ---

    private fun onCourtRoles(repo: HandballRepository, gameId: Int): List<PlayerRole> {
        val state = repo.snapshot.value
        return state.playerGames
            .filter { it.gameId == gameId && it.isOnCourt }
            .mapNotNull { pg -> state.players.find { it.playerId == pg.playerId }?.role }
    }

    @Test
    fun testNewGameStartsWithPerformanceBasedStartingSeven() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        val before = repo.snapshot.value
        val nominated = before.players.filter { it.role != PlayerRole.COACH }.map { it.playerId }.toSet()

        val game = repo.addGame(
            seasonId = 1,
            opponentId = before.opponentTeams.first().opponentTeamId,
            secondOpponentId = null,
            isHomeGame = true,
            location = "Halle",
            gameType = "Ligaspiel",
            gameDate = "2026-12-01",
            nominatedPlayerIds = nominated
        )

        val onCourt = repo.snapshot.value.playerGames
            .filter { it.gameId == game.gameId && it.isOnCourt }
            .map { it.playerId }.toSet()
        val expected = repo.recommendStartingSeven(game.gameId, useSeventhFieldPlayer = false)
            .chosenPlayerIds.toSet()

        assertEquals(7, onCourt.size, "Neues Spiel startet mit 7 Spielern auf dem Feld")
        assertEquals(expected, onCourt, "Startsieben eines neuen Spiels folgt der Performance-Empfehlung")
        assertEquals(1, onCourtRoles(repo, game.gameId).count { it == PlayerRole.GOALKEEPER })
    }

    @Test
    fun testStartingSevenNeverTwoGoalkeepers() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())

        // Spiel 1 hat zwei nominierte Torhüter (playerId 1 und 2)
        val rec = repo.recommendStartingSeven(gameId = 1, useSeventhFieldPlayer = false)
        assertEquals(1, rec.goalKeeperCount, "Genau 1 Torwart in der 6+1 Startsieben")
        assertEquals(6, rec.fieldPlayerCount)
        assertEquals(7, rec.chosenPlayerIds.toSet().size, "Keine doppelten Spieler")
        assertEquals("TW", rec.slots.first().slotLabel)
        assertEquals(PlayerRole.GOALKEEPER, rec.slots.first().runnerUp?.player?.role, "Zweiter Torwart als Alternative")
        assertEquals(
            listOf("TW", "LA", "RL", "RM", "RR", "RA", "KM"),
            rec.slots.map { it.slotLabel }
        )

        // Auch nach dem Tausch auf den Ersatz-TW bleibt es bei genau 1 Torwart
        val swapped = rec.swapWithRunnerUp(0)
        assertEquals(1, swapped.goalKeeperCount)
        assertEquals(rec.slots.first().runnerUp?.player?.playerId, swapped.slots.first().chosen?.player?.playerId)

        val logsBefore = repo.getGameLogs(1).size
        assertTrue(repo.applyRecommendedStartingSevenToGame(gameId = 1, useSeventhFieldPlayer = false))
        val roles = onCourtRoles(repo, 1)
        assertEquals(1, roles.count { it == PlayerRole.GOALKEEPER })
        assertEquals(6, roles.count { it == PlayerRole.FIELD_PLAYER })
        assertEquals(logsBefore + 1, repo.getGameLogs(1).size)
        assertEquals(GameLogEventType.LINEUP_SET, repo.getGameLogs(1).first().eventType)

        // Eine manuelle Aufstellung mit 2 Torhütern wird abgelehnt
        val invalid = listOf(1, 2) + rec.chosenPlayerIds.filter { it != 1 && it != 2 }.take(5)
        assertFalse(repo.applyStartingSevenLineup(gameId = 1, playerIds = invalid), "2 Torhüter dürfen nicht übernommen werden")
    }

    @Test
    fun testStartingSevenSeventhFieldPlayerVariantHasNoGoalkeeper() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())

        val rec = repo.recommendStartingSeven(gameId = 1, useSeventhFieldPlayer = true)
        assertEquals(0, rec.goalKeeperCount, "7+0 Variante hat keinen Torwart")
        assertEquals(7, rec.fieldPlayerCount)
        assertTrue(rec.slots.none { it.type == StartingSevenSlotType.GOALKEEPER })
        assertEquals(StartingSevenSlotType.EXTRA_FIELD_PLAYER, rec.slots.last().type)
        assertTrue(repo.isValidActiveCourtLineup(rec.fieldPlayerCount, rec.goalKeeperCount))

        assertTrue(repo.applyRecommendedStartingSevenToGame(gameId = 1, useSeventhFieldPlayer = true))
        val roles = onCourtRoles(repo, 1)
        assertEquals(0, roles.count { it == PlayerRole.GOALKEEPER })
        assertEquals(7, roles.count { it == PlayerRole.FIELD_PLAYER })
        assertTrue(repo.getGameLogs(1).first().description.contains("7 Feld + 0 TW"))
    }

    @Test
    fun testStartingSevenExcludesSuspendedAndDisqualifiedPlayers() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        repo.setGameClockSeconds(gameId = 1, targetSeconds = 1200)

        // Juri Knorr-Schmidt (playerId 3, pg 3) erhält Rot, Renars Uscins-Hoffmann (playerId 5, pg 5) 2 Minuten
        val pg3 = repo.snapshot.value.playerGames.first { it.playerGameId == 3 }
        repo.updatePlayerDisciplinary(3, pg3.twoMinuteSuspensions, pg3.yellowCard, redCard = true, blueCard = false)
        val pg5 = repo.snapshot.value.playerGames.first { it.playerGameId == 5 }
        repo.updatePlayerDisciplinary(5, pg5.twoMinuteSuspensions + 1, pg5.yellowCard, redCard = false, blueCard = false)

        for (useSeventh in listOf(false, true)) {
            val rec = repo.recommendStartingSeven(gameId = 1, useSeventhFieldPlayer = useSeventh)
            val involved = rec.slots.flatMap { listOfNotNull(it.chosen?.player?.playerId, it.runnerUp?.player?.playerId) }
            assertFalse(3 in involved, "Disqualifizierter Spieler darf nicht empfohlen werden")
            assertFalse(5 in involved, "Zeitbestrafter Spieler darf nicht empfohlen werden")
        }

        assertTrue(repo.applyRecommendedStartingSevenToGame(gameId = 1))
        val onCourtIds = repo.snapshot.value.playerGames.filter { it.gameId == 1 && it.isOnCourt }.map { it.playerId }
        assertFalse(3 in onCourtIds)
        assertFalse(5 in onCourtIds)
        // Direktes Übernehmen gesperrter Spieler wird ignoriert
        assertFalse(repo.applyStartingSevenLineup(gameId = 1, playerIds = listOf(3, 5)))
    }

    /** Minimaler Snapshot: Standard-Positionen, eine Saison, ein Gegner und frei definierbare Spiele/Spieler. */
    private fun buildSnapshot(
        players: List<Player>,
        games: List<Game>,
        participations: List<Triple<Int, Int, FieldPlayerGameStats?>> // (gameId, playerId, Feldstatistik)
    ): DatabaseSnapshot {
        val pgs = participations.mapIndexed { idx, (gameId, playerId, _) ->
            PlayerGame(playerGameId = idx + 1, gameId = gameId, playerId = playerId, secondsPlayed = 1800)
        }
        val fieldStats = participations.mapIndexedNotNull { idx, (_, _, stats) ->
            stats?.copy(fieldPlayerGamestatsId = idx + 1, playerGameId = idx + 1)
        }
        return DatabaseSnapshot(
            positions = DemoDataSeed.defaultPositions(),
            players = players,
            seasons = listOf(Season(1, "Test-Liga", "2026-01-01", "2026-12-31")),
            opponentTeams = listOf(OpponentTeam(1, "Gegner", "Halle")),
            games = games,
            playerGames = pgs,
            fieldPlayerStats = fieldStats
        )
    }

    @Test
    fun testStartingSevenFallbackWhenPositionHasNoPlayer() {
        val repo = HandballRepository(persistLocally = false)
        // Kein Linksaußen (positionId 2) im Kader, dafür zwei Rückraum-Links-Spieler
        val players = listOf(
            Player(1, positionId = 1, firstName = "T", lastName = "Wart", role = PlayerRole.GOALKEEPER, jerseyNumberHomeKit = 1),
            Player(2, positionId = 3, firstName = "A", lastName = "RL-Eins", jerseyNumberHomeKit = 2),
            Player(3, positionId = 3, firstName = "B", lastName = "RL-Zwei", jerseyNumberHomeKit = 3),
            Player(4, positionId = 4, firstName = "C", lastName = "RM", jerseyNumberHomeKit = 4),
            Player(5, positionId = 5, firstName = "D", lastName = "RR", jerseyNumberHomeKit = 5),
            Player(6, positionId = 6, firstName = "E", lastName = "RA", jerseyNumberHomeKit = 6),
            Player(7, positionId = 7, firstName = "F", lastName = "KM", jerseyNumberHomeKit = 7)
        )
        val game = Game(1, seasonId = 1, opponentId = 1, location = "Halle", gameDate = "2026-03-01")
        val state = buildSnapshot(players, listOf(game), players.map { Triple(1, it.playerId, null) })

        val rec = repo.recommendStartingSeven(gameId = 1, useSeventhFieldPlayer = false, state = state)
        val laSlot = rec.slots.first { it.slotLabel == "LA" }
        assertTrue(laSlot.chosen != null, "LA muss mit einem Ersatzspieler besetzt werden")
        assertTrue(laSlot.isPositionFallback, "LA ist eine Ersatzlösung")
        assertEquals(3, laSlot.chosen.player.positionId, "Freier RL-Spieler springt auf LA ein")
        assertEquals(1, rec.goalKeeperCount)
        assertEquals(6, rec.fieldPlayerCount)
        assertEquals(7, rec.chosenPlayerIds.toSet().size)
        assertTrue(rec.slots.filter { it.slotLabel != "LA" }.none { it.isPositionFallback })

        // Zu wenige Spieler: die Position bleibt leer, ohne Absturz und ohne Doppelbesetzung
        val tinyState = buildSnapshot(players.take(3), listOf(game), players.take(3).map { Triple(1, it.playerId, null) })
        val tiny = repo.recommendStartingSeven(gameId = 1, useSeventhFieldPlayer = false, state = tinyState)
        assertEquals(3, tiny.chosenPlayerIds.size)
        assertEquals(3, tiny.chosenPlayerIds.toSet().size)
        assertTrue(tiny.slots.any { it.chosen == null })
    }

    @Test
    fun testStartingSevenWeightingPrefersBetterRecentForm() {
        val repo = HandballRepository(persistLocally = false)
        // Beide spielen Linksaußen: "Früher" hatte ein überragendes erstes Spiel und danach 5 schwache,
        // "Konstant" spielt in allen 6 Spielen solide. Saisonwert spricht für "Früher", die Form für "Konstant".
        val earlyStar = Player(1, positionId = 2, firstName = "A", lastName = "Früher", jerseyNumberHomeKit = 11)
        val inForm = Player(2, positionId = 2, firstName = "B", lastName = "Konstant", jerseyNumberHomeKit = 22)

        val pastGames = (1..6).map { i ->
            Game(i, seasonId = 1, opponentId = 1, location = "Halle", gameDate = "2026-01-0$i", elapsedSeconds = 3600)
        }
        val target = Game(7, seasonId = 1, opponentId = 1, location = "Halle", gameDate = "2026-02-01")

        val participations = pastGames.flatMap { g ->
            val starStats = if (g.gameId == 1) {
                FieldPlayerGameStats(0, 0, goalsScored = 20, goalesAttempted = 20)
            } else {
                FieldPlayerGameStats(0, 0, goalsScored = 0, goalesAttempted = 0)
            }
            listOf(
                Triple(g.gameId, earlyStar.playerId, starStats),
                Triple(g.gameId, inForm.playerId, FieldPlayerGameStats(0, 0, goalsScored = 4, goalesAttempted = 6))
            )
        }
        // Restliche Feldpositionen (RL..KM) besetzen, damit kein LA-Kandidat als Ersatz abwandert
        val fillers = (3..7).map { pos ->
            Player(10 + pos, positionId = pos, firstName = "F", lastName = "Pos$pos", jerseyNumberHomeKit = 10 + pos)
        }
        val targetParticipations = (listOf(earlyStar, inForm) + fillers).map { Triple(7, it.playerId, FieldPlayerGameStats(0, 0)) }

        val state = buildSnapshot(listOf(earlyStar, inForm) + fillers, pastGames + target, participations + targetParticipations)
        val rec = repo.recommendStartingSeven(gameId = 7, useSeventhFieldPlayer = false, state = state)
        val laSlot = rec.slots.first { it.slotLabel == "LA" }

        val chosen = laSlot.chosen!!
        val runnerUp = laSlot.runnerUp!!
        assertEquals(inForm.playerId, chosen.player.playerId, "Bessere aktuelle Form muss bevorzugt werden")
        assertEquals(earlyStar.playerId, runnerUp.player.playerId)
        assertTrue(
            runnerUp.breakdown.seasonPerformanceScore > chosen.breakdown.seasonPerformanceScore,
            "Nach reinem Saisonwert läge der Zweitplatzierte vorn"
        )
        assertTrue(chosen.breakdown.recentFormScore > runnerUp.breakdown.recentFormScore)
        assertEquals(StartingSevenWeights.RECENT_GAMES, chosen.breakdown.recentGamesCount)
        assertTrue(chosen.breakdown.weightedScore > runnerUp.breakdown.weightedScore)

        // Kein TW im Kader → TW-Platz bleibt leer, es stehen trotzdem nie 2 TW oder Duplikate auf dem Feld
        assertEquals(0, rec.goalKeeperCount)
        assertEquals(rec.chosenPlayerIds.size, rec.chosenPlayerIds.toSet().size)
    }

    @Test
    fun testSupabaseEnvConfigAndLiveSync() = runBlocking {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())

        if (SupabaseConfig.hasConfiguredKeys() && SupabaseConfig.resolvedProjectUrl().isNotBlank()) {
            assertTrue(
                SupabaseConfig.resolvedApiKey().startsWith("sb_publishable_"),
                "Client muss den sicheren sb_publishable_ Key bevorzugen"
            )
            if (SupabaseAuthSession.isAuthenticated()) {
                repo.syncWithBackend()
                assertEquals(SyncState.SYNCED, repo.syncState.value, "Supabase Sync sollte erfolgreich sein")
            }
        }
    }

    @Test
    fun testSupabaseSyncWithoutSessionDegradesGracefully() = runBlocking {
        SupabaseAuthSession.clear()
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        val before = repo.snapshot.value

        // Darf weder crashen noch einen Netzwerkaufruf als anon machen noch lokale Daten verlieren
        repo.syncWithBackend(
            supabaseUrl = "https://example-project.supabase.co",
            supabaseAnonKey = "sb_publishable_test"
        )

        assertEquals(SyncState.UNAVAILABLE, repo.syncState.value, "Ohne Login darf kein erfolgreicher Sync gemeldet werden")
        assertEquals(before, repo.snapshot.value, "Lokaler Snapshot muss unverändert bleiben")
    }

    @Test
    fun testSecretKeysAreNeverUsedClientSide() {
        // Über die Runtime-Konfiguration lässt sich kein sb_secret_ Key einschleusen;
        // resolvedApiKey() darf ihn in keinem Fall zurückgeben.
        assertFalse(SupabaseConfig.resolvedApiKey().startsWith("sb_secret_", ignoreCase = true))
    }
}
