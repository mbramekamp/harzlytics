package com.handball.stattracker.repository

import com.handball.stattracker.model.*
import com.handball.stattracker.storage.PlatformStorage
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

class HandballRepository {

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
            return loaded
        }
        val seeded = DemoDataSeed.createDemoSnapshot()
        PlatformStorage.saveSnapshot(seeded)
        return seeded
    }

    private fun mutate(block: (DatabaseSnapshot) -> DatabaseSnapshot) {
        val now = Clock.System.now().toEpochMilliseconds()
        val updated = block(_snapshot.value).copy(lastUpdatedEpochMillis = now)
        _snapshot.value = updated
        PlatformStorage.saveSnapshot(updated)
    }

    fun resetToDemoData() {
        val demo = DemoDataSeed.createDemoSnapshot().copy(
            lastUpdatedEpochMillis = Clock.System.now().toEpochMilliseconds()
        )
        _snapshot.value = demo
        PlatformStorage.saveSnapshot(demo)
        _syncStatus.value = "Demo-Daten erfolgreich wiederhergestellt"
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
                coachStats = state.coachStats.filterNot { it.playerGameId in removedPgIds }
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
            awayTeamScore = awayScore
        )

        var nextPgId = (state.playerGames.maxOfOrNull { it.playerGameId } ?: 0) + 1
        var nextFieldStatId = (state.fieldPlayerStats.maxOfOrNull { it.fieldPlayerGamestatsId } ?: 0) + 1
        var nextKeeperStatId = (state.goalKeeperStats.maxOfOrNull { it.goalKeeperGamestatsId } ?: 0) + 1

        val newPlayerGames = mutableListOf<PlayerGame>()
        val newFieldStats = mutableListOf<FieldPlayerGameStats>()
        val newKeeperStats = mutableListOf<GoalKeeperGameStats>()

        if (secondOpponentId == null) {
            nominatedPlayerIds.forEach { pid ->
                val player = state.players.find { it.playerId == pid } ?: return@forEach
                val pgId = nextPgId++
                newPlayerGames += PlayerGame(
                    playerGameId = pgId,
                    gameId = nextGameId,
                    playerId = pid
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
                seasonOpponents = (current.seasonOpponents + extraSeasonOpponents).distinct()
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
                val nextPgId = (state.playerGames.maxOfOrNull { it.playerGameId } ?: 0) + 1
                val newPg = PlayerGame(playerGameId = nextPgId, gameId = gameId, playerId = playerId)
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

    fun updateGameScores(
        gameId: Int,
        homeScore: Int,
        awayScore: Int,
        homeHalftime: Int,
        awayHalftime: Int
    ) {
        mutate { state ->
            state.copy(
                games = state.games.map { game ->
                    if (game.gameId == gameId) {
                        game.copy(
                            homeTeamScore = homeScore.coerceAtLeast(0),
                            awayTeamScore = awayScore.coerceAtLeast(0),
                            homeTeamHalftimeScore = homeHalftime.coerceAtLeast(0),
                            awayTeamHalftimeScore = awayHalftime.coerceAtLeast(0)
                        )
                    } else game
                }
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
                coachStats = state.coachStats.filterNot { it.playerGameId in pgIds }
            )
        }
    }

    // --- PLAYER_GAME DISCIPLINARY ---
    fun updatePlayerDisciplinary(
        playerGameId: Int,
        twoMinuteSuspensions: Int,
        yellowCard: Boolean,
        redCard: Boolean,
        blueCard: Boolean
    ) {
        mutate { state ->
            state.copy(
                playerGames = state.playerGames.map { pg ->
                    if (pg.playerGameId == playerGameId) {
                        pg.copy(
                            twoMinuteSuspensions = twoMinuteSuspensions.coerceIn(0, 3),
                            yellowCard = yellowCard,
                            redCard = redCard,
                            blueCard = blueCard
                        )
                    } else pg
                }
            )
        }
    }

    // --- FIELD_PLAYER_GAMESTATS ---
    fun updateFieldPlayerStats(
        playerGameId: Int,
        autoSyncTeamScore: Boolean = false,
        transform: (FieldPlayerGameStats) -> FieldPlayerGameStats
    ) {
        mutate { state ->
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

            val newStatsList = if (existing != null) {
                state.fieldPlayerStats.map { if (it.playerGameId == playerGameId) updated else it }
            } else {
                state.fieldPlayerStats + updated
            }

            val updatedGames = if (autoSyncTeamScore && goalDelta != 0) {
                val pg = state.playerGames.find { it.playerGameId == playerGameId }
                state.games.map { g ->
                    if (g.gameId == pg?.gameId && g.isOwnTeamGame) {
                        if (g.isHomeGame) {
                            g.copy(homeTeamScore = (g.homeTeamScore + goalDelta).coerceAtLeast(0))
                        } else {
                            g.copy(awayTeamScore = (g.awayTeamScore + goalDelta).coerceAtLeast(0))
                        }
                    } else g
                }
            } else state.games

            state.copy(
                fieldPlayerStats = newStatsList,
                games = updatedGames
            )
        }
    }

    // --- GOAL_KEEPER_GAMESTATS ---
    fun updateGoalKeeperStats(
        playerGameId: Int,
        autoSyncOpponentScore: Boolean = false,
        transform: (GoalKeeperGameStats) -> GoalKeeperGameStats
    ) {
        mutate { state ->
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

            val newStatsList = if (existing != null) {
                state.goalKeeperStats.map { if (it.playerGameId == playerGameId) updated else it }
            } else {
                state.goalKeeperStats + updated
            }

            val updatedGames = if (autoSyncOpponentScore && concededDelta != 0) {
                val pg = state.playerGames.find { it.playerGameId == playerGameId }
                state.games.map { g ->
                    if (g.gameId == pg?.gameId && g.isOwnTeamGame) {
                        if (g.isHomeGame) {
                            g.copy(awayTeamScore = (g.awayTeamScore + concededDelta).coerceAtLeast(0))
                        } else {
                            g.copy(homeTeamScore = (g.homeTeamScore + concededDelta).coerceAtLeast(0))
                        }
                    } else g
                }
            } else state.games

            state.copy(
                goalKeeperStats = newStatsList,
                games = updatedGames
            )
        }
    }

    // --- COACH_GAMESTATS & TIMEOUT ---
    fun addCoachTimeout(playerGameId: Int, timeoutReason: String) {
        val reason = timeoutReason.trim().ifEmpty { "Team-Timeout (TTO)" }
        mutate { state ->
            val nextTimeoutId = (state.timeouts.maxOfOrNull { it.timeoutId } ?: 0) + 1
            val nextCoachStatId = (state.coachStats.maxOfOrNull { it.coachGamestatsId } ?: 0) + 1
            val timeout = Timeout(timeoutId = nextTimeoutId, timeoutReason = reason)
            val coachStat = CoachGameStats(
                coachGamestatsId = nextCoachStatId,
                playerGameId = playerGameId,
                timeoutId = nextTimeoutId
            )
            state.copy(
                timeouts = state.timeouts + timeout,
                coachStats = state.coachStats + coachStat
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
            // Nur gespielte Spiele werten (mindestens 1 Tor oder reguläres Ergebnis)
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
     * Berechnet einen objektiven Handball-Performance-Index (0..100 Punkte) auf Basis der aggregierten Werte.
     */
    fun computePerformanceScore(role: PlayerRole, stats: PlayerAggregatedStats): Double {
        if (stats.gamesAttended == 0) return 0.0
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
                18.0 + offense + defense - penalties
            }
            PlayerRole.GOALKEEPER -> {
                val keeping = (stats.savePercentage * 0.95) +
                    (stats.savesPerGame * 2.2) +
                    (stats.penaltySavePercentage * 0.20) +
                    (stats.fastBreakPassPercentage * 0.15)
                val penalties = (stats.twoMinPerGame * 2.5) +
                    ((stats.redCards + stats.blueCards) * 6.0 / stats.gamesAttended)
                keeping - penalties
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
            val penDrawn = byKey["pen_drawn_pg"]
            val mistakes = byKey["mistakes_pg"]

            val maxGoals = maxOf(10.0, (goals?.positionBest ?: 8.0) * 1.1)
            val maxAssists = maxOf(8.0, (assists?.positionBest ?: 6.0) * 1.1)
            val maxDefense = maxOf(5.0, (defense?.positionBest ?: 4.0) * 1.1)
            val maxPen = maxOf(3.0, (penDrawn?.positionBest ?: 2.5) * 1.1)

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
                    label = "7m gezogen",
                    playerNormalized = normHigher(penDrawn?.playerValue ?: 0.0, maxPen),
                    positionAvgNormalized = normHigher(penDrawn?.positionAverage ?: 0.0, maxPen),
                    playerFormatted = "${penDrawn?.playerValue ?: 0.0}",
                    avgFormatted = "${penDrawn?.positionAverage ?: 0.0}"
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
     * sowie sortierbar nach Gesamt-Performance oder Einzelkriterien).
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

    // --- OFFLINE-FIRST BACKEND SYNC ---
    suspend fun syncWithBackend(serverBaseUrl: String = "http://localhost:8080") {
        val normalizedUrl = serverBaseUrl.trimEnd('/')
        _syncStatus.value = "Synchronisiere mit $normalizedUrl ..."
        try {
            val response: DatabaseSnapshot = httpClient.post("$normalizedUrl/api/sync") {
                contentType(ContentType.Application.Json)
                setBody(_snapshot.value)
            }.body()
            _snapshot.value = response
            PlatformStorage.saveSnapshot(response)
            _syncStatus.value = "Sync erfolgreich mit $normalizedUrl (${response.games.size} Spiele, ${response.players.size} Kader)"
        } catch (e: Exception) {
            _syncStatus.value = "Offline-Modus aktiv (Server unter $normalizedUrl nicht erreichbar – lokal gespeichert)"
        }
    }
}
