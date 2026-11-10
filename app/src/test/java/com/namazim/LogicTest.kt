package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    private val min = 60_000L

    @Test fun remindersCadence() {
        // 2-hour window: 3 prep, every 15 min for the first 90 min, every 5 min in the last 30.
        val s = Slot(Vakit.OGLE, LocalDate.of(2026, 11, 1), 0, 120 * min, 120 * min)
        val r = reminders(s)
        assertEquals(listOf(-30L, -15L, -5L), r.filter { it.prep }.map { it.at / min })
        val nags = r.filterNot { it.prep }.map { it.at / min }
        assertEquals((0L..90L step 15).toList() + (95L..115L step 5).toList(), nags)
    }

    @Test fun faziletReminder() {
        val s = Slot(Vakit.OGLE, LocalDate.of(2026, 11, 1), 0, 120 * min, 120 * min)
        // faziletli time at the start: one reminder there, the fazilet one (no separate "girdi")
        val same = reminders(s, fz = 0).filterNot { it.prep }
        assertEquals(Ses.FAZILET, same.first().kind); assertEquals(1, same.count { it.at == 0L })
        // later (at 32 min): girdi at 0, fazilet at 32, the nag at 30 (2 min before) is dropped, 45 stays
        val later = reminders(s, fz = 32 * min).filterNot { it.prep }
        assertEquals(Ses.GIRDI, later.first().kind)
        assertEquals(listOf(0L, 15L, 32L, 45L), later.filter { it.at <= 45 * min }.map { it.at / min })
        assertEquals(Ses.FAZILET, later.first { it.at == 32 * min }.kind)
    }

    @Test fun cuma() {
        val cuma = LocalDate.of(2026, 11, 13)   // a Friday
        val ogle = Slot(Vakit.OGLE, cuma, 0, 1, 1)
        assertEquals("Cuma", ogle.ad(kadin = false)); assertEquals("Öğle", ogle.ad(kadin = true))
        assertEquals("Öğle", ogle.copy(date = cuma.plusDays(1)).ad(kadin = false))
        // icon: Thursday after akşam and Friday before akşam
        assertEquals(true, cumaVakti(cuma.minusDays(1), 100, 50)); assertEquals(false, cumaVakti(cuma.minusDays(1), 10, 50))
        assertEquals(true, cumaVakti(cuma, 10, 50)); assertEquals(false, cumaVakti(cuma, 100, 50))
        assertEquals(false, cumaVakti(cuma.plusDays(1), 10, 50))
        // one icon per vakit; ikindi and akşam were swapped on purpose (akşam is the everyday Zümrüt)
        assertEquals(listOf("imsak", "ogle", "ikindi", "zumrut", "yatsi"), Vakit.entries.map { ikonAdi(it) })
    }

    @Test fun cuzOfPage() {
        assertEquals(1, cuz(1)); assertEquals(1, cuz(21)); assertEquals(2, cuz(22))
        assertEquals(29, cuz(581)); assertEquals(30, cuz(582)); assertEquals(30, cuz(604))
    }

    @Test fun keptOnlyCountsEndedPrayers() {
        // two ended and prayed, the current one prayed early: 2/2, not 3/2 (the "%200" bug)
        val d = LocalDate.of(2026, 11, 4)
        val s = (0..2).map { Slot(Vakit.entries[it], d, it * 60 * min, (it + 1) * 60 * min, (it + 1) * 60 * min) }
        assertEquals(2 to 2, dueKept(s, s.map { it.key }.toSet(), now = 150 * min))
        assertEquals(2 to 1, dueKept(s, setOf(s[0].key), now = 150 * min))
    }
}
