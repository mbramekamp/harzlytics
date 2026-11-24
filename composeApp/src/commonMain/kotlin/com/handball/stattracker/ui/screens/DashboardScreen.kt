package com.handball.stattracker.ui.screens

import com.handball.stattracker.model.SeasonScope
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
import androidx.compose.ui.unit.sp
import com.handball.stattracker.repository.DisplayFormat
import com.handball.stattracker.ui.theme.scoreStyle
import com.handball.stattracker.config.SupabaseConfig
import com.handball.stattracker.model.*
import com.handball.stattracker.repository.HandballRepository
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    isWideScreen: Boolean = true,
    selectedScope: SeasonScope,
    onNavigateToPlayer: (Int) -> Unit,
    onNavigateToGame: (Int) -> Unit,
    onNavigateToStandings: () -> Unit
) {

    val activeSeason = snapshot.seasons.find { it.seasonId == selectedScope.seasonId } ?: snapshot.seasons.firstOrNull()
    val standings = remember(snapshot, activeSeason, selectedScope) {
        activeSeason?.let { repository.computeStandings(it.seasonId, snapshot) } ?: emptyList()
    }
    val ownStanding = standings.find { it.isOwnTeam }

    val topScorers = remember(snapshot, activeSeason, selectedScope) {
        snapshot.players
            .filter { it.role == PlayerRole.FIELD_PLAYER }
            .map { player ->
                player to repository.computePlayerAggregatedStats(player.playerId, selectedScope, snapshot)
            }
            .sortedByDescending { it.second.totalGoals }
            .take(5)
    }

    val topKeepers = remember(snapshot, activeSeason, selectedScope) {
        snapshot.players
            .filter { it.role == PlayerRole.GOALKEEPER }
            .map { player ->
                player to repository.computePlayerAggregatedStats(player.playerId, selectedScope, snapshot)
            }
            .sortedByDescending { it.second.savePercentage }
    }

    val recentGames = remember(snapshot, activeSeason, selectedScope) {
        snapshot.games
            .filter { selectedScope.includes(it) }
            .sortedByDescending { it.gameDate }
            .take(4)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (isWideScreen) 20.dp else 16.dp, vertical = if (isWideScreen) 4.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = snapshot.ownClubName,
            style = if (isWideScreen) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall
        )

        // Kennzahlen als eine Leiste (Tabellenplatz, Punkte, Kadergröße)
        DashboardPanel {
            Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                KpiCell(
                    modifier = Modifier.weight(1f).clickable { onNavigateToStandings() },
                    title = "Tabellenplatz",
                    value = ownStanding?.let { "${it.rank}." } ?: "–",
                    isWideScreen = isWideScreen
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                KpiCell(
                    modifier = Modifier.weight(1f).clickable { onNavigateToStandings() },
                    title = "Punkte",
                    value = ownStanding?.pointsFormatted ?: "0:0",
                    isWideScreen = isWideScreen
                )
                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                KpiCell(
                    modifier = Modifier.weight(1f),
                    title = "Kader",
                    value = "${snapshot.players.count { it.role != PlayerRole.COACH }}",
                    isWideScreen = isWideScreen
                )
            }
        }

        // Letzte Spiele & Top-Scorer (nebeneinander auf breiten Bildschirmen)
        if (isWideScreen) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RecentGamesCard(Modifier.weight(1f), recentGames, repository, snapshot, onNavigateToGame)
                TopScorerCard(Modifier.weight(1f), topScorers, topKeepers, snapshot, onNavigateToPlayer)
            }
        } else {
            RecentGamesCard(Modifier.fillMaxWidth(), recentGames, repository, snapshot, onNavigateToGame)
            TopScorerCard(Modifier.fillMaxWidth(), topScorers, topKeepers, snapshot, onNavigateToPlayer)
        }

        Spacer(Modifier.height(16.dp))
    }
}

/** Eine weiße Fläche mit Haarlinie; Inhalte darin sind durch Linien getrennt, nicht durch weitere Karten. */
@Composable
private fun DashboardPanel(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp)
                )
            }
            content()
        }
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
    DashboardPanel(modifier = modifier, title = "Letzte Spiele") {
        if (recentGames.isEmpty()) {
            Text(
                "Noch keine Spiele eingetragen.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            recentGames.forEachIndexed { index, game ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                val (home, away) = repository.gameTeamNames(game, snapshot)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToGame(game.gameId) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = DisplayFormat.date(game.gameDate),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    ScoreLine(home, game.homeTeamScore, won = game.homeTeamScore > game.awayTeamScore)
                    ScoreLine(away, game.awayTeamScore, won = game.awayTeamScore > game.homeTeamScore)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** Eine Mannschaft mit ihren Toren; der Sieger steht kräftig, der Verlierer gedämpft. */
@Composable
private fun ScoreLine(team: String, score: Int, won: Boolean) {
    val color = if (won) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = team,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (won) FontWeight.SemiBold else FontWeight.Normal,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        )
        Text(text = "$score", style = scoreStyle(20.sp), color = color)
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
    DashboardPanel(modifier = modifier, title = "Top-Scorer") {
        topScorers.forEachIndexed { index, (player, stats) ->
            if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
            LeaderRow(
                jersey = player.jerseyNumberHomeKit,
                name = player.fullName,
                detail = positionLabel(snapshot, player),
                value = "${stats.totalGoals} Tore",
                rate = "${stats.shotAccuracyPercent} %",
                onClick = { onNavigateToPlayer(player.playerId) }
            )
        }
        if (topKeepers.isNotEmpty()) {
            Text(
                text = "Torhüter",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp)
            )
            topKeepers.forEachIndexed { index, (keeper, stats) ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(horizontal = 16.dp))
                LeaderRow(
                    jersey = keeper.jerseyNumberHomeKit,
                    name = keeper.fullName,
                    detail = null,
                    value = "${stats.totalSaves} Paraden",
                    rate = "${stats.savePercentage} %",
                    onClick = { onNavigateToPlayer(keeper.playerId) }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

/** "RM - Rückraum Mitte" → "Rückraum Mitte" (Kürzel nicht doppelt zeigen). */
private fun positionLabel(snapshot: DatabaseSnapshot, player: Player): String? =
    snapshot.positions.find { it.positionId == player.positionId }?.positionName?.substringAfter(" - ")

@Composable
private fun LeaderRow(
    jersey: Int?,
    name: String,
    detail: String?,
    value: String,
    rate: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = jersey?.toString() ?: "–",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(32.dp)
        )
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(text = value, style = MaterialTheme.typography.titleSmall)
            Text(text = rate, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KpiCell(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    isWideScreen: Boolean = true
) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp, vertical = if (isWideScreen) 14.dp else 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = value,
            style = if (isWideScreen) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
            maxLines = 1
        )
    }
}
