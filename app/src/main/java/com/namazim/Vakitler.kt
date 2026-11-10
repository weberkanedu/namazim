package com.namazim

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import android.graphics.BlurMaskFilter
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

// Home "Vakitler" (design V7, design/design-system/vakitler-secenekler.html): the logo rose on top (outer petal = prayed,
// inner petal = prayed in the first third, the gül's "Vaktinde" petal), the current vakit as a three-part strip,
// one big "kıldım", then the day's list with the ek vakitler as chips. Any other vakit opens a sheet.

enum class VState { VAKTINDE, KILINDI, KAZA, ACIK, SONRA, GECTI }

/** [onTime]: prayed in a faziletli slice of the chosen mezhep (`Store.onTime`, `Fazilet.kt`). */
fun vakitState(s: Slot, now: Long, prayed: Boolean, onTime: Boolean, tracked: Boolean): VState = when {
    prayed -> if (onTime) VState.VAKTINDE else VState.KILINDI
    now < s.start -> VState.SONRA
    now < s.end -> VState.ACIK
    tracked -> VState.KAZA
    else -> VState.GECTI   // before tracking started: neither kaza nor owed
}

private val NAFILE = setOf("İşrak", "Kuşluk (duha)", "Evvâbin", "Teheccüd")
private val ROW_NAME = listOf("İmsak", "Öğle", "İkindi", "Akşam", "Yatsı")
private val ACCUSATIVE = listOf("Sabahı", "Öğleyi", "İkindiyi", "Akşamı", "Yatsıyı")
private val EXTRA_GROUP = listOf("GÜNEŞ", "ÖĞLE", "İKİNDİ", "AKŞAM", "YATSI")
private val Cream = Color(0xFFFFF4DC)
// K6 petal colours, base -> middle -> tip: night-blue velvet (empty) and warm gold (prayed)
private val Velvet = listOf(Color(0xFF060A18), Color(0xFF1A2A52), Color(0xFF4A65A6))
private val Warm = listOf(Color(0xFF5A3A10), Color(0xFFC8913C), Color(0xFFFFE7B0))
private val Velvet2 = listOf(Color(0xFF050914), Color(0xFF1C2C58), Color(0xFF5370B2))
private val Warm2 = listOf(Color(0xFF6B4312), Color(0xFFE2B060), Color(0xFFFFF3D6))

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VakitlerBody(
    slots: List<Slot>, now: Long, gunes: Long, ex: List<Extra>, next: Pair<Long, String>,
    onTesbihat: (String) -> Unit = {},
    between: @Composable () -> Unit,
) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    StoreRev.n.let { } // marking bumps the rev: redraw at once, without resetting the animations
    val haptic = LocalHapticFeedback.current
    // a prayer in a woman's özel gün is not owed: no button, no kaza ("takip yok")
    val exempt = slots.map { !store.isPrayed(it.key) && store.exempt(it) }
    val st = slots.mapIndexed { i, s -> if (exempt[i]) VState.GECTI else vakitState(s, now, store.isPrayed(s.key), store.onTime(s), store.tracked(s)) }
    var ozel by remember { mutableStateOf<String?>(null) }
    val ozelNow = store.ozelGunNow
    val cur = slots.indexOfFirst { now in it.start until it.end }   // -1 between güneş and öğle
    val c = slots.getOrNull(cur)
    val cs = st.getOrNull(cur)
    val adlar = slots.map { it.satir(store.kadin) }   // "Cuma" in place of Öğle on a man's Friday
    var sheet by remember { mutableStateOf<Int?>(null) }
    val toggle = { i: Int ->
        val s = slots[i]
        if (store.isPrayed(s.key)) { Reminders.unmarkPrayed(ctx, s.key); store.setTesbihat(s.key, null) }
        else if (now >= s.start) {
            Reminders.markPrayed(ctx, s.key, s.vakit)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
    // the current vakit in seven slices; the faziletli ones depend on the mezhep (Fazilet.kt)
    val dl = c?.let { store.dilimler(it) }
    val band = if (c != null && dl != null) faziletBand(c, dl) else null
    val open = cs == VState.ACIK
    val inBand = open && band != null && now >= band.first && now < band.second
    val beforeBand = open && band != null && now < band.first
    val inKerahat = open && dl != null && dl[dilimOf(c!!, now)] == Dilim.KERAHAT
    val bandText = band?.let { "faziletli dilim ${hm(it.first)}–${hm(it.second)}" } ?: ""

    val (label, time, sub) = when {
        inBand -> Triple("Vaktinde kılmak için", countdown(band!!.second - now), "son ${hm(band.second)} · sonra da kılınır, vakit ${hm(c!!.end)} çıkar")
        beforeBand -> Triple("Faziletli vakte", countdown(band!!.first - now), "şimdi de kılınır · $bandText")
        inKerahat && c!!.vakit == Vakit.YATSI -> Triple("Gece yarısı geçti", countdown(c.end - now), "mekruh vakit · vakit ${hm(c.end)} çıkar")
        inKerahat -> Triple("Kerahat vakti", countdown(c!!.end - now), "geciktirme · vakit ${hm(c.end)} çıkar")
        open -> Triple("Vakit çıkmadan", countdown(c!!.end - now), "faziletli dilim bitti · vakit ${hm(c.end)} çıkar")
        else -> Triple(
            if (next.second == "Güneş doğuşuna") next.second else "${next.second} vaktine", countdown(next.first - now),
            when (cs) {
                VState.VAKTINDE -> "${c!!.ad(store.kadin)} vaktinde kılındı"
                VState.KILINDI -> "${c!!.ad(store.kadin)} kılındı"
                else -> "kaldı"
            },
        )
    }
    val accent = if (inKerahat) Pal.Warn else Pal.Gold
    // the tesbihat card waits under a prayed vakit until it is marked or put off with "Sonra" (for this session)
    var later by rememberSaveable { mutableStateOf(listOf<String>()) }
    val tesbihEl = { key: String -> store.setTesbihat(key, "el"); haptic.performHapticFeedback(HapticFeedbackType.LongPress) }

    Column(Modifier.fillMaxWidth()) {
        if (store.kadin) OzelGunHome(store, now) { ozel = it }
        val arc = if (inBand) (band!!.second - now).toFloat() / (band.second - band.first) else null
        VakitGul(st, cur, arc, inKerahat, Modifier.align(Alignment.CenterHorizontally).size(236.dp).turHedef("gul").alpha(if (ozelNow != null) .35f else 1f), adlar) { i ->
            if (i == cur && st[i] == VState.ACIK) toggle(i) else sheet = i
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label.uppercase(Locale("tr")), color = accent, fontSize = Type.micro, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
            Text(time, style = NumStyle, fontSize = Type.display, lineHeight = 48.sp, color = if (inKerahat) Pal.Warn else Pal.Text)
            Text(sub, color = Pal.Muted, fontSize = Type.body2, textAlign = TextAlign.Center)
        }
        // güneş to öğle has no farz: a thin line shows the two kerahat times (design A2, design/design-system/ara-serit.html)
        if (c == null && now >= gunes && now < slots[1].start) AraSerit(gunes, slots[1].start, ex, now,
            Modifier.padding(start = Space.xl, end = Space.xl, top = Space.m).turHedef("serit"))
        if (c != null && cs != null) {
            VakitSerit(c, store.prayedAt(c.key).takeIf { store.isPrayed(c.key) }, dl!!, now,
                Modifier.padding(start = Space.xl, end = Space.xl, top = Space.m).turHedef("serit"))
            val mode = when {
                cs == VState.ACIK -> 0
                (cs == VState.VAKTINDE || cs == VState.KILINDI) && store.askTesbihat && store.tesbihat(c.key) == null && c.key !in later -> 1
                else -> 2
            }
            AnimatedContent(mode, Modifier.padding(start = Space.xl, end = Space.xl, top = Space.m).turHedef("kildim"),
                transitionSpec = { (fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 3 }) togetherWith fadeOut(tween(200)) }, label = "kildim") { m ->
                when (m) {
                    0 -> GoldButton((if (adlar[cur] == "Cuma") "Cumayı" else ACCUSATIVE[cur]) + " kıldım" + if (inBand) " · vaktinde" else "", Modifier.fillMaxWidth(), Icons.Default.Check) { toggle(cur) }
                    1 -> TesbihatCard(c.ad(store.kadin), onEl = { tesbihEl(c.key) }, onApp = { onTesbihat(c.key) },
                        onLater = { later = later + c.key })
                    // no undo button here: one stray touch would take the prayer back. Undo is in the vakit sheet (petal or row).
                    else -> Spacer(Modifier.fillMaxWidth())
                }
            }
        }
        between()
        Text("BUGÜN", Modifier.padding(start = Space.xl, top = Space.l, bottom = Space.xs), color = Pal.Muted,
            fontSize = Type.micro, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        slots.forEachIndexed { i, s ->
            val on = i == cur
            Row(
                Modifier.fillMaxWidth()
                    .background(if (on) Brush.horizontalGradient(listOf(accent.copy(alpha = .10f), Color.Transparent)) else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent)))
                    .clickable { sheet = i }.heightIn(min = 52.dp).padding(horizontal = Space.xl),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Bead(st[i], on, Modifier.size(20.dp))
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text(adlar[i], color = if (on) Pal.Gold else Pal.Text, fontSize = 15.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    val at = store.prayedAt(s.key)?.let { " · " + hm(it) } ?: ""
                    when (st[i]) {
                        VState.VAKTINDE -> Text("vaktinde$at", color = Pal.Gold, fontSize = Type.micro)
                        VState.KILINDI -> Text("kılındı$at", color = Pal.Muted, fontSize = Type.micro)
                        VState.KAZA -> Text("kaza", color = Pal.Warn, fontSize = Type.micro)
                        VState.ACIK -> Text("şimdi · ${hm(s.start)}–${hm(s.end)}", color = accent, fontSize = Type.micro)
                        else -> if (exempt[i]) Text("özel gün · takip yok", color = OzelRenk, fontSize = Type.micro)
                    }
                }
                if (store.askTesbihat && (st[i] == VState.VAKTINDE || st[i] == VState.KILINDI)) {
                    TesbihIcon(store.tesbihat(s.key)) { sheet = i }
                    Spacer(Modifier.width(Space.m))
                }
                Text(hm(s.start), style = NumStyle, fontSize = 20.sp, color = if (on) Pal.Text else Pal.Muted)
            }
            val chips = rowExtras(i, ex, gunes)
            if (chips.isNotEmpty()) FlowRow(
                Modifier.padding(start = Space.xl + 32.dp, end = Space.xl, bottom = Space.s),
                horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
            ) { chips.forEach { ExtraChip(it, now, store, slots[0].date) } }
        }
        Spacer(Modifier.height(Space.xl))
    }

    ozel?.let { OzelGunSheet(it, store) { ozel = null } }
    sheet?.let { i ->
        val s = slots[i]
        val x = st[i]
        val at = store.prayedAt(s.key)?.let { " · " + hm(it) } ?: ""
        ModalBottomSheet({ sheet = null }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
            Column(Modifier.padding(horizontal = Space.xl).navigationBarsPadding().padding(bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                Text("${s.ad(store.kadin)} namazı", style = NumStyle, fontSize = Type.title, color = Pal.Text)
                Text("${hm(s.start)} – ${hm(s.end)}" + (faziletBand(s, store.dilimler(s))?.let { " · faziletli dilim ${hm(it.first)}–${hm(it.second)}" } ?: ""),
                    color = Pal.Muted, fontSize = Type.body2)
                val (msg, col) = when (x) {
                    VState.VAKTINDE -> "Vaktinde kılındı$at" to Pal.Gold
                    VState.KILINDI -> "Kılındı$at · vaktinde dilimi geçmişti" to Pal.Text
                    VState.KAZA -> "Kılınmadı · kazaya yazıldı" to Pal.Warn
                    VState.ACIK -> "Vakit şimdi" to Pal.Gold
                    VState.SONRA -> "Henüz girmedi" to Pal.Muted
                    VState.GECTI -> "Takip başlamadan önceydi" to Pal.Muted
                }
                Text(msg, color = col, fontSize = Type.body, fontWeight = FontWeight.Medium)
                val chips = rowExtras(i, ex, gunes)
                if (chips.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { ExtraChip(it, now, store, s.date) }
                }
                if (store.askTesbihat && store.isPrayed(s.key)) {
                    val tk = store.tesbihat(s.key)
                    Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
                        TesbihIcon(tk) {}
                        Spacer(Modifier.width(Space.m))
                        Text(when (tk) { "el" -> "Tesbihat yapıldı (elle / cemaatle)"; "app" -> "Tesbihat uygulamada çekildi"; else -> "Tesbihat · 33 · 33 · 33" },
                            Modifier.weight(1f), color = if (tk != null) Pal.Gold else Pal.Text, fontSize = Type.body)
                        if (tk != null) TextButton({ store.setTesbihat(s.key, null) }) { Text("geri al", color = Pal.Muted, fontSize = Type.body2) }
                    }
                    if (tk == null) Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                        GhostButton("Yaptım", Modifier.weight(1f), Pal.Gold) { tesbihEl(s.key); sheet = null }
                        GoldButton("Zikirhanede çek", Modifier.weight(1f)) { sheet = null; onTesbihat(s.key) }
                    }
                }
                Spacer(Modifier.height(Space.xs))
                when {
                    store.isPrayed(s.key) -> GhostButton("Namazı geri al", Modifier.fillMaxWidth()) { toggle(i); sheet = null }
                    x == VState.SONRA -> Text("Vakit girince işaretlenir", color = Pal.Muted, fontSize = Type.body2)
                    else -> GoldButton("Kıldım", Modifier.fillMaxWidth(), Icons.Default.Check) { toggle(i); sheet = null }
                }
            }
        }
    }
}

/** After Kıldım: the tesbihat of that vakit, done by hand / with the cemaat, or said in Zikirhane. */
@Composable
private fun TesbihatCard(vakit: String, onEl: () -> Unit, onApp: () -> Unit, onLater: () -> Unit) {
    val turn by rememberInfiniteTransition(label = "tesbih").animateFloat(0f, 360f,
        infiniteRepeatable(tween(14_000, easing = androidx.compose.animation.core.LinearEasing)), label = "turn")
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.l)).background(Pal.Card)
            .border(1.dp, Pal.Gold.copy(alpha = .45f), RoundedCornerShape(Radius.l)).padding(Space.l),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(46.dp)) {
                val r = size.minDimension / 2 - 3.dp.toPx()
                for (b in 0 until 33) {
                    val a = Math.toRadians((b * 360.0 / 33) + turn - 90)
                    drawCircle(if (b % 11 == 0) Pal.Gold else Pal.Gold.copy(alpha = .35f), 2.2.dp.toPx(),
                        Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat()))
                }
            }
            Spacer(Modifier.width(Space.m))
            Column(Modifier.weight(1f)) {
                Text("$vakit tesbihatı", color = Pal.Text, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
                Text("33 Sübhânallah · 33 Elhamdülillah · 33 Allâhu ekber", color = Pal.Muted, fontSize = Type.label)
            }
        }
        Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            GhostButton("Yaptım", Modifier.weight(1f), Pal.Gold, onEl)
            GoldButton("Zikirhanede çek", Modifier.weight(1f), onClick = onApp)
        }
        TextButton(onLater, Modifier.align(Alignment.CenterHorizontally)) { Text("Sonra", color = Pal.Muted, fontSize = Type.body2) }
    }
}

/** The tesbih mark on a prayed row: dashed = not yet, gold ring = by hand, filled gold = said in the app. */
@Composable
private fun TesbihIcon(kind: String?, onClick: () -> Unit) {
    Box(
        Modifier.size(32.dp).clip(CircleShape).clickable(onClick = onClick)
            .background(if (kind == "app") Pal.Gold else if (kind == "el") Pal.Gold.copy(alpha = .14f) else Color.Transparent)
            .border(1.dp, if (kind == null) Pal.Border else Pal.Gold.copy(alpha = .7f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_nav_zikir), when (kind) { "el" -> "Tesbihat yapıldı"; "app" -> "Tesbihat uygulamada"; else -> "Tesbihat bekliyor" },
            Modifier.size(16.dp), tint = if (kind == "app") Pal.Ground else if (kind == "el") Pal.Gold else Pal.Muted)
    }
}

/** Ek vakitler under row [i]; the İmsak row also carries Güneş, since güneş–öğle has no prayer of its own. */
private fun rowExtras(i: Int, ex: List<Extra>, gunes: Long): List<Extra> =
    (if (i == 0) listOf(Extra("Güneş", gunes, null, false, "GÜNEŞ")) else emptyList()) + ex.filter { it.group == EXTRA_GROUP[i] }

@Composable
private fun ExtraChip(e: Extra, now: Long, store: Store, day: java.time.LocalDate) {
    val live = e.end != null && now in e.start until e.end
    // with "nafile takibi" on, the four nafile chips mark themselves prayed (only once their time has come)
    val nafile = store.nafileTakip && e.name in NAFILE
    val key = "$day|${e.name}"
    val done = nafile && store.nafileKilindi(key)
    val col = when {
        e.kerahat -> Pal.Warn
        live || done -> Pal.Gold
        else -> Pal.Muted
    }
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .background(if (done) Pal.Gold.copy(alpha = .14f) else Color.Transparent)
            .border(1.dp, if (live || e.kerahat || done) col.copy(alpha = if (live || done) .9f else .45f) else Pal.Border, RoundedCornerShape(50))
            .clickable(enabled = nafile && now >= e.start) { store.toggleNafile(key) }
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text((if (done) "✓ " else "") + e.name, color = col, fontSize = 11.sp)
        Spacer(Modifier.width(4.dp))
        Text(hm(e.start), style = NumStyle, fontSize = 13.sp, color = Pal.Text)
    }
}

/** A shaded tesbih bead: gold when prayed (a cream heart when on time), peach for kaza, the current one beats. */
@Composable
private fun Bead(state: VState, live: Boolean, modifier: Modifier) {
    val beat = if (live && state == VState.ACIK) heartbeat() else null
    Canvas(modifier) {
        val r = size.minDimension / 2
        val stops = when (state) {
            VState.VAKTINDE, VState.KILINDI -> listOf(Color(0xFFFFF1CF), Pal.Gold, Color(0xFF8A6420))
            VState.KAZA -> listOf(Color(0xFFFFD9C4), Pal.Warn, Color(0xFF9A5A3E))
            else -> listOf(Color(0xFF3A4A68), Color(0xFF1C2943), Color(0xFF1C2943))
        }
        beat?.let { b -> drawCircle(Pal.Gold.copy(alpha = .15f + .3f * b.value), r * (1.25f + .45f * b.value), style = Stroke(1.5.dp.toPx())) }
        drawCircle(Brush.radialGradient(stops, center = Offset(size.width * .35f, size.height * .3f), radius = r * 1.5f), r)
        if (state == VState.VAKTINDE) drawCircle(Color(0xFFFFF8E6), r * .32f)
        if (state == VState.ACIK) drawCircle(Pal.Gold, r - 1.dp.toPx(), style = Stroke(2.dp.toPx()))
    }
}

/** The logo rose: petal i = vakit i clockwise from the top. Outer petal fills gold when prayed, the inner one opens
 *  when it was on time; over the current petal a gold arc shrinks with the time left for "vaktinde". */
@Composable
private fun VakitGul(st: List<VState>, cur: Int, arc: Float?, kerahat: Boolean, modifier: Modifier, names: List<String> = ROW_NAME, onPetal: (Int) -> Unit) {
    val measurer = rememberTextMeasurer()
    val fill = st.map { animateFloatAsState(if (it == VState.VAKTINDE || it == VState.KILINDI) 1f else 0f, spring(.45f, 220f), label = "fill").value }
    val inner = st.map { animateFloatAsState(if (it == VState.VAKTINDE) 1f else 0f, spring(.4f, 150f), label = "inner").value }
    val breathe by rememberInfiniteTransition(label = "gul").animateFloat(.35f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "breathe")
    val tap by rememberUpdatedState(onPetal)
    val curNow by rememberUpdatedState(cur)
    val shade = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
    val blurs = remember { HashMap<Float, BlurMaskFilter>() }
    Canvas(modifier.pointerInput(Unit) {
        detectTapGestures { o ->
            val dx = o.x - size.width / 2f
            val dy = o.y - size.height / 2f
            if (hypot(dx, dy) < size.width * .07f && curNow >= 0) { tap(curNow); return@detectTapGestures }
            var deg = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
            if (deg < 0) deg += 360.0
            tap((deg / 72.0).roundToInt() % 5)
        }
    }) {
        val k = size.minDimension / 222f   // room around the petals for the labels
        fun pt(r: Float, a: Float): Offset {
            val t = Math.toRadians(a - 90.0)
            return Offset(center.x + r * k * cos(t).toFloat(), center.y + r * k * sin(t).toFloat())
        }
        fun petal(r0: Float, r1: Float, a0: Float, a1: Float, close: Boolean) = Path().apply {
            val w = a1 - a0
            val s0 = pt(r0, a0); moveTo(s0.x, s0.y)
            val c1 = pt(r1 * 1.02f, a0 - w * .08f); val c2 = pt(r1 * 1.16f, a0 + w * .34f); val m = pt(r1, a0 + w * .5f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, m.x, m.y)
            val c3 = pt(r1 * 1.16f, a1 - w * .34f); val c4 = pt(r1 * 1.02f, a1 + w * .08f); val e0 = pt(r0, a1)
            cubicTo(c3.x, c3.y, c4.x, c4.y, e0.x, e0.y)
            if (close) close()
        }
        val dash = PathEffect.dashPathEffect(floatArrayOf(3f * k, 3f * k))
        // the tip of a petal curls toward the light: a thin bright line just inside the rim
        fun tip(r1: Float, a0: Float, a1: Float, kk: Float) = Path().apply {
            val w = a1 - a0
            val s0 = pt(r1 * kk, a0 + w * .18f); moveTo(s0.x, s0.y)
            val c1 = pt(r1 * 1.1f, a0 + w * .36f); val c2 = pt(r1 * 1.1f, a1 - w * .36f); val e0 = pt(r1 * kk, a1 - w * .18f)
            cubicTo(c1.x, c1.y, c2.x, c2.y, e0.x, e0.y)
        }
        // soft shadow under a petal, so each one lies on the one below
        fun shadow(p: Path, dy: Float, blur: Float, alpha: Float) = drawIntoCanvas { cv ->
            shade.color = android.graphics.Color.argb((alpha * 255).toInt(), 0, 0, 0)
            shade.maskFilter = blurs.getOrPut(blur * k) { BlurMaskFilter(blur * k, BlurMaskFilter.Blur.NORMAL) }
            cv.save(); cv.translate(0f, dy * k); cv.nativeCanvas.drawPath(p.asAndroidPath(), shade); cv.restore()
        }
        // design K6 "gerçek yaprak" (design/design-system/gul-kabartma-2.html): cupped petals, dark at the base and light
        // toward the tip; night-blue velvet when empty, warm gold when prayed. The current petal goes last so nothing covers it.
        val order = (0 until 5).filter { it != cur } + listOfNotNull(cur.takeIf { it >= 0 })
        for (i in order) {
            val a = i * 72f
            val x = st[i]
            val open = i == cur && x == VState.ACIK
            val f = fill[i].coerceIn(0f, 1f)
            val outer = petal(24f, 70f, a - 40f, a + 40f, true)
            shadow(outer, 3f, 2.6f, .6f)
            scale(1f + .08f * (fill[i] - f), pivot = pt(24f, a)) {   // the spring's overshoot is a small pop
                drawPath(outer, Brush.radialGradient(
                    0f to lerp(Velvet[0], Warm[0], f), .55f to lerp(Velvet[1], Warm[1], f), 1f to lerp(Velvet[2], Warm[2], f),
                    center = pt(16f, a), radius = 66f * k,
                ))
            }
            if (x == VState.KAZA) drawPath(outer, Pal.Warn.copy(alpha = .30f))
            val t = tip(70f, a - 40f, a + 40f, .9f)
            drawPath(t, lerp(Color(0xFF9DB2E6), Color(0xFFFFF6DC), f).copy(alpha = .22f + .2f * f), style = Stroke(4f * k, cap = StrokeCap.Round))
            drawPath(tip(70f, a - 40f, a + 40f, .92f), lerp(Color(0xFFC6D3F2), Color(0xFFFFFBEF), f).copy(alpha = .6f + .35f * f),
                style = Stroke(1f * k, cap = StrokeCap.Round))
            val edge = when {
                x == VState.KAZA || (open && kerahat) -> Pal.Warn
                open -> Pal.Text
                else -> lerp(Color(0xFF0A1124), Color(0xFF7A5418), f)
            }
            drawPath(petal(24f, 70f, a - 40f, a + 40f, false), edge.copy(alpha = if (open && kerahat) breathe else .95f),
                style = Stroke((if (open) 2.4f else if (x == VState.KAZA) 1.6f else 1f) * k, join = StrokeJoin.Round))
            if (i == cur && arc != null) {
                val r = 79f * k
                drawArc(Pal.Gold, a - 38f - 90f, 76f * arc, false, Offset(center.x - r, center.y - r), Size(2 * r, 2 * r),
                    style = Stroke(3.5f * k, cap = StrokeCap.Round))
                drawCircle(Cream, 3.5f * k, pt(79f, a - 38f + 76f * arc))
            }
            val col = when {
                i == cur -> if (kerahat) Pal.Warn else Pal.Gold
                x == VState.KAZA -> Pal.Warn
                else -> Pal.Muted
            }
            val l = measurer.measure(names[i], TextStyle(color = col, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
            val p = pt(91f, a)
            drawText(l, topLeft = Offset(p.x - l.size.width / 2f, p.y - l.size.height / 2f))
        }
        // inner petals sit one layer higher; the on-time one turns gold with a small pop
        for (i in order) {
            val a = i * 72f
            val g = inner[i].coerceIn(0f, 1f)
            val inn = petal(12f, 46f, a - 26f, a + 26f, true)
            shadow(inn, 2.4f, 1.3f, .6f)
            scale(.94f + .06f * inner[i], pivot = pt(12f, a)) {
                drawPath(inn, Brush.radialGradient(
                    0f to lerp(Velvet2[0], Warm2[0], g), .6f to lerp(Velvet2[1], Warm2[1], g), 1f to lerp(Velvet2[2], Warm2[2], g),
                    center = pt(8f, a), radius = 44f * k,
                ))
                drawPath(tip(46f, a - 26f, a + 26f, .88f), lerp(Color(0xFFB9C8EE), Color(0xFFFFFBEF), g).copy(alpha = .75f),
                    style = Stroke(1f * k, cap = StrokeCap.Round))
            }
            if (i == cur && st[i] == VState.ACIK && arc != null)   // the on-time slice is still open: the inner petal waits, breathing
                drawPath(petal(12f, 46f, a - 26f, a + 26f, false), Pal.Gold.copy(alpha = breathe), style = Stroke(1.2f * k, pathEffect = dash))
        }
        val bc = Offset(center.x, center.y + 1.5f * k)
        drawIntoCanvas { cv ->
            shade.color = android.graphics.Color.argb(150, 0, 0, 0)
            shade.maskFilter = blurs.getOrPut(1.3f * k) { BlurMaskFilter(1.3f * k, BlurMaskFilter.Blur.NORMAL) }
            cv.nativeCanvas.drawCircle(bc.x, bc.y, 11f * k, shade)
        }
        drawCircle(Brush.radialGradient(0f to Color(0xFF6B4312), .5f to Color(0xFFD49E48), 1f to Color(0xFFFFF0C8),
            center = Offset(center.x, center.y + 10.5f * k), radius = 22f * k), 10.5f * k)
        val bud = Path()
        for (j in 0..60) {
            val th = j / 60.0 * Math.PI * 3.2
            val r = 9f * k * (1 - j / 68f)
            val px = center.x + r * cos(th).toFloat(); val py = center.y + r * sin(th).toFloat()
            if (j == 0) bud.moveTo(px, py) else bud.lineTo(px, py)
        }
        drawPath(bud, Color(0xFF5A3A10), style = Stroke(1.3f * k))
    }
}

/** Güneş to öğle: a thin line with the işrak and istiva kerahat in peach and a light at the clock; one line under it. */
@Composable
private fun AraSerit(gunes: Long, ogle: Long, ex: List<Extra>, now: Long, modifier: Modifier) {
    val kers = ex.filter { it.kerahat && it.end != null && it.start >= gunes && it.end <= ogle }
    val len = (ogle - gunes).toFloat()
    fun f(t: Long) = ((t - gunes) / len).coerceIn(0f, 1f)
    val inKer = kers.firstOrNull { now >= it.start && now < it.end!! }
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val h = 4.dp.toPx()
            val y0 = (size.height - h) / 2
            val rr = CornerRadius(h / 2)
            drawRoundRect(Pal.Track, Offset(0f, y0), Size(size.width, h), rr)
            kers.forEach { k -> drawRoundRect(Pal.Warn, Offset(f(k.start) * size.width, y0), Size((f(k.end!!) - f(k.start)) * size.width, h), rr) }
            val nx = f(now) * size.width
            drawCircle(Pal.Gold.copy(alpha = .35f), 8.dp.toPx(), Offset(nx, size.height / 2))
            drawCircle(Cream, 5.dp.toPx(), Offset(nx, size.height / 2))
        }
        Text(
            if (inKer != null) "${inKer.name} · ${hm(inKer.end!!)} kadar namaz kılınmaz" else "kuşluk vakti · nafile kılınabilir",
            Modifier.fillMaxWidth().padding(top = Space.xs), color = if (inKer != null) Pal.Warn else Pal.Gold,
            fontSize = Type.label, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
        )

    }
}

/** The current vakit in seven slices: faziletli gold, güzel half gold, the rest dark, kerahat / mekruh hatched peach;
 *  a light walks with the clock and the slice it is in is named under the strip. */
@Composable
private fun VakitSerit(s: Slot, prayedAt: Long?, dl: List<Dilim>, now: Long, modifier: Modifier) {
    val beat = heartbeat()
    val len = (s.end - s.start).toFloat()
    fun f(t: Long) = ((t - s.start) / len).coerceIn(0f, 1f)
    val nowIn = dilimOf(s, now)
    Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val h = 12.dp.toPx()
            val y0 = (size.height - h) / 2
            val gap = 3.dp.toPx()
            val w = size.width
            val rr = CornerRadius(h / 2)
            for (j in 0 until 7) {
                val x0 = j * w / 7 + if (j > 0) gap / 2 else 0f
                val x1 = (j + 1) * w / 7 - if (j < 6) gap / 2 else 0f
                val past = prayedAt != null || j < nowIn
                val col = when (dl[j]) {
                    Dilim.FAZILET -> Pal.Gold.copy(alpha = if (past) .28f else 1f)
                    Dilim.GUZEL -> Pal.Gold.copy(alpha = if (past) .16f else .45f)
                    Dilim.KERAHAT -> Pal.Warn.copy(alpha = .18f)
                    Dilim.VAKIT -> Pal.Track
                }
                drawRoundRect(col, Offset(x0, y0), Size(x1 - x0, h), rr)
                if (dl[j] == Dilim.KERAHAT) clipPath(Path().apply { addRoundRect(RoundRect(x0, y0, x1, y0 + h, rr)) }) {
                    var x = x0 - h
                    while (x < x1) { drawLine(Pal.Warn.copy(alpha = .55f), Offset(x, y0 + h), Offset(x + h, y0), 2.dp.toPx()); x += 6.dp.toPx() }
                }
            }
            val nx = f(now) * w
            drawRoundRect(Pal.Ground.copy(alpha = .55f), Offset(0f, y0), Size(nx, h), rr)   // the part already gone
            val cy = size.height / 2
            if (prayedAt != null) {
                val px = f(prayedAt) * w
                drawCircle(Pal.Gold, 8.dp.toPx(), Offset(px, cy))
                val u = 1.dp.toPx()
                drawPath(Path().apply { moveTo(px - 3.5f * u, cy); lineTo(px - 1f * u, cy + 2.5f * u); lineTo(px + 3.5f * u, cy - 2.5f * u) },
                    Pal.Ground, style = Stroke(1.8f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            if (now < s.end && prayedAt == null) {
                drawCircle(Pal.Gold.copy(alpha = .22f + .25f * beat.value), (11f + 5f * beat.value) * 1.dp.toPx(), Offset(nx, cy))
                drawCircle(Cream, 8.dp.toPx(), Offset(nx, cy))
            }
        }
        val band = faziletBand(s, dl)
        val labels = listOfNotNull(
            Triple(0f, hm(s.start), Pal.Muted),
            band?.let { Triple(f(it.first), hm(it.first), Pal.Gold) }?.takeIf { it.first > .08f },
            band?.let { Triple(f(it.second), hm(it.second), Pal.Gold) }?.takeIf { it.first < .92f },
            Triple(1f, hm(s.end), Pal.Muted),
        )
        Layout({ labels.forEach { Text(it.second, color = it.third, fontSize = Type.micro, fontWeight = if (it.third == Pal.Muted) FontWeight.Normal else FontWeight.SemiBold) } },
            Modifier.fillMaxWidth().padding(top = 2.dp)) { ms, cons ->
            val ps = ms.map { it.measure(Constraints()) }
            layout(cons.maxWidth, ps.maxOf { it.height }) {
                ps.forEachIndexed { i, p -> p.place((labels[i].first * cons.maxWidth - p.width / 2f).toInt().coerceIn(0, cons.maxWidth - p.width), 0) }
            }
        }
        if (prayedAt == null && now < s.end) Text(
            "Şu an: ${DILIM_ADI[nowIn]} (${nowIn + 1}/7)", Modifier.fillMaxWidth().padding(top = Space.xs),
            color = if (dl[nowIn] == Dilim.KERAHAT) Pal.Warn else Pal.Text, fontSize = Type.body2, textAlign = TextAlign.Center,
        )
    }
}
