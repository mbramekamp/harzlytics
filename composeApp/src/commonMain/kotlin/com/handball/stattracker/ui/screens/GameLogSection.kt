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
 * Eigener Tab für den Torverlauf-Graphen und die chronologischen Spiel-Logs (Wechsel, Tore, Paraden, Strafen, Auszeiten).
 */
@Composable
internal fun GameLogAndProgressionTabSection(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    game: Game,
    logs: List<GameLogEntry>,
    isEditMode: Boolean,
    onNavigateToPlayer: (Int) -> Unit
) {
    // 0 = Alle, 1 = Nur Wechsel, 2 = Tore & Paraden, 3 = Strafen & TTO
    var logFilter by remember { mutableStateOf(0) }

    val filteredLogs = remember(logs, logFilter) {
        logs.filter { entry ->
            when (logFilter) {
                1 -> entry.eventType == GameLogEventType.SUBSTITUTION || entry.eventType == GameLogEventType.LINEUP_SET
                2 -> entry.eventType in setOf(
                    GameLogEventType.GOAL,
                    GameLogEventType.SEVEN_METER_GOAL,
                    GameLogEventType.GOALKEEPER_SAVE,
                    GameLogEventType.GOAL_CONCEDED,
                    GameLogEventType.MISSED_SHOT
                )
                3 -> entry.eventType in setOf(
                    GameLogEventType.TWO_MINUTE_SUSPENSION,
                    GameLogEventType.YELLOW_CARD,
                    GameLogEventType.RED_CARD,
                    GameLogEventType.BLUE_CARD,
                    GameLogEventType.TIMEOUT
                )
                else -> true
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Torverlauf / Spielverlaufs-Graph
        ScoreProgressionMiniChartCard(game = game, logs = logs)

        // 2. Spielprotokoll-Liste (Game Log)
        Card(
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            imageVector = Icons.AutoMirrored.Filled.FactCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column {
                            Text(
                                text = "Spielprotokoll",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${logs.count { it.eventType == GameLogEventType.SUBSTITUTION }} Wechsel • ${logs.size} Einträge",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Filter-Chips für das Protokoll
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = logFilter == 0,
                        onClick = { logFilter = 0 },
                        label = { Text("Alle (${logs.size})") }
                    )
                    FilterChip(
                        selected = logFilter == 1,
                        onClick = { logFilter = 1 },
                        label = { Text("Wechsel (${logs.count { it.eventType == GameLogEventType.SUBSTITUTION || it.eventType == GameLogEventType.LINEUP_SET }})") }
                    )
                    FilterChip(
                        selected = logFilter == 2,
                        onClick = { logFilter = 2 },
                        label = { Text("Tore & Paraden") }
                    )
                    FilterChip(
                        selected = logFilter == 3,
                        onClick = { logFilter = 3 },
                        label = { Text("Strafen & TTO") }
                    )
                }

                if (filteredLogs.isEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Noch keine Protokoll-Einträge in diesem Filter vorhanden.",
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    filteredLogs.forEach { entry ->
                        GameLogEventRow(
                            entry = entry,
                            isEditMode = isEditMode,
                            onOpenPlayer = { pid -> onNavigateToPlayer(pid) },
                            onDelete = { repository.deleteGameLogEntry(entry.logId) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ScoreProgressionMiniChartCard(
    game: Game,
    logs: List<GameLogEntry>
) {
    // 0 = Tordifferenz-Ausschlag (±3 Skala um 0-Linie), 1 = Lauf-Impuls (±3 Serien-Ausschlag)
    var swingMode by remember { mutableStateOf(0) }

    val chronological = remember(logs, game) {
        val base = logs.sortedWith(compareBy<GameLogEntry> { it.gameSecond }.thenBy { it.logId })
        if (base.isEmpty()) {
            listOf(
                Triple(0, 0, 0),
                Triple(1800, game.homeTeamHalftimeScore, game.awayTeamHalftimeScore),
                Triple(3600, game.homeTeamScore, game.awayTeamScore)
            )
        } else {
            buildList {
                add(Triple(0, 0, 0))
                base.forEach { add(Triple(it.gameSecond, it.homeScoreAtEvent, it.awayScoreAtEvent)) }
                if (last().second != game.homeTeamScore || last().third != game.awayTeamScore) {
                    add(Triple(game.elapsedSeconds.coerceAtLeast(last().first), game.homeTeamScore, game.awayTeamScore))
                }
            }
        }
    }

    // Berechnet für jeden Zeitpunkt den Ausschlag im festen Bereich [-3, +3] um die 0-Mittellinie (kein steigender Summen-Graph!)
    val swingPoints = remember(chronological, swingMode) {
        if (swingMode == 0) {
            chronological.map { (sec, hs, ascore) ->
                val clampedSwing = (hs - ascore).coerceIn(-3, 3)
                sec to clampedSwing
            }
        } else {
            buildList {
                var currentSwing = 0
                var prevH = 0
                var prevA = 0
                chronological.forEachIndexed { idx, (sec, hs, ascore) ->
                    if (idx == 0) {
                        add(sec to 0)
                    } else {
                        val dH = hs - prevH
                        val dA = ascore - prevA
                        val delta = dH - dA
                        currentSwing = (currentSwing + delta).coerceIn(-3, 3)
                        add(sec to currentSwing)
                    }
                    prevH = hs
                    prevA = ascore
                }
            }
        }
    }

    val homeColor = MaterialTheme.colorScheme.primary
    val awayColor = MaterialTheme.colorScheme.secondary
    val centerLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)

    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Torverlauf",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(homeColor, CircleShape))
                        Text("Heim (${game.homeTeamScore})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Box(Modifier.size(8.dp).background(awayColor, CircleShape))
                        Text("Gast (${game.awayTeamScore})", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = swingMode == 0,
                    onClick = { swingMode = 0 },
                    label = { Text("Tordifferenz") }
                )
                FilterChip(
                    selected = swingMode == 1,
                    onClick = { swingMode = 1 },
                    label = { Text("Tor-Lauf") }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().height(175.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Feste Y-Achsen-Skala links: +3, +2, +1, 0, -1, -2, -3
                Column(
                    modifier = Modifier.fillMaxHeight().padding(end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                    horizontalAlignment = Alignment.End
                ) {
                    Text("+3", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = homeColor)
                    Text("+2", style = MaterialTheme.typography.labelSmall, color = homeColor.copy(alpha = 0.75f))
                    Text("+1", style = MaterialTheme.typography.labelSmall, color = homeColor.copy(alpha = 0.75f))
                    Text("0", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold)
                    Text("-1", style = MaterialTheme.typography.labelSmall, color = awayColor.copy(alpha = 0.75f))
                    Text("-2", style = MaterialTheme.typography.labelSmall, color = awayColor.copy(alpha = 0.75f))
                    Text("-3", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.ExtraBold, color = awayColor)
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val maxSec = maxOf(3600, game.elapsedSeconds).toFloat()

                        val padL = 6.dp.toPx()
                        val padR = 6.dp.toPx()
                        val padT = 8.dp.toPx()
                        val padB = 8.dp.toPx()
                        val w = (size.width - padL - padR).coerceAtLeast(10f)
                        val h = (size.height - padT - padB).coerceAtLeast(10f)
                        val centerY = padT + h / 2f
                        val halfH = h / 2f

                        // Horizontale Hilfslinien für +3, +2, +1, 0, -1, -2, -3
                        for (level in -3..3) {
                            val y = centerY - (level / 3f) * halfH
                            val isZeroLine = level == 0
                            drawLine(
                                color = if (isZeroLine) centerLineColor else gridColor,
                                start = Offset(padL, y),
                                end = Offset(padL + w, y),
                                strokeWidth = if (isZeroLine) 2.dp.toPx() else 1.dp.toPx(),
                                pathEffect = if (isZeroLine) null else PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                            )
                        }

                        // Vertikale Halbzeit-Linie (30:00)
                        val hzX = padL + w * (1800f / maxSec)
                        drawLine(
                            color = centerLineColor.copy(alpha = 0.45f),
                            start = Offset(hzX, padT),
                            end = Offset(hzX, padT + h),
                            strokeWidth = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f)
                        )

                        val pts = swingPoints.map { (sec, swingVal) ->
                            val x = padL + w * (sec.toFloat() / maxSec).coerceIn(0f, 1f)
                            val y = centerY - (swingVal.toFloat().coerceIn(-3f, 3f) / 3f) * halfH
                            Triple(x, y, swingVal)
                        }

                        // Zeichne farbige Ausschlag-Fläche zur 0-Mittellinie sowie die Ausschlag-Kurve
                        for (i in 0 until pts.size - 1) {
                            val (x1, y1, s1) = pts[i]
                            val (x2, y2, s2) = pts[i + 1]
                            val avgSwing = (s1 + s2) / 2f
                            val segColor = when {
                                avgSwing > 0f -> homeColor
                                avgSwing < 0f -> awayColor
                                else -> centerLineColor
                            }

                            val fillPath = Path().apply {
                                moveTo(x1, centerY)
                                lineTo(x1, y1)
                                lineTo(x2, y2)
                                lineTo(x2, centerY)
                                close()
                            }
                            drawPath(
                                path = fillPath,
                                color = segColor.copy(alpha = 0.16f)
                            )

                            drawLine(
                                color = segColor,
                                start = Offset(x1, y1),
                                end = Offset(x2, y2),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }

                        // Punkte auf dem Ausschlag-Verlauf
                        pts.forEach { (x, y, s) ->
                            val ptColor = when {
                                s > 0 -> homeColor
                                s < 0 -> awayColor
                                else -> centerLineColor
                            }
                            drawCircle(
                                color = ptColor,
                                radius = 4.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }
                }
            }

            // Zeitachsen-Beschriftung (0' bis 60')
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("00:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("15:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("30:00 (HZ)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("45:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("60:00", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun GameLogEventRow(
    entry: GameLogEntry,
    isEditMode: Boolean,
    onOpenPlayer: (Int) -> Unit,
    onDelete: () -> Unit
) {
    val badgeColor = when (entry.eventType) {
        GameLogEventType.SUBSTITUTION, GameLogEventType.LINEUP_SET -> MaterialTheme.colorScheme.tertiary
        GameLogEventType.GOAL, GameLogEventType.SEVEN_METER_GOAL -> MaterialTheme.colorScheme.primary
        GameLogEventType.GOALKEEPER_SAVE -> MaterialTheme.colorScheme.tertiary
        GameLogEventType.TWO_MINUTE_SUSPENSION, GameLogEventType.RED_CARD, GameLogEventType.BLUE_CARD -> MaterialTheme.colorScheme.error
        GameLogEventType.YELLOW_CARD, GameLogEventType.TIMEOUT -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier
            .fillMaxWidth()
            .let { mod ->
                val pid = entry.primaryPlayerId ?: entry.secondaryPlayerId
                if (pid != null) mod.clickable { onOpenPlayer(pid) } else mod
            }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Text(
                    text = entry.timeFormatted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = badgeColor.copy(alpha = 0.16f)
            ) {
                Text(
                    text = entry.eventType.label,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }

            Text(
                text = entry.description,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = entry.scoreFormatted,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            if (isEditMode) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Eintrag löschen",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
