package com.handball.stattracker.storage

import com.handball.stattracker.model.DatabaseSnapshot
import kotlinx.serialization.json.Json

// Direkter Zugriff auf den Browser-Speicher (localStorage) ohne Zusatzbibliothek
private fun lsGet(key: String): String? = js("localStorage.getItem(key)")
private fun lsSet(key: String, value: String): Unit = js("localStorage.setItem(key, value)")
private fun lsRemove(key: String): Unit = js("localStorage.removeItem(key)")

/**
 * Web-Version (PWA): Daten im localStorage des Browsers, je Herkunft (Domain) getrennt.
 * Login-Token liegen dort unverschlüsselt (im Browser üblich, die Seite läuft nur über HTTPS).
 * Der Datenstand wird zusätzlich über Supabase gesichert; gelöschte Browserdaten lädt der nächste Sync neu.
 */
actual object PlatformStorage {
    private const val STORAGE_KEY = "handball_stat_tracker_offline_snapshot_v1"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    actual fun loadSnapshot(): DatabaseSnapshot? = runCatching {
        lsGet(STORAGE_KEY)?.let { json.decodeFromString<DatabaseSnapshot>(it) }
    }.getOrNull()

    actual fun saveSnapshot(snapshot: DatabaseSnapshot) {
        // Kann bei vollem Speicher scheitern; der Stand bleibt dann im Speicher der laufenden App und im Sync
        runCatching { lsSet(STORAGE_KEY, json.encodeToString(DatabaseSnapshot.serializer(), snapshot)) }
    }

    actual fun platformName(): String = "Web (Browser)"

    // Im Browser gibt es keine Umgebungsvariablen; die Supabase-Werte kommen beim Build aus SupabaseEnvGenerated
    actual fun readEnvVar(key: String): String? = null

    actual fun readValue(key: String): String? = runCatching { lsGet("handball_kv_$key") }.getOrNull()

    actual fun writeValue(key: String, value: String?) {
        runCatching { if (value == null) lsRemove("handball_kv_$key") else lsSet("handball_kv_$key", value) }
    }

    actual fun readSecret(key: String): String? = runCatching { lsGet("handball_secret_$key") }.getOrNull()

    actual fun writeSecret(key: String, value: String?) {
        runCatching { if (value == null) lsRemove("handball_secret_$key") else lsSet("handball_secret_$key", value) }
    }
}
