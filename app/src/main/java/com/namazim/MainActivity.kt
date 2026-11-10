package com.namazim

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_SLOT = "slot" }

    private var reminderKey by mutableStateOf<String?>(null)
    private var startTab by mutableIntStateOf(0)
    private var navN by mutableIntStateOf(0)   // bumps on every tab request, so asking for the same tab again still moves there

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.createChannels(this)
    // Uygulama açılır açılmaz kaçan vakitleri kazaya yaz (Kaza sekmesini açmayı bekleme)
    Thread {
        runCatching { Store(this).settleKaza() }
        runCatching { Store(this).migrateTesbihatLog() }
        runCatching { Reminders.scheduleNext(this) }
    }.apply { isDaemon = true }.start()
        reminderKey = intent.getStringExtra(EXTRA_SLOT)
        startTab = intent.getIntExtra("tab", 0) // lets `adb shell am start -e tab N` open a tab for testing
        SkyDebug.offsetMs = intent.getLongExtra("skytime", 0L) // `--el skytime` previews another hour of the sky
        if (intent.hasExtra("kible")) SkyDebug.kible = intent.getFloatExtra("kible", 0f) // `--ef kible 160` fakes the compass
        DefterYer.yukle(Store(this))
        acKisayol(intent)
        SkyDebug.acik = intent.getStringExtra("acik")
        SkyDebug.asagi = intent.getBooleanExtra("asagi", false) // `--ez asagi true`: İstikrar opens scrolled to the bottom
        SkyDebug.zSection = intent.getIntExtra("zsection", 0) // `--ei zsection 1` opens Zikir > Takip
        // `--ez resetentries true` (debug builds): Ayarlar › Kayıtları sıfırla without tapping; tracking restarts at the next imsak
        if (intent.getBooleanExtra("resetentries", false) && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val s = Store(this)
            android.util.Log.i("Reset", "entries reset: ${s.resetEntries(s.nextImsak())}")
            Reminders.scheduleNext(this)
        }
        // `--es tesbihat "2026-11-05|SABAH" --ei tab 5` (debug builds): Zikirhane opens on that vakit's tesbihat
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) intent.getStringExtra("tesbihat")?.let { TesbihatNav.key = it }
        // `--ez testnotif true` (debug builds): the same sample "Kıldın mı?" as Ayarlar › Deneme bildirimi
        if (intent.getBooleanExtra("testnotif", false) && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) Reminders.test(this)
        // `--ez backuptest true` (debug builds): export → parse → compare with the live prefs, log only, writes nothing
        if (intent.getBooleanExtra("backuptest", false) && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val live = getSharedPreferences("namazim", MODE_PRIVATE).all
            val parsed = Store(this).let { it.readBackup(it.exportBackup()).values }
            val bad = live.keys.filter { k -> live[k] != parsed[k] }
            android.util.Log.i("Backup", "round trip: ${live.size} keys, ${parsed.size} parsed, mismatched=$bad")
        }
        // `--ei kurulum N` (debug builds): the setup screens from step N without writing tracking start or kaza; `--ei tur N`: the tour from step N
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            SkyDebug.kurulum = intent.getIntExtra("kurulum", -1)
            intent.getIntExtra("tur", -1).takeIf { it >= 0 }?.let { Tur.adim = it }
        }
        // `--es ikon imsak|ogle|ikindi|zumrut|yatsi|cuma` (debug builds): force the launcher icon; it switches when the app goes to the background
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0)
            intent.getStringExtra("ikon")?.let { SkyDebug.ikon = it.takeIf { k -> k in listOf("imsak", "ogle", "ikindi", "zumrut", "yatsi", "cuma") } }
        if (intent.getBooleanExtra("keepawake", false)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (intent.getBooleanExtra("widgetpreview", false)) {
            setContent { AppTheme { WidgetPreview() } }
            return
        }
        setContent { AppTheme { App(reminderKey, startTab, navN) { reminderKey = null } } }
    }

    /**
     * Launcher shortcuts (res/xml/shortcuts.xml: namazim://ac/pusula|zikirhane|ozet|tumu) and the debug extras
     * `--ei msection 0..2` / `--ei gperiod 0..3`, which open Defter on a section / İstikrar period.
     */
    private fun acKisayol(intent: Intent) {
        val store = Store(this)
        if (intent.hasExtra("msection") || intent.hasExtra("gperiod"))
            DefterYer.git(store, intent.getIntExtra("msection", DefterYer.section), intent.getIntExtra("gperiod", -1).takeIf { it >= 0 })
        if (intent.data?.scheme != "namazim") return
        navN++
        when (intent.data?.lastPathSegment) {
            "pusula" -> startTab = 1
            "zikirhane" -> { SkyDebug.zSection = 0; startTab = 5 }
            "ozet" -> { DefterYer.git(store, 0); startTab = 3 }
            "tumu" -> { DefterYer.git(store, 1, 3); startTab = 3 }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        acKisayol(intent)
        intent.getStringExtra(EXTRA_SLOT)?.let { reminderKey = it }
        if (intent.hasExtra("tab")) { startTab = intent.getIntExtra("tab", startTab); navN++ }
    }

    override fun onResume() {
        super.onResume()
        Reminders.scheduleNext(this)
    }

    // the Cuma icon turns while the app is out of sight: some launchers redraw the icon when it changes
    override fun onStop() {
        super.onStop()
        Reminders.ikonuAyarla(this)
    }
}

/** One line under the date that shows the next mübarek günler in turn, a few seconds each, sliding up. */
@Composable
fun HolyTicker(days: List<Pair<HolyDay, String>>) {
    if (days.isEmpty()) return
    var k by remember(days) { mutableIntStateOf(0) }
    LaunchedEffect(days) { while (days.size > 1) { delay(3500); k = (k + 1) % days.size } }
    AnimatedContent(
        k, contentAlignment = Alignment.CenterEnd,
        transitionSpec = {
            (fadeIn(tween(450)) + androidx.compose.animation.slideInVertically(tween(450)) { it / 2 }) togetherWith
                (fadeOut(tween(300)) + androidx.compose.animation.slideOutVertically(tween(300)) { -it / 2 })
        },
        label = "holy",
    ) { i ->
        val (d, left) = days[i % days.size]
        Column(horizontalAlignment = Alignment.End) {
            Text("✦ ${d.name}", color = Pal.Muted, fontSize = Type.label, maxLines = 1)
            // a little room on the right and below: Cormorant's "n" overhangs its box and was cut
            Text(left, Modifier.padding(end = 2.dp, bottom = 1.dp), style = NumStyle, fontSize = 18.sp, lineHeight = 20.sp, color = Pal.Gold)
        }
    }
}

/** Bottom space a page leaves so its last row can scroll clear of the floating dock. */
val LocalDockPad = compositionLocalOf { 0.dp }

@Composable
fun App(reminderKey: String?, startTab: Int, navN: Int = 0, onReminderClosed: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    // first run: the setup screens instead of the app (Kurulum.kt), then the tour once
    var kurulum by remember { mutableStateOf(SkyDebug.kurulum >= 0 || store.needsKurulum()) }
    if (kurulum) {
        KurulumScreen(store, if (SkyDebug.kurulum >= 0) SkyDebug.kurulum else store.kurulumStep, deneme = SkyDebug.kurulum >= 0) { tanit ->
            if (SkyDebug.kurulum < 0) store.kurulumBitti()
            SkyDebug.kurulum = -1
            if (tanit) Tur.adim = 0 else store.turDone = true
            kurulum = false
        }
        return
    }
    LaunchedEffect(Unit) { if (!store.turDone && Tur.adim < 0 && reminderKey == null) Tur.adim = 0 }
    var tab by remember { mutableIntStateOf(startTab) }
    // `am start --ei tab N` on a running instance moves to that tab (useful for screenshots)
    LaunchedEffect(startTab, navN) { tab = startTab }
    // Kıble (1) ve Ayarlar (4) sekme değil, ana sayfanın başlığından açılır: dock gizlenir, geri ana sayfaya döner
    val subPage = tab == 1 || tab == 4
    BackHandler(subPage) { tab = 0 }
    Box(Modifier.fillMaxSize().background(Pal.Ground)) {
        // floating dock: pages run under it (they end with LocalDockPad of space), only the island covers them
        val dockPad = if (subPage) 0.dp else 62.dp + Space.s * 2 + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        CompositionLocalProvider(LocalDockPad provides dockPad) {
            // sekmeler arası yumuşak geçiş: hafif kayma + solma
            AnimatedContent(
                targetState = tab,
                modifier = Modifier.fillMaxSize(),
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)) +
                        androidx.compose.animation.slideInVertically { it / 28 }) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
                },
                label = "tab",
            ) { t ->
                when (t) {
                    0 -> NamazScreen(onOpenKible = { tab = 1 }, onOpenSettings = { tab = 4 }) { TesbihatNav.key = it; tab = 5 }
                    1 -> KibleScreen { tab = 0 }
                    2 -> OkumaScreen()
                    3 -> MuhasebeScreen()
                    5 -> ZikirScreen { tab = 0 }
                    else -> AyarlarScreen { tab = 0 }
                }
            }
        }
        if (!subPage) Box(Modifier.align(Alignment.BottomCenter).turHedef("dock")) { SeherNav(tab) { tab = it } }
        if (Tur.adim >= 0) TurOverlay(onTab = { tab = it }) { Tur.adim = -1; store.turDone = true; tab = 0 }
        if (reminderKey != null) ReminderScreen(reminderKey, onReminderClosed)
    }
}

@Composable
fun SeherNav(selected: Int, onSelect: (Int) -> Unit) {
    // (tab, name, icon): Kıble (1) and Ayarlar (4) open from the home header, not from the dock
    val tabs = listOf(
        Triple(0, "Namaz", R.drawable.ic_nav_namaz), Triple(5, "Zikir", R.drawable.ic_nav_zikir),
        Triple(2, "Okuma", R.drawable.ic_nav_okuma), Triple(3, "Defter", R.drawable.ic_nav_muhasebe),
    )
    val haptic = LocalHapticFeedback.current
    // floating dock instead of a full-width bar: the photo keeps running underneath it
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = Space.xl, vertical = Space.s)) {
        Row(
            Modifier.fillMaxWidth().height(62.dp)
                .shadow(16.dp, RoundedCornerShape(31.dp))
                .clip(RoundedCornerShape(31.dp))
                .background(Pal.Nav.copy(alpha = .74f))
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = .07f), Color.White.copy(alpha = .015f), Color.Transparent),
                    ),
                )
                .border(1.dp, Color.White.copy(alpha = .10f), RoundedCornerShape(31.dp))
                .padding(horizontal = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // seçili sekme genişleyip adını gösterir; diğerleri yalnızca ikon
            tabs.forEach { (i, name, res) ->
                val on = selected == i
                Row(
                    Modifier.weight(if (on) 1.9f else 1f).height(46.dp)
                        .padding(horizontal = 2.dp).clip(CircleShape)
                        .background(if (on) Pal.Track else Color.Transparent)
                        .pressable {
                            if (!on) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(i)
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painterResource(res), name, tint = if (on) Pal.Gold else Pal.Muted,
                        modifier = Modifier.size(if (on) 21.dp else 22.dp),
                    )
                    AnimatedVisibility(on, enter = fadeIn(tween(180)) + expandHorizontally(tween(180)), exit = fadeOut(tween(120))) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(7.dp))
                            Text(name, color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LocationChip(city: String, locating: Boolean, onRefresh: () -> Unit) {
    val ctx = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val shadow = TextStyle(shadow = Shadow(Color(0x99000000), blurRadius = 12f))
    Box {
        Row(
            Modifier.clip(CircleShape).clickable(enabled = !locating) { open = true }.padding(horizontal = Space.s, vertical = Space.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Place, null, tint = Pal.Gold, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(Space.xs))
            Text(
                if (locating) "Konum alınıyor…" else city.ifEmpty { "Konumum" },
                color = Pal.Text, fontSize = Type.label, fontWeight = FontWeight.Medium, style = shadow, maxLines = 1,
            )
            Spacer(Modifier.width(1.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Pal.Text.copy(alpha = .55f), modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Pal.Card, shape = RoundedCornerShape(16.dp)) {
            Text(
                if (city.isEmpty()) "Konum belirlenmedi" else city,
                Modifier.padding(start = Space.l, end = Space.l, top = Space.xs, bottom = Space.s), color = Pal.Muted, fontSize = Type.label,
            )
            HorizontalDivider(color = Pal.Line)
            DropdownMenuItem(
                text = { Text("Konumu güncelle", color = Pal.Text, fontSize = Type.body) },
                leadingIcon = { Icon(Icons.Default.Refresh, null, tint = Pal.Gold, modifier = Modifier.size(18.dp)) },
                onClick = { open = false; onRefresh() },
            )
            DropdownMenuItem(
                text = { Text("Uygulama izinleri", color = Pal.Text, fontSize = Type.body) },
                leadingIcon = { Icon(Icons.Default.Settings, null, tint = Pal.Muted, modifier = Modifier.size(18.dp)) },
                onClick = {
                    open = false
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                },
            )
        }
    }
}

/** Round translucent button on the sky header (Kıble, gear). */
@Composable
fun HeaderButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0x26000000)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** Thin-line gear: eight teeth on a ring, more elegant than the filled Material icon. */
@Composable
fun GearButton(onClick: () -> Unit) {
    HeaderButton(onClick) {
        androidx.compose.foundation.Canvas(Modifier.size(18.dp)) {
            val c = center
            val r = size.minDimension * 0.29f
            val w = size.minDimension * 0.085f
            drawCircle(Pal.Text, r, c, style = androidx.compose.ui.graphics.drawscope.Stroke(w))
            for (i in 0 until 8) {
                val a = (i * 45f) * (PI / 180).toFloat()
                val p0 = Offset(c.x + cos(a) * r * 1.12f, c.y + sin(a) * r * 1.12f)
                val p1 = Offset(c.x + cos(a) * r * 1.55f, c.y + sin(a) * r * 1.55f)
                drawLine(Pal.Text, p0, p1, strokeWidth = w, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            }
            drawCircle(Pal.Text, r * 0.28f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.9f))
        }
    }
}

/** Debug-only: the widget art at 144 dp, so the live icon can be checked without adding it to the launcher. */
@Composable
fun WidgetPreview() {
    val ctx = LocalContext.current
    val bmp = remember { NamazimWidget.art(ctx, 288).asImageBitmap() }
    Column(
        Modifier.fillMaxSize().background(Pal.Ground), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(bmp, "Canlı ikon", Modifier.size(144.dp))
        // the whole family: İmsak, Öğle, İkindi, Akşam, Yatsı, Cuma (the 1×1 shows the one of the moment)
        Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            listOf(R.drawable.wg_vakit_imsak, R.drawable.wg_vakit_ogle, R.drawable.wg_vakit_ikindi, R.drawable.wg_vakit_zumrut,
                R.drawable.wg_vakit_yatsi, R.drawable.wg_vakit_cuma).forEach { res ->
                val b = remember(res) {
                    android.graphics.Bitmap.createBitmap(160, 160, android.graphics.Bitmap.Config.ARGB_8888).also { bm ->
                        ctx.getDrawable(res)?.apply { setBounds(0, 0, 160, 160); draw(android.graphics.Canvas(bm)) }
                    }.asImageBitmap()
                }
                Image(b, null, Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)))
            }
        }
        Spacer(Modifier.height(Space.s))
        Text(NamazimWidget.label(ctx), color = Pal.Text, fontSize = Type.micro, fontWeight = FontWeight.Medium)
        // the three big widgets as the launcher would draw them
        VakitWidgets.Tur.entries.forEach { tur ->
            Spacer(Modifier.height(Space.l))
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { c -> android.widget.FrameLayout(c).apply { addView(VakitWidgets.views(c, tur).apply(c, this)) } },
                modifier = Modifier.padding(horizontal = Space.l).fillMaxWidth().height(if (tur == VakitWidgets.Tur.SERIT) 84.dp else 176.dp),
            )
        }
    }
}

/** Heartbeat strength 0..1: a "lub-dub" double beat, then rest; one cycle ≈ 1.2 s (about 50 bpm, calm). */
@Composable
internal fun heartbeat(): androidx.compose.runtime.State<Float> =
    androidx.compose.animation.core.rememberInfiniteTransition(label = "beat").animateFloat(
        0f, 0f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.keyframes {
                durationMillis = 1200
                0f at 0
                1f at 120 using androidx.compose.animation.core.FastOutSlowInEasing
                .2f at 260
                .7f at 380 using androidx.compose.animation.core.FastOutSlowInEasing
                0f at 640
            },
        ),
        label = "beat",
    )

// ---------------------------------------------------------------- helpers shared by the screens

private val zone get() = ZoneId.systemDefault()
fun hm(ms: Long): String = Instant.ofEpochMilli(ms).atZone(zone).format(DateTimeFormatter.ofPattern("HH:mm"))
fun countdown(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60)
}

/** Ticks once a second so countdowns and statuses stay current. */
@Composable
fun rememberNow(): Long {
    // `--el skytime <ms>` (SkyDebug.offsetMs) moves this clock: the header clip and home preview another hour or day
    var now by remember { mutableLongStateOf(System.currentTimeMillis() + SkyDebug.offsetMs) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() + SkyDebug.offsetMs } }
    return now
}

@Composable
fun ScreenTitle(title: String, sub: String? = null, rule: Boolean = true, trailing: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.xl, end = Space.xl, top = Space.l)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text(title, style = NumStyle, fontSize = Type.display, lineHeight = 46.sp, letterSpacing = (-0.5).sp, color = Pal.Text)
                if (sub != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(sub, fontSize = Type.body2, letterSpacing = .3.sp, color = Pal.Muted)
                }
            }
            trailing()
        }
        // ince altın çizgi: başlığı gövdeden ayırır (sekmeli sayfalarda sekmelerin taban çizgisi bu işi görür)
        Spacer(Modifier.height(Space.m))
        if (rule) Box(
            Modifier.fillMaxWidth().height(1.dp).background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(Pal.Gold.copy(alpha = .45f), Pal.Gold.copy(alpha = .12f), Color.Transparent),
                ),
            ),
        )
    }
}

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 52.dp), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Pal.Gold, contentColor = Pal.Ground),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(Space.s)) }
        Text(text, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = Pal.Text, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (color == Pal.Text) Pal.Border else color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) { Text(text, fontSize = Type.body) }
}

private val TR_DATE = DateTimeFormatter.ofPattern("d MMMM EEEE", Locale("tr"))

// ---------------------------------------------------------------- Namaz (home)

@Composable
fun NamazScreen(onOpenKible: () -> Unit, onOpenSettings: () -> Unit, onTesbihat: (String) -> Unit = {}) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val now = rememberNow()
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }
    // `--es acik takvim|mubarek` (debug) opens one of them at start, for screenshots
    val acik = remember { SkyDebug.acik.also { SkyDebug.acik = null } }   // used once: coming back to home must not reopen it
    var takvim by remember { mutableStateOf<LocalDate?>(if (acik == "takvim") LocalDate.now() else null) }   // the calendar, opened on this day
    var mubarek by remember { mutableStateOf(acik == "mubarek") }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            locating = true
            scope.launch {
                fetchLocation(ctx, store)
                locating = false
                Reminders.scheduleNext(ctx)
                refresh++
            }
        }
    }
    val locate = { askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }

    key(refresh) {
        if (!store.hasLocation) {
            Column(Modifier.fillMaxSize().padding(Space.xxl), verticalArrangement = Arrangement.Center) {
                Text("Hoş geldin", style = NumStyle, fontSize = Type.display, color = Pal.Text)
                Spacer(Modifier.height(Space.s))
                Text("Vakitleri hesaplamak için konumuna ihtiyacım var. Konum sadece telefonda kalır.", color = Pal.Muted, fontSize = Type.body)
                Spacer(Modifier.height(Space.xl))
                GoldButton(if (locating) "Konum alınıyor…" else "Konumumu bul", Modifier.fillMaxWidth(), Icons.Default.Place) { if (!locating) locate() }
            }
        } else {

        // "Namaz günü" imsakla başlar: gece yarısından sonra imsaka kadar dünün yatsısı hâlâ kılınabilir.
        val calDate = java.time.Instant.ofEpochMilli(now).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val imsakToday = slots(store.lat, store.lng, calDate).first().start
        val today = if (now < imsakToday) calDate.minusDays(1) else calDate
        val d = dayTimes(store.lat, store.lng, today)
        val tomorrow = dayTimes(store.lat, store.lng, today.plusDays(1))
        val events = listOf(d.imsak to "İmsak", d.gunes to "Güneş doğuşuna", d.ogle to slots(store.lat, store.lng, today)[1].ad(store.kadin), d.ikindi to "İkindi",
            d.aksam to "Akşam", d.yatsi to "Yatsı", tomorrow.imsak to "İmsak")
        val next = events.first { it.first > now }
        val ex = extras(d, tomorrow.imsak)
        val holy = remember(today) { nextHolyDays(today) }

        Column(Modifier.fillMaxSize()) {
        // the Harem clips are the showcase: a 240dp header, all of it moving picture
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            HaremSky(store.lat, store.lng, Modifier.fillMaxSize())
            // the clips keep their subject low in the frame: only a short fade, nothing drawn over it
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
                .background(Brush.verticalGradient(
                    0f to Color.Transparent, 0.45f to Pal.Ground.copy(alpha = .72f), 1f to Pal.Ground,
                )))
            // Cuma: a gold line over the bottom of the picture, all day (design C, design/design-system/cuma-secenekler.html)
            if (calDate.dayOfWeek == java.time.DayOfWeek.FRIDAY) Text(
                "✦  Cumanız mübarek olsun  ✦", Modifier.align(Alignment.BottomCenter).padding(bottom = Space.xs),
                style = NumStyle.copy(shadow = androidx.compose.ui.graphics.Shadow(Pal.Gold.copy(alpha = .7f), blurRadius = 18f)),
                fontSize = 19.sp, letterSpacing = 1.sp, color = Color(0xFFFFE3A3),
            )
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().padding(start = Space.m, end = Space.s, top = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LocationChip(store.city, locating) { if (!locating) locate() }
                Spacer(Modifier.weight(1f))
                Row(Modifier.turHedef("ust")) {
                    HeaderButton(onOpenKible) { Icon(painterResource(R.drawable.ic_nav_kible), "Kıble", tint = Pal.Text, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(Space.s))
                    GearButton(onOpenSettings)
                }
            }
        }
        // the two dates under the picture: miladi in the text colour, hicri in gold, one line
        // design K1 (design/design-system/tarih-karma.html): a mini date box, the weekday and the hijri date on the left,
        // the next mübarek günler taking turns on the right, in days
        val tr = Locale("tr")
        Row(Modifier.fillMaxWidth().padding(start = Space.xl, end = Space.xl, top = Space.xs).height(46.dp).turHedef("tarih"), verticalAlignment = Alignment.CenterVertically) {
            Column(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(Pal.Card)
                    .border(1.dp, Pal.Gold.copy(alpha = .45f), RoundedCornerShape(10.dp)).clickable { takvim = calDate },
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Text("${calDate.dayOfMonth}", style = NumStyle, fontSize = 22.sp, lineHeight = 22.sp, color = Pal.Text)
                Text(calDate.format(DateTimeFormatter.ofPattern("MMM", tr)).uppercase(tr), color = Pal.Gold, fontSize = 8.5.sp, fontWeight = FontWeight.SemiBold, lineHeight = 10.sp)
            }
            Column(Modifier.padding(start = Space.m).weight(1f).clickable { takvim = calDate }) {
                Text(calDate.format(DateTimeFormatter.ofPattern("EEEE", tr)), color = Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.Medium)
                Text(hijri(calDate), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Box(Modifier.clickable { mubarek = true }) { HolyTicker(holy) }
        }
        // design M2 / C2 (design/design-system/takvim-secenekler.html)
        if (mubarek) MubarekSheet(calDate, onClose = { mubarek = false }) { mubarek = false; takvim = it }
        takvim?.let { TakvimSheet(it) { takvim = null } }
        // Cuma: the week's ayet once, the first time home is seen after the Cuma vakti enters; leaving home or × hides it
        val cumaGirdi = calDate.dayOfWeek == java.time.DayOfWeek.FRIDAY && now >= slots(store.lat, store.lng, calDate)[1].start
        var cumaKart by remember { mutableStateOf(false) }
        LaunchedEffect(cumaGirdi) {
            if (cumaGirdi && store.cumaAyetGun != calDate.toString()) {
                if (SkyDebug.offsetMs == 0L) store.cumaAyetGun = calDate.toString()   // a `skytime` preview must not use up the real Friday
                cumaKart = true
            }
        }
        androidx.compose.animation.AnimatedVisibility(cumaKart, enter = fadeIn(tween(600)) + androidx.compose.animation.expandVertically(tween(500)), exit = fadeOut(tween(300)) + androidx.compose.animation.shrinkVertically(tween(300))) {
            val a = cumaAyeti(calDate)
            Row(
                Modifier.padding(start = Space.xl, end = Space.s, top = Space.xs).fillMaxWidth().clip(RoundedCornerShape(Radius.m))
                    .background(Pal.Card).border(1.dp, Pal.Gold.copy(alpha = .25f), RoundedCornerShape(Radius.m))
                    .padding(start = Space.m, top = Space.s, bottom = Space.s),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("CUMA", color = Pal.Gold, fontSize = Type.micro, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                    Text("\"${a.text}\"", Modifier.padding(top = 2.dp), color = Pal.Text, fontSize = Type.body2, lineHeight = 19.sp,
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                    Text(a.source, Modifier.padding(top = 2.dp), color = Pal.Gold, fontSize = Type.label)
                }
                IconButton(onClick = { cumaKart = false }) { Icon(Icons.Default.Close, "Kapat", tint = Pal.Muted, modifier = Modifier.size(18.dp)) }
            }
        }

            val todaySlots = slots(store.lat, store.lng, today)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = LocalDockPad.current)) {
                VakitlerBody(todaySlots, now, d.gunes, ex, next, onTesbihat) {
                    // once a month without a backup: one quiet band, "Sonra" hides it for another 30 days
                    if (store.backupDue(now)) Row(
                        Modifier.padding(start = Space.xl, end = Space.xl, top = Space.m).fillMaxWidth()
                            .border(1.dp, Pal.Border, RoundedCornerShape(Radius.s)).padding(start = Space.m),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Info, null, tint = Pal.Gold, modifier = Modifier.size(16.dp))
                        Text("Kayıtların yalnızca bu telefonda.", Modifier.weight(1f).padding(start = Space.s), color = Pal.Text, fontSize = Type.label)
                        TextButton(onClick = { store.backupNagUntil = now + 30L * 86_400_000; refresh++ }) { Text("Sonra", color = Pal.Muted, fontSize = Type.label) }
                        TextButton(onClick = onOpenSettings) { Text("Yedek al", color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
        }
        }
    }
}

// ---------------------------------------------------------------- "Kıldın mı?" full screen

@Composable
fun ReminderScreen(key: String, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val now = rememberNow()
    val slot = remember(key) {
        val today = LocalDate.now()
        (-1L..1L).flatMap { slots(store.lat, store.lng, today.plusDays(it)) }.firstOrNull { it.key == key }
    }
    if (slot == null || store.isPrayed(key) || now >= slot.end) { LaunchedEffect(Unit) { onClose() }; return }
    val progress = ((now - slot.start).toFloat() / (slot.end - slot.start)).coerceIn(0f, 1f)

    Column(
        Modifier.fillMaxSize().background(Pal.Ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Kapat", tint = Pal.Muted) }
        }
        Spacer(Modifier.height(Space.xl))
        Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2
            drawCircle(Pal.Track, r - 2, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawArc(Pal.Gold, -90f, 360f * progress, false, topLeft = androidx.compose.ui.geometry.Offset(2f, 2f),
                size = androidx.compose.ui.geometry.Size(size.width - 4, size.height - 4),
                style = androidx.compose.ui.graphics.drawscope.Stroke(4f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        }
        Image(painterResource(R.drawable.ic_logo_mono), null, Modifier.size(150.dp))   // the "Beş vakit gülü"
        }
        Spacer(Modifier.height(Space.xxl))
        Text("${slot.ad(store.kadin)} vakti girdi".uppercase(Locale("tr")), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Text("Kıldın mı?", style = NumStyle, fontSize = 60.sp, color = Pal.Text)
        // one öğüt per opening of this page, the next in turn
        val ogut = remember(key) { nextOgut(store) }
        Text(ogut.text, color = Pal.Text.copy(alpha = .85f), fontSize = Type.body, lineHeight = 24.sp, textAlign = TextAlign.Center)
        Text(ogut.source, Modifier.padding(top = Space.xs), color = Pal.Gold.copy(alpha = .8f), fontSize = Type.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xl))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(countdown(slot.end - now), style = NumStyle, fontSize = Type.title, color = Pal.Text)
            Spacer(Modifier.width(Space.s))
            Text("vaktin çıkmasına", color = Pal.Muted, fontSize = Type.body, modifier = Modifier.padding(bottom = Space.xs))
        }
        Spacer(Modifier.weight(1f))
        GoldButton("Kıldım", Modifier.fillMaxWidth().height(60.dp), Icons.Default.Check) {
            Reminders.markPrayed(ctx, slot.key, slot.vakit); onClose()
        }
        Spacer(Modifier.height(Space.s))
        GhostButton("${store.snoozeMin} dk sonra hatırlat", Modifier.fillMaxWidth()) {
            Reminders.snooze(ctx, slot.key, slot.vakit); onClose()
        }
        Text("Kılana kadar hatırlatmaya devam edeceğim.", Modifier.padding(vertical = Space.m), color = Pal.Muted, fontSize = Type.label)
    }
}

internal fun openAutostart(ctx: Context) {
    val miui = Intent().setComponent(ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"))
    try { ctx.startActivity(miui) } catch (e: Exception) {
        ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
    }
}

@SuppressLint("MissingPermission") // only called after the permission was granted
internal suspend fun fetchLocation(ctx: Context, store: Store) {
    val lm = ctx.getSystemService(LocationManager::class.java)
    val provider = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
        .firstOrNull { it in lm.allProviders && lm.isProviderEnabled(it) } ?: return
    val loc = suspendCoroutine { cont -> lm.getCurrentLocation(provider, null, ctx.mainExecutor) { cont.resume(it) } }
        ?: lm.getLastKnownLocation(provider) ?: return
    val city = withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            Geocoder(ctx, Locale("tr")).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
                ?.let { it.subAdminArea ?: it.locality ?: it.adminArea }
        } catch (e: Exception) { null }
    }
    store.setLocation(loc.latitude, loc.longitude, city ?: "")
}


















