package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test

class KazaTest {
    @Test fun nineYearsMinusOneYearAlreadyPrayed() {
        // 13 → 22 = 9 years = 3285 days, one year (365) already made up
        assertEquals(2920, kazaDebtDays(13, 22, 0, 365))
    }

    @Test fun excusedDaysComeOffEveryMonth() {
        // 2 years, 7 excused days a month: 730 − 2·12·7 = 562
        assertEquals(562, kazaDebtDays(12, 14, 7, 0))
    }

    @Test fun dayByDayCountsFullSetsAndTheOneInProgress() {
        // two full days, the third has Sabah and Öğle in
        val (days, inNow) = kazaRound(listOf(3, 3, 2, 2, 2, 2), listOf(10, 10, 10, 10, 10, 10))
        assertEquals(2, days)
        assertEquals(listOf(true, true, false, false, false, false), inNow)
    }

    @Test fun aVakitNeverOwedDoesNotBlockTheDay() {
        // no vitir debt at all: five vakit make the day
        val (days, inNow) = kazaRound(listOf(1, 1, 1, 1, 1, 0), listOf(4, 4, 4, 4, 4, 0))
        assertEquals(1, days)
        assertEquals(true, inNow[5])
        assertEquals(0 to List(6) { false }, kazaRound(List(6) { 0 }, List(6) { 0 }))
    }

    @Test fun streakSkipsAnEmptyToday() {
        assertEquals(3, kazaStreak(listOf(0, 2, 1, 4, 0, 5)))
        assertEquals(2, kazaStreak(listOf(1, 1, 0)))
        assertEquals(0, kazaStreak(listOf(0, 0, 3)))
    }

    @Test fun neverNegativeAndNothingWhenAgesAreBackwards() {
        assertEquals(0, kazaDebtDays(20, 18, 0, 0))
        assertEquals(0, kazaDebtDays(12, 13, 0, 1000))
    }
}
