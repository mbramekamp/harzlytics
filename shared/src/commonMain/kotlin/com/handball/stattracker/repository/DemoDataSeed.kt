package com.handball.stattracker.repository

import com.handball.stattracker.model.*

object DemoDataSeed {

    fun defaultPositions(): List<Position> = listOf(
        Position(1, "TW - Torwart"),
        Position(2, "LA - Linksaußen"),
        Position(3, "RL - Rückraum Links"),
        Position(4, "RM - Rückraum Mitte"),
        Position(5, "RR - Rückraum Rechts"),
        Position(6, "RA - Rechtsaußen"),
        Position(7, "KM - Kreismitte")
    )

    fun createDemoSnapshot(): DatabaseSnapshot {
        val positions = defaultPositions()

        val players = listOf(
            // Torhüter
            Player(
                playerId = 1,
                positionId = 1,
                firstName = "Niklas",
                lastName = "Landin-Weber",
                email = "niklas.weber@handball-loewen.de",
                role = PlayerRole.GOALKEEPER,
                jerseyNumberHomeKit = 1,
                jerseyNumberAwayKit = 12
            ),
            Player(
                playerId = 2,
                positionId = 1,
                firstName = "Andreas",
                lastName = "Wolff-Bauer",
                email = "a.bauer@handball-loewen.de",
                role = PlayerRole.GOALKEEPER,
                jerseyNumberHomeKit = 33,
                jerseyNumberAwayKit = 33
            ),
            // Feldspieler
            Player(
                playerId = 3,
                positionId = 4, // RM
                firstName = "Juri",
                lastName = "Knorr-Schmidt",
                email = "juri.schmidt@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 15,
                jerseyNumberAwayKit = 15
            ),
            Player(
                playerId = 4,
                positionId = 3, // RL
                firstName = "Julian",
                lastName = "Köster-Lang",
                email = "julian.lang@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 18,
                jerseyNumberAwayKit = 81
            ),
            Player(
                playerId = 5,
                positionId = 5, // RR
                firstName = "Renars",
                lastName = "Uscins-Hoffmann",
                email = "r.hoffmann@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 7,
                jerseyNumberAwayKit = 77
            ),
            Player(
                playerId = 6,
                positionId = 7, // KM
                firstName = "Johannes",
                lastName = "Golla-Richter",
                email = "j.richter@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 4,
                jerseyNumberAwayKit = 4
            ),
            Player(
                playerId = 7,
                positionId = 2, // LA
                firstName = "Lukas",
                lastName = "Mertens-Klein",
                email = "l.klein@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 22,
                jerseyNumberAwayKit = 22
            ),
            Player(
                playerId = 8,
                positionId = 6, // RA
                firstName = "Timo",
                lastName = "Kastening-Wolf",
                email = "t.wolf@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 24,
                jerseyNumberAwayKit = 42
            ),
            Player(
                playerId = 9,
                positionId = 3, // RL
                firstName = "Sebastian",
                lastName = "Heymann-Berg",
                email = "s.berg@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 9,
                jerseyNumberAwayKit = 9
            ),
            // Trainer (ohne Position & ohne Trikotnummern)
            Player(
                playerId = 10,
                positionId = null,
                firstName = "Alfred",
                lastName = "Gislason-Krause",
                email = "coach.krause@handball-loewen.de",
                role = PlayerRole.COACH,
                jerseyNumberHomeKit = null,
                jerseyNumberAwayKit = null
            ),
            Player(
                playerId = 11,
                positionId = null,
                firstName = "Erik",
                lastName = "Wudtke-Schulz",
                email = "co.schulz@handball-loewen.de",
                role = PlayerRole.COACH,
                jerseyNumberHomeKit = null,
                jerseyNumberAwayKit = null
            )
        )

        val seasons = listOf(
            Season(
                seasonId = 1,
                division = "Oberliga Nord 2026/27",
                startOfSeason = "2026-09-01",
                endOfSeason = "2027-05-31"
            ),
            Season(
                seasonId = 2,
                division = "Oberliga Nord 2025/26",
                startOfSeason = "2025-09-01",
                endOfSeason = "2026-05-31"
            )
        )

        val opponents = listOf(
            OpponentTeam(1, "TSV Altenholz II", "Altenholz"),
            OpponentTeam(2, "SG Flensburg-Handewitt II", "Flensburg"),
            OpponentTeam(3, "HSG Ostsee N/G", "Grömitz"),
            OpponentTeam(4, "MTV Lübeck", "Lübeck"),
            OpponentTeam(5, "HG Hamburg-Barmbek", "Hamburg")
        )

        val seasonOpponents = listOf(
            SeasonOpponent(1, 1),
            SeasonOpponent(1, 2),
            SeasonOpponent(1, 3),
            SeasonOpponent(1, 4),
            SeasonOpponent(1, 5),
            SeasonOpponent(2, 1),
            SeasonOpponent(2, 3),
            SeasonOpponent(2, 4)
        )

        val games = listOf(
            // Spiel 1: Eigenes Heimspiel gegen TSV Altenholz II (Sieg 32:28)
            Game(
                gameId = 1,
                seasonId = 1,
                opponentId = 1,
                secondOpponentId = null,
                isHomeGame = true,
                location = "Löwen-Arena",
                gameType = "Ligaspiel",
                gameDate = "2026-09-19",
                homeTeamHalftimeScore = 16,
                awayTeamHalftimeScore = 14,
                homeTeamScore = 32,
                awayTeamScore = 28
            ),
            // Spiel 2: Eigenes Auswärtsspiel bei SG Flensburg-Handewitt II (Unentschieden 29:29)
            Game(
                gameId = 2,
                seasonId = 1,
                opponentId = 2,
                secondOpponentId = null,
                isHomeGame = false,
                location = "Wikinghalle Handewitt",
                gameType = "Ligaspiel",
                gameDate = "2026-10-03",
                homeTeamHalftimeScore = 15,
                awayTeamHalftimeScore = 13,
                homeTeamScore = 29,
                awayTeamScore = 29
            ),
            // Spiel 3: Eigenes Heimspiel gegen HSG Ostsee N/G (Sieg 34:26)
            Game(
                gameId = 3,
                seasonId = 1,
                opponentId = 3,
                secondOpponentId = null,
                isHomeGame = true,
                location = "Löwen-Arena",
                gameType = "Ligaspiel",
                gameDate = "2026-10-17",
                homeTeamHalftimeScore = 18,
                awayTeamHalftimeScore = 12,
                homeTeamScore = 34,
                awayTeamScore = 26
            ),
            // Spiel 4: Ligaspiel zwischen zwei Gegnern (MTV Lübeck vs HG Hamburg-Barmbek 27:30)
            Game(
                gameId = 4,
                seasonId = 1,
                opponentId = 4,
                secondOpponentId = 5,
                isHomeGame = true,
                location = "Hansehalle Lübeck",
                gameType = "Ligaspiel (Fremdpaarung)",
                gameDate = "2026-10-18",
                homeTeamHalftimeScore = 13,
                awayTeamHalftimeScore = 15,
                homeTeamScore = 27,
                awayTeamScore = 30
            )
        )

        val playerGames = listOf(
            // Spiel 1 Kader
            PlayerGame(1, gameId = 1, playerId = 1, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(2, gameId = 1, playerId = 2, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(3, gameId = 1, playerId = 3, twoMinuteSuspensions = 1, yellowCard = true, redCard = false, blueCard = false),
            PlayerGame(4, gameId = 1, playerId = 4, twoMinuteSuspensions = 2, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(5, gameId = 1, playerId = 5, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(6, gameId = 1, playerId = 6, twoMinuteSuspensions = 1, yellowCard = true, redCard = false, blueCard = false),
            PlayerGame(7, gameId = 1, playerId = 7, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(8, gameId = 1, playerId = 8, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(9, gameId = 1, playerId = 10, twoMinuteSuspensions = 0, yellowCard = true, redCard = false, blueCard = false),
            // Spiel 2 Kader
            PlayerGame(10, gameId = 2, playerId = 1, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(11, gameId = 2, playerId = 3, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(12, gameId = 2, playerId = 4, twoMinuteSuspensions = 1, yellowCard = true, redCard = false, blueCard = false),
            PlayerGame(13, gameId = 2, playerId = 5, twoMinuteSuspensions = 1, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(14, gameId = 2, playerId = 6, twoMinuteSuspensions = 2, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(15, gameId = 2, playerId = 7, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(16, gameId = 2, playerId = 10, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            // Spiel 3 Kader
            PlayerGame(17, gameId = 3, playerId = 1, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(18, gameId = 3, playerId = 2, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(19, gameId = 3, playerId = 3, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(20, gameId = 3, playerId = 5, twoMinuteSuspensions = 1, yellowCard = true, redCard = false, blueCard = false),
            PlayerGame(21, gameId = 3, playerId = 6, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(22, gameId = 3, playerId = 8, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(23, gameId = 3, playerId = 9, twoMinuteSuspensions = 1, yellowCard = false, redCard = false, blueCard = false),
            PlayerGame(24, gameId = 3, playerId = 10, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false)
        )

        val keeperStats = listOf(
            GoalKeeperGameStats(1, playerGameId = 1, saves = 13, goalesConceded = 21, penaltiesSaved = 2, penaltiesConceded = 3, sucessfulFastBreakPasses = 4, attemptedFastBreakPasses = 5),
            GoalKeeperGameStats(2, playerGameId = 2, saves = 4, goalesConceded = 7, penaltiesSaved = 1, penaltiesConceded = 1, sucessfulFastBreakPasses = 1, attemptedFastBreakPasses = 1),
            GoalKeeperGameStats(3, playerGameId = 10, saves = 15, goalesConceded = 29, penaltiesSaved = 1, penaltiesConceded = 4, sucessfulFastBreakPasses = 5, attemptedFastBreakPasses = 6),
            GoalKeeperGameStats(4, playerGameId = 17, saves = 11, goalesConceded = 16, penaltiesSaved = 2, penaltiesConceded = 2, sucessfulFastBreakPasses = 3, attemptedFastBreakPasses = 4),
            GoalKeeperGameStats(5, playerGameId = 18, saves = 7, goalesConceded = 10, penaltiesSaved = 1, penaltiesConceded = 1, sucessfulFastBreakPasses = 2, attemptedFastBreakPasses = 2)
        )

        val fieldStats = listOf(
            // Spiel 1
            FieldPlayerGameStats(1, playerGameId = 3, goalsScored = 9, goalesAttempted = 12, penaltiesScored = 4, penaltiesAttempted = 5, penaltiesDrawn = 2, assists = 7, steals = 1, blocks = 0, technicalMistakes = 2),
            FieldPlayerGameStats(2, playerGameId = 4, goalsScored = 5, goalesAttempted = 8, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 4, steals = 2, blocks = 3, technicalMistakes = 1),
            FieldPlayerGameStats(3, playerGameId = 5, goalsScored = 7, goalesAttempted = 10, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 2, assists = 3, steals = 1, blocks = 1, technicalMistakes = 1),
            FieldPlayerGameStats(4, playerGameId = 6, goalsScored = 5, goalesAttempted = 6, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 3, assists = 1, steals = 2, blocks = 4, technicalMistakes = 0),
            FieldPlayerGameStats(5, playerGameId = 7, goalsScored = 3, goalesAttempted = 4, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 0, assists = 1, steals = 2, blocks = 0, technicalMistakes = 0),
            FieldPlayerGameStats(6, playerGameId = 8, goalsScored = 3, goalesAttempted = 5, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 0, assists = 2, steals = 1, blocks = 0, technicalMistakes = 1),
            // Spiel 2
            FieldPlayerGameStats(7, playerGameId = 11, goalsScored = 8, goalesAttempted = 11, penaltiesScored = 3, penaltiesAttempted = 3, penaltiesDrawn = 1, assists = 6, steals = 1, blocks = 0, technicalMistakes = 3),
            FieldPlayerGameStats(8, playerGameId = 12, goalsScored = 6, goalesAttempted = 9, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 3, steals = 1, blocks = 2, technicalMistakes = 1),
            FieldPlayerGameStats(9, playerGameId = 13, goalsScored = 6, goalesAttempted = 9, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 2, assists = 4, steals = 0, blocks = 1, technicalMistakes = 2),
            FieldPlayerGameStats(10, playerGameId = 14, goalsScored = 5, goalesAttempted = 5, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 2, assists = 0, steals = 1, blocks = 3, technicalMistakes = 1),
            FieldPlayerGameStats(11, playerGameId = 15, goalsScored = 4, goalesAttempted = 6, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 0, assists = 1, steals = 2, blocks = 0, technicalMistakes = 0),
            // Spiel 3
            FieldPlayerGameStats(12, playerGameId = 19, goalsScored = 10, goalesAttempted = 13, penaltiesScored = 3, penaltiesAttempted = 4, penaltiesDrawn = 2, assists = 8, steals = 2, blocks = 0, technicalMistakes = 1),
            FieldPlayerGameStats(13, playerGameId = 20, goalsScored = 8, goalesAttempted = 10, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 5, steals = 1, blocks = 1, technicalMistakes = 1),
            FieldPlayerGameStats(14, playerGameId = 21, goalsScored = 6, goalesAttempted = 7, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 3, assists = 2, steals = 1, blocks = 3, technicalMistakes = 0),
            FieldPlayerGameStats(15, playerGameId = 22, goalsScored = 5, goalesAttempted = 6, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 0, assists = 1, steals = 2, blocks = 0, technicalMistakes = 0),
            FieldPlayerGameStats(16, playerGameId = 23, goalsScored = 5, goalesAttempted = 8, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 2, steals = 1, blocks = 2, technicalMistakes = 2)
        )

        val timeouts = listOf(
            Timeout(1, "TTO 1 (18. Min): Umstellung auf 5:1-Abwehr & Tempospiel beruhigen"),
            Timeout(2, "TTO 2 (52. Min): Spielzug 'Kreuz-Links' für Überzahl-Abschluss"),
            Timeout(3, "TTO 1 (24. Min): Gegenstoßverhalten und Rückzugsphase korrigieren"),
            Timeout(4, "TTO 2 (59. Min): Letzter Angriff 7-gegen-6 zum Ausgleich"),
            Timeout(5, "TTO 1 (41. Min): Wechsel im Rückraum und Fokus auf Kreisanspiele")
        )

        val coachStats = listOf(
            CoachGameStats(1, playerGameId = 9, timeoutId = 1),
            CoachGameStats(2, playerGameId = 9, timeoutId = 2),
            CoachGameStats(3, playerGameId = 16, timeoutId = 3),
            CoachGameStats(4, playerGameId = 16, timeoutId = 4),
            CoachGameStats(5, playerGameId = 24, timeoutId = 5)
        )

        return DatabaseSnapshot(
            ownClubName = "HC Handball Löwen",
            lastUpdatedEpochMillis = 1761298000000L,
            positions = positions,
            players = players,
            seasons = seasons,
            opponentTeams = opponents,
            seasonOpponents = seasonOpponents,
            games = games,
            playerGames = playerGames,
            fieldPlayerStats = fieldStats,
            goalKeeperStats = keeperStats,
            timeouts = timeouts,
            coachStats = coachStats
        )
    }
}
