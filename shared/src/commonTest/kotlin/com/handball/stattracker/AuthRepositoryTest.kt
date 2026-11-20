package com.handball.stattracker

import com.handball.stattracker.config.SupabaseAuthSession
import com.handball.stattracker.model.AuthActionResult
import com.handball.stattracker.model.AuthState
import com.handball.stattracker.model.StoredAuthSession
import com.handball.stattracker.model.UserProfile
import com.handball.stattracker.model.UserRole
import com.handball.stattracker.repository.AuthApi
import com.handball.stattracker.repository.AuthRejectedException
import com.handball.stattracker.repository.AuthRepository
import com.handball.stattracker.repository.AuthSessionStore
import com.handball.stattracker.repository.AuthTokens
import com.handball.stattracker.repository.SignUpOutcome
import com.handball.stattracker.repository.SupabaseAuthService
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryStore(var session: StoredAuthSession? = null) : AuthSessionStore {
    override fun load() = session
    override fun save(session: StoredAuthSession?) { this.session = session }
}

private class FakeAuthApi : AuthApi {
    var now = 1_000L
    var profile: UserProfile? = UserProfile(id = "u1", email = "coach@club.de", role = UserRole.TRAINER, status = "ACTIVE")
    var offline = false
    var refreshRejected = false
    var signUpNeedsConfirmation = true
    var refreshCalls = 0

    private fun tokens(tag: String) = AuthTokens("access-$tag", "refresh-$tag", now + 3600, "u1", "coach@club.de")
    private fun net() { if (offline) throw IllegalStateException("offline") }

    override suspend fun signIn(email: String, password: String): AuthTokens {
        net()
        if (password != "Richtig123") throw AuthRejectedException("E-Mail oder Passwort ist falsch.")
        return tokens("login")
    }
    override suspend fun signUp(email: String, password: String, fullName: String, inviteCode: String?): SignUpOutcome {
        net()
        return if (signUpNeedsConfirmation) SignUpOutcome.ConfirmationRequired else SignUpOutcome.SignedIn(tokens("signup"))
    }
    override suspend fun refresh(refreshToken: String): AuthTokens {
        net(); refreshCalls++
        if (refreshRejected) throw AuthRejectedException("Deine Sitzung ist abgelaufen.")
        return tokens("refreshed")
    }
    override suspend fun sendPasswordReset(email: String) { net() }
    override suspend fun signOut(accessToken: String) { net() }
    override suspend fun fetchProfile(accessToken: String, userId: String): UserProfile? { net(); return profile }
    override suspend fun requestPlayerClaim(accessToken: String, userId: String, playerId: Int?) {
        net()
        profile = profile?.copy(claimPlayerId = playerId)
    }
}

class AuthRepositoryTest {

    private fun repo(api: FakeAuthApi, store: MemoryStore) = AuthRepository(api, store) { api.now }

    @Test
    fun signInWithActiveProfileSignsInAndStoresSession() = runBlocking<Unit> {
        val api = FakeAuthApi(); val store = MemoryStore()
        val auth = repo(api, store)
        auth.restore()
        assertIs<AuthState.SignedOut>(auth.state.value)

        assertIs<AuthActionResult.Success>(auth.signIn("coach@club.de", "Richtig123"))
        val state = assertIs<AuthState.SignedIn>(auth.state.value)
        assertEquals(UserRole.TRAINER, state.profile.role)
        assertEquals("access-login", store.session?.accessToken)
        assertEquals("access-login", SupabaseAuthSession.currentAccessToken())
    }

    @Test
    fun playerClaimRequestIsStoredAndReflectedInProfile() = runBlocking<Unit> {
        val api = FakeAuthApi(); val store = MemoryStore()
        val auth = repo(api, store)
        auth.signIn("coach@club.de", "Richtig123")

        assertIs<AuthActionResult.Success>(auth.requestPlayerClaim(7))
        assertEquals(7, assertIs<AuthState.SignedIn>(auth.state.value).profile.claimPlayerId)
        assertEquals(7, store.session?.profile?.claimPlayerId)

        assertIs<AuthActionResult.Success>(auth.requestPlayerClaim(null))
        assertNull(assertIs<AuthState.SignedIn>(auth.state.value).profile.claimPlayerId)
    }

    @Test
    fun playerClaimRequestWithoutSessionFails() = runBlocking<Unit> {
        val auth = repo(FakeAuthApi(), MemoryStore())
        assertIs<AuthActionResult.Error>(auth.requestPlayerClaim(7))
    }

    @Test
    fun wrongPasswordShowsGermanError() = runBlocking<Unit> {
        val auth = repo(FakeAuthApi(), MemoryStore())
        val result = assertIs<AuthActionResult.Error>(auth.signIn("coach@club.de", "falsch"))
        assertEquals("E-Mail oder Passwort ist falsch.", result.message)
    }

    @Test
    fun pendingOrMissingProfileLeadsToPendingApproval() = runBlocking<Unit> {
        val api = FakeAuthApi().apply { profile = profile!!.copy(status = "PENDING") }
        val auth = repo(api, MemoryStore())
        auth.signIn("coach@club.de", "Richtig123")
        assertIs<AuthState.PendingApproval>(auth.state.value)

        api.profile = null
        auth.reloadProfile()
        assertNull(assertIs<AuthState.PendingApproval>(auth.state.value).profile)

        api.profile = UserProfile(id = "u1", role = UserRole.SPIELER, status = "ACTIVE")
        auth.reloadProfile()
        assertIs<AuthState.SignedIn>(auth.state.value)
    }

    @Test
    fun restoreRefreshesExpiredTokenAndWorksOffline() = runBlocking<Unit> {
        val api = FakeAuthApi()
        val profile = api.profile!!
        val expired = StoredAuthSession("old", "r", expiresAtEpochSeconds = api.now - 10, userId = "u1", email = "coach@club.de", profile = profile)

        val store = MemoryStore(expired)
        repo(api, store).restore()
        assertEquals(1, api.refreshCalls)
        assertEquals("access-refreshed", store.session?.accessToken)

        // offline: gespeicherte Session + Profil werden genutzt
        api.offline = true
        val offlineAuth = repo(api, MemoryStore(expired))
        offlineAuth.restore()
        assertTrue(assertIs<AuthState.SignedIn>(offlineAuth.state.value).offline)
    }

    @Test
    fun rejectedRefreshSignsOutAndClearsSession() = runBlocking<Unit> {
        val api = FakeAuthApi().apply { refreshRejected = true }
        val store = MemoryStore(StoredAuthSession("old", "r", api.now - 10, "u1", "coach@club.de"))
        val auth = repo(api, store)
        auth.restore()
        assertNotNull(assertIs<AuthState.SignedOut>(auth.state.value).message)
        assertNull(store.session)
    }

    @Test
    fun signUpValidatesAndHandlesEmailConfirmation() = runBlocking<Unit> {
        val auth = repo(FakeAuthApi(), MemoryStore())
        assertIs<AuthActionResult.Error>(auth.signUp("a@b.de", "kurz", "Max Muster", null))
        assertIs<AuthActionResult.Error>(auth.signUp("a@b.de", "ohnezahlGROSS", "Max Muster", null))
        assertIs<AuthActionResult.Error>(auth.signUp("keine-mail", "Richtig123", "Max Muster", null))
        assertIs<AuthActionResult.Info>(auth.signUp("a@b.de", "Richtig123", "Max Muster", "CODE"))
        assertNull(SupabaseAuthSession.currentAccessToken().takeIf { auth.state.value is AuthState.SignedIn })
    }

    @Test
    fun signOutClearsEverything() = runBlocking<Unit> {
        val store = MemoryStore()
        val auth = repo(FakeAuthApi(), store)
        auth.signIn("coach@club.de", "Richtig123")
        auth.signOut()
        assertIs<AuthState.SignedOut>(auth.state.value)
        assertNull(store.session)
        assertNull(SupabaseAuthSession.currentAccessToken())
    }

    @Test
    fun goTrueErrorsAreTranslated() {
        assertEquals(
            "E-Mail oder Passwort ist falsch.",
            SupabaseAuthService.germanAuthError(400, """{"code":400,"error_code":"invalid_credentials","msg":"Invalid login credentials"}""")
        )
        assertEquals(
            "E-Mail oder Passwort ist falsch.",
            SupabaseAuthService.germanAuthError(400, """{"error":"invalid_grant","error_description":"Invalid login credentials"}""")
        )
        assertTrue(SupabaseAuthService.germanAuthError(400, """{"error_code":"email_not_confirmed"}""").contains("bestätige"))
        assertEquals("Anmeldung fehlgeschlagen (HTTP 500).", SupabaseAuthService.germanAuthError(500, "oops"))
    }
}
