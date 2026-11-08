package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class FaziletTest {
    private val m = 60_000L
    private val day = LocalDate.of(2026, 11, 8)
    private fun slot(v: Vakit, a: Long, b: Long) = Slot(v, day, a * m, b * m, b * m)
    private fun plan(d: List<Dilim>) = d.joinToString("") { when (it) { Dilim.FAZILET -> "F"; Dilim.GUZEL -> "G"; Dilim.KERAHAT -> "K"; else -> "." } }

    @Test fun sevenSlices() {
        val s = slot(Vakit.OGLE, 0, 70)
        assertEquals(0, dilimOf(s, 0)); assertEquals(0, dilimOf(s, 10 * m - 1)); assertEquals(1, dilimOf(s, 10 * m))
        assertEquals(6, dilimOf(s, 70 * m - 1)); assertEquals(6, dilimOf(s, 99 * m))
    }

    @Test fun safiiIsTheBeginning() {
        assertEquals("FF.....", plan(dilimler(slot(Vakit.SABAH, 0, 70), false, false, 0, null)))
    }

    @Test fun hanefiPlans() {
        assertEquals("..GFFFG", plan(dilimler(slot(Vakit.SABAH, 0, 70), true, false, 0, null)))
        assertEquals("FFG....", plan(dilimler(slot(Vakit.OGLE, 0, 70), true, false, 0, null)))
        assertEquals("GGFF...", plan(dilimler(slot(Vakit.OGLE, 0, 70), true, true, 0, null)))
        assertEquals("FF.....", plan(dilimler(slot(Vakit.AKSAM, 0, 70), true, false, 0, null)))
    }

    @Test fun ikindiKerahatComesFromTheClock() {
        // İkindi 15:21–17:48 (147 min), sarı kerahat from 17:03 (45 min before akşam)
        val s = slot(Vakit.IKINDI, 921, 1068)
        assertEquals("GFFFFKK", plan(dilimler(s, true, false, 1068 * m, 1023 * m)))
    }

    @Test fun yatsiFollowsTheNight() {
        // akşam 17:48, yatsı 19:08, imsak 05:54 next day: night 726 min, first third ends 21:50, midnight 23:51
        val s = slot(Vakit.YATSI, 1148, 1440 + 354)
        assertEquals("FFGKKKK", plan(dilimler(s, true, false, 1068 * m, null)))
    }

    @Test fun bandAndOnTime() {
        val s = slot(Vakit.SABAH, 0, 70)
        val d = dilimler(s, true, false, 0, null)
        assertEquals(30 * m to 60 * m, faziletBand(s, d))
        assertTrue(inFazilet(s, d, 45 * m)); assertFalse(inFazilet(s, d, 5 * m)); assertFalse(inFazilet(s, d, null))
    }
}
