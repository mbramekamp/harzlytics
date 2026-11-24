package com.handball.stattracker.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.handball.stattracker.model.Competition
import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.model.Game
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.model.SeasonScope
import com.handball.stattracker.repository.HandballRepository
import com.handball.stattracker.repository.SeasonSelection

/** Im Handball stehen höchstens 16 Spieler im Aufgebot; darüber wird der Zähler rot. */
private const val MAX_SQUAD_PLAYERS = 16

/**
 * Neues Spiel in zwei Schritten: 1. Spieldaten, 2. Aufgebot (nur eigene Spiele).
 * Fremdpaarungen (zwei Gegner) haben kein Aufgebot, dafür direkt das Ergebnis.
 */
@Composable
internal fun NewGameForm(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    defaultScope: SeasonScope,
    onDismiss: () -> Unit,
    onCreated: (Game) -> Unit
) {
    var step by remember { mutableStateOf(1) }

    var isOwnGame by remember { mutableStateOf(true) }
    var competition by remember { mutableStateOf(if (defaultScope.isPreseason) Competition.FRIENDLY else Competition.LEAGUE) }
    var seasonId by remember {
        mutableStateOf(snapshot.seasons.find { it.seasonId == defaultScope.seasonId }?.seasonId ?: snapshot.seasons.firstOrNull()?.seasonId ?: 1)
    }
    var isHomeGame by remember { mutableStateOf(true) }
    var opponentId by remember { mutableStateOf(snapshot.opponentTeams.firstOrNull()?.opponentTeamId) }
    var secondOpponentId by remember { mutableStateOf(snapshot.opponentTeams.drop(1).firstOrNull()?.opponentTeamId) }
    var gameDate by remember { mutableStateOf(SeasonSelection.today()) }
    var location by remember { mutableStateOf(suggestLocation(snapshot, isHome = true, opponentId = opponentId)) }
    var locationEdited by remember { mutableStateOf(false) }
    var homeScore by remember { mutableStateOf("") }
    var awayScore by remember { mutableStateOf("") }
    var homeHalf by remember { mutableStateOf("") }
    var awayHalf by remember { mutableStateOf("") }
    var squad by remember { mutableStateOf(repository.suggestedSquad(snapshot)) }

    fun refreshLocation() {
        if (!locationEdited) location = suggestLocation(snapshot, isHomeGame, opponentId)
    }

    val detailsValid = opponentId != null && (isOwnGame || (secondOpponentId != null && secondOpponentId != opponentId))

    fun create() {
        val oppId = opponentId ?: return
        val created = repository.addGame(
            seasonId = seasonId,
            opponentId = oppId,
            secondOpponentId = if (isOwnGame) null else secondOpponentId,
            isHomeGame = isHomeGame,
            location = location,
            gameType = (if (isOwnGame) competition else Competition.LEAGUE).storageValue,
            gameDate = gameDate,
            homeHalftime = if (isOwnGame) 0 else homeHalf.toIntOrNull() ?: 0,
            awayHalftime = if (isOwnGame) 0 else awayHalf.toIntOrNull() ?: 0,
            homeScore = if (isOwnGame) 0 else homeScore.toIntOrNull() ?: 0,
            awayScore = if (isOwnGame) 0 else awayScore.toIntOrNull() ?: 0,
            nominatedPlayerIds = if (isOwnGame) squad else emptySet()
        )
        onCreated(created)
    }

    if (step == 1) {
        CreationFormFrame(
            title = "Neues Spiel",
            actionLabel = if (isOwnGame) "Weiter" else "Anlegen",
            actionEnabled = detailsValid,
            onAction = { if (isOwnGame) step = 2 else create() },
            onClose = onDismiss
        ) {
            val opponentOptions = snapshot.opponentTeams.map { it.opponentTeamId to it.opponentName }
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).then(FormContentPadding),
                verticalArrangement = FormFieldSpacing
            ) {
                SegmentedChoice(listOf(true to "Eigenes Spiel", false to "Fremdpaarung"), isOwnGame) { isOwnGame = it }
                if (isOwnGame) {
                    SegmentedChoice(listOf(Competition.LEAGUE to "Ligaspiel", Competition.FRIENDLY to "Testspiel"), competition) { competition = it }
                }
                DropdownField(
                    label = "Saison",
                    options = snapshot.seasons.map { it.seasonId to it.division },
                    selected = seasonId,
                    onSelect = { seasonId = it }
                )
                if (isOwnGame) {
                    SegmentedChoice(listOf(true to "Heim", false to "Auswärts"), isHomeGame) {
                        isHomeGame = it
                        refreshLocation()
                    }
                    DropdownField("Gegner", opponentOptions, opponentId, {
                        opponentId = it
                        refreshLocation()
                    })
                } else {
                    DropdownField("Heim", opponentOptions, opponentId, { opponentId = it })
                    DropdownField("Gast", opponentOptions, secondOpponentId, { secondOpponentId = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateField("Datum", gameDate, { gameDate = it }, Modifier.weight(1f))
                    OutlinedTextField(
                        value = location,
                        onValueChange = {
                            location = it
                            locationEdited = true
                        },
                        label = { Text("Spielort") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (!isOwnGame) {
                    FormSectionLabel("Ergebnis")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Tore Heim", homeScore, { homeScore = it }, Modifier.weight(1f))
                        NumberField("Tore Gast", awayScore, { awayScore = it }, Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField("Halbzeit Heim", homeHalf, { homeHalf = it }, Modifier.weight(1f))
                        NumberField("Halbzeit Gast", awayHalf, { awayHalf = it }, Modifier.weight(1f))
                    }
                }
            }
        }
    } else {
        val keepers = snapshot.players.filter { it.role == PlayerRole.GOALKEEPER }.sortedBy { it.jerseyNumberHomeKit ?: 99 }
        val fieldPlayers = snapshot.players.filter { it.role == PlayerRole.FIELD_PLAYER }.sortedBy { it.jerseyNumberHomeKit ?: 99 }
        val staff = snapshot.players.filter { it.role == PlayerRole.COACH }.sortedBy { it.lastName }
        val playerCount = squad.count { id -> snapshot.players.any { it.playerId == id && it.role != PlayerRole.COACH } }
        val staffCount = squad.size - playerCount

        CreationFormFrame(
            title = "Aufgebot",
            subtitle = "$playerCount Spieler · $staffCount Staff",
            actionLabel = "Anlegen",
            actionEnabled = detailsValid,
            onAction = { create() },
            onClose = onDismiss,
            showBack = true,
            onBack = { step = 1 }
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (playerCount > MAX_SQUAD_PLAYERS) {
                    Text(
                        "$playerCount von $MAX_SQUAD_PLAYERS Spielern",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { squad = snapshot.players.map { it.playerId }.toSet() }) { Text("Alle") }
                TextButton(onClick = { squad = emptySet() }) { Text("Keine") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            // LazyColumn: nur sichtbare Zeilen werden gezeichnet, Scrollen bleibt auch mit großem Kader flüssig
            LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
                listOf("Torhüter" to keepers, "Feldspieler" to fieldPlayers, "Staff" to staff).forEach { (header, people) ->
                    if (people.isNotEmpty()) {
                        item(key = "h_$header") {
                            Text(
                                header,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp)
                            )
                        }
                        items(people, key = { it.playerId }) { person ->
                            SquadRow(
                                person = person,
                                positionName = snapshot.positions.find { it.positionId == person.positionId }?.positionName?.substringBefore(" ")
                                    ?: person.role.label,
                                checked = person.playerId in squad,
                                onToggle = {
                                    squad = if (person.playerId in squad) squad - person.playerId else squad + person.playerId
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SquadRow(person: Player, positionName: String, checked: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).heightIn(min = 52.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = if (person.role == PlayerRole.COACH) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier.widthIn(min = 40.dp)
        ) {
            Text(
                text = person.jerseyNumberHomeKit?.let { "#$it" } ?: "–",
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = if (person.role == PlayerRole.COACH) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.inverseOnSurface
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(person.fullName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(positionName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}

/** Spielort-Vorschlag: Heim = Halle des letzten Heimspiels, auswärts = Halle des letzten Spiels beim Gegner, sonst dessen Ort. */
private fun suggestLocation(snapshot: DatabaseSnapshot, isHome: Boolean, opponentId: Int?): String {
    val ownGames = snapshot.games.filter { it.isOwnTeamGame }.sortedByDescending { it.gameDate }
    return if (isHome) {
        ownGames.firstOrNull { it.isHomeGame }?.location.orEmpty()
    } else {
        ownGames.firstOrNull { !it.isHomeGame && it.opponentId == opponentId }?.location
            ?: snapshot.opponentTeams.find { it.opponentTeamId == opponentId }?.opponentLocation.orEmpty()
    }
}
