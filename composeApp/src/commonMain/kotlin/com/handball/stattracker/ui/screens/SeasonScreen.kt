package com.handball.stattracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.*
import com.handball.stattracker.repository.HandballRepository

/**
 * Saison-Screen + Spielbericht & Statistik-Dashboard pro Spiel:
 * - Auswahl der Saison + direktes Anlegen einer 'Neuen Saison'
 * - Übersicht aller Spiele der Saison (eigene Spiele & Liga-Fremdpaarungen)
 * - Klick auf ein Spiel öffnet standardmäßig die schreibgeschützte Kaderliste mit allen Statistiken.
 * - Über den Button 'Bearbeitungsmodus' kann gezielt in das Editieren von Spielstand, Kader und Statistiken gewechselt werden.
 */
@Composable
fun SeasonScreen(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    selectedSeasonId: Int,
    selectedGameId: Int?,
    onSelectSeason: (Int) -> Unit,
    onSelectGame: (Int?) -> Unit,
    onNavigateToPlayer: (Int) -> Unit,
    onOpenNewSeasonDialog: () -> Unit,
    onOpenNewGameDialog: () -> Unit
) {
    val activeGame = snapshot.games.find { it.gameId == selectedGameId }
    if (activeGame != null) {
        GameInteractiveDashboard(
            repository = repository,
            snapshot = snapshot,
            game = activeGame,
            onBack = { onSelectGame(null) },
            onNavigateToPlayer = onNavigateToPlayer
        )
        return
    }

    val activeSeason = snapshot.seasons.find { it.seasonId == selectedSeasonId } ?: snapshot.seasons.firstOrNull()
    var filterMode by remember { mutableStateOf(0) } // 0 = Alle, 1 = Eigene Spiele, 2 = Liga-Fremdspiele

    val gamesInSeason = remember(snapshot.games, activeSeason, filterMode) {
        snapshot.games
            .filter { activeSeason == null || it.seasonId == activeSeason.seasonId }
            .filter {
                when (filterMode) {
                    1 -> it.isOwnTeamGame
                    2 -> !it.isOwnTeamGame
                    else -> true
                }
            }
            .sortedByDescending { it.gameDate }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Saison-Auswahlleiste + Neue Saison / Neues Spiel Buttons
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = activeSeason?.division ?: "Saison-Übersicht",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (activeSeason != null) {
                            Text(
                                text = "Zeitraum: ${activeSeason.startOfSeason} bis ${activeSeason.endOfSeason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onOpenNewSeasonDialog) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Neue Saison")
                        }
                        Button(onClick = onOpenNewGameDialog) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Neues Spiel")
                        }
                    }
                }

                // Saison-Chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    snapshot.seasons.forEach { season ->
                        FilterChip(
                            selected = season.seasonId == activeSeason?.seasonId,
                            onClick = { onSelectSeason(season.seasonId) },
                            label = { Text(season.division) }
                        )
                    }
                }

                // Spiel-Filter
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = filterMode == 0,
                        onClick = { filterMode = 0 },
                        label = { Text("Alle Spiele der Saison") }
                    )
                    FilterChip(
                        selected = filterMode == 1,
                        onClick = { filterMode = 1 },
                        label = { Text("Nur eigene Spiele") }
                    )
                    FilterChip(
                        selected = filterMode == 2,
                        onClick = { filterMode = 2 },
                        label = { Text("Fremdpaarungen (Liga-Replika)") }
                    )
                }
            }
        }

        // Spiele-Liste
        if (gamesInSeason.isEmpty()) {
            Card(
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Noch keine Spiele in dieser Ansicht vorhanden.",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = onOpenNewGameDialog) {
                        Text("Erstes Spiel für diese Saison anlegen")
                    }
                }
            }
        } else {
            gamesInSeason.forEach { game ->
                val squadCount = snapshot.playerGames.count { it.gameId == game.gameId }
                Card(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectGame(game.gameId) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (game.isOwnTeamGame) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    }
                                ) {
                                    Text(
                                        text = if (game.isOwnTeamGame) {
                                            if (game.isHomeGame) "HEIMSPIEL" else "AUSWÄRTSSPIEL"
                                        } else "LIGA-PAARUNG",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "${game.gameDate} • ${game.location} • ${game.gameType}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = repository.formatGameMatchup(game, snapshot),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (game.isOwnTeamGame) {
                                Text(
                                    text = "$squadCount Kader-Teilnehmer erfasst • Klick für Spielbericht & Statistiken",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer
                            ) {
                                Text(
                                    text = "${game.homeTeamScore} : ${game.awayTeamScore}",
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Text(
                                text = "HZ ${game.homeTeamHalftimeScore}:${game.awayTeamHalftimeScore}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Spiel-Detailansicht & Statistik-Dashboard:
 * Standardmäßig im schreibgeschützten Ansichtsmodus (Kaderliste mit allen Statistiken unbearbeitbar).
 * Über den Button "Bearbeitungsmodus" können Spielstand, Kader-Nominierung und alle Statistiken editiert werden.
 */
@Composable
private fun GameInteractiveDashboard(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    onBack: () -> Unit,
    onNavigateToPlayer: (Int) -> Unit
) {
    var isEditMode by remember(game.gameId) { mutableStateOf(false) }
    // 0 = Gesamte Kaderliste, 1 = Feldspieler, 2 = Torhüter, 3 = Trainer & Timeouts, 4 = Kader nominieren (nur im Edit-Modus)
    var activeTab by remember(game.gameId) { mutableStateOf(0) }
    var autoSyncScore by remember { mutableStateOf(true) }

    var showDirectScoreDialog by remember { mutableStateOf(false) }

    LaunchedEffect(isEditMode) {
        if (!isEditMode && activeTab == 4) {
            activeTab = 0
        }
    }

    val gamePlayerGames = remember(snapshot.playerGames, game.gameId) {
        snapshot.playerGames.filter { it.gameId == game.gameId }
    }

    val fieldParticipants = remember(gamePlayerGames, snapshot.players) {
        gamePlayerGames.mapNotNull { pg ->
            val p = snapshot.players.find { it.playerId == pg.playerId && it.role == PlayerRole.FIELD_PLAYER }
            if (p != null) pg to p else null
        }.sortedBy { it.second.jerseyNumberHomeKit ?: 99 }
    }

    val keeperParticipants = remember(gamePlayerGames, snapshot.players) {
        gamePlayerGames.mapNotNull { pg ->
            val p = snapshot.players.find { it.playerId == pg.playerId && it.role == PlayerRole.GOALKEEPER }
            if (p != null) pg to p else null
        }.sortedBy { it.second.jerseyNumberHomeKit ?: 99 }
    }

    val coachParticipants = remember(gamePlayerGames, snapshot.players) {
        gamePlayerGames.mapNotNull { pg ->
            val p = snapshot.players.find { it.playerId == pg.playerId && it.role == PlayerRole.COACH }
            if (p != null) pg to p else null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Scoreboard Card
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Zurück zur Saison")
                    }

                    Text(
                        text = "${game.gameDate} • ${game.location} • ${game.gameType}",
                        style = MaterialTheme.typography.labelLarge
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isEditMode) {
                            OutlinedButton(onClick = { showDirectScoreDialog = true }) {
                                Icon(Icons.Default.EditNote, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Ergebnis eingeben")
                            }
                            OutlinedButton(
                                onClick = {
                                    repository.deleteGame(game.gameId)
                                    onBack()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Spiel löschen")
                            }
                            Button(
                                onClick = { isEditMode = false },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Bearbeitung beenden")
                            }
                        } else {
                            Button(onClick = { isEditMode = true }) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Bearbeitungsmodus")
                            }
                        }
                    }
                }

                // Status-Hinweis für Ansichts- vs. Bearbeitungsmodus
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isEditMode) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditMode) Icons.Default.Edit else Icons.Default.Lock,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = if (isEditMode) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = if (isEditMode) {
                                "Bearbeitungsmodus aktiv: Spielstand, Kader-Nominierung und Spielerstatistiken können jetzt angepasst werden."
                            } else {
                                "Ansichtsmodus (schreibgeschützt): Die Kaderliste und Statistiken sind fixiert. Klicke oben rechts auf 'Bearbeitungsmodus' zum Editieren."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isEditMode) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // Haupt-Anzeigetafel für Heim & Gast sowie Halbzeit
                val opp1Name = snapshot.opponentTeams.find { it.opponentTeamId == game.opponentId }?.opponentName ?: "Gegner"
                val opp2Name = snapshot.opponentTeams.find { it.opponentTeamId == game.secondOpponentId }?.opponentName ?: "Gegner 2"
                val homeName = if (game.isOwnTeamGame) {
                    if (game.isHomeGame) snapshot.ownClubName else opp1Name
                } else opp1Name
                val awayName = if (game.isOwnTeamGame) {
                    if (game.isHomeGame) opp1Name else snapshot.ownClubName
                } else opp2Name

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Heim-Team
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("HEIM", style = MaterialTheme.typography.labelSmall)
                        Text(homeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            if (isEditMode) {
                                FilledTonalIconButton(
                                    onClick = {
                                        repository.updateGameScores(
                                            game.gameId,
                                            game.homeTeamScore - 1,
                                            game.awayTeamScore,
                                            game.homeTeamHalftimeScore,
                                            game.awayTeamHalftimeScore
                                        )
                                    }
                                ) {
                                    Text("-1", fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "${game.homeTeamScore}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (isEditMode) {
                                FilledIconButton(
                                    onClick = {
                                        repository.updateGameScores(
                                            game.gameId,
                                            game.homeTeamScore + 1,
                                            game.awayTeamScore,
                                            game.homeTeamHalftimeScore,
                                            game.awayTeamHalftimeScore
                                        )
                                    }
                                ) {
                                    Text("+1", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Halbzeitstand in der Mitte
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "VS",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Halbzeit: ${game.homeTeamHalftimeScore} : ${game.awayTeamHalftimeScore}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (isEditMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                                AssistChip(
                                    onClick = {
                                        repository.updateGameScores(
                                            game.gameId,
                                            game.homeTeamScore,
                                            game.awayTeamScore,
                                            game.homeTeamScore,
                                            game.awayTeamScore
                                        )
                                    },
                                    label = { Text("Aktuellen Stand als HZ übernehmen") }
                                )
                            }
                        }
                    }

                    // Gast-Team
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text("GAST", style = MaterialTheme.typography.labelSmall)
                        Text(awayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 6.dp)
                        ) {
                            if (isEditMode) {
                                FilledTonalIconButton(
                                    onClick = {
                                        repository.updateGameScores(
                                            game.gameId,
                                            game.homeTeamScore,
                                            game.awayTeamScore - 1,
                                            game.homeTeamHalftimeScore,
                                            game.awayTeamHalftimeScore
                                        )
                                    }
                                ) {
                                    Text("-1", fontWeight = FontWeight.Bold)
                                }
                            }
                            Text(
                                text = "${game.awayTeamScore}",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.ExtraBold
                            )
                            if (isEditMode) {
                                FilledIconButton(
                                    onClick = {
                                        repository.updateGameScores(
                                            game.gameId,
                                            game.homeTeamScore,
                                            game.awayTeamScore + 1,
                                            game.homeTeamHalftimeScore,
                                            game.awayTeamHalftimeScore
                                        )
                                    }
                                ) {
                                    Text("+1", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (game.isOwnTeamGame && isEditMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = autoSyncScore,
                            onCheckedChange = { autoSyncScore = it }
                        )
                        Text(
                            text = "Spielstand automatisch mitzählen, wenn Feldspieler-Tore oder Torwart-Gegentore per +/- geändert werden",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        if (!game.isOwnTeamGame) {
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (isEditMode) {
                        "Dies ist ein Ligaspiel zwischen zwei Gegnern der Staffel (für die vollständige Ligatabelle). Den Spielstand kannst du oben direkt anpassen."
                    } else {
                        "Dies ist ein Ligaspiel zwischen zwei Gegnern der Staffel. Klicke oben rechts auf 'Bearbeitungsmodus', um das Ergebnis anzupassen."
                    },
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            // Tabs für Gesamte Kaderliste, Feldspieler, Torhüter, Trainer (+ im Edit-Modus: Spieltagskader verwalten)
            SecondaryTabRow(selectedTabIndex = activeTab) {
                Tab(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    text = { Text("Kaderliste (${gamePlayerGames.size})") }
                )
                Tab(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    text = { Text("Feldspieler (${fieldParticipants.size})") }
                )
                Tab(
                    selected = activeTab == 2,
                    onClick = { activeTab = 2 },
                    text = { Text("Torhüter (${keeperParticipants.size})") }
                )
                Tab(
                    selected = activeTab == 3,
                    onClick = { activeTab = 3 },
                    text = { Text("Trainer & Timeouts (${coachParticipants.size})") }
                )
                if (isEditMode) {
                    Tab(
                        selected = activeTab == 4,
                        onClick = { activeTab = 4 },
                        text = { Text("Spieltagskader verwalten") }
                    )
                }
            }

            @Composable
            fun renderKeepersSection() {
                keeperParticipants.forEach { (pg, keeper) ->
                    val stats = snapshot.goalKeeperStats.find { it.playerGameId == pg.playerGameId }
                        ?: GoalKeeperGameStats(0, pg.playerGameId)
                    GoalKeeperStatTrackerCard(
                        keeper = keeper,
                        playerGame = pg,
                        stats = stats,
                        isEditMode = isEditMode,
                        onOpenProfile = { onNavigateToPlayer(keeper.playerId) },
                        onUpdateStats = { transform ->
                            repository.updateGoalKeeperStats(
                                playerGameId = pg.playerGameId,
                                autoSyncOpponentScore = autoSyncScore,
                                transform = transform
                            )
                        },
                        onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                            repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                        }
                    )
                }
            }

            @Composable
            fun renderFieldPlayersSection() {
                fieldParticipants.forEach { (pg, player) ->
                    val stats = snapshot.fieldPlayerStats.find { it.playerGameId == pg.playerGameId }
                        ?: FieldPlayerGameStats(0, pg.playerGameId)
                    val posName = snapshot.positions.find { it.positionId == player.positionId }?.positionName ?: "Feld"
                    FieldPlayerStatTrackerCard(
                        player = player,
                        positionName = posName,
                        playerGame = pg,
                        stats = stats,
                        isEditMode = isEditMode,
                        onOpenProfile = { onNavigateToPlayer(player.playerId) },
                        onUpdateStats = { transform ->
                            repository.updateFieldPlayerStats(
                                playerGameId = pg.playerGameId,
                                autoSyncTeamScore = autoSyncScore,
                                transform = transform
                            )
                        },
                        onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                            repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                        }
                    )
                }
            }

            @Composable
            fun renderCoachesSection() {
                coachParticipants.forEach { (pg, coach) ->
                    val coachStatRows = snapshot.coachStats.filter { it.playerGameId == pg.playerGameId }
                    CoachTimeoutTrackerCard(
                        coach = coach,
                        playerGame = pg,
                        coachStatRows = coachStatRows,
                        allTimeouts = snapshot.timeouts,
                        isEditMode = isEditMode,
                        onAddTimeout = { reason ->
                            repository.addCoachTimeout(pg.playerGameId, reason)
                        },
                        onRemoveTimeout = { coachGamestatsId ->
                            repository.removeCoachTimeout(coachGamestatsId)
                        },
                        onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                            repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                        }
                    )
                }
            }

            when (activeTab) {
                0 -> {
                    if (gamePlayerGames.isEmpty()) {
                        EmptySquadHint(
                            isEditMode = isEditMode,
                            onEnterEditAndManageSquad = {
                                isEditMode = true
                                activeTab = 4
                            }
                        )
                    } else {
                        if (keeperParticipants.isNotEmpty()) {
                            Text(
                                text = "TORHÜTER (${keeperParticipants.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            renderKeepersSection()
                        }
                        if (fieldParticipants.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "FELDSPIELER (${fieldParticipants.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            renderFieldPlayersSection()
                        }
                        if (coachParticipants.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = "TRAINER & AUSZEITEN (${coachParticipants.size})",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            renderCoachesSection()
                        }
                    }
                }

                1 -> {
                    if (fieldParticipants.isEmpty()) {
                        EmptySquadHint(
                            isEditMode = isEditMode,
                            onEnterEditAndManageSquad = {
                                isEditMode = true
                                activeTab = 4
                            }
                        )
                    } else {
                        renderFieldPlayersSection()
                    }
                }

                2 -> {
                    if (keeperParticipants.isEmpty()) {
                        EmptySquadHint(
                            isEditMode = isEditMode,
                            onEnterEditAndManageSquad = {
                                isEditMode = true
                                activeTab = 4
                            }
                        )
                    } else {
                        renderKeepersSection()
                    }
                }

                3 -> {
                    if (coachParticipants.isEmpty()) {
                        EmptySquadHint(
                            isEditMode = isEditMode,
                            onEnterEditAndManageSquad = {
                                isEditMode = true
                                activeTab = 4
                            }
                        )
                    } else {
                        renderCoachesSection()
                    }
                }

                4 -> {
                    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Nominierte Spieler & Trainer für dieses Spiel (PLAYER_GAME)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            snapshot.players.forEach { person ->
                                val isNominated = gamePlayerGames.any { it.playerId == person.playerId }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { repository.togglePlayerInGame(game.gameId, person.playerId) }
                                        .padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Checkbox(
                                            checked = isNominated,
                                            onCheckedChange = { repository.togglePlayerInGame(game.gameId, person.playerId) }
                                        )
                                        Text(
                                            text = "${person.fullName} (${person.role.label})",
                                            fontWeight = if (isNominated) FontWeight.Bold else FontWeight.Normal
                                        )
                                    }
                                    if (person.jerseyNumberHomeKit != null) {
                                        Text("#${person.jerseyNumberHomeKit}")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDirectScoreDialog) {
        DirectScoreEditDialog(
            game = game,
            onDismiss = { showDirectScoreDialog = false },
            onSave = { hScore, aScore, hHalf, aHalf ->
                repository.updateGameScores(game.gameId, hScore, aScore, hHalf, aHalf)
                showDirectScoreDialog = false
            }
        )
    }
}

@Composable
private fun EmptySquadHint(
    isEditMode: Boolean,
    onEnterEditAndManageSquad: () -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Noch keine Personen dieser Kategorie für dieses Spiel nominiert.")
            Button(onClick = onEnterEditAndManageSquad) {
                Text(if (isEditMode) "Spieltagskader auswählen" else "Bearbeitungsmodus starten & Kader auswählen")
            }
        }
    }
}

@Composable
private fun FieldPlayerStatTrackerCard(
    player: Player,
    positionName: String,
    playerGame: PlayerGame,
    stats: FieldPlayerGameStats,
    isEditMode: Boolean,
    onOpenProfile: () -> Unit,
    onUpdateStats: ((FieldPlayerGameStats) -> FieldPlayerGameStats) -> Unit,
    onUpdateDisciplinary: (Int, Boolean, Boolean, Boolean) -> Unit
) {
    var showDirectEdit by remember { mutableStateOf(false) }

    LaunchedEffect(isEditMode) {
        if (!isEditMode) showDirectEdit = false
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clickable(onClick = onOpenProfile)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = "#${player.jerseyNumberHomeKit ?: "-"}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = player.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val shotPct = if (stats.goalesAttempted > 0) (stats.goalsScored * 100) / stats.goalesAttempted else 0
                        Text(
                            text = "$positionName • Quote: ${stats.goalsScored}/${stats.goalesAttempted} ($shotPct%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Disziplinar-Leiste (2-Minuten, Gelb, Rot, Blau)
                if (isEditMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = playerGame.twoMinuteSuspensions > 0,
                            onClick = {
                                val next = (playerGame.twoMinuteSuspensions + 1) % 4
                                onUpdateDisciplinary(next, playerGame.yellowCard, playerGame.redCard, playerGame.blueCard)
                            },
                            label = { Text("2Min: ${playerGame.twoMinuteSuspensions}") }
                        )
                        FilterChip(
                            selected = playerGame.yellowCard,
                            onClick = {
                                onUpdateDisciplinary(
                                    playerGame.twoMinuteSuspensions,
                                    !playerGame.yellowCard,
                                    playerGame.redCard,
                                    playerGame.blueCard
                                )
                            },
                            label = { Text("Gelb") }
                        )
                        FilterChip(
                            selected = playerGame.redCard,
                            onClick = {
                                onUpdateDisciplinary(
                                    playerGame.twoMinuteSuspensions,
                                    playerGame.yellowCard,
                                    !playerGame.redCard,
                                    playerGame.blueCard
                                )
                            },
                            label = { Text("Rot") }
                        )
                        FilterChip(
                            selected = playerGame.blueCard,
                            onClick = {
                                onUpdateDisciplinary(
                                    playerGame.twoMinuteSuspensions,
                                    playerGame.yellowCard,
                                    playerGame.redCard,
                                    !playerGame.blueCard
                                )
                            },
                            label = { Text("Blau") }
                        )
                        IconButton(onClick = { showDirectEdit = !showDirectEdit }) {
                            Icon(Icons.Default.EditNote, contentDescription = "Direkte Zahleneingabe")
                        }
                    }
                } else {
                    ReadOnlyDisciplinaryRow(playerGame = playerGame)
                }
            }

            // Statistik-Werte (im Ansichtsmodus schreibgeschützt, im Bearbeitungsmodus mit +/- Buttons)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = if (isEditMode) "Tor (+Wurf)" else "Tore",
                    value = stats.goalsScored,
                    isEditMode = isEditMode,
                    onIncrement = {
                        onUpdateStats {
                            it.copy(
                                goalsScored = it.goalsScored + 1,
                                goalesAttempted = it.goalesAttempted + 1
                            )
                        }
                    },
                    onDecrement = {
                        onUpdateStats {
                            it.copy(
                                goalsScored = (it.goalsScored - 1).coerceAtLeast(0),
                                goalesAttempted = (it.goalesAttempted - 1).coerceAtLeast(0)
                            )
                        }
                    }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "Würfe ges.",
                    value = stats.goalesAttempted,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(goalesAttempted = it.goalesAttempted + 1) } },
                    onDecrement = { onUpdateStats { it.copy(goalesAttempted = (it.goalesAttempted - 1).coerceAtLeast(it.goalsScored)) } }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "7m-Tore",
                    value = stats.penaltiesScored,
                    isEditMode = isEditMode,
                    onIncrement = {
                        onUpdateStats {
                            it.copy(
                                penaltiesScored = it.penaltiesScored + 1,
                                penaltiesAttempted = it.penaltiesAttempted + 1,
                                goalsScored = it.goalsScored + 1,
                                goalesAttempted = it.goalesAttempted + 1
                            )
                        }
                    },
                    onDecrement = {
                        onUpdateStats {
                            it.copy(
                                penaltiesScored = (it.penaltiesScored - 1).coerceAtLeast(0),
                                penaltiesAttempted = (it.penaltiesAttempted - 1).coerceAtLeast(0)
                            )
                        }
                    }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "7m-Vers.",
                    value = stats.penaltiesAttempted,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(penaltiesAttempted = it.penaltiesAttempted + 1) } },
                    onDecrement = { onUpdateStats { it.copy(penaltiesAttempted = (it.penaltiesAttempted - 1).coerceAtLeast(it.penaltiesScored)) } }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "7m gez.",
                    value = stats.penaltiesDrawn,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(penaltiesDrawn = it.penaltiesDrawn + 1) } },
                    onDecrement = { onUpdateStats { it.copy(penaltiesDrawn = (it.penaltiesDrawn - 1).coerceAtLeast(0)) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "Assists",
                    value = stats.assists,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(assists = it.assists + 1) } },
                    onDecrement = { onUpdateStats { it.copy(assists = (it.assists - 1).coerceAtLeast(0)) } }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "Steals",
                    value = stats.steals,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(steals = it.steals + 1) } },
                    onDecrement = { onUpdateStats { it.copy(steals = (it.steals - 1).coerceAtLeast(0)) } }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "Blocks",
                    value = stats.blocks,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(blocks = it.blocks + 1) } },
                    onDecrement = { onUpdateStats { it.copy(blocks = (it.blocks - 1).coerceAtLeast(0)) } }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "Tech. Fehler",
                    value = stats.technicalMistakes,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(technicalMistakes = it.technicalMistakes + 1) } },
                    onDecrement = { onUpdateStats { it.copy(technicalMistakes = (it.technicalMistakes - 1).coerceAtLeast(0)) } }
                )
            }

            // Optionale direkte Zahleneingabe (nur im Bearbeitungsmodus)
            if (isEditMode && showDirectEdit) {
                var goalsText by remember(stats) { mutableStateOf(stats.goalsScored.toString()) }
                var shotsText by remember(stats) { mutableStateOf(stats.goalesAttempted.toString()) }
                var assistsText by remember(stats) { mutableStateOf(stats.assists.toString()) }
                var tfText by remember(stats) { mutableStateOf(stats.technicalMistakes.toString()) }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = goalsText,
                            onValueChange = { goalsText = it },
                            label = { Text("Tore") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = shotsText,
                            onValueChange = { shotsText = it },
                            label = { Text("Würfe") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = assistsText,
                            onValueChange = { assistsText = it },
                            label = { Text("Assists") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tfText,
                            onValueChange = { tfText = it },
                            label = { Text("TF") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onUpdateStats { current ->
                                    current.copy(
                                        goalsScored = goalsText.toIntOrNull() ?: current.goalsScored,
                                        goalesAttempted = shotsText.toIntOrNull() ?: current.goalesAttempted,
                                        assists = assistsText.toIntOrNull() ?: current.assists,
                                        technicalMistakes = tfText.toIntOrNull() ?: current.technicalMistakes
                                    )
                                }
                                showDirectEdit = false
                            }
                        ) {
                            Text("Übernehmen")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GoalKeeperStatTrackerCard(
    keeper: Player,
    playerGame: PlayerGame,
    stats: GoalKeeperGameStats,
    isEditMode: Boolean,
    onOpenProfile: () -> Unit,
    onUpdateStats: ((GoalKeeperGameStats) -> GoalKeeperGameStats) -> Unit,
    onUpdateDisciplinary: (Int, Boolean, Boolean, Boolean) -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.clickable(onClick = onOpenProfile)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.tertiary
                    ) {
                        Text(
                            text = "#${keeper.jerseyNumberHomeKit ?: "-"}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Text(
                            text = keeper.fullName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        val totalFaced = stats.saves + stats.goalesConceded
                        val savePct = if (totalFaced > 0) (stats.saves * 100) / totalFaced else 0
                        Text(
                            text = "Torwart • Fangquote: ${stats.saves}/$totalFaced ($savePct%)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isEditMode) {
                    FilterChip(
                        selected = playerGame.twoMinuteSuspensions > 0,
                        onClick = {
                            val next = (playerGame.twoMinuteSuspensions + 1) % 4
                            onUpdateDisciplinary(next, playerGame.yellowCard, playerGame.redCard, playerGame.blueCard)
                        },
                        label = { Text("2Min: ${playerGame.twoMinuteSuspensions}") }
                    )
                } else {
                    ReadOnlyDisciplinaryRow(playerGame = playerGame)
                }
            }

            // Alle 6 Felder aus GOAL_KEEPER_GAMESTATS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "Paraden",
                    value = stats.saves,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(saves = it.saves + 1) } },
                    onDecrement = { onUpdateStats { it.copy(saves = (it.saves - 1).coerceAtLeast(0)) } }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "Gegentore",
                    value = stats.goalesConceded,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(goalesConceded = it.goalesConceded + 1) } },
                    onDecrement = { onUpdateStats { it.copy(goalesConceded = (it.goalesConceded - 1).coerceAtLeast(0)) } }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "7m gehalten",
                    value = stats.penaltiesSaved,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(penaltiesSaved = it.penaltiesSaved + 1, saves = it.saves + 1) } },
                    onDecrement = { onUpdateStats { it.copy(penaltiesSaved = (it.penaltiesSaved - 1).coerceAtLeast(0)) } }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "7m-Gegentor",
                    value = stats.penaltiesConceded,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(penaltiesConceded = it.penaltiesConceded + 1, goalesConceded = it.goalesConceded + 1) } },
                    onDecrement = { onUpdateStats { it.copy(penaltiesConceded = (it.penaltiesConceded - 1).coerceAtLeast(0)) } }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "TG-Pass erf.",
                    value = stats.sucessfulFastBreakPasses,
                    isEditMode = isEditMode,
                    onIncrement = {
                        onUpdateStats {
                            it.copy(
                                sucessfulFastBreakPasses = it.sucessfulFastBreakPasses + 1,
                                attemptedFastBreakPasses = it.attemptedFastBreakPasses + 1
                            )
                        }
                    },
                    onDecrement = {
                        onUpdateStats {
                            it.copy(sucessfulFastBreakPasses = (it.sucessfulFastBreakPasses - 1).coerceAtLeast(0))
                        }
                    }
                )
                StatStepperBox(
                    modifier = Modifier.weight(1f),
                    label = "TG-Pass Vers.",
                    value = stats.attemptedFastBreakPasses,
                    isEditMode = isEditMode,
                    onIncrement = { onUpdateStats { it.copy(attemptedFastBreakPasses = it.attemptedFastBreakPasses + 1) } },
                    onDecrement = {
                        onUpdateStats {
                            it.copy(attemptedFastBreakPasses = (it.attemptedFastBreakPasses - 1).coerceAtLeast(it.sucessfulFastBreakPasses))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun CoachTimeoutTrackerCard(
    coach: Player,
    playerGame: PlayerGame,
    coachStatRows: List<CoachGameStats>,
    allTimeouts: List<Timeout>,
    isEditMode: Boolean,
    onAddTimeout: (String) -> Unit,
    onRemoveTimeout: (Int) -> Unit,
    onUpdateDisciplinary: (Int, Boolean, Boolean, Boolean) -> Unit
) {
    var timeoutReasonDraft by remember { mutableStateOf("") }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${coach.fullName} (Trainer)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${coachStatRows.size} Auszeiten (COACH_GAMESTATS / TIMEOUT) in diesem Spiel",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (isEditMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = playerGame.yellowCard,
                            onClick = {
                                onUpdateDisciplinary(
                                    playerGame.twoMinuteSuspensions,
                                    !playerGame.yellowCard,
                                    playerGame.redCard,
                                    playerGame.blueCard
                                )
                            },
                            label = { Text("Gelb (Bank)") }
                        )
                        FilterChip(
                            selected = playerGame.twoMinuteSuspensions > 0,
                            onClick = {
                                val next = (playerGame.twoMinuteSuspensions + 1) % 3
                                onUpdateDisciplinary(next, playerGame.yellowCard, playerGame.redCard, playerGame.blueCard)
                            },
                            label = { Text("2Min: ${playerGame.twoMinuteSuspensions}") }
                        )
                    }
                } else {
                    ReadOnlyDisciplinaryRow(playerGame = playerGame)
                }
            }

            // Vorhandene Timeouts auflisten
            if (coachStatRows.isEmpty()) {
                Text(
                    text = "Keine Auszeiten eingetragen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                coachStatRows.forEachIndexed { idx, cs ->
                    val timeout = allTimeouts.find { it.timeoutId == cs.timeoutId }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Auszeit ${idx + 1}: ${timeout?.timeoutReason ?: "Team-Timeout"}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (isEditMode) {
                                IconButton(onClick = { onRemoveTimeout(cs.coachGamestatsId) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Timeout löschen")
                                }
                            }
                        }
                    }
                }
            }

            // Neues Timeout eintragen (nur im Bearbeitungsmodus)
            if (isEditMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = timeoutReasonDraft,
                        onValueChange = { timeoutReasonDraft = it },
                        label = { Text("Timeout-Grund (timeout_reason, z.B. '45. Min: 7-gegen-6 Angriff')") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            onAddTimeout(timeoutReasonDraft)
                            timeoutReasonDraft = ""
                        }
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Timeout nehmen")
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadOnlyDisciplinaryRow(playerGame: PlayerGame) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (playerGame.twoMinuteSuspensions > 0) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ) {
            Text(
                text = "2Min: ${playerGame.twoMinuteSuspensions}",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (playerGame.yellowCard) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "Gelb",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (playerGame.redCard) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "Rot",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        if (playerGame.blueCard) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "Blau",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun StatStepperBox(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    isEditMode: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (isEditMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.clickable(onClick = onDecrement)
                    ) {
                        Text(
                            text = " - ",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "$value",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onIncrement)
                    ) {
                        Text(
                            text = " + ",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DirectScoreEditDialog(
    game: Game,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Int) -> Unit
) {
    var homeScore by remember { mutableStateOf(game.homeTeamScore.toString()) }
    var awayScore by remember { mutableStateOf(game.awayTeamScore.toString()) }
    var homeHalf by remember { mutableStateOf(game.homeTeamHalftimeScore.toString()) }
    var awayHalf by remember { mutableStateOf(game.awayTeamHalftimeScore.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spielstand direkt bearbeiten") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = homeScore,
                        onValueChange = { homeScore = it },
                        label = { Text("Endstand Heim") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = awayScore,
                        onValueChange = { awayScore = it },
                        label = { Text("Endstand Gast") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = homeHalf,
                        onValueChange = { homeHalf = it },
                        label = { Text("Halbzeit Heim") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = awayHalf,
                        onValueChange = { awayHalf = it },
                        label = { Text("Halbzeit Gast") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        homeScore.toIntOrNull() ?: game.homeTeamScore,
                        awayScore.toIntOrNull() ?: game.awayTeamScore,
                        homeHalf.toIntOrNull() ?: game.homeTeamHalftimeScore,
                        awayHalf.toIntOrNull() ?: game.awayTeamHalftimeScore
                    )
                }
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
