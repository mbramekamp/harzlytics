package com.handball.stattracker.storage

import com.handball.stattracker.model.DatabaseSnapshot

/**
 * Plattformspezifische lokale Speicherung für den Offline-First-Modus
 * (Desktop: SQLDelight SQLite + Datei im User-Home, Web: Browser localStorage, iOS: NSUserDefaults/Dokumentenordner, Android: App-Storage).
 */
expect object PlatformStorage {
    fun loadSnapshot(): DatabaseSnapshot?
    fun saveSnapshot(snapshot: DatabaseSnapshot)
    fun platformName(): String
    fun readEnvVar(key: String): String?
}
