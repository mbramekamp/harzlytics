package com.handball.stattracker.ui.screens

import androidx.compose.foundation.clickable
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
    defaultSeasonId: Int,
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
                        text = "Neu anlegen (+)",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Bitte wähle aus, was du neu erfassen möchtest:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        PlusOptionCard(
                            title = "Neues Spiel",
                            subtitle = "Eigenes Spiel (inkl. Spieltagskader) oder Liga-Paarung anlegen",
                            icon = Icons.Default.SportsHandball,
                            onClick = { onSelectModal(CreationModalType.NEW_GAME) }
                        )
                        PlusOptionCard(
                            title = "Neuen Spieler",
                            subtitle = "Feldspieler oder Torwart mit Position & Heim-/Auswärtstrikot",
                            icon = Icons.Default.PersonAdd,
                            onClick = { onSelectModal(CreationModalType.NEW_PLAYER) }
                        )
                        PlusOptionCard(
                            title = "Neuen Gegner (mit Saison-Zuweisung)",
                            subtitle = "Gegnerverein anlegen und Saisons zuweisen (Replika der Liga)",
                            icon = Icons.Default.Shield,
                            onClick = { onSelectModal(CreationModalType.NEW_OPPONENT) }
                        )
                        PlusOptionCard(
                            title = "Neuer Trainer",
                            subtitle = "Trainer / Co-Trainer im Kader erfassen",
                            icon = Icons.Default.Sports,
                            onClick = { onSelectModal(CreationModalType.NEW_TRAINER) }
                        )
                        PlusOptionCard(
                            title = "Neue Saison",
                            subtitle = "Neue Spielzeit / Staffel anlegen",
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
                defaultSeasonId = defaultSeasonId,
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
            NewGameModal(
                repository = repository,
                snapshot = snapshot,
                defaultSeasonId = defaultSeasonId,
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
        shape = RoundedCornerShape(12.dp),
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
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.padding(10.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuen Spieler anlegen", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = role == PlayerRole.FIELD_PLAYER,
                        onClick = { role = PlayerRole.FIELD_PLAYER },
                        label = { Text("Feldspieler") }
                    )
                    FilterChip(
                        selected = role == PlayerRole.GOALKEEPER,
                        onClick = {
                            role = PlayerRole.GOALKEEPER
                            selectedPositionId = snapshot.positions.firstOrNull { it.positionName.startsWith("TW") }?.positionId
                                ?: selectedPositionId
                        },
                        label = { Text("Torwart") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = { firstName = it },
                        label = { Text("Vorname *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = { lastName = it },
                        label = { Text("Nachname *") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = jerseyHome,
                        onValueChange = { jerseyHome = it },
                        label = { Text("Trikotnr. Heim") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = jerseyAway,
                        onValueChange = { jerseyAway = it },
                        label = { Text("Trikotnr. Auswärts") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Position (POSITION):", fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { showAddPosition = !showAddPosition }) {
                        Text("+ Eigene Position")
                    }
                }

                if (showAddPosition) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customPositionName,
                            onValueChange = { customPositionName = it },
                            label = { Text("Neue Position (z.B. 'Abwehrspezialist')") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                if (customPositionName.isNotBlank()) {
                                    val created = repository.addPosition(customPositionName)
                                    selectedPositionId = created.positionId
                                    customPositionName = ""
                                    showAddPosition = false
                                }
                            }
                        ) {
                            Text("Hinzufügen")
                        }
                    }
                }

                // Positionen zur Auswahl
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    snapshot.positions.forEach { pos ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedPositionId = pos.positionId }
                        ) {
                            RadioButton(
                                selected = selectedPositionId == pos.positionId,
                                onClick = { selectedPositionId = pos.positionId }
                            )
                            Text(pos.positionName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = firstName.isNotBlank() && lastName.isNotBlank(),
                onClick = {
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
                }
            ) {
                Text("Spieler speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuen Trainer anlegen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Trainer werden im Datenmodell mit role='COACH' (ohne Pflicht-Trikotnummer/Position) geführt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Vorname *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Nachname *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("E-Mail") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = firstName.isNotBlank() && lastName.isNotBlank(),
                onClick = {
                    val coach = repository.addPlayer(
                        firstName = firstName,
                        lastName = lastName,
                        email = email,
                        role = PlayerRole.COACH,
                        positionId = null,
                        jerseyHome = null,
                        jerseyAway = null
                    )
                    onCreated(coach)
                }
            ) {
                Text("Trainer speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neuen Gegner (mit Saison-Zuweisung)", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Lege einen neuen Gegnerverein an und weise ihn direkt einer oder mehreren Saisons zu, um die Liga nachzubauen (SEASON_OPPONENT).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = opponentName,
                    onValueChange = { opponentName = it },
                    label = { Text("Name des Gegnervereins * (z.B. 'THW Kiel II')") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = opponentLocation,
                    onValueChange = { opponentLocation = it },
                    label = { Text("Ort / Stadt * (z.B. 'Kiel')") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Saison-Zuweisung (Liga-Replika):", fontWeight = FontWeight.SemiBold)
                snapshot.seasons.forEach { season ->
                    val checked = season.seasonId in selectedSeasonIds
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedSeasonIds = if (checked) {
                                    selectedSeasonIds - season.seasonId
                                } else {
                                    selectedSeasonIds + season.seasonId
                                }
                            }
                    ) {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = { isChecked ->
                                selectedSeasonIds = if (isChecked) {
                                    selectedSeasonIds + season.seasonId
                                } else {
                                    selectedSeasonIds - season.seasonId
                                }
                            }
                        )
                        Text(season.division)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = opponentName.isNotBlank() && opponentLocation.isNotBlank(),
                onClick = {
                    repository.addOpponentWithSeasons(
                        opponentName = opponentName,
                        opponentLocation = opponentLocation,
                        assignedSeasonIds = selectedSeasonIds
                    )
                    onDismiss()
                }
            ) {
                Text("Gegner speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun NewSeasonModal(
    repository: HandballRepository,
    onDismiss: () -> Unit,
    onCreated: (Season) -> Unit
) {
    var division by remember { mutableStateOf("") }
    var startOfSeason by remember { mutableStateOf("2026-09-01") }
    var endOfSeason by remember { mutableStateOf("2027-05-31") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neue Saison anlegen", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = division,
                    onValueChange = { division = it },
                    label = { Text("Staffel / Liga-Bezeichnung * (z.B. 'Regionalliga 2027/28')") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = startOfSeason,
                    onValueChange = { startOfSeason = it },
                    label = { Text("Saisonbeginn (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = endOfSeason,
                    onValueChange = { endOfSeason = it },
                    label = { Text("Saisonende (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = division.isNotBlank(),
                onClick = {
                    val created = repository.addSeason(division, startOfSeason, endOfSeason)
                    onCreated(created)
                }
            ) {
                Text("Saison anlegen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun NewGameModal(
    repository: HandballRepository,
    snapshot: DatabaseSnapshot,
    defaultSeasonId: Int,
    onDismiss: () -> Unit,
    onCreated: (Game) -> Unit
) {
    var seasonId by remember {
        mutableStateOf(snapshot.seasons.find { it.seasonId == defaultSeasonId }?.seasonId ?: snapshot.seasons.firstOrNull()?.seasonId ?: 1)
    }
    var isOwnTeamMatch by remember { mutableStateOf(true) }
    var isHomeGame by remember { mutableStateOf(true) }
    var opponentId by remember {
        mutableStateOf(snapshot.opponentTeams.firstOrNull()?.opponentTeamId ?: 1)
    }
    var secondOpponentId by remember {
        mutableStateOf(snapshot.opponentTeams.drop(1).firstOrNull()?.opponentTeamId ?: opponentId)
    }
    var location by remember { mutableStateOf("Löwen-Arena") }
    var gameType by remember { mutableStateOf("Ligaspiel") }
    var gameDate by remember { mutableStateOf("2026-10-24") }
    var homeScore by remember { mutableStateOf("0") }
    var awayScore by remember { mutableStateOf("0") }
    var homeHalf by remember { mutableStateOf("0") }
    var awayHalf by remember { mutableStateOf("0") }

    var nominatedIds by remember {
        mutableStateOf(snapshot.players.map { it.playerId }.toSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Neues Spiel anlegen", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Spielmodus: Eigenes Spiel vs. Fremdpaarung der Liga
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isOwnTeamMatch,
                        onClick = { isOwnTeamMatch = true },
                        label = { Text("Eigenes Spiel (${snapshot.ownClubName})") }
                    )
                    FilterChip(
                        selected = !isOwnTeamMatch,
                        onClick = { isOwnTeamMatch = false },
                        label = { Text("Ligaspiel zweier Gegner") }
                    )
                }

                // Saison-Wahl
                Text("Saison:", fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    snapshot.seasons.forEach { s ->
                        FilterChip(
                            selected = seasonId == s.seasonId,
                            onClick = { seasonId = s.seasonId },
                            label = { Text(s.division) }
                        )
                    }
                }

                if (isOwnTeamMatch) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = isHomeGame,
                            onClick = {
                                isHomeGame = true
                                location = "Löwen-Arena"
                            },
                            label = { Text("Heimspiel") }
                        )
                        FilterChip(
                            selected = !isHomeGame,
                            onClick = {
                                isHomeGame = false
                                val oppLoc = snapshot.opponentTeams.find { it.opponentTeamId == opponentId }?.opponentLocation
                                if (!oppLoc.isNullOrBlank()) location = "Sporthalle $oppLoc"
                            },
                            label = { Text("Auswärtsspiel") }
                        )
                    }
                }

                Text(
                    text = if (isOwnTeamMatch) "Gegner wählen:" else "Heim-Team (Gegner 1) wählen:",
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    snapshot.opponentTeams.forEach { opp ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { opponentId = opp.opponentTeamId }
                        ) {
                            RadioButton(
                                selected = opponentId == opp.opponentTeamId,
                                onClick = { opponentId = opp.opponentTeamId }
                            )
                            Text("${opp.opponentName} (${opp.opponentLocation})")
                        }
                    }
                }

                if (!isOwnTeamMatch) {
                    Text("Gast-Team (Gegner 2) wählen:", fontWeight = FontWeight.SemiBold)
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        snapshot.opponentTeams.forEach { opp ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { secondOpponentId = opp.opponentTeamId }
                            ) {
                                RadioButton(
                                    selected = secondOpponentId == opp.opponentTeamId,
                                    onClick = { secondOpponentId = opp.opponentTeamId }
                                )
                                Text("${opp.opponentName} (${opp.opponentLocation})")
                            }
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = gameDate,
                        onValueChange = { gameDate = it },
                        label = { Text("Datum (YYYY-MM-DD)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Halle / Spielort") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = gameType,
                    onValueChange = { gameType = it },
                    label = { Text("Spieltyp (z.B. Ligaspiel, Pokal, Testspiel)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = homeScore,
                        onValueChange = { homeScore = it },
                        label = { Text("Tore Heim") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = awayScore,
                        onValueChange = { awayScore = it },
                        label = { Text("Tore Gast") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = homeHalf,
                        onValueChange = { homeHalf = it },
                        label = { Text("HZ Heim") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = awayHalf,
                        onValueChange = { awayHalf = it },
                        label = { Text("HZ Gast") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isOwnTeamMatch) {
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Spieltagskader (${nominatedIds.size}/${snapshot.players.size})",
                            fontWeight = FontWeight.Bold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { nominatedIds = snapshot.players.map { it.playerId }.toSet() }) {
                                Text("Alle")
                            }
                            TextButton(onClick = { nominatedIds = emptySet() }) {
                                Text("Keine")
                            }
                        }
                    }

                    snapshot.players.forEach { p ->
                        val checked = p.playerId in nominatedIds
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    nominatedIds = if (checked) nominatedIds - p.playerId else nominatedIds + p.playerId
                                }
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { isChecked ->
                                    nominatedIds = if (isChecked) nominatedIds + p.playerId else nominatedIds - p.playerId
                                }
                            )
                            Text("${p.fullName} (${p.role.label})")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = snapshot.opponentTeams.isNotEmpty() && (isOwnTeamMatch || opponentId != secondOpponentId),
                onClick = {
                    val created = repository.addGame(
                        seasonId = seasonId,
                        opponentId = opponentId,
                        secondOpponentId = if (isOwnTeamMatch) null else secondOpponentId,
                        isHomeGame = isHomeGame,
                        location = location,
                        gameType = gameType,
                        gameDate = gameDate,
                        homeHalftime = homeHalf.toIntOrNull() ?: 0,
                        awayHalftime = awayHalf.toIntOrNull() ?: 0,
                        homeScore = homeScore.toIntOrNull() ?: 0,
                        awayScore = awayScore.toIntOrNull() ?: 0,
                        nominatedPlayerIds = if (isOwnTeamMatch) nominatedIds else emptySet()
                    )
                    onCreated(created)
                }
            ) {
                Text("Spiel anlegen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}
