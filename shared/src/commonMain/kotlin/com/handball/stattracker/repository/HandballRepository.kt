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
