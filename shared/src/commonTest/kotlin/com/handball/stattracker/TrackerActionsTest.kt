package com.handball.stattracker

import com.handball.stattracker.model.FieldAction
import com.handball.stattracker.model.FieldPlayerGameStats
import com.handball.stattracker.model.GoalKeeperGameStats
import com.handball.stattracker.model.KeeperAction
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import kotlin.test.Test
import kotlin.test.assertEquals

class TrackerActionsTest {

    @Test
    fun fieldActionsKeepShotsAndPenaltiesConsistent() {
        var s = FieldPlayerGameStats(1, 1)
        listOf(FieldAction.GOAL, FieldAction.MISS, FieldAction.PENALTY_GOAL, FieldAction.PENALTY_MISS).forEach { s = it.apply(s) }
        assertEquals(2, s.goalsScored, "Tor + 7m-Tor")
        assertEquals(4, s.goalesAttempted, "Alle vier Versuche sind Würfe")
        assertEquals(1, s.penaltiesScored)
        assertEquals(2, s.penaltiesAttempted)
        assertEquals(2, FieldAction.MISS.count(s), "Fehlwürfe = Würfe − Tore")
        assertEquals(1, FieldAction.PENALTY_MISS.count(s))
        listOf(FieldAction.ASSIST, FieldAction.TECHNICAL_FAULT, FieldAction.STEAL, FieldAction.BLOCK, FieldAction.PENALTY_DRAWN)
            .forEach { assertEquals(1, it.count(it.apply(s))) }
    }

    @Test
    fun revertUndoesApplyAndNeverGoesNegative() {
        val start = FieldPlayerGameStats(1, 1, goalsScored = 3, goalesAttempted = 7, penaltiesScored = 1, penaltiesAttempted = 2)
        FieldAction.entries.forEach { action ->
            assertEquals(start, action.revert(action.apply(start)), "${action.name}: Minus hebt Plus auf")
        }
        val empty = FieldPlayerGameStats(1, 1)
        FieldAction.entries.forEach { assertEquals(empty, it.revert(empty), "${it.name}: nichts zum Zurücknehmen") }
        // 7m vorbei zählt als Wurf: Plus und Minus bewegen Würfe mit
        val miss = FieldAction.PENALTY_MISS.apply(start)
        assertEquals(start.goalesAttempted + 1, miss.goalesAttempted)
    }

    @Test
    fun keeperActionsIncludePenaltiesInSavesAndGoals() {
        var s = GoalKeeperGameStats(1, 1)
        KeeperAction.entries.forEach { s = it.apply(s) }
        assertEquals(2, s.saves, "Parade + 7m gehalten")
        assertEquals(2, s.goalesConceded, "Gegentor + 7m-Gegentor")
        assertEquals(1, s.sucessfulFastBreakPasses)
        assertEquals(2, s.attemptedFastBreakPasses)
        assertEquals(1, KeeperAction.FAST_BREAK_MISS.count(s))
    }

    @Test
    fun goalActionUpdatesScoreThroughRepository() {
        val repo = HandballRepository(persistLocally = false)
        repo.replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        val home = repo.snapshot.value.games.first { it.gameId == 1 }.homeTeamScore
        val pg = repo.snapshot.value.playerGames.first { it.gameId == 1 && it.playerId == 3 }
        repo.updateFieldPlayerStats(pg.playerGameId, autoSyncTeamScore = true) { FieldAction.GOAL.apply(it) }
        assertEquals(home + 1, repo.snapshot.value.games.first { it.gameId == 1 }.homeTeamScore)
    }
}
