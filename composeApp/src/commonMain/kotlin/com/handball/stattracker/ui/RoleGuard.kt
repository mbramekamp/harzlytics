package com.handball.stattracker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import com.handball.stattracker.model.UserRole

/**
 * Rolle des angemeldeten Benutzers. Standard ist [UserRole.SPIELER] (nur Lesezugriff), damit
 * eine vergessene Bereitstellung nie versehentlich Bearbeitungsrechte freischaltet.
 */
val LocalUserRole = compositionLocalOf { UserRole.SPIELER }

/** Zeigt [content] nur für die erlaubten Rollen, sonst [fallback]. */
@Composable
fun RoleGuard(
    vararg allowedRoles: UserRole,
    fallback: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    if (LocalUserRole.current in allowedRoles) content() else fallback()
}

/** Kurzform für Trainer und Admin: Aktionen, die Daten ändern oder anlegen. */
@Composable
fun EditorOnly(fallback: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    RoleGuard(UserRole.TRAINER, UserRole.ADMIN, fallback = fallback, content = content)
}

@Composable
fun rememberCanEdit(): Boolean = LocalUserRole.current != UserRole.SPIELER
