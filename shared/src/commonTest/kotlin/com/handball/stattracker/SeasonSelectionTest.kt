package com.handball.stattracker

import com.handball.stattracker.model.Season
import com.handball.stattracker.repository.SeasonSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SeasonSelectionTest {
    private fun season(id: Int, start: String, end: String) =
        Season(seasonId = id, division = "Liga $id", startOfSeason = start, endOfSeason = end)

    private val s2425 = season(1, "2024-09-01", "2025-05-31")
    private val s2526 = season(2, "2025-09-01", "2026-05-31")
    private val s2627 = season(3, "2026-09-01", "2027-05-31")

    @Test
    fun runningSeasonWins() {
        assertEquals(3, SeasonSelection.currentSeasonId(listOf(s2627, s2526, s2425), "2026-11-20"))
        assertEquals(2, SeasonSelection.currentSeasonId(listOf(s2627, s2526, s2425), "2026-01-15"))
    }

    @Test
    fun firstAndLastDayCount() {
        assertEquals(3, SeasonSelection.currentSeasonId(listOf(s2526, s2627), "2026-09-01"))
        assertEquals(3, SeasonSelection.currentSeasonId(listOf(s2526, s2627), "2027-05-31"))
    }

    @Test
    fun summerBreakShowsTheLastFinishedSeason() {
        assertEquals(2, SeasonSelection.currentSeasonId(listOf(s2627, s2526, s2425), "2026-07-10"))
    }

    @Test
    fun beforeAllSeasonsShowsTheNextOne() {
        assertEquals(1, SeasonSelection.currentSeasonId(listOf(s2627, s2526, s2425), "2024-03-01"))
    }

    @Test
    fun overlappingSeasonsPreferTheLatestStarted() {
        val a = season(1, "2026-08-01", "2027-06-30")
        val b = season(2, "2026-09-15", "2027-05-31")
        assertEquals(2, SeasonSelection.currentSeasonId(listOf(a, b), "2026-11-20"))
    }

    @Test
    fun brokenDatesFallBackToTheFirstSeason() {
        assertEquals(7, SeasonSelection.currentSeasonId(listOf(season(7, "", ""), season(8, "", "")), "2026-11-20"))
    }

    @Test
    fun noSeasonsGivesNull() {
        assertNull(SeasonSelection.currentSeasonId(emptyList(), "2026-11-20"))
    }
}
