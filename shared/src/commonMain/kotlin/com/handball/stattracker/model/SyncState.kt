package com.handball.stattracker.model

/** Zustand der Cloud-Synchronisation. Die Oberfläche zeigt dafür nur ein Symbol, keine Texte oder Adressen. */
enum class SyncState {
    /** Noch nicht synchronisiert (App gerade gestartet). */
    IDLE,
    SYNCING,
    /** Lokaler Stand und Cloud stimmen überein. */
    SYNCED,
    /** Synchronisiert, aber lokal gibt es neuere Änderungen, die noch nachgesendet werden. */
    SYNCED_PENDING,
    /** Server nicht erreichbar; lokale Daten sind gespeichert und werden später nachgesendet. */
    OFFLINE,
    /** Sync nicht möglich: nicht angemeldet, Konto nicht freigeschaltet oder Supabase nicht konfiguriert. */
    UNAVAILABLE
}
