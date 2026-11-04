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
        SkyDebug.mode = intent.getStringExtra("skymode") // `--es skymode photo|video` switches the header renderer
        if (intent.hasExtra("kible")) SkyDebug.kible = intent.getFloatExtra("kible", 0f) // `--ef kible 160` fakes the compass
        SkyDebug.mSection = intent.getIntExtra("msection", 0) // `--ei msection 1` opens Muhasebe > Gidişat
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
        Triple(2, "Okuma", R.drawable.ic_nav_okuma), Triple(3, "Muhasebe", R.drawable.ic_nav_muhasebe),
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

/** One node of the day's timeline: rail + dot, name, time, and the "kıldım" tick.
 *  Extra times hang under their vakit as children, opened with the chevron. */
@Composable
fun TimelineRow(
    name: String, time: String, sub: String?, main: Boolean, active: Boolean, prayed: Boolean, kaza: Boolean,
    color: Color, isFirst: Boolean, isLast: Boolean, onToggle: (() -> Unit)?,
    hasChildren: Boolean = false, expanded: Boolean = false, onExpand: (() -> Unit)? = null, child: Boolean = false,
) {
    val h = when {
        child -> 34.dp
        main -> 52.dp
        else -> 40.dp
    }
    Row(Modifier.fillMaxWidth().height(h), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.Canvas(Modifier.width(28.dp).fillMaxHeight()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val lw = 1.5.dp.toPx()
            val rail = Pal.Text.copy(alpha = .18f)
            if (!isFirst) drawLine(rail, Offset(cx, 0f), Offset(cx, cy - 7.dp.toPx()), lw)
            if (!isLast) drawLine(rail, Offset(cx, cy + 7.dp.toPx()), Offset(cx, size.height), lw)
            val node = when {
                prayed || active -> Pal.Gold
                kaza -> Pal.Warn
                else -> Pal.Border
            }
            if (active) drawCircle(Pal.Gold.copy(alpha = .20f), radius = 10.dp.toPx(), center = Offset(cx, cy))
            val r = when {
                active -> 5.5.dp.toPx()
                child -> 2.5.dp.toPx()
                else -> 4.dp.toPx()
            }
            drawCircle(node, radius = r, center = Offset(cx, cy))
        }
        Row(
            Modifier.weight(1f).fillMaxHeight()
                .clickable(enabled = hasChildren && onExpand != null) { onExpand?.invoke() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (hasChildren) {
                Icon(
                    Icons.Default.KeyboardArrowDown, if (expanded) "Kapat" else "Ek vakitler",
                    tint = if (active) Pal.Gold else Pal.Muted,
                    modifier = Modifier.size(16.dp).rotate(if (expanded) 180f else 0f),
                )
                Spacer(Modifier.width(2.dp))
            } else if (!child) {
                Spacer(Modifier.width(Space.l))
            }
            Column(Modifier.weight(1f).padding(start = if (child) 12.dp else 0.dp)) {
                Text(name, color = color, fontSize = if (child) 13.sp else 15.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
                when {
                    kaza -> Text("kaza", color = Pal.Warn, fontSize = Type.micro, letterSpacing = .4.sp)
                    !sub.isNullOrEmpty() -> Box(
                        Modifier.padding(top = 1.dp).clip(RoundedCornerShape(6.dp))
                            .border(1.dp, Pal.Gold.copy(alpha = .35f), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(sub, color = Pal.Gold.copy(alpha = .9f), fontSize = Type.micro, letterSpacing = .5.sp)
                    }
                }
            }
            Text(time, color = color, fontSize = if (child) 12.5.sp else 15.sp,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
        }
        Spacer(Modifier.width(Space.m))
        if (onToggle != null) TickButton(prayed, active, onToggle) else Spacer(Modifier.width(26.dp))
    }
}

@Composable
fun TickButton(on: Boolean, active: Boolean, onClick: () -> Unit) {
    // 44 dp touch target around a small, thin ring
    Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .background(if (on) Pal.Gold else Color.Transparent)
                .border(if (on) 0.dp else 1.dp, if (active) Pal.Gold.copy(alpha = .85f) else Pal.Border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (on) Icon(Icons.Default.Check, "Kılındı", tint = Pal.Ground, modifier = Modifier.size(13.dp))
        }
    }
}

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
    var fastSky by remember { mutableStateOf(false) }

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
        val near = (-1L..1L).flatMap { slots(store.lat, store.lng, today.plusDays(it)) }
        val d = dayTimes(store.lat, store.lng, today)
        val tomorrow = dayTimes(store.lat, store.lng, today.plusDays(1))
        val current = near.firstOrNull { now in it.start until it.end }
        val owed = current != null && !store.isPrayed(current.key) && store.tracked(current)
        val events = listOf(d.imsak to "İmsak", d.gunes to "Güneş doğuşuna", d.ogle to "Öğle", d.ikindi to "İkindi",
            d.aksam to "Akşam", d.yatsi to "Yatsı", tomorrow.imsak to "İmsak")
        val next = events.first { it.first > now }
        val ex = extras(d, tomorrow.imsak)
        val holy = remember(today) { nextHolyDay(today) }
        val heroLabel = when {
            owed -> "${current!!.vakit.ad} vakti · henüz kılmadın"
            next.second == "Güneş doğuşuna" -> "Güneş doğuşuna"
            else -> "${next.second} vaktine"
        }
        val heroTime = countdown(if (owed) current!!.end - now else next.first - now)
        val heroSub = when {
            owed -> "vaktin çıkmasına"
            current != null && store.isPrayed(current.key) -> "${current.vakit.ad} kılındı"
            else -> "kaldı"
        }

        Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(340.dp)) {
            when (SkyAssets.mode(ctx)) {
                SkyMode.Video -> {
                    SkyVideo("sky/scene.mp4", Modifier.fillMaxSize())
                    SkyArc(store.lat, store.lng, Modifier.fillMaxSize())
                }
                SkyMode.Photo -> SkyPhoto(store.lat, store.lng, Modifier.fillMaxSize())
                else -> SkyView(store.lat, store.lng, Modifier.fillMaxSize()) { fastSky = it }
            }
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(150.dp)
                .background(Brush.verticalGradient(
                    0f to Color.Transparent, 0.45f to Pal.Ground.copy(alpha = .72f), 1f to Pal.Ground,
                )))
            val shadow = TextStyle(shadow = Shadow(Color(0x99000000), blurRadius = 14f))
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
            // Date, hijri and the next mübarek gün: a small "1 KAS" tile like Vakit, then the details.
            Row(
                Modifier.align(Alignment.BottomStart).padding(start = Space.l, end = 96.dp, bottom = Space.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Color(0x59000000))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("${calDate.dayOfMonth}", style = NumStyle, fontSize = Type.h2, lineHeight = 20.sp, color = Pal.Text)
                    Text(
                        calDate.format(DateTimeFormatter.ofPattern("MMM", Locale("tr"))).uppercase(Locale("tr")),
                        color = Pal.Gold, fontSize = 8.sp, letterSpacing = 1.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.width(Space.s))
                Column {
                    Text(calDate.format(TR_DATE), color = Pal.Text.copy(alpha = .95f), fontSize = Type.label, style = shadow)
                    Text(hijri(calDate), color = Pal.Text.copy(alpha = .70f), fontSize = Type.micro, style = shadow)
                    Spacer(Modifier.height(2.dp))
                    Text("✦ ${holy.first.name} · ${holy.second}", color = Pal.Gold, fontSize = Type.micro,
                        fontWeight = FontWeight.SemiBold, style = shadow)
                }
            }
            if (fastSky) {
                Text("Hızlı önizleme · bitirmek için basılı tut", Modifier.align(Alignment.BottomCenter).padding(bottom = Space.s)
                    .clip(CircleShape).background(Color(0x66000000)).padding(horizontal = Space.m, vertical = Space.xs),
                    color = Pal.Text, fontSize = Type.micro)
            }
        }

            // ---- geri sayım: uygulama açılır açılmaz görünsün
            Column(Modifier.fillMaxWidth().padding(top = Space.s), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(heroLabel.uppercase(Locale("tr")), color = Pal.Gold, fontSize = Type.micro,
                    fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Text(heroTime, style = NumStyle, fontSize = Type.display, lineHeight = 48.sp, color = Pal.Text)
                Text(heroSub, color = Pal.Muted, fontSize = Type.body2)
            }

            val todaySlots = slots(store.lat, store.lng, today).associateBy { it.vakit }
            data class Row_(
                val name: String, val time: String, val start: Long, val active: Boolean, val main: Boolean,
                val slot: Slot?, val prayed: Boolean, val kaza: Boolean, val sub: String?, val color: Color,
                val group: String, val children: List<Extra>,
            )
            val groups = listOf(
                Triple("İmsak", d.imsak, Vakit.SABAH), Triple("Güneş", d.gunes, null), Triple("Öğle", d.ogle, Vakit.OGLE),
                Triple("İkindi", d.ikindi, Vakit.IKINDI), Triple("Akşam", d.aksam, Vakit.AKSAM), Triple("Yatsı", d.yatsi, Vakit.YATSI),
            )
            val main = groups.map { (name, t, v) ->
                val slot = v?.let { todaySlots[it] }
                val active = if (slot != null) now in slot.start until slot.end else now in d.gunes until d.ogle
                val prayed = slot != null && store.isPrayed(slot.key)
                val kaza = slot != null && !prayed && now >= slot.end && store.tracked(slot)
                val at = slot?.let { store.prayedAt(it.key) }
                val sub = when {
                    prayed -> at?.let { hm(it) } ?: ""
                    kaza -> "kaza"
                    else -> null
                }
                Row_(name, hm(t), t, active, true, slot, prayed, kaza, sub, if (active) Pal.Gold else Pal.Text,
                    name.uppercase(Locale("tr")), ex.filter { it.group == name.uppercase(Locale("tr")) })
            }

            // ek vakitler ağaç gibi: vaktin altında açılır/kapanır
            val expanded = remember { mutableStateMapOf<String, Boolean>() }
            val activeExtra = ex.firstOrNull { it.end != null && now in it.start until it.end }
            LaunchedEffect(Unit) { activeExtra?.let { expanded[it.group] = true } }

            LazyColumn(Modifier.weight(1f).padding(horizontal = Space.xl)) {
                main.forEachIndexed { i, r ->
                    item("p$i") {
                        TimelineRow(
                            r.name, r.time, r.sub, r.main, r.active, r.prayed, r.kaza, r.color,
                            isFirst = i == 0, isLast = i == main.lastIndex && r.children.isEmpty(),
                            onToggle = r.slot?.let { slot ->
                                {
                                    if (r.prayed) Reminders.unmarkPrayed(ctx, slot.key)
                                    else Reminders.markPrayed(ctx, slot.key, slot.vakit)
                                    refresh++
                                }
                            },
                            hasChildren = r.children.isNotEmpty(),
                            expanded = expanded[r.group] == true,
                            onExpand = { expanded[r.group] = expanded[r.group] != true },
                        )
                    }
                    if (expanded[r.group] == true) {
                        r.children.forEachIndexed { j, e ->
                            item("c$i-$j") {
                                TimelineRow(
                                    e.name, if (e.end == null) hm(e.start) else "${hm(e.start)}–${hm(e.end)}", null,
                                    false, e.end != null && now in e.start until e.end, false, false,
                                    if (e.kerahat) Pal.Warn else Pal.Muted,
                                    isFirst = false, isLast = i == main.lastIndex && j == r.children.lastIndex,
                                    onToggle = null, child = true,
                                )
                            }
                        }
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


















