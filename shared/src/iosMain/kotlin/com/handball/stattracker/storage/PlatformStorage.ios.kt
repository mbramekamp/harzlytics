package com.handball.stattracker.storage

import com.handball.stattracker.model.DatabaseSnapshot
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUserDefaults

actual object PlatformStorage {
    private const val STORAGE_KEY = "handball_stat_tracker_offline_snapshot_v1"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    actual fun loadSnapshot(): DatabaseSnapshot? {
        return runCatching {
            val raw = NSUserDefaults.standardUserDefaults.stringForKey(STORAGE_KEY) ?: return null
            json.decodeFromString<DatabaseSnapshot>(raw)
        }.getOrNull()
    }

    actual fun saveSnapshot(snapshot: DatabaseSnapshot) {
        runCatching {
            NSUserDefaults.standardUserDefaults.setObject(
                json.encodeToString(snapshot),
                forKey = STORAGE_KEY
            )
        }
    }

    actual fun platformName(): String = "iOS (Native)"

    actual fun readEnvVar(key: String): String? {
        return (NSProcessInfo.processInfo.environment[key] as? String)?.takeIf { it.isNotBlank() }
    }
}
