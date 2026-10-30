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
            ),
            // Weitere Spieler je Position für aussagekräftige Positionsvergleiche, Wechsel & Rankings
            Player(
                playerId = 12,
                positionId = 2, // LA
                firstName = "Rune",
                lastName = "Dahmke-Vogel",
                email = "r.vogel@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 34,
                jerseyNumberAwayKit = 43
            ),
            Player(
                playerId = 13,
                positionId = 4, // RM
                firstName = "Luca",
                lastName = "Witzke-Hartmann",
                email = "l.hartmann@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 20,
                jerseyNumberAwayKit = 20
            ),
            Player(
                playerId = 14,
                positionId = 5, // RR
                firstName = "Franz",
                lastName = "Semper-Koch",
                email = "f.koch@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 32,
                jerseyNumberAwayKit = 23
            ),
            Player(
                playerId = 15,
                positionId = 6, // RA
                firstName = "Christoph",
                lastName = "Steinert-Neumann",
                email = "c.neumann@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 19,
                jerseyNumberAwayKit = 91
            ),
            Player(
                playerId = 16,
                positionId = 7, // KM
                firstName = "Jannik",
                lastName = "Kohlbacher-Schwarz",
                email = "j.schwarz@handball-loewen.de",
                role = PlayerRole.FIELD_PLAYER,
                jerseyNumberHomeKit = 80,
                jerseyNumberAwayKit = 80
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
                awayTeamScore = 28,
                elapsedSeconds = 3600
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
                awayTeamScore = 29,
                elapsedSeconds = 3600
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
                awayTeamScore = 26,
                elapsedSeconds = 3600
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
                awayTeamScore = 30,
                elapsedSeconds = 3600
            )
        )

        val playerGames = listOf(
            // Spiel 1 Kader (7 Spieler aktiv auf dem Feld: #1, #15, #18, #7, #4, #22, #24)
            PlayerGame(1, gameId = 1, playerId = 1, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 5, secondsPlayed = 2760),
            PlayerGame(2, gameId = 1, playerId = 2, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = -1, secondsPlayed = 840),
            PlayerGame(3, gameId = 1, playerId = 3, twoMinuteSuspensions = 1, yellowCard = true, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 6, secondsPlayed = 2640),
            PlayerGame(4, gameId = 1, playerId = 4, twoMinuteSuspensions = 2, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 4, secondsPlayed = 2400),
            PlayerGame(5, gameId = 1, playerId = 5, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 5, secondsPlayed = 2580),
            PlayerGame(6, gameId = 1, playerId = 6, twoMinuteSuspensions = 1, yellowCard = true, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 6, secondsPlayed = 2520),
            PlayerGame(7, gameId = 1, playerId = 7, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 3, secondsPlayed = 2460),
            PlayerGame(8, gameId = 1, playerId = 8, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 4, secondsPlayed = 2520),
            PlayerGame(9, gameId = 1, playerId = 10, twoMinuteSuspensions = 0, yellowCard = true, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 0, secondsPlayed = 0),
            // Spiel 2 Kader (7 Spieler aktiv auf dem Feld: #1, #15, #18, #7, #4, #22, #19)
            PlayerGame(10, gameId = 2, playerId = 1, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 0, secondsPlayed = 3600),
            PlayerGame(11, gameId = 2, playerId = 3, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 2, secondsPlayed = 2700),
            PlayerGame(12, gameId = 2, playerId = 4, twoMinuteSuspensions = 1, yellowCard = true, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 1, secondsPlayed = 2460),
            PlayerGame(13, gameId = 2, playerId = 5, twoMinuteSuspensions = 1, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 1, secondsPlayed = 2520),
            PlayerGame(14, gameId = 2, playerId = 6, twoMinuteSuspensions = 2, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 2, secondsPlayed = 2340),
            PlayerGame(15, gameId = 2, playerId = 7, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 0, secondsPlayed = 2400),
            PlayerGame(16, gameId = 2, playerId = 10, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 0, secondsPlayed = 0),
            // Spiel 3 Kader (7 Spieler aktiv auf dem Feld: #1, #15, #7, #4, #24, #9, #34)
            PlayerGame(17, gameId = 3, playerId = 1, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 6, secondsPlayed = 2400),
            PlayerGame(18, gameId = 3, playerId = 2, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 2, secondsPlayed = 1200),
            PlayerGame(19, gameId = 3, playerId = 3, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 9, secondsPlayed = 2640),
            PlayerGame(20, gameId = 3, playerId = 5, twoMinuteSuspensions = 1, yellowCard = true, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 7, secondsPlayed = 2580),
            PlayerGame(21, gameId = 3, playerId = 6, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 8, secondsPlayed = 2520),
            PlayerGame(22, gameId = 3, playerId = 8, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 6, secondsPlayed = 2460),
            PlayerGame(23, gameId = 3, playerId = 9, twoMinuteSuspensions = 1, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 5, secondsPlayed = 2400),
            PlayerGame(24, gameId = 3, playerId = 10, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 0, secondsPlayed = 0),
            // Zusätzliche Einsätze (Bank / Wechselspieler) für Positionsvergleich & Formkurven
            PlayerGame(25, gameId = 1, playerId = 9, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 0, secondsPlayed = 1200),
            PlayerGame(26, gameId = 2, playerId = 9, twoMinuteSuspensions = 1, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = -1, secondsPlayed = 1140),
            PlayerGame(27, gameId = 1, playerId = 12, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 1, secondsPlayed = 1140),
            PlayerGame(28, gameId = 2, playerId = 12, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 0, secondsPlayed = 1200),
            PlayerGame(29, gameId = 3, playerId = 12, twoMinuteSuspensions = 1, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 8, secondsPlayed = 2520),
            PlayerGame(30, gameId = 1, playerId = 13, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = -2, secondsPlayed = 960),
            PlayerGame(31, gameId = 2, playerId = 13, twoMinuteSuspensions = 1, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = -2, secondsPlayed = 900),
            PlayerGame(32, gameId = 3, playerId = 13, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = -1, secondsPlayed = 960),
            PlayerGame(33, gameId = 1, playerId = 14, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = -1, secondsPlayed = 1020),
            PlayerGame(34, gameId = 2, playerId = 14, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = -1, secondsPlayed = 1080),
            PlayerGame(35, gameId = 3, playerId = 14, twoMinuteSuspensions = 1, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 1, secondsPlayed = 1020),
            PlayerGame(36, gameId = 1, playerId = 15, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 0, secondsPlayed = 1080),
            PlayerGame(37, gameId = 2, playerId = 15, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = true, plusMinus = 0, secondsPlayed = 2400),
            PlayerGame(38, gameId = 3, playerId = 15, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 2, secondsPlayed = 1140),
            PlayerGame(39, gameId = 1, playerId = 16, twoMinuteSuspensions = 1, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = -2, secondsPlayed = 1080),
            PlayerGame(40, gameId = 2, playerId = 16, twoMinuteSuspensions = 1, yellowCard = true, redCard = false, blueCard = false, isOnCourt = false, plusMinus = -2, secondsPlayed = 1260),
            PlayerGame(41, gameId = 3, playerId = 16, twoMinuteSuspensions = 0, yellowCard = false, redCard = false, blueCard = false, isOnCourt = false, plusMinus = 0, secondsPlayed = 1080)
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
            FieldPlayerGameStats(16, playerGameId = 23, goalsScored = 5, goalesAttempted = 8, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 2, steals = 1, blocks = 2, technicalMistakes = 2),
            // Zusätzliche Spielstatistiken für Positionsvergleich
            FieldPlayerGameStats(17, playerGameId = 25, goalsScored = 3, goalesAttempted = 6, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 2, steals = 1, blocks = 1, technicalMistakes = 1),
            FieldPlayerGameStats(18, playerGameId = 26, goalsScored = 4, goalesAttempted = 7, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 3, steals = 1, blocks = 2, technicalMistakes = 2),
            FieldPlayerGameStats(19, playerGameId = 27, goalsScored = 2, goalesAttempted = 4, penaltiesScored = 1, penaltiesAttempted = 1, penaltiesDrawn = 0, assists = 1, steals = 1, blocks = 0, technicalMistakes = 1),
            FieldPlayerGameStats(20, playerGameId = 28, goalsScored = 3, goalesAttempted = 4, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 1, steals = 2, blocks = 0, technicalMistakes = 0),
            FieldPlayerGameStats(21, playerGameId = 29, goalsScored = 4, goalesAttempted = 5, penaltiesScored = 1, penaltiesAttempted = 1, penaltiesDrawn = 0, assists = 2, steals = 1, blocks = 0, technicalMistakes = 1),
            FieldPlayerGameStats(22, playerGameId = 30, goalsScored = 4, goalesAttempted = 6, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 2, assists = 5, steals = 1, blocks = 0, technicalMistakes = 1),
            FieldPlayerGameStats(23, playerGameId = 31, goalsScored = 5, goalesAttempted = 8, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 4, steals = 1, blocks = 0, technicalMistakes = 2),
            FieldPlayerGameStats(24, playerGameId = 32, goalsScored = 6, goalesAttempted = 8, penaltiesScored = 1, penaltiesAttempted = 1, penaltiesDrawn = 2, assists = 6, steals = 2, blocks = 1, technicalMistakes = 1),
            FieldPlayerGameStats(25, playerGameId = 33, goalsScored = 4, goalesAttempted = 7, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 2, steals = 0, blocks = 1, technicalMistakes = 1),
            FieldPlayerGameStats(26, playerGameId = 34, goalsScored = 5, goalesAttempted = 8, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 2, steals = 1, blocks = 1, technicalMistakes = 2),
            FieldPlayerGameStats(27, playerGameId = 35, goalsScored = 4, goalesAttempted = 6, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 3, steals = 1, blocks = 0, technicalMistakes = 1),
            FieldPlayerGameStats(28, playerGameId = 36, goalsScored = 2, goalesAttempted = 4, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 0, assists = 1, steals = 1, blocks = 0, technicalMistakes = 1),
            FieldPlayerGameStats(29, playerGameId = 37, goalsScored = 3, goalesAttempted = 5, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 1, assists = 2, steals = 1, blocks = 0, technicalMistakes = 0),
            FieldPlayerGameStats(30, playerGameId = 38, goalsScored = 4, goalesAttempted = 6, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 0, assists = 1, steals = 1, blocks = 0, technicalMistakes = 1),
            FieldPlayerGameStats(31, playerGameId = 39, goalsScored = 3, goalesAttempted = 4, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 2, assists = 1, steals = 1, blocks = 2, technicalMistakes = 1),
            FieldPlayerGameStats(32, playerGameId = 40, goalsScored = 4, goalesAttempted = 5, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 2, assists = 0, steals = 1, blocks = 2, technicalMistakes = 1),
            FieldPlayerGameStats(33, playerGameId = 41, goalsScored = 4, goalesAttempted = 5, penaltiesScored = 0, penaltiesAttempted = 0, penaltiesDrawn = 2, assists = 1, steals = 2, blocks = 2, technicalMistakes = 0)
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

        val gameLogs = listOf(
            // --- Spiel 1 Protokoll (Heimspiel vs. TSV Altenholz II, 32:28) ---
            GameLogEntry(1, gameId = 1, gameSecond = 0, eventType = GameLogEventType.LINEUP_SET, primaryPlayerId = 1, homeScoreAtEvent = 0, awayScoreAtEvent = 0, description = "Startsieben aufgestellt (6 Feld + 1 TW): #1 Landin-Weber, #15 Knorr-Schmidt, #18 Köster-Lang, #7 Uscins-Hoffmann, #4 Golla-Richter, #22 Mertens-Klein, #24 Kastening-Wolf"),
            GameLogEntry(2, gameId = 1, gameSecond = 75, eventType = GameLogEventType.GOAL, primaryPlayerId = 3, homeScoreAtEvent = 1, awayScoreAtEvent = 0, description = "Tor durch #15 Juri Knorr-Schmidt (Rückraum Mitte)"),
            GameLogEntry(3, gameId = 1, gameSecond = 260, eventType = GameLogEventType.GOAL_CONCEDED, primaryPlayerId = 1, homeScoreAtEvent = 2, awayScoreAtEvent = 4, description = "Gegenstoß-Lauf der Gäste (Rückstand -2)"),
            GameLogEntry(4, gameId = 1, gameSecond = 480, eventType = GameLogEventType.SEVEN_METER_GOAL, primaryPlayerId = 3, homeScoreAtEvent = 5, awayScoreAtEvent = 4, description = "7m-Tor durch #15 Juri Knorr-Schmidt zur Führung (+1)"),
            GameLogEntry(5, gameId = 1, gameSecond = 720, eventType = GameLogEventType.GOAL, primaryPlayerId = 5, homeScoreAtEvent = 8, awayScoreAtEvent = 5, description = "Tor durch #7 Renars Uscins-Hoffmann (+3 Führung)"),
            GameLogEntry(6, gameId = 1, gameSecond = 860, eventType = GameLogEventType.TWO_MINUTE_SUSPENSION, primaryPlayerId = 4, homeScoreAtEvent = 9, awayScoreAtEvent = 8, description = "2-Minuten-Strafe gegen #18 Julian Köster-Lang → automatisch auf die Bank"),
            GameLogEntry(7, gameId = 1, gameSecond = 985, eventType = GameLogEventType.SUBSTITUTION, primaryPlayerId = 9, secondaryPlayerId = null, homeScoreAtEvent = 10, awayScoreAtEvent = 9, description = "Einwechslung nach Zeitstrafe: #9 Sebastian Heymann-Berg aufs Feld (6 Feld + 1 TW)"),
            GameLogEntry(8, gameId = 1, gameSecond = 1080, eventType = GameLogEventType.TIMEOUT, primaryPlayerId = 10, homeScoreAtEvent = 10, awayScoreAtEvent = 10, description = "Auszeit (TTO) durch Trainer Alfred Gislason-Krause: Umstellung auf 5:1-Abwehr"),
            GameLogEntry(9, gameId = 1, gameSecond = 1220, eventType = GameLogEventType.GOAL_CONCEDED, primaryPlayerId = 1, homeScoreAtEvent = 10, awayScoreAtEvent = 12, description = "Gegentore in Unterzahl (-2)"),
            GameLogEntry(10, gameId = 1, gameSecond = 1320, eventType = GameLogEventType.SUBSTITUTION, primaryPlayerId = 13, secondaryPlayerId = 3, homeScoreAtEvent = 12, awayScoreAtEvent = 12, description = "Wechsel: #20 Luca Witzke-Hartmann rein für #15 Juri Knorr-Schmidt (6 Feld + 1 TW)"),
            GameLogEntry(11, gameId = 1, gameSecond = 1800, eventType = GameLogEventType.SCORE_UPDATE, primaryPlayerId = null, homeScoreAtEvent = 16, awayScoreAtEvent = 14, description = "Halbzeitstand: 16:14 (+2)"),
            GameLogEntry(12, gameId = 1, gameSecond = 2100, eventType = GameLogEventType.SUBSTITUTION, primaryPlayerId = 3, secondaryPlayerId = 13, homeScoreAtEvent = 17, awayScoreAtEvent = 18, description = "Wechsel: #15 Juri Knorr-Schmidt rein für #20 Luca Witzke-Hartmann (6 Feld + 1 TW)"),
            GameLogEntry(13, gameId = 1, gameSecond = 2400, eventType = GameLogEventType.GOAL, primaryPlayerId = 6, homeScoreAtEvent = 21, awayScoreAtEvent = 21, description = "Ausgleich vom Kreis durch #4 Johannes Golla-Richter (21:21)"),
            GameLogEntry(14, gameId = 1, gameSecond = 2640, eventType = GameLogEventType.SUBSTITUTION, primaryPlayerId = 2, secondaryPlayerId = 1, homeScoreAtEvent = 24, awayScoreAtEvent = 21, description = "Torwart-Wechsel: #33 Andreas Wolff-Bauer rein für #1 Niklas Landin-Weber (6 Feld + 1 TW)"),
            GameLogEntry(15, gameId = 1, gameSecond = 2940, eventType = GameLogEventType.GOAL_CONCEDED, primaryPlayerId = 2, homeScoreAtEvent = 25, awayScoreAtEvent = 24, description = "Anschlusstreffer der Gäste (+1)"),
            GameLogEntry(16, gameId = 1, gameSecond = 3120, eventType = GameLogEventType.TIMEOUT, primaryPlayerId = 10, homeScoreAtEvent = 28, awayScoreAtEvent = 25, description = "Auszeit (TTO): Spielzug 'Kreuz-Links' für Überzahl-Abschluss"),
            GameLogEntry(17, gameId = 1, gameSecond = 3180, eventType = GameLogEventType.SUBSTITUTION, primaryPlayerId = 1, secondaryPlayerId = 2, homeScoreAtEvent = 29, awayScoreAtEvent = 27, description = "Torwart-Wechsel: #1 Niklas Landin-Weber rein für #33 Andreas Wolff-Bauer (6 Feld + 1 TW)"),
            GameLogEntry(18, gameId = 1, gameSecond = 3540, eventType = GameLogEventType.GOAL, primaryPlayerId = 5, homeScoreAtEvent = 32, awayScoreAtEvent = 28, description = "Tor zum Endstand durch #7 Renars Uscins-Hoffmann (32:28)"),

            // --- Spiel 2 Protokoll (Auswärts bei SG Flensburg-Handewitt II, 29:29) ---
            GameLogEntry(19, gameId = 2, gameSecond = 0, eventType = GameLogEventType.LINEUP_SET, primaryPlayerId = 1, homeScoreAtEvent = 0, awayScoreAtEvent = 0, description = "Startsieben für Auswärtsspiel in Handewitt aufgestellt (6 Feld + 1 TW)"),
            GameLogEntry(20, gameId = 2, gameSecond = 420, eventType = GameLogEventType.GOAL, primaryPlayerId = 3, homeScoreAtEvent = 2, awayScoreAtEvent = 5, description = "Auswärts-Führung (+3) durch #15 Juri Knorr-Schmidt"),
            GameLogEntry(21, gameId = 2, gameSecond = 960, eventType = GameLogEventType.GOAL_CONCEDED, primaryPlayerId = 1, homeScoreAtEvent = 8, awayScoreAtEvent = 8, description = "Ausgleich der Gastgeber (8:8)"),
            GameLogEntry(22, gameId = 2, gameSecond = 1440, eventType = GameLogEventType.TIMEOUT, primaryPlayerId = 10, homeScoreAtEvent = 12, awayScoreAtEvent = 10, description = "Auszeit (TTO): Gegenstoßverhalten und Rückzugsphase korrigieren"),
            GameLogEntry(23, gameId = 2, gameSecond = 1800, eventType = GameLogEventType.SCORE_UPDATE, primaryPlayerId = null, homeScoreAtEvent = 15, awayScoreAtEvent = 13, description = "Halbzeitstand: 15:13"),
            GameLogEntry(24, gameId = 2, gameSecond = 2100, eventType = GameLogEventType.GOAL, primaryPlayerId = 5, homeScoreAtEvent = 17, awayScoreAtEvent = 19, description = "Führung gedreht durch #7 Renars Uscins-Hoffmann"),
            GameLogEntry(25, gameId = 2, gameSecond = 2280, eventType = GameLogEventType.TWO_MINUTE_SUSPENSION, primaryPlayerId = 6, homeScoreAtEvent = 20, awayScoreAtEvent = 19, description = "2-Minuten-Strafe gegen #4 Johannes Golla-Richter → automatisch auf die Bank"),
            GameLogEntry(26, gameId = 2, gameSecond = 2405, eventType = GameLogEventType.SUBSTITUTION, primaryPlayerId = 16, secondaryPlayerId = null, homeScoreAtEvent = 21, awayScoreAtEvent = 20, description = "Einwechslung: #80 Jannik Kohlbacher-Schwarz aufs Feld (6 Feld + 1 TW)"),
            GameLogEntry(27, gameId = 2, gameSecond = 3000, eventType = GameLogEventType.GOAL_CONCEDED, primaryPlayerId = 1, homeScoreAtEvent = 26, awayScoreAtEvent = 23, description = "3-Tore-Lauf der Gastgeber"),
            GameLogEntry(28, gameId = 2, gameSecond = 3540, eventType = GameLogEventType.TIMEOUT, primaryPlayerId = 10, homeScoreAtEvent = 29, awayScoreAtEvent = 28, description = "Auszeit (TTO): Letzter Angriff 7-gegen-6 (7 Feldspieler) zum Ausgleich"),
            GameLogEntry(29, gameId = 2, gameSecond = 3592, eventType = GameLogEventType.GOAL, primaryPlayerId = 3, homeScoreAtEvent = 29, awayScoreAtEvent = 29, description = "Ausgleichstor in letzter Sekunde durch #15 Juri Knorr-Schmidt (29:29)"),

            // --- Spiel 3 Protokoll (Heimspiel vs. HSG Ostsee N/G, 34:26) ---
            GameLogEntry(30, gameId = 3, gameSecond = 0, eventType = GameLogEventType.LINEUP_SET, primaryPlayerId = 1, homeScoreAtEvent = 0, awayScoreAtEvent = 0, description = "Startsieben aufgestellt (6 Feld + 1 TW)"),
            GameLogEntry(31, gameId = 3, gameSecond = 360, eventType = GameLogEventType.GOAL_CONCEDED, primaryPlayerId = 1, homeScoreAtEvent = 2, awayScoreAtEvent = 4, description = "Früher Rückstand (-2)"),
            GameLogEntry(32, gameId = 3, gameSecond = 900, eventType = GameLogEventType.GOAL, primaryPlayerId = 3, homeScoreAtEvent = 8, awayScoreAtEvent = 6, description = "Führung (+2) durch #15 Juri Knorr-Schmidt"),
            GameLogEntry(33, gameId = 3, gameSecond = 1350, eventType = GameLogEventType.GOAL_CONCEDED, primaryPlayerId = 1, homeScoreAtEvent = 11, awayScoreAtEvent = 11, description = "Ausgleich zum 11:11"),
            GameLogEntry(34, gameId = 3, gameSecond = 1800, eventType = GameLogEventType.SCORE_UPDATE, primaryPlayerId = null, homeScoreAtEvent = 18, awayScoreAtEvent = 12, description = "Halbzeitführung: 18:12"),
            GameLogEntry(35, gameId = 3, gameSecond = 2460, eventType = GameLogEventType.TIMEOUT, primaryPlayerId = 10, homeScoreAtEvent = 24, awayScoreAtEvent = 18, description = "Auszeit (TTO): Wechsel im Rückraum und Fokus auf Kreisanspiele"),
            GameLogEntry(36, gameId = 3, gameSecond = 2520, eventType = GameLogEventType.SUBSTITUTION, primaryPlayerId = 12, secondaryPlayerId = 7, homeScoreAtEvent = 25, awayScoreAtEvent = 18, description = "Wechsel: #34 Rune Dahmke-Vogel rein für #22 Lukas Mertens-Klein (6 Feld + 1 TW)"),
            GameLogEntry(37, gameId = 3, gameSecond = 3550, eventType = GameLogEventType.GOAL, primaryPlayerId = 6, homeScoreAtEvent = 34, awayScoreAtEvent = 26, description = "Tor vom Kreis durch #4 Johannes Golla-Richter zum 34:26")
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
            coachStats = coachStats,
            gameLogs = gameLogs
        )
    }
}
