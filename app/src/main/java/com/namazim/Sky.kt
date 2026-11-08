package com.namazim

import kotlin.math.*

// ---------------------------------------------------------------- Astronomy (low precision, plenty for a sky picture)

private const val D2R = PI / 180

/** Altitude (rad) and azimuth from south, positive west (rad). */
data class AltAz(val alt: Double, val az: Double)

class SkyBodies(val sun: AltAz, val moon: AltAz, val moonPhase: Double)

fun skyBodies(lat: Double, lng: Double, millis: Long): SkyBodies {
    val n = millis / 86_400_000.0 + 2440587.5 - 2451545.0
    val eps = (23.439 - 0.0000004 * n) * D2R
    // Sun
    val g = (357.528 + 0.9856003 * n) * D2R
    val ls = (280.460 + 0.9856474 * n + 1.915 * sin(g) + 0.020 * sin(2 * g)) * D2R
    val sunRa = atan2(cos(eps) * sin(ls), cos(ls))
    val sunDec = asin(sin(eps) * sin(ls))
    // Moon
    val lp = (218.316 + 13.176396 * n) * D2R
    val mp = (134.963 + 13.064993 * n) * D2R
    val f = (93.272 + 13.229350 * n) * D2R
    val lm = lp + 6.289 * D2R * sin(mp)
    val bm = 5.128 * D2R * sin(f)
    val moonDec = asin(sin(bm) * cos(eps) + cos(bm) * sin(eps) * sin(lm))
    val moonRa = atan2(sin(lm) * cos(eps) - tan(bm) * sin(eps), cos(lm))
    val elong = acos(cos(bm) * cos(lm - ls))

    val lst = (280.46061837 + 360.98564736629 * n + lng) * D2R
    fun altAz(ra: Double, dec: Double): AltAz {
        val h = lst - ra
        val phi = lat * D2R
        val alt = asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(h))
        val az = atan2(sin(h), cos(h) * sin(phi) - tan(dec) * cos(phi))
        return AltAz(alt, az)
    }
    // +1.9° on the sun: refraction plus Diyanet's temkin, so the disc meets the horizon at güneş/akşam.
    val sun = altAz(sunRa, sunDec).let { it.copy(alt = it.alt + 1.9 * D2R) }
    return SkyBodies(sun, altAz(moonRa, moonDec), PI - elong)
}

/** Testing aid: `adb shell am start -n com.namazim/.MainActivity --el skytime 43200000` shifts the header clock. */
object SkyDebug { var kurulum = -1; var offsetMs = 0L; var kible: Float? = null; var mSection = 0; var zSection = 0; var gPeriod = 2 }
