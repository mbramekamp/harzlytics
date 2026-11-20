package com.handball.stattracker.repository

import com.handball.stattracker.config.SupabaseAuthSession
import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.Player
import com.handball.stattracker.model.PlayerRole
import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Admin-Zugriff auf `public.user_profiles` (RLS erlaubt das nur der Rolle ADMIN). */
interface AdminApi {
    suspend fun listProfiles(accessToken: String): List<UserProfile>
    suspend fun patchProfile(accessToken: String, userId: String, patch: JsonObject)
}

data class AdminState(
    val profiles: List<UserProfile> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

/** Profile nach Bearbeitungsbedarf gruppiert (für die Verwaltungsansicht). */
data class AdminSections(
    val pending: List<UserProfile>,
    val claims: List<UserProfile>,
    val members: List<UserProfile>,
    val suspended: List<UserProfile>
)

/**
 * Benutzerverwaltung für Admins: Konten freischalten oder sperren, Rollen ändern und
 * Verknüpfungen mit Kadereinträgen bestätigen. Die Berechtigung erzwingt der Server;
 * [ownUserId] schützt zusätzlich davor, sich selbst auszusperren.
 */
class AdminRepository(
    private val api: AdminApi,
    private val ownUserId: String,
    private val accessToken: () -> String? = { SupabaseAuthSession.currentAccessToken() }
) {
    private val _state = MutableStateFlow(AdminState())
    val state: StateFlow<AdminState> = _state.asStateFlow()

    suspend fun reload(): AuthActionResult {
        val token = accessToken() ?: return AuthActionResult.Error("Nicht angemeldet.")
        _state.value = _state.value.copy(loading = true, error = null)
        return try {
            val profiles = api.listProfiles(token)
            _state.value = AdminState(profiles = profiles)
            AuthActionResult.Success
        } catch (e: AuthRejectedException) {
            fail(e.message ?: "Keine Berechtigung.")
        } catch (e: Exception) {
            fail("Benutzer konnten nicht geladen werden. Bitte prüfe deine Internetverbindung.")
        }
    }

    /**
     * Schaltet ein Konto frei (PENDING/SUSPENDED → ACTIVE), setzt die Rolle und verknüpft optional
     * gleich den Kadereintrag [playerId] (ein offener Antrag des Kontos wird damit erledigt).
     */
    suspend fun approve(userId: String, role: UserRole, playerId: Int? = null): AuthActionResult =
        protectedChange(userId) {
            if (playerId != null && _state.value.profiles.any { it.playerId == playerId && it.id != userId }) {
                return@protectedChange AuthActionResult.Error("Dieser Kadereintrag ist bereits mit einem anderen Konto verknüpft.")
            }
            val fields = buildMap<String, JsonElement> {
                put("status", JsonPrimitive("ACTIVE"))
                put("role", JsonPrimitive(role.name))
                if (playerId != null) {
                    put("player_id", JsonPrimitive(playerId))
                    put("claim_player_id", JsonNull)
                }
            }
            update(userId, fields) {
                if (playerId != null) it.copy(status = "ACTIVE", role = role, playerId = playerId, claimPlayerId = null)
                else it.copy(status = "ACTIVE", role = role)
            }
        }

    suspend fun suspend(userId: String): AuthActionResult =
        protectedChange(userId) {
            update(userId, mapOf("status" to JsonPrimitive("SUSPENDED"))) { it.copy(status = "SUSPENDED") }
        }

    suspend fun setRole(userId: String, role: UserRole): AuthActionResult =
        protectedChange(userId) {
            update(userId, mapOf("role" to JsonPrimitive(role.name))) { it.copy(role = role) }
        }

    /** Bestätigt den beantragten Kadereintrag: `claim_player_id` wird zu `player_id`. */
    suspend fun confirmClaim(userId: String): AuthActionResult {
        val profile = find(userId) ?: return AuthActionResult.Error("Konto nicht gefunden.")
        val playerId = profile.claimPlayerId ?: return AuthActionResult.Error("Es liegt kein Antrag vor.")
        if (_state.value.profiles.any { it.playerId == playerId && it.id != userId }) {
            return AuthActionResult.Error("Dieser Kadereintrag ist bereits mit einem anderen Konto verknüpft.")
        }
        return update(userId, mapOf("player_id" to JsonPrimitive(playerId), "claim_player_id" to JsonNull)) {
            it.copy(playerId = playerId, claimPlayerId = null)
        }
    }

    suspend fun rejectClaim(userId: String): AuthActionResult =
        update(userId, mapOf("claim_player_id" to JsonNull)) { it.copy(claimPlayerId = null) }

    suspend fun unlinkPlayer(userId: String): AuthActionResult =
        update(userId, mapOf("player_id" to JsonNull)) { it.copy(playerId = null) }

    private fun find(userId: String) = _state.value.profiles.find { it.id == userId }

    private suspend fun protectedChange(userId: String, block: suspend () -> AuthActionResult): AuthActionResult =
        if (userId == ownUserId) {
            AuthActionResult.Error("Dein eigenes Konto kannst du hier nicht ändern.")
        } else {
            block()
        }

    private suspend fun update(
        userId: String,
        fields: Map<String, JsonElement>,
        applyLocally: (UserProfile) -> UserProfile
    ): AuthActionResult {
        val token = accessToken() ?: return AuthActionResult.Error("Nicht angemeldet.")
        return try {
            api.patchProfile(token, userId, JsonObject(fields))
            _state.value = _state.value.copy(
                profiles = _state.value.profiles.map { if (it.id == userId) applyLocally(it) else it },
                error = null
            )
            AuthActionResult.Success
        } catch (e: AuthRejectedException) {
            AuthActionResult.Error(e.message ?: "Keine Berechtigung.")
        } catch (e: Exception) {
            AuthActionResult.Error("Änderung nicht möglich. Bitte prüfe deine Internetverbindung.")
        }
    }

    private fun fail(message: String): AuthActionResult {
        _state.value = _state.value.copy(loading = false, error = message)
        return AuthActionResult.Error(message)
    }

    companion object {
        /** Spieler (ohne Trainer), die noch zu keinem Konto gehören, nach Nachname sortiert. */
        fun availablePlayers(players: List<Player>, profiles: List<UserProfile>): List<Player> {
            val linked = profiles.mapNotNull { it.playerId }.toSet()
            return players
                .filter { it.role != PlayerRole.COACH && it.playerId !in linked }
                .sortedWith(compareBy({ it.lastName.lowercase() }, { it.firstName.lowercase() }))
        }

        /**
         * Schlägt für [profile] den Kadereintrag vor: 1. der beantragte, 2. eindeutiger E-Mail-Treffer,
         * 3. eindeutiger Namenstreffer (Reihenfolge, Groß-/Kleinschreibung, Bindestrich und Umlaute egal).
         * Nur freie Spieler kommen infrage; bei keinem oder mehreren Treffern null.
         */
        fun suggestPlayerId(profile: UserProfile, players: List<Player>, profiles: List<UserProfile>): Int? {
            val free = availablePlayers(players, profiles.filter { it.id != profile.id })
            profile.claimPlayerId?.let { claimed -> free.find { it.playerId == claimed }?.let { return it.playerId } }

            val mail = profile.email.trim().lowercase()
            if (mail.isNotEmpty()) {
                free.filter { it.email.trim().lowercase() == mail }.singleOrNull()?.let { return it.playerId }
            }
            val wanted = nameTokens(profile.fullName)
            if (wanted.isEmpty()) return null
            return free.filter { nameTokens("${it.firstName} ${it.lastName}") == wanted }.singleOrNull()?.playerId
        }

        private fun nameTokens(name: String): List<String> =
            name.lowercase()
                .replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
                .split(Regex("[^a-z0-9]+"))
                .filter { it.isNotEmpty() }
                .sorted()

        fun sections(profiles: List<UserProfile>): AdminSections {
            val byName = compareBy<UserProfile> { it.fullName.ifBlank { it.email }.lowercase() }
            return AdminSections(
                pending = profiles.filter { it.status == "PENDING" }.sortedWith(byName),
                claims = profiles.filter { it.status == "ACTIVE" && it.claimPlayerId != null && it.playerId == null }.sortedWith(byName),
                members = profiles.filter { it.status == "ACTIVE" }.sortedWith(byName),
                suspended = profiles.filter { it.status == "SUSPENDED" }.sortedWith(byName)
            )
        }
    }
}
