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

    @Test fun neverNegativeAndNothingWhenAgesAreBackwards() {
        assertEquals(0, kazaDebtDays(20, 18, 0, 0))
        assertEquals(0, kazaDebtDays(12, 13, 0, 1000))
    }
}
