package com.handball.stattracker.repository

import com.handball.stattracker.model.Competition
import com.handball.stattracker.model.Game
import com.handball.stattracker.model.Season
import com.handball.stattracker.model.SeasonPhase
import com.handball.stattracker.model.SeasonScope
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Wählt die Saison, die beim Start angezeigt wird. */
object SeasonSelection {

    /**
     * Die laufende Saison (Beginn ≤ [today] ≤ Ende, bei Überschneidung die zuletzt begonnene);
     * sonst die zuletzt beendete; sonst die nächste anstehende. null nur ohne Saisons.
     * Daten sind ISO-Texte (`YYYY-MM-DD`) und deshalb als Text vergleichbar.
     */
    fun currentSeasonId(seasons: List<Season>, today: String): Int? {
        if (seasons.isEmpty()) return null
        seasons.filter { it.startOfSeason.isNotBlank() && it.startOfSeason <= today && today <= it.endOfSeason }
            .maxByOrNull { it.startOfSeason }?.let { return it.seasonId }
        seasons.filter { it.endOfSeason.isNotBlank() && it.endOfSeason < today }
            .maxByOrNull { it.endOfSeason }?.let { return it.seasonId }
        seasons.filter { it.startOfSeason.isNotBlank() && it.startOfSeason > today }
            .minByOrNull { it.startOfSeason }?.let { return it.seasonId }
        return seasons.first().seasonId
    }

    /**
     * Startauswahl im Saison-Menü: Läuft keine Saison, die nächste aber schon mit einem Testspiel
     * (Datum ≤ [today]) vorbereitet wird, deren Vorbereitung; sonst die Saison aus [currentSeasonId].
     */
    fun currentScope(seasons: List<Season>, games: List<Game>, today: String): SeasonScope? {
        val running = seasons.any { it.startOfSeason.isNotBlank() && it.startOfSeason <= today && today <= it.endOfSeason }
        if (!running) {
            val upcoming = seasons.filter { it.startOfSeason > today }.minByOrNull { it.startOfSeason }
            if (upcoming != null && games.any {
                    it.seasonId == upcoming.seasonId && it.competition == Competition.FRIENDLY && it.gameDate <= today
                }
            ) return SeasonScope(upcoming.seasonId, SeasonPhase.PRESEASON)
        }
        return currentSeasonId(seasons, today)?.let { SeasonScope(it) }
    }

    /** Einträge des Saison-Menüs: jede Saison, darunter ihre Vorbereitung, sobald es dort Testspiele gibt. */
    fun menuScopes(seasons: List<Season>, games: List<Game>): List<SeasonScope> = seasons.flatMap { season ->
        val hasPreseason = games.any { it.seasonId == season.seasonId && it.competition == Competition.FRIENDLY }
        listOfNotNull(SeasonScope(season.seasonId), SeasonScope(season.seasonId, SeasonPhase.PRESEASON).takeIf { hasPreseason })
    }

    fun today(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}
