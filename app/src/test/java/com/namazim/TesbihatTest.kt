package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test

class TesbihatTest {
    @Test fun steps() {
        assertEquals(0, tesbihStep(listOf(0, 0, 0)))
        assertEquals(1, tesbihStep(listOf(33, 5, 0)))
        assertEquals(2, tesbihStep(listOf(33, 33, 32)))
        assertEquals(-1, tesbihStep(listOf(33, 33, 33)))
        assertEquals(0, tesbihStep(emptyList()))
    }

    @Test fun dailyListWithoutTesbihat() {
        val own = Zikir("Sübhânallah", 100)
        val all = DEFAULT_ZIKIRS + Zikir("Salavat", 100) + own
        assertEquals(all, dailyZikirs(all, true))
        assertEquals(listOf(Zikir("Salavat", 100), own), dailyZikirs(all, false))   // a re-targeted one is the user's own
    }
}
