package com.handball.stattracker

import com.handball.stattracker.model.GameLogEventType
import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import com.handball.stattracker.repository.isSwapStandby
import com.handball.stattracker.repository.swapStandbyByCourtPlayer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Wechselpaar Angriff/Abwehr ("8. Mann"). Demo-Spiel 1: auf dem Feld TW 1 + Feld 3..8, Bank: TW 2, Feld 13.
 */
class SwapPairTest {

    private fun demoRepo(canEdit: Boolean = true) = HandballRepository(persistLocally = false).apply {
        replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        setCanEdit(canEdit)
    }

    private fun HandballRepository.pg(playerId: Int, gameId: Int = 1) =
        snapshot.value.playerGames.first { it.gameId == gameId && it.playerId == playerId }

    private fun HandballRepository.goals(playerId: Int) =
        snapshot.value.fieldPlayerStats.first { it.playerGameId == pg(playerId).playerGameId }.goalsScored

    private fun HandballRepository.addGoal(playerId: Int) =
        updateFieldPlayerStats(pg(playerId).playerGameId) { it.copy(goalsScored = it.goalsScored + 1, goalesAttempted = it.goalesAttempted + 1) }

    @Test
    fun pairMakesBenchPlayerVisibleAndAllowsStats() {
        val repo = demoRepo()
        assertTrue(repo.setSwapPartner(gameId = 1, playerIdA = 3, playerIdB = 13))

        val state = repo.snapshot.value
        assertTrue(state.isSwapStandby(repo.pg(13)))
        assertEquals(repo.pg(13).playerGameId, state.swapStandbyByCourtPlayer(1)[repo.pg(3).playerGameId]?.playerGameId)

        val before = repo.goals(13)
        repo.addGoal(13)
        assertEquals(before + 1, repo.goals(13), "Wechselspieler darf Statistik bekommen")
        assertFalse(repo.pg(13).isOnCourt, "Statistik ändert die Aufstellung nicht")
    }

    @Test
    fun benchPlayerWithoutPairStillCannotGetStats() {
        val repo = demoRepo()
        val before = repo.goals(13)
        repo.addGoal(13)
        assertEquals(before, repo.goals(13))
    }

    @Test
    fun swapWithPartnerSubstitutesAndKeepsLineupValid() {
        val repo = demoRepo()
        repo.setGameClockSeconds(1, 600)
        repo.setSwapPartner(1, 13, 3)
        val logsBefore = repo.getGameLogs(1).size

        assertTrue(repo.swapWithPartner(gameId = 1, playerId = 13))
        assertTrue(repo.pg(13).isOnCourt)
        assertFalse(repo.pg(3).isOnCourt)
        assertEquals(7, repo.snapshot.value.playerGames.count { it.gameId == 1 && it.isOnCourt })
        assertEquals(GameLogEventType.SUBSTITUTION, repo.snapshot.value.gameLogs.maxBy { it.logId }.eventType)
        assertEquals(logsBefore + 1, repo.getGameLogs(1).size)

        // Paar bleibt: jetzt ist 3 der Wechselspieler; Tausch zurück über beide Seiten möglich
        assertTrue(repo.snapshot.value.isSwapStandby(repo.pg(3)))
        assertTrue(repo.swapWithPartner(gameId = 1, playerId = 13))
        assertTrue(repo.pg(3).isOnCourt)
        assertFalse(repo.pg(13).isOnCourt)
    }

    @Test
    fun playingTimeCountsOnlyForPlayerOnCourt() {
        val repo = demoRepo()
        repo.setGameClockSeconds(1, 600)
        repo.setSwapPartner(1, 3, 13)
        val standbyBefore = repo.pg(13).secondsPlayed
        val courtBefore = repo.pg(3).secondsPlayed
        repo.advanceGameClock(1, 60)
        assertEquals(standbyBefore, repo.pg(13).secondsPlayed)
        assertEquals(courtBefore + 60, repo.pg(3).secondsPlayed)
    }

    @Test
    fun invalidPairsAreRejected() {
        val repo = demoRepo()
        assertFalse(repo.setSwapPartner(1, 3, 4), "beide auf dem Feld")
        assertFalse(repo.setSwapPartner(1, 1, 2), "Torhüter bilden kein Paar")
        assertFalse(repo.setSwapPartner(1, 2, 3), "Torhüter bilden kein Paar")
        assertFalse(repo.setSwapPartner(1, 3, 3))
        assertTrue(repo.snapshot.value.playerGames.all { it.swapPartnerPlayerGameId == null })
    }

    @Test
    fun newPairReplacesOldOneAndClearRemovesBothSides() {
        val repo = demoRepo()
        repo.setSwapPartner(1, 3, 13)
        assertTrue(repo.setSwapPartner(1, 4, 13))
        assertNull(repo.pg(3).swapPartnerPlayerGameId, "altes Paar gelöst")
        assertEquals(repo.pg(4).playerGameId, repo.pg(13).swapPartnerPlayerGameId)
        assertFalse(13 in repo.swapPartnerCandidates(1, 5), "13 ist schon vergeben")

        repo.clearSwapPartner(1, 4)
        assertNull(repo.pg(4).swapPartnerPlayerGameId)
        assertNull(repo.pg(13).swapPartnerPlayerGameId)
        assertTrue(13 in repo.swapPartnerCandidates(1, 5))
    }

    @Test
    fun disqualifiedPartnerEndsPair() {
        val repo = demoRepo()
        repo.setSwapPartner(1, 3, 13)
        val pg13 = repo.pg(13)
        repo.updatePlayerDisciplinary(pg13.playerGameId, pg13.twoMinuteSuspensions, pg13.yellowCard, redCard = true, blueCard = false)
        assertFalse(repo.snapshot.value.isSwapStandby(repo.pg(13)))
        assertFalse(repo.swapWithPartner(1, 3))
    }

    @Test
    fun swapIsBlockedWhileStandbyServesSuspension() {
        val repo = demoRepo()
        repo.setGameClockSeconds(1, 600)
        repo.setSwapPartner(1, 3, 13)
        val pg13 = repo.pg(13)
        repo.updatePlayerDisciplinary(pg13.playerGameId, pg13.twoMinuteSuspensions + 1, pg13.yellowCard, pg13.redCard, pg13.blueCard)
        assertFalse(repo.swapWithPartner(1, 13), "zeitbestraft: darf nicht aufs Feld")
        assertTrue(repo.pg(3).isOnCourt)
    }

    @Test
    fun readOnlyRoleCannotPairOrSwap() {
        val repo = demoRepo(canEdit = false)
        assertFalse(repo.setSwapPartner(1, 3, 13))
        assertNull(repo.pg(13).swapPartnerPlayerGameId)
    }

    @Test
    fun startingSevenAppliesSwapPairAndReplacesOldPairs() {
        val repo = demoRepo()
        repo.setSwapPartner(1, 3, 13)
        val rec = repo.recommendStartingSeven(1)
        val standby = rec.swapStandbyOptions.first()
        val withSwap = rec.withSwapStandby(standby.player.playerId)
        val pair = withSwap.swapPair
        requireNotNull(pair)
        assertTrue(pair.second in rec.chosenPlayerIds)
        assertTrue(standby.player.playerId !in rec.chosenPlayerIds)

        assertTrue(repo.applyStartingSevenLineup(1, withSwap.chosenPlayerIds, pair))
        val state = repo.snapshot.value
        assertTrue(state.isSwapStandby(repo.pg(pair.first)))
        assertEquals(repo.pg(pair.second).playerGameId, repo.pg(pair.first).swapPartnerPlayerGameId)
        val paired = state.playerGames.filter { it.gameId == 1 && it.swapPartnerPlayerGameId != null }.map { it.playerId }.toSet()
        assertEquals(setOf(pair.first, pair.second), paired, "nur das neue Paar bleibt")

        // Ohne Wechsel-Platz löst eine neue Startsieben alle Paare
        assertTrue(repo.applyStartingSevenLineup(1, withSwap.chosenPlayerIds))
        assertTrue(repo.snapshot.value.playerGames.filter { it.gameId == 1 }.all { it.swapPartnerPlayerGameId == null })
    }

    @Test
    fun recommendationSwapSlotFollowsRunnerUpSwaps() {
        val repo = demoRepo()
        val rec = repo.recommendStartingSeven(1)
        assertTrue(rec.fieldCandidates.all { it.player.role == PlayerRole.FIELD_PLAYER })

        val slotIdx = rec.slots.indices.first { rec.canSwapToRunnerUp(it) && rec.slots[it].runnerUp?.player?.role == PlayerRole.FIELD_PLAYER }
        val runnerUpId = rec.slots[slotIdx].runnerUp!!.player.playerId
        val withSwap = rec.withSwapStandby(runnerUpId)
        assertEquals(runnerUpId, withSwap.swapStandbyId)

        // Wechselspieler rückt in die Startsieben → Wechsel-Platz leer
        val swapped = withSwap.swapWithRunnerUp(slotIdx)
        assertNull(swapped.swapStandbyId)
        assertNull(swapped.swapPair)
    }
}
