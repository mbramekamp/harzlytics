package com.handball.stattracker.storage

import com.handball.stattracker.model.DatabaseSnapshot
import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

actual object PlatformStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Volatile
    private var appContext: Context? = null

    /** Früherer Speicherort: der Cache-Ordner, den Android jederzeit leeren darf (Datenverlust-Risiko). */
    private val legacyDir: File
        get() = File(System.getProperty("java.io.tmpdir"), "handball-stat-tracker")

    /**
     * Muss einmal beim App-Start aufgerufen werden (`MainActivity.onCreate`, vor `setContent`).
     * Ab dann liegen alle Daten im dauerhaften App-Ordner `filesDir`; vorhandene Daten aus dem
     * alten Cache-Ordner werden dorthin übernommen.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        runCatching { StorageMigration.moveFiles(legacyDir, storageDir) }
    }

    /** Vor [init] (z. B. in Tests) bleibt es beim alten Ordner, damit nichts verloren geht. */
    private val storageDir: File
        get() {
            val dir = appContext?.let { File(it.filesDir, "handball-stat-tracker") } ?: legacyDir
            if (!dir.exists()) dir.mkdirs()
            return dir
        }

    private val storageFile: File
        get() = File(storageDir, "handball-android-offline.json")

    actual fun loadSnapshot(): DatabaseSnapshot? {
        if (!storageFile.exists()) return null
        return runCatching {
            json.decodeFromString<DatabaseSnapshot>(storageFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    actual fun saveSnapshot(snapshot: DatabaseSnapshot) {
        runCatching {
            storageFile.writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        }
    }

    actual fun platformName(): String = "Android (App Storage)"

    actual fun readEnvVar(key: String): String? = System.getenv(key)?.takeIf { it.isNotBlank() }

    private fun valueFile(key: String): File =
        File(storageDir, "kv-" + key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".txt")

    actual fun readValue(key: String): String? = runCatching {
        valueFile(key).takeIf { it.exists() }?.readText(Charsets.UTF_8)
    }.getOrNull()

    private fun secretFile(key: String): File =
        File(storageDir, "secret-" + key.replace(Regex("[^A-Za-z0-9_-]"), "_") + ".bin")

    actual fun readSecret(key: String): String? = runCatching {
        secretFile(key).takeIf { it.exists() }?.readText(Charsets.UTF_8)?.let { AndroidSecretCipher.decrypt(it) }
    }.getOrNull()

    actual fun writeSecret(key: String, value: String?) {
        runCatching {
            val file = secretFile(key)
            if (value == null) {
                file.delete()
            } else {
                // Ohne Keystore wird bewusst nichts im Klartext gespeichert (dann neu anmelden).
                val sealed = AndroidSecretCipher.encrypt(value) ?: return
                file.writeText(sealed, Charsets.UTF_8)
            }
        }
    }

    actual fun writeValue(key: String, value: String?) {
        runCatching {
            val file = valueFile(key)
            if (value == null) {
                file.delete()
            } else {
                file.writeText(value, Charsets.UTF_8)
                // nur für den aktuellen Benutzer lesbar/schreibbar
                file.setReadable(false, false); file.setReadable(true, true)
                file.setWritable(false, false); file.setWritable(true, true)
            }
        }
    }
}
