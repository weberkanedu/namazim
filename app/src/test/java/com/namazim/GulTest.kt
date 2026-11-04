package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GulTest {
    private val min = 60_000L
    private val h = 60 * min
    private val day = LocalDate.of(2026, 11, 5)
    // five 3-hour windows back to back: 0-3h, 3-6h, ...
    private val slots = Vakit.entries.mapIndexed { i, v -> Slot(v, day, i * 3 * h, (i + 1) * 3 * h, (i + 1) * 3 * h) }
    private val k = slots.map { it.key }

    @Test fun prayerDayBeforeImsakIsYesterday() {
        assertEquals(day.minusDays(1), prayerDayOf(day, now = 1_000, imsakToday = 2_000))
        assertEquals(day, prayerDayOf(day, now = 2_000, imsakToday = 2_000))
    }

    @Test fun firstThirdBoundaries() {   // window 0..90 min → first third is [0, 30)
        val s = Slot(Vakit.OGLE, day, 0, 90 * min, 90 * min)
        assertTrue(inFirstThird(s, 0)); assertTrue(inFirstThird(s, 30 * min - 1))
        assertFalse(inFirstThird(s, 30 * min)); assertFalse(inFirstThird(s, null)); assertFalse(inFirstThird(s, -1))
    }

    @Test fun zeroGoalCountsAsDone() {
        assertEquals(1f, ratio(0, 0)); assertEquals(1f, ratio(5, 3)); assertEquals(.5f, ratio(1, 2))
    }

    @Test fun petalsOrderAndLabels() {
        // 3 prayed: slots 0 and 1 in their first hour (first third), slot 2 in its last hour
        val at = mapOf(k[0] to 10 * min, k[1] to 3 * h + 10 * min, k[2] to 8 * h + 30 * min)
        val p = gulPetals(slots, prayed = setOf(k[0], k[1], k[2]), prayedAt = at::get,
            pagesToday = 1, readGoal = 3, kazaToday = 1, kazaRate = 2, kazaOwed = 40)
        assertEquals(listOf("Namaz", "Vaktinde", "Sayfa", "Kaza", "Zikir"), p.map { it.name })
        assertEquals(listOf(.6f, .4f, 1f / 3, .5f, 0f), p.map { it.progress })
        assertEquals(listOf("3/5", "2/5", "1/3", "1/2", "yakında"), p.map { it.label })
        assertEquals(listOf(true, true, true, true, false), p.map { it.counts })
    }

    @Test fun noKazaDebtIsFullPetal() {
        assertEquals(1f, gulPetals(slots, emptySet(), { null }, 0, 3, 0, 2, kazaOwed = 0)[3].progress)
    }

    @Test fun unmarkedPrayerDoesNotCount() {
        val p = gulPetals(slots, prayed = emptySet(), prayedAt = { 10 * min }, 0, 3, 0, 2, 40)
        assertEquals(0f, p[0].progress); assertEquals(0f, p[1].progress)
    }
}
