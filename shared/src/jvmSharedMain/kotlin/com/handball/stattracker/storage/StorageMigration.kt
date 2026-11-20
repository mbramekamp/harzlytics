package com.handball.stattracker.storage

import java.io.File

/**
 * Verschiebt Dateien aus einem alten Speicherordner in einen neuen (z. B. vom Android-Cache-Ordner,
 * den das System jederzeit leeren darf, in den dauerhaften App-Ordner).
 *
 * Regeln: Dateien, die es im Ziel schon gibt, bleiben unverändert (der neuere Stand gewinnt) und die
 * alte Kopie wird gelöscht. Eine Quelle wird erst gelöscht, nachdem das Kopieren geklappt hat.
 * Diese Datei wird in androidMain und in desktopTest eingebunden (siehe shared/build.gradle.kts).
 */
internal object StorageMigration {
    /** @return Anzahl der Dateien, die ins Ziel übernommen wurden. */
    fun moveFiles(from: File, to: File): Int {
        if (!from.isDirectory || from.canonicalFile == to.canonicalFile) return 0
        val files = from.listFiles()?.filter { it.isFile } ?: return 0
        if (files.isEmpty()) return 0
        to.mkdirs()
        var moved = 0
        for (source in files) {
            runCatching {
                val target = File(to, source.name)
                if (!target.exists()) {
                    source.copyTo(target)
                    moved++
                }
                source.delete()
            }
        }
        return moved
    }
}
