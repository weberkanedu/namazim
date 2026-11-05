package com.namazim

import org.junit.Assert.assertEquals
import org.junit.Test

class HaremTest {
    private val imsak = 5_000L; private val gunes = 7_000L; private val ikindi = 15_000L; private val aksam = 17_000L
    private fun at(t: Long) = haremClip(t, imsak, gunes, ikindi, aksam)

    @Test fun eachStretchOfTheDayHasItsClip() {
        assertEquals("medine-safak.mp4", at(imsak))
        assertEquals("mekke-gun.mp4", at(gunes))
        assertEquals("mekke-gun.mp4", at(ikindi - 1))
        assertEquals("medine-aksam.mp4", at(ikindi))
        assertEquals("mekke-gece.mp4", at(aksam))
    }

    @Test fun afterMidnightBeforeImsakIsStillNight() {
        assertEquals("mekke-gece.mp4", at(1_000))
    }
}
