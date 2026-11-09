package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ZikirNurTest {
    @Test fun meaningsEndWithAPeriod() {
        assertEquals("Allah en büyüktür.", cumle("Allah en büyüktür"))
        assertEquals("Allah en büyüktür.", cumle("Allah en büyüktür. "))
        assertEquals("", cumle(""))
        assertTrue(ZIKIR_LIST.all { it.mean.last() in ".!?" })
    }

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
        assertEquals(98, ZIKIR_LIST.count { it.name.startsWith("Yâ ") && it.name != "Yâ Hayyu yâ Kayyûm" && it.name != "Yâ mukallibe'l-kulûb" })
        assertNotNull(ZIKIR_LIST.firstOrNull { it.name == "Allah" })
        assertEquals(ZIKIR_LIST.size, ZIKIR_LIST.map { it.name }.toSet().size)   // no name twice
    }

    @Test fun suggestions() {
        assertEquals("Yâ Rahmân", suggestZikir("ya rahm").first().name)
        assertEquals("Yâ Kâbıd", suggestZikir("kabid").first().name)
        assert(suggestZikir("x").isEmpty())
        assert(suggestZikir("zzqq").isEmpty())
    }

    @Test fun ownArabicWins() {
        assertEquals("abc" to "anlam", zikirArabic(Zikir("Yâ Latîf", 33, "abc", "anlam")))
        assertNotNull(zikirArabic(Zikir("Yâ Latîf", 33)))
        assertNull(zikirArabic(Zikir("Kendi zikrim", 33)))
    }

    @Test fun hadisPicksCarryTheirCount() {
        val s = ZIKIR_LIST.first { it.name == "Seyyidü'l-istiğfâr" }
        assertEquals(1, s.adet); assertNotNull(s.kaynak)
        assertEquals(100, suggestZikir("estagfirullahe ve").first().adet)
        // loose names must not collide
        assertEquals(ZIKIR_LIST.size, ZIKIR_LIST.map { it.name.lowercase().filter(Char::isLetter) }.toSet().size)
    }

    @Test fun librarySections() {
        assertEquals("Esmâ", turOf(ZIKIR_LIST.first { it.name == "Yâ Latîf" }))
        assertEquals("İstiğfar", turOf(ZIKIR_LIST.first { it.name == "Seyyidü'l-istiğfâr" }))
        assertEquals("Salavat", turOf(ZIKIR_LIST.first { it.name == "Salli-bârik" }))
        assertEquals("Dua", turOf(ZIKIR_LIST.first { it.name == "Yâ mukallibe'l-kulûb" }))
        assertEquals("Tesbihat", turOf(ZIKIR_LIST.first { it.name == "Yatarken tesbihat (33·33·34)" }))
        assertEquals(ZIKIR_LIST.size, kutuphane("Tümü", "").size)
        assert(kutuphane("Esmâ", "rahim").any { it.name == "Yâ Rahîm" })
        assert(kutuphane("Tesbihat", "rahim").isEmpty())
    }
}
