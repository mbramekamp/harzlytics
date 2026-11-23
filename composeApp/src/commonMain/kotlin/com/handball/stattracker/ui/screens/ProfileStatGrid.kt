package com.handball.stattracker.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.PlayerAggregatedStats
import com.handball.stattracker.model.PlayerRole

/** Eine Profil-Kachel: ein Wert (oder ein echtes Paar wie Tore / Würfe), optional mit Quote. */
internal data class ProfileStatTile(val label: String, val value: String, val sub: String? = null)

/** Kacheln der Profil-Übersicht je Rolle, eine Kachel = ein Wert. */
internal fun profileStatTiles(role: PlayerRole, stats: PlayerAggregatedStats): List<ProfileStatTile> = when (role) {
    PlayerRole.FIELD_PLAYER -> listOf(
        ProfileStatTile("Tore / Würfe", "${stats.totalGoals} / ${stats.totalShots}", "Quote: ${stats.shotAccuracyPercent}%"),
        ProfileStatTile(
            "7-Meter",
            "${stats.totalPenaltyGoals} / ${stats.totalPenaltyAttempts}",
            "Quote: ${stats.penaltyAccuracyPercent}% • ${stats.totalPenaltiesDrawn} herausgeholt"
        ),
        ProfileStatTile("+/-", stats.plusMinusFormatted),
        ProfileStatTile("Spielzeit", stats.totalPlayingTimeFormatted),
        ProfileStatTile("Assists", "${stats.totalAssists}"),
        ProfileStatTile(
            "Abwehr",
            "${stats.totalSteals + stats.totalBlocks}",
            "${stats.totalSteals} Steals • ${stats.totalBlocks} Blocks"
        ),
        ProfileStatTile("Techn. Fehler", "${stats.totalTechnicalMistakes}"),
        ProfileStatTile("2 Minuten", "${stats.totalTwoMin}")
    )
    PlayerRole.GOALKEEPER -> listOf(
        ProfileStatTile("Paraden / Gegentore", "${stats.totalSaves} / ${stats.totalGoalsConceded}", "Fangquote: ${stats.savePercentage}%"),
        ProfileStatTile(
            "7m-Paraden",
            "${stats.totalPenaltiesSaved} / ${stats.totalPenaltiesSaved + stats.totalPenaltiesConceded}",
            "Quote: ${stats.penaltySavePercentage}%"
        ),
        ProfileStatTile(
            "Gegenstoßpässe",
            "${stats.totalFastBreakPassesSuccess} / ${stats.totalFastBreakPassesAttempted}",
            "Quote: ${stats.fastBreakPassPercentage}%"
        ),
        ProfileStatTile("+/-", stats.plusMinusFormatted),
        ProfileStatTile("Spielzeit", stats.totalPlayingTimeFormatted),
        ProfileStatTile("2 Minuten", "${stats.totalTwoMin}")
    )
    PlayerRole.COACH -> listOf(
        ProfileStatTile("Spiele", "${stats.gamesAttended}"),
        ProfileStatTile("Auszeiten", "${stats.totalTimeoutsTaken}"),
        ProfileStatTile("Gelb", "${stats.yellowCards}"),
        ProfileStatTile("2 Minuten", "${stats.totalTwoMin}"),
        ProfileStatTile("Rot", "${stats.redCards}"),
        ProfileStatTile("Blau", "${stats.blueCards}")
    )
}

/** Raster gleich hoher Kacheln; leere Plätze in der letzten Reihe bleiben frei. */
@Composable
internal fun ProfileStatGrid(tiles: List<ProfileStatTile>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(columns).forEach { rowTiles ->
            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                rowTiles.forEach { tile ->
                    ProfileStatBox(modifier = Modifier.weight(1f).fillMaxHeight(), tile = tile)
                }
                repeat(columns - rowTiles.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun ProfileStatBox(modifier: Modifier, tile: ProfileStatTile) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = tile.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = tile.value,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            tile.sub?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
