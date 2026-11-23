package com.handball.stattracker.ui.screens

import com.handball.stattracker.ui.EditorOnly
import com.handball.stattracker.ui.rememberCanEdit
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
    isWideScreen: Boolean = true,
    selectedSeasonId: Int
) {
    val activeSeason = snapshot.seasons.find { it.seasonId == selectedSeasonId } ?: snapshot.seasons.firstOrNull()
    val standings = remember(snapshot, activeSeason) {
        activeSeason?.let { repository.computeStandings(it.seasonId, snapshot) } ?: emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(if (isWideScreen) 20.dp else 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Ligatabelle: Auf Smartphone horizontal scrollbar für saubere, ungeschnittene Spalten
        Card(
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
              // Tabelle füllt auf breiten Fenstern die ganze Breite, auf schmalen scrollt sie waagerecht
              val tableMinWidth = maxOf(620.dp, maxWidth)
              // restliche Spalten (Pl., Sp., S, U, N, Tore, Diff., Punkte) + Zeilenrand = 426 dp, der Rest gehört dem Namen
              val nameColumnWidth = maxOf(180.dp, tableMinWidth - 426.dp)
              Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
              ) {
                Column(modifier = Modifier.widthIn(min = tableMinWidth)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pl.", modifier = Modifier.width(38.dp), fontWeight = FontWeight.Bold)
                        Text("Mannschaft", modifier = Modifier.width(nameColumnWidth), fontWeight = FontWeight.Bold)
                        Text("Sp.", modifier = Modifier.width(42.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        Text("S", modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        Text("U", modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        Text("N", modifier = Modifier.width(36.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        Text("Tore", modifier = Modifier.width(80.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        Text("Diff.", modifier = Modifier.width(56.dp), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                        Text("Punkte", modifier = Modifier.width(70.dp), textAlign = TextAlign.End, fontWeight = FontWeight.Bold)
                    }

                    standings.forEach { row ->
                        val rowBg = if (row.isOwnTeam) {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
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
                                modifier = Modifier.width(38.dp),
                                fontWeight = FontWeight.Bold
                            )
                            Column(modifier = Modifier.width(nameColumnWidth)) {
                                Text(
                                    text = row.teamName,
                                    fontWeight = if (row.isOwnTeam) FontWeight.ExtraBold else FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                // Für den eigenen Verein steht dort sonst nur "Heimverein"
                                if (!row.isOwnTeam && row.location.isNotBlank()) {
                                    Text(
                                        text = row.location,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text("${row.gamesPlayed}", modifier = Modifier.width(42.dp), textAlign = TextAlign.Center)
                            Text("${row.wins}", modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                            Text("${row.draws}", modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                            Text("${row.losses}", modifier = Modifier.width(36.dp), textAlign = TextAlign.Center)
                            Text(row.goalsFormatted, modifier = Modifier.width(80.dp), textAlign = TextAlign.Center)
                            val diffStr = if (row.goalDifference > 0) "+${row.goalDifference}" else "${row.goalDifference}"
                            Text(diffStr, modifier = Modifier.width(56.dp), textAlign = TextAlign.Center)
                            Text(
                                text = row.pointsFormatted,
                                modifier = Modifier.width(70.dp),
                                textAlign = TextAlign.End,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
              }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
