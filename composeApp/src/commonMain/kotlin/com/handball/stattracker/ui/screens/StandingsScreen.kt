package com.handball.stattracker.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.repository.HandballRepository

@Composable
fun StandingsScreen(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    selectedSeasonId: Int,
    onSelectSeason: (Int) -> Unit,
    onOpenNewOpponentDialog: () -> Unit,
    onOpenNewGameDialog: () -> Unit
) {
    val activeSeason = snapshot.seasons.find { it.seasonId == selectedSeasonId } ?: snapshot.seasons.firstOrNull()
    val standings = remember(snapshot, activeSeason) {
        activeSeason?.let { repository.computeStandings(it.seasonId, snapshot) } ?: emptyList()
    }
    var showManageLeagueTeams by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = activeSeason?.division ?: "Ligatabelle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showManageLeagueTeams = !showManageLeagueTeams }) {
                            Icon(Icons.Default.Groups, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Mannschaften")
                        }
                        OutlinedButton(onClick = onOpenNewOpponentDialog) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Gegner")
                        }
                        Button(onClick = onOpenNewGameDialog) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(4.dp))
                            Text("Spiel")
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

        if (showManageLeagueTeams && activeSeason != null) {
            Card(
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Teilnehmende Vereine (${activeSeason.division})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        snapshot.opponentTeams.forEach { opp ->
                            val isAssigned = snapshot.seasonOpponents.any {
                                it.seasonId == activeSeason.seasonId && it.opponentTeamId == opp.opponentTeamId
                            }
                            FilterChip(
                                selected = isAssigned,
                                onClick = {
                                    repository.toggleOpponentSeasonAssignment(activeSeason.seasonId, opp.opponentTeamId)
                                },
                                label = { Text("${opp.opponentName} (${opp.opponentLocation})") }
                            )
                        }
                    }
                }
            }
        }

        Card(
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Pl.", modifier = Modifier.width(42.dp), fontWeight = FontWeight.Bold)
                    Text("Mannschaft", modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Text("Sp.", modifier = Modifier.width(46.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text("S", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text("U", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text("N", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text("Tore", modifier = Modifier.width(84.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text("Diff.", modifier = Modifier.width(64.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    Text("Punkte", modifier = Modifier.width(76.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                }

                standings.forEach { row ->
                    val rowBg = if (row.isOwnTeam) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(rowBg)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${row.rank}.",
                            modifier = Modifier.width(42.dp),
                            fontWeight = FontWeight.Bold
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.teamName,
                                fontWeight = if (row.isOwnTeam) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (row.isOwnTeam) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = row.location,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${row.gamesPlayed}", modifier = Modifier.width(46.dp), textAlign = TextAlign.Center)
                        Text("${row.wins}", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                        Text("${row.draws}", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                        Text("${row.losses}", modifier = Modifier.width(40.dp), textAlign = TextAlign.Center)
                        Text(row.goalsFormatted, modifier = Modifier.width(84.dp), textAlign = TextAlign.Center)
                        val diffStr = if (row.goalDifference > 0) "+${row.goalDifference}" else "${row.goalDifference}"
                        Text(diffStr, modifier = Modifier.width(64.dp), textAlign = TextAlign.Center)
                        Text(
                            text = row.pointsFormatted,
                            modifier = Modifier.width(76.dp),
                            textAlign = TextAlign.End,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
            }
        }
    }
}
