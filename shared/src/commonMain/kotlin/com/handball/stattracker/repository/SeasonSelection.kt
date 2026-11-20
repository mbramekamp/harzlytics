package com.handball.stattracker.repository

import com.handball.stattracker.model.Season
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

    fun today(): String =
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
}
