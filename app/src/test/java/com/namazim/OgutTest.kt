package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class OgutTest {
    @Test fun everyOgutNamesItsSource() = OGUTLER.forEach { assert(it.text.isNotBlank() && it.source.isNotBlank()) }

    @Test fun feedbackLimit() {
        val day = "2026-11-05"
        var log = ""
        var t = 1_000_000L
        repeat(FEEDBACK_PER_DAY) {
            assertNull(feedbackBlock(log, t, day))
            log = feedbackLogged(log, t, day)
            assertNotNull(feedbackBlock(log, t + 30_000, day))   // a minute between sends
            t += 120_000
        }
        assertNotNull(feedbackBlock(log, t, day))                 // the day is full
        assertNull(feedbackBlock(log, t, "2026-11-06"))           // a new day starts over
        assertEquals("2026-11-06|1|$t", feedbackLogged(log, t, "2026-11-06"))
    }
}
