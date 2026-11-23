package com.handball.stattracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.PlayerGame
import com.handball.stattracker.ui.theme.amberContainer

/** Klappt seltene Eingaben einer Spielerkarte auf oder zu. */
@Composable
internal fun CardMoreToggle(expanded: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Weniger" else "Mehr"
        )
    }
}

/** Kleine Abzeichen für vergebene Karten (Gelb, Rot, Blau). */
@Composable
internal fun AwardedCardBadges(playerGame: PlayerGame) {
    if (playerGame.yellowCard) {
        PenaltyBadge("Gelb", amberContainer(), MaterialTheme.colorScheme.onSurface)
    }
    if (playerGame.redCard) {
        PenaltyBadge("Rot", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
    }
    if (playerGame.blueCard) {
        PenaltyBadge("Blau", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
internal fun PenaltyBadge(text: String, container: Color, content: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = container) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

/** Umschalter für Karten im aufgeklappten Bereich; Torhüter bekommen nur Rot. */
@Composable
internal fun CardToggleChips(
    playerGame: PlayerGame,
    includeYellowAndBlue: Boolean,
    onUpdateDisciplinary: (Int, Boolean, Boolean, Boolean) -> Unit
) {
    val twoMin = playerGame.twoMinuteSuspensions
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (includeYellowAndBlue) {
            FilterChip(
                selected = playerGame.yellowCard,
                onClick = { onUpdateDisciplinary(twoMin, !playerGame.yellowCard, playerGame.redCard, playerGame.blueCard) },
                label = { Text("Gelb") }
            )
        }
        FilterChip(
            selected = playerGame.redCard,
            onClick = { onUpdateDisciplinary(twoMin, playerGame.yellowCard, !playerGame.redCard, playerGame.blueCard) },
            label = { Text("Rot") }
        )
        if (includeYellowAndBlue) {
            FilterChip(
                selected = playerGame.blueCard,
                onClick = { onUpdateDisciplinary(twoMin, playerGame.yellowCard, playerGame.redCard, !playerGame.blueCard) },
                label = { Text("Blau") }
            )
        }
    }
}
