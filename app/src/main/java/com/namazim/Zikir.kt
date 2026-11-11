package com.namazim

import android.os.VibrationEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectTapGestures
import android.os.VibratorManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin


/** A chip in Zikirhane: the free counter (target null) or one of the user's zikirs. */
private data class Counter(val key: String, val label: String, val target: Int?)

/** Zikir tab: Zikirhane (the counter) and Takip (last five days as a grid). */
@Composable
fun ZikirScreen(onTesbihatDone: () -> Unit = {}) {
    StoreRev.n.let { } // recompose on every count / list change
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    TesbihatNav.key?.let { key ->
        TesbihatMode(store, key, onClose = { TesbihatNav.key = null }) { TesbihatNav.key = null; onTesbihatDone() }
        return
    }
    val all = store.zikirs
    val zikirs = dailyZikirs(all, store.tesbihatCounts)
    var section by rememberSaveable { mutableIntStateOf(SkyDebug.zSection) }
    // a zikir picked in Takip: Zikirhane opens on it
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.statusBarsPadding().padding(top = Space.s).turHedef("zikirsekme")) { PageTabs(listOf("Zikirhane", "Takip"), section) { section = it } }
        if (section == 0) { Zikirhane(store, zikirs, pick); Spacer(Modifier.height(LocalDockPad.current)) }
        else ZikirTakip(store, zikirs) { pick = it; section = 0 }
    }
}

@Composable
private fun ColumnScope.Zikirhane(store: Store, zikirs: List<Zikir>, pick: String?) {
    // read here, not only in ZikirScreen: with the default list (same instance every time) this call is skipped,
    // so a tap saved the count but the screen stayed put until the tab was left (seen on a fresh install, 2026-11-08)
    StoreRev.n.let { }
    val ctx = LocalContext.current
    val vibrator = remember { ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator }
    val scope = rememberCoroutineScope()
    val glow = remember { Animatable(0f) }
    val counts = store.zikirCounts()
    if (zikirs.isEmpty()) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(Space.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("Zikir listen boş", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
            Text("Takip › Kütüphaneden ekle ile bir zikir seç ya da kendin oluştur.", color = Pal.Muted, fontSize = Type.body2,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }
    val items = zikirs.map { Counter(it.name, it.name, it.target) }
    // opens on the zikir picked in Takip, else the first unfinished one (else the first)
    var sel by rememberSaveable(pick) {
        mutableIntStateOf(pick?.let { k -> items.indexOfFirst { it.key == k }.takeIf { it >= 0 } }
            ?: zikirs.indexOfFirst { (counts[it.name] ?: 0) < it.target }.coerceAtLeast(0))
    }
    val bursts = remember { mutableStateListOf<Burst>() }
    var tapAt by remember { mutableStateOf(Offset.Zero) }
    val i = sel.coerceIn(0, items.size - 1)
    val z = items[i]
    val c = counts[z.key] ?: 0
    fun buzz(ms: Long) {
        vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        scope.launch { glow.snapTo(1f); glow.animateTo(0f, tween(900)) }
    }

    // no chips: a zikir is picked in Takip, and reaching a target moves on to the next one
    Spacer(Modifier.height(Space.m))
    // the whole body is the tap target, so counting works without looking
    Box(
        Modifier.weight(1f).fillMaxWidth()
            // the light grows with the progress
            .background(Brush.radialGradient(listOf(Pal.Gold.copy(alpha = .04f + .14f * zikirProgress(c, z.target)), Color.Transparent)))
            .semantics { contentDescription = "${z.label}, $c${z.target?.let { " / $it" } ?: ""}. Saymak için dokun." }
            .pointerInput(Unit) { awaitEachGesture { tapAt = awaitFirstDown(requireUnconsumed = false).position } }
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                bursts += Burst(tapAt, frameClock(), List(6) { (Math.random().toFloat() - .5f) * 160f })
                store.addZikir(z.key, 1)
                val n = c + 1
                when {
                    n == z.target -> {
                        buzz(320)
                        // move on once the glow has been seen; past the target the same chip keeps counting extras
                        scope.launch { delay(450); sel = nextOpenZikir(zikirs, counts + (z.key to n), i) }
                    }
                    else -> vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val own = zikirs.firstOrNull { it.name == z.key }
            NurHead(z.label, own?.let { zikirArabic(it) }, own?.okunus)
            Spacer(Modifier.height(Space.m))
            Box(contentAlignment = Alignment.Center) {
                NurRing(c, z.target, glow.value, Modifier.size(260.dp).turHedef("halka"))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val t = z.target
                    Text("${if (t == null) c else c.coerceAtMost(t)}", style = NumStyle, fontSize = 80.sp, lineHeight = 80.sp, color = Pal.Text)
                    Text(if (t == null) "serbest" else "/ $t", color = Pal.Muted, fontSize = Type.body)
                    when {
                        t != null && c > t -> Text("+${c - t} ekstra", color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
                        t != null && c == t -> Text("tamam", color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(Space.s))
            Text(if (c == 0) "her yere dokun" else " ", color = Pal.Muted.copy(alpha = .6f), fontSize = Type.label)
        }
        NurBursts(bursts, Modifier.matchParentSize())
    }
    val (ayet, kaynak) = ayetOfDay()
    Text("\"$ayet\" $kaynak", Modifier.fillMaxWidth().padding(horizontal = Space.xl), color = Pal.Muted.copy(alpha = .75f),
        fontSize = Type.label, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    Row(
        Modifier.fillMaxWidth().padding(start = Space.xl, end = Space.xl, bottom = Space.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.heightIn(min = TouchMin).clip(CircleShape).clickable(enabled = c > 0) { store.addZikir(z.key, -1) }
                .semantics { contentDescription = "Bir geri al" },
            contentAlignment = Alignment.Center,
        ) {
            Text("−1", Modifier.clip(CircleShape).background(Pal.Track).padding(horizontal = Space.l, vertical = Space.s),
                color = if (c > 0) Pal.Gold else Pal.Muted.copy(alpha = .4f), fontSize = Type.body, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.weight(1f))
        if (zikirs.isNotEmpty()) {
            Text("Bugün ", color = Pal.Muted, fontSize = Type.body2)
            Text("%${(zikirPetal(zikirs, counts).progress * 100).toInt()}", color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Set by the home screen ("Zikirhanede çek"): Zikirhane opens on that vakit's tesbihat. */
object TesbihatNav { var key by mutableStateOf<String?>(null) }

/** Namaz sonrası tesbihat: 33 Sübhânallah → 33 Elhamdülillah → 33 Allâhu ekber, the whole body is the tap target.
 *  At 99 the vakit's tesbihat is marked "app" and the page goes back home. */
@Composable
private fun TesbihatMode(store: Store, key: String, onClose: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val vibrator = remember { ctx.getSystemService(VibratorManager::class.java)?.defaultVibrator }
    val scope = rememberCoroutineScope()
    val glow = remember { Animatable(0f) }
    val vakit = runCatching { Vakit.valueOf(key.substringAfter('|')).ad }.getOrDefault("")
    val demo = SkyDebug.tesbihDemo
    var counts by remember(key) { mutableStateOf(if (demo) DEFAULT_ZIKIRS.map { 0 } else if (store.tesbihat(key) != null) DEFAULT_ZIKIRS.map { it.target } else store.tesbihProgress(key)) }
    val step = tesbihStep(counts)
    val shown = if (step < 0) DEFAULT_ZIKIRS.lastIndex else step
    fun buzz(ms: Long) {
        vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
        scope.launch { glow.snapTo(1f); glow.animateTo(0f, tween(900)) }
    }
    // design V2 "Sağdan sola yazılış" (design/design-system/tesbihat-nur-secenekler.html, 2026-11-11)
    val motes = remember { mutableStateListOf<Mote>() }
    var root by remember { mutableStateOf(Offset.Zero) }
    val hedef = remember { NurHedef() }   // where in the letters the motes land
    // the word on screen stays, full, for a moment before the next one (owner, 2026-11-12: "33'ü görelim, 1-2 sn sonra geçsin")
    var yazilan by remember(key) { mutableIntStateOf(shown) }
    val bekliyor = yazilan != shown && step >= 0
    LaunchedEffect(shown) { if (shown != yazilan) { delay(2600); yazilan = shown } }
    Box(Modifier.fillMaxSize().onGloballyPositioned { root = it.positionInRoot() }) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.xl, end = Space.s, top = Space.l), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("NAMAZ SONRASI", color = Pal.Gold, fontSize = Type.micro, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Text("$vakit tesbihatı", style = NumStyle, fontSize = Type.title, color = Pal.Text)
            }
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Kapat", tint = Pal.Muted) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Space.xl, vertical = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            DEFAULT_ZIKIRS.forEachIndexed { i, z ->
                val done = counts[i] >= z.target
                val on = i == (if (step < 0) -1 else yazilan)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(Radius.s))
                        .background(if (on) Pal.Gold.copy(alpha = .14f) else Color.Transparent)
                        .border(1.dp, if (on) Pal.Gold else Pal.Border, RoundedCornerShape(Radius.s)).padding(vertical = Space.s),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(z.name, color = if (on || done) Pal.Gold else Pal.Muted, fontSize = 11.sp, maxLines = 1)
                    Text(if (done) "✓ ${z.target}" else "${counts[i]}/${z.target}", style = NumStyle, fontSize = 18.sp, color = if (done) Pal.Gold else Pal.Text)
                }
            }
        }
        var alan by remember { mutableStateOf(Offset.Zero) }
        fun say(at: Offset?) {
            if (step < 0 || bekliyor) return
            // six motes from the touch into the letters, onto the points this count lights up
            val from = at?.let { it + alan - root }
            val pts = hedef.noktalar
            val tgt = DEFAULT_ZIKIRS[step].target
            if (from != null && pts.isNotEmpty()) {
                val a = pts.size * counts[step] / tgt; val b = (pts.size * (counts[step] + 1) / tgt).coerceAtMost(pts.size)
                repeat(6) { i -> val j = (a + (b - a) * (2 * i + 1) / 12).coerceIn(0, pts.lastIndex)
                    motes += Mote(from, pts[j] - root, frameClock() + i * 25L, 650 + (0..350).random()) }
            }
            val n = counts.toMutableList().also { it[step]++ }
                    counts = n
                    if (!demo) store.setTesbihProgress(key, n)
                    StoreRev.n++   // the progress counts toward today's zikir on read
                    if (n[step] == DEFAULT_ZIKIRS[step].target) {
                        buzz(320)
                        if (tesbihStep(n) < 0 && !demo) {
                            store.setTesbihat(key, "app")
                            scope.launch { delay(2600); onDone() }
                        }
                    } else vibrator?.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
        }
        var alanBoy by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
        if (demo) LaunchedEffect(Unit) {
            delay(1500)
            while (true) { delay(420); if (alanBoy.width > 0) say(Offset(alanBoy.width * (.3f + .4f * Math.random().toFloat()), alanBoy.height * .85f)) }
        }
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .onGloballyPositioned { alan = it.positionInRoot(); alanBoy = it.size }
                .semantics { contentDescription = "$vakit tesbihatı, ${DEFAULT_ZIKIRS[shown].name} ${counts[shown]}. Saymak için dokun." }
                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = step >= 0) { say(null) }
                .pointerInput(step) { detectTapGestures { if (step >= 0) say(it) } },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val z = DEFAULT_ZIKIRS[yazilan]
                val ar = zikirArabic(z.name)
                NurYazi(ar?.first ?: z.name, counts[yazilan].toFloat() / z.target, glow.value, hedef)
                Text(z.name, Modifier.padding(top = Space.s), style = NumStyle, fontSize = Type.h2, color = Pal.Text)
                if (ar != null) Text(cumle(ar.second), color = Pal.Muted, fontSize = Type.label)
                Spacer(Modifier.height(Space.xl))
                Text(if (step < 0) "99" else "${counts[yazilan]}", style = NumStyle, fontSize = 84.sp, lineHeight = 84.sp, color = Pal.Text)
                Text(if (step < 0) "Allah kabul etsin" else "/ ${DEFAULT_ZIKIRS[yazilan].target}", color = Pal.Muted, fontSize = Type.body)
                Spacer(Modifier.height(Space.l))
                Text(if (step < 0) "ana sayfaya dönülüyor" else "her yere dokun", color = Pal.Muted.copy(alpha = .6f), fontSize = Type.label)
            }
        }
        Spacer(Modifier.height(LocalDockPad.current))
    }
    NurMotes(motes, Modifier.fillMaxSize())
    }
}

private val CELL = 42.dp

/** Takip: rows are the zikirs, columns the last five prayer days, today first. */
@Composable
private fun ZikirTakip(store: Store, zikirs: List<Zikir>, onOpen: (String) -> Unit) {
    StoreRev.n.let { } // same reason as in Zikirhane
    var library by remember { mutableStateOf(false) }
    var create by remember { mutableStateOf(false) }
    if (library) KutuphaneSheet(store, onClose = { library = false }) { library = false; create = true }
    if (create) ZikirSheet(store, store.zikirs) { create = false }
    val tr = Locale("tr")
    val days = (0L until 5L).map { store.prayerDay().minusDays(it) }
    val logs = days.map { store.zikirCounts(it) }
    val rows = zikirs.map { Counter(it.name, it.name, it.target) }
    // drag to reorder: hold a row, move it, let go (owner, 2026-11-11)
    var surukle by remember { mutableStateOf<Int?>(null) }
    var dy by remember { mutableStateOf(0f) }
    var satirH by remember { mutableStateOf(1f) }
    val hedef = surukle?.let { (it + kotlin.math.round(dy / satirH).toInt()).coerceIn(0, rows.lastIndex) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState(), enabled = surukle == null).padding(bottom = LocalDockPad.current).padding(horizontal = Space.l, vertical = Space.m)) {
        Row(Modifier.fillMaxWidth().padding(start = Space.l, end = Space.l, bottom = Space.s), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            days.forEachIndexed { k, d ->
                Column(Modifier.width(CELL), horizontalAlignment = Alignment.CenterHorizontally) {
                    val col = if (k == 0) Pal.Gold else Pal.Muted
                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, tr).uppercase(tr), color = col, fontSize = Type.micro, fontWeight = FontWeight.SemiBold)
                    Text("${d.dayOfMonth}", style = NumStyle, color = col, fontSize = Type.body)
                }
            }
        }
        AppCard(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { r, z ->
                // while a row is dragged, the ones it passes step aside by one row
                val kay = when {
                    surukle == null || hedef == null || r == surukle -> 0f
                    r in (surukle!! + 1)..hedef -> -satirH
                    r in hedef until surukle!! -> satirH
                    else -> 0f
                }
                Column(
                    Modifier.zIndex(if (r == surukle) 1f else 0f)
                        .onSizeChanged { if (r == 0) satirH = it.height.toFloat().coerceAtLeast(1f) }
                        .graphicsLayer { translationY = if (r == surukle) dy else kay }
                        .clip(RoundedCornerShape(Radius.s))
                        .background(if (r == surukle) Pal.Track else Color.Transparent)
                        .pointerInput(z.key, rows.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { surukle = r; dy = 0f; haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress) },
                                onDrag = { ch, d -> ch.consume(); dy += d.y },
                                onDragEnd = {
                                    val from = surukle; val to = hedef
                                    if (from != null && to != null && from != to) store.zikirs = sirala(store.zikirs, rows.map { it.key }, from, to)
                                    surukle = null; dy = 0f
                                },
                                onDragCancel = { surukle = null; dy = 0f },
                            )
                        },
                ) {
                if (r > 0) HorizontalDivider(Modifier.padding(vertical = Space.xs), color = Pal.Line)
                // every row the same: one line of name (a long one ends in "…", Zikirhane shows it whole), the target under it
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(Radius.s)).clickable { onOpen(z.key) }.padding(vertical = Space.xs)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(z.label, Modifier.weight(1f, fill = false), color = Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Zikirhane'de aç", tint = Pal.Gold, modifier = Modifier.size(16.dp))
                        }
                        Text(z.target?.let { "hedef $it" } ?: "", color = Pal.Muted.copy(alpha = .6f), fontSize = Type.micro)
                    }
                    logs.forEach { log -> TakipCell(zikirCell(log[z.key] ?: 0, z.target)) }
                }
                }
            }
        }
        // design K1 (design/design-system/kutuphane-secenekler.html): the built-in library, by section, with search
        Box(
            Modifier.padding(top = Space.m).fillMaxWidth().clip(RoundedCornerShape(Radius.m))
                .border(1.dp, Pal.Gold.copy(alpha = .55f), RoundedCornerShape(Radius.m)).clickable { library = true }
                .padding(vertical = Space.m),
            contentAlignment = Alignment.Center,
        ) { Text("＋  Kütüphaneden ekle", color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.height(Space.m))
        Text("Zikir adına dokununca Zikirhane o zikirle açılır. Sırasını değiştirmek için bir satıra basılı tut ve sürükle. Zikirhane'de saydıkça bugünün sütunu kendiliğinden dolar. Hedef sonrası ekstralar hedefe sayılmaz.",
            Modifier.padding(horizontal = Space.s), color = Pal.Muted.copy(alpha = .7f), fontSize = Type.label, lineHeight = 17.sp)
    }
}

@Composable
private fun TakipCell(cell: ZikirCell) {
    Column(Modifier.width(CELL), horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            cell.done -> {
                Icon(Icons.Default.Check, "tamam", tint = Pal.Gold, modifier = Modifier.size(20.dp))
                if (cell.text.isNotEmpty()) Text(cell.text, color = Pal.Gold, fontSize = Type.micro)
            }
            cell.text.isNotEmpty() -> Text(cell.text, style = NumStyle, color = Pal.Text, fontSize = Type.body)
            else -> Text("×", color = Pal.Muted.copy(alpha = .3f), fontSize = Type.body)
        }
    }
}

/** The built-in library: sections, search, and "Ekle" (the hadith's count becomes the target, else 33). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KutuphaneSheet(store: Store, onClose: () -> Unit, onCreate: () -> Unit) {
    var tur by remember { mutableStateOf("Tümü") }
    var q by remember { mutableStateOf("") }
    val mine = store.zikirs
    val list = remember(tur, q) { kutuphane(tur, q) }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.xl).fillMaxHeight(.92f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kütüphane", Modifier.weight(1f), style = NumStyle, fontSize = Type.h2, color = Pal.Text)
                // your own zikir (with Arabic, reading and meaning), and your list to edit
                Row(Modifier.clip(CircleShape).border(1.dp, Pal.Gold.copy(alpha = .6f), CircleShape).clickable(onClick = onCreate)
                    .padding(horizontal = Space.m, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Edit, null, tint = Pal.Gold, modifier = Modifier.size(16.dp))
                    Text(" Oluştur", color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
                }
            }
            Row(Modifier.padding(top = Space.s).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ZIKIR_TURLERI.forEach { t ->
                    val on = t == tur
                    Text(t, Modifier.clip(CircleShape).border(1.dp, if (on) Pal.Gold else Pal.Border, CircleShape).clickable { tur = t }
                        .padding(horizontal = Space.m, vertical = 6.dp), color = if (on) Pal.Gold else Pal.Muted, fontSize = Type.label)
                }
            }
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(top = Space.s), singleLine = true,
                label = { Text("Ara: zikir, esmâ, dua…") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Border,
                    focusedLabelColor = Pal.Gold, cursorColor = Pal.Gold, focusedTextColor = Pal.Text, unfocusedTextColor = Pal.Text))
            LazyColumn(Modifier.padding(top = Space.s).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(Space.s)) {
                items(list, key = { it.name }) { h ->
                    val added = mine.any { it.name.equals(h.name, ignoreCase = true) }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.s)).background(Pal.Ground).padding(Space.m)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(h.name, Modifier.weight(1f), color = Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.Medium)
                            Text(if (added) "✓ Listede" else "Ekle",
                                Modifier.clip(CircleShape).then(if (added) Modifier.border(1.dp, Pal.Gold, CircleShape) else Modifier.background(Pal.Gold))
                                    .clickable(enabled = !added) { store.zikirs = store.zikirs + Zikir(h.name, h.adet ?: 33, h.ar, h.mean) }
                                    .padding(horizontal = Space.m, vertical = 5.dp),
                                color = if (added) Pal.Gold else Pal.Ground, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
                        }
                        Text(h.ar, Modifier.fillMaxWidth().padding(top = Space.xs), color = Color(0xFFF2D79B), fontSize = 19.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End, maxLines = 2)
                        Text(listOfNotNull(h.adet?.let { "$it kere" }, h.mean.removeSuffix("."), h.kaynak).joinToString(" · "),
                            Modifier.padding(top = 2.dp), color = Pal.Muted, fontSize = Type.micro, lineHeight = 15.sp)
                    }
                }
                item { Spacer(Modifier.height(Space.xl)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ZikirSheet(store: Store, zikirs: List<Zikir>, onClose: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("33") }
    // Arabic: picked from the built-in list (design C) into the two fields, or typed there by hand
    var ar by remember { mutableStateOf("") }
    var mean by remember { mutableStateOf("") }
    var okunus by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf<String?>(null) }
    val n = name.trim()
    val t = target.trim().toIntOrNull()
    val error = when {
        n.isNotEmpty() && zikirs.any { it.name.equals(n, ignoreCase = true) } -> "Bu zikir listede var"
        n.startsWith("#") -> "Ad # ile başlayamaz"
        target.isNotBlank() && (t == null || t !in 1..10_000) -> "Hedef 1 ile 10.000 arası olmalı"
        else -> null
    }
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Border, focusedLabelColor = Pal.Gold,
        cursorColor = Pal.Gold, focusedTextColor = Pal.Text, unfocusedTextColor = Pal.Text,
    )
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.xl).navigationBarsPadding().padding(bottom = Space.l)) {
            Text("Zikirlerim", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
            Text("Her gün imsakta sıfırlanır", color = Pal.Muted, fontSize = Type.body2)
            Spacer(Modifier.height(Space.s))
            zikirs.forEachIndexed { idx, z ->
                if (idx > 0) HorizontalDivider(color = Pal.Line)
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(z.name, Modifier.weight(1f), color = Pal.Text, fontSize = Type.body)
                    zikirArabic(z)?.let { Text(it.first, Modifier.padding(end = Space.m), color = Color(0xFFF2D79B), fontSize = 18.sp, maxLines = 1) }
                    Text("${z.target}", color = Pal.Muted, fontSize = Type.body2)
                    IconButton(onClick = { store.zikirs = zikirs - z }) { Icon(Icons.Default.Delete, "${z.name} sil", tint = Pal.Muted, modifier = Modifier.size(20.dp)) }
                }
            }
            Spacer(Modifier.height(Space.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("Zikir adı") }, singleLine = true, colors = fieldColors)
                OutlinedTextField(target, { target = it.filter(Char::isDigit).take(5) }, Modifier.width(96.dp), label = { Text("Hedef") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), colors = fieldColors)
            }
            if (error != null) Text(error, Modifier.padding(top = Space.xs), color = Pal.Warn, fontSize = Type.label)
            if (picked != n) suggestZikir(n).forEach { hz ->
                val (sn, sa, sm) = hz
                Row(
                    Modifier.padding(top = Space.s).fillMaxWidth().clip(RoundedCornerShape(Radius.s))
                        .border(1.dp, Pal.Gold.copy(alpha = .45f), RoundedCornerShape(Radius.s))
                        .clickable { name = sn; ar = sa; mean = sm; picked = sn; hz.adet?.let { target = "$it" } }.padding(horizontal = Space.m, vertical = Space.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(sn, color = Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.Medium)
                        Text(listOfNotNull(hz.adet?.let { "$it kere" }, hz.kaynak).joinToString(" · ").ifEmpty { sm },
                            color = Pal.Muted, fontSize = Type.micro, maxLines = 1)
                    }
                    Text(sa, color = Color(0xFFF2D79B), fontSize = 20.sp, maxLines = 1)
                }
            }
            // always open: a pick from the list fills them, otherwise they are typed (or left empty)
            run {
                OutlinedTextField(ar, { ar = it }, Modifier.fillMaxWidth().padding(top = Space.s), label = { Text("Arapça yazı (isteğe bağlı)") },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 20.sp, textDirection = androidx.compose.ui.text.style.TextDirection.Rtl),
                    singleLine = true, colors = fieldColors)
                OutlinedTextField(okunus, { okunus = it }, Modifier.fillMaxWidth().padding(top = Space.s), label = { Text("Okunuşu (isteğe bağlı)") },
                    singleLine = true, colors = fieldColors)
                OutlinedTextField(mean, { mean = it }, Modifier.fillMaxWidth().padding(top = Space.s), label = { Text("Anlamı (isteğe bağlı)") },
                    singleLine = true, colors = fieldColors)
            }
            Spacer(Modifier.height(Space.l))
            val ok = n.isNotEmpty() && t != null && error == null
            GoldButton("Zikir ekle", Modifier.fillMaxWidth().alpha(if (ok) 1f else .4f), Icons.Default.Add) {
                if (ok) {
                    store.zikirs = zikirs + Zikir(n, t!!, ar.trim().ifEmpty { null }, mean.trim().ifEmpty { null }, okunus.trim().ifEmpty { null })
                    name = ""; target = "33"; ar = ""; mean = ""; okunus = ""; picked = null
                }
            }
        }
    }
}

/** The whole list after moving the visible row [from] to [to]; [shown] = the names on screen in order. Zikirs not on
 *  screen (the default 33s while tesbihat counting is off) keep their places. */
fun sirala(all: List<Zikir>, shown: List<String>, from: Int, to: Int): List<Zikir> {
    val moved = all.firstOrNull { it.name == shown[from] } ?: return all
    val order = shown.toMutableList().apply { add(to, removeAt(from)) }
    val rest = all.filter { it.name != moved.name }.toMutableList()
    // put it before the row that now follows it, or after the one that now comes before it
    val after = order.getOrNull(to + 1)?.let { n -> rest.indexOfFirst { it.name == n } } ?: -1
    val before = order.getOrNull(to - 1)?.let { n -> rest.indexOfFirst { it.name == n } } ?: -1
    rest.add(if (after >= 0) after else if (before >= 0) before + 1 else rest.size, moved)
    return rest
}
