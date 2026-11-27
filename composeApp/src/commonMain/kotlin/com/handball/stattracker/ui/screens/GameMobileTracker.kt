package com.handball.stattracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handball.stattracker.model.FieldAction
import com.handball.stattracker.model.FieldPlayerGameStats
import com.handball.stattracker.model.GoalKeeperGameStats
import com.handball.stattracker.model.KeeperAction
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.PlayerGame
import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.ui.theme.amberContainer

/*
 * Mobile Spielerfassung (Handy < 700 dp): eine Zeile pro Spieler auf dem Feld, darunter fest eine Aktionsleiste.
 * Erst Spieler wählen, dann Aktion tippen; Korrekturen über "Rückgängig". Tablet/Desktop behalten die Karten.
 */

internal val FieldAction.label: String
    get() = when (this) {
        FieldAction.GOAL -> "Tor"
        FieldAction.MISS -> "Fehlwurf"
        FieldAction.PENALTY_GOAL -> "7m Tor"
        FieldAction.PENALTY_MISS -> "7m vorbei"
        FieldAction.ASSIST -> "Assist"
        FieldAction.TECHNICAL_FAULT -> "Techn. F."
        FieldAction.STEAL -> "Steal"
        FieldAction.BLOCK -> "Block"
        FieldAction.PENALTY_DRAWN -> "7m herausgeholt"
    }

internal val KeeperAction.label: String
    get() = when (this) {
        KeeperAction.SAVE -> "Parade"
        KeeperAction.GOAL_CONCEDED -> "Gegentor"
        KeeperAction.PENALTY_SAVED -> "7m gehalten"
        KeeperAction.PENALTY_CONCEDED -> "7m Gegentor"
        KeeperAction.FAST_BREAK_PASS -> "TG-Pass"
        KeeperAction.FAST_BREAK_MISS -> "TG-Fehlpass"
    }

/** Aktionen in der Leiste; 7m herausgeholt liegt unter "Mehr". */
private val BAR_FIELD_ACTIONS = listOf(
    FieldAction.GOAL, FieldAction.MISS, FieldAction.PENALTY_GOAL, FieldAction.PENALTY_MISS,
    FieldAction.ASSIST, FieldAction.TECHNICAL_FAULT, FieldAction.STEAL, FieldAction.BLOCK
)

/** Kurzname für Leiste und Bestätigung: "#7 Uscins-Hoffmann". */
internal fun Player.shortLabel(): String {
    val last = fullName.substringAfter(' ', fullName)
    return listOfNotNull(jerseyNumberHomeKit?.let { "#$it" }, last).joinToString(" ")
}

@Composable
internal fun MobileCourtList(
    onCourt: List<Pair<PlayerGame, Player>>,
    /** Wechselspieler (Angriff/Abwehr) je Feldspieler: playerGameId des Feldspielers → Wechselspieler. */
    swapStandby: Map<Int, Pair<PlayerGame, Player>> = emptyMap(),
    positionName: (Player) -> String,
    fieldStats: (PlayerGame) -> FieldPlayerGameStats,
    keeperStats: (PlayerGame) -> GoalKeeperGameStats,
    isEditMode: Boolean,
    selectedPlayerId: Int?,
    showFreeSlot: Boolean,
    onSelect: (Int) -> Unit,
    onOpenProfile: (Int) -> Unit,
    onOpenFreeSlot: () -> Unit,
    onOpenStartingSeven: () -> Unit,
    /** Freier 8. Platz (Wechselpaar Angriff/Abwehr) kann besetzt werden. */
    showSwapSlot: Boolean = false,
    onOpenSwapSlot: () -> Unit = {}
) {
    Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auf dem Feld", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text("${onCourt.size}/7" + if (swapStandby.isNotEmpty()) " + ${swapStandby.size}" else "", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (isEditMode) {
                    TextButton(onClick = onOpenStartingSeven) { Text("Startsieben") }
                } else {
                    Spacer(Modifier.heightIn(min = 40.dp))
                }
            }
            if (onCourt.isEmpty()) {
                Text(
                    text = "Noch niemand auf dem Feld.",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Jeder Wechselspieler steht direkt unter seinem Partner
            val rows = onCourt.flatMap { entry ->
                listOfNotNull(entry to false, swapStandby[entry.first.playerGameId]?.let { it to true })
            }
            rows.forEach { (entry, _) ->
                val (pg, player) = entry
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val isKeeper = player.role == PlayerRole.GOALKEEPER
                val stat = if (isKeeper) {
                    val k = keeperStats(pg)
                    "${k.saves}/${k.saves + k.goalesConceded}"
                } else {
                    val f = fieldStats(pg)
                    "${f.goalsScored}/${f.goalesAttempted}"
                }
                MobileCourtRow(
                    player = player,
                    playerGame = pg,
                    positionName = positionName(player),
                    stat = stat,
                    selected = isEditMode && player.playerId == selectedPlayerId,
                    onClick = { if (isEditMode) onSelect(player.playerId) else onOpenProfile(player.playerId) }
                )
            }
            if (showFreeSlot) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenFreeSlot).heightIn(min = 54.dp).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PersonAddAlt1, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Freier Platz (${onCourt.size}/7)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (showSwapSlot) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenSwapSlot).heightIn(min = 54.dp).padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PersonAddAlt1, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Freier Platz (8.)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun MobileCourtRow(
    player: Player,
    playerGame: PlayerGame,
    positionName: String,
    stat: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val isKeeper = player.role == PlayerRole.GOALKEEPER
    val stripe = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            // Auswahl-Streifen links
            .drawBehind { if (selected) drawRect(stripe, size = Size(4.dp.toPx(), size.height)) }
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = if (isKeeper) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.inverseSurface,
                modifier = Modifier.widthIn(min = 44.dp)
            ) {
                Text(
                    text = "#${player.jerseyNumberHomeKit ?: "-"}",
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (isKeeper) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.inverseOnSurface
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(player.fullName, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(positionName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (playerGame.twoMinuteSuspensions > 0) {
                        PenaltyBadge("${playerGame.twoMinuteSuspensions}× 2 Min", amberContainer(), MaterialTheme.colorScheme.onSurface)
                    }
                    if (playerGame.yellowCard) PenaltyBadge("Gelb", amberContainer(), MaterialTheme.colorScheme.onSurface)
                }
            }
            Text(stat, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PlusMinusBadge(playerGame.plusMinus)
        }
    }
}

@Composable
private fun PlusMinusBadge(value: Int) {
    val color = when {
        value > 0 -> MaterialTheme.colorScheme.tertiary
        value < 0 -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.widthIn(min = 38.dp)) {
        Text(
            text = if (value > 0) "+$value" else "$value",
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

/**
 * Feste Aktionsleiste unten. [status] ist die Kopfzeile: gewählter Spieler oder kurz die letzte Eingabe.
 */
@Composable
internal fun MobileActionBar(
    player: Player?,
    fieldStats: FieldPlayerGameStats?,
    keeperStats: GoalKeeperGameStats?,
    status: String,
    canUndo: Boolean,
    onFieldAction: (FieldAction) -> Unit,
    onKeeperAction: (KeeperAction) -> Unit,
    // Nur bei 7 Feldspielern ohne TW: Gegentor für die Mannschaft
    onGoalConcededWithoutKeeper: (() -> Unit)? = null,
    onSubstitute: () -> Unit,
    onTwoMinutes: () -> Unit,
    onMore: () -> Unit,
    onUndo: () -> Unit
) {
    val enabled = player != null
    val isKeeper = player?.role == PlayerRole.GOALKEEPER
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            val buttons: List<BarButton> = if (isKeeper && keeperStats != null) {
                KeeperAction.entries.map { a -> BarButton(a.label, a.count(keeperStats)) { onKeeperAction(a) } }
            } else {
                BAR_FIELD_ACTIONS.map { a -> BarButton(a.label, fieldStats?.let { a.count(it) }) { onFieldAction(a) } } +
                    listOfNotNull(onGoalConcededWithoutKeeper?.let { BarButton("Gegentor", null, opponent = true, onClick = it) })
            }
            buttons.chunked(4).forEachIndexed { rowIdx, rowButtons ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rowButtons.forEachIndexed { idx, b ->
                        ActionTile(
                            label = b.label,
                            count = b.count,
                            primary = rowIdx == 0 && idx == 0,
                            opponent = b.opponent,
                            enabled = enabled || b.opponent,
                            onClick = b.onClick,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(4 - rowButtons.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SecondaryTile("Wechseln", enabled, onSubstitute, Modifier.weight(1f))
                SecondaryTile("2 Min", enabled, onTwoMinutes, Modifier.weight(1f))
                SecondaryTile("Mehr", enabled, onMore, Modifier.weight(1f))
                SecondaryTile("Rückgängig", canUndo, onUndo, Modifier.weight(1f))
            }
        }
    }
}

/** Ein Knopf der Aktionsleiste; [opponent] = Aktion für die gegnerische Seite (Bernstein). */
private class BarButton(val label: String, val count: Int?, val opponent: Boolean = false, val onClick: () -> Unit)

@Composable
private fun ActionTile(label: String, count: Int?, primary: Boolean, opponent: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val container = when {
        primary -> MaterialTheme.colorScheme.primary
        opponent -> amberContainer()
        else -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when {
        primary -> MaterialTheme.colorScheme.onPrimary
        opponent -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = container.copy(alpha = container.alpha * (if (enabled) 1f else 0.4f)),
        modifier = modifier.heightIn(min = 54.dp).clickable(enabled = enabled, onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FittingLabel(label, color = content, bold = true)
            if (count != null) {
                Text("$count", style = MaterialTheme.typography.labelSmall, color = content.copy(alpha = 0.8f))
            }
        }
    }
}

@Composable
private fun SecondaryTile(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        modifier = modifier.heightIn(min = 44.dp)
    ) {
        FittingLabel(label, color = MaterialTheme.colorScheme.onSurface, bold = false)
    }
}

/** Einzeiliger Knopftext, der auf schmalen Handys kleiner wird statt abgeschnitten zu werden. */
@Composable
private fun FittingLabel(text: String, color: Color, bold: Boolean) {
    BasicText(
        text = text,
        style = MaterialTheme.typography.labelLarge.copy(
            color = color,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        ),
        maxLines = 1,
        autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = MaterialTheme.typography.labelLarge.fontSize)
    )
}

/** Seltene Eingaben für den gewählten Spieler: 7m herausgeholt (Feldspieler) und Karten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MobileMoreSheet(
    player: Player,
    playerGame: PlayerGame,
    fieldStats: FieldPlayerGameStats?,
    onPenaltyDrawn: () -> Unit,
    onToggleYellow: () -> Unit,
    onRed: () -> Unit,
    onBlue: () -> Unit,
    onDismiss: () -> Unit
) {
    val isKeeper = player.role == PlayerRole.GOALKEEPER
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            Text(player.shortLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(6.dp))
            val rows = buildList {
                if (!isKeeper) add(Triple(FieldAction.PENALTY_DRAWN.label, fieldStats?.penaltiesDrawn?.toString(), onPenaltyDrawn))
                add(Triple(if (playerGame.yellowCard) "Gelb zurücknehmen" else "Gelb", null, onToggleYellow))
                add(Triple("Rot", null, onRed))
                if (!isKeeper) add(Triple("Blau", null, onBlue))
            }
            rows.forEachIndexed { idx, (label, value, action) ->
                if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = action).heightIn(min = 54.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    if (value != null) Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
