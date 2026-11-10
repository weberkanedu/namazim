package com.namazim

import com.batoulapps.adhan2.CalculationMethod
import com.batoulapps.adhan2.Coordinates
import com.batoulapps.adhan2.PrayerTimes
import com.batoulapps.adhan2.Qibla
import com.batoulapps.adhan2.data.DateComponents
import com.batoulapps.adhan2.model.Rounding
import java.time.LocalDate

enum class Vakit(val ad: String) { SABAH("Sabah"), OGLE("Öğle"), IKINDI("İkindi"), AKSAM("Akşam"), YATSI("Yatsı") }

/** One prayer on one day. [key] identifies it in storage; [nagEnd] is when reminders stop. */
/** "Cuma" for a man's Friday öğle (the Cuma namazı is farz on men; for women it stays öğle), else the vakit's name. */
fun Slot.ad(kadin: Boolean): String =
    if (vakit == Vakit.OGLE && date.dayOfWeek == java.time.DayOfWeek.FRIDAY && !kadin) "Cuma" else vakit.ad
/** The name on a row: the first one is "İmsak" (sabah starts at imsak), Friday öğle is "Cuma" for men. */
fun Slot.satir(kadin: Boolean): String = if (vakit == Vakit.SABAH) "İmsak" else ad(kadin)

data class Slot(val vakit: Vakit, val date: LocalDate, val start: Long, val end: Long, val nagEnd: Long) {
    val key get() = "$date|$vakit"
}

/** Times shown on the home screen, epoch millis. */
data class DayTimes(val imsak: Long, val gunes: Long, val ogle: Long, val ikindi: Long, val aksam: Long, val yatsi: Long)

// Diyanet's method; adhan2 defaults to Shafi asr (asr-ı evvel), which Diyanet also uses.
private val params = CalculationMethod.TURKEY.parameters.copy(rounding = Rounding.NONE)

// Seconds added before rounding to the minute, fitted to Diyanet's Nov 2026 tables for Ankara and İstanbul:
// imsak/güneş/öğle then match every day, ikindi/akşam/yatsı ~90% (the rest is off by 1 min).
// shortcut: calibrated on one month; replace with Diyanet's own tables once the Awqat Salah account exists.
private val diyanetOffsetSec = longArrayOf(-4, -10, -5, 53, 36, 27)

private fun toDiyanet(ms: Long, i: Int): Long {
    val s = ms / 1000 + diyanetOffsetSec[i]
    return (s + 30) / 60 * 60_000
}

fun dayTimes(lat: Double, lng: Double, date: LocalDate): DayTimes {
    val t = PrayerTimes(Coordinates(lat, lng), DateComponents(date.year, date.monthValue, date.dayOfMonth), params)
    val v = listOf(t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha).mapIndexed { i, x -> toDiyanet(x.toEpochMilliseconds(), i) }
    return DayTimes(v[0], v[1], v[2], v[3], v[4], v[5])
}

fun slots(lat: Double, lng: Double, date: LocalDate): List<Slot> {
    val d = dayTimes(lat, lng, date)
    val nextImsak = dayTimes(lat, lng, date.plusDays(1)).imsak
    // Yatsı imsağa kadar sürer; gece boyunca sık hatırlatmak yorucu, ama tamamen susmak da yanlış.
    // Nag'lar gece yarısından sonra seyrekleşir (Reminders.reminders içinde 60 dk), son yarım saatte sıklaşır.
    val yatsiNagEnd = nextImsak - 30 * 60_000L
    return listOf(
        Slot(Vakit.SABAH, date, d.imsak, d.gunes, d.gunes),
        Slot(Vakit.OGLE, date, d.ogle, d.ikindi, d.ikindi),
        Slot(Vakit.IKINDI, date, d.ikindi, d.aksam, d.aksam),
        Slot(Vakit.AKSAM, date, d.aksam, d.yatsi, d.yatsi),
        Slot(Vakit.YATSI, date, d.yatsi, nextImsak, yatsiNagEnd),
    )
}

fun qiblaDegrees(lat: Double, lng: Double): Double = Qibla(Coordinates(lat, lng)).direction

/** Optional extra times (same rules as Vakit): kerahat windows and voluntary-prayer windows.
 *  [group] says which main row of the day's timeline the time belongs under. */
data class Extra(val name: String, val start: Long, val end: Long?, val kerahat: Boolean, val group: String)

fun extras(d: DayTimes, nextImsak: Long): List<Extra> {
    val m = 60_000L
    val tehecud = d.aksam + (nextImsak - d.aksam) * 2 / 3 // last third of the night
    return listOf(
        Extra("İşrak kerahati", d.gunes, d.gunes + 45 * m, true, "GÜNEŞ"),
        Extra("İşrak", d.gunes + 45 * m, null, false, "GÜNEŞ"),
        Extra("Kuşluk (duha)", d.gunes + 45 * m, d.ogle - 10 * m, false, "GÜNEŞ"),
        Extra("İstiva kerahati", d.ogle - 10 * m, d.ogle, true, "GÜNEŞ"),
        Extra("Sarı kerahat", d.aksam - 45 * m, d.aksam, true, "İKİNDİ"),
        Extra("Evvâbin", d.aksam, d.yatsi, false, "AKŞAM"),
        Extra("Teheccüd", tehecud, nextImsak, false, "YATSI"),
    )
}

private val HIJRI_MONTHS = listOf(
    "Muharrem", "Safer", "Rebiülevvel", "Rebiülahir", "Cemaziyelevvel", "Cemaziyelahir",
    "Recep", "Şaban", "Ramazan", "Şevval", "Zilkade", "Zilhicce",
)

// shortcut: Umm al-Qura calendar; Diyanet's own hijri date can differ by a day around month starts.
fun hijri(date: LocalDate): String {
    val cal = android.icu.util.IslamicCalendar()
    cal.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
    cal.timeInMillis = date.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    return "${cal.get(android.icu.util.Calendar.DAY_OF_MONTH)} ${HIJRI_MONTHS[cal.get(android.icu.util.Calendar.MONTH)]} ${cal.get(android.icu.util.Calendar.YEAR)}"
}

data class HolyDay(val name: String, val date: LocalDate)

private fun hijriToDate(year: Int, month: Int, day: Int): LocalDate {
    val cal = android.icu.util.IslamicCalendar()
    cal.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
    cal.clear()
    cal.set(year, month - 1, day, 12, 0)
    return java.time.Instant.ofEpochMilli(cal.timeInMillis).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
}

/** Mübarek günler of a hijri year, on the Gregorian day Diyanet lists them (kandils: the evening before). */
fun holyDays(hYear: Int): List<HolyDay> {
    val recep1 = hijriToDate(hYear, 7, 1)
    // Regaib: the night before the first Friday of Recep, listed on that Thursday.
    val regaib = recep1.plusDays(((5 - recep1.dayOfWeek.value + 7) % 7).toLong()).minusDays(1)
    val sevval1 = hijriToDate(hYear, 10, 1)
    return listOf(
        HolyDay("Hicri Yılbaşı", hijriToDate(hYear, 1, 1)),
        HolyDay("Aşure Günü", hijriToDate(hYear, 1, 10)),
        HolyDay("Mevlid Kandili", hijriToDate(hYear, 3, 11)),
        HolyDay("Üç Ayların Başlangıcı", recep1),
        HolyDay("Regaib Kandili", regaib),
        HolyDay("Miraç Kandili", hijriToDate(hYear, 7, 26)),
        HolyDay("Berat Kandili", hijriToDate(hYear, 8, 14)),
        HolyDay("Ramazan Başlangıcı", hijriToDate(hYear, 9, 1)),
        HolyDay("Kadir Gecesi", hijriToDate(hYear, 9, 26)),
        HolyDay("Ramazan Bayramı Arifesi", sevval1.minusDays(1)),
        HolyDay("Ramazan Bayramı", sevval1),
        HolyDay("Kurban Bayramı Arifesi", hijriToDate(hYear, 12, 9)),
        HolyDay("Kurban Bayramı", hijriToDate(hYear, 12, 10)),
    )
}

/** The next [n] mübarek günler (today included), each with "32 gün" style days left. */
fun nextHolyDays(today: LocalDate, n: Int = 3): List<Pair<HolyDay, String>> {
    val cal = android.icu.util.IslamicCalendar()
    cal.calculationType = android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
    cal.timeInMillis = today.atTime(12, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    val hYear = cal.get(android.icu.util.Calendar.YEAR)
    return (holyDays(hYear) + holyDays(hYear + 1)).filter { !it.date.isBefore(today) }.sortedBy { it.date }.take(n)
        .map { it to holyLeft(java.time.temporal.ChronoUnit.DAYS.between(today, it.date)) }
}

/** Days to a mübarek gün in plain days (owner, 2026-11-08: "4 hafta" was too vague). */
fun holyLeft(days: Long): String = when (days) { 0L -> "bugün"; 1L -> "yarın"; else -> "$days gün" }

// First page of each sûre in the standard 604-page mushaf.
private val SURE_PAGES = listOf(
    1 to "Fâtiha", 2 to "Bakara", 50 to "Âl-i İmrân", 77 to "Nisâ", 106 to "Mâide", 128 to "En'âm", 151 to "A'râf",
    177 to "Enfâl", 187 to "Tevbe", 208 to "Yûnus", 221 to "Hûd", 235 to "Yûsuf", 249 to "Ra'd", 255 to "İbrâhîm",
    262 to "Hicr", 267 to "Nahl", 282 to "İsrâ", 293 to "Kehf", 305 to "Meryem", 312 to "Tâhâ", 322 to "Enbiyâ",
    332 to "Hac", 342 to "Mü'minûn", 350 to "Nûr", 359 to "Furkân", 367 to "Şuarâ", 377 to "Neml", 385 to "Kasas",
    396 to "Ankebût", 404 to "Rûm", 411 to "Lokmân", 415 to "Secde", 418 to "Ahzâb", 428 to "Sebe'", 434 to "Fâtır",
    440 to "Yâsîn", 446 to "Sâffât", 453 to "Sâd", 458 to "Zümer", 467 to "Mü'min", 477 to "Fussilet", 483 to "Şûrâ",
    489 to "Zuhruf", 496 to "Duhân", 499 to "Câsiye", 502 to "Ahkâf", 507 to "Muhammed", 511 to "Fetih", 515 to "Hucurât",
    518 to "Kâf", 520 to "Zâriyât", 523 to "Tûr", 526 to "Necm", 528 to "Kamer", 531 to "Rahmân", 534 to "Vâkıa",
    537 to "Hadîd", 542 to "Mücâdele", 545 to "Haşr", 549 to "Mümtehine", 551 to "Saf", 553 to "Cuma", 554 to "Münâfikûn",
    556 to "Teğâbün", 558 to "Talâk", 560 to "Tahrîm", 562 to "Mülk", 564 to "Kalem", 566 to "Hâkka", 568 to "Meâric",
    570 to "Nûh", 572 to "Cin", 574 to "Müzzemmil", 575 to "Müddessir", 577 to "Kıyâme", 578 to "İnsân", 580 to "Mürselât",
    582 to "Nebe'", 583 to "Nâziât", 585 to "Abese", 586 to "Tekvîr", 587 to "Mutaffifîn", 589 to "İnşikâk", 590 to "Bürûc",
    591 to "A'lâ", 592 to "Gâşiye", 593 to "Fecr", 594 to "Beled", 595 to "Leyl", 596 to "İnşirâh", 597 to "Alak",
    598 to "Beyyine", 599 to "Âdiyât", 600 to "Tekâsür", 601 to "Fîl", 602 to "Kevser", 603 to "Nasr", 604 to "İhlâs",
)

/** Sûre that a page is in (shared pages give the later sûre's name, good enough for "where am I"). */
fun sureOf(page: Int): String = SURE_PAGES.last { it.first <= page.coerceAtLeast(1) }.second

