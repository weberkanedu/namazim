package com.namazim

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// Phone off, forgot to mark: the past can be put right (design A1 + B1 of design/design-system/gecmis-duzelt-secenekler.html,
// 2026-11-11). Marking a vakit that already went to kaza takes the kaza off (Store.markPrayed); undoing puts it back.

/** How far back a day can be corrected, in prayer days. */
const val DUZELT_GUN = 7L

/** B1: the five vakits of a day in the calendar, each with what it is and, within the last week, Kıldım / Geri al / Saat. */
@Composable
fun GunVakitleri(store: Store, day: LocalDate) {
    StoreRev.n.let { }
    val ctx = LocalContext.current
    val now = rememberNow()
    if (!store.hasLocation) return
    val editable = !day.isBefore(store.prayerDay(now).minusDays(DUZELT_GUN - 1))
    slots(store.lat, store.lng, day).forEach { s ->
        val prayed = store.isPrayed(s.key)
        val (txt, col) = when {
            prayed && store.saatsiz(s.key) -> "kılındı · saat bilinmiyor" to Pal.Gold
            prayed -> "kılındı · ${store.prayedAt(s.key)?.let { hm(it) } ?: ""}" + (if (store.onTime(s)) " · vaktinde" else "") to Pal.Gold
            s.start > now -> "henüz girmedi" to Pal.Muted
            !store.tracked(s) -> (if (store.exempt(s)) "özel gün" else "takip öncesi") to Pal.Muted
            s.end > now -> "vakit şimdi" to Pal.Text
            else -> "kazaya yazıldı" to Pal.Warn
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(58.dp)) {
                Text(s.satir(store.kadin), color = Pal.Text, fontSize = Type.body2)
                Text(hm(s.start), color = Pal.Muted, fontSize = Type.micro)
            }
            Text(txt, Modifier.weight(1f), color = col, fontSize = Type.label)
            if (editable && s.start <= now && (prayed || store.tracked(s))) {
                if (prayed) {
                    Text("Saat", Modifier.padding(end = Space.xs).clip(CircleShape).border(1.dp, Pal.Border, CircleShape)
                        .clickable { saatSec(ctx, s, now) { store.markPrayed(s.key, it); Reminders.scheduleNext(ctx) } }
                        .padding(horizontal = 10.dp, vertical = 5.dp), color = Pal.Muted, fontSize = Type.label)
                    Text("Geri al", Modifier.clip(CircleShape).border(1.dp, Pal.Border, CircleShape)
                        .clickable { Reminders.unmarkPrayed(ctx, s.key) }
                        .padding(horizontal = 10.dp, vertical = 5.dp), color = Pal.Muted, fontSize = Type.label)
                } else Text("Kıldım", Modifier.clip(CircleShape).border(1.dp, Pal.Gold.copy(alpha = .6f), CircleShape)
                    .clickable { Reminders.markPrayed(ctx, s.key, s.vakit) }
                    .padding(horizontal = 12.dp, vertical = 5.dp), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
    if (!editable) Text("Bir haftadan eski günler düzeltilemez; kaza defterinden bakılır.", color = Pal.Muted, fontSize = Type.micro)
    else Text("Kazaya yazılmış bir vakti Kıldım ile işaretlersen kazadan düşer; Geri al'da kaza geri yazılır.", color = Pal.Muted, fontSize = Type.micro)
}

/** A1: on home, when two or more vakits went to kaza since home was last opened: tick the ones that were prayed. */
@Composable
fun DonusKarti(store: Store, kazalar: List<Slot>, onBitti: () -> Unit) {
    val ctx = LocalContext.current
    val secili = remember { mutableStateListOf<String>() }
    val gunler = kazalar.groupBy { it.date }
    val gunSayisi = gunler.size
    val tr = Locale("tr")
    Column(
        Modifier.padding(start = Space.l, end = Space.l, top = Space.m).fillMaxWidth().clip(RoundedCornerShape(Radius.l))
            .background(Brush.linearGradient(listOf(Pal.Warn.copy(alpha = .14f), Pal.Card)))
            .border(1.dp, Pal.Warn.copy(alpha = .45f), RoundedCornerShape(Radius.l)).padding(Space.m),
    ) {
        Text(if (gunSayisi > 1) "$gunSayisi gündür uğramadın" else "Uğramadığın vakitler var", style = NumStyle, fontSize = 22.sp, lineHeight = 24.sp, color = Pal.Text)
        Text("${kazalar.size} vakit kazaya yazıldı. Telefon kapalıyken kıldıkların varsa işaretle; kazadan düşer.",
            Modifier.padding(top = 2.dp), color = Pal.Text.copy(alpha = .85f), fontSize = Type.label, lineHeight = 17.sp)
        gunler.forEach { (d, list) ->
            Text(d.format(DateTimeFormatter.ofPattern("EEEE d MMMM", tr)).uppercase(tr), Modifier.padding(top = Space.m, bottom = 4.dp),
                color = Pal.Muted, fontSize = Type.micro, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                slots(store.lat, store.lng, d).forEach { s ->
                    val soru = list.any { it.key == s.key }
                    val on = s.key in secili
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                            .background(if (on) Pal.Gold else Color.Transparent)
                            .border(1.dp, when { on -> Pal.Gold; soru -> Pal.Warn.copy(alpha = .6f); else -> Pal.Border }, RoundedCornerShape(10.dp))
                            .then(if (soru) Modifier.clickable { if (on) secili.remove(s.key) else secili.add(s.key) } else Modifier)
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val c = when { on -> Pal.Ground; soru -> Pal.Warn; else -> Pal.Muted.copy(alpha = .4f) }
                        Text(listOf("S", "Ö", "İ", "A", "Y")[s.vakit.ordinal], color = c, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
                        Text(when { on -> "kıldım"; soru -> "kaza"; store.isPrayed(s.key) -> "✓"; else -> "—" }, color = c, fontSize = 10.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }
        Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            Box(Modifier.weight(1f).clip(CircleShape).border(1.dp, Pal.Border, CircleShape).clickable { onBitti() }.padding(vertical = 11.dp),
                contentAlignment = Alignment.Center) { Text("Hiçbirini kılmadım", color = Pal.Muted, fontSize = Type.label, fontWeight = FontWeight.SemiBold) }
            Box(Modifier.weight(1f).clip(CircleShape).background(Pal.Gold).clickable {
                kazalar.filter { it.key in secili }.forEach { store.markPrayed(it.key) }
                Reminders.scheduleNext(ctx)
                onBitti()
            }.padding(vertical = 11.dp), contentAlignment = Alignment.Center) {
                Text(if (secili.isEmpty()) "Kaydet" else "Kaydet (${secili.size})", color = Pal.Ground, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
