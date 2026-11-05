package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class VakitlerTest {
    private val m = 60_000L
    // İkindi 15:24–17:51 as minutes from an arbitrary origin: 147 min, vaktinde until +49 min
    private val s = Slot(Vakit.IKINDI, LocalDate.of(2026, 11, 5), 924 * m, 1071 * m, 1071 * m)

    @Test fun thirdEndIsAFirstThird() = assertEquals(973 * m, thirdEnd(s))

    @Test fun states() {
        assertEquals(VState.SONRA, vakitState(s, 900 * m, false, null, true))
        assertEquals(VState.ACIK, vakitState(s, 950 * m, false, null, true))
        assertEquals(VState.VAKTINDE, vakitState(s, 950 * m, true, 950 * m, true))
        assertEquals(VState.KILINDI, vakitState(s, 1000 * m, true, 990 * m, true))
        assertEquals(VState.KILINDI, vakitState(s, 1000 * m, true, null, true))   // old marks without a time
        assertEquals(VState.KAZA, vakitState(s, 1100 * m, false, null, true))
        assertEquals(VState.GECTI, vakitState(s, 1100 * m, false, null, false))
    }
}
