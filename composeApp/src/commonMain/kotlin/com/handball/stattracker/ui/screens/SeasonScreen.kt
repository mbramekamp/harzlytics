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
    onSelectGame: (Int?) -> Unit,
    onNavigateToPlayer: (Int) -> Unit
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
        // Spiel-Filter (die Saison wählt das Menü in der Kopfzeile; Neues Spiel/Saison über das +-Menü)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = filterMode == 0,
                onClick = { filterMode = 0 },
                label = { Text("Alle") },
                colors = seasonChipColors()
            )
            FilterChip(
                selected = filterMode == 1,
                onClick = { filterMode = 1 },
                label = { Text("Eigene Spiele") },
                colors = seasonChipColors()
            )
            FilterChip(
                selected = filterMode == 2,
                onClick = { filterMode = 2 },
                label = { Text("Liga-Spiele") },
                colors = seasonChipColors()
            )
        }

        // Spiele-Liste
        if (gamesInSeason.isEmpty()) {
            Card(
                shape = RoundedCornerShape(14.dp),
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
                }
            }
        } else {
            gamesInSeason.forEach { game ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                                // Heim/Auswärts nur bei eigenen Spielen; Fremdpaarungen brauchen keine Marke
                                if (game.isOwnTeamGame) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = if (game.isHomeGame) "Heim" else "Auswärts",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Text(
                                    text = "${com.handball.stattracker.repository.DisplayFormat.date(game.gameDate)} • ${game.location}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = repository.formatGameMatchup(game, snapshot),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "${game.homeTeamScore}:${game.awayTeamScore}",
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = com.handball.stattracker.ui.theme.scoreStyle(24.sp),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
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
