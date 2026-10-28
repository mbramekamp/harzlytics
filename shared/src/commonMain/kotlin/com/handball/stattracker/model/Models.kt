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

    val goalsPerGame: Double
        get() = if (gamesAttended > 0) totalGoals.toDouble() / gamesAttended else 0.0

    val shotsPerGame: Double
        get() = if (gamesAttended > 0) totalShots.toDouble() / gamesAttended else 0.0

    val assistsPerGame: Double
        get() = if (gamesAttended > 0) totalAssists.toDouble() / gamesAttended else 0.0

    val stealsPerGame: Double
        get() = if (gamesAttended > 0) totalSteals.toDouble() / gamesAttended else 0.0

    val blocksPerGame: Double
        get() = if (gamesAttended > 0) totalBlocks.toDouble() / gamesAttended else 0.0

    val defensiveActionsPerGame: Double
        get() = if (gamesAttended > 0) (totalSteals + totalBlocks).toDouble() / gamesAttended else 0.0

    val penaltiesDrawnPerGame: Double
        get() = if (gamesAttended > 0) totalPenaltiesDrawn.toDouble() / gamesAttended else 0.0

    val technicalMistakesPerGame: Double
        get() = if (gamesAttended > 0) totalTechnicalMistakes.toDouble() / gamesAttended else 0.0

    val twoMinPerGame: Double
        get() = if (gamesAttended > 0) totalTwoMin.toDouble() / gamesAttended else 0.0

    val savesPerGame: Double
        get() = if (gamesAttended > 0) totalSaves.toDouble() / gamesAttended else 0.0

    val goalsConcededPerGame: Double
        get() = if (gamesAttended > 0) totalGoalsConceded.toDouble() / gamesAttended else 0.0
}

/**
 * Einzelner Datenpunkt für die chronologischen Performance-Graphen eines Spielers pro Spiel.
 */
data class PlayerGamePerformancePoint(
    val gameId: Int,
    val gameDate: String,
    val shortLabel: String,
    val matchupTitle: String,
    val outcomeBadge: String,
    val performanceScore: Double,
    // Feldspieler-Metriken
    val goals: Int = 0,
    val shots: Int = 0,
    val shotAccuracyPercent: Int = 0,
    val assists: Int = 0,
    val steals: Int = 0,
    val blocks: Int = 0,
    val penaltiesDrawn: Int = 0,
    val technicalMistakes: Int = 0,
    // Torhüter-Metriken
    val saves: Int = 0,
    val goalsConceded: Int = 0,
    val savePercentage: Int = 0,
    val penaltiesSaved: Int = 0,
    val fastBreakPassesSuccess: Int = 0,
    val fastBreakPassPercentage: Int = 0,
    // Disziplin
    val twoMinuteSuspensions: Int = 0
) {
    val defensiveActions: Int get() = steals + blocks
}

/**
 * Vergleich eines einzelnen Statistik-Werts eines Spielers mit allen anderen Spielern derselben Position.
 */
data class PositionMetricComparison(
    val metricKey: String,
    val metricLabel: String,
    val unit: String = "",
    val higherIsBetter: Boolean = true,
    val playerValue: Double,
    val positionAverage: Double,
    val positionBest: Double,
    val bestPlayerName: String,
    val rankInPosition: Int,
    val totalPlayersInPosition: Int
)

/**
 * Sortierkriterien für das Trainer-Spieler-Ranking.
 */
enum class RankingSortCriterion(val label: String) {
    PERFORMANCE_INDEX("Performance-Index"),
    GOALS_OR_SAVES("Tore / Paraden"),
    ACCURACY_OR_SAVE_RATE("Quote (%)"),
    ASSISTS_OR_FASTBREAK("Assists / Gegenstoß"),
    DEFENSE("Abwehr (Steals+Blocks/7m)"),
    DISCIPLINE("Disziplin (Wenig TF)")
}

/**
 * Eintrag im Positions- und Kader-Spieler-Ranking für Trainer.
 */
data class PlayerRankingEntry(
    val rank: Int,
    val player: Player,
    val positionName: String,
    val stats: PlayerAggregatedStats,
    val performanceScore: Double
)

/**
 * Normalisierte Achse (0..100) für das Radar-/Stärkenprofil (Spieler vs. Positions-Durchschnitt).
 */
data class RadarMetricAxis(
    val label: String,
    val playerNormalized: Float, // 0f..1f
    val positionAvgNormalized: Float, // 0f..1f
    val playerFormatted: String,
    val avgFormatted: String
)

