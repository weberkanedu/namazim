package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    private val min = 60_000L

    @Test fun remindersCadence() {
        // 2-hour window: 3 prep, every 15 min for the first 90 min, every 5 min in the last 30.
        val s = Slot(Vakit.OGLE, LocalDate.of(2026, 11, 1), 0, 120 * min, 120 * min)
        val r = reminders(s)
        assertEquals(listOf(-30L, -15L, -5L), r.filter { it.prep }.map { it.at / min })
        val nags = r.filterNot { it.prep }.map { it.at / min }
        assertEquals((0L..90L step 15).toList() + (95L..115L step 5).toList(), nags)
    }

    @Test fun cuzOfPage() {
        assertEquals(1, cuz(1)); assertEquals(1, cuz(21)); assertEquals(2, cuz(22))
        assertEquals(29, cuz(581)); assertEquals(30, cuz(582)); assertEquals(30, cuz(604))
    }
}
