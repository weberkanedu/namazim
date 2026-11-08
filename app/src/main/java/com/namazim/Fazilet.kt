package com.namazim

import java.time.LocalDate

// Every vakit is cut into seven equal slices (design `design/design-system/vakit-yedi-dilim.html`, approved 2026-11-08).
// Which slices are "faziletli" depends on the mezhep: Şâfiî = the beginning of every vakit; Hanefî = the müstehap time
// of each prayer (sabah at isfar, öğle early in winter / after the heat in summer, ikindi a little late but before the
// sun yellows, akşam at once, yatsı in the first third of the night, after midnight mekruh).
// shortcut: the Hanefî slices are the owner-approved draft; have a hoca or the Diyanet ilmihal confirm the borders.

enum class Dilim { FAZILET, GUZEL, VAKIT, KERAHAT }

val DILIM_ADI = listOf("vaktin hemen başı", "vaktin başı", "ortasından önce", "vaktin ortası", "ortasından hemen sonra", "sonlara doğru", "vaktin sonu")

/** The slice as "where": "Akşam vaktin hemen başında kılıyorsun". */

/** Which seventh of the vakit [at] falls in, 0..6. */
fun dilimOf(s: Slot, at: Long): Int = (((at - s.start) * 7) / (s.end - s.start).coerceAtLeast(1)).toInt().coerceIn(0, 6)

/** Start of slice [i] (0..7; 7 = the end of the vakit). */
fun dilimStart(s: Slot, i: Int): Long = s.start + (s.end - s.start) * i / 7

// shortcut: "summer" is May to September; a hoca may prefer the real heat, upgrade if asked
fun isSummer(d: LocalDate) = d.monthValue in 5..9

/** The seven slices of [s]. [aksam] is that day's akşam (the yatsı night runs akşam to imsak), [kerahat] the start of
 *  the sarı kerahat for ikindi (null for the other vakits). */
fun dilimler(s: Slot, hanefi: Boolean, summer: Boolean, aksam: Long, kerahat: Long?): List<Dilim> {
    val plan = if (!hanefi) "FF....." else when (s.vakit) {
        Vakit.SABAH -> "..GFFFG"
        Vakit.OGLE -> if (summer) "GGFF..." else "FFG...."
        Vakit.IKINDI -> "GFFFF.."
        Vakit.AKSAM -> "FF....."
        Vakit.YATSI -> "......."   // from the night below
    }
    val night = s.end - aksam
    return (0 until 7).map { i ->
        val mid = (dilimStart(s, i) + dilimStart(s, i + 1)) / 2
        when {
            kerahat != null && mid >= kerahat -> Dilim.KERAHAT
            hanefi && s.vakit == Vakit.YATSI -> when {
                mid < aksam + night / 3 -> Dilim.FAZILET
                mid >= aksam + night / 2 -> Dilim.KERAHAT
                else -> Dilim.GUZEL
            }
            else -> when (plan[i]) { 'F' -> Dilim.FAZILET; 'G' -> Dilim.GUZEL; else -> Dilim.VAKIT }
        }
    }
}

/** First and end (exclusive) of the faziletli slices, as times; null when a vakit has none. */
fun faziletBand(s: Slot, d: List<Dilim>): Pair<Long, Long>? {
    val a = d.indexOfFirst { it == Dilim.FAZILET }
    if (a < 0) return null
    return dilimStart(s, a) to dilimStart(s, d.indexOfLast { it == Dilim.FAZILET } + 1)
}

/** Prayed in a faziletli slice: the gül's "Vaktinde" petal. */
fun inFazilet(s: Slot, d: List<Dilim>, at: Long?): Boolean =
    at != null && at >= s.start && at < s.end && d[dilimOf(s, at)] == Dilim.FAZILET
