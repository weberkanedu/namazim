package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test

class HolyTest {
    @Test fun daysNotWeeks() {
        assertEquals("bugün", holyLeft(0)); assertEquals("yarın", holyLeft(1))
        assertEquals("32 gün", holyLeft(32)); assertEquals("4 gün", holyLeft(4))
    }
}
