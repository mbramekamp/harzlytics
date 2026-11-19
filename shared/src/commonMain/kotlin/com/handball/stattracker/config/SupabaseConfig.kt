package com.handball.stattracker.config

import com.handball.stattracker.storage.PlatformStorage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Lädt `SUPABASE_ANON_KEY`, `SUPABASE_PUBLIC_KEY` und `SUPABASE_URL`
 * aus der `.env`-Datei (sowohl zur Laufzeit über [PlatformStorage.readEnvVar] als auch
 * zur Build-Zeit über [SupabaseEnvGenerated]).
 */
object SupabaseConfig {

    val anonKey: String
        get() = PlatformStorage.readEnvVar("SUPABASE_ANON_KEY")
            ?.takeIf { it.isNotBlank() }
            ?: SupabaseEnvGenerated.SUPABASE_ANON_KEY

    val publicKey: String
        get() = PlatformStorage.readEnvVar("SUPABASE_PUBLIC_KEY")
            ?.takeIf { it.isNotBlank() }
            ?: SupabaseEnvGenerated.SUPABASE_PUBLIC_KEY

    val explicitUrl: String
        get() = PlatformStorage.readEnvVar("SUPABASE_URL")
            ?.takeIf { it.isNotBlank() }
            ?: SupabaseEnvGenerated.SUPABASE_URL

    /**
     * Liefert die Supabase Project URL:
     * 1. Aus `SUPABASE_URL` (falls gesetzt)
     * 2. Falls in `SUPABASE_PUBLIC_KEY` oder `SUPABASE_ANON_KEY` eine `https://`-URL steht
     * 3. Automatisch aus dem JWT-Payload (`"ref"`) von `SUPABASE_ANON_KEY` / `SUPABASE_PUBLIC_KEY`
     *    (`https://<ref>.supabase.co`)
     */
    fun resolvedProjectUrl(): String {
        val candidates = listOf(explicitUrl, publicKey, anonKey).map { it.trim() }
        candidates.firstOrNull { it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true) }
            ?.let { return it.trimEnd('/') }

        val jwtRef = extractProjectRefFromJwt(anonKey) ?: extractProjectRefFromJwt(publicKey)
        if (!jwtRef.isNullOrBlank()) {
            return "https://$jwtRef.supabase.co"
        }
        return ""
    }

    /**
     * Liefert den sicheren Client-API-Key für die KMP-App (Desktop, Web, iOS, Android).
     * Nur `sb_publishable_...` bzw. Nicht-Secret-Keys (`SUPABASE_PUBLIC_KEY`, dann
     * `SUPABASE_ANON_KEY`). `sb_secret_...`-Keys umgehen RLS und gehören nur in
     * Server-Backends – sie werden hier NIE zurückgegeben; dann ist das Ergebnis "".
     */
    fun resolvedApiKey(): String {
        val p = publicKey.trim()
        val a = anonKey.trim()
        return when {
            isClientSafeKey(p) -> p
            isClientSafeKey(a) -> a
            else -> ""
        }
    }

    private fun isClientSafeKey(key: String): Boolean =
        key.isNotEmpty() &&
            !key.startsWith("http", ignoreCase = true) &&
            !key.startsWith("sb_secret_", ignoreCase = true) &&
            // Legacy-JWT-Keys: der service_role-Key ist ebenfalls ein Secret
            !extractJwtClaim(key, "role").equals("service_role", ignoreCase = true)

    fun hasConfiguredKeys(): Boolean =
        resolvedApiKey().isNotBlank()

    private fun extractProjectRefFromJwt(token: String): String? = extractJwtClaim(token, "ref")

    @OptIn(ExperimentalEncodingApi::class)
    private fun extractJwtClaim(token: String, claim: String): String? {
        val trimmed = token.trim()
        val parts = trimmed.split('.')
        if (parts.size != 3) return null
        return runCatching {
            val payloadPart = parts[1]
            val padded = payloadPart.padEnd((payloadPart.length + 3) / 4 * 4, '=')
            val decodedBytes = Base64.UrlSafe.decode(padded)
            val jsonString = decodedBytes.decodeToString()
            val jsonObj = Json.parseToJsonElement(jsonString).jsonObject
            jsonObj[claim]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
