package com.handball.stattracker

import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.model.UserRole
import com.handball.stattracker.repository.DemoDataSeed
import com.handball.stattracker.repository.HandballRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Stakeholder-Frage: kann die Rolle TRAINER ein Spiel anlegen, starten und Statistik erfassen?
 * Das Repository bekommt seine Rechte wie in der App aus [UserProfile.canEdit].
 */
class TrainerRoleRepositoryTest {

    private fun repoFor(profile: UserProfile) = HandballRepository(persistLocally = false).apply {
        replaceSnapshot(DemoDataSeed.createDemoSnapshot())
        setCanEdit(profile.canEdit)
    }

    private val activeTrainer = UserProfile(id = "t1", role = UserRole.TRAINER, status = "ACTIVE")

    @Test
    fun activeTrainerCanRunWholeGame() {
        assertTrue(activeTrainer.canEdit)
        val repo = repoFor(activeTrainer)
        val state = repo.snapshot.value
        val seasonId = state.seasons.first().seasonId
        val opponentId = state.opponentTeams.first().opponentTeamId
        val squad = state.players.map { it.playerId }.toSet()

        // Spiel anlegen (mit Aufgebot) → Startsieben steht automatisch
        val game = repo.addGame(
            seasonId = seasonId, opponentId = opponentId, secondOpponentId = null, isHomeGame = true,
            location = "Halle", gameType = "Ligaspiel", gameDate = "2026-11-28", nominatedPlayerIds = squad
        )
        val onCourt = { repo.snapshot.value.playerGames.filter { it.gameId == game.gameId && it.isOnCourt } }
        assertEquals(7, onCourt().size)

        // Startsieben neu setzen, Uhr starten
        val rec = repo.recommendStartingSeven(game.gameId)
        assertTrue(repo.applyStartingSevenLineup(game.gameId, rec.chosenPlayerIds))
        repo.advanceGameClock(game.gameId, 30)
        assertEquals(30, repo.snapshot.value.games.first { it.gameId == game.gameId }.elapsedSeconds)

        // Tor erfassen → Spielstand
        val scorer = onCourt().first { pg -> repo.snapshot.value.fieldPlayerStats.any { it.playerGameId == pg.playerGameId } }
        repo.updateFieldPlayerStats(scorer.playerGameId, autoSyncTeamScore = true) {
            it.copy(goalsScored = it.goalsScored + 1, goalesAttempted = it.goalesAttempted + 1)
        }
        assertEquals(1, repo.snapshot.value.fieldPlayerStats.first { it.playerGameId == scorer.playerGameId }.goalsScored)
        assertEquals(1, repo.snapshot.value.games.first { it.gameId == game.gameId }.homeTeamScore)

        // Wechsel
        val bench = repo.snapshot.value.playerGames.first { pg ->
            pg.gameId == game.gameId && !pg.isOnCourt &&
                repo.substitutionOptions(game.gameId, pg.playerId)?.partnerIds?.isNotEmpty() == true
        }
        val out = repo.substitutionOptions(game.gameId, bench.playerId)!!.partnerIds.first()
        assertTrue(repo.substitutePlayer(game.gameId, playerInId = bench.playerId, playerOutId = out))
    }

    @Test
    fun pendingTrainerAndPlayerCannotEdit() {
        listOf(
            UserProfile(id = "t2", role = UserRole.TRAINER, status = "PENDING"),
            UserProfile(id = "s1", role = UserRole.SPIELER, status = "ACTIVE")
        ).forEach { profile ->
            assertFalse(profile.canEdit, "${profile.role}/${profile.status}")
            val repo = repoFor(profile)
            val before = repo.snapshot.value
            repo.advanceGameClock(1, 30)
            repo.updateFieldPlayerStats(3) { it.copy(goalsScored = it.goalsScored + 1) }
            repo.substitutePlayer(1, playerInId = 13, playerOutId = 3)
            assertEquals(before, repo.snapshot.value, "${profile.role}/${profile.status} darf nichts ändern")
        }
    }

    @Test
    fun adminCanEditLikeTrainer() {
        assertTrue(UserProfile(id = "a1", role = UserRole.ADMIN, status = "ACTIVE").canEdit)
    }
}
