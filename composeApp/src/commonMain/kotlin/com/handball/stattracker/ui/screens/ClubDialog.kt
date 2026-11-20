package com.handball.stattracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.repository.HandballRepository

/**
 * Vereinsverwaltung (Trainer/Admin): Vereinsname und welche Gegner in der gewählten Saison mitspielen.
 * Neue Gegner, Spiele, Saisons und Personen legt man ausschließlich über das +-Menü an.
 */
@Composable
fun ClubDialog(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    selectedSeasonId: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Verein", fontWeight = FontWeight.Bold) },
        text = { ClubManagementContent(repository, snapshot, selectedSeasonId) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}

@Composable
internal fun ClubManagementContent(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    selectedSeasonId: Int
) {
    var nameDraft by remember(snapshot.ownClubName) { mutableStateOf(snapshot.ownClubName) }
    val season = snapshot.seasons.find { it.seasonId == selectedSeasonId }
    val nameChanged = nameDraft.trim().isNotEmpty() && nameDraft.trim() != snapshot.ownClubName

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = nameDraft,
                onValueChange = { nameDraft = it },
                singleLine = true,
                label = { Text("Vereinsname") },
                modifier = Modifier.weight(1f)
            )
            Button(enabled = nameChanged, onClick = { repository.updateClubName(nameDraft) }) { Text("Speichern") }
        }

        if (season != null && snapshot.opponentTeams.isNotEmpty()) {
            Text("Teilnehmer ${season.division}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                snapshot.opponentTeams.forEach { opponent ->
                    val taking = snapshot.seasonOpponents.any {
                        it.seasonId == season.seasonId && it.opponentTeamId == opponent.opponentTeamId
                    }
                    FilterChip(
                        selected = taking,
                        onClick = { repository.toggleOpponentSeasonAssignment(season.seasonId, opponent.opponentTeamId) },
                        label = { Text(opponent.opponentName) }
                    )
                }
            }
        }
    }
}
