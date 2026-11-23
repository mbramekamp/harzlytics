package com.handball.stattracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.model.UserRole
import com.handball.stattracker.repository.AdminRepository
import kotlinx.coroutines.launch

/** Benutzerverwaltung (nur Admin): Konten freischalten, Rollen ändern, Kader-Verknüpfungen bestätigen. */
@Composable
fun AdminDialog(
    admin: AdminRepository,
    ownUserId: String,
    players: List<Player>,
    onDismiss: () -> Unit
) {
    val state by admin.state.collectAsState()
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(admin) { admin.reload() }

    fun act(block: suspend () -> AuthActionResult) {
        scope.launch {
            busy = true
            message = (block() as? AuthActionResult.Error)?.message
            busy = false
        }
    }

    fun playerName(id: Int?): String? = players.find { it.playerId == id }
        ?.let { "${it.firstName} ${it.lastName}".trim() }

    val sections = remember(state.profiles) { AdminRepository.sections(state.profiles) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(14.dp),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.94f).widthIn(max = 720.dp).fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Benutzer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(enabled = !busy && !state.loading, onClick = { act { admin.reload() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Neu laden")
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = "Schließen") }
                }

                (message ?: state.error)?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (state.loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (sections.pending.isNotEmpty()) {
                        item { SectionTitle("Warten auf Freigabe (${sections.pending.size})") }
                        items(sections.pending, key = { "p-" + it.id }) { profile ->
                            PendingRow(
                                profile = profile,
                                available = AdminRepository.availablePlayers(players, state.profiles),
                                suggested = AdminRepository.suggestPlayerId(profile, players, state.profiles),
                                enabled = !busy
                            ) { role, playerId -> act { admin.approve(profile.id, role, playerId) } }
                        }
                    }
                    if (sections.claims.isNotEmpty()) {
                        item { SectionTitle("Kader-Anträge (${sections.claims.size})") }
                        items(sections.claims, key = { "c-" + it.id }) { profile ->
                            ClaimRow(
                                profile = profile,
                                wanted = playerName(profile.claimPlayerId) ?: "Spieler ${profile.claimPlayerId}",
                                enabled = !busy,
                                onConfirm = { act { admin.confirmClaim(profile.id) } },
                                onReject = { act { admin.rejectClaim(profile.id) } }
                            )
                        }
                    }
                    item { SectionTitle("Mitglieder (${sections.members.size})") }
                    items(sections.members, key = { "m-" + it.id }) { profile ->
                        MemberRow(
                            profile = profile,
                            isSelf = profile.id == ownUserId,
                            linkedName = playerName(profile.playerId),
                            enabled = !busy,
                            onRole = { role -> act { admin.setRole(profile.id, role) } },
                            onSuspend = { act { admin.suspend(profile.id) } },
                            onUnlink = { act { admin.unlinkPlayer(profile.id) } }
                        )
                    }
                    if (sections.suspended.isNotEmpty()) {
                        item { SectionTitle("Gesperrt (${sections.suspended.size})") }
                        items(sections.suspended, key = { "s-" + it.id }) { profile ->
                            UserCard(profile) {
                                TextButton(enabled = !busy, onClick = { act { admin.approve(profile.id, profile.role) } }) {
                                    Text("Wieder freischalten")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
}

@Composable
private fun UserCard(profile: UserProfile, trailing: @Composable ColumnScope.() -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(profile.fullName.ifBlank { profile.email }, fontWeight = FontWeight.SemiBold)
                    Text(profile.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RoleBadge(profile.role)
            }
            trailing()
        }
    }
}

@Composable
private fun RoleChips(selected: UserRole, enabled: Boolean, onSelect: (UserRole) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        UserRole.entries.forEach { role ->
            FilterChip(
                selected = role == selected,
                enabled = enabled,
                onClick = { if (role != selected) onSelect(role) },
                label = { Text(role.label) }
            )
        }
    }
}

@Composable
private fun PendingRow(
    profile: UserProfile,
    available: List<Player>,
    suggested: Int?,
    enabled: Boolean,
    onApprove: (UserRole, Int?) -> Unit
) {
    var role by remember(profile.id) { mutableStateOf(UserRole.SPIELER) }
    // Der Vorschlag (Antrag, E-Mail, Name) ist vorgewählt, solange der Admin nicht selbst gewählt hat
    var picked by remember(profile.id) { mutableStateOf(false) }
    var playerId by remember(profile.id) { mutableStateOf(suggested) }
    LaunchedEffect(suggested) { if (!picked) playerId = suggested }

    UserCard(profile) {
        PlayerPicker(available, playerId, enabled) { picked = true; playerId = it }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) { RoleChips(role, enabled) { role = it } }
            Button(enabled = enabled, onClick = { onApprove(role, playerId) }) { Text("Freigeben") }
        }
    }
}

/** Auswahl des Kadereintrags beim Freigeben; zur Wahl stehen nur Spieler ohne Konto. */
@Composable
private fun PlayerPicker(available: List<Player>, selectedId: Int?, enabled: Boolean, onSelect: (Int?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val label = available.find { it.playerId == selectedId }
        ?.let { "Kader: ${it.firstName} ${it.lastName}".trim() } ?: "Kein Kadereintrag"
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Kadereintrag wählen")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("Kein Kadereintrag") }, onClick = { expanded = false; onSelect(null) })
            available.forEach { p ->
                DropdownMenuItem(
                    text = { Text("${p.firstName} ${p.lastName}".trim()) },
                    onClick = { expanded = false; onSelect(p.playerId) }
                )
            }
        }
    }
}

@Composable
private fun ClaimRow(profile: UserProfile, wanted: String, enabled: Boolean, onConfirm: () -> Unit, onReject: () -> Unit) {
    UserCard(profile) {
        Text("Möchte verknüpft werden mit $wanted")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = enabled, onClick = onConfirm) { Text("Bestätigen") }
            OutlinedButton(enabled = enabled, onClick = onReject) { Text("Ablehnen") }
        }
    }
}

@Composable
private fun MemberRow(
    profile: UserProfile,
    isSelf: Boolean,
    linkedName: String?,
    enabled: Boolean,
    onRole: (UserRole) -> Unit,
    onSuspend: () -> Unit,
    onUnlink: () -> Unit
) {
    UserCard(profile) {
        if (linkedName != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kader: $linkedName", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                TextButton(enabled = enabled, onClick = onUnlink) { Text("Lösen") }
            }
        }
        if (isSelf) {
            Text("Das bist du", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(modifier = Modifier.weight(1f)) { RoleChips(profile.role, enabled, onRole) }
                TextButton(enabled = enabled, onClick = onSuspend) { Text("Sperren") }
            }
        }
    }
}
