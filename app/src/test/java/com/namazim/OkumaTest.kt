package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class OkumaTest {
    private val today = LocalDate.of(2026, 11, 5)

    @Test fun shareSpreadsWhatIsLeftOverTheDaysLeft() {
        // 392 pages left, 105 days including today → 4 a day (rounded up)
        assertEquals(4, todayShare(604, 212, 0, today, LocalDate.of(2027, 2, 17)))
        // pages read today do not shrink today's share
        assertEquals(4, todayShare(604, 215, 3, today, LocalDate.of(2027, 2, 17)))
    }

    @Test fun shareOnTheLastDayOrAfterIsEverythingLeft() {
        assertEquals(30, todayShare(100, 70, 0, today, today))
        assertEquals(30, todayShare(100, 70, 0, today, today.minusDays(3)))
    }

    @Test fun leadCountsWholeDaysAheadOrBehind() {
        // 100 pages over 10 days from page 0 = 10/day; end of day 3 expects 30
        val from = today.minusDays(2); val until = from.plusDays(9)
        assertEquals(2, planLead(0, 100, from, until, today, 52))
        assertEquals(-1, planLead(0, 100, from, until, today, 15))
        assertEquals(0, planLead(0, 100, from, until, today, 35))
    }

    @Test fun undatedKuranUsesTheDailyGoalAndUndatedBookHasNone() {
        val quran = Reading("quran", "Kur'an", 21, 604, 0, null, null, true)
        val book = Reading("Mesnevî", "Mesnevî", 10, 300, 0, null, null, false)
        assertEquals(10, readingShare(quran, 0, today, 10))
        assertEquals(0, readingShare(book, 0, today, 10))
        assertEquals(0, readingShare(book.copy(page = 300, until = today), 0, today, 10))
    }
}
