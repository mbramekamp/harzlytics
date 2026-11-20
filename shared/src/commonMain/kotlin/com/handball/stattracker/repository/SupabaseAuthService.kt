package com.handball.stattracker.repository

import com.handball.stattracker.config.SupabaseConfig
import com.handball.stattracker.model.UserProfile
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Von Supabase abgelehnte Anfrage (falsches Passwort, Token ungültig, ...). Nachricht ist anzeigbar (Deutsch). */
class AuthRejectedException(message: String) : Exception(message)

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val userId: String,
    val email: String
)

sealed interface SignUpOutcome {
    data class SignedIn(val tokens: AuthTokens) : SignUpOutcome
    data object ConfirmationRequired : SignUpOutcome
}

/** Schnittstelle zu Supabase Auth (GoTrue) + Profil-Abfrage – im Test austauschbar. */
interface AuthApi {
    suspend fun signIn(email: String, password: String): AuthTokens
    suspend fun signUp(email: String, password: String, fullName: String, inviteCode: String?): SignUpOutcome
    suspend fun refresh(refreshToken: String): AuthTokens
    suspend fun sendPasswordReset(email: String)
    suspend fun signOut(accessToken: String)
    /** null = kein Profil vorhanden (z. B. SQL-Migration noch nicht ausgeführt). */
    suspend fun fetchProfile(accessToken: String, userId: String): UserProfile?
    /** Beantragt die Verknüpfung mit einem Kadereintrag (`claim_player_id`); null nimmt den Antrag zurück. */
    suspend fun requestPlayerClaim(accessToken: String, userId: String, playerId: Int?)
}

@Serializable
private data class GoTrueUser(val id: String, val email: String? = null)

@Serializable
private data class GoTrueSessionResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long? = null,
    @SerialName("expires_at") val expiresAt: Long? = null,
    val user: GoTrueUser? = null
)

/** Supabase Auth über REST (`/auth/v1/...`) und PostgREST (`/rest/v1/user_profiles`). */
class SupabaseAuthService(
    private val baseUrl: String,
    private val apiKey: String,
    private val nowEpochSeconds: () -> Long,
    private val httpClient: HttpClient = defaultClient()
) : AuthApi {

    override suspend fun signIn(email: String, password: String): AuthTokens {
        val response = httpClient.post("$baseUrl/auth/v1/token?grant_type=password") {
            authHeaders()
            setBody(buildJsonObject { put("email", email); put("password", password) })
        }
        return parseTokens(response)
    }

    override suspend fun signUp(email: String, password: String, fullName: String, inviteCode: String?): SignUpOutcome {
        val response = httpClient.post("$baseUrl/auth/v1/signup") {
            authHeaders()
            setBody(buildJsonObject {
                put("email", email)
                put("password", password)
                putJsonObject("data") {
                    put("full_name", fullName)
                    if (!inviteCode.isNullOrBlank()) put("invite_code", inviteCode)
                }
            })
        }
        ensureSuccess(response)
        val text = response.bodyAsText()
        val session = runCatching { json.decodeFromString<GoTrueSessionResponse>(text) }.getOrNull()
        return if (session?.accessToken != null) {
            SignUpOutcome.SignedIn(session.toTokens())
        } else {
            SignUpOutcome.ConfirmationRequired
        }
    }

    override suspend fun refresh(refreshToken: String): AuthTokens {
        val response = httpClient.post("$baseUrl/auth/v1/token?grant_type=refresh_token") {
            authHeaders()
            setBody(buildJsonObject { put("refresh_token", refreshToken) })
        }
        return parseTokens(response)
    }

    override suspend fun sendPasswordReset(email: String) {
        val response = httpClient.post("$baseUrl/auth/v1/recover") {
            authHeaders()
            setBody(buildJsonObject { put("email", email) })
        }
        ensureSuccess(response)
    }

    override suspend fun signOut(accessToken: String) {
        httpClient.post("$baseUrl/auth/v1/logout") {
            authHeaders(accessToken)
        }
    }

    override suspend fun fetchProfile(accessToken: String, userId: String): UserProfile? {
        val response = httpClient.get("$baseUrl/rest/v1/user_profiles") {
            authHeaders(accessToken)
            parameter("id", "eq.$userId")
            parameter("select", "*")
        }
        // 404 = Tabelle existiert nicht (Migration fehlt) -> wie "kein Profil" behandeln
        if (response.status == HttpStatusCode.NotFound) return null
        ensureSuccess(response)
        return json.decodeFromString<List<UserProfile>>(response.bodyAsText()).firstOrNull()
    }

    override suspend fun requestPlayerClaim(accessToken: String, userId: String, playerId: Int?) {
        val response = httpClient.patch("$baseUrl/rest/v1/user_profiles") {
            authHeaders(accessToken)
            parameter("id", "eq.$userId")
            setBody(buildJsonObject { put("claim_player_id", playerId?.let { JsonPrimitive(it) } ?: JsonNull) })
        }
        ensureSuccess(response)
    }

    private fun HttpRequestBuilder.authHeaders(userJwt: String? = null) {
        contentType(ContentType.Application.Json)
        header("apikey", apiKey)
        if (userJwt != null) header(HttpHeaders.Authorization, "Bearer $userJwt")
    }

    private suspend fun parseTokens(response: HttpResponse): AuthTokens {
        ensureSuccess(response)
        return json.decodeFromString<GoTrueSessionResponse>(response.bodyAsText()).toTokens()
    }

    private fun GoTrueSessionResponse.toTokens(): AuthTokens {
        val access = accessToken ?: throw AuthRejectedException("Ungültige Antwort von Supabase (kein Token).")
        val refresh = refreshToken ?: throw AuthRejectedException("Ungültige Antwort von Supabase (kein Refresh-Token).")
        val u = user ?: throw AuthRejectedException("Ungültige Antwort von Supabase (kein Benutzer).")
        return AuthTokens(
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = expiresAt ?: (nowEpochSeconds() + (expiresIn ?: 3600L)),
            userId = u.id,
            email = u.email.orEmpty()
        )
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.isSuccess()) return
        val body = runCatching { response.bodyAsText() }.getOrDefault("")
        throw AuthRejectedException(germanAuthError(response.status.value, body))
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        private fun defaultClient() = HttpClient {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }

        /** Erstellt den Service aus `.env` / Build-Konfiguration; null, wenn Supabase nicht konfiguriert ist. */
        fun fromConfig(nowEpochSeconds: () -> Long): SupabaseAuthService? {
            val url = SupabaseConfig.resolvedProjectUrl()
            val key = SupabaseConfig.resolvedApiKey()
            if (url.isBlank() || key.isBlank()) return null
            return SupabaseAuthService(url, key, nowEpochSeconds)
        }

        /** Übersetzt GoTrue-Fehler (`error_code` / `code` / `msg` / `error_description`) in deutsche Meldungen. */
        fun germanAuthError(status: Int, body: String): String {
            val obj: JsonObject? = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull()
            fun field(name: String) = runCatching { obj?.get(name)?.jsonPrimitive?.contentOrNull }.getOrNull()
            val code = (field("error_code") ?: field("code") ?: field("error")).orEmpty().lowercase()
            val msg = (field("msg") ?: field("error_description") ?: field("message")).orEmpty()
            val lowerMsg = msg.lowercase()
            return when {
                code == "invalid_credentials" || code == "invalid_grant" && "credentials" in lowerMsg ||
                    "invalid login credentials" in lowerMsg -> "E-Mail oder Passwort ist falsch."
                code == "email_not_confirmed" || "email not confirmed" in lowerMsg ->
                    "Bitte bestätige zuerst deine E-Mail-Adresse (Link in der Bestätigungs-E-Mail)."
                code == "user_already_exists" || code == "email_exists" || "already registered" in lowerMsg ->
                    "Für diese E-Mail-Adresse gibt es bereits ein Konto."
                code == "weak_password" || "password should" in lowerMsg -> "Das Passwort ist zu schwach."
                code.contains("rate_limit") || status == 429 -> "Zu viele Versuche. Bitte warte kurz und versuche es erneut."
                code == "signup_disabled" -> "Registrierung ist im Supabase-Projekt deaktiviert."
                code == "refresh_token_not_found" || code == "refresh_token_already_used" || code == "session_not_found" ->
                    "Deine Sitzung ist abgelaufen. Bitte melde dich erneut an."
                code == "validation_failed" || code == "email_address_invalid" -> "Bitte gib eine gültige E-Mail-Adresse ein."
                msg.isNotBlank() -> "Anmeldung fehlgeschlagen: $msg"
                else -> "Anmeldung fehlgeschlagen (HTTP $status)."
            }
        }
    }
}
