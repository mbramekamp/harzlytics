package com.handball.stattracker.repository

import com.handball.stattracker.config.SupabaseConfig
import com.handball.stattracker.model.*
import com.handball.stattracker.storage.PlatformStorage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class HandballRepository(
    private val autoSync: Boolean = false
) {
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var debounceSyncJob: Job? = null

    private val httpClient = HttpClient {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
    }

    private val _snapshot = MutableStateFlow(loadInitialSnapshot())
    val snapshot: StateFlow<DatabaseSnapshot> = _snapshot.asStateFlow()

    private val _syncStatus = MutableStateFlow("Offline-First bereit (${PlatformStorage.platformName()})")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private fun loadInitialSnapshot(): DatabaseSnapshot {
        val loaded = PlatformStorage.loadSnapshot()
        if (loaded != null && loaded.positions.isNotEmpty()) {
            val migrated = migrateSnapshotIfNeeded(loaded)
            if (migrated != loaded) {
                PlatformStorage.saveSnapshot(migrated)
            }
            return migrated
        }
        val seeded = DemoDataSeed.createDemoSnapshot()
        PlatformStorage.saveSnapshot(seeded)
        return seeded
    }

    /**
     * Stellt sicher, dass ältere gespeicherte Snapshots (vor Einführung von Aufstellung, +/- und Spielprotokoll)
     * automatisch eine aktive Startsieben auf dem Feld sowie realistische Demo-Protokolle erhalten.
     */
    private fun migrateSnapshotIfNeeded(snapshot: DatabaseSnapshot): DatabaseSnapshot {
        val hasAnyOnCourt = snapshot.playerGames.any { it.isOnCourt }
        if (hasAnyOnCourt && snapshot.gameLogs.isNotEmpty()) {
            return snapshot
        }

        val demo = DemoDataSeed.createDemoSnapshot()
        val demoPgById = demo.playerGames.associateBy { it.playerGameId }

        val updatedGames = snapshot.games.map { g ->
            if (g.elapsedSeconds == 0 && (g.homeTeamScore > 0 || g.awayTeamScore > 0)) {
                g.copy(elapsedSeconds = 3600)
            } else g
        }

        val updatedPlayerGames = snapshot.playerGames.groupBy { it.gameId }.flatMap { (_, pbs) ->
            val anyActive = pbs.any { it.isOnCourt }
            if (anyActive) {
                pbs
            } else {
                // Prüfe ob Demo-Werte vorhanden sind, sonst wähle bis zu 7 Spieler (1 TW + 6 Feld)
                val keeperCandidates = pbs.filter { pg ->
                    snapshot.players.find { it.playerId == pg.playerId }?.role == PlayerRole.GOALKEEPER
                }
                val fieldCandidates = pbs.filter { pg ->
                    snapshot.players.find { it.playerId == pg.playerId }?.role == PlayerRole.FIELD_PLAYER
                }
                val defaultActiveIds = (keeperCandidates.take(1) + fieldCandidates.take(6))
                    .map { it.playerGameId }
                    .toSet()

                pbs.map { pg ->
                    val demoPg = demoPgById[pg.playerGameId]
                    if (demoPg != null && demoPg.gameId == pg.gameId && demoPg.playerId == pg.playerId) {
                        pg.copy(
                            isOnCourt = demoPg.isOnCourt,
                            plusMinus = if (pg.plusMinus == 0) demoPg.plusMinus else pg.plusMinus,
                            secondsPlayed = if (pg.secondsPlayed == 0) demoPg.secondsPlayed else pg.secondsPlayed
                        )
                    } else {
                        pg.copy(
                            isOnCourt = pg.playerGameId in defaultActiveIds,
                            secondsPlayed = if (pg.secondsPlayed == 0 && pg.playerGameId in defaultActiveIds) 1800 else pg.secondsPlayed
                        )
                    }
                }
            }
        }

        val updatedLogs = if (snapshot.gameLogs.isEmpty()) {
            demo.gameLogs.filter { log -> snapshot.games.any { it.gameId == log.gameId } }
        } else {
            snapshot.gameLogs
        }

        return snapshot.copy(
            games = updatedGames,
            playerGames = updatedPlayerGames,
            gameLogs = updatedLogs
        )
    }

    private fun mutate(block: (DatabaseSnapshot) -> DatabaseSnapshot) {
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = block(_snapshot.value).copy(lastUpdatedEpochMillis = now)
        _snapshot.value = updated
        PlatformStorage.saveSnapshot(updated)
        scheduleAutoSyncIfEnabled()
    }

    private fun scheduleAutoSyncIfEnabled() {
        if (!autoSync || !SupabaseConfig.hasConfiguredKeys()) return
        debounceSyncJob?.cancel()
        debounceSyncJob = syncScope.launch {
            delay(700)
            syncWithBackend()
        }
    }

    fun resetToDemoData() {
        val demo = DemoDataSeed.createDemoSnapshot().copy(
            lastUpdatedEpochMillis = Clock.System.now().toEpochMilliseconds()
        )
        _snapshot.value = demo
        PlatformStorage.saveSnapshot(demo)
        _syncStatus.value = "Demo-Daten erfolgreich wiederhergestellt"
        scheduleAutoSyncIfEnabled()
    }

    fun updateClubName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        mutate { it.copy(ownClubName = trimmed) }
    }

    // --- POSITION ---
    fun addPosition(positionName: String): Position {
        val current = _snapshot.value.positions
        val nextId = (current.maxOfOrNull { it.positionId } ?: 0) + 1
        val pos = Position(positionId = nextId, positionName = positionName.trim())
        mutate { it.copy(positions = it.positions + pos) }
        return pos
    }

    // --- PLAYER & TRAINER ---
    fun addPlayer(
        firstName: String,
        lastName: String,
        email: String,
        role: PlayerRole,
        positionId: Int?,
        jerseyHome: Int?,
        jerseyAway: Int?
    ): Player {
        val nextId = (_snapshot.value.players.maxOfOrNull { it.playerId } ?: 0) + 1
        val player = Player(
            playerId = nextId,
            positionId = if (role == PlayerRole.COACH) null else positionId,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            email = email.trim(),
            role = role,
            jerseyNumberHomeKit = if (role == PlayerRole.COACH) null else jerseyHome,
            jerseyNumberAwayKit = if (role == PlayerRole.COACH) null else jerseyAway
        )
        mutate { it.copy(players = it.players + player) }
        return player
    }

    fun updatePlayer(updatedPlayer: Player) {
        mutate { state ->
            state.copy(players = state.players.map { if (it.playerId == updatedPlayer.playerId) updatedPlayer else it })
        }
    }

    fun deletePlayer(playerId: Int) {
        mutate { state ->
            val removedPgIds = state.playerGames.filter { it.playerId == playerId }.map { it.playerGameId }.toSet()
            state.copy(
                players = state.players.filterNot { it.playerId == playerId },
                playerGames = state.playerGames.filterNot { it.playerId == playerId },
                fieldPlayerStats = state.fieldPlayerStats.filterNot { it.playerGameId in removedPgIds },
                goalKeeperStats = state.goalKeeperStats.filterNot { it.playerGameId in removedPgIds },
                coachStats = state.coachStats.filterNot { it.playerGameId in removedPgIds },
                gameLogs = state.gameLogs.filterNot { it.primaryPlayerId == playerId || it.secondaryPlayerId == playerId }
            )
        }
    }

    // --- SEASON ---
    fun addSeason(division: String, startOfSeason: String, endOfSeason: String): Season {
        val nextId = (_snapshot.value.seasons.maxOfOrNull { it.seasonId } ?: 0) + 1
        val season = Season(
            seasonId = nextId,
            division = division.trim(),
            startOfSeason = startOfSeason.trim(),
            endOfSeason = endOfSeason.trim()
        )
        mutate { it.copy(seasons = listOf(season) + it.seasons) }
        return season
    }

    // --- OPPONENT_TEAMS & SEASON_OPPONENT ---
    fun addOpponentWithSeasons(
        opponentName: String,
        opponentLocation: String,
        assignedSeasonIds: Set<Int>
    ): OpponentTeam {
        val nextId = (_snapshot.value.opponentTeams.maxOfOrNull { it.opponentTeamId } ?: 0) + 1
        val opponent = OpponentTeam(
            opponentTeamId = nextId,
            opponentName = opponentName.trim(),
            opponentLocation = opponentLocation.trim()
        )
        val links = assignedSeasonIds.map { seasonId ->
            SeasonOpponent(seasonId = seasonId, opponentTeamId = nextId)
        }
        mutate { state ->
            state.copy(
                opponentTeams = state.opponentTeams + opponent,
                seasonOpponents = (state.seasonOpponents + links).distinct()
            )
        }
        return opponent
    }

    fun toggleOpponentSeasonAssignment(seasonId: Int, opponentTeamId: Int) {
        mutate { state ->
            val exists = state.seasonOpponents.any {
                it.seasonId == seasonId && it.opponentTeamId == opponentTeamId
            }
            val updatedLinks = if (exists) {
                state.seasonOpponents.filterNot {
                    it.seasonId == seasonId && it.opponentTeamId == opponentTeamId
                }
            } else {
                state.seasonOpponents + SeasonOpponent(seasonId, opponentTeamId)
            }
            state.copy(seasonOpponents = updatedLinks)
        }
    }

    // --- GAME & PLAYER_GAME ---
    fun addGame(
        seasonId: Int,
        opponentId: Int,
        secondOpponentId: Int?,
        isHomeGame: Boolean,
        location: String,
        gameType: String,
        gameDate: String,
        homeHalftime: Int = 0,
        awayHalftime: Int = 0,
        homeScore: Int = 0,
        awayScore: Int = 0,
        nominatedPlayerIds: Set<Int> = emptySet()
    ): Game {
        val state = _snapshot.value
        val nextGameId = (state.games.maxOfOrNull { it.gameId } ?: 0) + 1
        val newGame = Game(
            gameId = nextGameId,
            seasonId = seasonId,
            opponentId = opponentId,
            secondOpponentId = secondOpponentId,
            isHomeGame = isHomeGame,
            location = location.trim().ifEmpty { "Sporthalle" },
            gameType = gameType.trim().ifEmpty { "Ligaspiel" },
            gameDate = gameDate.trim(),
            homeTeamHalftimeScore = homeHalftime,
            awayTeamHalftimeScore = awayHalftime,
            homeTeamScore = homeScore,
            awayTeamScore = awayScore,
            elapsedSeconds = 0
        )

        var nextPgId = (state.playerGames.maxOfOrNull { it.playerGameId } ?: 0) + 1
        var nextFieldStatId = (state.fieldPlayerStats.maxOfOrNull { it.fieldPlayerGamestatsId } ?: 0) + 1
        var nextKeeperStatId = (state.goalKeeperStats.maxOfOrNull { it.goalKeeperGamestatsId } ?: 0) + 1

        val newPlayerGames = mutableListOf<PlayerGame>()
        val newFieldStats = mutableListOf<FieldPlayerGameStats>()
        val newKeeperStats = mutableListOf<GoalKeeperGameStats>()
        val initialLogs = mutableListOf<GameLogEntry>()

        if (secondOpponentId == null) {
            val nominatedPlayers = nominatedPlayerIds.mapNotNull { pid ->
                state.players.find { it.playerId == pid }
            }
            // Wähle bis zu 7 Spieler als initiale Startsieben (1 Torwart + bis zu 6 Feldspieler)
            val startingKeeper = nominatedPlayers.firstOrNull { it.role == PlayerRole.GOALKEEPER }
            val startingField = nominatedPlayers
                .filter { it.role == PlayerRole.FIELD_PLAYER }
                .distinctBy { it.positionId ?: it.playerId }
                .let { distinctPos ->
                    if (distinctPos.size >= 6) distinctPos.take(6)
                    else (distinctPos + nominatedPlayers.filter { it.role == PlayerRole.FIELD_PLAYER && it !in distinctPos }).take(6)
                }
            val startingSevenIds = (listOfNotNull(startingKeeper) + startingField).map { it.playerId }.toSet()

            nominatedPlayers.forEach { player ->
                val pgId = nextPgId++
                newPlayerGames += PlayerGame(
                    playerGameId = pgId,
                    gameId = nextGameId,
                    playerId = player.playerId,
                    isOnCourt = player.playerId in startingSevenIds
                )
                when (player.role) {
                    PlayerRole.FIELD_PLAYER -> {
                        newFieldStats += FieldPlayerGameStats(
                            fieldPlayerGamestatsId = nextFieldStatId++,
                            playerGameId = pgId
                        )
                    }
                    PlayerRole.GOALKEEPER -> {
                        newKeeperStats += GoalKeeperGameStats(
                            goalKeeperGamestatsId = nextKeeperStatId++,
                            playerGameId = pgId
                        )
                    }
                    PlayerRole.COACH -> {
                        // Timeouts werden bei Bedarf über COACH_GAMESTATS + TIMEOUT angelegt
                    }
                }
            }

            if (startingSevenIds.isNotEmpty()) {
                val nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1
                val names = nominatedPlayers
                    .filter { it.playerId in startingSevenIds }
                    .joinToString(", ") { "#${it.jerseyNumberHomeKit ?: "-"} ${it.lastName}" }
                initialLogs += GameLogEntry(
                    logId = nextLogId,
                    gameId = nextGameId,
                    gameSecond = 0,
                    eventType = GameLogEventType.LINEUP_SET,
                    primaryPlayerId = startingSevenIds.firstOrNull(),
                    homeScoreAtEvent = homeScore,
                    awayScoreAtEvent = awayScore,
                    description = "Startaufstellung (${startingSevenIds.size}/7): $names"
                )
            }
        }

        // Stelle sicher, dass Gegner der Saison zugewiesen sind
        val extraSeasonOpponents = buildList {
            add(SeasonOpponent(seasonId, opponentId))
            if (secondOpponentId != null) {
                add(SeasonOpponent(seasonId, secondOpponentId))
            }
        }

        mutate { current ->
            current.copy(
                games = listOf(newGame) + current.games,
                playerGames = current.playerGames + newPlayerGames,
                fieldPlayerStats = current.fieldPlayerStats + newFieldStats,
                goalKeeperStats = current.goalKeeperStats + newKeeperStats,
                seasonOpponents = (current.seasonOpponents + extraSeasonOpponents).distinct(),
                gameLogs = current.gameLogs + initialLogs
            )
        }
        return newGame
    }

    fun togglePlayerInGame(gameId: Int, playerId: Int) {
        mutate { state ->
            val existing = state.playerGames.find { it.gameId == gameId && it.playerId == playerId }
            if (existing != null) {
                val pgId = existing.playerGameId
                state.copy(
                    playerGames = state.playerGames.filterNot { it.playerGameId == pgId },
                    fieldPlayerStats = state.fieldPlayerStats.filterNot { it.playerGameId == pgId },
                    goalKeeperStats = state.goalKeeperStats.filterNot { it.playerGameId == pgId },
                    coachStats = state.coachStats.filterNot { it.playerGameId == pgId }
                )
            } else {
                val player = state.players.find { it.playerId == playerId } ?: return@mutate state
                val activeCourtPlayers = state.playerGames.mapNotNull { pg ->
                    if (pg.gameId == gameId && pg.isOnCourt) {
                        state.players.find { it.playerId == pg.playerId && it.role != PlayerRole.COACH }
                    } else null
                }
                val activeFieldCount = activeCourtPlayers.count { it.role == PlayerRole.FIELD_PLAYER }
                val activeKeeperCount = activeCourtPlayers.count { it.role == PlayerRole.GOALKEEPER }
                val newFieldCount = activeFieldCount + (if (player.role == PlayerRole.FIELD_PLAYER) 1 else 0)
                val newKeeperCount = activeKeeperCount + (if (player.role == PlayerRole.GOALKEEPER) 1 else 0)
                val putOnCourt = player.role != PlayerRole.COACH &&
                    isValidActiveCourtLineup(newFieldCount, newKeeperCount)
                val nextPgId = (state.playerGames.maxOfOrNull { it.playerGameId } ?: 0) + 1
                val newPg = PlayerGame(
                    playerGameId = nextPgId,
                    gameId = gameId,
                    playerId = playerId,
                    isOnCourt = putOnCourt
                )
                val updatedField = if (player.role == PlayerRole.FIELD_PLAYER) {
                    val nextId = (state.fieldPlayerStats.maxOfOrNull { it.fieldPlayerGamestatsId } ?: 0) + 1
                    state.fieldPlayerStats + FieldPlayerGameStats(fieldPlayerGamestatsId = nextId, playerGameId = nextPgId)
                } else state.fieldPlayerStats
                val updatedKeeper = if (player.role == PlayerRole.GOALKEEPER) {
                    val nextId = (state.goalKeeperStats.maxOfOrNull { it.goalKeeperGamestatsId } ?: 0) + 1
                    state.goalKeeperStats + GoalKeeperGameStats(goalKeeperGamestatsId = nextId, playerGameId = nextPgId)
                } else state.goalKeeperStats

                state.copy(
                    playerGames = state.playerGames + newPg,
                    fieldPlayerStats = updatedField,
                    goalKeeperStats = updatedKeeper
                )
            }
        }
    }

    // --- MATCH CLOCK & EINSATZZEIT ---
    fun advanceGameClock(gameId: Int, deltaSeconds: Int) {
        if (deltaSeconds == 0) return
        mutate { state ->
            val game = state.games.find { it.gameId == gameId } ?: return@mutate state
            val newElapsed = (game.elapsedSeconds + deltaSeconds).coerceIn(0, 3600)
            val actualDelta = newElapsed - game.elapsedSeconds

            val updatedGames = state.games.map { g ->
                if (g.gameId == gameId) g.copy(elapsedSeconds = newElapsed) else g
            }

            val updatedPlayerGames = state.playerGames.map { pg ->
                if (pg.gameId == gameId) {
                    val role = state.players.find { it.playerId == pg.playerId }?.role
                    val addedTime = if (pg.isOnCourt && role != PlayerRole.COACH && actualDelta > 0) actualDelta else 0
                    val clearedSuspension = if (pg.activeSuspensionUntilSeconds != null && newElapsed >= pg.activeSuspensionUntilSeconds) {
                        null
                    } else {
                        pg.activeSuspensionUntilSeconds
                    }
                    pg.copy(
                        secondsPlayed = (pg.secondsPlayed + addedTime).coerceAtLeast(0),
                        activeSuspensionUntilSeconds = clearedSuspension
                    )
                } else pg
            }

            state.copy(
                games = updatedGames,
                playerGames = updatedPlayerGames
            )
        }
    }

    fun setGameClockSeconds(gameId: Int, targetSeconds: Int) {
        val clamped = targetSeconds.coerceIn(0, 3600)
        val current = _snapshot.value.games.find { it.gameId == gameId }?.elapsedSeconds ?: return
        if (clamped == current) return
        advanceGameClock(gameId, clamped - current)
    }

    // --- AKTIVE 7-ON-COURT AUFSTELLUNG & WECHSEL (SUBSTITUTIONS) ---

    /**
     * Prüft die Handball-Regel für die aktiven Spieler auf dem Feld:
     * - Maximal 7 aktive Spieler gleichzeitig auf dem Feld:
     *   Entweder **6 Feldspieler + 1 Torwart** ODER **7 Feldspieler + 0 Torwart** (bzw. weniger bei Unterzahl).
     * - Maximal 1 Torwart gleichzeitig auf dem Feld (`goalKeeperCount <= 1`).
     * - Maximal 7 Feldspieler gleichzeitig auf dem Feld (`fieldPlayerCount <= 7`).
     */
    fun isValidActiveCourtLineup(fieldPlayerCount: Int, goalKeeperCount: Int): Boolean =
        fieldPlayerCount in 0..7 &&
            goalKeeperCount in 0..1 &&
            (fieldPlayerCount + goalKeeperCount) <= 7

    /**
     * Prüft vorab, ob ein Bank-Spieler ([playerInId]) eingewechselt werden darf:
     * - Wenn [playerOutId] != null: 1-zu-1 Wechsel (muss eine gültige Kombination aus max. 6 Feld + 1 TW oder 7 Feld + 0 TW ergeben).
     * - Wenn [playerOutId] == null: Direkt aufs Feld (nur erlaubt, wenn < 7 aktiv sind UND bei einem Torwart noch kein Torwart auf dem Feld steht).
     */
    fun canSubstituteOrEnterCourt(
        gameId: Int,
        playerInId: Int,
        playerOutId: Int? = null,
        state: DatabaseSnapshot = _snapshot.value
    ): Boolean {
        val game = state.games.find { it.gameId == gameId } ?: return false
        val pgIn = state.playerGames.find { it.gameId == gameId && it.playerId == playerInId } ?: return false
        val playerIn = state.players.find { it.playerId == playerInId } ?: return false
        if (playerIn.role == PlayerRole.COACH) return false
        if (pgIn.isOnCourt) return false
        if (pgIn.isDisqualified || pgIn.hasActiveSuspension(game.elapsedSeconds)) return false

        val activeCourtPlayers = state.playerGames.mapNotNull { pg ->
            if (pg.gameId == gameId && pg.isOnCourt) {
                state.players.find { it.playerId == pg.playerId && it.role != PlayerRole.COACH }
            } else null
        }
        val activeFieldCount = activeCourtPlayers.count { it.role == PlayerRole.FIELD_PLAYER }
        val activeKeeperCount = activeCourtPlayers.count { it.role == PlayerRole.GOALKEEPER }

        return if (playerOutId != null) {
            if (playerOutId == playerInId) return false
            val pgOut = state.playerGames.find { it.gameId == gameId && it.playerId == playerOutId } ?: return false
            val playerOut = state.players.find { it.playerId == playerOutId } ?: return false
            if (!pgOut.isOnCourt || playerOut.role == PlayerRole.COACH) return false

            val newFieldCount = activeFieldCount -
                (if (playerOut.role == PlayerRole.FIELD_PLAYER) 1 else 0) +
                (if (playerIn.role == PlayerRole.FIELD_PLAYER) 1 else 0)
            val newKeeperCount = activeKeeperCount -
                (if (playerOut.role == PlayerRole.GOALKEEPER) 1 else 0) +
                (if (playerIn.role == PlayerRole.GOALKEEPER) 1 else 0)

            isValidActiveCourtLineup(newFieldCount, newKeeperCount)
        } else {
            val newFieldCount = activeFieldCount + (if (playerIn.role == PlayerRole.FIELD_PLAYER) 1 else 0)
            val newKeeperCount = activeKeeperCount + (if (playerIn.role == PlayerRole.GOALKEEPER) 1 else 0)
            isValidActiveCourtLineup(newFieldCount, newKeeperCount)
        }
    }

    /**
     * Führt einen Spielerwechsel durch:
     * - Wenn [playerOutId] angegeben ist: 1-zu-1 Wechsel ([playerInId] kommt von der Bank aufs Feld, [playerOutId] geht auf die Bank).
     * - Wenn [playerOutId] null ist: [playerInId] wird direkt von der Bank aufs Feld gestellt (sofern < 7 Feld-/Torwart-Spieler aktiv sind und max. 1 TW).
     * Prüft zwingend die Regel: 6 Feldspieler + 1 Torwart ODER 7 Feldspieler (0 Torwart).
     * Protokolliert den Wechsel automatisch im Spielprotokoll (GAME_LOG).
     */
    fun substitutePlayer(gameId: Int, playerInId: Int, playerOutId: Int? = null): Boolean {
        var success = false
        mutate { state ->
            if (!canSubstituteOrEnterCourt(gameId, playerInId, playerOutId, state)) return@mutate state
            val game = state.games.find { it.gameId == gameId } ?: return@mutate state
            val pgIn = state.playerGames.find { it.gameId == gameId && it.playerId == playerInId } ?: return@mutate state
            val playerIn = state.players.find { it.playerId == playerInId } ?: return@mutate state

            val activeCourtPlayers = state.playerGames.mapNotNull { pg ->
                if (pg.gameId == gameId && pg.isOnCourt) {
                    state.players.find { it.playerId == pg.playerId && it.role != PlayerRole.COACH }
                } else null
            }
            val activeFieldCount = activeCourtPlayers.count { it.role == PlayerRole.FIELD_PLAYER }
            val activeKeeperCount = activeCourtPlayers.count { it.role == PlayerRole.GOALKEEPER }

            if (playerOutId != null) {
                val pgOut = state.playerGames.find { it.gameId == gameId && it.playerId == playerOutId } ?: return@mutate state
                val playerOut = state.players.find { it.playerId == playerOutId } ?: return@mutate state

                val newFieldCount = activeFieldCount -
                    (if (playerOut.role == PlayerRole.FIELD_PLAYER) 1 else 0) +
                    (if (playerIn.role == PlayerRole.FIELD_PLAYER) 1 else 0)
                val newKeeperCount = activeKeeperCount -
                    (if (playerOut.role == PlayerRole.GOALKEEPER) 1 else 0) +
                    (if (playerIn.role == PlayerRole.GOALKEEPER) 1 else 0)

                val updatedPgs = state.playerGames.map { pg ->
                    when (pg.playerGameId) {
                        pgIn.playerGameId -> pg.copy(isOnCourt = true)
                        pgOut.playerGameId -> pg.copy(isOnCourt = false)
                        else -> pg
                    }
                }

                val inNr = playerIn.jerseyNumberHomeKit?.let { "#$it " } ?: ""
                val outNr = playerOut.jerseyNumberHomeKit?.let { "#$it " } ?: ""
                val modePrefix = when {
                    newFieldCount == 7 && newKeeperCount == 0 -> "Wechsel (7. Feldspieler / Empty Net)"
                    playerIn.role == PlayerRole.GOALKEEPER && playerOut.role == PlayerRole.FIELD_PLAYER -> "Wechsel (TW zurück → 6+1)"
                    playerIn.role == PlayerRole.GOALKEEPER && playerOut.role == PlayerRole.GOALKEEPER -> "Torwart-Wechsel"
                    else -> "Wechsel"
                }
                val nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1
                val logEntry = GameLogEntry(
                    logId = nextLogId,
                    gameId = gameId,
                    gameSecond = game.elapsedSeconds,
                    eventType = GameLogEventType.SUBSTITUTION,
                    primaryPlayerId = playerIn.playerId,
                    secondaryPlayerId = playerOut.playerId,
                    homeScoreAtEvent = game.homeTeamScore,
                    awayScoreAtEvent = game.awayTeamScore,
                    description = "$modePrefix: $inNr${playerIn.fullName} rein für $outNr${playerOut.fullName} ($newFieldCount Feld + $newKeeperCount TW)"
                )
                success = true
                state.copy(
                    playerGames = updatedPgs,
                    gameLogs = state.gameLogs + logEntry
                )
            } else {
                val newFieldCount = activeFieldCount + (if (playerIn.role == PlayerRole.FIELD_PLAYER) 1 else 0)
                val newKeeperCount = activeKeeperCount + (if (playerIn.role == PlayerRole.GOALKEEPER) 1 else 0)
                val updatedPgs = state.playerGames.map { pg ->
                    if (pg.playerGameId == pgIn.playerGameId) pg.copy(isOnCourt = true) else pg
                }
                val inNr = playerIn.jerseyNumberHomeKit?.let { "#$it " } ?: ""
                val nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1
                val logEntry = GameLogEntry(
                    logId = nextLogId,
                    gameId = gameId,
                    gameSecond = game.elapsedSeconds,
                    eventType = GameLogEventType.SUBSTITUTION,
                    primaryPlayerId = playerIn.playerId,
                    secondaryPlayerId = null,
                    homeScoreAtEvent = game.homeTeamScore,
                    awayScoreAtEvent = game.awayTeamScore,
                    description = "Einwechslung: $inNr${playerIn.fullName} aufs Feld ($newFieldCount Feld + $newKeeperCount TW)"
                )
                success = true
                state.copy(
                    playerGames = updatedPgs,
                    gameLogs = state.gameLogs + logEntry
                )
            }
        }
        return success
    }

    /**
     * Nimmt einen aktuell auf dem Feld stehenden Spieler auf die Bank herunter (z.B. bei Unterzahl oder taktischem Wechsel).
     */
    fun movePlayerToBench(gameId: Int, playerOutId: Int, reasonSuffix: String = "") {
        mutate { state ->
            val game = state.games.find { it.gameId == gameId } ?: return@mutate state
            val pgOut = state.playerGames.find { it.gameId == gameId && it.playerId == playerOutId } ?: return@mutate state
            if (!pgOut.isOnCourt) return@mutate state
            val playerOut = state.players.find { it.playerId == playerOutId } ?: return@mutate state

            val updatedPgs = state.playerGames.map { pg ->
                if (pg.playerGameId == pgOut.playerGameId) pg.copy(isOnCourt = false) else pg
            }
            val remainingCount = updatedPgs.count { pg ->
                pg.gameId == gameId && pg.isOnCourt &&
                    state.players.find { it.playerId == pg.playerId }?.role != PlayerRole.COACH
            }
            val outNr = playerOut.jerseyNumberHomeKit?.let { "#$it " } ?: ""
            val extra = if (reasonSuffix.isNotBlank()) " ($reasonSuffix)" else ""
            val nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1
            val logEntry = GameLogEntry(
                logId = nextLogId,
                gameId = gameId,
                gameSecond = game.elapsedSeconds,
                eventType = GameLogEventType.SUBSTITUTION,
                primaryPlayerId = null,
                secondaryPlayerId = playerOut.playerId,
                homeScoreAtEvent = game.homeTeamScore,
                awayScoreAtEvent = game.awayTeamScore,
                description = "Auf die Bank: $outNr${playerOut.fullName}$extra ($remainingCount/7 auf dem Feld)"
            )
            state.copy(
                playerGames = updatedPgs,
                gameLogs = state.gameLogs + logEntry
            )
        }
    }

    /**
     * Besetzt das Spielfeld automatisch mit der empfohlenen Startsieben:
     * - [useSeventhFieldPlayer] == false (Standard): **6 Feldspieler + 1 Torwart** (nach bestem Performance-Index je Position).
     * - [useSeventhFieldPlayer] == true: **7 Feldspieler + 0 Torwart** (Empty Net / 7. Feldspieler).
     */
    fun applyRecommendedStartingSevenToGame(gameId: Int, useSeventhFieldPlayer: Boolean = false) {
        mutate { state ->
            val game = state.games.find { it.gameId == gameId } ?: return@mutate state
            val gamePgs = state.playerGames.filter { it.gameId == gameId }
            val eligiblePgs = gamePgs.filter { pg ->
                val p = state.players.find { it.playerId == pg.playerId }
                p != null && p.role != PlayerRole.COACH && !pg.isDisqualified && !pg.hasActiveSuspension(game.elapsedSeconds)
            }
            if (eligiblePgs.isEmpty()) return@mutate state

            val rankings = computePlayerRankings(seasonIdFilter = game.seasonId, state = state)
                .associateBy { it.player.playerId }

            val keeperPg = if (useSeventhFieldPlayer) {
                null
            } else {
                eligiblePgs
                    .filter { pg -> state.players.find { it.playerId == pg.playerId }?.role == PlayerRole.GOALKEEPER }
                    .maxByOrNull { pg -> rankings[pg.playerId]?.performanceScore ?: 0.0 }
            }

            val targetFieldCount = if (keeperPg != null) 6 else 7

            val fieldPgs = eligiblePgs
                .filter { pg -> state.players.find { it.playerId == pg.playerId }?.role == PlayerRole.FIELD_PLAYER }
                .sortedByDescending { pg -> rankings[pg.playerId]?.performanceScore ?: 0.0 }

            val selectedFieldByPos = fieldPgs
                .distinctBy { pg -> state.players.find { it.playerId == pg.playerId }?.positionId ?: pg.playerId }
                .take(targetFieldCount)

            val finalField = if (selectedFieldByPos.size >= targetFieldCount) {
                selectedFieldByPos
            } else {
                (selectedFieldByPos + fieldPgs.filterNot { it in selectedFieldByPos }).take(targetFieldCount)
            }

            val chosenPgIds = (listOfNotNull(keeperPg) + finalField).take(7).map { it.playerGameId }.toSet()

            val updatedPgs = state.playerGames.map { pg ->
                if (pg.gameId == gameId) {
                    pg.copy(isOnCourt = pg.playerGameId in chosenPgIds)
                } else pg
            }

            val chosenNames = chosenPgIds.mapNotNull { pgId ->
                val pid = gamePgs.find { it.playerGameId == pgId }?.playerId
                state.players.find { it.playerId == pid }
            }.joinToString(", ") { "#${it.jerseyNumberHomeKit ?: "-"} ${it.lastName}" }

            val formationText = if (keeperPg != null) "${finalField.size} Feld + 1 TW" else "${finalField.size} Feld + 0 TW"
            val nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1
            val logEntry = GameLogEntry(
                logId = nextLogId,
                gameId = gameId,
                gameSecond = game.elapsedSeconds,
                eventType = GameLogEventType.LINEUP_SET,
                primaryPlayerId = null,
                homeScoreAtEvent = game.homeTeamScore,
                awayScoreAtEvent = game.awayTeamScore,
                description = "Aufstellung gesetzt ($formationText): $chosenNames"
            )

            state.copy(
                playerGames = updatedPgs,
                gameLogs = state.gameLogs + logEntry
            )
        }
    }

    fun adjustPlayerPlusMinus(playerGameId: Int, delta: Int) {
        if (delta == 0) return
        mutate { state ->
            state.copy(
                playerGames = state.playerGames.map { pg ->
                    if (pg.playerGameId == playerGameId) {
                        pg.copy(plusMinus = pg.plusMinus + delta)
                    } else pg
                }
            )
        }
    }

    fun deleteGameLogEntry(logId: Int) {
        mutate { state ->
            state.copy(gameLogs = state.gameLogs.filterNot { it.logId == logId })
        }
    }

    fun getGameLogs(gameId: Int, state: DatabaseSnapshot = _snapshot.value): List<GameLogEntry> =
        state.gameLogs
            .filter { it.gameId == gameId }
            .sortedWith(compareByDescending<GameLogEntry> { it.gameSecond }.thenByDescending { it.logId })

    fun updateGameScores(
        gameId: Int,
        homeScore: Int,
        awayScore: Int,
        homeHalftime: Int,
        awayHalftime: Int
    ) {
        mutate { state ->
            val oldGame = state.games.find { it.gameId == gameId } ?: return@mutate state
            val newHome = homeScore.coerceAtLeast(0)
            val newAway = awayScore.coerceAtLeast(0)
            val newHomeHalf = homeHalftime.coerceAtLeast(0)
            val newAwayHalf = awayHalftime.coerceAtLeast(0)

            val homeDelta = newHome - oldGame.homeTeamScore
            val awayDelta = newAway - oldGame.awayTeamScore
            val netOwnDelta = if (oldGame.isOwnTeamGame) {
                if (oldGame.isHomeGame) homeDelta - awayDelta else awayDelta - homeDelta
            } else 0

            val updatedGames = state.games.map { game ->
                if (game.gameId == gameId) {
                    game.copy(
                        homeTeamScore = newHome,
                        awayTeamScore = newAway,
                        homeTeamHalftimeScore = newHomeHalf,
                        awayTeamHalftimeScore = newAwayHalf
                    )
                } else game
            }

            val updatedPlayerGames = if (netOwnDelta != 0) {
                state.playerGames.map { pg ->
                    val role = state.players.find { it.playerId == pg.playerId }?.role
                    if (pg.gameId == gameId && pg.isOnCourt && role != PlayerRole.COACH) {
                        pg.copy(plusMinus = pg.plusMinus + netOwnDelta)
                    } else pg
                }
            } else state.playerGames

            val newLogs = if (homeDelta != 0 || awayDelta != 0) {
                val nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1
                state.gameLogs + GameLogEntry(
                    logId = nextLogId,
                    gameId = gameId,
                    gameSecond = oldGame.elapsedSeconds,
                    eventType = GameLogEventType.SCORE_UPDATE,
                    homeScoreAtEvent = newHome,
                    awayScoreAtEvent = newAway,
                    description = "Spielstand angepasst auf $newHome:$newAway (HZ $newHomeHalf:$newAwayHalf)"
                )
            } else state.gameLogs

            state.copy(
                games = updatedGames,
                playerGames = updatedPlayerGames,
                gameLogs = newLogs
            )
        }
    }

    fun deleteGame(gameId: Int) {
        mutate { state ->
            val pgIds = state.playerGames.filter { it.gameId == gameId }.map { it.playerGameId }.toSet()
            state.copy(
                games = state.games.filterNot { it.gameId == gameId },
                playerGames = state.playerGames.filterNot { it.gameId == gameId },
                fieldPlayerStats = state.fieldPlayerStats.filterNot { it.playerGameId in pgIds },
                goalKeeperStats = state.goalKeeperStats.filterNot { it.playerGameId in pgIds },
                coachStats = state.coachStats.filterNot { it.playerGameId in pgIds },
                gameLogs = state.gameLogs.filterNot { it.gameId == gameId }
            )
        }
    }

    // --- PLAYER_GAME DISCIPLINARY (INKL. AUTOMATISCHEM BANK-WECHSEL BEI 2-MIN & ROT/BLAU) ---
    fun updatePlayerDisciplinary(
        playerGameId: Int,
        twoMinuteSuspensions: Int,
        yellowCard: Boolean,
        redCard: Boolean,
        blueCard: Boolean
    ) {
        mutate { state ->
            val pg = state.playerGames.find { it.playerGameId == playerGameId } ?: return@mutate state
            val game = state.games.find { it.gameId == pg.gameId } ?: return@mutate state
            val player = state.players.find { it.playerId == pg.playerId }

            val clampedTwoMin = twoMinuteSuspensions.coerceIn(0, 3)
            val twoMinIncreased = clampedTwoMin > pg.twoMinuteSuspensions
            val yellowAdded = yellowCard && !pg.yellowCard
            val redAdded = redCard && !pg.redCard
            val blueAdded = blueCard && !pg.blueCard

            // 2-Minuten-Strafe, Rote Karte und Blaue Karte versetzen den Spieler automatisch auf die Bank
            val forceToBench = twoMinIncreased || redCard || blueCard || clampedTwoMin >= 3
            val newSuspensionUntil = when {
                twoMinIncreased -> (game.elapsedSeconds + 120).coerceAtMost(3600)
                clampedTwoMin < pg.twoMinuteSuspensions -> null
                else -> pg.activeSuspensionUntilSeconds
            }

            val updatedPlayerGames = state.playerGames.map { item ->
                if (item.playerGameId == playerGameId) {
                    item.copy(
                        twoMinuteSuspensions = clampedTwoMin,
                        yellowCard = yellowCard,
                        redCard = redCard,
                        blueCard = blueCard,
                        isOnCourt = if (forceToBench) false else item.isOnCourt,
                        activeSuspensionUntilSeconds = newSuspensionUntil
                    )
                } else item
            }

            val nrPrefix = player?.jerseyNumberHomeKit?.let { "#$it " } ?: ""
            val pName = player?.fullName ?: "Spieler"
            val benchNote = if (pg.isOnCourt && forceToBench) " → automatisch auf die Bank" else ""

            val addedLogs = mutableListOf<GameLogEntry>()
            var nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1

            if (twoMinIncreased) {
                addedLogs += GameLogEntry(
                    logId = nextLogId++,
                    gameId = game.gameId,
                    gameSecond = game.elapsedSeconds,
                    eventType = GameLogEventType.TWO_MINUTE_SUSPENSION,
                    primaryPlayerId = pg.playerId,
                    homeScoreAtEvent = game.homeTeamScore,
                    awayScoreAtEvent = game.awayTeamScore,
                    description = "2-Minuten-Strafe ($clampedTwoMin.) gegen $nrPrefix$pName$benchNote"
                )
            }
            if (yellowAdded) {
                addedLogs += GameLogEntry(
                    logId = nextLogId++,
                    gameId = game.gameId,
                    gameSecond = game.elapsedSeconds,
                    eventType = GameLogEventType.YELLOW_CARD,
                    primaryPlayerId = pg.playerId,
                    homeScoreAtEvent = game.homeTeamScore,
                    awayScoreAtEvent = game.awayTeamScore,
                    description = "Gelbe Karte gegen $nrPrefix$pName"
                )
            }
            if (redAdded) {
                addedLogs += GameLogEntry(
                    logId = nextLogId++,
                    gameId = game.gameId,
                    gameSecond = game.elapsedSeconds,
                    eventType = GameLogEventType.RED_CARD,
                    primaryPlayerId = pg.playerId,
                    homeScoreAtEvent = game.homeTeamScore,
                    awayScoreAtEvent = game.awayTeamScore,
                    description = "Rote Karte (Disqualifikation) gegen $nrPrefix$pName$benchNote"
                )
            }
            if (blueAdded) {
                addedLogs += GameLogEntry(
                    logId = nextLogId++,
                    gameId = game.gameId,
                    gameSecond = game.elapsedSeconds,
                    eventType = GameLogEventType.BLUE_CARD,
                    primaryPlayerId = pg.playerId,
                    homeScoreAtEvent = game.homeTeamScore,
                    awayScoreAtEvent = game.awayTeamScore,
                    description = "Blaue Karte (mit Bericht) gegen $nrPrefix$pName$benchNote"
                )
            }

            state.copy(
                playerGames = updatedPlayerGames,
                gameLogs = state.gameLogs + addedLogs
            )
        }
    }

    // --- FIELD_PLAYER_GAMESTATS (NUR FÜR AKTIVE SPIELER AUF DEM FELD + AUTO +/- & GAME LOG) ---
    fun updateFieldPlayerStats(
        playerGameId: Int,
        autoSyncTeamScore: Boolean = false,
        transform: (FieldPlayerGameStats) -> FieldPlayerGameStats
    ) {
        mutate { state ->
            val pg = state.playerGames.find { it.playerGameId == playerGameId } ?: return@mutate state
            // Bank-Spieler sind im inaktiven Zustand und können keine Spielstatistiken verbuchen
            if (!pg.isOnCourt) return@mutate state

            val game = state.games.find { it.gameId == pg.gameId }
            val player = state.players.find { it.playerId == pg.playerId }

            val existing = state.fieldPlayerStats.find { it.playerGameId == playerGameId }
            val currentStats = existing ?: FieldPlayerGameStats(
                fieldPlayerGamestatsId = (state.fieldPlayerStats.maxOfOrNull { it.fieldPlayerGamestatsId } ?: 0) + 1,
                playerGameId = playerGameId
            )
            val rawUpdated = transform(currentStats)
            // Stelle konsistente Werte sicher (z.B. Versuche >= Treffer, >= 0)
            val updated = rawUpdated.copy(
                goalsScored = rawUpdated.goalsScored.coerceAtLeast(0),
                goalesAttempted = maxOf(rawUpdated.goalesAttempted.coerceAtLeast(0), rawUpdated.goalsScored.coerceAtLeast(0)),
                penaltiesScored = rawUpdated.penaltiesScored.coerceAtLeast(0),
                penaltiesAttempted = maxOf(rawUpdated.penaltiesAttempted.coerceAtLeast(0), rawUpdated.penaltiesScored.coerceAtLeast(0)),
                penaltiesDrawn = rawUpdated.penaltiesDrawn.coerceAtLeast(0),
                assists = rawUpdated.assists.coerceAtLeast(0),
                steals = rawUpdated.steals.coerceAtLeast(0),
                blocks = rawUpdated.blocks.coerceAtLeast(0),
                technicalMistakes = rawUpdated.technicalMistakes.coerceAtLeast(0)
            )
            val goalDelta = updated.goalsScored - currentStats.goalsScored
            val penGoalDelta = updated.penaltiesScored - currentStats.penaltiesScored
            val shotDelta = updated.goalesAttempted - currentStats.goalesAttempted

            val newStatsList = if (existing != null) {
                state.fieldPlayerStats.map { if (it.playerGameId == playerGameId) updated else it }
            } else {
                state.fieldPlayerStats + updated
            }

            var newHomeScore = game?.homeTeamScore ?: 0
            var newAwayScore = game?.awayTeamScore ?: 0

            val updatedGames = if (autoSyncTeamScore && goalDelta != 0 && game != null && game.isOwnTeamGame) {
                if (game.isHomeGame) {
                    newHomeScore = (game.homeTeamScore + goalDelta).coerceAtLeast(0)
                } else {
                    newAwayScore = (game.awayTeamScore + goalDelta).coerceAtLeast(0)
                }
                state.games.map { g ->
                    if (g.gameId == game.gameId) {
                        g.copy(homeTeamScore = newHomeScore, awayTeamScore = newAwayScore)
                    } else g
                }
            } else state.games

            // Plus/Minus (+/-) für alle aktuell auf dem Feld stehenden Spieler automatisch aktualisieren
            val updatedPlayerGames = if (goalDelta != 0 && game != null) {
                state.playerGames.map { item ->
                    val role = state.players.find { it.playerId == item.playerId }?.role
                    if (item.gameId == game.gameId && item.isOnCourt && role != PlayerRole.COACH) {
                        item.copy(plusMinus = item.plusMinus + goalDelta)
                    } else item
                }
            } else state.playerGames

            val nrPrefix = player?.jerseyNumberHomeKit?.let { "#$it " } ?: ""
            val pName = player?.fullName ?: "Feldspieler"
            val nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1
            val updatedLogs = when {
                game != null && goalDelta > 0 -> {
                    val isSevenMeter = penGoalDelta > 0
                    state.gameLogs + GameLogEntry(
                        logId = nextLogId,
                        gameId = game.gameId,
                        gameSecond = game.elapsedSeconds,
                        eventType = if (isSevenMeter) GameLogEventType.SEVEN_METER_GOAL else GameLogEventType.GOAL,
                        primaryPlayerId = pg.playerId,
                        homeScoreAtEvent = newHomeScore,
                        awayScoreAtEvent = newAwayScore,
                        description = if (isSevenMeter) {
                            "7m-Tor durch $nrPrefix$pName (${updated.goalsScored}. Tor)"
                        } else {
                            "Tor durch $nrPrefix$pName (${updated.goalsScored}. Tor)"
                        }
                    )
                }
                game != null && goalDelta == 0 && shotDelta > 0 -> {
                    state.gameLogs + GameLogEntry(
                        logId = nextLogId,
                        gameId = game.gameId,
                        gameSecond = game.elapsedSeconds,
                        eventType = GameLogEventType.MISSED_SHOT,
                        primaryPlayerId = pg.playerId,
                        homeScoreAtEvent = newHomeScore,
                        awayScoreAtEvent = newAwayScore,
                        description = "Fehlwurf von $nrPrefix$pName"
                    )
                }
                else -> state.gameLogs
            }

            state.copy(
                fieldPlayerStats = newStatsList,
                games = updatedGames,
                playerGames = updatedPlayerGames,
                gameLogs = updatedLogs
            )
        }
    }

    // --- GOAL_KEEPER_GAMESTATS (NUR FÜR AKTIVE TORHÜTER AUF DEM FELD + AUTO +/- & GAME LOG) ---
    fun updateGoalKeeperStats(
        playerGameId: Int,
        autoSyncOpponentScore: Boolean = false,
        transform: (GoalKeeperGameStats) -> GoalKeeperGameStats
    ) {
        mutate { state ->
            val pg = state.playerGames.find { it.playerGameId == playerGameId } ?: return@mutate state
            if (!pg.isOnCourt) return@mutate state

            val game = state.games.find { it.gameId == pg.gameId }
            val keeper = state.players.find { it.playerId == pg.playerId }

            val existing = state.goalKeeperStats.find { it.playerGameId == playerGameId }
            val currentStats = existing ?: GoalKeeperGameStats(
                goalKeeperGamestatsId = (state.goalKeeperStats.maxOfOrNull { it.goalKeeperGamestatsId } ?: 0) + 1,
                playerGameId = playerGameId
            )
            val rawUpdated = transform(currentStats)
            val updated = rawUpdated.copy(
                saves = rawUpdated.saves.coerceAtLeast(0),
                goalesConceded = rawUpdated.goalesConceded.coerceAtLeast(0),
                penaltiesSaved = rawUpdated.penaltiesSaved.coerceAtLeast(0),
                penaltiesConceded = rawUpdated.penaltiesConceded.coerceAtLeast(0),
                sucessfulFastBreakPasses = rawUpdated.sucessfulFastBreakPasses.coerceAtLeast(0),
                attemptedFastBreakPasses = maxOf(
                    rawUpdated.attemptedFastBreakPasses.coerceAtLeast(0),
                    rawUpdated.sucessfulFastBreakPasses.coerceAtLeast(0)
                )
            )
            val concededDelta = updated.goalesConceded - currentStats.goalesConceded
            val savesDelta = updated.saves - currentStats.saves
            val penSavedDelta = updated.penaltiesSaved - currentStats.penaltiesSaved

            val newStatsList = if (existing != null) {
                state.goalKeeperStats.map { if (it.playerGameId == playerGameId) updated else it }
            } else {
                state.goalKeeperStats + updated
            }

            var newHomeScore = game?.homeTeamScore ?: 0
            var newAwayScore = game?.awayTeamScore ?: 0

            val updatedGames = if (autoSyncOpponentScore && concededDelta != 0 && game != null && game.isOwnTeamGame) {
                if (game.isHomeGame) {
                    newAwayScore = (game.awayTeamScore + concededDelta).coerceAtLeast(0)
                } else {
                    newHomeScore = (game.homeTeamScore + concededDelta).coerceAtLeast(0)
                }
                state.games.map { g ->
                    if (g.gameId == game.gameId) {
                        g.copy(homeTeamScore = newHomeScore, awayTeamScore = newAwayScore)
                    } else g
                }
            } else state.games

            // Bei Gegentor erhalten alle aktuell auf dem Feld stehenden Spieler -1 auf ihren Plus/Minus-Wert
            val updatedPlayerGames = if (concededDelta != 0 && game != null) {
                state.playerGames.map { item ->
                    val role = state.players.find { it.playerId == item.playerId }?.role
                    if (item.gameId == game.gameId && item.isOnCourt && role != PlayerRole.COACH) {
                        item.copy(plusMinus = item.plusMinus - concededDelta)
                    } else item
                }
            } else state.playerGames

            val nrPrefix = keeper?.jerseyNumberHomeKit?.let { "#$it " } ?: ""
            val kName = keeper?.fullName ?: "Torwart"
            val nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1
            val updatedLogs = when {
                game != null && savesDelta > 0 -> {
                    val detail = if (penSavedDelta > 0) "7m-Parade" else "Parade"
                    state.gameLogs + GameLogEntry(
                        logId = nextLogId,
                        gameId = game.gameId,
                        gameSecond = game.elapsedSeconds,
                        eventType = GameLogEventType.GOALKEEPER_SAVE,
                        primaryPlayerId = pg.playerId,
                        homeScoreAtEvent = newHomeScore,
                        awayScoreAtEvent = newAwayScore,
                        description = "$detail durch $nrPrefix$kName (${updated.saves}. Parade)"
                    )
                }
                game != null && concededDelta > 0 -> {
                    state.gameLogs + GameLogEntry(
                        logId = nextLogId,
                        gameId = game.gameId,
                        gameSecond = game.elapsedSeconds,
                        eventType = GameLogEventType.GOAL_CONCEDED,
                        primaryPlayerId = pg.playerId,
                        homeScoreAtEvent = newHomeScore,
                        awayScoreAtEvent = newAwayScore,
                        description = "Gegentor gegen $nrPrefix$kName"
                    )
                }
                else -> state.gameLogs
            }

            state.copy(
                goalKeeperStats = newStatsList,
                games = updatedGames,
                playerGames = updatedPlayerGames,
                gameLogs = updatedLogs
            )
        }
    }

    // --- COACH_GAMESTATS & TIMEOUT ---
    fun addCoachTimeout(playerGameId: Int, timeoutReason: String) {
        val reason = timeoutReason.trim().ifEmpty { "Team-Timeout (TTO)" }
        mutate { state ->
            val pg = state.playerGames.find { it.playerGameId == playerGameId }
            val game = state.games.find { it.gameId == pg?.gameId }
            val coach = state.players.find { it.playerId == pg?.playerId }

            val nextTimeoutId = (state.timeouts.maxOfOrNull { it.timeoutId } ?: 0) + 1
            val nextCoachStatId = (state.coachStats.maxOfOrNull { it.coachGamestatsId } ?: 0) + 1
            val timeout = Timeout(timeoutId = nextTimeoutId, timeoutReason = reason)
            val coachStat = CoachGameStats(
                coachGamestatsId = nextCoachStatId,
                playerGameId = playerGameId,
                timeoutId = nextTimeoutId
            )

            val updatedLogs = if (game != null) {
                val nextLogId = (state.gameLogs.maxOfOrNull { it.logId } ?: 0) + 1
                val cName = coach?.fullName ?: "Trainer"
                state.gameLogs + GameLogEntry(
                    logId = nextLogId,
                    gameId = game.gameId,
                    gameSecond = game.elapsedSeconds,
                    eventType = GameLogEventType.TIMEOUT,
                    primaryPlayerId = pg?.playerId,
                    homeScoreAtEvent = game.homeTeamScore,
                    awayScoreAtEvent = game.awayTeamScore,
                    description = "Auszeit (TTO – $cName): $reason"
                )
            } else state.gameLogs

            state.copy(
                timeouts = state.timeouts + timeout,
                coachStats = state.coachStats + coachStat,
                gameLogs = updatedLogs
            )
        }
    }

    fun removeCoachTimeout(coachGamestatsId: Int) {
        mutate { state ->
            val target = state.coachStats.find { it.coachGamestatsId == coachGamestatsId } ?: return@mutate state
            state.copy(
                coachStats = state.coachStats.filterNot { it.coachGamestatsId == coachGamestatsId },
                timeouts = state.timeouts.filterNot { it.timeoutId == target.timeoutId }
            )
        }
    }

    // --- TABELLEN-BERECHNUNG (LIGA-REPLIKA) ---
    fun computeStandings(seasonId: Int, state: DatabaseSnapshot = _snapshot.value): List<StandingsRow> {
        data class MutableStanding(
            val teamKey: String,
            val teamName: String,
            val location: String,
            val isOwnTeam: Boolean,
            var gamesPlayed: Int = 0,
            var wins: Int = 0,
            var draws: Int = 0,
            var losses: Int = 0,
            var goalsFor: Int = 0,
            var goalsAgainst: Int = 0,
            var pointsPlus: Int = 0,
            var pointsMinus: Int = 0
        )

        val map = linkedMapOf<String, MutableStanding>()
        val ownKey = "OWN_TEAM"
        map[ownKey] = MutableStanding(
            teamKey = ownKey,
            teamName = state.ownClubName,
            location = "Heimverein",
            isOwnTeam = true
        )

        val assignedOpponentIds = state.seasonOpponents
            .filter { it.seasonId == seasonId }
            .map { it.opponentTeamId }
            .toSet()

        val seasonGames = state.games.filter { it.seasonId == seasonId }
        val gameOpponentIds = seasonGames.flatMap { listOfNotNull(it.opponentId, it.secondOpponentId) }.toSet()
        val allOpponentIdsInSeason = assignedOpponentIds + gameOpponentIds

        allOpponentIdsInSeason.forEach { oppId ->
            val opp = state.opponentTeams.find { it.opponentTeamId == oppId } ?: return@forEach
            val key = "OPP_$oppId"
            map[key] = MutableStanding(
                teamKey = key,
                teamName = opp.opponentName,
                location = opp.opponentLocation,
                isOwnTeam = false
            )
        }

        seasonGames.forEach { game ->
            val isPlayed = game.homeTeamScore > 0 || game.awayTeamScore > 0 ||
                state.playerGames.any { it.gameId == game.gameId }
            if (!isPlayed) return@forEach

            val homeKey: String
            val awayKey: String
            if (game.secondOpponentId == null) {
                if (game.isHomeGame) {
                    homeKey = ownKey
                    awayKey = "OPP_${game.opponentId}"
                } else {
                    homeKey = "OPP_${game.opponentId}"
                    awayKey = ownKey
                }
            } else {
                homeKey = "OPP_${game.opponentId}"
                awayKey = "OPP_${game.secondOpponentId}"
            }

            val home = map[homeKey]
            val away = map[awayKey]

            if (home != null && away != null) {
                home.gamesPlayed++
                away.gamesPlayed++
                home.goalsFor += game.homeTeamScore
                home.goalsAgainst += game.awayTeamScore
                away.goalsFor += game.awayTeamScore
                away.goalsAgainst += game.homeTeamScore

                when {
                    game.homeTeamScore > game.awayTeamScore -> {
                        home.wins++
                        home.pointsPlus += 2
                        away.losses++
                        away.pointsMinus += 2
                    }
                    game.homeTeamScore < game.awayTeamScore -> {
                        away.wins++
                        away.pointsPlus += 2
                        home.losses++
                        home.pointsMinus += 2
                    }
                    else -> {
                        home.draws++
                        away.draws++
                        home.pointsPlus += 1
                        home.pointsMinus += 1
                        away.pointsPlus += 1
                        away.pointsMinus += 1
                    }
                }
            }
        }

        return map.values
            .sortedWith(
                compareByDescending<MutableStanding> { it.pointsPlus }
                    .thenBy { it.pointsMinus }
                    .thenByDescending { it.goalsFor - it.goalsAgainst }
                    .thenByDescending { it.goalsFor }
                    .thenBy { it.teamName }
            )
            .mapIndexed { index, item ->
                StandingsRow(
                    rank = index + 1,
                    teamName = item.teamName,
                    location = item.location,
                    isOwnTeam = item.isOwnTeam,
                    gamesPlayed = item.gamesPlayed,
                    wins = item.wins,
                    draws = item.draws,
                    losses = item.losses,
                    goalsFor = item.goalsFor,
                    goalsAgainst = item.goalsAgainst,
                    pointsPlus = item.pointsPlus,
                    pointsMinus = item.pointsMinus
                )
            }
    }

    // --- SPIELERPROFIL & SPIEL-HISTORIE ---
    fun getPlayerGameHistory(
        playerId: Int,
        seasonIdFilter: Int? = null,
        state: DatabaseSnapshot = _snapshot.value
    ): List<PlayerGameHistoryEntry> {
        val pGames = state.playerGames.filter { it.playerId == playerId }
        return pGames.mapNotNull { pg ->
            val game = state.games.find { it.gameId == pg.gameId } ?: return@mapNotNull null
            if (seasonIdFilter != null && game.seasonId != seasonIdFilter) return@mapNotNull null

            val season = state.seasons.find { it.seasonId == game.seasonId }
            val opponent = state.opponentTeams.find { it.opponentTeamId == game.opponentId }
            val oppName = opponent?.opponentName ?: "Gegner"
            val matchupTitle = if (game.isHomeGame) {
                "${state.ownClubName} vs. $oppName"
            } else {
                "$oppName vs. ${state.ownClubName}"
            }

            val ownGoals = if (game.isHomeGame) game.homeTeamScore else game.awayTeamScore
            val oppGoals = if (game.isHomeGame) game.awayTeamScore else game.homeTeamScore
            val outcomeBadge = when {
                ownGoals > oppGoals -> "S"
                ownGoals < oppGoals -> "N"
                else -> "U"
            }

            val fieldStats = state.fieldPlayerStats.find { it.playerGameId == pg.playerGameId }
            val keeperStats = state.goalKeeperStats.find { it.playerGameId == pg.playerGameId }
            val timeoutIds = state.coachStats
                .filter { it.playerGameId == pg.playerGameId }
                .map { it.timeoutId }
                .toSet()
            val timeouts = state.timeouts.filter { it.timeoutId in timeoutIds }

            PlayerGameHistoryEntry(
                game = game,
                seasonName = season?.division ?: "Saison",
                matchupTitle = matchupTitle,
                resultFormatted = "${game.homeTeamScore}:${game.awayTeamScore} (${game.homeTeamHalftimeScore}:${game.awayTeamHalftimeScore})",
                outcomeBadge = outcomeBadge,
                playerGame = pg,
                fieldStats = fieldStats,
                keeperStats = keeperStats,
                coachTimeouts = timeouts
            )
        }.sortedByDescending { it.game.gameDate }
    }

    fun computePlayerAggregatedStats(
        playerId: Int,
        seasonIdFilter: Int? = null,
        state: DatabaseSnapshot = _snapshot.value
    ): PlayerAggregatedStats {
        val history = getPlayerGameHistory(playerId, seasonIdFilter, state)
        if (history.isEmpty()) return PlayerAggregatedStats()

        var twoMin = 0
        var yellow = 0
        var red = 0
        var blue = 0
        var plusMinus = 0
        var secondsPlayed = 0

        var goals = 0
        var shots = 0
        var penGoals = 0
        var penAttempts = 0
        var penDrawn = 0
        var assists = 0
        var steals = 0
        var blocks = 0
        var mistakes = 0

        var saves = 0
        var conceded = 0
        var penSaved = 0
        var penConceded = 0
        var fbSuccess = 0
        var fbAttempts = 0

        var timeoutsCount = 0

        history.forEach { entry ->
            twoMin += entry.playerGame.twoMinuteSuspensions
            if (entry.playerGame.yellowCard) yellow++
            if (entry.playerGame.redCard) red++
            if (entry.playerGame.blueCard) blue++
            plusMinus += entry.playerGame.plusMinus
            secondsPlayed += entry.playerGame.secondsPlayed

            entry.fieldStats?.let { fs ->
                goals += fs.goalsScored
                shots += fs.goalesAttempted
                penGoals += fs.penaltiesScored
                penAttempts += fs.penaltiesAttempted
                penDrawn += fs.penaltiesDrawn
                assists += fs.assists
                steals += fs.steals
                blocks += fs.blocks
                mistakes += fs.technicalMistakes
            }

            entry.keeperStats?.let { ks ->
                saves += ks.saves
                conceded += ks.goalesConceded
                penSaved += ks.penaltiesSaved
                penConceded += ks.penaltiesConceded
                fbSuccess += ks.sucessfulFastBreakPasses
                fbAttempts += ks.attemptedFastBreakPasses
            }

            timeoutsCount += entry.coachTimeouts.size
        }

        return PlayerAggregatedStats(
            gamesAttended = history.size,
            totalTwoMin = twoMin,
            yellowCards = yellow,
            redCards = red,
            blueCards = blue,
            totalPlusMinus = plusMinus,
            totalSecondsPlayed = secondsPlayed,
            totalGoals = goals,
            totalShots = shots,
            totalPenaltyGoals = penGoals,
            totalPenaltyAttempts = penAttempts,
            totalPenaltiesDrawn = penDrawn,
            totalAssists = assists,
            totalSteals = steals,
            totalBlocks = blocks,
            totalTechnicalMistakes = mistakes,
            totalSaves = saves,
            totalGoalsConceded = conceded,
            totalPenaltiesSaved = penSaved,
            totalPenaltiesConceded = penConceded,
            totalFastBreakPassesSuccess = fbSuccess,
            totalFastBreakPassesAttempted = fbAttempts,
            totalTimeoutsTaken = timeoutsCount
        )
    }

    // --- PERFORMANCE-INDEX, GRAPHEN, POSITIONSVERGLEICH & TRAINER-RANKING ---

    private fun roundToOneDecimal(value: Double): Double =
        kotlin.math.round(value * 10.0) / 10.0

    /**
     * Berechnet einen objektiven Handball-Performance-Index (0..100 Punkte) auf Basis der aggregierten Werte
     * inklusive Plus/Minus (+/-) auf dem Feld.
     */
    fun computePerformanceScore(role: PlayerRole, stats: PlayerAggregatedStats): Double {
        if (stats.gamesAttended == 0) return 0.0
        val plusMinusImpact = (stats.plusMinusPerGame * 1.1).coerceIn(-8.0, 10.0)
        val raw = when (role) {
            PlayerRole.FIELD_PLAYER -> {
                val offense = (stats.goalsPerGame * 4.8) +
                    (stats.shotAccuracyPercent * 0.30) +
                    (stats.assistsPerGame * 3.8) +
                    (stats.penaltiesDrawnPerGame * 2.5)
                val defense = stats.defensiveActionsPerGame * 4.5
                val penalties = (stats.technicalMistakesPerGame * 3.5) +
                    (stats.twoMinPerGame * 2.2) +
                    ((stats.redCards + stats.blueCards) * 6.0 / stats.gamesAttended)
                18.0 + offense + defense + plusMinusImpact - penalties
            }
            PlayerRole.GOALKEEPER -> {
                val keeping = (stats.savePercentage * 0.95) +
                    (stats.savesPerGame * 2.2) +
                    (stats.penaltySavePercentage * 0.20) +
                    (stats.fastBreakPassPercentage * 0.15)
                val penalties = (stats.twoMinPerGame * 2.5) +
                    ((stats.redCards + stats.blueCards) * 6.0 / stats.gamesAttended)
                keeping + (plusMinusImpact * 0.6) - penalties
            }
            PlayerRole.COACH -> {
                50.0 + (stats.totalTimeoutsTaken.toDouble() / stats.gamesAttended) * 10.0 - (stats.twoMinPerGame * 4.0)
            }
        }
        return roundToOneDecimal(raw.coerceIn(0.0, 100.0))
    }

    /**
     * Berechnet den Performance-Index (0..100) für ein einzelnes Spiel.
     */
    fun computeSingleGamePerformanceScore(role: PlayerRole, entry: PlayerGameHistoryEntry): Double {
        val singleAgg = when (role) {
            PlayerRole.FIELD_PLAYER -> {
                val fs = entry.fieldStats ?: FieldPlayerGameStats(0, entry.playerGame.playerGameId)
                PlayerAggregatedStats(
                    gamesAttended = 1,
                    totalTwoMin = entry.playerGame.twoMinuteSuspensions,
                    yellowCards = if (entry.playerGame.yellowCard) 1 else 0,
                    redCards = if (entry.playerGame.redCard) 1 else 0,
                    blueCards = if (entry.playerGame.blueCard) 1 else 0,
                    totalPlusMinus = entry.playerGame.plusMinus,
                    totalSecondsPlayed = entry.playerGame.secondsPlayed,
                    totalGoals = fs.goalsScored,
                    totalShots = fs.goalesAttempted,
                    totalPenaltyGoals = fs.penaltiesScored,
                    totalPenaltyAttempts = fs.penaltiesAttempted,
                    totalPenaltiesDrawn = fs.penaltiesDrawn,
                    totalAssists = fs.assists,
                    totalSteals = fs.steals,
                    totalBlocks = fs.blocks,
                    totalTechnicalMistakes = fs.technicalMistakes
                )
            }
            PlayerRole.GOALKEEPER -> {
                val ks = entry.keeperStats ?: GoalKeeperGameStats(0, entry.playerGame.playerGameId)
                PlayerAggregatedStats(
                    gamesAttended = 1,
                    totalTwoMin = entry.playerGame.twoMinuteSuspensions,
                    yellowCards = if (entry.playerGame.yellowCard) 1 else 0,
                    redCards = if (entry.playerGame.redCard) 1 else 0,
                    blueCards = if (entry.playerGame.blueCard) 1 else 0,
                    totalPlusMinus = entry.playerGame.plusMinus,
                    totalSecondsPlayed = entry.playerGame.secondsPlayed,
                    totalSaves = ks.saves,
                    totalGoalsConceded = ks.goalesConceded,
                    totalPenaltiesSaved = ks.penaltiesSaved,
                    totalPenaltiesConceded = ks.penaltiesConceded,
                    totalFastBreakPassesSuccess = ks.sucessfulFastBreakPasses,
                    totalFastBreakPassesAttempted = ks.attemptedFastBreakPasses
                )
            }
            PlayerRole.COACH -> {
                PlayerAggregatedStats(
                    gamesAttended = 1,
                    totalTwoMin = entry.playerGame.twoMinuteSuspensions,
                    yellowCards = if (entry.playerGame.yellowCard) 1 else 0,
                    redCards = if (entry.playerGame.redCard) 1 else 0,
                    blueCards = if (entry.playerGame.blueCard) 1 else 0,
                    totalTimeoutsTaken = entry.coachTimeouts.size
                )
            }
        }
        return computePerformanceScore(role, singleAgg)
    }

    /**
     * Liefert die chronologische Zeitreihe (ältestes bis neuestes Spiel) für die Performance-Graphen im Spielerprofil.
     */
    fun getPlayerPerformanceSeries(
        playerId: Int,
        seasonIdFilter: Int? = null,
        state: DatabaseSnapshot = _snapshot.value
    ): List<PlayerGamePerformancePoint> {
        val player = state.players.find { it.playerId == playerId } ?: return emptyList()
        val chronological = getPlayerGameHistory(playerId, seasonIdFilter, state)
            .sortedWith(compareBy<PlayerGameHistoryEntry> { it.game.gameDate }.thenBy { it.game.gameId })

        return chronological.mapIndexed { idx, entry ->
            val opp = state.opponentTeams.find { it.opponentTeamId == entry.game.opponentId }
            val oppShort = opp?.opponentName?.split(" ")?.firstOrNull() ?: "Sp.${idx + 1}"
            val dateShort = if (entry.game.gameDate.length >= 10) {
                "${entry.game.gameDate.substring(8, 10)}.${entry.game.gameDate.substring(5, 7)}."
            } else {
                entry.game.gameDate
            }

            val fs = entry.fieldStats
            val ks = entry.keeperStats
            val shots = fs?.goalesAttempted ?: 0
            val goals = fs?.goalsScored ?: 0
            val shotAcc = if (shots > 0) ((goals * 100.0) / shots).toInt() else 0

            val saves = ks?.saves ?: 0
            val conceded = ks?.goalesConceded ?: 0
            val savePct = if (saves + conceded > 0) ((saves * 100.0) / (saves + conceded)).toInt() else 0
            val fbSucc = ks?.sucessfulFastBreakPasses ?: 0
            val fbAtt = ks?.attemptedFastBreakPasses ?: 0
            val fbPct = if (fbAtt > 0) ((fbSucc * 100.0) / fbAtt).toInt() else 0

            PlayerGamePerformancePoint(
                gameId = entry.game.gameId,
                gameDate = entry.game.gameDate,
                shortLabel = "$dateShort $oppShort",
                matchupTitle = entry.matchupTitle,
                outcomeBadge = entry.outcomeBadge,
                performanceScore = computeSingleGamePerformanceScore(player.role, entry),
                plusMinus = entry.playerGame.plusMinus,
                minutesPlayed = roundToOneDecimal(entry.playerGame.secondsPlayed / 60.0),
                goals = goals,
                shots = shots,
                shotAccuracyPercent = shotAcc,
                assists = fs?.assists ?: 0,
                steals = fs?.steals ?: 0,
                blocks = fs?.blocks ?: 0,
                penaltiesDrawn = fs?.penaltiesDrawn ?: 0,
                technicalMistakes = fs?.technicalMistakes ?: 0,
                saves = saves,
                goalsConceded = conceded,
                savePercentage = savePct,
                penaltiesSaved = ks?.penaltiesSaved ?: 0,
                fastBreakPassesSuccess = fbSucc,
                fastBreakPassPercentage = fbPct,
                twoMinuteSuspensions = entry.playerGame.twoMinuteSuspensions
            )
        }
    }

    /**
     * Vergleicht jeden Einzelwert eines Spielers mit allen anderen Spielern auf derselben Position.
     */
    fun computePositionMetricComparisons(
        playerId: Int,
        seasonIdFilter: Int? = null,
        state: DatabaseSnapshot = _snapshot.value
    ): List<PositionMetricComparison> {
        val player = state.players.find { it.playerId == playerId } ?: return emptyList()
        if (player.role == PlayerRole.COACH) return emptyList()

        val peers = state.players.filter {
            it.role == player.role && (player.positionId == null || it.positionId == player.positionId)
        }.ifEmpty { listOf(player) }

        val peerStats = peers.associateWith { p ->
            computePlayerAggregatedStats(p.playerId, seasonIdFilter, state)
        }

        data class MetricSpec(
            val key: String,
            val label: String,
            val unit: String,
            val higherIsBetter: Boolean,
            val extractor: (Player, PlayerAggregatedStats) -> Double
        )

        val specs = if (player.role == PlayerRole.FIELD_PLAYER) {
            listOf(
                MetricSpec("perf_index", "Performance-Index", "Pkt", true) { p, s ->
                    computePerformanceScore(p.role, s)
                },
                MetricSpec("plus_minus_pg", "Plus/Minus (+/-) pro Spiel", "", true) { _, s ->
                    roundToOneDecimal(s.plusMinusPerGame)
                },
                MetricSpec("minutes_pg", "Einsatzzeit pro Spiel", "Min", true) { _, s ->
                    roundToOneDecimal(s.minutesPerGame)
                },
                MetricSpec("goals_pg", "Tore pro Spiel", "", true) { _, s ->
                    roundToOneDecimal(s.goalsPerGame)
                },
                MetricSpec("shot_acc", "Wurfquote", "%", true) { _, s ->
                    s.shotAccuracyPercent.toDouble()
                },
                MetricSpec("assists_pg", "Assists pro Spiel", "", true) { _, s ->
                    roundToOneDecimal(s.assistsPerGame)
                },
                MetricSpec("defense_pg", "Steals & Blocks pro Spiel", "", true) { _, s ->
                    roundToOneDecimal(s.defensiveActionsPerGame)
                },
                MetricSpec("pen_drawn_pg", "Gezogene 7m pro Spiel", "", true) { _, s ->
                    roundToOneDecimal(s.penaltiesDrawnPerGame)
                },
                MetricSpec("pen_acc", "7-Meter-Quote", "%", true) { _, s ->
                    s.penaltyAccuracyPercent.toDouble()
                },
                MetricSpec("mistakes_pg", "Technische Fehler pro Spiel", "", false) { _, s ->
                    roundToOneDecimal(s.technicalMistakesPerGame)
                },
                MetricSpec("twomin_pg", "2-Minuten-Strafen pro Spiel", "", false) { _, s ->
                    roundToOneDecimal(s.twoMinPerGame)
                }
            )
        } else {
            listOf(
                MetricSpec("perf_index", "Performance-Index", "Pkt", true) { p, s ->
                    computePerformanceScore(p.role, s)
                },
                MetricSpec("plus_minus_pg", "Plus/Minus (+/-) pro Spiel", "", true) { _, s ->
                    roundToOneDecimal(s.plusMinusPerGame)
                },
                MetricSpec("minutes_pg", "Einsatzzeit pro Spiel", "Min", true) { _, s ->
                    roundToOneDecimal(s.minutesPerGame)
                },
                MetricSpec("saves_pg", "Paraden pro Spiel", "", true) { _, s ->
                    roundToOneDecimal(s.savesPerGame)
                },
                MetricSpec("save_pct", "Fangquote", "%", true) { _, s ->
                    s.savePercentage.toDouble()
                },
                MetricSpec("pen_save_pct", "7-Meter-Fangquote", "%", true) { _, s ->
                    s.penaltySavePercentage.toDouble()
                },
                MetricSpec("fb_pass_pct", "Gegenstoß-Passquote", "%", true) { _, s ->
                    s.fastBreakPassPercentage.toDouble()
                },
                MetricSpec("conceded_pg", "Gegentore pro Spiel", "", false) { _, s ->
                    roundToOneDecimal(s.goalsConcededPerGame)
                },
                MetricSpec("twomin_pg", "2-Minuten-Strafen pro Spiel", "", false) { _, s ->
                    roundToOneDecimal(s.twoMinPerGame)
                }
            )
        }

        return specs.map { spec ->
            val valuesByPlayer = peers.map { p ->
                val st = peerStats[p] ?: PlayerAggregatedStats()
                p to spec.extractor(p, st)
            }
            val sorted = if (spec.higherIsBetter) {
                valuesByPlayer.sortedWith(compareByDescending<Pair<Player, Double>> { it.second }.thenBy { it.first.lastName })
            } else {
                valuesByPlayer.sortedWith(compareBy<Pair<Player, Double>> { it.second }.thenBy { it.first.lastName })
            }

            val myVal = valuesByPlayer.firstOrNull { it.first.playerId == player.playerId }?.second ?: 0.0
            val avgVal = roundToOneDecimal(valuesByPlayer.map { it.second }.average())
            val bestPair = sorted.first()
            val myRank = sorted.indexOfFirst { it.first.playerId == player.playerId }.let { if (it >= 0) it + 1 else 1 }

            PositionMetricComparison(
                metricKey = spec.key,
                metricLabel = spec.label,
                unit = spec.unit,
                higherIsBetter = spec.higherIsBetter,
                playerValue = myVal,
                positionAverage = avgVal,
                positionBest = bestPair.second,
                bestPlayerName = bestPair.first.fullName,
                rankInPosition = myRank,
                totalPlayersInPosition = peers.size
            )
        }
    }

    /**
     * Erzeugt die 6 normalisierten Achsen (0..1) für das Spinnennetz-/Radar-Diagramm (Spieler vs. Positions-Durchschnitt).
     */
    fun computeRadarAxesForPlayer(
        playerId: Int,
        seasonIdFilter: Int? = null,
        state: DatabaseSnapshot = _snapshot.value
    ): List<RadarMetricAxis> {
        val comparisons = computePositionMetricComparisons(playerId, seasonIdFilter, state)
        if (comparisons.isEmpty()) return emptyList()
        val byKey = comparisons.associateBy { it.metricKey }
        val player = state.players.find { it.playerId == playerId } ?: return emptyList()

        fun normHigher(value: Double, maxScale: Double): Float =
            (value / maxScale).coerceIn(0.08, 1.0).toFloat()

        fun normLower(value: Double, worstScale: Double): Float =
            (1.0 - (value / worstScale).coerceIn(0.0, 0.92)).toFloat()

        return if (player.role == PlayerRole.FIELD_PLAYER) {
            val goals = byKey["goals_pg"]
            val acc = byKey["shot_acc"]
            val assists = byKey["assists_pg"]
            val defense = byKey["defense_pg"]
            val plusMinus = byKey["plus_minus_pg"]
            val mistakes = byKey["mistakes_pg"]

            val maxGoals = maxOf(10.0, (goals?.positionBest ?: 8.0) * 1.1)
            val maxAssists = maxOf(8.0, (assists?.positionBest ?: 6.0) * 1.1)
            val maxDefense = maxOf(5.0, (defense?.positionBest ?: 4.0) * 1.1)

            fun normPlusMinus(v: Double): Float =
                ((v + 6.0) / 14.0).coerceIn(0.08, 1.0).toFloat()

            val pmPlayer = plusMinus?.playerValue ?: 0.0
            val pmAvg = plusMinus?.positionAverage ?: 0.0

            listOf(
                RadarMetricAxis(
                    label = "Tore/Sp.",
                    playerNormalized = normHigher(goals?.playerValue ?: 0.0, maxGoals),
                    positionAvgNormalized = normHigher(goals?.positionAverage ?: 0.0, maxGoals),
                    playerFormatted = "${goals?.playerValue ?: 0.0}",
                    avgFormatted = "${goals?.positionAverage ?: 0.0}"
                ),
                RadarMetricAxis(
                    label = "Wurfquote",
                    playerNormalized = normHigher(acc?.playerValue ?: 0.0, 100.0),
                    positionAvgNormalized = normHigher(acc?.positionAverage ?: 0.0, 100.0),
                    playerFormatted = "${acc?.playerValue?.toInt() ?: 0}%",
                    avgFormatted = "${acc?.positionAverage?.toInt() ?: 0}%"
                ),
                RadarMetricAxis(
                    label = "Assists",
                    playerNormalized = normHigher(assists?.playerValue ?: 0.0, maxAssists),
                    positionAvgNormalized = normHigher(assists?.positionAverage ?: 0.0, maxAssists),
                    playerFormatted = "${assists?.playerValue ?: 0.0}",
                    avgFormatted = "${assists?.positionAverage ?: 0.0}"
                ),
                RadarMetricAxis(
                    label = "Abwehr",
                    playerNormalized = normHigher(defense?.playerValue ?: 0.0, maxDefense),
                    positionAvgNormalized = normHigher(defense?.positionAverage ?: 0.0, maxDefense),
                    playerFormatted = "${defense?.playerValue ?: 0.0}",
                    avgFormatted = "${defense?.positionAverage ?: 0.0}"
                ),
                RadarMetricAxis(
                    label = "Plus/Minus",
                    playerNormalized = normPlusMinus(pmPlayer),
                    positionAvgNormalized = normPlusMinus(pmAvg),
                    playerFormatted = if (pmPlayer > 0) "+$pmPlayer" else "$pmPlayer",
                    avgFormatted = if (pmAvg > 0) "+$pmAvg" else "$pmAvg"
                ),
                RadarMetricAxis(
                    label = "Ballkontrolle",
                    playerNormalized = normLower(mistakes?.playerValue ?: 0.0, 4.0),
                    positionAvgNormalized = normLower(mistakes?.positionAverage ?: 0.0, 4.0),
                    playerFormatted = "${mistakes?.playerValue ?: 0.0} TF",
                    avgFormatted = "${mistakes?.positionAverage ?: 0.0} TF"
                )
            )
        } else {
            val saves = byKey["saves_pg"]
            val savePct = byKey["save_pct"]
            val penPct = byKey["pen_save_pct"]
            val fbPct = byKey["fb_pass_pct"]
            val conceded = byKey["conceded_pg"]
            val perf = byKey["perf_index"]

            val maxSaves = maxOf(16.0, (saves?.positionBest ?: 14.0) * 1.1)

            listOf(
                RadarMetricAxis(
                    label = "Paraden/Sp.",
                    playerNormalized = normHigher(saves?.playerValue ?: 0.0, maxSaves),
                    positionAvgNormalized = normHigher(saves?.positionAverage ?: 0.0, maxSaves),
                    playerFormatted = "${saves?.playerValue ?: 0.0}",
                    avgFormatted = "${saves?.positionAverage ?: 0.0}"
                ),
                RadarMetricAxis(
                    label = "Fangquote",
                    playerNormalized = normHigher(savePct?.playerValue ?: 0.0, 60.0),
                    positionAvgNormalized = normHigher(savePct?.positionAverage ?: 0.0, 60.0),
                    playerFormatted = "${savePct?.playerValue?.toInt() ?: 0}%",
                    avgFormatted = "${savePct?.positionAverage?.toInt() ?: 0}%"
                ),
                RadarMetricAxis(
                    label = "7m-Abwehr",
                    playerNormalized = normHigher(penPct?.playerValue ?: 0.0, 60.0),
                    positionAvgNormalized = normHigher(penPct?.positionAverage ?: 0.0, 60.0),
                    playerFormatted = "${penPct?.playerValue?.toInt() ?: 0}%",
                    avgFormatted = "${penPct?.positionAverage?.toInt() ?: 0}%"
                ),
                RadarMetricAxis(
                    label = "Gegenstoß",
                    playerNormalized = normHigher(fbPct?.playerValue ?: 0.0, 100.0),
                    positionAvgNormalized = normHigher(fbPct?.positionAverage ?: 0.0, 100.0),
                    playerFormatted = "${fbPct?.playerValue?.toInt() ?: 0}%",
                    avgFormatted = "${fbPct?.positionAverage?.toInt() ?: 0}%"
                ),
                RadarMetricAxis(
                    label = "Gegentor-Schutz",
                    playerNormalized = normLower(conceded?.playerValue ?: 0.0, 35.0),
                    positionAvgNormalized = normLower(conceded?.positionAverage ?: 0.0, 35.0),
                    playerFormatted = "${conceded?.playerValue ?: 0.0} GT",
                    avgFormatted = "${conceded?.positionAverage ?: 0.0} GT"
                ),
                RadarMetricAxis(
                    label = "Gesamt-Index",
                    playerNormalized = normHigher(perf?.playerValue ?: 0.0, 100.0),
                    positionAvgNormalized = normHigher(perf?.positionAverage ?: 0.0, 100.0),
                    playerFormatted = "${perf?.playerValue ?: 0.0}",
                    avgFormatted = "${perf?.positionAverage ?: 0.0}"
                )
            )
        }
    }

    /**
     * Berechnet das Spieler-Ranking für Trainer (optional gefiltert nach Position, Rolle und Saison,
     * sowie sortierbar nach Gesamt-Performance, Plus/Minus, Einsatzzeit oder Einzelkriterien).
     */
    fun computePlayerRankings(
        positionIdFilter: Int? = null,
        roleFilter: PlayerRole? = null,
        seasonIdFilter: Int? = null,
        sortCriterion: RankingSortCriterion = RankingSortCriterion.PERFORMANCE_INDEX,
        state: DatabaseSnapshot = _snapshot.value
    ): List<PlayerRankingEntry> {
        val candidates = state.players.filter { p ->
            p.role != PlayerRole.COACH &&
                (positionIdFilter == null || p.positionId == positionIdFilter) &&
                (roleFilter == null || p.role == roleFilter)
        }

        val rawEntries = candidates.map { player ->
            val stats = computePlayerAggregatedStats(player.playerId, seasonIdFilter, state)
            val perfScore = computePerformanceScore(player.role, stats)
            val posName = state.positions.find { it.positionId == player.positionId }?.positionName ?: player.role.label
            PlayerRankingEntry(
                rank = 0,
                player = player,
                positionName = posName,
                stats = stats,
                performanceScore = perfScore
            )
        }

        val sorted = when (sortCriterion) {
            RankingSortCriterion.PERFORMANCE_INDEX -> {
                rawEntries.sortedWith(
                    compareByDescending<PlayerRankingEntry> { it.performanceScore }
                        .thenByDescending { it.stats.totalGoals + it.stats.totalSaves }
                        .thenBy { it.player.lastName }
                )
            }
            RankingSortCriterion.PLUS_MINUS -> {
                rawEntries.sortedWith(
                    compareByDescending<PlayerRankingEntry> { it.stats.plusMinusPerGame }
                        .thenByDescending { it.stats.totalPlusMinus }
                        .thenByDescending { it.performanceScore }
                )
            }
            RankingSortCriterion.PLAYING_TIME -> {
                rawEntries.sortedWith(
                    compareByDescending<PlayerRankingEntry> { it.stats.minutesPerGame }
                        .thenByDescending { it.stats.totalSecondsPlayed }
                        .thenByDescending { it.performanceScore }
                )
            }
            RankingSortCriterion.GOALS_OR_SAVES -> {
                rawEntries.sortedWith(
                    compareByDescending<PlayerRankingEntry> {
                        if (it.player.role == PlayerRole.GOALKEEPER) it.stats.savesPerGame else it.stats.goalsPerGame
                    }.thenByDescending { it.performanceScore }
                )
            }
            RankingSortCriterion.ACCURACY_OR_SAVE_RATE -> {
                rawEntries.sortedWith(
                    compareByDescending<PlayerRankingEntry> {
                        if (it.player.role == PlayerRole.GOALKEEPER) it.stats.savePercentage else it.stats.shotAccuracyPercent
                    }.thenByDescending { it.performanceScore }
                )
            }
            RankingSortCriterion.ASSISTS_OR_FASTBREAK -> {
                rawEntries.sortedWith(
                    compareByDescending<PlayerRankingEntry> {
                        if (it.player.role == PlayerRole.GOALKEEPER) it.stats.fastBreakPassPercentage.toDouble() else it.stats.assistsPerGame
                    }.thenByDescending { it.performanceScore }
                )
            }
            RankingSortCriterion.DEFENSE -> {
                rawEntries.sortedWith(
                    compareByDescending<PlayerRankingEntry> {
                        if (it.player.role == PlayerRole.GOALKEEPER) it.stats.penaltySavePercentage.toDouble() else it.stats.defensiveActionsPerGame
                    }.thenByDescending { it.performanceScore }
                )
            }
            RankingSortCriterion.DISCIPLINE -> {
                rawEntries.sortedWith(
                    compareBy<PlayerRankingEntry> { it.stats.technicalMistakesPerGame + it.stats.twoMinPerGame }
                        .thenByDescending { it.performanceScore }
                )
            }
        }

        return sorted.mapIndexed { idx, entry -> entry.copy(rank = idx + 1) }
    }

    fun formatGameMatchup(game: Game, state: DatabaseSnapshot = _snapshot.value): String {
        val opp1 = state.opponentTeams.find { it.opponentTeamId == game.opponentId }?.opponentName ?: "Gegner"
        return if (game.secondOpponentId == null) {
            if (game.isHomeGame) {
                "${state.ownClubName} vs. $opp1"
            } else {
                "$opp1 vs. ${state.ownClubName}"
            }
        } else {
            val opp2 = state.opponentTeams.find { it.opponentTeamId == game.secondOpponentId }?.opponentName ?: "Gegner 2"
            "$opp1 vs. $opp2"
        }
    }

    // --- OFFLINE-FIRST BACKEND SYNC (Supabase & lokaler Ktor-Server) ---
    suspend fun syncWithBackend(
        serverBaseUrl: String = SupabaseConfig.resolvedProjectUrl().ifBlank { "http://localhost:8080" },
        supabaseAnonKey: String = SupabaseConfig.resolvedApiKey()
    ) {
        val cleanAnonKey = supabaseAnonKey.trim().ifBlank { SupabaseConfig.resolvedApiKey() }
        val normalizedUrl = serverBaseUrl.trim().trimEnd('/').ifBlank {
            SupabaseConfig.resolvedProjectUrl().ifBlank { "http://localhost:8080" }
        }
        val isSupabase = cleanAnonKey.isNotEmpty() || normalizedUrl.contains(".supabase.co", ignoreCase = true)

        if (isSupabase && cleanAnonKey.isEmpty()) {
            _syncStatus.value = "Bitte SUPABASE_ANON_KEY / SUPABASE_PUBLIC_KEY in .env oder im Feld eintragen."
            return
        }

        _syncStatus.value = "Synchronisiere mit $normalizedUrl ..."
        try {
            val response: DatabaseSnapshot = if (isSupabase) {
                SupabaseSyncService.syncSnapshotWithSupabase(
                    httpClient = httpClient,
                    baseUrl = normalizedUrl,
                    apiKey = cleanAnonKey,
                    localSnapshot = _snapshot.value
                )
            } else {
                httpClient.post("$normalizedUrl/api/sync") {
                    contentType(ContentType.Application.Json)
                    setBody(_snapshot.value)
                }.body()
            }
            _snapshot.value = response
            PlatformStorage.saveSnapshot(response)
            _syncStatus.value = "Sync erfolgreich mit $normalizedUrl (${response.games.size} Spiele, ${response.players.size} Kader)"
        } catch (e: Exception) {
            _syncStatus.value = "Offline-Modus aktiv (Server unter $normalizedUrl nicht erreichbar – lokal gespeichert)"
        }
    }
}
