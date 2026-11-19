package com.handball.stattracker.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Vereinsrollen (entspricht `public.app_role` in Supabase). */
@Serializable
enum class UserRole(val label: String) {
    SPIELER("Spieler"),
    TRAINER("Trainer"),
    ADMIN("Admin")
}

/** Zeile aus `public.user_profiles`. */
@Serializable
data class UserProfile(
    val id: String,
    val email: String = "",
    @SerialName("full_name") val fullName: String = "",
    val role: UserRole = UserRole.SPIELER,
    @SerialName("player_id") val playerId: Int? = null,
    @SerialName("claim_player_id") val claimPlayerId: Int? = null,
    val status: String = "PENDING"
) {
    val isActive: Boolean get() = status == "ACTIVE"
    val canEdit: Boolean get() = isActive && role != UserRole.SPIELER
}

/** Lokal gespeicherte Supabase-Session (inkl. zuletzt bekanntem Profil für den Offline-Start). */
@Serializable
data class StoredAuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String,
    val profile: UserProfile? = null
)

sealed interface AuthState {
    /** Gespeicherte Session wird geprüft. */
    data object Loading : AuthState

    /** Supabase-URL / Public Key fehlen (`.env`). */
    data object NotConfigured : AuthState

    data class SignedOut(val message: String? = null) : AuthState

    /** Angemeldet, aber noch nicht vom Admin freigeschaltet (oder Profil fehlt). */
    data class PendingApproval(val session: StoredAuthSession, val profile: UserProfile?) : AuthState

    /** Angemeldet und freigeschaltet. [offline] = Session/Profil aus dem lokalen Speicher, Server nicht erreichbar. */
    data class SignedIn(
        val session: StoredAuthSession,
        val profile: UserProfile,
        val offline: Boolean = false
    ) : AuthState
}

/** Ergebnis einer Login-/Registrierungs-Aktion für die Anzeige im Login-Screen. */
sealed interface AuthActionResult {
    data object Success : AuthActionResult
    data class Info(val message: String) : AuthActionResult
    data class Error(val message: String) : AuthActionResult
}
