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
        // morning, on yesterday's line, today's pages not read yet: on plan, not a day behind
        assertEquals(0, planLead(0, 100, from, until, today, 20))
        assertEquals(0, planLead(0, 100, from, until, today, 25))
    }

    @Test fun paceCountsFromTheFirstDayRead() {
        // started 3 days ago, 10 pages a day: 10/day, not 30/14
        assertEquals(10f, readingPace(listOf(10, 10, 10, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)), 1e-6f)
        // nothing today yet still counts today: 20 over 3 days
        assertEquals(20f / 3, readingPace(listOf(0, 10, 10, 0)), 1e-6f)
        assertEquals(0f, readingPace(List(14) { 0 }), 0f)
    }

    @Test fun finishDateIncludesToday() {
        // 30 pages left at 10 a day: today, tomorrow, the day after
        assertEquals(today.plusDays(2), finishDate(today, 30, 10f))
        assertEquals(today, finishDate(today, 5, 10f))
        assertEquals(null, finishDate(today, 0, 10f))
        assertEquals(null, finishDate(today, 30, 0f))
    }

    @Test fun undatedKuranUsesTheDailyGoalAndUndatedBookHasNone() {
        val quran = Reading("quran", "Kur'an", 21, 604, 0, null, null, true)
        val book = Reading("Mesnevî", "Mesnevî", 10, 300, 0, null, null, false)
        assertEquals(10, readingShare(quran, 0, today, 10))
        assertEquals(0, readingShare(book, 0, today, 10))
        assertEquals(0, readingShare(book.copy(page = 300, until = today), 0, today, 10))
    }
}
