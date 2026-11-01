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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
        Pal.current = Pal.byId(Store(this).theme)
        reminderKey = intent.getStringExtra(EXTRA_SLOT)
        startTab = intent.getIntExtra("tab", 0) // lets `adb shell am start -e tab N` open a tab for testing
        SkyDebug.offsetMs = intent.getLongExtra("skytime", 0L) // `--el skytime` previews another hour of the sky
        SkyDebug.mode = intent.getStringExtra("skymode") // `--es skymode photo|video` switches the header renderer
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
    Box(Modifier.fillMaxSize().background(Pal.Ground)) {
        Scaffold(
            containerColor = Pal.Ground,
            contentWindowInsets = WindowInsets(0),
            bottomBar = { SeherNav(tab) { tab = it } },
        ) { pad ->
            Box(Modifier.padding(pad).fillMaxSize()) {
                when (tab) {
                    0 -> NamazScreen { tab = 4 }
                    1 -> KibleScreen()
                    2 -> OkumaScreen()
                    3 -> MuhasebeScreen()
                    else -> AyarlarScreen { tab = 0 }
                }
            }
        }
        if (reminderKey != null) ReminderScreen(reminderKey, onReminderClosed)
    }
}

@Composable
fun SeherNav(selected: Int, onSelect: (Int) -> Unit) {
    // Ayarlar is not a tab any more: it opens from the gear on the home header (tab 4), so back returns here.
    val tabs = listOf<Pair<String, ImageVector>>(
        "Namaz" to Icons.Default.Home, "Kıble" to Icons.Default.LocationOn, "Okuma" to Icons.Default.Edit,
        "Muhasebe" to Icons.Default.DateRange,
    )
    NavigationBar(containerColor = Pal.Nav, tonalElevation = 0.dp) {
        tabs.forEachIndexed { i, (name, icon) ->
            NavigationBarItem(
                selected = selected == i, onClick = { onSelect(i) },
                icon = { Icon(icon, null) }, label = { Text(name, fontSize = 11.sp) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = Pal.Gold, selectedTextColor = Pal.Gold, indicatorColor = Pal.Track,
                    unselectedIconColor = Pal.Muted, unselectedTextColor = Pal.Muted,
                ),
            )
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
            Modifier.clip(CircleShape).clickable(enabled = !locating) { open = true }.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Place, null, tint = Pal.Gold, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
            Text(
                if (locating) "Konum alınıyor…" else city.ifEmpty { "Konumum" },
                color = Pal.Text, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, style = shadow, maxLines = 1,
            )
            Spacer(Modifier.width(1.dp))
            Icon(Icons.Default.KeyboardArrowDown, null, tint = Pal.Text.copy(alpha = .55f), modifier = Modifier.size(14.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Pal.Card, shape = RoundedCornerShape(16.dp)) {
            Text(
                if (city.isEmpty()) "Konum belirlenmedi" else city,
                Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 8.dp), color = Pal.Muted, fontSize = 12.sp,
            )
            HorizontalDivider(color = Pal.Line)
            DropdownMenuItem(
                text = { Text("Konumu güncelle", color = Pal.Text, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Refresh, null, tint = Pal.Gold, modifier = Modifier.size(18.dp)) },
                onClick = { open = false; onRefresh() },
            )
            DropdownMenuItem(
                text = { Text("Uygulama izinleri", color = Pal.Text, fontSize = 14.sp) },
                leadingIcon = { Icon(Icons.Default.Settings, null, tint = Pal.Muted, modifier = Modifier.size(18.dp)) },
                onClick = {
                    open = false
                    ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")))
                },
            )
        }
    }
}

/** Thin-line gear: eight teeth on a ring, more elegant than the filled Material icon. */
@Composable
fun GearButton(onClick: () -> Unit) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Color(0x26000000)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Image(painterResource(R.drawable.ic_logo), "Logo", Modifier.size(108.dp))
            Image(bmp, "Canlı ikon", Modifier.size(144.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(NamazimWidget.label(ctx), color = Pal.Text, fontSize = 11.sp, fontWeight = FontWeight.Medium)
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
fun ScreenTitle(title: String, sub: String? = null, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 24.dp, end = 24.dp, top = 20.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, style = NumStyle, fontSize = 36.sp, lineHeight = 40.sp, color = Pal.Text)
            if (sub != null) Text(sub, fontSize = 13.sp, color = Pal.Muted)
        }
        trailing()
    }
}

@Composable
fun GoldButton(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier.heightIn(min = 52.dp), shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Pal.Gold, contentColor = Pal.Ground),
    ) {
        if (icon != null) { Icon(icon, null, Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun GhostButton(text: String, modifier: Modifier = Modifier, color: Color = Pal.Text, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = 48.dp), shape = CircleShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (color == Pal.Text) Pal.Border else color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
    ) { Text(text, fontSize = 14.sp) }
}

private val TR_DATE = DateTimeFormatter.ofPattern("d MMMM EEEE", Locale("tr"))

// ---------------------------------------------------------------- Namaz (home)

@Composable
fun NamazScreen(onOpenSettings: () -> Unit) {
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
            Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center) {
                Text("Hoş geldin", style = NumStyle, fontSize = 44.sp, color = Pal.Text)
                Spacer(Modifier.height(8.dp))
                Text("Vakitleri hesaplamak için konumuna ihtiyacım var. Konum sadece telefonda kalır.", color = Pal.Muted, fontSize = 15.sp)
                Spacer(Modifier.height(24.dp))
                GoldButton(if (locating) "Konum alınıyor…" else "Konumumu bul", Modifier.fillMaxWidth(), Icons.Default.Place) { if (!locating) locate() }
            }
        } else {

        val today = LocalDate.now()
        val near = (-1L..1L).flatMap { slots(store.lat, store.lng, today.plusDays(it)) }
        val d = dayTimes(store.lat, store.lng, today)
        val tomorrow = dayTimes(store.lat, store.lng, today.plusDays(1))
        val current = near.firstOrNull { now in it.start until it.end }
        val owed = current != null && !store.isPrayed(current.key) && store.tracked(current)
        val events = listOf(d.imsak to "İmsak", d.gunes to "Güneş doğuşuna", d.ogle to "Öğle", d.ikindi to "İkindi",
            d.aksam to "Akşam", d.yatsi to "Yatsı", tomorrow.imsak to "İmsak")
        val next = events.first { it.first > now }
        val extrasOn = store.showExtras
        val ex = extras(d, tomorrow.imsak)
        val note = if (!extrasOn) null else ex.firstOrNull { it.end != null && now in it.start until it.end }
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
            if (Pal.current.id == OdakPalette.id) {
                val todaySlotsList = slots(store.lat, store.lng, today)
                OdakHeader(store.city, today, holy, todaySlotsList, now, store, heroLabel, heroTime, heroSub,
                    locating, { if (!locating) locate() }, onOpenSettings)
            } else {
            Box(Modifier.fillMaxWidth().height(380.dp)) {
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
                    Modifier.statusBarsPadding().fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LocationChip(store.city, locating) { if (!locating) locate() }
                    Spacer(Modifier.weight(1f))
                    GearButton(onOpenSettings)
                }
                // Date, hijri and the next mübarek gün live at the bottom left, over the horizon.
                Column(Modifier.align(Alignment.BottomStart).padding(start = 22.dp, end = 96.dp, bottom = 14.dp)) {
                    Text(today.format(TR_DATE), color = Pal.Text.copy(alpha = .95f), fontSize = 12.sp, style = shadow)
                    Text(hijri(today), color = Pal.Text.copy(alpha = .68f), fontSize = 11.5.sp, style = shadow)
                    Spacer(Modifier.height(3.dp))
                    Text("✦ ${holy.first.name} · ${holy.second}", color = Pal.Gold, fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold, style = shadow)
                }
                if (fastSky) {
                    Text("Hızlı önizleme · bitirmek için basılı tut", Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                        .clip(CircleShape).background(Color(0x66000000)).padding(horizontal = 12.dp, vertical = 4.dp),
                        color = Pal.Text, fontSize = 11.sp)
                }
            }

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(heroLabel.uppercase(Locale("tr")), color = Pal.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
                Text(heroTime, style = NumStyle, fontSize = 64.sp, lineHeight = 68.sp, color = Pal.Text)
                Text(heroSub, color = Pal.Muted, fontSize = 14.sp)
            }
            }
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (note != null) {
                    Text(
                        if (note.kerahat) "${note.name} · namaz kılınmaz" else "${note.name} vakti",
                        Modifier.padding(top = 6.dp).clip(CircleShape)
                            .background(if (note.kerahat) Pal.Warn.copy(alpha = .16f) else Pal.Gold.copy(alpha = .14f))
                            .padding(horizontal = 12.dp, vertical = 5.dp),
                        color = if (note.kerahat) Pal.Warn else Pal.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Box(Modifier.padding(horizontal = 24.dp, vertical = 12.dp).fillMaxWidth()) {
                if (owed) {
                    GoldButton("${current!!.vakit.ad} kıldım", Modifier.fillMaxWidth(), Icons.Default.Check) {
                        Reminders.markPrayed(ctx, current.key, current.vakit); refresh++
                    }
                } else if (current != null && store.isPrayed(current.key)) {
                    Row(Modifier.fillMaxWidth().height(52.dp).border(1.dp, Pal.Gold.copy(alpha = .5f), CircleShape),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, null, tint = Pal.Gold); Spacer(Modifier.width(8.dp))
                        Text("Kılındı · Allah kabul etsin", color = Pal.Gold, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Row(Modifier.padding(horizontal = 24.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bugünün vakitleri", Modifier.weight(1f), color = Pal.Muted, fontSize = 13.sp)
                Text("Ek vakitler", color = Pal.Text, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = extrasOn, onCheckedChange = { store.showExtras = it; refresh++ },
                    colors = SwitchDefaults.colors(checkedTrackColor = Pal.Gold, checkedThumbColor = Pal.Ground,
                        uncheckedTrackColor = Pal.Track, uncheckedThumbColor = Pal.Muted, uncheckedBorderColor = Pal.Border),
                )
            }

            val todaySlots = slots(store.lat, store.lng, today).associateBy { it.vakit }
            data class Row_(val name: String, val time: String, val start: Long, val active: Boolean, val main: Boolean,
                            val mark: String, val color: Color)
            val main = listOf(
                Triple("İmsak", d.imsak, Vakit.SABAH), Triple("Güneş", d.gunes, null), Triple("Öğle", d.ogle, Vakit.OGLE),
                Triple("İkindi", d.ikindi, Vakit.IKINDI), Triple("Akşam", d.aksam, Vakit.AKSAM), Triple("Yatsı", d.yatsi, Vakit.YATSI),
            ).map { (name, t, v) ->
                val slot = v?.let { todaySlots[it] }
                val active = if (slot != null) now in slot.start until slot.end else now in d.gunes until d.ogle
                val mark = when {
                    slot == null -> ""
                    store.isPrayed(slot.key) -> "✓"
                    now >= slot.end && store.tracked(slot) -> "kaza"
                    else -> ""
                }
                Row_(name, hm(t), t, active, true, mark, if (active) Pal.Gold else Pal.Text)
            }
            val extraRows = if (!extrasOn) emptyList() else ex.map {
                val active = it.end != null && now in it.start until it.end
                Row_(it.name, if (it.end == null) hm(it.start) else "${hm(it.start)}–${hm(it.end)}", it.start, active, false, "",
                    if (!active) Pal.Muted else if (it.kerahat) Pal.Warn else Pal.Gold)
            }
            val rows = (main + extraRows).sortedBy { (it.start - d.imsak + 86_400_000) % 86_400_000 }

            LazyColumn(Modifier.weight(1f).padding(horizontal = 24.dp)) {
                item { HorizontalDivider(color = Pal.Line) }
                items(rows) { r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = if (r.main) 42.dp else 34.dp).padding(start = if (r.main) 0.dp else 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(r.name, Modifier.weight(1f), color = r.color, fontSize = if (r.main) 15.sp else 13.sp,
                            fontWeight = if (r.active) FontWeight.SemiBold else FontWeight.Normal)
                        if (r.mark.isNotEmpty()) {
                            Text(r.mark, color = if (r.mark == "kaza") Pal.Warn else Pal.Gold, fontSize = 12.sp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(r.time, color = r.color, fontSize = if (r.main) 15.sp else 13.sp,
                            fontWeight = if (r.active) FontWeight.SemiBold else FontWeight.Normal)
                    }
                    HorizontalDivider(color = Pal.Line.copy(alpha = .5f))
                }
                item { Spacer(Modifier.height(12.dp)); SetupCard(); Spacer(Modifier.height(12.dp)) }
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
        Modifier.fillMaxSize().background(Color(0xFF0A1220)).statusBarsPadding().navigationBarsPadding().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Kapat", tint = Pal.Muted) }
        }
        Spacer(Modifier.height(24.dp))
        androidx.compose.foundation.Canvas(Modifier.size(200.dp)) {
            val r = size.minDimension / 2
            drawCircle(Color(0xFF1E2B40), r - 2, style = androidx.compose.ui.graphics.drawscope.Stroke(2f))
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
        Spacer(Modifier.height(32.dp))
        Text("${slot.vakit.ad} vakti girdi".uppercase(Locale("tr")), color = Pal.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp)
        Text("Kıldın mı?", style = NumStyle, fontSize = 60.sp, color = Pal.Text)
        Text(NAGS[((now / 60_000) % NAGS.size).toInt()], color = Color(0xFFC9D1DC), fontSize = 17.sp, lineHeight = 26.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(countdown(slot.end - now), style = NumStyle, fontSize = 34.sp, color = Pal.Text)
            Spacer(Modifier.width(10.dp))
            Text("vaktin çıkmasına", color = Pal.Muted, fontSize = 14.sp, modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.weight(1f))
        GoldButton("Kıldım", Modifier.fillMaxWidth().height(60.dp), Icons.Default.Check) {
            Reminders.markPrayed(ctx, slot.key, slot.vakit); onClose()
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("${store.snoozeMin} dk sonra hatırlat", Modifier.fillMaxWidth()) {
            Reminders.snooze(ctx, slot.key, slot.vakit); onClose()
        }
        Text("Kılana kadar hatırlatmaya devam edeceğim.", Modifier.padding(vertical = 14.dp), color = Pal.Muted, fontSize = 12.sp)
    }
}

// ---------------------------------------------------------------- setup (permissions the reminders need)

/** Reminders only work if the phone lets us: notifications on, no battery kill, Xiaomi autostart. */
@Composable
fun SetupCard() {
    val ctx = LocalContext.current
    rememberNow() // ticking recomposes this card, so it re-checks after the user returns from settings
    val notifOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val batteryOk = ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName)
    val prefs = ctx.getSharedPreferences("namazim", Context.MODE_PRIVATE)
    // Android can't tell us if autostart is on, so hide the hint once the user has opened that screen.
    var autostartSeen by remember { mutableStateOf(prefs.getBoolean("autostartSeen", false)) }
    val xiaomi = Build.MANUFACTURER.equals("xiaomi", true) && !autostartSeen
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    if (notifOk && batteryOk && !xiaomi) return

    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Pal.Card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Hatırlatmalar için", color = Pal.Text, fontWeight = FontWeight.SemiBold)
        if (!notifOk) GhostButton("Bildirimlere izin ver", color = Pal.Gold) { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
        if (!batteryOk) GhostButton("Pil kısıtlamasını kaldır", color = Pal.Gold) {
            @SuppressLint("BatteryLife")
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
            ctx.startActivity(i)
        }
        if (xiaomi) {
            Text("Xiaomi uygulamaları arka planda kapatır. Otomatik başlatmayı aç:", color = Pal.Muted, fontSize = 13.sp)
            GhostButton("Otomatik başlatma", color = Pal.Gold) {
                openAutostart(ctx)
                prefs.edit().putBoolean("autostartSeen", true).apply()
                autostartSeen = true
            }
        }
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
