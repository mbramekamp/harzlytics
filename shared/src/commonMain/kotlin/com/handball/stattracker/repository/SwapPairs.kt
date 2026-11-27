package com.handball.stattracker.repository

import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.model.PlayerGame
import com.handball.stattracker.model.PlayerRole

/*
 * Wechselpaar Angriff/Abwehr: zwei Feldspieler teilen sich einen Platz und wechseln ständig.
 * Der Partner (formal auf der Bank, damit die 7er-Regel gilt) ist voll aktiv ("8. Mann"):
 * er steht in der Feldansicht, bekommt Statistik, Spielzeit und Plus/Minus wie ein Spieler auf dem Feld.
 * Ein Tausch per Knopf entfällt bewusst (Entscheidung des Nutzers 2026-11-27).
 */

/**
 * Wechselpartner von [pg] oder null. Gültig nur, wenn der Verweis gegenseitig ist, beide im selben Spiel
 * Feldspieler sind und keiner disqualifiziert ist.
 */
fun DatabaseSnapshot.swapPartnerOf(pg: PlayerGame): PlayerGame? {
    val partnerId = pg.swapPartnerPlayerGameId ?: return null
    val partner = playerGames.find { it.playerGameId == partnerId } ?: return null
    if (partner.gameId != pg.gameId || partner.swapPartnerPlayerGameId != pg.playerGameId) return null
    if (pg.isDisqualified || partner.isDisqualified) return null
    if (!isFieldPlayer(pg) || !isFieldPlayer(partner)) return null
    return partner
}

/** true = [pg] sitzt auf der Bank, sein Wechselpartner steht auf dem Feld. */
fun DatabaseSnapshot.isSwapStandby(pg: PlayerGame): Boolean =
    !pg.isOnCourt && swapPartnerOf(pg)?.isOnCourt == true

/** Statistik darf gebucht werden: auf dem Feld oder als Wechselspieler eines Feldspielers. */
fun DatabaseSnapshot.canRecordStats(pg: PlayerGame): Boolean = pg.isOnCourt || isSwapStandby(pg)

/**
 * Zählt für Spielzeit und Plus/Minus als auf dem Feld: tatsächlich auf dem Feld oder
 * Wechselspieler eines Feldspielers ohne laufende Zeitstrafe.
 */
fun DatabaseSnapshot.isActiveInGame(pg: PlayerGame, gameElapsedSeconds: Int): Boolean =
    pg.isOnCourt || (isSwapStandby(pg) && !pg.hasActiveSuspension(gameElapsedSeconds))

/** Wechselspieler je Feldspieler im Spiel [gameId]: playerGameId (Feld) → PlayerGame (Bank). */
fun DatabaseSnapshot.swapStandbyByCourtPlayer(gameId: Int): Map<Int, PlayerGame> =
    playerGames
        .filter { it.gameId == gameId && isSwapStandby(it) }
        .mapNotNull { standby -> swapPartnerOf(standby)?.let { it.playerGameId to standby } }
        .toMap()

/** Erstes aktives Wechselpaar im Spiel [gameId] als (Wechselspieler, Partner auf dem Feld) – playerIds. */
fun DatabaseSnapshot.currentSwapPair(gameId: Int): Pair<Int, Int>? =
    swapStandbyByCourtPlayer(gameId).entries.firstNotNullOfOrNull { (courtPgId, standby) ->
        playerGames.find { it.playerGameId == courtPgId }?.let { standby.playerId to it.playerId }
    }

private fun DatabaseSnapshot.isFieldPlayer(pg: PlayerGame): Boolean =
    players.find { it.playerId == pg.playerId }?.role == PlayerRole.FIELD_PLAYER

/** Entfernt alle Wechselpaare, an denen [playerGameIds] beteiligt sind (beide Seiten). */
internal fun List<PlayerGame>.withoutSwapPairs(playerGameIds: Set<Int>): List<PlayerGame> {
    val affected = playerGameIds + filter { it.playerGameId in playerGameIds }.mapNotNull { it.swapPartnerPlayerGameId }
    return map { pg ->
        if (pg.playerGameId in affected || pg.swapPartnerPlayerGameId in affected) pg.copy(swapPartnerPlayerGameId = null) else pg
    }
}
