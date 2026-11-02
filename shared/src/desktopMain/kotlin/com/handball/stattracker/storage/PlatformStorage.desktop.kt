package com.handball.stattracker.storage

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.handball.stattracker.db.HandballDatabase
import com.handball.stattracker.model.DatabaseSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

actual object PlatformStorage {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val storageDir: File by lazy {
        val dir = File(System.getProperty("user.home"), ".handball-stat-tracker")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        dir
    }

    private val snapshotFile: File by lazy {
        File(storageDir, "handball-offline-db.json")
    }

    private val sqliteFile: File by lazy {
        File(storageDir, "handball-local.sqlite")
    }

    private val database: HandballDatabase? by lazy {
        runCatching {
            val isNew = !sqliteFile.exists()
            val driver = JdbcSqliteDriver("jdbc:sqlite:${sqliteFile.absolutePath}")
            if (isNew) {
                HandballDatabase.Schema.create(driver)
            }
            HandballDatabase(driver)
        }.getOrNull()
    }

    actual fun loadSnapshot(): DatabaseSnapshot? {
        if (!snapshotFile.exists()) return null
        return runCatching {
            json.decodeFromString<DatabaseSnapshot>(snapshotFile.readText(Charsets.UTF_8))
        }.getOrNull()
    }

    actual fun saveSnapshot(snapshot: DatabaseSnapshot) {
        runCatching {
            snapshotFile.writeText(json.encodeToString(snapshot), Charsets.UTF_8)
        }
        // Synchronisiere zusätzlich in die lokale SQLDelight SQLite-Datenbank
        runCatching {
            val db = database ?: return@runCatching
            db.transaction {
                snapshot.positions.forEach { pos ->
                    db.handballDatabaseQueries.insertPosition(
                        position_id = pos.positionId.toLong(),
                        position_name = pos.positionName
                    )
                }
            }
        }
    }

    actual fun platformName(): String = "Desktop (JVM + SQLite)"

    actual fun readEnvVar(key: String): String? {
        val candidateFiles = listOf(
            File(".env"),
            File("../.env"),
            File(System.getProperty("user.dir", "."), ".env"),
            File(System.getProperty("user.dir", "."), "../.env")
        )
        for (file in candidateFiles) {
            if (file.exists() && file.isFile) {
                val found = runCatching {
                    file.readLines(Charsets.UTF_8)
                        .map { it.trim() }
                        .firstOrNull { it.isNotEmpty() && !it.startsWith("#") && it.substringBefore('=').trim() == key }
                        ?.substringAfter('=')
                        ?.trim()
                        ?.removeSurrounding("\"")
                        ?.removeSurrounding("'")
                }.getOrNull()
                if (!found.isNullOrBlank()) {
                    return found
                }
            }
        }
        return System.getenv(key)?.takeIf { it.isNotBlank() }
    }
}
