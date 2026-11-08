package com.namazim

import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

// Özel gün on home and in Ayarlar (design `design/design-system/ozel-gun-secenekler.html`); the rules are in OzelGun.kt.

val OzelRenk = Color(0xFFD9A3B5)
private val GUN = DateTimeFormatter.ofPattern("d MMMM HH:mm", Locale("tr"))
fun ozelTarih(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).format(GUN)

/** The prayer whose vakit holds [t], or null between güneş and öğle. */
private fun slotAt(store: Store, t: Long): Slot? {
    val d = Instant.ofEpochMilli(t).atZone(ZoneId.systemDefault()).toLocalDate()
    return (-1L..0L).flatMap { slots(store.lat, store.lng, d.plusDays(it)) }.firstOrNull { t in it.start until it.end }
}

/** Date, then time; only moments in [min, max]. */
private fun pickMoment(ctx: Context, initial: Long, min: Long, max: Long, onPicked: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = initial }
    DatePickerDialog(ctx, { _, y, mo, d ->
        TimePickerDialog(ctx, { _, h, mi ->
            val t = Calendar.getInstance().apply { set(y, mo, d, h, mi, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            onPicked(t.coerceIn(min, max))
        }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).apply {
        datePicker.minDate = min; datePicker.maxDate = max
    }.show()
}

/** Two or three choices side by side. */
@Composable
private fun Seg(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.s)).border(1.dp, Pal.Border, RoundedCornerShape(Radius.s))) {
        options.forEachIndexed { i, o ->
            Text(o, Modifier.weight(1f).background(if (i == selected) Pal.Track else Color.Transparent).clickable { onSelect(i) }
                .padding(vertical = Space.s), color = if (i == selected) Pal.Gold else Pal.Muted, fontSize = Type.body2, textAlign = TextAlign.Center)
        }
    }
}

/** Home: a quiet "Özel gün başlat" chip, or while a period lasts a band with "Özel gün bitti". */
@Composable
fun OzelGunHome(store: Store, now: Long, onOpen: (String) -> Unit) {
    val og = store.ozelGunNow
    if (og == null) {
        if (!store.ozelGunGizli) Box(Modifier.fillMaxWidth().padding(top = Space.s), contentAlignment = Alignment.Center) {
            Text("🌙  Özel gün başlat", Modifier.clip(CircleShape).border(1.dp, Pal.Border, CircleShape).clickable { onOpen("baslat") }
                .padding(horizontal = Space.m, vertical = 6.dp), color = Pal.Muted, fontSize = Type.label)
        }
        return
    }
    val days = ((now - og.start) / DAY_MS).toInt() + 1
    val max = maxDays(og.nifas, store.hanefi)
    Column(
        Modifier.padding(start = Space.xl, end = Space.xl, top = Space.s).fillMaxWidth().clip(RoundedCornerShape(Radius.m))
            .background(OzelRenk.copy(alpha = .08f)).border(1.dp, OzelRenk.copy(alpha = .35f), RoundedCornerShape(Radius.m)).padding(Space.m),
    ) {
        Text("${if (og.nifas) "Lohusalık" else "Özel gün"} · $days. gün", color = OzelRenk, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
        Text(
            if (days > max) "En uzun süre ($max gün) doldu. Bundan sonrası özür kanı sayılır, namaz kılınır."
            else "Namaz takibi duraklatıldı, kaza yazılmıyor. Zikir ve dua serbest; Kur'an planı bekliyor.",
            Modifier.padding(top = 2.dp), color = if (days > max) Pal.Warn else Pal.Muted, fontSize = Type.label,
        )
        GhostButton("${if (og.nifas) "Lohusalık" else "Özel gün"} bitti", Modifier.padding(top = Space.s).fillMaxWidth(), OzelRenk) { onOpen("bitir") }
    }
}

/** "baslat": start now or earlier; "bitir": end the running period; "gecmis": add a past period (Ayarlar). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OzelGunSheet(mode: String, store: Store, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val now = remember { System.currentTimeMillis() }
    var nifas by remember { mutableStateOf(false) }
    var a by remember { mutableLongStateOf(now) }        // start (baslat / gecmis) or end (bitir)
    var b by remember { mutableLongStateOf(now) }        // end (gecmis)
    var pastA by remember { mutableStateOf(false) }
    var pastB by remember { mutableStateOf(false) }
    val og = store.ozelGunNow
    val done = {
        // nothing to nag about any more (or again): rebuild the alarm chain
        ctx.getSystemService(NotificationManager::class.java).cancelAll()
        Reminders.scheduleNext(ctx)
        onClose()
    }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.xl).navigationBarsPadding().padding(bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.s)) {
            when (mode) {
                "baslat" -> {
                    Text("Özel gün başlat", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
                    Seg(listOf("Hayız", "Lohusalık"), if (nifas) 1 else 0) { nifas = it == 1 }
                    Seg(listOf("Şimdi (${hm(now)})", if (pastA) ozelTarih(a) else "Daha önce…"), if (pastA) 1 else 0) {
                        if (it == 0) { pastA = false; a = now } else pickMoment(ctx, now, now - 60 * DAY_MS, now) { t -> a = t; pastA = true }
                    }
                    val s = slotAt(store, a)
                    Text(startNote(s, a, s != null && store.isPrayed(s.key), store.hanefi), color = Pal.Text, fontSize = Type.body2)
                    if (pastA) Text("Bu tarihten bu yana kazaya yazılanlar düzeltilir.", color = Pal.Muted, fontSize = Type.label)
                    GoldButton("Başlat", Modifier.fillMaxWidth()) { store.setOzelGunler(store.ozelGunler + OzelGun(a, null, nifas)); done() }
                }
                "bitir" -> {
                    if (og == null) { LaunchedEffect(Unit) { onClose() }; return@Column }
                    val max = maxDays(og.nifas, store.hanefi)
                    val maxEnd = og.start + max * DAY_MS
                    Text("${if (og.nifas) "Lohusalık" else "Özel gün"} bitti", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
                    Text("Başlangıç: ${ozelTarih(og.start)}", color = Pal.Muted, fontSize = Type.label)
                    Seg(listOf("Şimdi (${hm(now)})", if (pastA) ozelTarih(a) else "Daha önce…"), if (pastA) 1 else 0) {
                        if (it == 0) { pastA = false; a = now } else pickMoment(ctx, now, og.start, now) { t -> a = t; pastA = true }
                    }
                    val end = minOf(a, maxEnd)
                    if (a > maxEnd) Text("En uzun süre ($max gün) ${ozelTarih(maxEnd)} tarihinde doldu; sonrası özür kanı sayılır ve o günlerin namazları borçtur. Bitiş bu tarih olarak kaydedilir.",
                        color = Pal.Warn, fontSize = Type.body2)
                    Text(endNote(slotAt(store, end), end, og, store.hanefi), color = Pal.Text, fontSize = Type.body2)
                    val min = minDays(og.nifas, store.hanefi)
                    val finish = { store.setOzelGunler(store.ozelGunler.map { if (it == og) og.copy(end = end) else it }); done() }
                    if (end - og.start < min * DAY_MS) {
                        Text("$min günden kısa sürdü: ${if (store.hanefi) "Hanefî" else "Şâfiî"} mezhebine göre hayız sayılmaz, bu günlerin namazları kaza edilir.",
                            color = Pal.Warn, fontSize = Type.body2)
                        GoldButton("Kazaya yaz, özel gün sayma", Modifier.fillMaxWidth()) { store.setOzelGunler(store.ozelGunler - og); done() }
                        GhostButton("Yine de özel gün say", Modifier.fillMaxWidth()) { finish() }
                    } else GoldButton("Tamam, takibe başla", Modifier.fillMaxWidth()) { finish() }
                }
                else -> {
                    Text("Geçmiş bir dönem ekle", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
                    Seg(listOf("Hayız", "Lohusalık"), if (nifas) 1 else 0) { nifas = it == 1 }
                    Text("Başlangıç", color = Pal.Muted, fontSize = Type.label)
                    Seg(listOf(if (pastA) ozelTarih(a) else "Seç…"), 0) { pickMoment(ctx, now - DAY_MS, now - 400 * DAY_MS, now) { t -> a = t; pastA = true } }
                    Text("Bitiş", color = Pal.Muted, fontSize = Type.label)
                    Seg(listOf(if (pastB) ozelTarih(b) else "Seç…"), 0) { pickMoment(ctx, now, now - 400 * DAY_MS, now) { t -> b = t; pastB = true } }
                    val ok = pastA && pastB && b > a
                    if (pastA && pastB && !ok) Text("Bitiş başlangıçtan sonra olmalı.", color = Pal.Warn, fontSize = Type.label)
                    Text("Bu dönemde kazaya yazılmış namazlar silinir.", color = Pal.Muted, fontSize = Type.label)
                    GoldButton("Kaydet", Modifier.fillMaxWidth()) { if (ok) { store.setOzelGunler(store.ozelGunler + OzelGun(a, b, nifas)); done() } }
                }
            }
        }
    }
}
