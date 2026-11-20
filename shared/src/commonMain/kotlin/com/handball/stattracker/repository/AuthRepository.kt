package com.handball.stattracker.repository

import com.handball.stattracker.config.SupabaseAuthSession
import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.AuthState
import com.handball.stattracker.model.StoredAuthSession
import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.storage.PlatformStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.serialization.json.Json

/** Speicherort der Login-Session – im Test austauschbar. */
interface AuthSessionStore {
    fun load(): StoredAuthSession?
    fun save(session: StoredAuthSession?)
}

object PlatformAuthSessionStore : AuthSessionStore {
    private const val KEY = "supabase_auth_session"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun load(): StoredAuthSession? {
        val raw = PlatformStorage.readSecret(KEY) ?: migrateLegacyPlaintext() ?: return null
        return runCatching { json.decodeFromString<StoredAuthSession>(raw) }.getOrNull()
    }

    override fun save(session: StoredAuthSession?) {
        PlatformStorage.writeSecret(KEY, session?.let { json.encodeToString(StoredAuthSession.serializer(), it) })
        // Klartext-Rest aus früheren Versionen entfernen
        PlatformStorage.writeValue(KEY, null)
    }

    /** Frühere Versionen haben die Session unverschlüsselt gespeichert: einmal übernehmen und löschen. */
    private fun migrateLegacyPlaintext(): String? {
        val legacy = PlatformStorage.readValue(KEY) ?: return null
        PlatformStorage.writeSecret(KEY, legacy)
        if (PlatformStorage.readSecret(KEY) != null) PlatformStorage.writeValue(KEY, null)
        return legacy
    }
}

/**
 * Login-Zustand der App (Supabase Auth). Hält das Access Token in [SupabaseAuthSession]
 * aktuell, damit der Cloud-Sync es verwenden kann.
 */
class AuthRepository(
    private val api: AuthApi? = SupabaseAuthService.fromConfig { Clock.System.now().epochSeconds },
    private val store: AuthSessionStore = PlatformAuthSessionStore,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds }
) {
    private val _state = MutableStateFlow<AuthState>(if (api == null) AuthState.NotConfigured else AuthState.Loading)
    val state: StateFlow<AuthState> = _state.asStateFlow()

    private val refreshMutex = Mutex()

    /** Benutzerverwaltung für Admins; null, wenn Supabase nicht konfiguriert ist. */
    fun adminRepository(ownUserId: String): AdminRepository? =
        (api as? AdminApi)?.let { AdminRepository(it, ownUserId) }

    /** Beim App-Start: gespeicherte Session wiederherstellen (auch offline). */
    suspend fun restore() {
        val api = api ?: return
        val stored = store.load()
        if (stored == null) {
            _state.value = AuthState.SignedOut()
            return
        }
        var session = stored
        if (isExpiringSoon(session)) {
            try {
                session = session.withTokens(api.refresh(session.refreshToken))
            } catch (e: AuthRejectedException) {
                clearLocalSession()
                _state.value = AuthState.SignedOut("Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.")
                return
            } catch (e: Exception) {
                publishOffline(session)
                return
            }
        }
        try {
            publish(session, api.fetchProfile(session.accessToken, session.userId))
        } catch (e: AuthRejectedException) {
            clearLocalSession()
            _state.value = AuthState.SignedOut("Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.")
        } catch (e: Exception) {
            publishOffline(session)
        }
    }

    suspend fun signIn(email: String, password: String): AuthActionResult {
        val api = api ?: return notConfigured()
        val mail = email.trim()
        if (mail.isEmpty() || password.isEmpty()) return AuthActionResult.Error("Bitte E-Mail und Passwort eingeben.")
        return guarded {
            val tokens = api.signIn(mail, password)
            val session = tokens.toStoredSession()
            publish(session, api.fetchProfile(session.accessToken, session.userId))
            AuthActionResult.Success
        }
    }

    suspend fun signUp(email: String, password: String, fullName: String, inviteCode: String?): AuthActionResult {
        val api = api ?: return notConfigured()
        val mail = email.trim()
        val name = fullName.trim()
        validateRegistration(mail, password, name)?.let { return AuthActionResult.Error(it) }
        return guarded {
            when (val outcome = api.signUp(mail, password, name, inviteCode?.trim()?.takeIf { it.isNotEmpty() })) {
                is SignUpOutcome.SignedIn -> {
                    val session = outcome.tokens.toStoredSession()
                    publish(session, api.fetchProfile(session.accessToken, session.userId))
                    AuthActionResult.Success
                }
                SignUpOutcome.ConfirmationRequired -> AuthActionResult.Info(
                    "Konto angelegt. Bitte bestätige deine E-Mail-Adresse über den Link in der E-Mail und melde dich dann an."
                )
            }
        }
    }

    suspend fun sendPasswordReset(email: String): AuthActionResult {
        val api = api ?: return notConfigured()
        val mail = email.trim()
        if (mail.isEmpty()) return AuthActionResult.Error("Bitte gib deine E-Mail-Adresse ein.")
        return guarded {
            api.sendPasswordReset(mail)
            AuthActionResult.Info("Falls ein Konto existiert, wurde eine E-Mail zum Zurücksetzen des Passworts gesendet.")
        }
    }

    /** Profil neu laden (z. B. „Erneut prüfen“, nachdem ein Admin das Konto freigeschaltet hat). */
    suspend fun reloadProfile(): AuthActionResult {
        val api = api ?: return notConfigured()
        val session = currentSession() ?: return AuthActionResult.Error("Nicht angemeldet.")
        return guarded {
            val fresh = ensureFreshSession(session)
            publish(fresh, api.fetchProfile(fresh.accessToken, fresh.userId))
            AuthActionResult.Success
        }
    }

    /** Beantragt die Verknüpfung des Kontos mit einem Kadereintrag (Freigabe durch einen Admin). */
    suspend fun requestPlayerClaim(playerId: Int?): AuthActionResult {
        val api = api ?: return notConfigured()
        val session = currentSession() ?: return AuthActionResult.Error("Nicht angemeldet.")
        return guarded {
            val fresh = ensureFreshSession(session)
            api.requestPlayerClaim(fresh.accessToken, fresh.userId, playerId)
            publish(fresh, api.fetchProfile(fresh.accessToken, fresh.userId))
            AuthActionResult.Success
        }
    }

    suspend fun signOut() {
        val session = currentSession()
        val api = api
        if (api != null && session != null) {
            runCatching { api.signOut(session.accessToken) }
        }
        clearLocalSession()
        _state.value = AuthState.SignedOut()
    }

    /**
     * Hält die Session aktiv: erneuert das Access Token kurz vor Ablauf. Läuft, bis die
     * Coroutine abgebrochen wird oder der Benutzer abgemeldet ist.
     */
    suspend fun keepSessionFresh() {
        while (true) {
            val session = currentSession() ?: return
            val waitSeconds = (session.expiresAtEpochSeconds - REFRESH_MARGIN_SECONDS - nowEpochSeconds()).coerceAtLeast(5)
            delay(waitSeconds * 1000)
            val current = currentSession() ?: return
            try {
                val fresh = ensureFreshSession(current)
                val profile = api?.fetchProfile(fresh.accessToken, fresh.userId)
                publish(fresh, profile)
            } catch (e: AuthRejectedException) {
                clearLocalSession()
                _state.value = AuthState.SignedOut("Deine Sitzung ist abgelaufen. Bitte melde dich erneut an.")
                return
            } catch (e: Exception) {
                delay(30_000) // offline – später erneut versuchen
            }
        }
    }

    private suspend fun ensureFreshSession(session: StoredAuthSession): StoredAuthSession = refreshMutex.withLock {
        val latest = currentSession() ?: session
        if (!isExpiringSoon(latest)) return@withLock latest
        val api = api ?: return@withLock latest
        val refreshed = latest.withTokens(api.refresh(latest.refreshToken))
        store.save(refreshed)
        SupabaseAuthSession.updateAccessToken(refreshed.accessToken)
        refreshed
    }

    private fun currentSession(): StoredAuthSession? = when (val s = _state.value) {
        is AuthState.SignedIn -> s.session
        is AuthState.PendingApproval -> s.session
        else -> null
    }

    private fun publish(session: StoredAuthSession, profile: UserProfile?) {
        val stored = session.copy(profile = profile)
        store.save(stored)
        SupabaseAuthSession.updateAccessToken(stored.accessToken)
        _state.value = if (profile != null && profile.isActive) {
            AuthState.SignedIn(stored, profile)
        } else {
            AuthState.PendingApproval(stored, profile)
        }
    }

    private fun publishOffline(session: StoredAuthSession) {
        SupabaseAuthSession.updateAccessToken(session.accessToken)
        val profile = session.profile
        _state.value = if (profile != null && profile.isActive) {
            AuthState.SignedIn(session, profile, offline = true)
        } else {
            AuthState.PendingApproval(session, profile)
        }
    }

    private fun clearLocalSession() {
        store.save(null)
        SupabaseAuthSession.clear()
    }

    private fun isExpiringSoon(session: StoredAuthSession) =
        session.expiresAtEpochSeconds - nowEpochSeconds() <= REFRESH_MARGIN_SECONDS

    private fun StoredAuthSession.withTokens(tokens: AuthTokens) = copy(
        accessToken = tokens.accessToken,
        refreshToken = tokens.refreshToken,
        expiresAtEpochSeconds = tokens.expiresAtEpochSeconds,
        email = tokens.email.ifBlank { email }
    )

    private fun AuthTokens.toStoredSession() = StoredAuthSession(
        accessToken = accessToken,
        refreshToken = refreshToken,
        expiresAtEpochSeconds = expiresAtEpochSeconds,
        userId = userId,
        email = email
    )

    private suspend fun guarded(block: suspend () -> AuthActionResult): AuthActionResult = try {
        block()
    } catch (e: AuthRejectedException) {
        AuthActionResult.Error(e.message ?: "Anmeldung fehlgeschlagen.")
    } catch (e: Exception) {
        AuthActionResult.Error("Supabase ist nicht erreichbar. Bitte prüfe deine Internetverbindung.")
    }

    private fun notConfigured() = AuthActionResult.Error(
        "Supabase ist nicht konfiguriert (SUPABASE_URL / SUPABASE_PUBLIC_KEY in .env fehlen)."
    )

    companion object {
        private const val REFRESH_MARGIN_SECONDS = 60L

        /** Prüft die Registrierungsfelder; null = gültig, sonst Fehlermeldung. */
        fun validateRegistration(email: String, password: String, fullName: String): String? = when {
            fullName.isBlank() -> "Bitte gib deinen Namen ein."
            !Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$").matches(email) -> "Bitte gib eine gültige E-Mail-Adresse ein."
            password.length < 8 -> "Das Passwort muss mindestens 8 Zeichen lang sein."
            password.none { it.isDigit() } -> "Das Passwort muss mindestens eine Zahl enthalten."
            password.none { it.isUpperCase() } -> "Das Passwort muss mindestens einen Großbuchstaben enthalten."
            else -> null
        }
    }
}
