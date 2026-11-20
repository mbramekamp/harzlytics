package com.handball.stattracker.repository

import com.handball.stattracker.config.SupabaseAuthSession
import com.handball.stattracker.model.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request-Body für `POST /rest/v1/rpc/sync_handball_snapshot`. */
@Serializable
private data class SyncSnapshotRpcRequest(
    @SerialName("incoming_snapshot") val incomingSnapshot: DatabaseSnapshot
)

/**
 * Cloud-Sync ist nicht möglich, weil keine angemeldete Benutzer-Session vorliegt
 * (oder Supabase das Token mit 401/403 abgelehnt hat). Der Aufrufer behandelt das
 * wie "offline": lokale Daten bleiben erhalten, es wird nichts überschrieben.
 */
class SupabaseAuthRequiredException(message: String) : IllegalStateException(message)

/**
 * Synchronisiert den lokalen Snapshot mit Supabase.
 *
 * Seit dem Härten von `supabase_schema.sql` gilt:
 *  - `anon` hat keinerlei Zugriff, `authenticated` darf nur lesen.
 *  - Schreiben geht ausschließlich über die RPC `public.sync_handball_snapshot`
 *    (SECURITY DEFINER). Sie vergleicht `lastUpdatedEpochMillis`, speichert den neuesten
 *    Stand in `app_snapshots`, spiegelt alle relationalen Tabellen und gibt den
 *    gültigen (neuesten) Snapshot zurück.
 *
 * Früher hat der Client direkt `GET/POST /rest/v1/app_snapshots` und Upserts auf alle
 * 12 relationalen Tabellen mit dem Publishable Key als Bearer (= Rolle `anon`) gemacht.
 * Das schlägt jetzt fehl und wurde durch den einzelnen RPC-Aufruf mit Benutzer-JWT ersetzt.
 */
internal object SupabaseSyncService {

    suspend fun syncSnapshotWithSupabase(
        httpClient: HttpClient,
        baseUrl: String,
        apiKey: String,
        localSnapshot: DatabaseSnapshot,
        accessToken: String? = SupabaseAuthSession.currentAccessToken()
    ): DatabaseSnapshot {
        val userJwt = accessToken?.trim()?.takeIf { it.isNotEmpty() }
        if (userJwt == null) {
            // Ohne Login kein Netzwerkaufruf: anon würde ohnehin mit 401 abgewiesen.
            val msg = "Supabase-Sync übersprungen: keine angemeldete Benutzer-Session (Login erforderlich)."
            println("[SupabaseSync] $msg")
            throw SupabaseAuthRequiredException(msg)
        }

        val response: HttpResponse = httpClient.post("$baseUrl/rest/v1/rpc/sync_handball_snapshot") {
            contentType(ContentType.Application.Json)
            // apikey = Publishable Key (Projekt-Identifikation), Authorization = Benutzer-JWT
            header("apikey", apiKey)
            header(HttpHeaders.Authorization, "Bearer $userJwt")
            setBody(SyncSnapshotRpcRequest(localSnapshot))
        }

        when {
            response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden -> {
                val msg = "Supabase-Sync abgelehnt (HTTP ${response.status.value}): Session ungültig/abgelaufen oder fehlende Berechtigung."
                println("[SupabaseSync] $msg")
                throw SupabaseAuthRequiredException(msg)
            }
            !response.status.isSuccess() -> {
                val msg = "Supabase RPC sync_handball_snapshot fehlgeschlagen: HTTP ${response.status.value}"
                println("[SupabaseSync] $msg")
                throw IllegalStateException(msg)
            }
        }

        return response.body()
    }
}
