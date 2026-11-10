package com.namazim

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

// Home › the date row: the mübarek günler (design M2) and the two calendars (design C2),
// design/design-system/takvim-secenekler.html, chosen 2026-11-11.

private val TR = Locale("tr")
private val GUN_AY_YIL = DateTimeFormatter.ofPattern("d MMMM yyyy, EEEE", TR)

/** Tap on the mübarek gün line: the hijri year as a bar with a notch per mübarek gün, then a timeline of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MubarekSheet(today: LocalDate, onClose: () -> Unit, onGun: (LocalDate) -> Unit) {
    val y = hicriOf(today).yil
    val days = remember(y) { holyDays(y) + HolyDay("Hicri Yılbaşı (${y + 1})", hicriAyBasi(y + 1, 1)) }
    val start = hicriAyBasi(y, 1); val end = hicriAyBasi(y + 1, 1)
    val total = ChronoUnit.DAYS.between(start, end).toFloat()
    val f = (ChronoUnit.DAYS.between(start, today) / total).coerceIn(0f, 1f)
    val next = days.indexOfFirst { !it.date.isBefore(today) }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.xl).navigationBarsPadding().padding(bottom = Space.xl).verticalScroll(rememberScrollState())) {
            Text("Mübarek günler", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
            Text("Hicri $y yılı: yılın %${(f * 100).toInt()}'i geride", color = Pal.Muted, fontSize = Type.label)
            // the year bar: filled up to today, a notch per mübarek gün (gold ahead, grey behind)
            Canvas(Modifier.padding(top = Space.m).fillMaxWidth().height(16.dp)) {
                val mid = size.height / 2; val h = 8.dp.toPx()
                drawRoundRect(Pal.Track, Offset(0f, mid - h / 2), androidx.compose.ui.geometry.Size(size.width, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                drawRoundRect(Pal.Gold, Offset(0f, mid - h / 2), androidx.compose.ui.geometry.Size(size.width * f, h), androidx.compose.ui.geometry.CornerRadius(h / 2))
                days.dropLast(1).forEach { d ->
                    val x = size.width * (ChronoUnit.DAYS.between(start, d.date) / total)
                    drawLine(if (d.date.isBefore(today)) Color(0xFF5A6582) else Color(0xFFF3EEE3), Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                Text("Muharrem", Modifier.weight(1f), color = Pal.Muted, fontSize = Type.micro)
                Text("Zilhicce", color = Pal.Muted, fontSize = Type.micro)
            }
            // the timeline
            Spacer(Modifier.height(Space.m))
            days.forEachIndexed { i, d ->
                val past = d.date.isBefore(today)
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min).clip(RoundedCornerShape(Radius.s)).clickable { onGun(d.date) }.alpha(if (past) .45f else 1f)) {
                    Column(Modifier.width(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.padding(top = 5.dp).size(12.dp).clip(CircleShape).background(when {
                            past -> Color(0xFF5A6582); i == next -> Pal.Gold; else -> Pal.Track
                        }))
                        if (i < days.lastIndex) Box(Modifier.width(2.dp).weight(1f).background(Pal.Track))
                    }
                    Column(Modifier.weight(1f).padding(start = Space.s, bottom = Space.m)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(d.name, color = Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
                            if (!past) Text(" · " + holyLeft(ChronoUnit.DAYS.between(today, d.date)), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
                        }
                        Text("${d.date.format(GUN_AY_YIL)} · ${hijri(d.date)}", color = Pal.Muted, fontSize = Type.label)
                    }
                }
            }
            Text("Tarihler Umm al-Qura takvimine göre; Diyanet'in ilanıyla bir gün farklı olabilir.", color = Pal.Muted, fontSize = Type.micro)
        }
    }
}

/** Tap on the date: a month, either Miladi (big Gregorian day, small hijri) or Hicri (the other way round); a tapped day shows its times. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TakvimSheet(start: LocalDate, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val today = LocalDate.now()
    var hicri by rememberSaveable { mutableStateOf(true) }
    var secili by remember { mutableStateOf(start) }
    // the month shown, as the day it starts on: a Gregorian 1st, or the 1st of a hijri month
    var ay by remember(hicri) { mutableStateOf(if (hicri) hicriOf(start).let { hicriAyBasi(it.yil, it.ay) } else start.withDayOfMonth(1)) }
    val ayBitis = if (hicri) hicriOf(ay).let { h -> if (h.ay == 12) hicriAyBasi(h.yil + 1, 1) else hicriAyBasi(h.yil, h.ay + 1) } else ay.plusMonths(1)
    val holy = remember(ay) { hicriOf(ay).yil.let { holyDays(it - 1) + holyDays(it) + holyDays(it + 1) }.groupBy { it.date } }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.l).navigationBarsPadding().padding(bottom = Space.xl).verticalScroll(rememberScrollState())) {
            // Miladi | Hicri
            Row(Modifier.fillMaxWidth().clip(CircleShape).background(Color(0xFF0E1830)).padding(3.dp)) {
                listOf(false to "Miladi", true to "Hicri").forEach { (h, t) ->
                    Box(Modifier.weight(1f).clip(CircleShape).background(if (hicri == h) Pal.Gold.copy(alpha = .18f) else Color.Transparent)
                        .clickable { hicri = h }.padding(vertical = 7.dp), contentAlignment = Alignment.Center) {
                        Text(t, color = if (hicri == h) Pal.Gold else Pal.Muted, fontSize = Type.label, fontWeight = if (hicri == h) FontWeight.SemiBold else FontWeight.Medium)
                    }
                }
            }
            // the month and the arrows
            Row(Modifier.fillMaxWidth().padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", Modifier.clip(CircleShape).clickable {
                    ay = if (hicri) hicriOf(ay.minusDays(1)).let { hicriAyBasi(it.yil, it.ay) } else ay.minusMonths(1)
                }.padding(horizontal = Space.m, vertical = Space.xs), color = Pal.Muted, fontSize = 24.sp)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val son = ayBitis.minusDays(1)
                    if (hicri) {
                        val h = hicriOf(ay)
                        Text("${HICRI_AYLAR[h.ay - 1]} ${h.yil}", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
                        Text("${ay.format(DateTimeFormatter.ofPattern("d MMMM", TR))} – ${son.format(DateTimeFormatter.ofPattern("d MMMM yyyy", TR))}", color = Pal.Gold, fontSize = Type.label)
                    } else {
                        Text(ay.format(DateTimeFormatter.ofPattern("MMMM yyyy", TR)), style = NumStyle, fontSize = Type.h2, color = Pal.Text)
                        val a = hicriOf(ay); val b = hicriOf(son)
                        Text(if (a.ay == b.ay) "${HICRI_AYLAR[a.ay - 1]} ${a.yil}" else "${HICRI_AYLAR[a.ay - 1]} – ${HICRI_AYLAR[b.ay - 1]} ${b.yil}", color = Pal.Gold, fontSize = Type.label)
                    }
                }
                Text("›", Modifier.clip(CircleShape).clickable { ay = ayBitis }.padding(horizontal = Space.m, vertical = Space.xs), color = Pal.Muted, fontSize = 24.sp)
            }
            // the grid, Monday first, the neighbouring months' days faded
            Row(Modifier.padding(top = Space.s)) {
                listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz").forEach { Text(it, Modifier.weight(1f), color = Pal.Muted, fontSize = Type.micro, textAlign = TextAlign.Center) }
            }
            val first = ay.minusDays((ay.dayOfWeek.value - 1).toLong())
            val cells = (0 until 42).map { first.plusDays(it.toLong()) }   // always six weeks: the sheet keeps its height, the arrows stay put
            cells.chunked(7).forEach { week ->
                Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    week.forEach { d ->
                        val disari = d.isBefore(ay) || !d.isBefore(ayBitis)
                        val h = hicriOf(d)
                        Box(
                            Modifier.weight(1f).height(48.dp).clip(RoundedCornerShape(9.dp))
                                .background(if (d == secili) Pal.Gold.copy(alpha = .2f) else Color(0xFF0E1830))
                                .then(if (d == today) Modifier.border(1.5.dp, Pal.Gold, RoundedCornerShape(9.dp)) else Modifier)
                                .clickable { secili = d }.alpha(if (disari) .3f else 1f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${if (hicri) h.gun else d.dayOfMonth}", style = NumStyle, fontSize = 18.sp, lineHeight = 18.sp, color = Pal.Text)
                                Text(
                                    if (hicri) "${d.dayOfMonth}" else if (h.gun == 1) HICRI_AYLAR[h.ay - 1].take(4) + "." else "${h.gun}",
                                    color = if (hicri) Pal.Muted else Pal.Gold, fontSize = 9.sp, lineHeight = 10.sp, fontWeight = FontWeight.SemiBold,
                                )
                            }
                            if (holy[d] != null) Box(Modifier.align(Alignment.TopEnd).padding(5.dp).size(5.dp).clip(CircleShape).background(Pal.Gold))
                        }
                    }
                }
            }
            Text(if (hicri) "Büyük sayı hicri gün, küçük sayı miladi gün. Altın nokta mübarek gün." else "Büyük sayı miladi gün, küçük altın sayı hicri gün. Altın nokta mübarek gün.",
                Modifier.padding(top = Space.s), color = Pal.Muted, fontSize = Type.micro)

            // the tapped day: its dates, its mübarek gün, its prayer times
            Column(Modifier.padding(top = Space.m).fillMaxWidth().clip(RoundedCornerShape(Radius.m)).background(Color(0xFF0E1830)).padding(Space.m)) {
                Text(secili.format(GUN_AY_YIL), color = Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
                Text(hijri(secili), color = Pal.Gold, fontSize = Type.label)
                holy[secili]?.forEach { Text("✦ ${it.name}", Modifier.padding(top = 2.dp), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold) }
                // design B1 (gecmis-duzelt-secenekler.html): each vakit with its state; within the last week it can be put right
                Column(Modifier.padding(top = Space.s)) { GunVakitleri(store, secili) }
            }
        }
    }
}
