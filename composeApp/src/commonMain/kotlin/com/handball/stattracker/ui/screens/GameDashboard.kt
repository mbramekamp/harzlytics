package com.handball.stattracker.ui.screens

import com.handball.stattracker.ui.EditorOnly
import com.handball.stattracker.ui.theme.amberContainer
import com.handball.stattracker.ui.theme.seasonChipColors
import com.handball.stattracker.ui.rememberCanEdit
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
import androidx.compose.ui.unit.sp
import com.handball.stattracker.model.*
import com.handball.stattracker.repository.HandballRepository
import kotlinx.coroutines.delay

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
internal fun GameInteractiveDashboard(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    isWideScreen: Boolean,
    onBack: () -> Unit,
    onNavigateToPlayer: (Int) -> Unit
) {
    val canEdit = rememberCanEdit()
    var isEditMode by remember(game.gameId) { mutableStateOf(false) }
    var isClockRunning by remember(game.gameId) { mutableStateOf(false) }
    // 0 = Aufstellung: Feld (7) & Bank, 1 = Spielprotokoll & Torverlauf, 2 = Trainer & Auszeiten, 3 = Kader nominieren (nur im Edit-Modus)
    var activeTab by remember(game.gameId) { mutableStateOf(0) }
    var autoSyncScore by remember { mutableStateOf(true) }
    var showDirectScoreDialog by remember { mutableStateOf(false) }
    // null = geschlossen, false = 6+1, true = 7+0
    var startingSevenDialogVariant by remember(game.gameId) { mutableStateOf<Boolean?>(null) }

    // Angetippter Spieler, für den die Wechsel-Auswahl offen ist
    var substitutionPlayerId by remember(game.gameId) { mutableStateOf<Int?>(null) }
    // Auswahl für einen freien Platz auf dem Feld (Handy, weniger als 7 aktiv)
    var showFreeSlotPicker by remember(game.gameId) { mutableStateOf(false) }

    // Mobile Erfassung (Handy): gewählter Spieler, "Mehr"-Sheet, Rückgängig-Stapel und kurze Bestätigung
    var selectedPlayerId by remember(game.gameId) { mutableStateOf<Int?>(null) }
    var showMoreSheet by remember(game.gameId) { mutableStateOf(false) }
    val undoStack = remember(game.gameId) { mutableStateListOf<DatabaseSnapshot>() }
    var lastActionText by remember(game.gameId) { mutableStateOf<String?>(null) }
    LaunchedEffect(lastActionText) {
        if (lastActionText != null) {
            delay(1800L)
            lastActionText = null
        }
    }
    // Merkt den Stand vor einer Eingabe für "Rückgängig" und zeigt danach kurz, was erfasst wurde
    fun track(confirmation: String, change: () -> Unit) {
        undoStack.add(repository.snapshot.value)
        if (undoStack.size > 30) undoStack.removeAt(0)
        change()
        lastActionText = confirmation
    }

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
            substitutionPlayerId = null
            showFreeSlotPicker = false
            showMoreSheet = false
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

    val courtEntryCandidates = remember(snapshot, game.gameId) {
        repository.courtEntryCandidates(game.gameId, snapshot)
    }

    val activeSuspensions = remember(allPlayerParticipants, game.elapsedSeconds) {
        allPlayerParticipants.filter { (pg, _) -> pg.hasActiveSuspension(game.elapsedSeconds) }
    }

    // Handy: Zeilen + feste Aktionsleiste statt großer Karten
    val useMobileTracker = !isWideScreen && game.isOwnTeamGame
    val selectedPair = onCourtParticipants.find { it.second.playerId == selectedPlayerId }
        ?: onCourtParticipants.firstOrNull { it.second.role == PlayerRole.FIELD_PLAYER }
        ?: onCourtParticipants.firstOrNull()
    val selectedFieldStats = selectedPair?.let { (pg, _) ->
        snapshot.fieldPlayerStats.find { it.playerGameId == pg.playerGameId } ?: FieldPlayerGameStats(0, pg.playerGameId)
    }
    val selectedKeeperStats = selectedPair?.let { (pg, _) ->
        snapshot.goalKeeperStats.find { it.playerGameId == pg.playerGameId } ?: GoalKeeperGameStats(0, pg.playerGameId)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isSideBySideColumns = maxWidth >= 780.dp

        Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(if (isWideScreen) 18.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // 1. Anzeigetafel: Handy kompakt, Tablet/Desktop groß
            if (!isWideScreen) CompactScoreboard(
                repository = repository,
                snapshot = snapshot,
                game = game,
                isEditMode = isEditMode,
                isClockRunning = isClockRunning,
                autoSyncScore = autoSyncScore,
                activeSuspensions = activeSuspensions,
                onBack = onBack,
                onToggleEditMode = { if (canEdit) isEditMode = !isEditMode },
                onToggleClockRunning = { isClockRunning = !isClockRunning },
                onAdjustClock = { deltaSec -> repository.advanceGameClock(game.gameId, deltaSec) },
                onSetClock = { targetSec -> repository.setGameClockSeconds(game.gameId, targetSec) },
                onToggleAutoSync = { autoSyncScore = it },
                onOpenDirectScoreDialog = { showDirectScoreDialog = true }
            ) else ScoreboardAndMatchClockCard(
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
                onToggleEditMode = { if (canEdit) isEditMode = !isEditMode },
                onToggleClockRunning = { isClockRunning = !isClockRunning },
                onAdjustClock = { deltaSec -> repository.advanceGameClock(game.gameId, deltaSec) },
                onSetClock = { targetSec -> repository.setGameClockSeconds(game.gameId, targetSec) },
                onToggleAutoSync = { autoSyncScore = it },
                onOpenDirectScoreDialog = { showDirectScoreDialog = true },
                onApplyStartingSeven = {
                    substitutionPlayerId = null
                    startingSevenDialogVariant = false
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
                            text = { Text("Aufstellung") }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Verlauf (${gameLogs.size})") }
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = { Text("Staff (${coachParticipants.size})") }
                        )
                        if (isEditMode) {
                            Tab(
                                selected = activeTab == 3,
                                onClick = { activeTab = 3 },
                                text = { Text("Aufgebot") }
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
                            text = { Text("Aufstellung") }
                        )
                        Tab(
                            selected = activeTab == 1,
                            onClick = { activeTab = 1 },
                            text = { Text("Verlauf (${gameLogs.size})") }
                        )
                        Tab(
                            selected = activeTab == 2,
                            onClick = { activeTab = 2 },
                            text = { Text("Staff (${coachParticipants.size})") }
                        )
                        if (isEditMode) {
                            Tab(
                                selected = activeTab == 3,
                                onClick = { activeTab = 3 },
                                text = { Text("Aufgebot") }
                            )
                        }
                    }
                }

                // 3. Inhalt je nach ausgewähltem Tab
                when (activeTab.coerceIn(0, maxTabIdx)) {
                    0 -> if (useMobileTracker) {
                        MobileCourtList(
                            onCourt = onCourtParticipants,
                            positionName = { p ->
                                snapshot.positions.find { it.positionId == p.positionId }?.positionName?.substringBefore(" ")
                                    ?: p.role.label
                            },
                            fieldStats = { pg -> snapshot.fieldPlayerStats.find { it.playerGameId == pg.playerGameId } ?: FieldPlayerGameStats(0, pg.playerGameId) },
                            keeperStats = { pg -> snapshot.goalKeeperStats.find { it.playerGameId == pg.playerGameId } ?: GoalKeeperGameStats(0, pg.playerGameId) },
                            isEditMode = isEditMode,
                            selectedPlayerId = selectedPair?.second?.playerId,
                            showFreeSlot = isEditMode && onCourtParticipants.size in 1..6 && courtEntryCandidates.isNotEmpty(),
                            onSelect = { selectedPlayerId = it },
                            onOpenProfile = onNavigateToPlayer,
                            onOpenFreeSlot = { showFreeSlotPicker = true },
                            onOpenStartingSeven = { startingSevenDialogVariant = false }
                        )
                    } else {
                        SideBySideLineupAndBenchSection(
                            repository = repository,
                            snapshot = snapshot,
                            game = game,
                            isSideBySide = isSideBySideColumns,
                            isEditMode = isEditMode,
                            autoSyncScore = autoSyncScore,
                            onCourtParticipants = onCourtParticipants,
                            benchParticipants = benchParticipants,
                            onTapPlayerForSubstitution = { playerId -> substitutionPlayerId = playerId },
                            showBench = isWideScreen,
                            canFillFreeSlot = courtEntryCandidates.isNotEmpty(),
                            onOpenFreeSlot = { showFreeSlotPicker = true },
                            onApplyStartingSeven = { useSeventh ->
                                substitutionPlayerId = null
                                startingSevenDialogVariant = useSeventh
                            },
                            onNavigateToPlayer = onNavigateToPlayer,
                            onEnterEditAndManageSquad = {
                                if (canEdit) {
                                    isEditMode = true
                                    activeTab = 3
                                }
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
                                    if (canEdit) {
                                        isEditMode = true
                                        activeTab = 3
                                    }
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

        if (useMobileTracker && isEditMode && activeTab == 0 && onCourtParticipants.isNotEmpty()) {
            val selected = selectedPair?.second
            MobileActionBar(
                player = selected,
                fieldStats = selectedFieldStats,
                keeperStats = selectedKeeperStats,
                status = lastActionText ?: selected?.let { "Für ${it.shortLabel()}" } ?: "Spieler wählen",
                canUndo = undoStack.isNotEmpty(),
                onFieldAction = { action ->
                    val (pg, p) = selectedPair ?: return@MobileActionBar
                    track("${action.label} · ${p.shortLabel()}") {
                        repository.updateFieldPlayerStats(pg.playerGameId, autoSyncTeamScore = autoSyncScore) { action.apply(it) }
                    }
                },
                onKeeperAction = { action ->
                    val (pg, p) = selectedPair ?: return@MobileActionBar
                    track("${action.label} · ${p.shortLabel()}") {
                        repository.updateGoalKeeperStats(pg.playerGameId, autoSyncOpponentScore = autoSyncScore) { action.apply(it) }
                    }
                },
                onGoalConcededWithoutKeeper = if (onCourtParticipants.none { it.second.role == PlayerRole.GOALKEEPER }) {
                    { track("Gegentor (leeres Tor)") { repository.recordGoalConcededWithoutKeeper(game.gameId, autoSyncScore) } }
                } else null,
                onSubstitute = { selectedPair?.let { substitutionPlayerId = it.second.playerId } },
                onTwoMinutes = {
                    val (pg, p) = selectedPair ?: return@MobileActionBar
                    track("2 Min · ${p.shortLabel()}") {
                        repository.updatePlayerDisciplinary(pg.playerGameId, pg.twoMinuteSuspensions + 1, pg.yellowCard, pg.redCard, pg.blueCard)
                    }
                },
                onMore = { showMoreSheet = true },
                onUndo = {
                    if (undoStack.isNotEmpty()) {
                        repository.restoreGameState(game.gameId, undoStack.removeAt(undoStack.lastIndex))
                        lastActionText = "Rückgängig"
                    }
                }
            )
        }
        }
    }

    if (showMoreSheet) {
        val pair = selectedPair
        if (pair == null) {
            LaunchedEffect(Unit) { showMoreSheet = false }
        } else {
            val (pg, p) = pair
            MobileMoreSheet(
                player = p,
                playerGame = pg,
                fieldStats = selectedFieldStats,
                onPenaltyDrawn = {
                    showMoreSheet = false
                    track("${FieldAction.PENALTY_DRAWN.label} · ${p.shortLabel()}") {
                        repository.updateFieldPlayerStats(pg.playerGameId) { FieldAction.PENALTY_DRAWN.apply(it) }
                    }
                },
                onToggleYellow = {
                    showMoreSheet = false
                    track("${if (pg.yellowCard) "Gelb zurück" else "Gelb"} · ${p.shortLabel()}") {
                        repository.updatePlayerDisciplinary(pg.playerGameId, pg.twoMinuteSuspensions, !pg.yellowCard, pg.redCard, pg.blueCard)
                    }
                },
                onRed = {
                    showMoreSheet = false
                    track("Rot · ${p.shortLabel()}") {
                        repository.updatePlayerDisciplinary(pg.playerGameId, pg.twoMinuteSuspensions, pg.yellowCard, true, pg.blueCard)
                    }
                },
                onBlue = {
                    showMoreSheet = false
                    track("Blau · ${p.shortLabel()}") {
                        repository.updatePlayerDisciplinary(pg.playerGameId, pg.twoMinuteSuspensions, pg.yellowCard, pg.redCard, true)
                    }
                },
                onDismiss = { showMoreSheet = false }
            )
        }
    }

    substitutionPlayerId?.let { playerId ->
        val options = repository.substitutionOptions(game.gameId, playerId, snapshot)
        val tapped = snapshot.players.find { it.playerId == playerId }
        if (options == null || tapped == null || !options.hasAnyOption) {
            LaunchedEffect(playerId) { substitutionPlayerId = null }
        } else {
            SubstitutionPicker(
                asBottomSheet = !isWideScreen,
                tappedPlayer = tapped,
                options = options,
                snapshot = snapshot,
                gameId = game.gameId,
                onCourtCount = onCourtParticipants.size,
                onPickPartner = { partnerId ->
                    val inId = if (options.isOnCourt) partnerId else playerId
                    val outId = if (options.isOnCourt) playerId else partnerId
                    val inLabel = snapshot.players.find { it.playerId == inId }?.shortLabel() ?: ""
                    val outNr = snapshot.players.find { it.playerId == outId }?.jerseyNumberHomeKit?.let { "#$it" } ?: ""
                    track("$inLabel für $outNr") {
                        repository.substitutePlayer(game.gameId, playerInId = inId, playerOutId = outId)
                    }
                    // Der Eingewechselte ist gleich für die nächste Eingabe gewählt
                    selectedPlayerId = inId
                    substitutionPlayerId = null
                },
                onActAlone = {
                    if (options.isOnCourt) {
                        track("${tapped.shortLabel()} auf die Bank") { repository.movePlayerToBench(game.gameId, playerId) }
                    } else {
                        track("${tapped.shortLabel()} aufs Feld") {
                            repository.substitutePlayer(game.gameId, playerInId = playerId, playerOutId = null)
                        }
                        selectedPlayerId = playerId
                    }
                    substitutionPlayerId = null
                },
                onDismiss = { substitutionPlayerId = null }
            )
        }
    }

    if (showFreeSlotPicker) {
        if (courtEntryCandidates.isEmpty() || onCourtParticipants.size >= 7) {
            LaunchedEffect(Unit) { showFreeSlotPicker = false }
        } else {
            SubstitutionPicker(
                asBottomSheet = !isWideScreen,
                title = "Freier Platz (${onCourtParticipants.size}/7)",
                partnerIds = courtEntryCandidates,
                emptyText = "Niemand auf der Bank kann kommen.",
                actAloneLabel = null,
                snapshot = snapshot,
                gameId = game.gameId,
                onPickPartner = { playerId ->
                    val label = snapshot.players.find { it.playerId == playerId }?.shortLabel() ?: ""
                    track("$label aufs Feld") {
                        repository.substitutePlayer(game.gameId, playerInId = playerId, playerOutId = null)
                    }
                    selectedPlayerId = playerId
                    showFreeSlotPicker = false
                },
                onActAlone = {},
                onDismiss = { showFreeSlotPicker = false }
            )
        }
    }

    startingSevenDialogVariant?.let { useSeventh ->
        StartingSevenDialog(
            repository = repository,
            gameId = game.gameId,
            initialUseSeventhFieldPlayer = useSeventh,
            onDismiss = { startingSevenDialogVariant = null },
            onApplied = { startingSevenDialogVariant = null }
        )
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
internal fun DirectScoreEditDialog(
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
        title = { Text("Spielstand") },
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

@Composable
internal fun CoachTimeoutTrackerCard(
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                        color = amberContainer(),
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
