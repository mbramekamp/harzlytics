package com.handball.stattracker

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
    fun allEightAreActiveForTimeAndPlusMinus() {
        val repo = demoRepo()
        repo.setGameClockSeconds(1, 600)
        repo.setSwapPartner(1, 3, 13)
        val standbyBefore = repo.pg(13)
        val courtBefore = repo.pg(3)
        val benchKeeperBefore = repo.pg(2)

        repo.advanceGameClock(1, 60)
        assertEquals(standbyBefore.secondsPlayed + 60, repo.pg(13).secondsPlayed, "Wechselspieler bekommt Spielzeit")
        assertEquals(courtBefore.secondsPlayed + 60, repo.pg(3).secondsPlayed)
        assertEquals(benchKeeperBefore.secondsPlayed, repo.pg(2).secondsPlayed, "Bank bleibt bei 0")

        // Tor durch den Wechselspieler: alle 8 bekommen +1, die Bank nicht
        repo.updateFieldPlayerStats(repo.pg(13).playerGameId, autoSyncTeamScore = true) {
            it.copy(goalsScored = it.goalsScored + 1, goalesAttempted = it.goalesAttempted + 1)
        }
        assertEquals(standbyBefore.plusMinus + 1, repo.pg(13).plusMinus)
        assertEquals(courtBefore.plusMinus + 1, repo.pg(3).plusMinus)
        assertEquals(benchKeeperBefore.plusMinus, repo.pg(2).plusMinus)

        // Gegentor beim TW: alle 8 bekommen −1
        repo.updateGoalKeeperStats(repo.pg(1).playerGameId, autoSyncOpponentScore = true) {
            it.copy(goalesConceded = it.goalesConceded + 1)
        }
        assertEquals(standbyBefore.plusMinus, repo.pg(13).plusMinus)

        // Aufstellung bleibt formal bei 7 (Regel 6+1 / 7+0)
        assertEquals(7, repo.snapshot.value.playerGames.count { it.gameId == 1 && it.isOnCourt })
    }

    @Test
    fun suspendedStandbyGetsNoPlayingTime() {
        val repo = demoRepo()
        repo.setGameClockSeconds(1, 600)
        repo.setSwapPartner(1, 3, 13)
        val pg13 = repo.pg(13)
        repo.updatePlayerDisciplinary(pg13.playerGameId, pg13.twoMinuteSuspensions + 1, pg13.yellowCard, pg13.redCard, pg13.blueCard)
        val before = repo.pg(13).secondsPlayed
        repo.advanceGameClock(1, 60)
        assertEquals(before, repo.pg(13).secondsPlayed)
    }

    @Test
    fun eighthPlayerToBenchOpensSlotAndSlotCanBeFilledAgain() {
        val repo = demoRepo()
        repo.setSwapPartner(1, 3, 13)
        assertFalse(repo.isSwapSlotOpen(1))

        val options = repo.substitutionOptions(1, 13)!!
        assertTrue(options.isOnCourt, "8. Spieler verhält sich wie ein Spieler auf dem Feld")
        assertTrue(options.canActAlone)

        repo.movePlayerToBench(1, 13)
        assertFalse(repo.snapshot.value.isSwapStandby(repo.pg(13)))
        assertEquals(7, repo.snapshot.value.playerGames.count { it.gameId == 1 && it.isOnCourt })
        assertTrue(repo.isSwapSlotOpen(1), "8. Platz ist frei")

        assertTrue(13 in repo.swapSlotCandidates(1))
        assertTrue(repo.fillSwapSlot(1, 13))
        assertTrue(repo.snapshot.value.isSwapStandby(repo.pg(13)))
        assertFalse(repo.isSwapSlotOpen(1))
    }

    @Test
    fun courtPartnerAloneToBenchLetsEighthPlayerTakeHisPlace() {
        val repo = demoRepo()
        repo.setSwapPartner(1, 3, 13)
        repo.movePlayerToBench(1, 3)
        assertTrue(repo.pg(13).isOnCourt, "8. Spieler spielt auf dem Platz weiter")
        assertFalse(repo.pg(3).isOnCourt)
        assertEquals(7, repo.snapshot.value.playerGames.count { it.gameId == 1 && it.isOnCourt })
        assertTrue(repo.snapshot.value.swapStandbyByCourtPlayer(1).isEmpty())
        assertTrue(repo.isSwapSlotOpen(1))
    }

    @Test
    fun substitutingEitherPartnerKeepsEightActive() {
        val repo = demoRepo()
        repo.setSwapPartner(1, 3, 13)
        val benchPlayer = repo.swapSlotCandidates(1).first()
        assertTrue(13 !in repo.courtEntryCandidates(1), "8. Spieler ist schon aktiv")
        assertTrue(13 in repo.substitutionOptions(1, benchPlayer)!!.partnerIds, "Bank-Spieler darf den 8. ersetzen")

        // Feldspieler mit 8. Spieler wird ersetzt → der Neue übernimmt das Paar
        assertTrue(repo.substitutePlayer(1, playerInId = benchPlayer, playerOutId = 3))
        assertEquals(repo.pg(benchPlayer).playerGameId, repo.pg(13).swapPartnerPlayerGameId)
        assertTrue(repo.snapshot.value.isSwapStandby(repo.pg(13)))
        assertNull(repo.pg(3).swapPartnerPlayerGameId)

        // 8. Spieler wird ersetzt → Paar bleibt beim Feldspieler
        assertTrue(repo.substitutePlayer(1, playerInId = 3, playerOutId = 13))
        assertTrue(repo.snapshot.value.isSwapStandby(repo.pg(3)))
        assertEquals(repo.pg(benchPlayer).playerGameId, repo.pg(3).swapPartnerPlayerGameId)
        assertNull(repo.pg(13).swapPartnerPlayerGameId)
        assertFalse(repo.pg(13).isOnCourt)
        assertEquals(7, repo.snapshot.value.playerGames.count { it.gameId == 1 && it.isOnCourt })
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
    }

    @Test
    fun readOnlyRoleCannotPair() {
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
