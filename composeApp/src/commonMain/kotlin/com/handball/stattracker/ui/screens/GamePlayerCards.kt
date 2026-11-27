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
internal fun FieldPlayerStatTrackerCard(
    player: Player,
    positionName: String,
    playerGame: PlayerGame,
    stats: FieldPlayerGameStats,
    gameElapsedSeconds: Int,
    isWideScreen: Boolean = true,
    isEditMode: Boolean,
    canSubstitute: Boolean,
    onOpenProfile: () -> Unit,
    onSubstitutionClick: () -> Unit,
    onAdjustPlusMinus: (Int) -> Unit,
    onUpdateStats: ((FieldPlayerGameStats) -> FieldPlayerGameStats) -> Unit,
    onUpdateDisciplinary: (Int, Boolean, Boolean, Boolean) -> Unit,
    /** Wechselspieler (Angriff/Abwehr) eines Feldspielers: voll aktiv wie ein Spieler auf dem Feld. */
    isSwapStandby: Boolean = false
) {
    // Darstellung und Eingabe wie auf dem Feld; der Wechselspieler zählt dazu
    val isOnCourt = playerGame.isOnCourt || isSwapStandby
    // Statistik nur für Spieler auf dem Feld und für Wechselspieler eines Feldspielers
    val canEditStats = isEditMode && isOnCourt
    var showDirectEdit by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    LaunchedEffect(canEditStats) {
        if (!canEditStats) showDirectEdit = false
    }
    LaunchedEffect(isEditMode) {
        if (!isEditMode) showMore = false
    }

    val remainingSusp = playerGame.remainingSuspensionSeconds(gameElapsedSeconds)

    val borderStroke = when {
        isOnCourt -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOnCourt -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            },
            // Halbtransparente Bankfarbe ist keine Theme-Farbe: Textfarbe ausdrücklich setzen (Dunkelmodus)
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isOnCourt) 1f else 0.76f)
            // Bank-Spieler: ganze Karte antippen = Wechsel (ihre Werte sind ohnehin gesperrt)
            .let { mod ->
                if (isEditMode && !isOnCourt && canSubstitute) mod.clickable(onClick = onSubstitutionClick) else mod
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        // Kopfzeile antippen: im Bearbeitungsmodus Wechsel, sonst Profil
                        .let { mod ->
                            when {
                                !isEditMode -> mod.clickable(onClick = onOpenProfile)
                                canSubstitute -> mod.clickable(onClick = onSubstitutionClick)
                                else -> mod
                            }
                        }
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isOnCourt) MaterialTheme.colorScheme.inverseSurface else MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text = "#${player.jerseyNumberHomeKit ?: "-"}",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            color = if (isOnCourt) MaterialTheme.colorScheme.inverseOnSurface else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = player.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            // Position direkt als Badge in der Spielerkarte
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = positionName,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        val shotPct = if (stats.goalesAttempted > 0) (stats.goalsScored * 100) / stats.goalesAttempted else 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${stats.goalsScored}/${stats.goalesAttempted} ($shotPct%) • ${playerGame.playingTimeFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LineupStatusAndPlusMinusPills(
                                playerGame = playerGame,
                                remainingSuspensionSec = remainingSusp,
                                isEditMode = isEditMode,
                                onAdjustPlusMinus = onAdjustPlusMinus
                            )
                        }
                    }
                }
                if (isEditMode) {
                    CardMoreToggle(expanded = showMore, onToggle = { showMore = !showMore })
                }
            }

            // Aktions- & Disziplinar-Leiste (Wechsel-Buttons + 2Min, Gelb, Rot, Blau)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = playerGame.twoMinuteSuspensions > 0,
                            onClick = {
                                val next = (playerGame.twoMinuteSuspensions + 1) % 4
                                onUpdateDisciplinary(next, playerGame.yellowCard, playerGame.redCard, playerGame.blueCard)
                            },
                            label = { Text("2Min: ${playerGame.twoMinuteSuspensions}") }
                        )
                        if (!showMore) AwardedCardBadges(playerGame)
                        if (canEditStats) {
                            IconButton(onClick = { showDirectEdit = !showDirectEdit }) {
                                Icon(Icons.Default.EditNote, contentDescription = "Spielstand ändern")
                            }
                        }
                    }
                } else {
                    ReadOnlyDisciplinaryRow(playerGame = playerGame)
                }
            }

            // Statistik-Werte (nur für aktive Spieler AUF DEM FELD sind +/- Buttons freigeschaltet)
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Tore",
                        value = stats.goalsScored,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { FieldAction.GOAL.apply(it) } },
                        onDecrement = { onUpdateStats { FieldAction.GOAL.revert(it) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Würfe",
                        value = stats.goalesAttempted,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { FieldAction.MISS.apply(it) } },
                        onDecrement = { onUpdateStats { FieldAction.MISS.revert(it) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "7m-Tore",
                        value = stats.penaltiesScored,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { FieldAction.PENALTY_GOAL.apply(it) } },
                        onDecrement = { onUpdateStats { FieldAction.PENALTY_GOAL.revert(it) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "7m-Vers.",
                        value = stats.penaltiesAttempted,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { FieldAction.PENALTY_MISS.apply(it) } },
                        onDecrement = { onUpdateStats { FieldAction.PENALTY_MISS.revert(it) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "7m gez.",
                        value = stats.penaltiesDrawn,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(penaltiesDrawn = it.penaltiesDrawn + 1) } },
                        onDecrement = { onUpdateStats { it.copy(penaltiesDrawn = (it.penaltiesDrawn - 1).coerceAtLeast(0)) } }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Assists",
                        value = stats.assists,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(assists = it.assists + 1) } },
                        onDecrement = { onUpdateStats { it.copy(assists = (it.assists - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Steals",
                        value = stats.steals,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(steals = it.steals + 1) } },
                        onDecrement = { onUpdateStats { it.copy(steals = (it.steals - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Blocks",
                        value = stats.blocks,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(blocks = it.blocks + 1) } },
                        onDecrement = { onUpdateStats { it.copy(blocks = (it.blocks - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Tech. Fehler",
                        value = stats.technicalMistakes,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(technicalMistakes = it.technicalMistakes + 1) } },
                        onDecrement = { onUpdateStats { it.copy(technicalMistakes = (it.technicalMistakes - 1).coerceAtLeast(0)) } }
                    )
                }
            } else {
                // Mobile übersichtliche Anordnung
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Tore & Würfe
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Tore",
                            value = stats.goalsScored,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { FieldAction.GOAL.apply(it) } },
                            onDecrement = { onUpdateStats { FieldAction.GOAL.revert(it) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Würfe",
                            value = stats.goalesAttempted,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { FieldAction.MISS.apply(it) } },
                            onDecrement = { onUpdateStats { FieldAction.MISS.revert(it) } }
                        )
                    }

                    // 7-Meter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "7m-Tore",
                            value = stats.penaltiesScored,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { FieldAction.PENALTY_GOAL.apply(it) } },
                            onDecrement = { onUpdateStats { FieldAction.PENALTY_GOAL.revert(it) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "7m-Vers.",
                            value = stats.penaltiesAttempted,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { FieldAction.PENALTY_MISS.apply(it) } },
                            onDecrement = { onUpdateStats { FieldAction.PENALTY_MISS.revert(it) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "7m gez.",
                            value = stats.penaltiesDrawn,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(penaltiesDrawn = it.penaltiesDrawn + 1) } },
                            onDecrement = { onUpdateStats { it.copy(penaltiesDrawn = (it.penaltiesDrawn - 1).coerceAtLeast(0)) } }
                        )
                    }

                    // Spielgestaltung
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Assists",
                            value = stats.assists,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(assists = it.assists + 1) } },
                            onDecrement = { onUpdateStats { it.copy(assists = (it.assists - 1).coerceAtLeast(0)) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Tech. Fehler",
                            value = stats.technicalMistakes,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(technicalMistakes = it.technicalMistakes + 1) } },
                            onDecrement = { onUpdateStats { it.copy(technicalMistakes = (it.technicalMistakes - 1).coerceAtLeast(0)) } }
                        )
                    }

                    // Abwehr
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Steals",
                            value = stats.steals,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(steals = it.steals + 1) } },
                            onDecrement = { onUpdateStats { it.copy(steals = (it.steals - 1).coerceAtLeast(0)) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Blocks",
                            value = stats.blocks,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(blocks = it.blocks + 1) } },
                            onDecrement = { onUpdateStats { it.copy(blocks = (it.blocks - 1).coerceAtLeast(0)) } }
                        )
                    }
                }
            }

            if (isEditMode && showMore) {
                CardToggleChips(
                    playerGame = playerGame,
                    includeYellowAndBlue = true,
                    onUpdateDisciplinary = onUpdateDisciplinary
                )
            }

            if (canEditStats && showDirectEdit) {
                var goalsText by remember(stats) { mutableStateOf(stats.goalsScored.toString()) }
                var shotsText by remember(stats) { mutableStateOf(stats.goalesAttempted.toString()) }
                var assistsText by remember(stats) { mutableStateOf(stats.assists.toString()) }
                var tfText by remember(stats) { mutableStateOf(stats.technicalMistakes.toString()) }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = goalsText,
                            onValueChange = { goalsText = it },
                            label = { Text("Tore") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = shotsText,
                            onValueChange = { shotsText = it },
                            label = { Text("Würfe") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = assistsText,
                            onValueChange = { assistsText = it },
                            label = { Text("Assists") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = tfText,
                            onValueChange = { tfText = it },
                            label = { Text("TF") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                onUpdateStats { current ->
                                    current.copy(
                                        goalsScored = goalsText.toIntOrNull() ?: current.goalsScored,
                                        goalesAttempted = shotsText.toIntOrNull() ?: current.goalesAttempted,
                                        assists = assistsText.toIntOrNull() ?: current.assists,
                                        technicalMistakes = tfText.toIntOrNull() ?: current.technicalMistakes
                                    )
                                }
                                showDirectEdit = false
                            }
                        ) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GoalKeeperStatTrackerCard(
    keeper: Player,
    positionName: String,
    playerGame: PlayerGame,
    stats: GoalKeeperGameStats,
    gameElapsedSeconds: Int,
    isWideScreen: Boolean = true,
    isEditMode: Boolean,
    canSubstitute: Boolean,
    onOpenProfile: () -> Unit,
    onSubstitutionClick: () -> Unit,
    onAdjustPlusMinus: (Int) -> Unit,
    onUpdateStats: ((GoalKeeperGameStats) -> GoalKeeperGameStats) -> Unit,
    onUpdateDisciplinary: (Int, Boolean, Boolean, Boolean) -> Unit
) {
    val isOnCourt = playerGame.isOnCourt
    val canEditStats = isEditMode && isOnCourt
    val remainingSusp = playerGame.remainingSuspensionSeconds(gameElapsedSeconds)
    var showMore by remember { mutableStateOf(false) }

    val borderStroke = when {
        isOnCourt -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.6f))
        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        border = borderStroke,
        colors = CardDefaults.cardColors(
            containerColor = when {
                isOnCourt -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (isOnCourt) 1f else 0.76f)
            // Bank-Spieler: ganze Karte antippen = Wechsel (ihre Werte sind ohnehin gesperrt)
            .let { mod -> if (isEditMode && !isOnCourt && canSubstitute) mod.clickable(onClick = onSubstitutionClick) else mod }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        // Kopfzeile antippen: im Bearbeitungsmodus Wechsel, sonst Profil
                        .let { mod ->
                            when {
                                !isEditMode -> mod.clickable(onClick = onOpenProfile)
                                canSubstitute -> mod.clickable(onClick = onSubstitutionClick)
                                else -> mod
                            }
                        }
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (isOnCourt) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline
                    ) {
                        Text(
                            text = "#${keeper.jerseyNumberHomeKit ?: "-"}",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = keeper.fullName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.tertiaryContainer
                            ) {
                                Text(
                                    text = positionName,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                        val totalFaced = stats.saves + stats.goalesConceded
                        val savePct = if (totalFaced > 0) (stats.saves * 100) / totalFaced else 0
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${stats.saves}/$totalFaced ($savePct%) • ${playerGame.playingTimeFormatted}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            LineupStatusAndPlusMinusPills(
                                playerGame = playerGame,
                                remainingSuspensionSec = remainingSusp,
                                isEditMode = isEditMode,
                                onAdjustPlusMinus = onAdjustPlusMinus
                            )
                        }
                    }
                }
                CardMoreToggle(expanded = showMore, onToggle = { showMore = !showMore })
            }

            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isEditMode) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = playerGame.twoMinuteSuspensions > 0,
                            onClick = {
                                val next = (playerGame.twoMinuteSuspensions + 1) % 4
                                onUpdateDisciplinary(next, playerGame.yellowCard, playerGame.redCard, playerGame.blueCard)
                            },
                            label = { Text("2Min: ${playerGame.twoMinuteSuspensions}") }
                        )
                        if (!showMore) AwardedCardBadges(playerGame)
                    }
                } else {
                    ReadOnlyDisciplinaryRow(playerGame = playerGame)
                }
            }

            // Alle 6 Felder aus GOAL_KEEPER_GAMESTATS (nur im aktiven Feld-Zustand editierbar)
            if (isWideScreen) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Paraden",
                        value = stats.saves,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(saves = it.saves + 1) } },
                        onDecrement = { onUpdateStats { it.copy(saves = (it.saves - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "Gegentore",
                        value = stats.goalesConceded,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(goalesConceded = it.goalesConceded + 1) } },
                        onDecrement = { onUpdateStats { it.copy(goalesConceded = (it.goalesConceded - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "7m geh.",
                        value = stats.penaltiesSaved,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(penaltiesSaved = it.penaltiesSaved + 1, saves = it.saves + 1) } },
                        onDecrement = { onUpdateStats { it.copy(penaltiesSaved = (it.penaltiesSaved - 1).coerceAtLeast(0)) } }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "7m-GT",
                        value = stats.penaltiesConceded,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(penaltiesConceded = it.penaltiesConceded + 1, goalesConceded = it.goalesConceded + 1) } },
                        onDecrement = { onUpdateStats { it.copy(penaltiesConceded = (it.penaltiesConceded - 1).coerceAtLeast(0)) } }
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Paraden",
                            value = stats.saves,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(saves = it.saves + 1) } },
                            onDecrement = { onUpdateStats { it.copy(saves = (it.saves - 1).coerceAtLeast(0)) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "Gegentore",
                            value = stats.goalesConceded,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(goalesConceded = it.goalesConceded + 1) } },
                            onDecrement = { onUpdateStats { it.copy(goalesConceded = (it.goalesConceded - 1).coerceAtLeast(0)) } }
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "7m geh.",
                            value = stats.penaltiesSaved,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(penaltiesSaved = it.penaltiesSaved + 1, saves = it.saves + 1) } },
                            onDecrement = { onUpdateStats { it.copy(penaltiesSaved = (it.penaltiesSaved - 1).coerceAtLeast(0)) } }
                        )
                        StatStepperBox(
                            modifier = Modifier.weight(1f),
                            label = "7m-GT",
                            value = stats.penaltiesConceded,
                            isEditMode = canEditStats,
                            onIncrement = { onUpdateStats { it.copy(penaltiesConceded = it.penaltiesConceded + 1, goalesConceded = it.goalesConceded + 1) } },
                            onDecrement = { onUpdateStats { it.copy(penaltiesConceded = (it.penaltiesConceded - 1).coerceAtLeast(0)) } }
                        )
                    }
                }
            }

            if (showMore) {
                if (isEditMode) {
                    CardToggleChips(
                        playerGame = playerGame,
                        includeYellowAndBlue = false,
                        onUpdateDisciplinary = onUpdateDisciplinary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "TG-Pass",
                        value = stats.sucessfulFastBreakPasses,
                        isEditMode = canEditStats,
                        onIncrement = {
                            onUpdateStats {
                                it.copy(
                                    sucessfulFastBreakPasses = it.sucessfulFastBreakPasses + 1,
                                    attemptedFastBreakPasses = it.attemptedFastBreakPasses + 1
                                )
                            }
                        },
                        onDecrement = {
                            onUpdateStats {
                                it.copy(sucessfulFastBreakPasses = (it.sucessfulFastBreakPasses - 1).coerceAtLeast(0))
                            }
                        }
                    )
                    StatStepperBox(
                        modifier = Modifier.weight(1f),
                        label = "TG-Vers.",
                        value = stats.attemptedFastBreakPasses,
                        isEditMode = canEditStats,
                        onIncrement = { onUpdateStats { it.copy(attemptedFastBreakPasses = it.attemptedFastBreakPasses + 1) } },
                        onDecrement = {
                            onUpdateStats {
                                it.copy(attemptedFastBreakPasses = (it.attemptedFastBreakPasses - 1).coerceAtLeast(it.sucessfulFastBreakPasses))
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
internal fun LineupStatusAndPlusMinusPills(
    playerGame: PlayerGame,
    remainingSuspensionSec: Int,
    isEditMode: Boolean,
    onAdjustPlusMinus: (Int) -> Unit
) {
    when {
        playerGame.isDisqualified -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "DISQUALIFIZIERT",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
        remainingSuspensionSec > 0 -> {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = "2Min (${formatGameClockSeconds(remainingSuspensionSec)})",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    }

    val pmColor = when {
        playerGame.plusMinus > 0 -> MaterialTheme.colorScheme.tertiary
        playerGame.plusMinus < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = pmColor.copy(alpha = 0.15f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (isEditMode && playerGame.isOnCourt) {
                Text(
                    text = "-",
                    modifier = Modifier.clickable { onAdjustPlusMinus(-1) }.padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = pmColor
                )
            }
            Text(
                text = "+/- ${playerGame.plusMinusFormatted}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = pmColor
            )
            if (isEditMode && playerGame.isOnCourt) {
                Text(
                    text = "+",
                    modifier = Modifier.clickable { onAdjustPlusMinus(1) }.padding(horizontal = 2.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = pmColor
                )
            }
        }
    }
}

@Composable
internal fun ReadOnlyDisciplinaryRow(playerGame: PlayerGame) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (playerGame.twoMinuteSuspensions > 0) {
            PenaltyBadge("2Min: ${playerGame.twoMinuteSuspensions}", amberContainer(), MaterialTheme.colorScheme.onSurface)
        }
        AwardedCardBadges(playerGame)
    }
}

@Composable
internal fun StatStepperBox(
    modifier: Modifier = Modifier,
    label: String,
    value: Int,
    isEditMode: Boolean,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isEditMode) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                            .clickable(onClick = onDecrement),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "−",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "$value",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                            .clickable(onClick = onIncrement),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            } else {
                Text(
                    text = "$value",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

