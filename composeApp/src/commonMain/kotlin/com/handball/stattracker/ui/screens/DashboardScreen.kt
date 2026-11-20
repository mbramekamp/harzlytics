package com.handball.stattracker.ui.screens

import com.handball.stattracker.ui.EditorOnly
import com.handball.stattracker.ui.theme.seasonChipColors
import com.handball.stattracker.ui.rememberCanEdit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handball.stattracker.config.SupabaseConfig
import com.handball.stattracker.model.*
import com.handball.stattracker.repository.HandballRepository
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    isWideScreen: Boolean = true,
    selectedSeasonId: Int,
    onNavigateToPlayer: (Int) -> Unit,
    onNavigateToGame: (Int) -> Unit,
    onNavigateToStandings: () -> Unit
) {
    var editingClubName by remember { mutableStateOf(false) }
    var clubNameDraft by remember(snapshot.ownClubName) { mutableStateOf(snapshot.ownClubName) }

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
            .padding(if (isWideScreen) 20.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Vereinsname (Änderung nur für Trainer/Admin)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (editingClubName) {
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
            } else {
                Text(
                    text = snapshot.ownClubName,
                    style = if (isWideScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                EditorOnly {
                    IconButton(onClick = { editingClubName = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Vereinsnamen ändern")
                    }
                }
            }
        }

        // Kennzahlen-Reihe (Tabellenplatz, Punkte, Kadergröße)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KpiStatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToStandings() },
                title = "Tabellenplatz",
                value = ownStanding?.let { "${it.rank}. Platz" } ?: "-",
                isWideScreen = isWideScreen
            )
            KpiStatCard(
                modifier = Modifier.weight(1f).clickable { onNavigateToStandings() },
                title = "Punkte",
                value = ownStanding?.pointsFormatted ?: "0:0",
                isWideScreen = isWideScreen
            )
            KpiStatCard(
                modifier = Modifier.weight(1f),
                title = "Kader",
                value = "${snapshot.players.count { it.role != PlayerRole.COACH }} Spieler",
                isWideScreen = isWideScreen
            )
        }

        // Letzte Spiele & Top-Scorer (Responsive: Row auf Desktop/Tablet, Column auf Smartphone)
        if (isWideScreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RecentGamesCard(
                    modifier = Modifier.weight(1f),
                    recentGames = recentGames,
                    repository = repository,
                    snapshot = snapshot,
                    onNavigateToGame = onNavigateToGame
                )
                TopScorerCard(
                    modifier = Modifier.weight(1f),
                    topScorers = topScorers,
                    topKeepers = topKeepers,
                    snapshot = snapshot,
                    onNavigateToPlayer = onNavigateToPlayer
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RecentGamesCard(
                    modifier = Modifier.fillMaxWidth(),
                    recentGames = recentGames,
                    repository = repository,
                    snapshot = snapshot,
                    onNavigateToGame = onNavigateToGame
                )
                TopScorerCard(
                    modifier = Modifier.fillMaxWidth(),
                    topScorers = topScorers,
                    topKeepers = topKeepers,
                    snapshot = snapshot,
                    onNavigateToPlayer = onNavigateToPlayer
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun RecentGamesCard(
    modifier: Modifier = Modifier,
    recentGames: List<Game>,
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    onNavigateToGame: (Int) -> Unit
) {
    Card(
        modifier = modifier,
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
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = repository.formatGameMatchup(game, snapshot),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = game.gameDate,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
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
}

@Composable
private fun TopScorerCard(
    modifier: Modifier = Modifier,
    topScorers: List<Pair<Player, PlayerAggregatedStats>>,
    topKeepers: List<Pair<Player, PlayerAggregatedStats>>,
    snapshot: DatabaseSnapshot,
    onNavigateToPlayer: (Int) -> Unit
) {
    Card(
        modifier = modifier,
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
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            Text(
                                text = "#${player.jerseyNumberHomeKit ?: "-"} ${player.fullName}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "$posName • ${stats.gamesAttended} Spiele • ${stats.totalAssists} Assists",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "#${keeper.jerseyNumberHomeKit ?: "-"} ${keeper.fullName}",
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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

@Composable
private fun KpiStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    isWideScreen: Boolean = true
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(if (isWideScreen) 14.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = if (isWideScreen) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
