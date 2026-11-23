package com.handball.stattracker.model

/**
 * Ein-Tipp-Aktionen der mobilen Spielerfassung für Feldspieler. Jede Aktion zählt +1;
 * Korrekturen laufen über "Rückgängig" (`HandballRepository.restoreGameState`).
 * Würfe zählen alle Versuche inklusive 7m, Tore inklusive 7m-Tore.
 */
enum class FieldAction(val countsAsGoal: Boolean = false) {
    GOAL(countsAsGoal = true),
    MISS,
    PENALTY_GOAL(countsAsGoal = true),
    PENALTY_MISS,
    ASSIST,
    TECHNICAL_FAULT,
    STEAL,
    BLOCK,
    PENALTY_DRAWN;

    fun apply(s: FieldPlayerGameStats): FieldPlayerGameStats = when (this) {
        GOAL -> s.copy(goalsScored = s.goalsScored + 1, goalesAttempted = s.goalesAttempted + 1)
        MISS -> s.copy(goalesAttempted = s.goalesAttempted + 1)
        PENALTY_GOAL -> s.copy(
            penaltiesScored = s.penaltiesScored + 1, penaltiesAttempted = s.penaltiesAttempted + 1,
            goalsScored = s.goalsScored + 1, goalesAttempted = s.goalesAttempted + 1
        )
        PENALTY_MISS -> s.copy(penaltiesAttempted = s.penaltiesAttempted + 1, goalesAttempted = s.goalesAttempted + 1)
        ASSIST -> s.copy(assists = s.assists + 1)
        TECHNICAL_FAULT -> s.copy(technicalMistakes = s.technicalMistakes + 1)
        STEAL -> s.copy(steals = s.steals + 1)
        BLOCK -> s.copy(blocks = s.blocks + 1)
        PENALTY_DRAWN -> s.copy(penaltiesDrawn = s.penaltiesDrawn + 1)
    }

    /**
     * Gegenstück zu [apply] für die Minus-Knöpfe der großen Karten (Tablet/Desktop): nimmt eine Aktion zurück,
     * wenn sie gezählt ist ([count] > 0), und hält dabei Tore ≤ Würfe und 7m-Tore ≤ 7m-Versuche ein.
     */
    fun revert(s: FieldPlayerGameStats): FieldPlayerGameStats {
        if (count(s) <= 0) return s
        return when (this) {
            GOAL -> s.copy(goalsScored = s.goalsScored - 1, goalesAttempted = s.goalesAttempted - 1)
            MISS -> s.copy(goalesAttempted = s.goalesAttempted - 1)
            PENALTY_GOAL -> s.copy(
                penaltiesScored = s.penaltiesScored - 1, penaltiesAttempted = s.penaltiesAttempted - 1,
                goalsScored = (s.goalsScored - 1).coerceAtLeast(0), goalesAttempted = (s.goalesAttempted - 1).coerceAtLeast(0)
            )
            PENALTY_MISS -> s.copy(
                penaltiesAttempted = s.penaltiesAttempted - 1,
                goalesAttempted = (s.goalesAttempted - 1).coerceAtLeast(s.goalsScored)
            )
            ASSIST -> s.copy(assists = s.assists - 1)
            TECHNICAL_FAULT -> s.copy(technicalMistakes = s.technicalMistakes - 1)
            STEAL -> s.copy(steals = s.steals - 1)
            BLOCK -> s.copy(blocks = s.blocks - 1)
            PENALTY_DRAWN -> s.copy(penaltiesDrawn = s.penaltiesDrawn - 1)
        }
    }

    /** Bisheriger Zählerstand dieser Aktion (z. B. Fehlwürfe = Würfe − Tore). */
    fun count(s: FieldPlayerGameStats): Int = when (this) {
        GOAL -> s.goalsScored
        MISS -> (s.goalesAttempted - s.goalsScored).coerceAtLeast(0)
        PENALTY_GOAL -> s.penaltiesScored
        PENALTY_MISS -> (s.penaltiesAttempted - s.penaltiesScored).coerceAtLeast(0)
        ASSIST -> s.assists
        TECHNICAL_FAULT -> s.technicalMistakes
        STEAL -> s.steals
        BLOCK -> s.blocks
        PENALTY_DRAWN -> s.penaltiesDrawn
    }
}

/** Ein-Tipp-Aktionen für Torhüter. Paraden und Gegentore enthalten die 7m-Werte. */
enum class KeeperAction {
    SAVE,
    GOAL_CONCEDED,
    PENALTY_SAVED,
    PENALTY_CONCEDED,
    FAST_BREAK_PASS,
    FAST_BREAK_MISS;

    fun apply(s: GoalKeeperGameStats): GoalKeeperGameStats = when (this) {
        SAVE -> s.copy(saves = s.saves + 1)
        GOAL_CONCEDED -> s.copy(goalesConceded = s.goalesConceded + 1)
        PENALTY_SAVED -> s.copy(penaltiesSaved = s.penaltiesSaved + 1, saves = s.saves + 1)
        PENALTY_CONCEDED -> s.copy(penaltiesConceded = s.penaltiesConceded + 1, goalesConceded = s.goalesConceded + 1)
        FAST_BREAK_PASS -> s.copy(
            sucessfulFastBreakPasses = s.sucessfulFastBreakPasses + 1,
            attemptedFastBreakPasses = s.attemptedFastBreakPasses + 1
        )
        FAST_BREAK_MISS -> s.copy(attemptedFastBreakPasses = s.attemptedFastBreakPasses + 1)
    }

    fun count(s: GoalKeeperGameStats): Int = when (this) {
        SAVE -> s.saves
        GOAL_CONCEDED -> s.goalesConceded
        PENALTY_SAVED -> s.penaltiesSaved
        PENALTY_CONCEDED -> s.penaltiesConceded
        FAST_BREAK_PASS -> s.sucessfulFastBreakPasses
        FAST_BREAK_MISS -> (s.attemptedFastBreakPasses - s.sucessfulFastBreakPasses).coerceAtLeast(0)
    }
}
