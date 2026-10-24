package com.handball.stattracker.model

import kotlinx.serialization.Serializable

/**
 * Rolle einer Person in der Tabelle PLAYER.
 * Trainer werden laut Design-Entscheidung ebenfalls in PLAYER mit role = COACH geführt
 * (wobei positionId und Trikotnummern optional/nullable sind).
 */
@Serializable
enum class PlayerRole(val label: String) {
    GOALKEEPER("Torwart"),
    FIELD_PLAYER("Feldspieler"),
    COACH("Trainer")
}

@Serializable
data class Position(
    val positionId: Int,
    val positionName: String
)

@Serializable
data class Player(
    val playerId: Int,
    val positionId: Int? = null,
    val firstName: String,
    val lastName: String,
    val email: String = "",
    val role: PlayerRole = PlayerRole.FIELD_PLAYER,
    val jerseyNumberHomeKit: Int? = null,
    val jerseyNumberAwayKit: Int? = null
) {
    val fullName: String get() = "$firstName $lastName".trim()
}

@Serializable
data class Season(
    val seasonId: Int,
    val division: String,
    val startOfSeason: String,
    val endOfSeason: String
)

@Serializable
data class OpponentTeam(
    val opponentTeamId: Int,
    val opponentName: String,
    val opponentLocation: String
)

/**
 * Verknüpfungstabelle SEASON_OPPONENT zur Zuweisung von Gegnern zu einer Saison ("Replika der Liga").
 */
@Serializable
data class SeasonOpponent(
    val seasonId: Int,
    val opponentTeamId: Int
)

@Serializable
data class Game(
    val gameId: Int,
    val seasonId: Int,
    val opponentId: Int,
    val secondOpponentId: Int? = null, // Falls nicht null: Ligaspiel zwischen opponentId (Heim) und secondOpponentId (Gast)
    val isHomeGame: Boolean = true,    // Für eigene Spiele: true = Eigenes Team ist Heim, false = Eigenes Team ist Gast
    val location: String,
    val gameType: String = "Ligaspiel",
    val gameDate: String,
    val homeTeamHalftimeScore: Int = 0,
    val awayTeamHalftimeScore: Int = 0,
    val homeTeamScore: Int = 0,
    val awayTeamScore: Int = 0
) {
    val isOwnTeamGame: Boolean get() = secondOpponentId == null
}

@Serializable
data class PlayerGame(
    val playerGameId: Int,
    val gameId: Int,
    val playerId: Int,
    val twoMinuteSuspensions: Int = 0,
    val yellowCard: Boolean = false,
    val redCard: Boolean = false,
    val blueCard: Boolean = false
)

@Serializable
data class FieldPlayerGameStats(
    val fieldPlayerGamestatsId: Int,
    val playerGameId: Int,
    val goalsScored: Int = 0,
    val goalesAttempted: Int = 0,
    val penaltiesScored: Int = 0,
    val penaltiesAttempted: Int = 0,
    val penaltiesDrawn: Int = 0,
    val assists: Int = 0,
    val steals: Int = 0,
    val blocks: Int = 0,
    val technicalMistakes: Int = 0
)

@Serializable
data class GoalKeeperGameStats(
    val goalKeeperGamestatsId: Int,
    val playerGameId: Int,
    val saves: Int = 0,
    val goalesConceded: Int = 0,
    val penaltiesSaved: Int = 0,
    val penaltiesConceded: Int = 0,
    val sucessfulFastBreakPasses: Int = 0,
    val attemptedFastBreakPasses: Int = 0
)

@Serializable
data class Timeout(
    val timeoutId: Int,
    val timeoutReason: String
)

@Serializable
data class CoachGameStats(
    val coachGamestatsId: Int,
    val playerGameId: Int,
    val timeoutId: Int
)

/**
 * Gesamter Datenbank-Snapshot für Offline-First-Persistenz und Backend-Synchronisation.
 */
@Serializable
data class DatabaseSnapshot(
    val ownClubName: String = "HC Handball Löwen",
    val lastUpdatedEpochMillis: Long = 0L,
    val positions: List<Position> = emptyList(),
    val players: List<Player> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val opponentTeams: List<OpponentTeam> = emptyList(),
    val seasonOpponents: List<SeasonOpponent> = emptyList(),
    val games: List<Game> = emptyList(),
    val playerGames: List<PlayerGame> = emptyList(),
    val fieldPlayerStats: List<FieldPlayerGameStats> = emptyList(),
    val goalKeeperStats: List<GoalKeeperGameStats> = emptyList(),
    val timeouts: List<Timeout> = emptyList(),
    val coachStats: List<CoachGameStats> = emptyList()
)

/**
 * Berechnete Zeile der Handball-Ligatabelle (2-Punkte-Regel).
 */
data class StandingsRow(
    val rank: Int,
    val teamName: String,
    val location: String,
    val isOwnTeam: Boolean,
    val gamesPlayed: Int,
    val wins: Int,
    val draws: Int,
    val losses: Int,
    val goalsFor: Int,
    val goalsAgainst: Int,
    val pointsPlus: Int,
    val pointsMinus: Int
) {
    val goalDifference: Int get() = goalsFor - goalsAgainst
    val pointsFormatted: String get() = "$pointsPlus:$pointsMinus"
    val goalsFormatted: String get() = "$goalsFor:$goalsAgainst"
}

/**
 * Eintrag in der Spielhistorie eines Spielers oder Trainers.
 */
data class PlayerGameHistoryEntry(
    val game: Game,
    val seasonName: String,
    val matchupTitle: String,
    val resultFormatted: String,
    val outcomeBadge: String, // "S", "U", "N"
    val playerGame: PlayerGame,
    val fieldStats: FieldPlayerGameStats?,
    val keeperStats: GoalKeeperGameStats?,
    val coachTimeouts: List<Timeout>
)

/**
 * Aggregierte Profil-Statistik eines Spielers oder Trainers über alle Spiele.
 */
data class PlayerAggregatedStats(
    val gamesAttended: Int = 0,
    val totalTwoMin: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
    val blueCards: Int = 0,
    // Feldspieler
    val totalGoals: Int = 0,
    val totalShots: Int = 0,
    val totalPenaltyGoals: Int = 0,
    val totalPenaltyAttempts: Int = 0,
    val totalPenaltiesDrawn: Int = 0,
    val totalAssists: Int = 0,
    val totalSteals: Int = 0,
    val totalBlocks: Int = 0,
    val totalTechnicalMistakes: Int = 0,
    // Torwart
    val totalSaves: Int = 0,
    val totalGoalsConceded: Int = 0,
    val totalPenaltiesSaved: Int = 0,
    val totalPenaltiesConceded: Int = 0,
    val totalFastBreakPassesSuccess: Int = 0,
    val totalFastBreakPassesAttempted: Int = 0,
    // Trainer
    val totalTimeoutsTaken: Int = 0
) {
    val shotAccuracyPercent: Int
        get() = if (totalShots > 0) ((totalGoals * 100.0) / totalShots).toInt() else 0

    val penaltyAccuracyPercent: Int
        get() = if (totalPenaltyAttempts > 0) ((totalPenaltyGoals * 100.0) / totalPenaltyAttempts).toInt() else 0

    val savePercentage: Int
        get() {
            val totalFaced = totalSaves + totalGoalsConceded
            return if (totalFaced > 0) ((totalSaves * 100.0) / totalFaced).toInt() else 0
        }

    val penaltySavePercentage: Int
        get() {
            val totalPenFaced = totalPenaltiesSaved + totalPenaltiesConceded
            return if (totalPenFaced > 0) ((totalPenaltiesSaved * 100.0) / totalPenFaced).toInt() else 0
        }

    val fastBreakPassPercentage: Int
        get() = if (totalFastBreakPassesAttempted > 0) {
            ((totalFastBreakPassesSuccess * 100.0) / totalFastBreakPassesAttempted).toInt()
        } else 0
}
