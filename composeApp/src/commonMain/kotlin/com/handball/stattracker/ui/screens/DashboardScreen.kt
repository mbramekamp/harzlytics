package com.handball.stattracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.*
import com.handball.stattracker.repository.HandballRepository
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    selectedSeasonId: Int,
    onSelectSeason: (Int) -> Unit,
    onNavigateToPlayer: (Int) -> Unit,
    onNavigateToGame: (Int) -> Unit,
    onNavigateToStandings: () -> Unit,
    onOpenPlusMenu: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val syncStatus by repository.syncStatus.collectAsState()
    var editingClubName by remember { mutableStateOf(false) }
    var clubNameDraft by remember(snapshot.ownClubName) { mutableStateOf(snapshot.ownClubName) }
    var serverUrlDraft by remember { mutableStateOf("http://localhost:8080") }

    val activeSeason = snapshot.seasons.find { it.seasonId == selectedSeasonId } ?: snapshot.seasons.firstOrNull()
    val standings = remember(snapshot, activeSeason) {
        activeSeason?.let { repository.computeStandings(it.seasonId, snapshot) } ?: emptyList()
    }
    val ownStanding = standings.find { it.isOwnTeam }

    val topScorers = remember(snapshot, activeSeason) {
        snapshot.players
            .filter { it.role == PlayerRole.FIELD_PLAYER }
            .map { player ->
                player to repository.computePlayerAggregatedStats(player.playerId, activeSeason?.seasonId, snapshot)
            }
            .sortedByDescending { it.second.totalGoals }
            .take(5)
    }

    val topKeepers = remember(snapshot, activeSeason) {
        snapshot.players
            .filter { it.role == PlayerRole.GOALKEEPER }
            .map { player ->
                player to repository.computePlayerAggregatedStats(player.playerId, activeSeason?.seasonId, snapshot)
            }
            .sortedByDescending { it.second.savePercentage }
    }

    val recentGames = remember(snapshot, activeSeason) {
        snapshot.games
            .filter { activeSeason == null || it.seasonId == activeSeason.seasonId }
            .sortedByDescending { it.gameDate }
            .take(4)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Obere Vereins- & Saison-Leiste
        Card(
            shape = RoundedCornerShape(16.dp),
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
                    Column(modifier = Modifier.weight(1f)) {
                        if (editingClubName) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                OutlinedTextField(
                                    value = clubNameDraft,
                                    onValueChange = { clubNameDraft = it },
                                    singleLine = true,
                                    label = { Text("Vereinsname") },
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = {
                                    repository.updateClubName(clubNameDraft)
                                    editingClubName = false
                                }) {
                                    Text("Speichern")
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = snapshot.ownClubName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                IconButton(onClick = { editingClubName = true }) {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = "Vereinsnamen ändern",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }

                    FilledTonalButton(onClick = onOpenPlusMenu) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Neu")
                    }
                }

                // Saison-Schnellauswahl
                if (snapshot.seasons.isNotEmpty()) {
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
                }
            }
        }

        // Kennzahlen-Reihe (Tabellenplatz, Punkte, Kadergröße)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            KpiStatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToStandings() },
                title = "Tabellenplatz",
                value = ownStanding?.let { "${it.rank}. Platz" } ?: "-",
                subtitle = activeSeason?.division ?: ""
            )
            KpiStatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToStandings() },
                title = "Punkte & Tore",
                value = ownStanding?.pointsFormatted ?: "0:0",
                subtitle = ownStanding?.let { "Tore ${it.goalsFormatted} (${if (it.goalDifference >= 0) "+${it.goalDifference}" else "${it.goalDifference}"})" } ?: "0:0"
            )
            KpiStatCard(
                modifier = Modifier.weight(1f),
                title = "Kader",
                value = "${snapshot.players.count { it.role != PlayerRole.COACH }} Spieler",
                subtitle = "${snapshot.players.count { it.role == PlayerRole.COACH }} Trainer"
            )
        }

        // Letzte Spiele & Top-Scorer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Letzte Spiele",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (recentGames.isEmpty()) {
                        Text("Noch keine Spiele eingetragen.")
                    } else {
                        recentGames.forEach { game ->
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigateToGame(game.gameId) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = repository.formatGameMatchup(game, snapshot),
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "${game.gameDate} • ${game.location} • ${game.gameType}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "${game.homeTeamScore}:${game.awayTeamScore}",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Top-Scorer",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    topScorers.forEach { (player, stats) ->
                        val posName = snapshot.positions.find { it.positionId == player.positionId }?.positionName ?: ""
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToPlayer(player.playerId) }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "#${player.jerseyNumberHomeKit ?: "-"} ${player.fullName}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "$posName • ${stats.gamesAttended} Spiele • ${stats.totalAssists} Assists",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    text = "${stats.totalGoals} Tore (${stats.shotAccuracyPercent}%)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "Torhüter",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    topKeepers.forEach { (keeper, stats) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToPlayer(keeper.playerId) }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("#${keeper.jerseyNumberHomeKit ?: "-"} ${keeper.fullName}")
                            Text(
                                text = "${stats.totalSaves} Paraden (${stats.savePercentage}%)",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                    }
                }
            }
        }

        // Synchronisation & Demo-Reset
        Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
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
                        text = "Synchronisation",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = syncStatus,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = serverUrlDraft,
                        onValueChange = { serverUrlDraft = it },
                        label = { Text("Server-URL") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                repository.syncWithBackend(serverUrlDraft)
                            }
                        }
                    ) {
                        Icon(Icons.Default.Sync, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Synchronisieren")
                    }
                    OutlinedButton(onClick = { repository.resetToDemoData() }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Demo-Daten")
                    }
                }
            }
        }
    }
}

@Composable
private fun KpiStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
