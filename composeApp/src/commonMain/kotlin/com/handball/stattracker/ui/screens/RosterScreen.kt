package com.handball.stattracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
 * Kader-Ansicht gemäß UI-Grundkonzept:
 * - Gruppiert nach Rolle/Position (Torhüter, Feldspieler, Trainer)
 * - In der Kader-Ansicht sind Spieler/Trainer auswählbar
 * - Nach dem Auswählen wird das Spieler-/Trainerprofil mit Gesamtstatistiken
 *   und einer Historie an Spielen (inkl. Einzelstatistiken) angezeigt.
 * - Master-Detail auf breiten Screens, Vollbild-Profil mit Zurück-Button auf Mobile.
 */
enum class PlayerProfileTab(val label: String) {
    OVERVIEW("Übersicht & Spiele"),
    PERFORMANCE("Performance & Ranking")
}

@Composable
fun RosterScreen(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    selectedPlayerId: Int?,
    isWideScreen: Boolean,
    onSelectPlayer: (Int?) -> Unit,
    onNavigateToGame: (Int) -> Unit,
    onOpenNewPlayerDialog: () -> Unit,
    onOpenNewTrainerDialog: () -> Unit
) {
    val selectedPlayer = snapshot.players.find { it.playerId == selectedPlayerId }
    var activeProfileTab by remember { mutableStateOf(PlayerProfileTab.OVERVIEW) }

    if (isWideScreen) {
        Row(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Linke Spalte: Gruppierte Kaderliste
            Card(
                modifier = Modifier.width(340.dp).fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                RosterListPanel(
                    snapshot = snapshot,
                    selectedPlayerId = selectedPlayer?.playerId,
                    onSelectPlayer = { onSelectPlayer(it) },
                    onOpenNewPlayerDialog = onOpenNewPlayerDialog,
                    onOpenNewTrainerDialog = onOpenNewTrainerDialog
                )
            }

            // Rechte Spalte: Spieler-/Trainerprofil mit Reitern (Übersicht & Spiele | Performance & Ranking)
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                if (selectedPlayer != null) {
                    PlayerProfileDetailPanel(
                        repository = repository,
                        snapshot = snapshot,
                        player = selectedPlayer,
                        activeTab = activeProfileTab,
                        onSelectTab = { activeProfileTab = it },
                        isWideScreen = true,
                        showBackButton = false,
                        onBack = {},
                        onSelectPlayer = { onSelectPlayer(it) },
                        onNavigateToGame = onNavigateToGame,
                        onDeleted = { onSelectPlayer(null) }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PersonSearch,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Bitte links einen Spieler oder Trainer aus dem Kader auswählen",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Das Spielerprofil zeigt Karrierewerte, Spielhistorie sowie im Reiter 'Performance' Graphen, Positionsvergleiche und das Spieler-Ranking.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    } else {
        // Mobile Ansicht: Entweder Kaderliste oder Vollbild-Spielerprofil mit Zurück-Button
        if (selectedPlayer != null) {
            PlayerProfileDetailPanel(
                repository = repository,
                snapshot = snapshot,
                player = selectedPlayer,
                activeTab = activeProfileTab,
                onSelectTab = { activeProfileTab = it },
                isWideScreen = false,
                showBackButton = true,
                onBack = { onSelectPlayer(null) },
                onSelectPlayer = { onSelectPlayer(it) },
                onNavigateToGame = onNavigateToGame,
                onDeleted = { onSelectPlayer(null) }
            )
        } else {
            RosterListPanel(
                snapshot = snapshot,
                selectedPlayerId = null,
                onSelectPlayer = { onSelectPlayer(it) },
                onOpenNewPlayerDialog = onOpenNewPlayerDialog,
                onOpenNewTrainerDialog = onOpenNewTrainerDialog
            )
        }
    }
}

@Composable
private fun RosterListPanel(
    snapshot: DatabaseSnapshot,
    selectedPlayerId: Int?,
    onSelectPlayer: (Int) -> Unit,
    onOpenNewPlayerDialog: () -> Unit,
    onOpenNewTrainerDialog: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredPlayers = remember(snapshot.players, searchQuery) {
        if (searchQuery.isBlank()) snapshot.players
        else snapshot.players.filter {
            it.fullName.contains(searchQuery.trim(), ignoreCase = true) ||
                it.jerseyNumberHomeKit?.toString() == searchQuery.trim()
        }
    }

    val goalkeepers = filteredPlayers.filter { it.role == PlayerRole.GOALKEEPER }
    val fieldPlayers = filteredPlayers.filter { it.role == PlayerRole.FIELD_PLAYER }
    val coaches = filteredPlayers.filter { it.role == PlayerRole.COACH }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Kader (${snapshot.players.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilledTonalIconButton(onClick = onOpenNewPlayerDialog) {
                    Icon(Icons.Default.PersonAdd, contentDescription = "Neuen Spieler anlegen")
                }
                FilledTonalIconButton(onClick = onOpenNewTrainerDialog) {
                    Icon(Icons.Default.Sports, contentDescription = "Neuen Trainer anlegen")
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Spieler / Trikotnr. suchen...") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (goalkeepers.isNotEmpty()) {
                item {
                    SectionHeaderBadge("TORHÜTER (${goalkeepers.size})")
                }
                items(goalkeepers, key = { it.playerId }) { player ->
                    RosterPersonRow(
                        player = player,
                        positionName = snapshot.positions.find { it.positionId == player.positionId }?.positionName ?: "TW",
                        isSelected = player.playerId == selectedPlayerId,
                        onClick = { onSelectPlayer(player.playerId) }
                    )
                }
            }

            if (fieldPlayers.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    SectionHeaderBadge("FELDSPIELER (${fieldPlayers.size})")
                }
                items(fieldPlayers, key = { it.playerId }) { player ->
                    RosterPersonRow(
                        player = player,
                        positionName = snapshot.positions.find { it.positionId == player.positionId }?.positionName ?: "Feld",
                        isSelected = player.playerId == selectedPlayerId,
                        onClick = { onSelectPlayer(player.playerId) }
                    )
                }
            }

            if (coaches.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(6.dp))
                    SectionHeaderBadge("TRAINERTEAM (${coaches.size})")
                }
                items(coaches, key = { it.playerId }) { coach ->
                    RosterPersonRow(
                        player = coach,
                        positionName = "Trainer",
                        isSelected = coach.playerId == selectedPlayerId,
                        onClick = { onSelectPlayer(coach.playerId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeaderBadge(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

@Composable
private fun RosterPersonRow(
    player: Player,
    positionName: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Trikotnummer-Badge (oder TR für Trainer)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                val badgeText = if (player.role == PlayerRole.COACH) {
                    "TR"
                } else {
                    "#${player.jerseyNumberHomeKit ?: "-"}"
                }
                Text(
                    text = badgeText,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.fullName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                val kitInfo = if (player.role != PlayerRole.COACH) {
                    " • Heim #${player.jerseyNumberHomeKit ?: "-"} / Ausw. #${player.jerseyNumberAwayKit ?: "-"}"
                } else ""
                Text(
                    text = "$positionName$kitInfo",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlayerProfileDetailPanel(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    player: Player,
    activeTab: PlayerProfileTab,
    onSelectTab: (PlayerProfileTab) -> Unit,
    isWideScreen: Boolean,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onSelectPlayer: (Int) -> Unit,
    onNavigateToGame: (Int) -> Unit,
    onDeleted: () -> Unit
) {
    var seasonFilterId by remember { mutableStateOf<Int?>(null) }

    val stats = remember(snapshot, player.playerId, seasonFilterId) {
        repository.computePlayerAggregatedStats(player.playerId, seasonFilterId, snapshot)
    }
    val gameHistory = remember(snapshot, player.playerId, seasonFilterId) {
        repository.getPlayerGameHistory(player.playerId, seasonFilterId, snapshot)
    }
    val positionName = snapshot.positions.find { it.positionId == player.positionId }?.positionName ?: "Trainerstab"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Profil-Kopfbereich
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (showBackButton) {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück zur Kaderliste")
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Text(
                                text = if (player.role == PlayerRole.COACH) "COACH" else "#${player.jerseyNumberHomeKit ?: "-"}",
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        Column {
                            Text(
                                text = player.fullName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${player.role.label} • $positionName",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            repository.deletePlayer(player.playerId)
                            onDeleted()
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Entfernen")
                    }
                }

                // Stammdaten-Zeile (Trikots & E-Mail)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (player.role != PlayerRole.COACH) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Heimtrikot: #${player.jerseyNumberHomeKit ?: "-"}") }
                        )
                        AssistChip(
                            onClick = {},
                            label = { Text("Auswärtstrikot: #${player.jerseyNumberAwayKit ?: "-"}") }
                        )
                    }
                    if (player.email.isNotBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("E-Mail: ${player.email}") }
                        )
                    }
                }
            }
        }

        // Reiter (Tabs) im Spielerprofil: "Übersicht & Spiele" vs. "Performance & Ranking"
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PlayerProfileTab.entries.forEach { tab ->
                    val isSelected = activeTab == tab
                    val tabLabel = if (tab == PlayerProfileTab.PERFORMANCE && player.role == PlayerRole.COACH) {
                        "Performance & Spieler-Ranking"
                    } else {
                        tab.label
                    }
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onSelectTab(tab) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0f)
                    ) {
                        Row(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (tab == PlayerProfileTab.OVERVIEW) Icons.Default.History else Icons.Default.QueryStats,
                                contentDescription = tabLabel,
                                modifier = Modifier.size(18.dp),
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = tabLabel,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Saison-Filter für Statistik, Historie, Performance-Graphen & Positions-Ranking
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Zeitraum:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            FilterChip(
                selected = seasonFilterId == null,
                onClick = { seasonFilterId = null },
                label = { Text("Gesamte Historie") }
            )
            snapshot.seasons.forEach { season ->
                FilterChip(
                    selected = seasonFilterId == season.seasonId,
                    onClick = { seasonFilterId = season.seasonId },
                    label = { Text(season.division) }
                )
            }
        }

        if (activeTab == PlayerProfileTab.PERFORMANCE) {
            PlayerPerformanceTabContent(
                repository = repository,
                snapshot = snapshot,
                player = player,
                seasonFilterId = seasonFilterId,
                isWideScreen = isWideScreen,
                onSelectPlayer = onSelectPlayer,
                onNavigateToGame = onNavigateToGame
            )
        } else {
            // Rollenspezifische Statistik-Übersicht
            Text(
                text = "Statistik-Zusammenfassung (${stats.gamesAttended} Spiele)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            when (player.role) {
                PlayerRole.FIELD_PLAYER -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Tore / Würfe",
                            value = "${stats.totalGoals} / ${stats.totalShots}",
                            sub = "Wurfquote: ${stats.shotAccuracyPercent}%"
                        )
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "7-Meter",
                            value = "${stats.totalPenaltyGoals} / ${stats.totalPenaltyAttempts}",
                            sub = "Quote: ${stats.penaltyAccuracyPercent}% (${stats.totalPenaltiesDrawn} gezogen)"
                        )
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Assists & Steals",
                            value = "${stats.totalAssists} / ${stats.totalSteals}",
                            sub = "${stats.totalBlocks} Blocks • ${stats.totalTechnicalMistakes} TF"
                        )
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Strafen",
                            value = "${stats.totalTwoMin}× 2Min",
                            sub = "Gelb: ${stats.yellowCards} • Rot: ${stats.redCards} • Blau: ${stats.blueCards}"
                        )
                    }
                }

                PlayerRole.GOALKEEPER -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Paraden / Gegentore",
                            value = "${stats.totalSaves} / ${stats.totalGoalsConceded}",
                            sub = "Fangquote: ${stats.savePercentage}%"
                        )
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "7m-Paraden",
                            value = "${stats.totalPenaltiesSaved} / ${stats.totalPenaltiesSaved + stats.totalPenaltiesConceded}",
                            sub = "7m-Quote: ${stats.penaltySavePercentage}%"
                        )
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Gegenstoßpässe",
                            value = "${stats.totalFastBreakPassesSuccess} / ${stats.totalFastBreakPassesAttempted}",
                            sub = "Passquote: ${stats.fastBreakPassPercentage}%"
                        )
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Strafen",
                            value = "${stats.totalTwoMin}× 2Min",
                            sub = "Gelb: ${stats.yellowCards} • Rot: ${stats.redCards}"
                        )
                    }
                }

                PlayerRole.COACH -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Betreute Spiele",
                            value = "${stats.gamesAttended}",
                            sub = "Als verantwortlicher Trainer"
                        )
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Genommene Auszeiten",
                            value = "${stats.totalTimeoutsTaken} TTOs",
                            sub = "Team-Timeouts mit Coaching-Grund"
                        )
                        ProfileStatBox(
                            modifier = Modifier.weight(1f),
                            label = "Bank-Strafen",
                            value = "${stats.yellowCards} Gelb / ${stats.totalTwoMin}× 2Min",
                            sub = "Rot: ${stats.redCards} • Blau: ${stats.blueCards}"
                        )
                    }
                }
            }

            // Spiel-Historie (laut UI-Konzept: "mit den Spielen einer Historie an spielen")
            Text(
                text = "Historie an Spielen (${gameHistory.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (gameHistory.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Noch keine Spiele für diese Person erfasst.",
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                gameHistory.forEach { entry ->
                    PlayerGameHistoryCard(
                        entry = entry,
                        role = player.role,
                        onClick = { onNavigateToGame(entry.game.gameId) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileStatBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    sub: String
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = sub,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlayerGameHistoryCard(
    entry: PlayerGameHistoryEntry,
    role: PlayerRole,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.matchupTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${entry.game.gameDate} • ${entry.seasonName} • ${entry.game.location}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when (entry.outcomeBadge) {
                            "S" -> MaterialTheme.colorScheme.tertiaryContainer
                            "U" -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.errorContainer
                        }
                    ) {
                        Text(
                            text = entry.outcomeBadge,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = entry.resultFormatted,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Detailwerte des Spielers in diesem Spiel
            when (role) {
                PlayerRole.FIELD_PLAYER -> {
                    val fs = entry.fieldStats
                    if (fs != null) {
                        Text(
                            text = "Tore: ${fs.goalsScored}/${fs.goalesAttempted} • 7m: ${fs.penaltiesScored}/${fs.penaltiesAttempted} (${fs.penaltiesDrawn} gez.) • Assists: ${fs.assists} • Steals: ${fs.steals} • Blocks: ${fs.blocks} • TF: ${fs.technicalMistakes} • 2Min: ${entry.playerGame.twoMinuteSuspensions}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                PlayerRole.GOALKEEPER -> {
                    val ks = entry.keeperStats
                    if (ks != null) {
                        Text(
                            text = "Paraden: ${ks.saves} (Gegentore: ${ks.goalesConceded}) • 7m gehalten: ${ks.penaltiesSaved}/${ks.penaltiesSaved + ks.penaltiesConceded} • Gegenstoßpässe: ${ks.sucessfulFastBreakPasses}/${ks.attemptedFastBreakPasses}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }
                PlayerRole.COACH -> {
                    if (entry.coachTimeouts.isEmpty()) {
                        Text(
                            text = "Keine Auszeit eingetragen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        entry.coachTimeouts.forEach { tto ->
                            Text(
                                text = "• Timeout: ${tto.timeoutReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                }
            }
        }
    }
}
