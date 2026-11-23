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

@Composable
internal fun ScoreboardAndMatchClockCard(
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
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        text = "${com.handball.stattracker.repository.DisplayFormat.date(game.gameDate)} • ${game.location} • ${game.gameType}",
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
                            EditorOnly {
                                Button(onClick = onToggleEditMode) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("Bearbeiten")
                                }
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
                            EditorOnly {
                                Button(onClick = onToggleEditMode) {
                                    Icon(Icons.Default.Edit, contentDescription = null)
                                    Spacer(Modifier.width(4.dp))
                                    Text("Bearbeiten")
                                }
                            }
                        }
                    }

                    Text(
                        text = "${com.handball.stattracker.repository.DisplayFormat.date(game.gameDate)} • ${game.location} • ${game.gameType}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
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
                        Text("Heim", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                style = com.handball.stattracker.ui.theme.scoreStyle(56.sp)
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
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                                        color = MaterialTheme.colorScheme.onSurface
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
                        Text("Gast", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                style = com.handball.stattracker.ui.theme.scoreStyle(56.sp)
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
                        shape = RoundedCornerShape(10.dp),
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
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Heim", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        style = com.handball.stattracker.ui.theme.scoreStyle(40.sp)
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
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Gast", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                        style = com.handball.stattracker.ui.theme.scoreStyle(40.sp)
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
