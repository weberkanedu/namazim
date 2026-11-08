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

    @Test fun refreshAtNextImsak() {   // the rose resets at imsak, not at midnight
        assertEquals(2_000L, nextImsakAt(now = 1_000, imsakToday = 2_000, imsakTomorrow = 9_000))
        assertEquals(9_000L, nextImsakAt(now = 2_000, imsakToday = 2_000, imsakTomorrow = 9_000))
    }

    @Test fun zeroGoalCountsAsDone() {
        assertEquals(1f, ratio(0, 0)); assertEquals(1f, ratio(5, 3)); assertEquals(.5f, ratio(1, 2))
    }

    @Test fun petalsOrderAndLabels() {
        // 3 prayed: slots 0 and 1 in their first hour (first third), slot 2 in its last hour
        val at = mapOf(k[0] to 10 * min, k[1] to 3 * h + 10 * min, k[2] to 8 * h + 30 * min)
        val p = gulPetals(slots, prayed = setOf(k[0], k[1], k[2]), onTime = { s -> at[s.key]?.let { it < s.start + (s.end - s.start) / 3 } == true },
            pagesToday = 1, readGoal = 3, kazaToday = 1, kazaRate = 2, kazaOwed = 40,
            zikirs = DEFAULT_ZIKIRS, zikirCounts = mapOf("Sübhânallah" to 33))
        assertEquals(listOf("Namaz", "Vaktinde", "Sayfa", "Kaza", "Zikir"), p.map { it.name })
        assertEquals(listOf(.6f, .4f, 1f / 3, .5f, 1f / 3), p.map { it.progress })
        assertEquals(listOf("3/5", "2/5", "1/3", "1/2", "1/3"), p.map { it.label })
        assertTrue(p.all { it.counts })
    }

    @Test fun zikirPetalAveragesClampedRatios() {
        val z = listOf(Zikir("A", 33), Zikir("B", 100))
        // A overshoots (clamped to 1), B is a quarter done
        assertEquals(.625f, zikirPetal(z, mapOf("A" to 40, "B" to 25)).progress, 1e-6f)
        assertEquals("1/2", zikirPetal(z, mapOf("A" to 40, "B" to 25)).label)
        assertEquals(0f, zikirPetal(z, emptyMap()).progress)
    }

    @Test fun emptyZikirListIsOpen() {
        assertEquals(1f, zikirPetal(emptyList(), emptyMap()).progress)
    }

    @Test fun nextOpenZikirWrapsAndStaysWhenAllDone() {
        val z = listOf(Zikir("A", 1), Zikir("B", 1), Zikir("C", 1))
        assertEquals(2, nextOpenZikir(z, mapOf("A" to 1, "B" to 1), from = 1))
        assertEquals(0, nextOpenZikir(z, mapOf("B" to 1, "C" to 1), from = 2))
        assertEquals(1, nextOpenZikir(z, mapOf("A" to 1, "B" to 1, "C" to 1), from = 1))
        assertEquals(0, nextOpenZikir(listOf(Zikir("A", 1)), mapOf("A" to 1), from = 0))
    }

    @Test fun noKazaDebtIsFullPetal() {
        assertEquals(1f, gulPetals(slots, emptySet(), { false }, 0, 3, 0, 2, kazaOwed = 0)[3].progress)
    }

    @Test fun unmarkedPrayerDoesNotCount() {
        val p = gulPetals(slots, prayed = emptySet(), onTime = { true }, 0, 3, 0, 2, 40)
        assertEquals(0f, p[0].progress); assertEquals(0f, p[1].progress)
    }
}
