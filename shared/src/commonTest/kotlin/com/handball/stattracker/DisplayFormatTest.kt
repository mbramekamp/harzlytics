package com.handball.stattracker

import com.handball.stattracker.repository.DisplayFormat
import kotlin.test.Test
import kotlin.test.assertEquals

class DisplayFormatTest {
    @Test
    fun isoDateBecomesGermanDate() {
        assertEquals("18.10.2026", DisplayFormat.date("2026-10-18"))
        assertEquals("01.02.2025", DisplayFormat.date(" 2025-02-01 "))
    }

    @Test
    fun unknownFormatsStayUnchanged() {
        assertEquals("", DisplayFormat.date(""))
        assertEquals("18.10.2026", DisplayFormat.date("18.10.2026"))
        assertEquals("morgen", DisplayFormat.date("morgen"))
    }
}
