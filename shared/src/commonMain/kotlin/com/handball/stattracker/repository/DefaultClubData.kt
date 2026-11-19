package com.handball.stattracker.repository

import com.handball.stattracker.model.DatabaseSnapshot
import com.handball.stattracker.model.Position

/** Ausgangsdaten für einen neuen, leeren Verein (nur die 7 klassischen Handball-Positionen). */
object DefaultClubData {

    fun defaultPositions(): List<Position> = listOf(
        Position(1, "TW - Torwart"),
        Position(2, "LA - Linksaußen"),
        Position(3, "RL - Rückraum Links"),
        Position(4, "RM - Rückraum Mitte"),
        Position(5, "RR - Rückraum Rechts"),
        Position(6, "RA - Rechtsaußen"),
        Position(7, "KM - Kreismitte")
    )

    /** Leerer Snapshot mit `lastUpdatedEpochMillis = 0`, damit beim Sync immer der Server-Stand gewinnt. */
    fun emptySnapshot(): DatabaseSnapshot = DatabaseSnapshot(
        lastUpdatedEpochMillis = 0L,
        positions = defaultPositions()
    )
}
