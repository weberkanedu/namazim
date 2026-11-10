package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test

class IstikrarTest {
    @Test fun goalLadder() {
        // 6 days, 5 full days, a 5-day run, 26 vakit: the first two goals reached, 7 gün seri is next at 5/7
        val g = hedefler(gun = 6, tamGun = 5, seri = 5, vakit = 26)
        assertEquals(listOf(true, true, false, false), g.take(4).map { it.tamam })
        assertEquals("7 gün seri", g.first { !it.tamam }.ad)
        assertEquals(5 to 7, g[2].olan to g[2].hedef)
        // everything reached after a long road
        assertEquals(true, hedefler(400, 365, 365, 2000).all { it.tamam })
    }
}
