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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Reminders.createChannels(this)
    // Uygulama açılır açılmaz kaçan vakitleri kazaya yaz (Kaza sekmesini açmayı bekleme)
    Thread {
        runCatching { Store(this).settleKaza() }
        runCatching { Reminders.scheduleNext(this) }
    }.apply { isDaemon = true }.start()
        reminderKey = intent.getStringExtra(EXTRA_SLOT)
        startTab = intent.getIntExtra("tab", 0) // lets `adb shell am start -e tab N` open a tab for testing
        SkyDebug.offsetMs = intent.getLongExtra("skytime", 0L) // `--el skytime` previews another hour of the sky
        if (intent.hasExtra("kible")) SkyDebug.kible = intent.getFloatExtra("kible", 0f) // `--ef kible 160` fakes the compass
        SkyDebug.mSection = intent.getIntExtra("msection", 0) // `--ei msection 1` opens Muhasebe > Gidişat
        SkyDebug.zSection = intent.getIntExtra("zsection", 0) // `--ei zsection 1` opens Zikir > Takip
        // `--ez resetentries true` (debug builds): Ayarlar › Kayıtları sıfırla without tapping; tracking restarts at the next imsak
        if (intent.getBooleanExtra("resetentries", false) && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val s = Store(this)
            android.util.Log.i("Reset", "entries reset: ${s.resetEntries(s.nextImsak())}")
            Reminders.scheduleNext(this)
        }
        // `--ez backuptest true` (debug builds): export → parse → compare with the live prefs, log only, writes nothing
        if (intent.getBooleanExtra("backuptest", false) && applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            val live = getSharedPreferences("namazim", MODE_PRIVATE).all
            val parsed = Store(this).let { it.readBackup(it.exportBackup()).values }
            val bad = live.keys.filter { k -> live[k] != parsed[k] }
            android.util.Log.i("Backup", "round trip: ${live.size} keys, ${parsed.size} parsed, mismatched=$bad")
        }
        SkyDebug.gPeriod = intent.getIntExtra("gperiod", 2) // `--ei gperiod 0..3` opens Gidişat on Hafta/Ay/Yıl/Tümü
        if (intent.getBooleanExtra("keepawake", false)) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (intent.getBooleanExtra("widgetpreview", false)) {
            setContent { AppTheme { WidgetPreview() } }
            return
        }
        setContent { AppTheme { App(reminderKey, startTab) { reminderKey = null } } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_SLOT)?.let { reminderKey = it }
        if (intent.hasExtra("tab")) startTab = intent.getIntExtra("tab", startTab)
    }

    override fun onResume() {
        super.onResume()
        Reminders.scheduleNext(this)
    }
}

@Composable
fun App(reminderKey: String?, startTab: Int, onReminderClosed: () -> Unit) {
    var tab by remember { mutableIntStateOf(startTab) }
    // `am start --ei tab N` on a running instance moves to that tab (useful for screenshots)
    LaunchedEffect(startTab) { tab = startTab }
    // Kıble (1) ve Ayarlar (4) sekme değil, ana sayfanın başlığından açılır: dock gizlenir, geri ana sayfaya döner
    val subPage = tab == 1 || tab == 4
    BackHandler(subPage) { tab = 0 }
    Box(Modifier.fillMaxSize().background(Pal.Ground)) {
        Scaffold(
            containerColor = Pal.Ground,
            contentWindowInsets = WindowInsets(0),
            bottomBar = { if (!subPage) SeherNav(tab) { tab = it } },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                // sekmeler arası yumuşak geçiş: hafif kayma + solma
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)) +
                            androidx.compose.animation.slideInVertically { it / 28 }) togetherWith
                            androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150))
                    },
                    label = "tab",
                ) { t ->
                    when (t) {
                        0 -> NamazScreen(onOpenKible = { tab = 1 }) { tab = 4 }
                        1 -> KibleScreen { tab = 0 }
                        2 -> OkumaScreen()
                        3 -> MuhasebeScreen()
                        5 -> ZikirScreen()
                        else -> AyarlarScreen { tab = 0 }
                    }
                }
            }
        }
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
                .background(Pal.Nav.copy(alpha = .88f))
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xl)) {
            Image(painterResource(R.drawable.ic_logo), "Logo", Modifier.size(108.dp))
            Image(bmp, "Canlı ikon", Modifier.size(144.dp))
        }
        Spacer(Modifier.height(Space.s))
        Text(NamazimWidget.label(ctx), color = Pal.Text, fontSize = Type.micro, fontWeight = FontWeight.Medium)
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
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { delay(1000); now = System.currentTimeMillis() } }
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
fun NamazScreen(onOpenKible: () -> Unit, onOpenSettings: () -> Unit) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    val now = rememberNow()
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }

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
        val calDate = LocalDate.now()
        val imsakToday = slots(store.lat, store.lng, calDate).first().start
        val today = if (now < imsakToday) calDate.minusDays(1) else calDate
        val d = dayTimes(store.lat, store.lng, today)
        val tomorrow = dayTimes(store.lat, store.lng, today.plusDays(1))
        val events = listOf(d.imsak to "İmsak", d.gunes to "Güneş doğuşuna", d.ogle to "Öğle", d.ikindi to "İkindi",
            d.aksam to "Akşam", d.yatsi to "Yatsı", tomorrow.imsak to "İmsak")
        val next = events.first { it.first > now }
        val ex = extras(d, tomorrow.imsak)
        val holy = remember(today) { nextHolyDay(today) }

        Column(Modifier.fillMaxSize()) {
        // the Harem clips are the showcase: a 240dp header, all of it moving picture
        Box(Modifier.fillMaxWidth().height(240.dp)) {
            HaremSky(store.lat, store.lng, Modifier.fillMaxSize())
            // the clips keep their subject low in the frame: only a short fade, nothing drawn over it
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
                .background(Brush.verticalGradient(
                    0f to Color.Transparent, 0.45f to Pal.Ground.copy(alpha = .72f), 1f to Pal.Ground,
                )))
            Row(
                Modifier.statusBarsPadding().fillMaxWidth().padding(start = Space.m, end = Space.s, top = Space.s),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LocationChip(store.city, locating) { if (!locating) locate() }
                Spacer(Modifier.weight(1f))
                HeaderButton(onOpenKible) { Icon(painterResource(R.drawable.ic_nav_kible), "Kıble", tint = Pal.Text, modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(Space.s))
                GearButton(onOpenSettings)
            }
        }
        // the date sits under the picture as one quiet line
        Text(
            "${calDate.format(TR_DATE)} · ${hijri(calDate)}",
            Modifier.fillMaxWidth().padding(top = Space.xs), color = Pal.Muted, fontSize = Type.label, textAlign = TextAlign.Center,
        )
        Text(
            "✦ ${holy.first.name} · ${holy.second}", Modifier.fillMaxWidth(),
            color = Pal.Gold, fontSize = Type.micro, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
        )

            val todaySlots = slots(store.lat, store.lng, today)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                VakitlerBody(todaySlots, now, d.gunes, ex, next) {
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

private val NAGS = listOf(
    "Dünya işi bekler, namaz beklemez.\nBirkaç dakikanı ayır, kıl gel.",
    "Kalk kıl, vakit geçiyor.\nSevaptan mahrum kalma.",
    "Seccade seni bekliyor.\nHadi, şimdi tam zamanı.",
)

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
        androidx.compose.foundation.Canvas(Modifier.size(200.dp)) {
            val r = size.minDimension / 2
            drawCircle(Pal.Track, r - 2, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
            drawArc(Pal.Gold, -90f, 360f * progress, false, topLeft = androidx.compose.ui.geometry.Offset(2f, 2f),
                size = androidx.compose.ui.geometry.Size(size.width - 4, size.height - 4),
                style = androidx.compose.ui.graphics.drawscope.Stroke(4f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
            val crescent = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(center, r * 0.42f))
            }
            val cut = androidx.compose.ui.graphics.Path().apply {
                addOval(androidx.compose.ui.geometry.Rect(center + androidx.compose.ui.geometry.Offset(r * 0.2f, -r * 0.14f), r * 0.36f))
            }
            drawPath(androidx.compose.ui.graphics.Path.combine(androidx.compose.ui.graphics.PathOperation.Difference, crescent, cut), Pal.Gold)
        }
        Spacer(Modifier.height(Space.xxl))
        Text("${slot.vakit.ad} vakti girdi".uppercase(Locale("tr")), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Text("Kıldın mı?", style = NumStyle, fontSize = 60.sp, color = Pal.Text)
        Text(NAGS[((now / 60_000) % NAGS.size).toInt()], color = Pal.Muted, fontSize = Type.body, lineHeight = 26.sp, textAlign = TextAlign.Center)
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


















