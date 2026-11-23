package com.handball.stattracker.repository

/** Anzeige-Formate für die Oberfläche (deutsch). Gespeichert wird weiterhin ISO (`yyyy-MM-dd`). */
object DisplayFormat {
    private val isoDate = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

    /** `2026-10-18` → `18.10.2026`; alles andere bleibt unverändert. */
    fun date(iso: String): String {
        val m = isoDate.matchEntire(iso.trim()) ?: return iso
        val (y, mo, d) = m.destructured
        return "$d.$mo.$y"
    }
}
