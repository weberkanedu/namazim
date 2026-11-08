package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ZikirNurTest {
    @Test fun ringFillsOnceToTheTarget() {
        assertEquals(.47f, zikirProgress(47, 100), 1e-4f)
        assertEquals(1f, zikirProgress(130, 100), 0f)      // extras keep it full, no second turn
        assertEquals(0f, zikirProgress(0, 1000), 0f)
    }

    @Test fun freeCounterTurnsEvery33() {
        assertEquals(1f, zikirProgress(33, null), 0f)
        assertEquals(1 / 33f, zikirProgress(34, null), 1e-4f)
    }

    @Test fun arabicMatchesLooseSpelling() {
        assertNotNull(zikirArabic("Yâ Latîf"))
        assertNotNull(zikirArabic("ya latif"))
        assertNotNull(zikirArabic("Sübhânallah"))
        assertNull(zikirArabic("Benim zikrim"))
    }
}
