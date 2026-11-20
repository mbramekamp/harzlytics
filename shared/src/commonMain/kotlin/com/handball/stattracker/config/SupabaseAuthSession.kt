package com.handball.stattracker.config

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Hält das Access Token (JWT) der aktuell angemeldeten Supabase-Benutzer-Session.
 *
 * Seit dem Härten von `supabase_schema.sql` hat die Rolle `anon` keinen Zugriff mehr;
 * der Cloud-Sync braucht daher ein Benutzer-JWT (Rolle `authenticated`). Der
 * Publishable Key allein reicht nicht.
 *
 * TODO(Auth, siehe USER_FEATURE.md): Der Login-Flow (Supabase Auth) setzt hier das Token
 * per [updateAccessToken] und löscht es beim Logout/Ablauf per [clear].
 */
object SupabaseAuthSession {

    private val _accessToken = MutableStateFlow<String?>(null)
    val accessToken: StateFlow<String?> = _accessToken.asStateFlow()

    fun currentAccessToken(): String? = _accessToken.value?.trim()?.takeIf { it.isNotEmpty() }

    fun isAuthenticated(): Boolean = currentAccessToken() != null

    fun updateAccessToken(token: String?) {
        _accessToken.value = token?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun clear() {
        _accessToken.value = null
    }
}
