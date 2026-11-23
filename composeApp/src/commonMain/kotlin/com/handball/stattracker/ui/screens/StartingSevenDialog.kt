package com.handball.stattracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.StartingSevenCandidate
import com.handball.stattracker.model.StartingSevenRecommendation
import com.handball.stattracker.model.StartingSevenSlot
import com.handball.stattracker.repository.HandballRepository

/**
 * Vorschau der performance-basierten Startsieben:
 * je Platz der empfohlene Spieler mit Bewertung (Saison, Form, +/-), Tausch mit dem Zweitplatzierten,
 * Umschalter 6+1 / 7+0 und Übernahme aufs Feld.
 */
@Composable
fun StartingSevenDialog(
    repository: HandballRepository,
    gameId: Int,
    initialUseSeventhFieldPlayer: Boolean,
    onDismiss: () -> Unit,
    onApplied: () -> Unit
) {
    var useSeventh by remember(gameId) { mutableStateOf(initialUseSeventhFieldPlayer) }
    var recommendation by remember(gameId, useSeventh) {
        mutableStateOf(repository.recommendStartingSeven(gameId, useSeventh))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Startsieben", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = !useSeventh,
                        onClick = { useSeventh = false },
                        label = { Text("6+1") }
                    )
                    FilterChip(
                        selected = useSeventh,
                        onClick = { useSeventh = true },
                        label = { Text("7+0") }
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FormationSummaryRow(recommendation)
                if (recommendation.slots.isEmpty()) {
                    Text(
                        "Keine spielberechtigten Spieler im Kader.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                recommendation.slots.forEachIndexed { idx, slot ->
                    StartingSevenSlotCard(
                        slot = slot,
                        canSwap = recommendation.canSwapToRunnerUp(idx),
                        onSwap = { recommendation = recommendation.swapWithRunnerUp(idx) }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = recommendation.chosenPlayerIds.isNotEmpty(),
                onClick = {
                    if (repository.applyStartingSevenLineup(gameId, recommendation.chosenPlayerIds)) {
                        onApplied()
                    }
                }
            ) {
                Text("Übernehmen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Abbrechen") }
        }
    )
}

@Composable
private fun FormationSummaryRow(recommendation: StartingSevenRecommendation) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (recommendation.useSeventhFieldPlayer) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            }
        ) {
            Text(
                text = "${recommendation.fieldPlayerCount} Feld + ${recommendation.goalKeeperCount} TW",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Text(
            text = "Σ ${formatOneDecimal(recommendation.totalWeightedScore)}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StartingSevenSlotCard(
    slot: StartingSevenSlot,
    canSwap: Boolean,
    onSwap: () -> Unit
) {
    val chosen = slot.chosen
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SlotBadge(slot.slotLabel)
                if (chosen == null) {
                    Text(
                        "—",
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = playerLabel(chosen),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (slot.isPositionFallback) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer
                        ) {
                            Text(
                                text = chosen.positionShortName,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                    Text(
                        text = formatOneDecimal(chosen.breakdown.weightedScore),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (chosen != null) {
                BreakdownRow(chosen)
            }

            val runnerUp = slot.runnerUp
            if (runnerUp != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = playerLabel(runnerUp),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val delta = runnerUp.breakdown.weightedScore - (chosen?.breakdown?.weightedScore ?: 0.0)
                    Text(
                        text = "${formatOneDecimal(runnerUp.breakdown.weightedScore)} (${formatSigned(delta)})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = onSwap,
                        enabled = canSwap,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Tauschen", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SlotBadge(label: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.widthIn(min = 44.dp)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

@Composable
private fun BreakdownRow(candidate: StartingSevenCandidate) {
    val b = candidate.breakdown
    val trend = when {
        b.recentGamesCount == 0 -> ""
        b.recentFormScore > b.seasonPerformanceScore + 0.5 -> " ▲"
        b.recentFormScore < b.seasonPerformanceScore - 0.5 -> " ▼"
        else -> ""
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        MetricChip("Saison", formatOneDecimal(b.seasonPerformanceScore))
        MetricChip("Form",formatOneDecimal(b.recentFormScore) + trend)
        MetricChip("+/-", formatSigned(b.plusMinusPerGame))
    }
}

@Composable
private fun MetricChip(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

private fun playerLabel(candidate: StartingSevenCandidate): String =
    "#${candidate.player.jerseyNumberHomeKit ?: "-"} ${candidate.player.fullName}"

private fun formatOneDecimal(value: Double): String {
    val rounded = (kotlin.math.round(value * 10.0) / 10.0).let { if (it == 0.0) 0.0 else it }
    val text = rounded.toString()
    return if ('.' in text) text else "$text.0"
}

private fun formatSigned(value: Double): String {
    val text = formatOneDecimal(value)
    return if (value > 0.0) "+$text" else text
}
