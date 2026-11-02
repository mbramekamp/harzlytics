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

    private val storageFile: File by lazy {
        val dir = File(System.getProperty("java.io.tmpdir"), "handball-stat-tracker")
        if (!dir.exists()) dir.mkdirs()
        File(dir, "handball-android-offline.json")
    }

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
}
