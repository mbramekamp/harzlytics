package com.handball.stattracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.SubstitutionOptions

/**
 * Wechsel-Auswahl: eine Liste von Spielern, ein Tipp wählt aus; optional eine Einzelaktion
 * ("Nur auf die Bank" / "Aufs Feld"). Genutzt nach Tipp auf eine Spielerkarte und für "Freier Platz".
 * Handy ([asBottomSheet]): Bottom Sheet auf Inhaltshöhe (Liste scrollt, Einzelaktion bleibt unten),
 * schließt per Wischen oder Tipp daneben. Tablet/Desktop: Pop-up.
 */
@Composable
internal fun SubstitutionPicker(
    asBottomSheet: Boolean,
    title: String,
    partnerIds: List<Int>,
    emptyText: String,
    actAloneLabel: String?,
    snapshot: DatabaseSnapshot,
    gameId: Int,
    onPickPartner: (Int) -> Unit,
    onActAlone: () -> Unit,
    onDismiss: () -> Unit,
    /** Optionale Zweitaktion, z. B. "Wechselpaar bilden" / "Wechselpaar lösen". */
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    val list: @Composable () -> Unit = { SubstitutionPartnerList(partnerIds, emptyText, snapshot, gameId, onPickPartner) }
    if (asBottomSheet) {
        SubstitutionBottomSheet(title, actAloneLabel, onActAlone, onDismiss, list, secondaryLabel, onSecondary)
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { SubstitutionTitle(title) },
            text = {
                Column(modifier = Modifier.widthIn(max = 420.dp).verticalScroll(rememberScrollState())) { list() }
            },
            confirmButton = {
                if (actAloneLabel != null) {
                    FilledTonalButton(onClick = onActAlone) { Text(actAloneLabel) }
                }
            },
            dismissButton = {
                Row {
                    if (secondaryLabel != null) {
                        TextButton(onClick = onSecondary) { Text(secondaryLabel) }
                    }
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                }
            }
        )
    }
}

/** Wechsel-Auswahl für einen angetippten Spieler (Feld → Bank-Partner, Bank → Feld-Partner). */
@Composable
internal fun SubstitutionPicker(
    asBottomSheet: Boolean,
    tappedPlayer: Player,
    options: SubstitutionOptions,
    snapshot: DatabaseSnapshot,
    gameId: Int,
    onCourtCount: Int,
    onPickPartner: (Int) -> Unit,
    onActAlone: () -> Unit,
    onDismiss: () -> Unit,
    secondaryLabel: String? = null,
    onSecondary: () -> Unit = {}
) {
    val number = tappedPlayer.jerseyNumberHomeKit?.let { "#$it " } ?: ""
    SubstitutionPicker(
        asBottomSheet = asBottomSheet,
        title = if (options.isOnCourt) "Wechsel für $number${tappedPlayer.fullName}" else "$number${tappedPlayer.fullName} einwechseln für",
        partnerIds = options.partnerIds,
        emptyText = if (options.isOnCourt) "Niemand auf der Bank kann kommen." else "Kein Tausch möglich.",
        actAloneLabel = when {
            !options.canActAlone -> null
            options.isOnCourt -> "Nur auf die Bank"
            else -> "Aufs Feld (${onCourtCount + 1}/7)"
        },
        snapshot = snapshot,
        gameId = gameId,
        onPickPartner = onPickPartner,
        onActAlone = onActAlone,
        onDismiss = onDismiss,
        secondaryLabel = secondaryLabel,
        onSecondary = onSecondary
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubstitutionBottomSheet(
    title: String,
    actAloneLabel: String?,
    onActAlone: () -> Unit,
    onDismiss: () -> Unit,
    list: @Composable () -> Unit,
    secondaryLabel: String?,
    onSecondary: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SubstitutionTitle(title)
            Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                list()
            }
            // Einzelaktion ganz unten: liegt in der Daumenzone
            if (actAloneLabel != null) {
                FilledTonalButton(onClick = onActAlone, modifier = Modifier.fillMaxWidth()) {
                    Text(actAloneLabel)
                }
            }
            if (secondaryLabel != null) {
                TextButton(onClick = onSecondary, modifier = Modifier.fillMaxWidth()) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}

@Composable
private fun SubstitutionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun SubstitutionPartnerList(
    partnerIds: List<Int>,
    emptyText: String,
    snapshot: DatabaseSnapshot,
    gameId: Int,
    onPickPartner: (Int) -> Unit
) {
    val partners = partnerIds.mapNotNull { id -> snapshot.players.find { it.playerId == id } }
    if (partners.isEmpty()) {
        Text(
            text = emptyText,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    partners.forEachIndexed { index, partner ->
        if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        SubstitutionPartnerRow(
            player = partner,
            positionName = snapshot.positions.find { it.positionId == partner.positionId }?.positionName
                ?: partner.role.label,
            secondsPlayed = snapshot.playerGames
                .find { it.gameId == gameId && it.playerId == partner.playerId }?.playingTimeFormatted,
            onClick = { onPickPartner(partner.playerId) }
        )
    }
}

@Composable
private fun SubstitutionPartnerRow(
    player: Player,
    positionName: String,
    secondsPlayed: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier.widthIn(min = 44.dp)
        ) {
            Text(
                text = "#${player.jerseyNumberHomeKit ?: "-"}",
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Bold
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = player.fullName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = positionName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        secondsPlayed?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
