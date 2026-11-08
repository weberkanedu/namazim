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

    @Test fun ninetyNineNames() {
        assertEquals(98, ZIKIR_LIST.count { it.first.startsWith("Yâ ") && it.first != "Yâ Hayyu yâ Kayyûm" })
        assertNotNull(ZIKIR_LIST.firstOrNull { it.first == "Allah" })
        assertEquals(ZIKIR_LIST.size, ZIKIR_LIST.map { it.first }.toSet().size)   // no name twice
    }

    @Test fun suggestions() {
        assertEquals("Yâ Rahmân", suggestZikir("ya rahm").first().first)
        assertEquals("Yâ Kâbıd", suggestZikir("kabid").first().first)
        assert(suggestZikir("x").isEmpty())
        assert(suggestZikir("zzqq").isEmpty())
    }

    @Test fun ownArabicWins() {
        assertEquals("abc" to "anlam", zikirArabic(Zikir("Yâ Latîf", 33, "abc", "anlam")))
        assertNotNull(zikirArabic(Zikir("Yâ Latîf", 33)))
        assertNull(zikirArabic(Zikir("Kendi zikrim", 33)))
    }
}
