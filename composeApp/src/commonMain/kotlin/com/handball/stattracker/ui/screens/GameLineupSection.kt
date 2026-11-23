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
 * Side-by-Side Aufstellungs-Ansicht:
 * - LINKS: Die 7 aktiven Spieler 'AUF DEM FELD' (Statistiken editierbar)
 * - RECHTS: Die Spieler 'AUF DER BANK' (inaktiver Zustand, Statistiken gesperrt, Klick startet Einwechslung)
 */
@Composable
internal fun SideBySideLineupAndBenchSection(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    isSideBySide: Boolean,
    isEditMode: Boolean,
    autoSyncScore: Boolean,
    onCourtParticipants: List<Pair<PlayerGame, Player>>,
    benchParticipants: List<Pair<PlayerGame, Player>>,
    onTapPlayerForSubstitution: (Int) -> Unit,
    showBench: Boolean,
    canFillFreeSlot: Boolean,
    onOpenFreeSlot: () -> Unit,
    onApplyStartingSeven: (useSeventhFieldPlayer: Boolean) -> Unit,
    onNavigateToPlayer: (Int) -> Unit,
    onEnterEditAndManageSquad: () -> Unit
) {
    if (onCourtParticipants.isEmpty() && benchParticipants.isEmpty()) {
        EmptySquadHint(isEditMode = isEditMode, onEnterEditAndManageSquad = onEnterEditAndManageSquad)
        return
    }

    val activeFieldCount = remember(onCourtParticipants) {
        onCourtParticipants.count { it.second.role == PlayerRole.FIELD_PLAYER }
    }
    val activeKeeperCount = remember(onCourtParticipants) {
        onCourtParticipants.count { it.second.role == PlayerRole.GOALKEEPER }
    }

    @Composable
    fun renderActiveOnCourtColumn(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isSideBySide) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SportsHandball,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "Auf dem Feld (${onCourtParticipants.size}/7)",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // Nur der Sonderfall ohne Torwart ist eine Information; 6+1 ist der Normalfall
                            if (activeFieldCount == 7 && activeKeeperCount == 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (activeFieldCount == 7 && activeKeeperCount == 0) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                ) {
                                    Text(
                                        text = "$activeFieldCount Feld + $activeKeeperCount TW",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (isEditMode) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilledTonalButton(
                                    onClick = { onApplyStartingSeven(false) }
                                ) {
                                    Text("6+1")
                                }
                                OutlinedButton(
                                    onClick = { onApplyStartingSeven(true) }
                                ) {
                                    Text("7 Feld")
                                }
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SportsHandball,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = "Auf dem Feld (${onCourtParticipants.size}/7)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            // Nur der Sonderfall ohne Torwart ist eine Information; 6+1 ist der Normalfall
                            if (activeFieldCount == 7 && activeKeeperCount == 0) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (activeFieldCount == 7 && activeKeeperCount == 0) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                ) {
                                    Text(
                                        text = "$activeFieldCount Feld + $activeKeeperCount TW",
                                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        if (isEditMode) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                FilledTonalButton(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    onClick = { onApplyStartingSeven(false) }
                                ) {
                                    Text("6+1 Startsieben", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    onClick = { onApplyStartingSeven(true) }
                                ) {
                                    Text("7 Feldspieler", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }

            if (onCourtParticipants.isEmpty()) {
                Card(shape = RoundedCornerShape(10.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Noch keine aktiven Spieler auf dem Feld.")
                        if (isEditMode) {
                            Button(onClick = { onApplyStartingSeven(false) }) {
                                Text("Empfohlene Startsieben (6+1) aufs Feld")
                            }
                        }
                    }
                }
            } else {
                onCourtParticipants.forEach { (pg, person) ->
                    val posName = snapshot.positions.find { it.positionId == person.positionId }?.positionName
                        ?: person.role.label
                    val canSubstitute = repository.substitutionOptions(game.gameId, person.playerId, snapshot)?.hasAnyOption == true
                    if (person.role == PlayerRole.GOALKEEPER) {
                        val stats = snapshot.goalKeeperStats.find { it.playerGameId == pg.playerGameId }
                            ?: GoalKeeperGameStats(0, pg.playerGameId)
                        GoalKeeperStatTrackerCard(
                            keeper = person,
                            positionName = posName,
                            playerGame = pg,
                            stats = stats,
                            gameElapsedSeconds = game.elapsedSeconds,
                            isWideScreen = isSideBySide,
                            isEditMode = isEditMode,
                            canSubstitute = canSubstitute,
                            onOpenProfile = { onNavigateToPlayer(person.playerId) },
                            onSubstitutionClick = { onTapPlayerForSubstitution(person.playerId) },
                            onAdjustPlusMinus = { delta -> repository.adjustPlayerPlusMinus(pg.playerGameId, delta) },
                            onUpdateStats = { transform ->
                                repository.updateGoalKeeperStats(
                                    playerGameId = pg.playerGameId,
                                    autoSyncOpponentScore = autoSyncScore,
                                    transform = transform
                                )
                            },
                            onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                                repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                            }
                        )
                    } else {
                        val stats = snapshot.fieldPlayerStats.find { it.playerGameId == pg.playerGameId }
                            ?: FieldPlayerGameStats(0, pg.playerGameId)
                        FieldPlayerStatTrackerCard(
                            player = person,
                            positionName = posName,
                            playerGame = pg,
                            stats = stats,
                            gameElapsedSeconds = game.elapsedSeconds,
                            isWideScreen = isSideBySide,
                            isEditMode = isEditMode,
                            canSubstitute = canSubstitute,
                            onOpenProfile = { onNavigateToPlayer(person.playerId) },
                            onSubstitutionClick = { onTapPlayerForSubstitution(person.playerId) },
                            onAdjustPlusMinus = { delta -> repository.adjustPlayerPlusMinus(pg.playerGameId, delta) },
                            onUpdateStats = { transform ->
                                repository.updateFieldPlayerStats(
                                    playerGameId = pg.playerGameId,
                                    autoSyncTeamScore = autoSyncScore,
                                    transform = transform
                                )
                            },
                            onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                                repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                            }
                        )
                    }
                }

                // Handy ohne Bank-Liste: freien Platz hier besetzen
                if (isEditMode && !showBench && canFillFreeSlot && onCourtParticipants.size < 7) {
                    FreeCourtSlotRow(onCourtCount = onCourtParticipants.size, onClick = onOpenFreeSlot)
                }
            }
        }
    }

    @Composable
    fun renderBenchColumn(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.EventSeat,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Bank (${benchParticipants.size})",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (benchParticipants.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Keine Spieler auf der Bank.",
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                benchParticipants.forEach { (pg, person) ->
                    val posName = snapshot.positions.find { it.positionId == person.positionId }?.positionName
                        ?: person.role.label
                    val canSubstitute = repository.substitutionOptions(game.gameId, person.playerId, snapshot)?.hasAnyOption == true

                    if (person.role == PlayerRole.GOALKEEPER) {
                        val stats = snapshot.goalKeeperStats.find { it.playerGameId == pg.playerGameId }
                            ?: GoalKeeperGameStats(0, pg.playerGameId)
                        GoalKeeperStatTrackerCard(
                            keeper = person,
                            positionName = posName,
                            playerGame = pg,
                            stats = stats,
                            gameElapsedSeconds = game.elapsedSeconds,
                            isWideScreen = isSideBySide,
                            isEditMode = isEditMode,
                            canSubstitute = canSubstitute,
                            onOpenProfile = { onNavigateToPlayer(person.playerId) },
                            onSubstitutionClick = { onTapPlayerForSubstitution(person.playerId) },
                            onAdjustPlusMinus = { delta -> repository.adjustPlayerPlusMinus(pg.playerGameId, delta) },
                            onUpdateStats = {},
                            onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                                repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                            }
                        )
                    } else {
                        val stats = snapshot.fieldPlayerStats.find { it.playerGameId == pg.playerGameId }
                            ?: FieldPlayerGameStats(0, pg.playerGameId)
                        FieldPlayerStatTrackerCard(
                            player = person,
                            positionName = posName,
                            playerGame = pg,
                            stats = stats,
                            gameElapsedSeconds = game.elapsedSeconds,
                            isWideScreen = isSideBySide,
                            isEditMode = isEditMode,
                            canSubstitute = canSubstitute,
                            onOpenProfile = { onNavigateToPlayer(person.playerId) },
                            onSubstitutionClick = { onTapPlayerForSubstitution(person.playerId) },
                            onAdjustPlusMinus = { delta -> repository.adjustPlayerPlusMinus(pg.playerGameId, delta) },
                            onUpdateStats = {},
                            onUpdateDisciplinary = { twoMin, yellow, red, blue ->
                                repository.updatePlayerDisciplinary(pg.playerGameId, twoMin, yellow, red, blue)
                            }
                        )
                    }
                }
            }
        }
    }

    if (isSideBySide) {
        // Links: 7 aktive Spieler auf dem Feld | Rechts: Spieler auf der Bank
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            renderActiveOnCourtColumn(modifier = Modifier.weight(1.15f))
            renderBenchColumn(modifier = Modifier.weight(1f))
        }
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            renderActiveOnCourtColumn(modifier = Modifier.fillMaxWidth())
            // Auf dem Handy nur die Spieler auf dem Feld; die Bank erscheint in der Wechsel-Auswahl
            if (showBench) renderBenchColumn(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun SquadNominationTabSection(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    gamePlayerGames: List<PlayerGame>
) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Aufgebot (${gamePlayerGames.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            snapshot.players.forEach { person ->
                val pg = gamePlayerGames.find { it.playerId == person.playerId }
                val isNominated = pg != null
                val posName = snapshot.positions.find { it.positionId == person.positionId }?.positionName
                    ?: person.role.label
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { repository.togglePlayerInGame(game.gameId, person.playerId) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Checkbox(
                            checked = isNominated,
                            onCheckedChange = { repository.togglePlayerInGame(game.gameId, person.playerId) }
                        )
                        Text(
                            text = "${person.fullName} ($posName)",
                            fontWeight = if (isNominated) FontWeight.Bold else FontWeight.Normal
                        )
                        if (pg?.isOnCourt == true) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = "Auf dem Feld",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                    if (person.jerseyNumberHomeKit != null) {
                        Text("#${person.jerseyNumberHomeKit}")
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptySquadHint(
    isEditMode: Boolean,
    onEnterEditAndManageSquad: () -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Noch keine Personen dieser Kategorie für dieses Spiel nominiert.")
            EditorOnly {
                Button(onClick = onEnterEditAndManageSquad) {
                    Text("Aufgebot auswählen")
                }
            }
        }
    }
}

/** Platzhalter für einen unbesetzten Platz auf dem Feld (weniger als 7 aktiv); öffnet die Auswahl der Bank. */
@Composable
private fun FreeCourtSlotRow(onCourtCount: Int, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.PersonAddAlt1, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "Freier Platz ($onCourtCount/7)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
