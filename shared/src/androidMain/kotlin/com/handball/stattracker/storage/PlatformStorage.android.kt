package com.handball.stattracker.storage

import com.handball.stattracker.model.DatabaseSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

actual object PlatformStorage {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val storageDir: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "handball-stat-tracker").apply { if (!exists()) mkdirs() }
    }

    private val storageFile: File by lazy { File(storageDir, "handball-android-offline.json") }

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
