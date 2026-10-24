package com.handball.stattracker.storage

import com.handball.stattracker.model.DatabaseSnapshot
import kotlinx.browser.localStorage
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

actual object PlatformStorage {
    private const val STORAGE_KEY = "handball_stat_tracker_offline_snapshot_v1"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    actual fun loadSnapshot(): DatabaseSnapshot? {
        return runCatching {
            val raw = localStorage.getItem(STORAGE_KEY) ?: return null
            json.decodeFromString<DatabaseSnapshot>(raw)
        }.getOrNull()
    }

    actual fun saveSnapshot(snapshot: DatabaseSnapshot) {
        runCatching {
            localStorage.setItem(STORAGE_KEY, json.encodeToString(snapshot))
        }
    }

    actual fun platformName(): String = "Web (Browser LocalStorage)"
}
