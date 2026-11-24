package com.handball.stattracker.repository

import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime

/** Anzeige-Formate für die Oberfläche (deutsch). Gespeichert wird weiterhin ISO (`yyyy-MM-dd`). */
object DisplayFormat {
    private val isoDate = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    /** `2026-10-18` → `18.10.2026`; alles andere bleibt unverändert. */
    fun date(iso: String): String {
        val m = isoDate.matchEntire(iso.trim()) ?: return iso
        val (y, mo, d) = m.destructured
        return "$d.$mo.$y"
    }

    /** `2026-10-18` → Mitternacht UTC in Millisekunden (für die Kalenderauswahl); null bei ungültigem Datum. */
    fun isoToEpochMillisUtc(iso: String): Long? = runCatching {
        kotlinx.datetime.LocalDate.parse(iso.trim()).atStartOfDayIn(kotlinx.datetime.TimeZone.UTC).toEpochMilliseconds()
    }.getOrNull()

    /** Gegenstück zu [isoToEpochMillisUtc]: Millisekunden (UTC) → `2026-10-18`. */
    fun epochMillisUtcToIso(millis: Long): String =
        kotlinx.datetime.Instant.fromEpochMilliseconds(millis).toLocalDateTime(kotlinx.datetime.TimeZone.UTC).date.toString()
}
