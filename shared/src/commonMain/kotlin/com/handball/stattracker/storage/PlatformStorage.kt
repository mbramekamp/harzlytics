package com.handball.stattracker.storage

import com.handball.stattracker.model.DatabaseSnapshot

/**
 * Plattformspezifische lokale Speicherung für den Offline-First-Modus
 * (Desktop: SQLDelight SQLite + Datei im User-Home, iOS: NSUserDefaults/Dokumentenordner, Android: App-Storage).
 */
expect object PlatformStorage {
    fun loadSnapshot(): DatabaseSnapshot?
    fun saveSnapshot(snapshot: DatabaseSnapshot)
    fun platformName(): String
    fun readEnvVar(key: String): String?

    /** Kleiner Schlüssel-Wert-Speicher (z. B. Login-Session, Besitzer der lokalen Daten). null = nicht vorhanden. */
    fun readValue(key: String): String?

    /** Speichert [value] unter [key]; `null` löscht den Eintrag. */
    fun writeValue(key: String, value: String?)

    /**
     * Wie [readValue], aber für Geheimnisse (Login-Token): verschlüsselt gespeichert, soweit die
     * Plattform es erlaubt (Android Keystore, Windows DPAPI, sonst AES mit Schlüsseldatei).
     */
    fun readSecret(key: String): String?

    /** Wie [writeValue] für Geheimnisse; `null` löscht den Eintrag. */
    fun writeSecret(key: String, value: String?)
}
