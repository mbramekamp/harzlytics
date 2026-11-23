package com.handball.stattracker.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.model.UserRole
import kotlinx.coroutines.launch

/** Kleines Rollen-Abzeichen (`Spieler` / `Trainer` / `Admin`). */
@Composable
fun RoleBadge(role: UserRole, offline: Boolean = false, modifier: Modifier = Modifier) {
    val container = when (role) {
        UserRole.ADMIN -> MaterialTheme.colorScheme.inverseSurface
        UserRole.TRAINER -> MaterialTheme.colorScheme.secondaryContainer
        UserRole.SPIELER -> MaterialTheme.colorScheme.surfaceVariant
    }
    Surface(shape = MaterialTheme.shapes.small, color = container, contentColor = contentColorFor(container), modifier = modifier) {
        Text(
            text = if (offline) "${role.label} · offline" else role.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/**
 * Konto-Dialog: Profil, Rolle, Verknüpfung mit einem Kadereintrag und Abmelden.
 * Die Verknüpfung ist ein Antrag (`claim_player_id`); ein Admin gibt sie frei.
 */
@Composable
fun AccountDialog(
    profile: UserProfile,
    email: String,
    offline: Boolean,
    players: List<Player>,
    onRequestClaim: suspend (Int?) -> AuthActionResult,
    onSignOut: () -> Unit,
    darkTheme: Boolean,
    onToggleDarkTheme: () -> Unit,
    onOpenClub: (() -> Unit)? = null,
    onOpenAdmin: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }

    fun nameOf(playerId: Int?): String? = players.find { it.playerId == playerId }
        ?.let { "${it.firstName} ${it.lastName}".trim() }

    fun claim(playerId: Int?) {
        scope.launch {
            busy = true
            message = when (val result = onRequestClaim(playerId)) {
                is AuthActionResult.Success -> if (playerId == null) "Antrag zurückgezogen." else "Antrag gesendet."
                is AuthActionResult.Info -> result.message
                is AuthActionResult.Error -> result.message
            }
            busy = false
            choosing = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(profile.fullName.ifBlank { email }, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    RoleBadge(profile.role, offline)
                    Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val linked = nameOf(profile.playerId)
                val requested = nameOf(profile.claimPlayerId)

                Text("Kadereintrag", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                when {
                    linked != null -> Text("Verknüpft mit $linked")
                    requested != null -> {
                        Text("Angefragt: $requested (wartet auf Freigabe)")
                        TextButton(enabled = !busy && !offline, onClick = { claim(null) }) { Text("Antrag zurückziehen") }
                    }
                    choosing -> {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(players.filter { it.role != com.handball.stattracker.model.PlayerRole.COACH }) { p ->
                                TextButton(
                                    enabled = !busy,
                                    onClick = { claim(p.playerId) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("${p.firstName} ${p.lastName}".trim(), modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                    else -> OutlinedButton(enabled = !busy && !offline && players.isNotEmpty(), onClick = { choosing = true }) {
                        Text("Mit Kadereintrag verknüpfen")
                    }
                }
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dunkles Design", modifier = Modifier.weight(1f))
                    Switch(checked = darkTheme, onCheckedChange = { onToggleDarkTheme() })
                }
                if (onOpenClub != null) {
                    FilledTonalButton(onClick = onOpenClub) { Text("Verein verwalten") }
                }
                if (onOpenAdmin != null) {
                    FilledTonalButton(enabled = !offline, onClick = onOpenAdmin) { Text("Benutzer verwalten") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSignOut) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Abmelden")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Schließen") } }
    )
}
