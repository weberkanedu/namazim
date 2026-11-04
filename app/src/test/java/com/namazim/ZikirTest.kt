package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test

class ZikirTest {
    @Test fun goalCellShowsCountThenTickThenExtra() {
        assertEquals(ZikirCell(false, ""), zikirCell(0, 33))
        assertEquals(ZikirCell(false, "21"), zikirCell(21, 33))
        assertEquals(ZikirCell(true, ""), zikirCell(33, 33))
        assertEquals(ZikirCell(true, "+12"), zikirCell(45, 33))
    }

    @Test fun freeCellIsJustTheCount() {
        assertEquals(ZikirCell(false, ""), zikirCell(0, null))
        assertEquals(ZikirCell(false, "120"), zikirCell(120, null))
    }

    @Test fun freeCounterNeverReachesTheRose() {
        // the free counter lives in the same day log but is not on the list, so the petal ignores it
        val p = zikirPetal(listOf(Zikir("A", 10)), mapOf("A" to 5, FREE_ZIKIR to 500))
        assertEquals(.5f, p.progress)
    }

    @Test fun extrasDoNotLiftThePetalPastFull() {
        assertEquals(1f, zikirPetal(listOf(Zikir("A", 33)), mapOf("A" to 99)).progress)
    }
}
