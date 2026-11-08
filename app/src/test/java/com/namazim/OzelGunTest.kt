package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OzelGunTest {
    private val m = 60_000L
    private val day = LocalDate.of(2026, 11, 8)
    // öğle 12:37–15:21, ikindi 15:21–17:48 as minutes of the day
    private val ogle = Slot(Vakit.OGLE, day, 757 * m, 921 * m, 921 * m)
    private val ikindi = Slot(Vakit.IKINDI, day, 921 * m, 1068 * m, 1068 * m)

    @Test fun insideAPeriodNothingIsOwed() {
        val p = OzelGun(0, null, false)
        assertTrue(exemptBy(ogle, ikindi, p, true)); assertTrue(exemptBy(ogle, ikindi, p, false))
    }

    @Test fun untouchedVakitIsOwed() {
        assertFalse(exemptBy(ogle, ikindi, OzelGun(1000 * m, null, false), true))
    }

    @Test fun startInsideTheVakit() {
        val p = OzelGun(850 * m, null, false)   // began 14:10, öğle entered 12:37
        assertTrue(exemptBy(ogle, ikindi, p, true))     // Hanefî: dropped
        assertFalse(exemptBy(ogle, ikindi, p, false))   // Şâfiî: time to pray had passed, owed
        assertTrue(exemptBy(ogle, ikindi, OzelGun(760 * m, null, false), false))   // Şâfiî: 3 min in, not enough
    }

    @Test fun endInsideTheVakit() {
        val p = OzelGun(0, 965 * m, false)   // ended 16:05, ikindi ends 17:48
        assertFalse(exemptBy(ikindi, null, p, true))    // enough for gusül + tekbir: owed
        assertTrue(exemptBy(ikindi, null, OzelGun(0, 1060 * m, false), true))   // 8 min left: Hanefî drops it
        assertFalse(exemptBy(ikindi, null, OzelGun(0, 1060 * m, false), false)) // Şâfiî: a tekbir is enough
        // Şâfiî cem: ending in ikindi makes öğle owed too, Hanefî does not
        assertFalse(exemptBy(ogle, ikindi, p, false))
        assertTrue(exemptBy(ogle, ikindi, p, true))
    }

    @Test fun limits() {
        assertEquals(10, maxDays(false, true)); assertEquals(15, maxDays(false, false))
        assertEquals(40, maxDays(true, true)); assertEquals(60, maxDays(true, false))
        assertEquals(3, minDays(false, true)); assertEquals(1, minDays(false, false)); assertEquals(0, minDays(true, true))
    }
}
