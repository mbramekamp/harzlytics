package com.handball.stattracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.model.Game
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.PlayerGame
import com.handball.stattracker.model.formatGameClockSeconds
import com.handball.stattracker.repository.HandballRepository
import com.handball.stattracker.ui.EditorOnly
import com.handball.stattracker.ui.theme.amberContainer
import com.handball.stattracker.ui.theme.scoreStyle

/**
 * Kompakte Anzeigetafel fürs Handy (< 700 dp): eine Zeile Spielstand mit Zurück und Bearbeiten,
 * eine Zeile Uhr/Halbzeit, bei Bedarf laufende 2-Minuten-Strafen. Seltene Eingaben liegen im Menü (⋮)
 * bzw. hinter einem Tipp auf die Uhr. Tablet/Desktop nutzen weiter [ScoreboardAndMatchClockCard].
 */
@Composable
internal fun CompactScoreboard(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    isEditMode: Boolean,
    isClockRunning: Boolean,
    autoSyncScore: Boolean,
    activeSuspensions: List<Pair<PlayerGame, Player>>,
    onBack: () -> Unit,
    onToggleEditMode: () -> Unit,
    onToggleClockRunning: () -> Unit,
    onAdjustClock: (Int) -> Unit,
    onSetClock: (Int) -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onOpenDirectScoreDialog: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    var showClockSheet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val opp1Name = snapshot.opponentTeams.find { it.opponentTeamId == game.opponentId }?.opponentName ?: "Gegner"
    val opp2Name = snapshot.opponentTeams.find { it.opponentTeamId == game.secondOpponentId }?.opponentName ?: "Gegner 2"
    val homeName = if (game.isOwnTeamGame && game.isHomeGame) snapshot.ownClubName else opp1Name
    val awayName = when {
        !game.isOwnTeamGame -> opp2Name
        game.isHomeGame -> opp1Name
        else -> snapshot.ownClubName
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Zeile 1: Zurück | Teams + Spielstand (immer mittig) | Bearbeiten bzw. Fertig + Menü
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Zurück", tint = MaterialTheme.colorScheme.primary)
                }
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = if (isEditMode) 100.dp else 52.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "$homeName – $awayName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Text(text = "${game.homeTeamScore} : ${game.awayTeamScore}", style = scoreStyle(34.sp))
                }
                Row(modifier = Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically) {
                    if (isEditMode) {
                        FilledIconButton(
                            onClick = onToggleEditMode,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            )
                        ) {
                            Icon(Icons.Default.Check, contentDescription = "Fertig")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Mehr")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Spielstand ändern") },
                                    onClick = { menuOpen = false; onOpenDirectScoreDialog() }
                                )
                                if (game.isOwnTeamGame) {
                                    DropdownMenuItem(
                                        text = { Text("Spielstand & +/- automatisch") },
                                        trailingIcon = { Checkbox(checked = autoSyncScore, onCheckedChange = null) },
                                        onClick = { onToggleAutoSync(!autoSyncScore) }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Spiel löschen", color = MaterialTheme.colorScheme.error) },
                                    onClick = { menuOpen = false; confirmDelete = true }
                                )
                            }
                        }
                    } else {
                        EditorOnly {
                            IconButton(onClick = onToggleEditMode) {
                                Icon(Icons.Default.Edit, contentDescription = "Bearbeiten", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Zeile 2: Uhr, Halbzeitstand, Start/Pause (Tipp auf die Uhr: Uhr stellen)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).heightIn(min = 40.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .let { if (isEditMode) it.clickable { showClockSheet = true } else it },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text(
                        text = game.elapsedFormatted,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "HZ ${game.homeTeamHalftimeScore} : ${game.awayTeamHalftimeScore}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isEditMode) {
                    FilledIconButton(
                        onClick = onToggleClockRunning,
                        modifier = Modifier.size(36.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isClockRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.tertiary,
                            contentColor = if (isClockRunning) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onTertiary
                        )
                    ) {
                        Icon(
                            imageVector = if (isClockRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isClockRunning) "Uhr pausieren" else "Uhr starten",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Zeile 3 (nur bei Bedarf): laufende 2-Minuten-Strafen mit Restzeit
            if (activeSuspensions.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    activeSuspensions.forEach { (pg, p) ->
                        PenaltyBadge(
                            text = "#${p.jerseyNumberHomeKit ?: "-"} ${p.lastName} ${formatGameClockSeconds(pg.remainingSuspensionSeconds(game.elapsedSeconds))}",
                            container = amberContainer(),
                            content = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    if (showClockSheet) {
        ClockAdjustSheet(
            game = game,
            onAdjustClock = onAdjustClock,
            onSetClock = onSetClock,
            onTakeHalftimeScore = {
                repository.updateGameScores(game.gameId, game.homeTeamScore, game.awayTeamScore, game.homeTeamScore, game.awayTeamScore)
            },
            onDismiss = { showClockSheet = false }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Spiel löschen?") },
            text = { Text("Alle Werte dieses Spiels werden gelöscht.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmDelete = false
                        repository.deleteGame(game.gameId)
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen") } }
        )
    }
}

/** Uhr stellen: bleibt offen, damit man mehrmals ±10 s tippen kann. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClockAdjustSheet(
    game: Game,
    onAdjustClock: (Int) -> Unit,
    onSetClock: (Int) -> Unit,
    onTakeHalftimeScore: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "${game.elapsedFormatted} (${game.currentMinute}. Min)",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("−1 Min" to -60, "−10 s" to -10, "+10 s" to 10, "+1 Min" to 60).forEach { (label, delta) ->
                    FilledTonalButton(onClick = { onAdjustClock(delta) }, modifier = Modifier.weight(1f)) {
                        Text(label, maxLines = 1)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { onSetClock(1800) }, modifier = Modifier.weight(1f)) { Text("Halbzeit 30:00") }
                OutlinedButton(onClick = onTakeHalftimeScore, modifier = Modifier.weight(1f)) { Text("Stand als HZ") }
            }
        }
    }
}
