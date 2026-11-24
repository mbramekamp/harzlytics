package com.handball.stattracker.ui.screens

import com.handball.stattracker.model.SeasonScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.*
import com.handball.stattracker.repository.HandballRepository

enum class CreationModalType {
    NONE,
    CHOOSER,
    NEW_GAME,
    NEW_PLAYER,
    NEW_OPPONENT,
    NEW_TRAINER,
    NEW_SEASON
}

@Composable
fun PlusCreationModalHost(
    modalType: CreationModalType,
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    defaultScope: SeasonScope,
    onSelectModal: (CreationModalType) -> Unit,
    onDismiss: () -> Unit,
    onGameCreated: (Game) -> Unit,
    onPlayerCreated: (Player) -> Unit,
    onSeasonCreated: (Season) -> Unit
) {
    when (modalType) {
        CreationModalType.NONE -> Unit

        CreationModalType.CHOOSER -> {
            AlertDialog(
                onDismissRequest = onDismiss,
                title = {
                    Text(
                        text = "Neu anlegen",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        PlusOptionCard(
                            title = "Spiel",
                            subtitle = "Spielbericht oder Ligapaarung",
                            icon = Icons.Default.SportsHandball,
                            onClick = { onSelectModal(CreationModalType.NEW_GAME) }
                        )
                        PlusOptionCard(
                            title = "Spieler",
                            subtitle = "Feldspieler oder Torwart",
                            icon = Icons.Default.PersonAdd,
                            onClick = { onSelectModal(CreationModalType.NEW_PLAYER) }
                        )
                        PlusOptionCard(
                            title = "Gegner",
                            subtitle = "Verein anlegen & Staffel zuweisen",
                            icon = Icons.Default.Shield,
                            onClick = { onSelectModal(CreationModalType.NEW_OPPONENT) }
                        )
                        PlusOptionCard(
                            title = "Trainer",
                            subtitle = "Trainerteam ergänzen",
                            icon = Icons.Default.Sports,
                            onClick = { onSelectModal(CreationModalType.NEW_TRAINER) }
                        )
                        PlusOptionCard(
                            title = "Saison",
                            subtitle = "Spielzeit / Staffel anlegen",
                            icon = Icons.Default.CalendarMonth,
                            onClick = { onSelectModal(CreationModalType.NEW_SEASON) }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = onDismiss) {
                        Text("Schließen")
                    }
                }
            )
        }

        CreationModalType.NEW_PLAYER -> {
            NewPlayerModal(
                repository = repository,
                snapshot = snapshot,
                onDismiss = onDismiss,
                onCreated = {
                    onPlayerCreated(it)
                    onDismiss()
                }
            )
        }

        CreationModalType.NEW_TRAINER -> {
            NewTrainerModal(
                repository = repository,
                onDismiss = onDismiss,
                onCreated = {
                    onPlayerCreated(it)
                    onDismiss()
                }
            )
        }

        CreationModalType.NEW_OPPONENT -> {
            NewOpponentModal(
                repository = repository,
                snapshot = snapshot,
                defaultSeasonId = defaultScope.seasonId,
                onDismiss = onDismiss
            )
        }

        CreationModalType.NEW_SEASON -> {
            NewSeasonModal(
                repository = repository,
                onDismiss = onDismiss,
                onCreated = {
                    onSeasonCreated(it)
                    onDismiss()
                }
            )
        }

        CreationModalType.NEW_GAME -> {
            NewGameForm(
                repository = repository,
                snapshot = snapshot,
                defaultScope = defaultScope,
                onDismiss = onDismiss,
                onCreated = {
                    onGameCreated(it)
                    onDismiss()
                }
            )
        }
    }
}

@Composable
private fun PlusOptionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NewPlayerModal(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    onDismiss: () -> Unit,
    onCreated: (Player) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(PlayerRole.FIELD_PLAYER) }
    var selectedPositionId by remember {
        mutableStateOf(snapshot.positions.firstOrNull { it.positionId == 3 }?.positionId ?: snapshot.positions.firstOrNull()?.positionId)
    }
    var jerseyHome by remember { mutableStateOf("") }
    var jerseyAway by remember { mutableStateOf("") }
    var customPositionName by remember { mutableStateOf("") }
    var showAddPosition by remember { mutableStateOf(false) }

    CreationFormFrame(
        title = "Neuer Spieler",
        actionLabel = "Anlegen",
        actionEnabled = firstName.isNotBlank() && lastName.isNotBlank(),
        onAction = {
            val created = repository.addPlayer(
                firstName = firstName,
                lastName = lastName,
                email = email,
                role = role,
                positionId = selectedPositionId,
                jerseyHome = jerseyHome.toIntOrNull(),
                jerseyAway = jerseyAway.toIntOrNull() ?: jerseyHome.toIntOrNull()
            )
            onCreated(created)
        },
        onClose = onDismiss
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).then(FormContentPadding),
            verticalArrangement = FormFieldSpacing
        ) {
            SegmentedChoice(listOf(PlayerRole.FIELD_PLAYER to "Feldspieler", PlayerRole.GOALKEEPER to "Torwart"), role) { newRole ->
                role = newRole
                if (newRole == PlayerRole.GOALKEEPER) {
                    selectedPositionId = snapshot.positions.firstOrNull { it.positionName.startsWith("TW") }?.positionId ?: selectedPositionId
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Vorname") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(lastName, { lastName = it }, label = { Text("Nachname") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(email, { email = it }, label = { Text("E-Mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            // Kurze, gleich lange Beschriftungen: beide Felder bleiben einzeilig und gleich hoch
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                NumberField("Nr. Heim", jerseyHome, { jerseyHome = it }, Modifier.weight(1f))
                NumberField("Nr. Auswärts", jerseyAway, { jerseyAway = it }, Modifier.weight(1f))
            }
            DropdownField(
                label = "Position",
                options = snapshot.positions.map { it.positionId to it.positionName },
                selected = selectedPositionId,
                onSelect = { selectedPositionId = it }
            )
            if (showAddPosition) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        customPositionName,
                        { customPositionName = it },
                        label = { Text("Neue Position") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    FilledTonalButton(
                        enabled = customPositionName.isNotBlank(),
                        onClick = {
                            val created = repository.addPosition(customPositionName)
                            selectedPositionId = created.positionId
                            customPositionName = ""
                            showAddPosition = false
                        }
                    ) { Text("Hinzufügen") }
                }
            } else {
                TextButton(onClick = { showAddPosition = true }) { Text("Eigene Position") }
            }
        }
    }
}

@Composable
private fun NewTrainerModal(
    repository: HandballRepository,
    onDismiss: () -> Unit,
    onCreated: (Player) -> Unit
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    CreationFormFrame(
        title = "Neuer Trainer",
        actionLabel = "Anlegen",
        actionEnabled = firstName.isNotBlank() && lastName.isNotBlank(),
        onAction = {
            onCreated(
                repository.addPlayer(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    role = PlayerRole.COACH,
                    positionId = null,
                    jerseyHome = null,
                    jerseyAway = null
                )
            )
        },
        onClose = onDismiss
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).then(FormContentPadding),
            verticalArrangement = FormFieldSpacing
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(firstName, { firstName = it }, label = { Text("Vorname") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(lastName, { lastName = it }, label = { Text("Nachname") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(email, { email = it }, label = { Text("E-Mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun NewOpponentModal(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    defaultSeasonId: Int,
    onDismiss: () -> Unit
) {
    var opponentName by remember { mutableStateOf("") }
    var opponentLocation by remember { mutableStateOf("") }
    var selectedSeasonIds by remember { mutableStateOf(setOf(defaultSeasonId)) }

    CreationFormFrame(
        title = "Neuer Gegner",
        actionLabel = "Anlegen",
        actionEnabled = opponentName.isNotBlank() && opponentLocation.isNotBlank(),
        onAction = {
            repository.addOpponentWithSeasons(
                opponentName = opponentName,
                opponentLocation = opponentLocation,
                assignedSeasonIds = selectedSeasonIds
            )
            onDismiss()
        },
        onClose = onDismiss
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).then(FormContentPadding),
            verticalArrangement = FormFieldSpacing
        ) {
            OutlinedTextField(opponentName, { opponentName = it }, label = { Text("Verein") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(opponentLocation, { opponentLocation = it }, label = { Text("Ort") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            FormSectionLabel("Spielt in")
            Column {
                snapshot.seasons.forEach { season ->
                    val checked = season.seasonId in selectedSeasonIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedSeasonIds = if (checked) selectedSeasonIds - season.seasonId else selectedSeasonIds + season.seasonId
                        }
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                selectedSeasonIds = if (isChecked) selectedSeasonIds + season.seasonId else selectedSeasonIds - season.seasonId
                            }
                        )
                        Text(season.division)
                    }
                }
            }
        }
    }
}

@Composable
private fun NewSeasonModal(
    repository: HandballRepository,
    onDismiss: () -> Unit,
    onCreated: (Season) -> Unit
) {
    // Vorschlag: nächste Saison ab 1. September bis 31. Mai
    val year = com.handball.stattracker.repository.SeasonSelection.today().take(4).toIntOrNull() ?: 2026
    var division by remember { mutableStateOf("") }
    var startOfSeason by remember { mutableStateOf("$year-09-01") }
    var endOfSeason by remember { mutableStateOf("${year + 1}-05-31") }

    CreationFormFrame(
        title = "Neue Saison",
        actionLabel = "Anlegen",
        actionEnabled = division.isNotBlank() && startOfSeason <= endOfSeason,
        onAction = { onCreated(repository.addSeason(division, startOfSeason, endOfSeason)) },
        onClose = onDismiss
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()).then(FormContentPadding),
            verticalArrangement = FormFieldSpacing
        ) {
            OutlinedTextField(division, { division = it }, label = { Text("Staffel / Liga") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DateField("Beginn", startOfSeason, { startOfSeason = it }, Modifier.weight(1f))
                DateField("Ende", endOfSeason, { endOfSeason = it }, Modifier.weight(1f))
            }
        }
    }
}
