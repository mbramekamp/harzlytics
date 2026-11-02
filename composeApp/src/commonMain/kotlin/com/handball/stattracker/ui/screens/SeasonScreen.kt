package com.handball.stattracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.*
import com.handball.stattracker.repository.HandballRepository
import kotlinx.coroutines.delay

/**
 * Saison-Screen + Spielbericht & Statistik-Dashboard pro Spiel:
 * - Auswahl der Saison + direktes Anlegen einer 'Neuen Saison'
 * - Übersicht aller Spiele der Saison (eigene Spiele & Liga-Fremdpaarungen)
 * - Spiel-Detailansicht mit:
 *   - Live-Spieluhr (00:00–60:00), Schnell-Zeitsteuerung & automatischem 2-Minuten-Straf-Countdown
 *   - Side-by-Side Aufstellungs-Ansicht:
 *     - LINKS: Die 7 aktiven Spieler 'AUF DEM FELD' (Statistiken editierbar, Position in der Karte ausgewiesen)
 *     - RECHTS: Die Spieler 'AUF DER BANK' (inaktiver Zustand, Statistiken gesperrt)
 *   - Direkter 2-Klick-Wechsel zwischen rechter Bank-Spalte und linker Feld-Spalte
 *   - Eigener Tab 'Spielprotokoll & Torverlauf' für die chronologischen Game-Logs und den Torfolge-Graphen
 */
@Composable
fun SeasonScreen(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    selectedSeasonId: Int,
    selectedGameId: Int?,
    isWideScreen: Boolean = true,
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
            isWideScreen = isWideScreen,
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
            .padding(if (isWideScreen) 20.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Saison-Auswahlleiste + Neue Saison / Neues Spiel Buttons
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(if (isWideScreen) 16.dp else 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isWideScreen) {
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
                                    text = "${activeSeason.startOfSeason} – ${activeSeason.endOfSeason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onOpenNewSeasonDialog) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Saison")
                            }
                            Button(onClick = onOpenNewGameDialog) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Spiel")
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column {
                            Text(
                                text = activeSeason?.division ?: "Saison-Übersicht",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            if (activeSeason != null) {
                                Text(
                                    text = "${activeSeason.startOfSeason} – ${activeSeason.endOfSeason}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = onOpenNewSeasonDialog
                            ) {
                                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Saison")
                            }
                            Button(
                                modifier = Modifier.weight(1f),
                                onClick = onOpenNewGameDialog
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Spiel")
                            }
                        }
                    }
                }

                // Saison-Chips
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
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
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = filterMode == 0,
                        onClick = { filterMode = 0 },
                        label = { Text("Alle") }
                    )
                    FilterChip(
                        selected = filterMode == 1,
                        onClick = { filterMode = 1 },
                        label = { Text("Eigene Spiele") }
                    )
                    FilterChip(
                        selected = filterMode == 2,
                        onClick = { filterMode = 2 },
                        label = { Text("Liga-Spiele") }
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
                val gamePgs = snapshot.playerGames.filter { it.gameId == game.gameId }
                val squadCount = gamePgs.size
                val onCourtCount = gamePgs.count { pg ->
                    pg.isOnCourt && snapshot.players.find { it.playerId == pg.playerId }?.role != PlayerRole.COACH
                }
                val logCount = snapshot.gameLogs.count { it.gameId == game.gameId }

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
                                            if (game.isHomeGame) "HEIM" else "AUSWÄRTS"
                                        } else "LIGA",
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
                                    text = "$onCourtCount/7 aktiv • $squadCount Kader • $logCount Events",
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

        Spacer(Modifier.height(24.dp))
    }
}

/**
 * Spiel-Detailansicht & Statistik-Dashboard:
 * - Tab 0 (Aufstellung & Statistiken):
 *   - Links: Die 7 aktiven Spieler 'AUF DEM FELD' (Statistiken im Bearbeitungsmodus editierbar)
 *   - Rechts: Die Spieler 'AUF DER BANK' (inaktiver Zustand, Statistiken gesperrt, 1. Klick startet Einwechslung)
 * - Tab 1 (Spielprotokoll & Torverlauf):
 *   - Torfolge-Graph (0'–60') sowie chronologisches Spielprotokoll (Wechsel, Tore, Paraden, Strafen, Auszeiten)
 * - Tab 2 (Trainer & Auszeiten):
 *   - Trainerbank, Team-Timeouts (TTO) und Bank-Strafen
 * - Tab 3 (nur im Bearbeitungsmodus):
 *   - Spieltagskader nominieren (PLAYER_GAME)
 */
@Composable
private fun GameInteractiveDashboard(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onNavigateToPlayer: (Int) -> Unit
) {
    var isEditMode by remember(game.gameId) { mutableStateOf(false) }
    var isClockRunning by remember(game.gameId) { mutableStateOf(false) }
    // 0 = Aufstellung: Feld (7) & Bank, 1 = Spielprotokoll & Torverlauf, 2 = Trainer & Auszeiten, 3 = Kader nominieren (nur im Edit-Modus)
    var activeTab by remember(game.gameId) { mutableStateOf(0) }
    var autoSyncScore by remember { mutableStateOf(true) }
    var showDirectScoreDialog by remember { mutableStateOf(false) }

    // Für den 2-Klick-Wechsel-Workflow: ID des ausgewählten Bank-Spielers (rechts), der gegen einen Feld-Spieler (links) eingewechselt werden soll
    var pendingSubInPlayerId by remember(game.gameId) { mutableStateOf<Int?>(null) }

    // Live-Spieluhr-Ticker (läuft im Sekundentakt, solange aktiv und < 60:00)
    LaunchedEffect(isClockRunning, game.gameId) {
        while (isClockRunning) {
            delay(1000L)
            val current = repository.snapshot.value.games.find { it.gameId == game.gameId }
            if (current == null || current.elapsedSeconds >= 3600) {
                isClockRunning = false
                break
            }
            repository.advanceGameClock(game.gameId, 1)
        }
    }

    LaunchedEffect(isEditMode) {
        if (!isEditMode) {
            isClockRunning = false
            pendingSubInPlayerId = null
            if (activeTab == 3) activeTab = 0
        }
    }

    val gamePlayerGames = remember(snapshot.playerGames, game.gameId) {
        snapshot.playerGames.filter { it.gameId == game.gameId }
    }

    val gameLogs = remember(snapshot.gameLogs, game.gameId) {
        repository.getGameLogs(game.gameId, snapshot)
    }

    // Alle Spieler des Spiels (Torhüter zuerst, danach Feldspieler nach Trikotnummer; Position steht auf jeder Karte)
    val allPlayerParticipants = remember(gamePlayerGames, snapshot.players) {
        gamePlayerGames.mapNotNull { pg ->
            val p = snapshot.players.find { it.playerId == pg.playerId && it.role != PlayerRole.COACH }
            if (p != null) pg to p else null
        }.sortedWith(
            compareBy<Pair<PlayerGame, Player>> { it.second.role != PlayerRole.GOALKEEPER }
                .thenBy { it.second.jerseyNumberHomeKit ?: 99 }
        )
    }

    val onCourtParticipants = remember(allPlayerParticipants) {
        allPlayerParticipants.filter { it.first.isOnCourt }
    }

    val benchParticipants = remember(allPlayerParticipants) {
        allPlayerParticipants.filter { !it.first.isOnCourt }
    }

    val coachParticipants = remember(gamePlayerGames, snapshot.players) {
        gamePlayerGames.mapNotNull { pg ->
            val p = snapshot.players.find { it.playerId == pg.playerId && it.role == PlayerRole.COACH }
            if (p != null) pg to p else null
        }
    }

    val activeSuspensions = remember(allPlayerParticipants, game.elapsedSeconds) {
        allPlayerParticipants.filter { (pg, _) -> pg.hasActiveSuspension(game.elapsedSeconds) }
    }

    // Bereinige pendingSubInPlayerId, falls der Spieler inzwischen auf dem Feld oder gesperrt ist
    val pendingSubInPair = benchParticipants.find { it.second.playerId == pendingSubInPlayerId }
    LaunchedEffect(pendingSubInPair) {
        if (pendingSubInPlayerId != null && pendingSubInPair == null) {
            pendingSubInPlayerId = null
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isSideBySideColumns = maxWidth >= 780.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(if (isWideScreen) 18.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Scoreboard + Match Clock Card
            ScoreboardAndMatchClockCard(
                repository = repository,
                snapshot = snapshot,
                game = game,
                isWideScreen = isWideScreen,
                isEditMode = isEditMode,
                isClockRunning = isClockRunning,
                autoSyncScore = autoSyncScore,
                onCourtCount = onCourtParticipants.size,
                activeSuspensions = activeSuspensions,
                onBack = onBack,
                onToggleEditMode = { isEditMode = !isEditMode },
                onToggleClockRunning = { isClockRunning = !isClockRunning },
                onAdjustClock = { deltaSec -> repository.advanceGameClock(game.gameId, deltaSec) },
                onSetClock = { targetSec -> repository.setGameClockSeconds(game.gameId, targetSec) },
                onToggleAutoSync = { autoSyncScore = it },
                onOpenDirectScoreDialog = { showDirectScoreDialog = true },
                onApplyStartingSeven = {
                    pendingSubInPlayerId = null
                    repository.applyRecommendedStartingSevenToGame(game.gameId)
                }
            )

            if (!game.isOwnTeamGame) {
                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Ligaspiel zweier Staffel-Gegner.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                // 2. Tab-Auswahl
                val maxTabIdx = if (isEditMode) 3 else 2
                if (isWideScreen) {
                    SecondaryTabRow(selectedTabIndex = activeTab.coerceIn(0, maxTabIdx)) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("Aufstellung (${onCourtParticipants.size}/7)") }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Verlauf & Protokoll (${gameLogs.size})") }
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = { Text("Trainer & Auszeiten (${coachParticipants.size})") }
                        )
                        if (isEditMode) {
                            Tab(
                                selected = activeTab == 3,
                                onClick = { activeTab = 3 },
                                text = { Text("Kader (${gamePlayerGames.size})") }
                            )
                        }
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = activeTab.coerceIn(0, maxTabIdx),
                        edgePadding = 8.dp
                    ) {
                        Tab(
                            selected = activeTab == 0,
                            onClick = { activeTab = 0 },
                            text = { Text("Aufstellung (${onCourtParticipants.size}/7)") }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Verlauf & Protokoll (${gameLogs.size})") }
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = { Text("Trainer & Auszeiten (${coachParticipants.size})") }
                        )
                        if (isEditMode) {
                            Tab(
                                selected = activeTab == 3,
                                onClick = { activeTab = 3 },
                                text = { Text("Kader (${gamePlayerGames.size})") }
                            )
                        }
                    }
                }

                // 3. Inhalt je nach ausgewähltem Tab
                when (activeTab.coerceIn(0, maxTabIdx)) {
                    0 -> {
                        SideBySideLineupAndBenchSection(
                            repository = repository,
                            snapshot = snapshot,
                            game = game,
                            isSideBySide = isSideBySideColumns,
                            isEditMode = isEditMode,
                            autoSyncScore = autoSyncScore,
                            onCourtParticipants = onCourtParticipants,
                            benchParticipants = benchParticipants,
                            pendingSubInPair = pendingSubInPair,
                            onSelectBenchForSub = { playerId ->
                                pendingSubInPlayerId = if (pendingSubInPlayerId == playerId) null else playerId
                            },
                            onCompleteSubWithCourtPlayer = { outPlayerId ->
                                val inId = pendingSubInPlayerId ?: return@SideBySideLineupAndBenchSection
                                repository.substitutePlayer(game.gameId, playerInId = inId, playerOutId = outPlayerId)
                                pendingSubInPlayerId = null
                            },
                            onPutBenchPlayerDirectlyOnCourt = { inPlayerId ->
                                repository.substitutePlayer(game.gameId, playerInId = inPlayerId, playerOutId = null)
                                pendingSubInPlayerId = null
                            },
                            onMoveCourtPlayerToBench = { outPlayerId ->
                                repository.movePlayerToBench(game.gameId, outPlayerId)
                            },
                            onApplyStartingSeven = {
                                pendingSubInPlayerId = null
                                repository.applyRecommendedStartingSevenToGame(game.gameId)
                            },
                            onNavigateToPlayer = onNavigateToPlayer,
                            onEnterEditAndManageSquad = {
                                isEditMode = true
                                activeTab = 3
                            }
                        )
                    }

                    1 -> {
                        GameLogAndProgressionTabSection(
                            repository = repository,
                            snapshot = snapshot,
                            game = game,
                            logs = gameLogs,
                            isEditMode = isEditMode,
                            onNavigateToPlayer = onNavigateToPlayer
                        )
                    }

                    2 -> {
                        if (coachParticipants.isEmpty()) {
                            EmptySquadHint(
                                isEditMode = isEditMode,
                                onEnterEditAndManageSquad = {
                                    isEditMode = true
                                    activeTab = 3
                                }
                            )
                        } else {
                            coachParticipants.forEach { (pg, coach) ->
                                val coachStatRows = snapshot.coachStats.filter { it.playerGameId == pg.playerGameId }
                                CoachTimeoutTrackerCard(
                                    coach = coach,
                                    playerGame = pg,
                                    coachStatRows = coachStatRows,
                                    allTimeouts = snapshot.timeouts,
                                    isEditMode = isEditMode,
                                    onAddTimeout = { reason -> repository.addCoachTimeout(pg.playerGameId, reason) },
                                    onRemoveTimeout = { coachGamestatsId -> repository.removeCoachTimeout(coachGamestatsId) },
                                    onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                                        repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                                    }
                                )
                            }
                        }
                    }

                    3 -> {
                        SquadNominationTabSection(
                            repository = repository,
                            snapshot = snapshot,
                            game = game,
                            gamePlayerGames = gamePlayerGames
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))
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
private fun ScoreboardAndMatchClockCard(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    isWideScreen: Boolean,
    isEditMode: Boolean,
    isClockRunning: Boolean,
    autoSyncScore: Boolean,
    onCourtCount: Int,
    activeSuspensions: List<Pair<PlayerGame, Player>>,
    onBack: () -> Unit,
    onToggleEditMode: () -> Unit,
    onToggleClockRunning: () -> Unit,
    onAdjustClock: (Int) -> Unit,
    onSetClock: (Int) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onOpenDirectScoreDialog: () -> Unit,
    onApplyStartingSeven: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Obere Aktionsleiste
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Zurück")
                    }

                    Text(
                        text = "${game.gameDate} • ${game.location} • ${game.gameType}",
                        style = MaterialTheme.typography.labelLarge
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (isEditMode) {
                            if (game.isOwnTeamGame) {
                                OutlinedButton(onClick = onApplyStartingSeven) {
                                    Icon(Icons.Default.Groups, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Startsieben")
                                }
                            }
                            OutlinedButton(onClick = onOpenDirectScoreDialog) {
                                Icon(Icons.Default.EditNote, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Spielstand")
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
                                onClick = onToggleEditMode,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Fertig")
                            }
                        } else {
                            Button(onClick = onToggleEditMode) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Bearbeiten")
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Zurück")
                        }

                        if (isEditMode) {
                            Button(
                                onClick = onToggleEditMode,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.tertiary,
                                    contentColor = MaterialTheme.colorScheme.onTertiary
                                )
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Fertig")
                            }
                        } else {
                            Button(onClick = onToggleEditMode) {
                                Icon(Icons.Default.Edit, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Bearbeiten")
                            }
                        }
                    }

                    Text(
                        text = "${game.gameDate} • ${game.location} • ${game.gameType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )

                    if (isEditMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (game.isOwnTeamGame) {
                                OutlinedButton(onClick = onApplyStartingSeven) {
                                    Icon(Icons.Default.Groups, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Startsieben")
                                }
                            }
                            OutlinedButton(onClick = onOpenDirectScoreDialog) {
                                Icon(Icons.Default.EditNote, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Spielstand")
                            }
                            OutlinedButton(
                                onClick = {
                                    repository.deleteGame(game.gameId)
                                    onBack()
                                }
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Spiel löschen")
                                Spacer(Modifier.width(4.dp))
                                Text("Löschen")
                            }
                        }
                    }
                }
            }

            // Haupt-Anzeigetafel für Heim & Gast, Halbzeit und Match-Clock (00:00–60:00)
            val opp1Name = snapshot.opponentTeams.find { it.opponentTeamId == game.opponentId }?.opponentName ?: "Gegner"
            val opp2Name = snapshot.opponentTeams.find { it.opponentTeamId == game.secondOpponentId }?.opponentName ?: "Gegner 2"
            val homeName = if (game.isOwnTeamGame) {
                if (game.isHomeGame) snapshot.ownClubName else opp1Name
            } else opp1Name
            val awayName = if (game.isOwnTeamGame) {
                if (game.isHomeGame) opp1Name else snapshot.ownClubName
            } else opp2Name

            if (isWideScreen) {
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

                    // Spieluhr (00:00 - 60:00) + Halbzeitstand in der Mitte
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = game.elapsedFormatted,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "${game.currentMinute}. Min",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isEditMode) {
                                    FilledIconButton(
                                        onClick = onToggleClockRunning,
                                        colors = IconButtonDefaults.filledIconButtonColors(
                                            containerColor = if (isClockRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                        )
                                    ) {
                                        Icon(
                                            imageVector = if (isClockRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                            contentDescription = if (isClockRunning) "Uhr pausieren" else "Uhr starten",
                                            tint = Color.White
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = "HZ ${game.homeTeamHalftimeScore} : ${game.awayTeamHalftimeScore}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
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
            } else {
                // Mobile: Spieluhr & Halbzeit oben zentriert, darunter Heim & Gast nebeneinander
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = game.elapsedFormatted,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "(${game.currentMinute}. Min)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "HZ ${game.homeTeamHalftimeScore} : ${game.awayTeamHalftimeScore}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isEditMode) {
                                FilledIconButton(
                                    onClick = onToggleClockRunning,
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = if (isClockRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary
                                    )
                                ) {
                                    Icon(
                                        imageVector = if (isClockRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = if (isClockRunning) "Uhr pausieren" else "Uhr starten",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Heim-Team Panel
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("HEIM", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = homeName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-1", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Text(
                                        text = "${game.homeTeamScore}",
                                        style = MaterialTheme.typography.headlineMedium,
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
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+1", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }

                        // Gast-Team Panel
                        Surface(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("GAST", style = MaterialTheme.typography.labelSmall)
                                Text(
                                    text = awayName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-1", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                    Text(
                                        text = "${game.awayTeamScore}",
                                        style = MaterialTheme.typography.headlineMedium,
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
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+1", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Schnell-Steuerung für die Spieluhr (-1m, -10s, +10s, +1m, HZ 30:00, Stand -> HZ)
            if (isEditMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AssistChip(onClick = { onAdjustClock(-60) }, label = { Text("-1m") })
                    AssistChip(onClick = { onAdjustClock(-10) }, label = { Text("-10s") })
                    AssistChip(onClick = { onAdjustClock(10) }, label = { Text("+10s") })
                    AssistChip(onClick = { onAdjustClock(60) }, label = { Text("+1m") })
                    AssistChip(onClick = { onSetClock(1800) }, label = { Text("HZ 30:00") })
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
                        label = { Text("Stand -> HZ") }
                    )
                }
            }

            // Laufende 2-Minuten-Zeitstrafen als Live-Countdown-Anzeige
            if (activeSuspensions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Aktive 2-Minuten-Strafen:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    activeSuspensions.forEach { (pg, p) ->
                        val rem = pg.remainingSuspensionSeconds(game.elapsedSeconds)
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "#${p.jerseyNumberHomeKit ?: "-"} ${p.lastName}: ${formatGameClockSeconds(rem)} Rest",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
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
                        onCheckedChange = onToggleAutoSync
                    )
                    Text(
                        text = "Spielstand & +/- automatisch aktualisieren",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

/**
 * Side-by-Side Aufstellungs-Ansicht:
 * - LINKS: Die 7 aktiven Spieler 'AUF DEM FELD' (Statistiken editierbar)
 * - RECHTS: Die Spieler 'AUF DER BANK' (inaktiver Zustand, Statistiken gesperrt, Klick startet Einwechslung)
 */
@Composable
private fun SideBySideLineupAndBenchSection(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    isSideBySide: Boolean,
    isEditMode: Boolean,
    autoSyncScore: Boolean,
    onCourtParticipants: List<Pair<PlayerGame, Player>>,
    benchParticipants: List<Pair<PlayerGame, Player>>,
    pendingSubInPair: Pair<PlayerGame, Player>?,
    onSelectBenchForSub: (Int) -> Unit,
    onCompleteSubWithCourtPlayer: (Int) -> Unit,
    onPutBenchPlayerDirectlyOnCourt: (Int) -> Unit,
    onMoveCourtPlayerToBench: (Int) -> Unit,
    onApplyStartingSeven: () -> Unit,
    onNavigateToPlayer: (Int) -> Unit,
    onEnterEditAndManageSquad: () -> Unit
) {
    if (onCourtParticipants.isEmpty() && benchParticipants.isEmpty()) {
        EmptySquadHint(isEditMode = isEditMode, onEnterEditAndManageSquad = onEnterEditAndManageSquad)
        return
    }

    val activeFieldCount = remember(onCourtParticipants) {
        onCourtParticipants.count { it.second.role == PlayerRole.FIELD_PLAYER }
    }
    val activeKeeperCount = remember(onCourtParticipants) {
        onCourtParticipants.count { it.second.role == PlayerRole.GOALKEEPER }
    }

    // Aktives Wechsel-Banner über beiden Spalten, wenn rechts ein Bank-Spieler angeklickt wurde
    if (isEditMode && pendingSubInPair != null) {
        val (_, benchPlayer) = pendingSubInPair
        val benchPosName = snapshot.positions.find { it.positionId == benchPlayer.positionId }?.positionName
            ?: benchPlayer.role.label
        val canDirectEnter = repository.canSubstituteOrEnterCourt(
            gameId = game.gameId,
            playerInId = benchPlayer.playerId,
            playerOutId = null,
            state = snapshot
        )
        val ruleHintText = when {
            benchPlayer.role == PlayerRole.GOALKEEPER && activeKeeperCount >= 1 ->
                "Nur gegen den aktiven Torwart wechselbar (max. 1 TW)"
            benchPlayer.role == PlayerRole.GOALKEEPER && activeKeeperCount == 0 ->
                "Feldspieler links auswählen (Wechsel auf 6+1)"
            else ->
                "Spieler links zum Tauschen auswählen"
        }

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Column {
                        Text(
                            text = "Einwechslung: #${benchPlayer.jerseyNumberHomeKit ?: "-"} ${benchPlayer.fullName} ($benchPosName)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = ruleHintText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (canDirectEnter) {
                        Button(
                            onClick = { onPutBenchPlayerDirectlyOnCourt(benchPlayer.playerId) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Icon(Icons.Default.PersonAddAlt1, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Aufs Feld (${onCourtParticipants.size + 1}/7)")
                        }
                    }
                    OutlinedButton(onClick = { onSelectBenchForSub(benchPlayer.playerId) }) {
                        Text("Abbrechen")
                    }
                }
            }
        }
    }

    @Composable
    fun renderActiveOnCourtColumn(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSideBySide) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsHandball,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "AUF DEM FELD (${onCourtParticipants.size}/7)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (activeFieldCount == 7 && activeKeeperCount == 0) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            ) {
                                Text(
                                    text = "$activeFieldCount Feld + $activeKeeperCount TW",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        if (isEditMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = { repository.applyRecommendedStartingSevenToGame(game.gameId, useSeventhFieldPlayer = false) }
                                ) {
                                    Text("6+1")
                                }
                                OutlinedButton(
                                    onClick = { repository.applyRecommendedStartingSevenToGame(game.gameId, useSeventhFieldPlayer = true) }
                                ) {
                                    Text("7 Feld")
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsHandball,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "AUF DEM FELD (${onCourtParticipants.size}/7)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (activeFieldCount == 7 && activeKeeperCount == 0) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            ) {
                                Text(
                                    text = "$activeFieldCount Feld + $activeKeeperCount TW",
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        if (isEditMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    onClick = { repository.applyRecommendedStartingSevenToGame(game.gameId, useSeventhFieldPlayer = false) }
                                ) {
                                    Text("6+1 Startsieben", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    onClick = { repository.applyRecommendedStartingSevenToGame(game.gameId, useSeventhFieldPlayer = true) }
                                ) {
                                    Text("7 Feldspieler", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (onCourtParticipants.isEmpty()) {
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Noch keine aktiven Spieler auf dem Feld.")
                        if (isEditMode) {
                            Button(onClick = onApplyStartingSeven) {
                                Text("Empfohlene Startsieben (6+1) aufs Feld")
                            }
                        }
                    }
                }
            } else {
                onCourtParticipants.forEach { (pg, person) ->
                    val posName = snapshot.positions.find { it.positionId == person.positionId }?.positionName
                        ?: person.role.label
                    val canSwapWithPending = pendingSubInPair != null && repository.canSubstituteOrEnterCourt(
                        gameId = game.gameId,
                        playerInId = pendingSubInPair.second.playerId,
                        playerOutId = person.playerId,
                        state = snapshot
                    )
                    if (person.role == PlayerRole.GOALKEEPER) {
                        val stats = snapshot.goalKeeperStats.find { it.playerGameId == pg.playerGameId }
                            ?: GoalKeeperGameStats(0, pg.playerGameId)
                        GoalKeeperStatTrackerCard(
                            keeper = person,
                            positionName = posName,
                            playerGame = pg,
                            stats = stats,
                            gameElapsedSeconds = game.elapsedSeconds,
                            isWideScreen = isSideBySide,
                            isEditMode = isEditMode,
                            pendingSubInPlayer = pendingSubInPair?.second,
                            canSwapWithPending = canSwapWithPending,
                            isSelectedForSubIn = false,
                            canEnterDirectly = false,
                            onOpenProfile = { onNavigateToPlayer(person.playerId) },
                            onSubstitutionClick = {
                                if (pendingSubInPair != null) {
                                    if (canSwapWithPending) onCompleteSubWithCourtPlayer(person.playerId)
                                } else {
                                    onMoveCourtPlayerToBench(person.playerId)
                                }
                            },
                            onEnterCourtDirectly = {},
                            onAdjustPlusMinus = { delta -> repository.adjustPlayerPlusMinus(pg.playerGameId, delta) },
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
                    } else {
                        val stats = snapshot.fieldPlayerStats.find { it.playerGameId == pg.playerGameId }
                            ?: FieldPlayerGameStats(0, pg.playerGameId)
                        FieldPlayerStatTrackerCard(
                            player = person,
                            positionName = posName,
                            playerGame = pg,
                            stats = stats,
                            gameElapsedSeconds = game.elapsedSeconds,
                            isWideScreen = isSideBySide,
                            isEditMode = isEditMode,
                            pendingSubInPlayer = pendingSubInPair?.second,
                            canSwapWithPending = canSwapWithPending,
                            isSelectedForSubIn = false,
                            canEnterDirectly = false,
                            onOpenProfile = { onNavigateToPlayer(person.playerId) },
                            onSubstitutionClick = {
                                if (pendingSubInPair != null) {
                                    if (canSwapWithPending) onCompleteSubWithCourtPlayer(person.playerId)
                                } else {
                                    onMoveCourtPlayerToBench(person.playerId)
                                }
                            },
                            onEnterCourtDirectly = {},
                            onAdjustPlusMinus = { delta -> repository.adjustPlayerPlusMinus(pg.playerGameId, delta) },
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
            }
        }
    }

    @Composable
    fun renderBenchColumn(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventSeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "BANK (${benchParticipants.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (benchParticipants.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Keine Spieler auf der Bank.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                benchParticipants.forEach { (pg, person) ->
                    val posName = snapshot.positions.find { it.positionId == person.positionId }?.positionName
                        ?: person.role.label
                    val isSelectedSubIn = pendingSubInPair?.second?.playerId == person.playerId
                    val canEnterDirectly = repository.canSubstituteOrEnterCourt(
                        gameId = game.gameId,
                        playerInId = person.playerId,
                        playerOutId = null,
                        state = snapshot
                    )

                    if (person.role == PlayerRole.GOALKEEPER) {
                        val stats = snapshot.goalKeeperStats.find { it.playerGameId == pg.playerGameId }
                            ?: GoalKeeperGameStats(0, pg.playerGameId)
                        GoalKeeperStatTrackerCard(
                            keeper = person,
                            positionName = posName,
                            playerGame = pg,
                            stats = stats,
                            gameElapsedSeconds = game.elapsedSeconds,
                            isWideScreen = isSideBySide,
                            isEditMode = isEditMode,
                            pendingSubInPlayer = null,
                            canSwapWithPending = false,
                            isSelectedForSubIn = isSelectedSubIn,
                            canEnterDirectly = canEnterDirectly,
                            onOpenProfile = { onNavigateToPlayer(person.playerId) },
                            onSubstitutionClick = { onSelectBenchForSub(person.playerId) },
                            onEnterCourtDirectly = { onPutBenchPlayerDirectlyOnCourt(person.playerId) },
                            onAdjustPlusMinus = { delta -> repository.adjustPlayerPlusMinus(pg.playerGameId, delta) },
                            onUpdateStats = {},
                            onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                                repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                            }
                        )
                    } else {
                        val stats = snapshot.fieldPlayerStats.find { it.playerGameId == pg.playerGameId }
                            ?: FieldPlayerGameStats(0, pg.playerGameId)
                        FieldPlayerStatTrackerCard(
                            player = person,
                            positionName = posName,
                            playerGame = pg,
                            stats = stats,
                            gameElapsedSeconds = game.elapsedSeconds,
                            isWideScreen = isSideBySide,
                            isEditMode = isEditMode,
                            pendingSubInPlayer = null,
                            canSwapWithPending = false,
                            isSelectedForSubIn = isSelectedSubIn,
                            canEnterDirectly = canEnterDirectly,
                            onOpenProfile = { onNavigateToPlayer(person.playerId) },
                            onSubstitutionClick = { onSelectBenchForSub(person.playerId) },
                            onEnterCourtDirectly = { onPutBenchPlayerDirectlyOnCourt(person.playerId) },
                            onAdjustPlusMinus = { delta -> repository.adjustPlayerPlusMinus(pg.playerGameId, delta) },
                            onUpdateStats = {},
                            onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                                repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                            }
                        )
                    }
                }
            }
        }
    }

    if (isSideBySide) {
        // Links: 7 aktive Spieler auf dem Feld | Rechts: Spieler auf der Bank
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            renderActiveOnCourtColumn(modifier = Modifier.weight(1.15f))
            renderBenchColumn(modifier = Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            renderActiveOnCourtColumn(modifier = Modifier.fillMaxWidth())
            renderBenchColumn(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SquadNominationTabSection(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    gamePlayerGames: List<PlayerGame>
) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Spieltagskader (${gamePlayerGames.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            snapshot.players.forEach { person ->
                val pg = gamePlayerGames.find { it.playerId == person.playerId }
                val isNominated = pg != null
                val posName = snapshot.positions.find { it.positionId == person.positionId }?.positionName
                    ?: person.role.label
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
                            text = "${person.fullName} ($posName)",
                            fontWeight = if (isNominated) FontWeight.Bold else FontWeight.Normal
                        )
                        if (pg?.isOnCourt == true) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Auf dem Feld",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    if (person.jerseyNumberHomeKit != null) {
                        Text("#${person.jerseyNumberHomeKit}")
                    }
                }
            }
        }
    }
}

/**
 * Eigener Tab für den Torverlauf-Graphen und die chronologischen Spiel-Logs (Wechsel, Tore, Paraden, Strafen, Auszeiten).
 */
@Composable
private fun GameLogAndProgressionTabSection(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    logs: List<GameLogEntry>,
    isEditMode: Boolean,
    onNavigateToPlayer: (Int) -> Unit
) {
    // 0 = Alle, 1 = Nur Wechsel, 2 = Tore & Paraden, 3 = Strafen & TTO
    var logFilter by remember { mutableStateOf(0) }

    val filteredLogs = remember(logs, logFilter) {
        logs.filter { entry ->
            when (logFilter) {
                1 -> entry.eventType == GameLogEventType.SUBSTITUTION || entry.eventType == GameLogEventType.LINEUP_SET
                2 -> entry.eventType in setOf(
                    GameLogEventType.GOAL,
                    GameLogEventType.SEVEN_METER_GOAL,
                    GameLogEventType.GOALKEEPER_SAVE,
                    GameLogEventType.GOAL_CONCEDED,
                    GameLogEventType.MISSED_SHOT
                )
                3 -> entry.eventType in setOf(
                    GameLogEventType.TWO_MINUTE_SUSPENSION,
                    GameLogEventType.YELLOW_CARD,
                    GameLogEventType.RED_CARD,
                    GameLogEventType.BLUE_CARD,
                    GameLogEventType.TIMEOUT
                )
                else -> true
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Torverlauf / Spielverlaufs-Graph
        ScoreProgressionMiniChartCard(game = game, logs = logs)

        // 2. Spielprotokoll-Liste (Game Log)
        Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Spielprotokoll",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${logs.count { it.eventType == GameLogEventType.SUBSTITUTION }} Wechsel • ${logs.size} Einträge",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Filter-Chips für das Protokoll
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = logFilter == 0,
                        onClick = { logFilter = 0 },
                        label = { Text("Alle (${logs.size})") }
                    )
                    FilterChip(
                        selected = logFilter == 1,
                        onClick = { logFilter = 1 },
                        label = { Text("Wechsel (${logs.count { it.eventType == GameLogEventType.SUBSTITUTION || it.eventType == GameLogEventType.LINEUP_SET }})") }
                    )
                    FilterChip(
                        selected = logFilter == 2,
                        onClick = { logFilter = 2 },
                        label = { Text("Tore & Paraden") }
                    )
                    FilterChip(
                        selected = logFilter == 3,
                        onClick = { logFilter = 3 },
                        label = { Text("Strafen & TTO") }
                    )
                }

                if (filteredLogs.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Noch keine Protokoll-Einträge in diesem Filter vorhanden.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    filteredLogs.forEach { entry ->
                        GameLogEventRow(
                            entry = entry,
                            isEditMode = isEditMode,
                            onOpenPlayer = { pid -> onNavigateToPlayer(pid) },
                            onDelete = { repository.deleteGameLogEntry(entry.logId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreProgressionMiniChartCard(
    game: Game,
    logs: List<GameLogEntry>
) {
    // 0 = Tordifferenz-Ausschlag (±3 Skala um 0-Linie), 1 = Lauf-Impuls (±3 Serien-Ausschlag)
    var swingMode by remember { mutableStateOf(0) }

    val chronological = remember(logs, game) {
        val base = logs.sortedWith(compareBy<GameLogEntry> { it.gameSecond }.thenBy { it.logId })
        if (base.isEmpty()) {
            listOf(
                Triple(0, 0, 0),
                Triple(1800, game.homeTeamHalftimeScore, game.awayTeamHalftimeScore),
                Triple(3600, game.homeTeamScore, game.awayTeamScore)
            )
        } else {
            buildList {
                add(Triple(0, 0, 0))
                base.forEach { add(Triple(it.gameSecond, it.homeScoreAtEvent, it.awayScoreAtEvent)) }
                if (last().second != game.homeTeamScore || last().third != game.awayTeamScore) {
                    add(Triple(game.elapsedSeconds.coerceAtLeast(last().first), game.homeTeamScore, game.awayTeamScore))
                }
            }
        }
    }

    // Berechnet für jeden Zeitpunkt den Ausschlag im festen Bereich [-3, +3] um die 0-Mittellinie (kein steigender Summen-Graph!)
    val swingPoints = remember(chronological, swingMode) {
        if (swingMode == 0) {
            chronological.map { (sec, hs, ascore) ->
                val clampedSwing = (hs - ascore).coerceIn(-3, 3)
                sec to clampedSwing
            }
        } else {
            buildList {
                var currentSwing = 0
                var prevH = 0
                var prevA = 0
                chronological.forEachIndexed { idx, (sec, hs, ascore) ->
                    if (idx == 0) {
                        add(sec to 0)
                    } else {
                        val dH = hs - prevH
                        val dA = ascore - prevA
                        val delta = dH - dA
                        currentSwing = (currentSwing + delta).coerceIn(-3, 3)
                        add(sec to currentSwing)
                    }
                    prevH = hs
                    prevA = ascore
                }
            }
        }
    }

    val homeColor = MaterialTheme.colorScheme.primary
    val awayColor = MaterialTheme.colorScheme.secondary
    val centerLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    Card(
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Torverlauf (+3 / 0 / -3)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(homeColor, CircleShape))
                        Text("Heim (${game.homeTeamScore})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(awayColor, CircleShape))
                        Text("Gast (${game.awayTeamScore})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = swingMode == 0,
                    onClick = { swingMode = 0 },
                    label = { Text("Tordifferenz") }
                )
                FilterChip(
                    selected = swingMode == 1,
                    onClick = { swingMode = 1 },
                    label = { Text("Tor-Lauf") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(175.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Feste Y-Achsen-Skala links: +3, +2, +1, 0, -1, -2, -3
                Column(
                    modifier = Modifier.fillMaxHeight().padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("+3", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = homeColor)
                    Text("+2", style = MaterialTheme.typography.labelSmall, color = homeColor.copy(alpha = 0.75f))
                    Text("+1", style = MaterialTheme.typography.labelSmall, color = homeColor.copy(alpha = 0.75f))
                    Text("0", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                    Text("-1", style = MaterialTheme.typography.labelSmall, color = awayColor.copy(alpha = 0.75f))
                    Text("-2", style = MaterialTheme.typography.labelSmall, color = awayColor.copy(alpha = 0.75f))
                    Text("-3", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = awayColor)
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxSec = maxOf(3600, game.elapsedSeconds).toFloat()

                        val padL = 6.dp.toPx()
                        val padR = 6.dp.toPx()
                        val padT = 8.dp.toPx()
                        val padB = 8.dp.toPx()
                        val w = (size.width - padL - padR).coerceAtLeast(10f)
                        val h = (size.height - padT - padB).coerceAtLeast(10f)
                        val centerY = padT + h / 2f
                        val halfH = h / 2f

                        // Horizontale Hilfslinien für +3, +2, +1, 0, -1, -2, -3
                        for (level in -3..3) {
                            val y = centerY - (level / 3f) * halfH
                            val isZeroLine = level == 0
                            drawLine(
                                color = if (isZeroLine) centerLineColor else gridColor,
                                start = Offset(padL, y),
                                end = Offset(padL + w, y),
                                strokeWidth = if (isZeroLine) 2.dp.toPx() else 1.dp.toPx(),
                                pathEffect = if (isZeroLine) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                        }

                        // Vertikale Halbzeit-Linie (30:00)
                        val hzX = padL + w * (1800f / maxSec)
                        drawLine(
                            color = centerLineColor.copy(alpha = 0.45f),
                            start = Offset(hzX, padT),
                            end = Offset(hzX, padT + h),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )

                        val pts = swingPoints.map { (sec, swingVal) ->
                            val x = padL + w * (sec.toFloat() / maxSec).coerceIn(0f, 1f)
                            val y = centerY - (swingVal.toFloat().coerceIn(-3f, 3f) / 3f) * halfH
                            Triple(x, y, swingVal)
                        }

                        // Zeichne farbige Ausschlag-Fläche zur 0-Mittellinie sowie die Ausschlag-Kurve
                        for (i in 0 until pts.size - 1) {
                            val (x1, y1, s1) = pts[i]
                            val (x2, y2, s2) = pts[i + 1]
                            val avgSwing = (s1 + s2) / 2f
                            val segColor = when {
                                avgSwing > 0f -> homeColor
                                avgSwing < 0f -> awayColor
                                else -> centerLineColor
                            }

                            val fillPath = Path().apply {
                                moveTo(x1, centerY)
                                lineTo(x1, y1)
                                lineTo(x2, y2)
                                lineTo(x2, centerY)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                color = segColor.copy(alpha = 0.16f)
                            )

                            drawLine(
                                color = segColor,
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Punkte auf dem Ausschlag-Verlauf
                        pts.forEach { (x, y, s) ->
                            val ptColor = when {
                                s > 0 -> homeColor
                                s < 0 -> awayColor
                                else -> centerLineColor
                            }
                            drawCircle(
                                color = ptColor,
                                radius = 4.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }

            // Zeitachsen-Beschriftung (0' bis 60')
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("15:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("30:00 (HZ)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("45:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("60:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GameLogEventRow(
    entry: GameLogEntry,
    isEditMode: Boolean,
    onOpenPlayer: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val badgeColor = when (entry.eventType) {
        GameLogEventType.SUBSTITUTION, GameLogEventType.LINEUP_SET -> MaterialTheme.colorScheme.tertiary
        GameLogEventType.GOAL, GameLogEventType.SEVEN_METER_GOAL -> MaterialTheme.colorScheme.primary
        GameLogEventType.GOALKEEPER_SAVE -> MaterialTheme.colorScheme.tertiary
        GameLogEventType.TWO_MINUTE_SUSPENSION, GameLogEventType.RED_CARD, GameLogEventType.BLUE_CARD -> MaterialTheme.colorScheme.error
        GameLogEventType.YELLOW_CARD, GameLogEventType.TIMEOUT -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                val pid = entry.primaryPlayerId ?: entry.secondaryPlayerId
                if (pid != null) mod.clickable { onOpenPlayer(pid) } else mod
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = entry.timeFormatted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.16f)
            ) {
                Text(
                    text = entry.eventType.label,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }

            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = entry.scoreFormatted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            if (isEditMode) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Eintrag löschen",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
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
    gameElapsedSeconds: Int,
    isWideScreen: Boolean = true,
    isEditMode: Boolean,
    pendingSubInPlayer: Player?,
    canSwapWithPending: Boolean,
    isSelectedForSubIn: Boolean,
    canEnterDirectly: Boolean,
    onOpenProfile: () -> Unit,
    onSubstitutionClick: () -> Unit,
    onEnterCourtDirectly: () -> Unit,
    onAdjustPlusMinus: (Int) -> Unit,
    onUpdateStats: ((FieldPlayerGameStats) -> FieldPlayerGameStats) -> Unit,
    onUpdateDisciplinary: (Int, Boolean, Boolean, Boolean) -> Unit
) {
    val isOnCourt = playerGame.isOnCourt
    // Nur aktive Spieler AUF DEM FELD (linke Seite) können im Bearbeitungsmodus Spielstatistiken hinzufügen!
    val canEditStats = isEditMode && isOnCourt
    var showDirectEdit by remember { mutableStateOf(false) }

    LaunchedEffect(canEditStats) {
        if (!canEditStats) showDirectEdit = false
    }

    val remainingSusp = playerGame.remainingSuspensionSeconds(gameElapsedSeconds)
    val isBlockedFromSubIn = playerGame.isDisqualified || remainingSusp > 0

    val borderStroke = when {
        isSelectedForSubIn -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.tertiary)
        isEditMode && isOnCourt && pendingSubInPlayer != null && canSwapWithPending -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        isOnCourt -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.55f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelectedForSubIn -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                isOnCourt -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isOnCourt || isSelectedForSubIn) 1f else 0.76f)
            .let { mod ->
                if (isEditMode && !isOnCourt && !isBlockedFromSubIn) {
                    mod.clickable { onSubstitutionClick() }
                } else if (isEditMode && isOnCourt && pendingSubInPlayer != null && canSwapWithPending) {
                    mod.clickable { onSubstitutionClick() }
                } else mod
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenProfile)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isOnCourt) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text = "#${player.jerseyNumberHomeKit ?: "-"}",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = player.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Position direkt als Badge in der Spielerkarte
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = positionName,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        val shotPct = if (stats.goalesAttempted > 0) (stats.goalsScored * 100) / stats.goalesAttempted else 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${stats.goalsScored}/${stats.goalesAttempted} ($shotPct%) • ${playerGame.playingTimeFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LineupStatusAndPlusMinusPills(
                                playerGame = playerGame,
                                remainingSuspensionSec = remainingSusp,
                                isEditMode = isEditMode,
                                onAdjustPlusMinus = onAdjustPlusMinus
                            )
                        }
                    }
                }
            }

            // Aktions- & Disziplinar-Leiste (Wechsel-Buttons + 2Min, Gelb, Rot, Blau)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOnCourt) {
                            if (pendingSubInPlayer != null) {
                                if (canSwapWithPending) {
                                    val subBtnText = if (pendingSubInPlayer.role == PlayerRole.GOALKEEPER) {
                                        "Auswechseln → 6+1 (TW #${pendingSubInPlayer.jerseyNumberHomeKit ?: "-"})"
                                    } else {
                                        "Auswechseln für #${pendingSubInPlayer.jerseyNumberHomeKit ?: "-"}"
                                    }
                                    Button(
                                        onClick = onSubstitutionClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(subBtnText)
                                    }
                                } else {
                                    OutlinedButton(onClick = {}, enabled = false) {
                                        Text("Gesperrt (Max. 1 TW)")
                                    }
                                }
                            } else {
                                OutlinedButton(onClick = onSubstitutionClick) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Auf Bank")
                                }
                            }
                        } else if (!isBlockedFromSubIn) {
                            if (isSelectedForSubIn) {
                                Button(
                                    onClick = onSubstitutionClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Text("Links Spieler wählen...")
                                }
                            } else {
                                FilledTonalButton(onClick = onSubstitutionClick) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Einwechseln")
                                }
                            }
                            if (canEnterDirectly) {
                                OutlinedButton(onClick = onEnterCourtDirectly) {
                                    Text("Direkt aufs Feld")
                                }
                            }
                        }

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
                        if (canEditStats) {
                            IconButton(onClick = { showDirectEdit = !showDirectEdit }) {
                                Icon(Icons.Default.EditNote, contentDescription = "Direkte Zahleneingabe")
                            }
                        }
                    }
                } else {
                    ReadOnlyDisciplinaryRow(playerGame = playerGame)
                }
            }

            // Statistik-Werte (nur für aktive Spieler AUF DEM FELD sind +/- Buttons freigeschaltet)
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Tore",
                        value = stats.goalsScored,
                        isEditMode = canEditStats,
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
                        label = "Würfe",
                        value = stats.goalesAttempted,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(goalesAttempted = it.goalesAttempted + 1) } },
                        onDecrement = { onUpdateStats { it.copy(goalesAttempted = (it.goalesAttempted - 1).coerceAtLeast(it.goalsScored)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "7m-Tore",
                        value = stats.penaltiesScored,
                        isEditMode = canEditStats,
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
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(penaltiesAttempted = it.penaltiesAttempted + 1) } },
                        onDecrement = { onUpdateStats { it.copy(penaltiesAttempted = (it.penaltiesAttempted - 1).coerceAtLeast(it.penaltiesScored)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "7m gez.",
                        value = stats.penaltiesDrawn,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(penaltiesDrawn = it.penaltiesDrawn + 1) } },
                        onDecrement = { onUpdateStats { it.copy(penaltiesDrawn = (it.penaltiesDrawn - 1).coerceAtLeast(0)) } }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Assists",
                        value = stats.assists,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(assists = it.assists + 1) } },
                        onDecrement = { onUpdateStats { it.copy(assists = (it.assists - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Steals",
                        value = stats.steals,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(steals = it.steals + 1) } },
                        onDecrement = { onUpdateStats { it.copy(steals = (it.steals - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Blocks",
                        value = stats.blocks,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(blocks = it.blocks + 1) } },
                        onDecrement = { onUpdateStats { it.copy(blocks = (it.blocks - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Tech. Fehler",
                        value = stats.technicalMistakes,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(technicalMistakes = it.technicalMistakes + 1) } },
                        onDecrement = { onUpdateStats { it.copy(technicalMistakes = (it.technicalMistakes - 1).coerceAtLeast(0)) } }
                    )
                }
            } else {
                // Mobile übersichtliche Anordnung
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Tore & Würfe
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Tore",
                            value = stats.goalsScored,
                            isEditMode = canEditStats,
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
                            label = "Würfe",
                            value = stats.goalesAttempted,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(goalesAttempted = it.goalesAttempted + 1) } },
                            onDecrement = { onUpdateStats { it.copy(goalesAttempted = (it.goalesAttempted - 1).coerceAtLeast(it.goalsScored)) } }
                        )
                    }

                    // 7-Meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "7m-Tore",
                            value = stats.penaltiesScored,
                            isEditMode = canEditStats,
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
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(penaltiesAttempted = it.penaltiesAttempted + 1) } },
                            onDecrement = { onUpdateStats { it.copy(penaltiesAttempted = (it.penaltiesAttempted - 1).coerceAtLeast(it.penaltiesScored)) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "7m gez.",
                            value = stats.penaltiesDrawn,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(penaltiesDrawn = it.penaltiesDrawn + 1) } },
                            onDecrement = { onUpdateStats { it.copy(penaltiesDrawn = (it.penaltiesDrawn - 1).coerceAtLeast(0)) } }
                        )
                    }

                    // Spielgestaltung
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Assists",
                            value = stats.assists,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(assists = it.assists + 1) } },
                            onDecrement = { onUpdateStats { it.copy(assists = (it.assists - 1).coerceAtLeast(0)) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Tech. Fehler",
                            value = stats.technicalMistakes,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(technicalMistakes = it.technicalMistakes + 1) } },
                            onDecrement = { onUpdateStats { it.copy(technicalMistakes = (it.technicalMistakes - 1).coerceAtLeast(0)) } }
                        )
                    }

                    // Abwehr
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Steals",
                            value = stats.steals,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(steals = it.steals + 1) } },
                            onDecrement = { onUpdateStats { it.copy(steals = (it.steals - 1).coerceAtLeast(0)) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Blocks",
                            value = stats.blocks,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(blocks = it.blocks + 1) } },
                            onDecrement = { onUpdateStats { it.copy(blocks = (it.blocks - 1).coerceAtLeast(0)) } }
                        )
                    }
                }
            }

            if (canEditStats && showDirectEdit) {
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
                            Text("OK")
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
    positionName: String,
    playerGame: PlayerGame,
    stats: GoalKeeperGameStats,
    gameElapsedSeconds: Int,
    isWideScreen: Boolean = true,
    isEditMode: Boolean,
    pendingSubInPlayer: Player?,
    canSwapWithPending: Boolean,
    isSelectedForSubIn: Boolean,
    canEnterDirectly: Boolean,
    onOpenProfile: () -> Unit,
    onSubstitutionClick: () -> Unit,
    onEnterCourtDirectly: () -> Unit,
    onAdjustPlusMinus: (Int) -> Unit,
    onUpdateStats: ((GoalKeeperGameStats) -> GoalKeeperGameStats) -> Unit,
    onUpdateDisciplinary: (Int, Boolean, Boolean, Boolean) -> Unit
) {
    val isOnCourt = playerGame.isOnCourt
    val canEditStats = isEditMode && isOnCourt
    val remainingSusp = playerGame.remainingSuspensionSeconds(gameElapsedSeconds)
    val isBlockedFromSubIn = playerGame.isDisqualified || remainingSusp > 0

    val borderStroke = when {
        isSelectedForSubIn -> BorderStroke(2.5.dp, MaterialTheme.colorScheme.tertiary)
        isEditMode && isOnCourt && pendingSubInPlayer != null && canSwapWithPending -> BorderStroke(2.dp, MaterialTheme.colorScheme.tertiary)
        isOnCourt -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f))
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isSelectedForSubIn -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
                isOnCourt -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isOnCourt || isSelectedForSubIn) 1f else 0.76f)
            .let { mod ->
                if (isEditMode && !isOnCourt && !isBlockedFromSubIn) {
                    mod.clickable { onSubstitutionClick() }
                } else if (isEditMode && isOnCourt && pendingSubInPlayer != null && canSwapWithPending) {
                    mod.clickable { onSubstitutionClick() }
                } else mod
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpenProfile)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isOnCourt) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text = "#${keeper.jerseyNumberHomeKit ?: "-"}",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = keeper.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = positionName,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        val totalFaced = stats.saves + stats.goalesConceded
                        val savePct = if (totalFaced > 0) (stats.saves * 100) / totalFaced else 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${stats.saves}/$totalFaced ($savePct%) • ${playerGame.playingTimeFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LineupStatusAndPlusMinusPills(
                                playerGame = playerGame,
                                remainingSuspensionSec = remainingSusp,
                                isEditMode = isEditMode,
                                onAdjustPlusMinus = onAdjustPlusMinus
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isOnCourt) {
                            if (pendingSubInPlayer != null) {
                                if (canSwapWithPending) {
                                    val subBtnText = if (pendingSubInPlayer.role == PlayerRole.FIELD_PLAYER) {
                                        "TW raus → 7. Feldspieler (#${pendingSubInPlayer.jerseyNumberHomeKit ?: "-"})"
                                    } else {
                                        "Torwart-Wechsel für #${pendingSubInPlayer.jerseyNumberHomeKit ?: "-"}"
                                    }
                                    Button(
                                        onClick = onSubstitutionClick,
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                    ) {
                                        Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(subBtnText)
                                    }
                                } else {
                                    OutlinedButton(onClick = {}, enabled = false) {
                                        Text("Gesperrt")
                                    }
                                }
                            } else {
                                OutlinedButton(onClick = onSubstitutionClick) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Auf Bank")
                                }
                            }
                        } else if (!isBlockedFromSubIn) {
                            if (isSelectedForSubIn) {
                                Button(
                                    onClick = onSubstitutionClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                                ) {
                                    Text("Links Spieler wählen...")
                                }
                            } else {
                                FilledTonalButton(onClick = onSubstitutionClick) {
                                    Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Einwechseln")
                                }
                            }
                            if (canEnterDirectly) {
                                OutlinedButton(onClick = onEnterCourtDirectly) {
                                    Text("Direkt aufs Feld")
                                }
                            }
                        }

                        FilterChip(
                            selected = playerGame.twoMinuteSuspensions > 0,
                            onClick = {
                                val next = (playerGame.twoMinuteSuspensions + 1) % 4
                                onUpdateDisciplinary(next, playerGame.yellowCard, playerGame.redCard, playerGame.blueCard)
                            },
                            label = { Text("2Min: ${playerGame.twoMinuteSuspensions}") }
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
                    }
                } else {
                    ReadOnlyDisciplinaryRow(playerGame = playerGame)
                }
            }

            // Alle 6 Felder aus GOAL_KEEPER_GAMESTATS (nur im aktiven Feld-Zustand editierbar)
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Paraden",
                        value = stats.saves,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(saves = it.saves + 1) } },
                        onDecrement = { onUpdateStats { it.copy(saves = (it.saves - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Gegentore",
                        value = stats.goalesConceded,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(goalesConceded = it.goalesConceded + 1) } },
                        onDecrement = { onUpdateStats { it.copy(goalesConceded = (it.goalesConceded - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "7m geh.",
                        value = stats.penaltiesSaved,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(penaltiesSaved = it.penaltiesSaved + 1, saves = it.saves + 1) } },
                        onDecrement = { onUpdateStats { it.copy(penaltiesSaved = (it.penaltiesSaved - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "7m-GT",
                        value = stats.penaltiesConceded,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(penaltiesConceded = it.penaltiesConceded + 1, goalesConceded = it.goalesConceded + 1) } },
                        onDecrement = { onUpdateStats { it.copy(penaltiesConceded = (it.penaltiesConceded - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "TG-Pass",
                        value = stats.sucessfulFastBreakPasses,
                        isEditMode = canEditStats,
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
                        label = "TG-Vers.",
                        value = stats.attemptedFastBreakPasses,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(attemptedFastBreakPasses = it.attemptedFastBreakPasses + 1) } },
                        onDecrement = {
                            onUpdateStats {
                                it.copy(attemptedFastBreakPasses = (it.attemptedFastBreakPasses - 1).coerceAtLeast(it.sucessfulFastBreakPasses))
                            }
                        }
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Paraden",
                            value = stats.saves,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(saves = it.saves + 1) } },
                            onDecrement = { onUpdateStats { it.copy(saves = (it.saves - 1).coerceAtLeast(0)) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Gegentore",
                            value = stats.goalesConceded,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(goalesConceded = it.goalesConceded + 1) } },
                            onDecrement = { onUpdateStats { it.copy(goalesConceded = (it.goalesConceded - 1).coerceAtLeast(0)) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "7m geh.",
                            value = stats.penaltiesSaved,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(penaltiesSaved = it.penaltiesSaved + 1, saves = it.saves + 1) } },
                            onDecrement = { onUpdateStats { it.copy(penaltiesSaved = (it.penaltiesSaved - 1).coerceAtLeast(0)) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "7m-GT",
                            value = stats.penaltiesConceded,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(penaltiesConceded = it.penaltiesConceded + 1, goalesConceded = it.goalesConceded + 1) } },
                            onDecrement = { onUpdateStats { it.copy(penaltiesConceded = (it.penaltiesConceded - 1).coerceAtLeast(0)) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "TG-Pass",
                            value = stats.sucessfulFastBreakPasses,
                            isEditMode = canEditStats,
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
                            label = "TG-Vers.",
                            value = stats.attemptedFastBreakPasses,
                            isEditMode = canEditStats,
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
    }
}

@Composable
private fun LineupStatusAndPlusMinusPills(
    playerGame: PlayerGame,
    remainingSuspensionSec: Int,
    isEditMode: Boolean,
    onAdjustPlusMinus: (Int) -> Unit
) {
    when {
        playerGame.isDisqualified -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "DISQUALIFIZIERT",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        remainingSuspensionSec > 0 -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "2Min (${formatGameClockSeconds(remainingSuspensionSec)})",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    val pmColor = when {
        playerGame.plusMinus > 0 -> MaterialTheme.colorScheme.tertiary
        playerGame.plusMinus < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = pmColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (isEditMode && playerGame.isOnCourt) {
                Text(
                    text = "-",
                    modifier = Modifier.clickable { onAdjustPlusMinus(-1) }.padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = pmColor
                )
            }
            Text(
                text = "+/- ${playerGame.plusMinusFormatted}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = pmColor
            )
            if (isEditMode && playerGame.isOnCourt) {
                Text(
                    text = "+",
                    modifier = Modifier.clickable { onAdjustPlusMinus(1) }.padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = pmColor
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
                        text = coach.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${coachStatRows.size} Auszeiten",
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
                            label = { Text("Gelb") }
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
                                text = "TTO ${idx + 1}: ${timeout?.timeoutReason ?: "Team-Timeout"}",
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

            if (isEditMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = timeoutReasonDraft,
                        onValueChange = { timeoutReasonDraft = it },
                        label = { Text("Notiz / Spielsituation") },
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
                        Text("Auszeit")
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
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isEditMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .clickable(onClick = onDecrement),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "$value",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .clickable(onClick = onIncrement),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            } else {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleSmall,
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
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
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
