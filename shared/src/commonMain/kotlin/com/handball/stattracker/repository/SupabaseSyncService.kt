package com.handball.stattracker.repository

import com.handball.stattracker.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class SupabaseSnapshotRow(
    @SerialName("snapshot_key") val snapshotKey: String = "default",
    @SerialName("own_club_name") val ownClubName: String,
    @SerialName("last_updated_epoch_millis") val lastUpdatedEpochMillis: Long,
    @SerialName("payload") val payload: DatabaseSnapshot
)

@Serializable
private data class PgPositionRow(
    @SerialName("position_id") val positionId: Int,
    @SerialName("position_name") val positionName: String
)

@Serializable
private data class PgPlayerRow(
    @SerialName("player_id") val playerId: Int,
    @SerialName("position_id") val positionId: Int? = null,
    @SerialName("first_name") val firstName: String,
    @SerialName("last_name") val lastName: String,
    @SerialName("email") val email: String,
    @SerialName("role") val role: String,
    @SerialName("jersey_number_home_kit") val jerseyNumberHomeKit: Int? = null,
    @SerialName("jersey_number_away_kit") val jerseyNumberAwayKit: Int? = null
)

@Serializable
private data class PgSeasonRow(
    @SerialName("season_id") val seasonId: Int,
    @SerialName("division") val division: String,
    @SerialName("start_of_season") val startOfSeason: String,
    @SerialName("end_of_season") val endOfSeason: String
)

@Serializable
private data class PgOpponentTeamRow(
    @SerialName("opponent_team_id") val opponentTeamId: Int,
    @SerialName("opponent_name") val opponentName: String,
    @SerialName("opponent_location") val opponentLocation: String
)

@Serializable
private data class PgSeasonOpponentRow(
    @SerialName("season_id") val seasonId: Int,
    @SerialName("opponent_team_id") val opponentTeamId: Int
)

@Serializable
private data class PgGameRow(
    @SerialName("game_id") val gameId: Int,
    @SerialName("season_id") val seasonId: Int,
    @SerialName("opponent_id") val opponentId: Int,
    @SerialName("second_opponent_id") val secondOpponentId: Int? = null,
    @SerialName("is_home_game") val isHomeGame: Boolean,
    @SerialName("location") val location: String,
    @SerialName("game_type") val gameType: String,
    @SerialName("game_date") val gameDate: String,
    @SerialName("home_team_halftime_score") val homeTeamHalftimeScore: Int,
    @SerialName("away_team_halftime_score") val awayTeamHalftimeScore: Int,
    @SerialName("home_team_score") val homeTeamScore: Int,
    @SerialName("away_team_score") val awayTeamScore: Int,
    @SerialName("elapsed_seconds") val elapsedSeconds: Int
)

@Serializable
private data class PgPlayerGameRow(
    @SerialName("player_game_id") val playerGameId: Int,
    @SerialName("game_id") val gameId: Int,
    @SerialName("player_id") val playerId: Int,
    @SerialName("two_minute_suspensions") val twoMinuteSuspensions: Int,
    @SerialName("yellow_card") val yellowCard: Boolean,
    @SerialName("red_card") val redCard: Boolean,
    @SerialName("blue_card") val blueCard: Boolean,
    @SerialName("is_on_court") val isOnCourt: Boolean,
    @SerialName("plus_minus") val plusMinus: Int,
    @SerialName("seconds_played") val secondsPlayed: Int,
    @SerialName("active_suspension_until_seconds") val activeSuspensionUntilSeconds: Int? = null
)

@Serializable
private data class PgFieldPlayerStatsRow(
    @SerialName("field_player_gamestats_id") val fieldPlayerGamestatsId: Int,
    @SerialName("player_game_id") val playerGameId: Int,
    @SerialName("goals_scored") val goalsScored: Int,
    @SerialName("goales_attempted") val goalesAttempted: Int,
    @SerialName("penalties_scored") val penaltiesScored: Int,
    @SerialName("penalties_attempted") val penaltiesAttempted: Int,
    @SerialName("penalties_drawn") val penaltiesDrawn: Int,
    @SerialName("assists") val assists: Int,
    @SerialName("steals") val steals: Int,
    @SerialName("blocks") val blocks: Int,
    @SerialName("technical_mistakes") val technicalMistakes: Int
)

@Serializable
private data class PgGoalKeeperStatsRow(
    @SerialName("goal_keeper_gamestats_id") val goalKeeperGamestatsId: Int,
    @SerialName("player_game_id") val playerGameId: Int,
    @SerialName("saves") val saves: Int,
    @SerialName("goales_conceded") val goalesConceded: Int,
    @SerialName("penalties_saved") val penaltiesSaved: Int,
    @SerialName("penalties_conceded") val penaltiesConceded: Int,
    @SerialName("sucessful_fast_break_passes") val sucessfulFastBreakPasses: Int,
    @SerialName("attempted_fast_break_passes") val attemptedFastBreakPasses: Int
)

@Serializable
private data class PgTimeoutRow(
    @SerialName("timeout_id") val timeoutId: Int,
    @SerialName("timeout_reason") val timeoutReason: String
)

@Serializable
private data class PgCoachStatsRow(
    @SerialName("coach_gamestats_id") val coachGamestatsId: Int,
    @SerialName("player_game_id") val playerGameId: Int,
    @SerialName("timeout_id") val timeoutId: Int
)

@Serializable
private data class PgGameLogRow(
    @SerialName("log_id") val logId: Int,
    @SerialName("game_id") val gameId: Int,
    @SerialName("game_second") val gameSecond: Int,
    @SerialName("event_type") val eventType: String,
    @SerialName("primary_player_id") val primaryPlayerId: Int? = null,
    @SerialName("secondary_player_id") val secondaryPlayerId: Int? = null,
    @SerialName("home_score_at_event") val homeScoreAtEvent: Int,
    @SerialName("away_score_at_event") val awayScoreAtEvent: Int,
    @SerialName("description") val description: String
)

internal object SupabaseSyncService {

    suspend fun syncSnapshotWithSupabase(
        httpClient: HttpClient,
        baseUrl: String,
        apiKey: String,
        localSnapshot: DatabaseSnapshot
    ): DatabaseSnapshot {
        // 1. Prüfe, ob in app_snapshots bereits ein neuerer Snapshot vorliegt
        val getResponse: HttpResponse = httpClient.get("$baseUrl/rest/v1/app_snapshots") {
            parameter("snapshot_key", "eq.default")
            parameter("select", "*")
            header("apikey", apiKey)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
        }
        if (!getResponse.status.isSuccess()) {
            throw IllegalStateException("Supabase GET app_snapshots fehlgeschlagen: HTTP ${getResponse.status.value}")
        }

        val existingRows: List<SupabaseSnapshotRow> = getResponse.body()
        val remoteRow = existingRows.firstOrNull()
        if (remoteRow != null &&
            remoteRow.lastUpdatedEpochMillis > localSnapshot.lastUpdatedEpochMillis &&
            remoteRow.payload.positions.isNotEmpty()
        ) {
            return remoteRow.payload
        }

        // 2. Lokaler Snapshot ist neuer (oder erster Sync): In app_snapshots speichern
        val rowToSave = SupabaseSnapshotRow(
            snapshotKey = "default",
            ownClubName = localSnapshot.ownClubName,
            lastUpdatedEpochMillis = localSnapshot.lastUpdatedEpochMillis,
            payload = localSnapshot
        )
        val upsertSnapshotResp = httpClient.post("$baseUrl/rest/v1/app_snapshots") {
            parameter("on_conflict", "snapshot_key")
            contentType(ContentType.Application.Json)
            header("apikey", apiKey)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(rowToSave)
        }
        if (!upsertSnapshotResp.status.isSuccess()) {
            throw IllegalStateException("Supabase POST app_snapshots fehlgeschlagen: HTTP ${upsertSnapshotResp.status.value}")
        }

        // 3. Alle 12 relationalen PostgreSQL-Tabellen synchronisieren
        mirrorRelationalTables(httpClient, baseUrl, apiKey, localSnapshot)

        return localSnapshot
    }

    private suspend inline fun <reified T : Any> upsertRows(
        httpClient: HttpClient,
        baseUrl: String,
        apiKey: String,
        table: String,
        onConflict: String,
        rows: List<T>
    ) {
        if (rows.isEmpty()) return
        val resp = httpClient.post("$baseUrl/rest/v1/$table") {
            parameter("on_conflict", onConflict)
            contentType(ContentType.Application.Json)
            header("apikey", apiKey)
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            header("Prefer", "resolution=merge-duplicates,return=minimal")
            setBody(rows)
        }
        if (!resp.status.isSuccess()) {
            throw IllegalStateException("Supabase Upsert in $table fehlgeschlagen: HTTP ${resp.status.value}")
        }
    }

    private suspend fun mirrorRelationalTables(
        httpClient: HttpClient,
        baseUrl: String,
        apiKey: String,
        snapshot: DatabaseSnapshot
    ) {
        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "position",
            onConflict = "position_id",
            rows = snapshot.positions.map { PgPositionRow(it.positionId, it.positionName) }
        )

        val validPositionIds = snapshot.positions.map { it.positionId }.toSet()
        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "player",
            onConflict = "player_id",
            rows = snapshot.players.map {
                PgPlayerRow(
                    playerId = it.playerId,
                    positionId = it.positionId?.takeIf { id -> id in validPositionIds },
                    firstName = it.firstName,
                    lastName = it.lastName,
                    email = it.email,
                    role = it.role.name,
                    jerseyNumberHomeKit = it.jerseyNumberHomeKit,
                    jerseyNumberAwayKit = it.jerseyNumberAwayKit
                )
            }
        )

        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "season",
            onConflict = "season_id",
            rows = snapshot.seasons.map {
                PgSeasonRow(it.seasonId, it.division, it.startOfSeason, it.endOfSeason)
            }
        )

        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "opponent_teams",
            onConflict = "opponent_team_id",
            rows = snapshot.opponentTeams.map {
                PgOpponentTeamRow(it.opponentTeamId, it.opponentName, it.opponentLocation)
            }
        )

        val validSeasonIds = snapshot.seasons.map { it.seasonId }.toSet()
        val validOpponentIds = snapshot.opponentTeams.map { it.opponentTeamId }.toSet()
        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "season_opponent",
            onConflict = "season_id,opponent_team_id",
            rows = snapshot.seasonOpponents
                .filter { it.seasonId in validSeasonIds && it.opponentTeamId in validOpponentIds }
                .map { PgSeasonOpponentRow(it.seasonId, it.opponentTeamId) }
        )

        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "game",
            onConflict = "game_id",
            rows = snapshot.games
                .filter { it.seasonId in validSeasonIds && it.opponentId in validOpponentIds }
                .map {
                    PgGameRow(
                        gameId = it.gameId,
                        seasonId = it.seasonId,
                        opponentId = it.opponentId,
                        secondOpponentId = it.secondOpponentId?.takeIf { id -> id in validOpponentIds },
                        isHomeGame = it.isHomeGame,
                        location = it.location,
                        gameType = it.gameType,
                        gameDate = it.gameDate,
                        homeTeamHalftimeScore = it.homeTeamHalftimeScore,
                        awayTeamHalftimeScore = it.awayTeamHalftimeScore,
                        homeTeamScore = it.homeTeamScore,
                        awayTeamScore = it.awayTeamScore,
                        elapsedSeconds = it.elapsedSeconds
                    )
                }
        )

        val validGameIds = snapshot.games.map { it.gameId }.toSet()
        val validPlayerIds = snapshot.players.map { it.playerId }.toSet()
        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "player_game",
            onConflict = "player_game_id",
            rows = snapshot.playerGames
                .filter { it.gameId in validGameIds && it.playerId in validPlayerIds }
                .map {
                    PgPlayerGameRow(
                        playerGameId = it.playerGameId,
                        gameId = it.gameId,
                        playerId = it.playerId,
                        twoMinuteSuspensions = it.twoMinuteSuspensions,
                        yellowCard = it.yellowCard,
                        redCard = it.redCard,
                        blueCard = it.blueCard,
                        isOnCourt = it.isOnCourt,
                        plusMinus = it.plusMinus,
                        secondsPlayed = it.secondsPlayed,
                        activeSuspensionUntilSeconds = it.activeSuspensionUntilSeconds
                    )
                }
        )

        val validPlayerGameIds = snapshot.playerGames.map { it.playerGameId }.toSet()
        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "field_player_gamestats",
            onConflict = "field_player_gamestats_id",
            rows = snapshot.fieldPlayerStats
                .filter { it.playerGameId in validPlayerGameIds }
                .map {
                    PgFieldPlayerStatsRow(
                        fieldPlayerGamestatsId = it.fieldPlayerGamestatsId,
                        playerGameId = it.playerGameId,
                        goalsScored = it.goalsScored,
                        goalesAttempted = it.goalesAttempted,
                        penaltiesScored = it.penaltiesScored,
                        penaltiesAttempted = it.penaltiesAttempted,
                        penaltiesDrawn = it.penaltiesDrawn,
                        assists = it.assists,
                        steals = it.steals,
                        blocks = it.blocks,
                        technicalMistakes = it.technicalMistakes
                    )
                }
        )

        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "goal_keeper_gamestats",
            onConflict = "goal_keeper_gamestats_id",
            rows = snapshot.goalKeeperStats
                .filter { it.playerGameId in validPlayerGameIds }
                .map {
                    PgGoalKeeperStatsRow(
                        goalKeeperGamestatsId = it.goalKeeperGamestatsId,
                        playerGameId = it.playerGameId,
                        saves = it.saves,
                        goalesConceded = it.goalesConceded,
                        penaltiesSaved = it.penaltiesSaved,
                        penaltiesConceded = it.penaltiesConceded,
                        sucessfulFastBreakPasses = it.sucessfulFastBreakPasses,
                        attemptedFastBreakPasses = it.attemptedFastBreakPasses
                    )
                }
        )

        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "timeout",
            onConflict = "timeout_id",
            rows = snapshot.timeouts.map { PgTimeoutRow(it.timeoutId, it.timeoutReason) }
        )

        val validTimeoutIds = snapshot.timeouts.map { it.timeoutId }.toSet()
        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "coach_gamestats",
            onConflict = "coach_gamestats_id",
            rows = snapshot.coachStats
                .filter { it.playerGameId in validPlayerGameIds && it.timeoutId in validTimeoutIds }
                .map { PgCoachStatsRow(it.coachGamestatsId, it.playerGameId, it.timeoutId) }
        )

        upsertRows(
            httpClient = httpClient,
            baseUrl = baseUrl,
            apiKey = apiKey,
            table = "game_log",
            onConflict = "log_id",
            rows = snapshot.gameLogs
                .filter { it.gameId in validGameIds }
                .map {
                    PgGameLogRow(
                        logId = it.logId,
                        gameId = it.gameId,
                        gameSecond = it.gameSecond,
                        eventType = it.eventType.name,
                        primaryPlayerId = it.primaryPlayerId?.takeIf { id -> id in validPlayerIds },
                        secondaryPlayerId = it.secondaryPlayerId?.takeIf { id -> id in validPlayerIds },
                        homeScoreAtEvent = it.homeScoreAtEvent,
                        awayScoreAtEvent = it.awayScoreAtEvent,
                        description = it.description
                    )
                }
        )
    }
}
