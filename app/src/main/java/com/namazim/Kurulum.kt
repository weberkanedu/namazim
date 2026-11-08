package com.namazim

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

// First-run setup and the spotlight tour (design/design-system/ilk-kurulum.html, approved 2026-11-08).

private const val ADIMLAR = 10

/**
 * One screen per question, "‹ Geri" on every step after the first, the step is kept so a half-done setup resumes.
 * [deneme] (debug `--ei kurulum N` on a phone already set up): the screens work, but tracking start and kaza are not written.
 */
@Composable
fun KurulumScreen(store: Store, startAt: Int, deneme: Boolean, onDone: (tanit: Boolean) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableIntStateOf(startAt.coerceIn(0, ADIMLAR - 1)) }
    fun go(n: Int) { step = n.coerceIn(0, ADIMLAR - 1); if (!deneme) store.kurulumStep = step }
    BackHandler(step > 0) { go(step - 1) }
    val now = rememberNow() // re-checks permissions after coming back from system pages

    var cins by remember { mutableStateOf(if (deneme || store.kurulumStep > 5) (if (store.kadin) "kadin" else "erkek") else null) }
    var bas by remember { mutableStateOf("imsak") }
    var locating by remember { mutableStateOf(false) }
    var konumHata by remember { mutableStateOf(false) }
    var kazaSheet by remember { mutableStateOf(false) }
    var kazaEklendi by remember { mutableIntStateOf(0) }
    var pending by remember { mutableStateOf<Store.Backup?>(null) }
    var msg by remember { mutableStateOf("") }

    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.any { it }) {
            locating = true
            scope.launch { fetchLocation(ctx, store); locating = false; konumHata = !store.hasLocation; Reminders.scheduleNext(ctx) }
        } else konumHata = true
    }
    val askNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { go(step + 1) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
            require(bytes.size < 5_000_000) { "Dosya çok büyük; bu bir Namazım yedeği değil." }
            pending = store.readBackup(String(bytes))
        } catch (e: IllegalArgumentException) { msg = e.message ?: "Yedek okunamadı." }
        catch (e: Exception) { msg = "Yedek okunamadı: ${e.message}" }
    }
    val notifOk = ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    val batteryOk = now.let { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }

    Column(Modifier.fillMaxSize().background(Pal.Ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = Space.xl)) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            if (step > 0) TextButton(onClick = { go(step - 1) }, Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Pal.Muted, modifier = Modifier.size(18.dp))
                Text(" Geri", color = Pal.Muted, fontSize = Type.body2)
            }
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(ADIMLAR) { k ->
                    Box(Modifier.height(6.dp).width(if (k == step) 18.dp else 6.dp).clip(CircleShape)
                        .background(if (k == step) Pal.Gold else if (k < step) Pal.Gold.copy(alpha = .4f) else Pal.Track))
                }
            }
        }
        AnimatedContent(step, Modifier.weight(1f), label = "kurulum",
            transitionSpec = { (fadeIn(tween(250)) + slideInHorizontally(tween(250)) { if (targetState > initialState) it / 6 else -it / 6 }) togetherWith fadeOut(tween(120)) },
        ) { s ->
            Column(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                    when (s) {
                        0 -> {
                            Image(painterResource(R.drawable.ic_logo_mono), null, Modifier.padding(top = Space.xl).size(132.dp))
                            Baslik("Namazım'a hoş geldin", "Vaktinde hatırlatır, kıldığını işaretlersin; kaçanlar kazaya yazılır. Zikir, okuma ve defterin bir arada.")
                        }
                        1 -> {
                            Simge(Icons.Default.Place)
                            Baslik("Vakitler için konumun", "Namaz vakitleri bulunduğun yere göre Diyanet yöntemiyle hesaplanır. Konum yalnızca telefonunda kalır.")
                            Text(
                                when { locating -> "Konum alınıyor…"; store.hasLocation -> "📍 " + store.city.ifEmpty { "Konum alındı" }
                                    konumHata -> "Konum alınamadı. Telefonda konum açık mı? İzin verdiysen tekrar dene."; else -> "" },
                                Modifier.padding(top = Space.l), color = if (konumHata && !store.hasLocation) Pal.Warn else Pal.Gold,
                                fontSize = Type.body, textAlign = TextAlign.Center,
                            )
                        }
                        2 -> {
                            Simge(Icons.Default.Notifications)
                            Baslik("Hatırlatabilmem için", "Vakit girmeden önce haber veririm; kılana kadar \"Kıldın mı?\" diye sorarım. Bunun için bildirim izni gerekiyor.")
                            if (notifOk) Tamam("Bildirim izni verildi")
                        }
                        3 -> {
                            Simge(Icons.Default.Lock)
                            Baslik("Hatırlatmalar gecikmesin", "Bazı telefonlar pili korumak için uygulamaları uyutur; hatırlatma geç gelir. Namazım'ı bu kısıtlamadan çıkaralım.")
                            if (batteryOk) Tamam("Pil kısıtlaması kaldırıldı")
                            if (Build.MANUFACTURER.equals("xiaomi", true)) {
                                Text("Xiaomi'de ayrıca Otomatik başlatma'yı açman gerekir: açılan sayfada Namazım'ı aç.",
                                    Modifier.padding(top = Space.l), color = Pal.Muted, fontSize = Type.body2, textAlign = TextAlign.Center)
                                TextButton(onClick = { openAutostart(ctx) }) { Text("Otomatik başlatmayı aç", color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
                            } else if (Build.MANUFACTURER.equals("samsung", true)) {
                                Text("Samsung'da ayrıca: Ayarlar › Pil › Arka plan kullanım sınırları › Hiç uyutulmayan uygulamalar'a Namazım'ı ekle.",
                                    Modifier.padding(top = Space.l), color = Pal.Muted, fontSize = Type.body2, textAlign = TextAlign.Center)
                            }
                        }
                        4 -> {
                            Simge(Icons.Default.Star)
                            Baslik("Mezhebin", "Faziletli vakitler (gülün vaktinde yaprağı) ve bazı hesaplar mezhebe göre değişir.")
                            Spacer(Modifier.height(Space.l))
                            Secenek("Hanefî", "Türkiye'de yaygın. Sabah ortalık aydınlanınca, akşam hemen, yatsı gecenin ilk üçte birinde faziletli.", store.hanefi) { store.hanefi = true }
                            Secenek("Şâfiî", "Her vakitte vaktin başı faziletli.", !store.hanefi) { store.hanefi = false }
                        }
                        5 -> {
                            Simge(Icons.Default.Person)
                            Baslik("Seni tanıyalım", "Kadınlarda özel günlerde namaz takibi durur, kaza yazılmaz. Bu bilgi yalnızca telefonunda kalır.")
                            Spacer(Modifier.height(Space.l))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                Secenek("Erkek", null, cins == "erkek", Modifier.weight(1f)) { cins = "erkek"; store.kadin = false }
                                Secenek("Kadın", null, cins == "kadin", Modifier.weight(1f)) { cins = "kadin"; store.kadin = true }
                            }
                        }
                        6 -> {
                            Simge(Icons.Default.Notifications)
                            val prep = store.prepOffsets.sortedDescending()
                            Baslik("Nasıl hatırlatayım?",
                                (if (prep.isEmpty()) "" else "Vakit girmeden ${prep.joinToString(", ")} dakika önce haber veririm. ") +
                                    "Vakit girince kılana kadar şu sıklıkla \"Kıldın mı?\" diye sorarım:")
                            Row(Modifier.padding(top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                                listOf(10, 15, 20, 30).forEach { m ->
                                    val on = store.nagEvery == m
                                    Text("her $m dk", Modifier.clip(CircleShape).border(1.dp, if (on) Pal.Gold else Pal.Border, CircleShape)
                                        .clickable { store.nagEvery = m; Reminders.scheduleNext(ctx) }.padding(horizontal = Space.m, vertical = Space.s),
                                        color = if (on) Pal.Gold else Pal.Muted, fontSize = Type.body2, fontWeight = FontWeight.Medium)
                                }
                            }
                            Text("Vaktin son yarım saatinde sıklaşır. Hepsi Ayarlar › Hatırlatmalar'dan değişir.",
                                Modifier.padding(top = Space.m), color = Pal.Muted, fontSize = Type.label, textAlign = TextAlign.Center)
                        }
                        7 -> {
                            Simge(Icons.Default.DateRange)
                            Baslik("Takibe nereden başlayalım?", "Bugün kıldığın ama işaretlemediğin vakitler kazaya yazılmasın.")
                            Spacer(Modifier.height(Space.l))
                            Secenek("Sonraki imsaktan", "Bugün sadece tanış; takip sabah başlar.", bas == "imsak") { bas = "imsak" }
                            Secenek("Şu anki vakitten", "Önceki vakitler sayılmaz.", bas == "simdi") { bas = "simdi" }
                        }
                        8 -> {
                            Simge(Icons.Default.Refresh)
                            Baslik("Geçmişten kaza borcun var mı?", "İstersen kabaca hesaplayıp ekleyelim. Sonra Defter › Kaza'dan da yapabilirsin.")
                            if (kazaEklendi > 0) Tamam("Her vakte $kazaEklendi kaza eklendi")
                        }
                        else -> {
                            Simge(Icons.Default.Check)
                            Baslik("Hazırsın", listOf(
                                if (store.hanefi) "Hanefî" else "Şâfiî", if (store.kadin) "Kadın" else "Erkek",
                                "her ${store.nagEvery} dk", if (bas == "imsak") "takip imsaktan" else "takip şimdiden",
                            ).joinToString(" · ") + "\n\nAna sayfayı bir dakikada tanıtayım mı?")
                        }
                    }
                    if (msg.isNotEmpty() && s == 0) Text(msg, Modifier.padding(top = Space.m), color = Pal.Warn, fontSize = Type.body2, textAlign = TextAlign.Center)
                }
                // the buttons stay at the bottom, the primary one first
                Column(Modifier.fillMaxWidth().padding(vertical = Space.l), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    when (s) {
                        0 -> { GoldButton("Başlayalım", Modifier.fillMaxWidth()) { go(1) }
                            Ikincil("Eski telefondan yedeğim var · yükle") { openBackup.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } }
                        1 -> if (store.hasLocation) GoldButton("Devam", Modifier.fillMaxWidth()) { go(2) }
                            else { GoldButton(if (locating) "Konum alınıyor…" else "Konumumu bul", Modifier.fillMaxWidth(), Icons.Default.Place) {
                                    if (!locating) askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
                                Ikincil("Sonra") { go(2) } }
                        2 -> if (notifOk) GoldButton("Devam", Modifier.fillMaxWidth()) { go(3) }
                            else { GoldButton("İzin ver", Modifier.fillMaxWidth()) { askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
                                Ikincil("Sonra") { go(3) } }
                        3 -> if (batteryOk) GoldButton("Devam", Modifier.fillMaxWidth()) { go(4) }
                            else { GoldButton("Kısıtlamayı kaldır", Modifier.fillMaxWidth()) {
                                    @SuppressLint("BatteryLife")
                                    val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                                    runCatching { ctx.startActivity(i) } }
                                Ikincil("Sonra") { go(4) } }
                        5 -> GoldButton("Devam", Modifier.fillMaxWidth().then(if (cins == null) Modifier.graphicsLayer { alpha = .4f } else Modifier)) { if (cins != null) go(6) }
                        7 -> GoldButton("Devam", Modifier.fillMaxWidth()) {
                            if (!deneme) { store.startTracking(if (bas == "imsak") store.nextImsak() else System.currentTimeMillis()); Reminders.scheduleNext(ctx) }
                            go(8)
                        }
                        8 -> { if (kazaEklendi == 0) { GoldButton("Evet, hesaplayalım", Modifier.fillMaxWidth()) { kazaSheet = true }; Ikincil("Şimdilik geç") { go(9) } }
                            else GoldButton("Devam", Modifier.fillMaxWidth()) { go(9) } }
                        ADIMLAR - 1 -> { GoldButton("Tanıt", Modifier.fillMaxWidth()) { onDone(true) }; Ikincil("Atla, kendim keşfederim") { onDone(false) } }
                        else -> GoldButton("Devam", Modifier.fillMaxWidth()) { go(s + 1) }
                    }
                }
            }
        }
    }

    if (kazaSheet) KazaDebtSheet(store.kazaRate, onDismiss = { kazaSheet = false }) { days ->
        kazaSheet = false
        if (!deneme) KAZA_KEYS.forEach { store.addKaza(it.first, days) }
        kazaEklendi = days
    }
    pending?.let { b ->
        AlertDialog(
            onDismissRequest = { pending = null }, containerColor = Pal.Card,
            title = { Text("Yedek yüklensin mi?", color = Pal.Text) },
            text = { Text("${b.prayedCount} işaretli namaz · ${b.kazaTotal} kaza" + (if (b.city.isNotEmpty()) " · ${b.city}" else "") +
                "\n\nKurulum atlanır; ayarların yedekteki gibi gelir.", color = Pal.Muted, fontSize = Type.body2) },
            confirmButton = { TextButton(onClick = {
                pending = null
                if (store.restore(b)) { store.kurulumBitti(); Reminders.scheduleNext(ctx); onDone(true) } else msg = "Yedek yüklenemedi."
            }) { Text("Yükle", color = Pal.Gold) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Vazgeç", color = Pal.Muted) } },
        )
    }
}

@Composable
private fun Simge(icon: ImageVector) {
    Box(Modifier.padding(top = Space.xxl).size(88.dp).clip(CircleShape).background(Pal.Gold.copy(alpha = .12f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Pal.Gold, modifier = Modifier.size(38.dp))
    }
}

@Composable
private fun Baslik(title: String, text: String) {
    Text(title, Modifier.padding(top = Space.xl), style = NumStyle, fontSize = 32.sp, lineHeight = 36.sp, color = Pal.Text, textAlign = TextAlign.Center)
    Text(text, Modifier.padding(top = Space.s), color = Pal.Muted, fontSize = Type.body, lineHeight = 22.sp, textAlign = TextAlign.Center)
}

@Composable
private fun Tamam(text: String) =
    Text("✓ $text", Modifier.padding(top = Space.l), color = Pal.Gold, fontSize = Type.body, fontWeight = FontWeight.SemiBold)

@Composable
private fun Ikincil(text: String, onClick: () -> Unit) =
    TextButton(onClick = onClick, Modifier.fillMaxWidth()) { Text(text, color = Pal.Muted, fontSize = Type.body2) }

@Composable
private fun Secenek(title: String, sub: String?, on: Boolean, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.m)
    Column(
        modifier.padding(vertical = Space.xs / 2).clip(shape).background(if (on) Pal.Gold.copy(alpha = .08f) else Color.Transparent)
            .border(1.dp, if (on) Pal.Gold else Pal.Border, shape).clickable(onClick = onClick).padding(Space.m),
        horizontalAlignment = if (sub == null) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(title, color = if (on) Pal.Gold else Pal.Text, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = Pal.Muted, fontSize = Type.label, lineHeight = 16.sp)
    }
}

// ---------------------------------------------------------------- tanıtım turu

/** One stop of the tour: the tab to show, the [hedef] tagged with [turHedef] to light up (null = no spotlight). */
class TurAdim(val tab: Int, val hedef: String?, val baslik: String, val metin: String)

val TUR = listOf(
    TurAdim(0, "gul", "Gül, bugünün beş vakti", "Her yaprak bir vakit. Kıldıkça altına döner; vaktinde kılarsan içindeki yaprak da açılır. Bir yaprağa dokununca o vaktin ayrıntısı açılır."),
    TurAdim(0, "serit", "Vaktin yedi dilimi", "Altın dilimler faziletli vakit, taralı turuncu kerahat. Işıklı nokta şu anı gösterir."),
    TurAdim(0, "kildim", "Kıldım", "Vakit girince burada büyük bir \"Kıldım\" düğmesi olur. Bildirimden de işaretleyebilirsin. Kıldıktan sonra tesbihat kartı çıkar."),
    TurAdim(0, "tarih", "Tarih ve mübarek günler", "Miladi ve hicri tarih; sağda en yakın mübarek günler kaç gün kaldığıyla, sırayla değişir."),
    TurAdim(0, "ust", "Kıble ve ayarlar", "Pusula kıbleyi gösterir; çark ayarları açar: mezhep, hatırlatmalar, izinler, yedek."),
    TurAdim(0, "dock", "Alt menü", "Namaz, Zikir, Okuma ve Defter. Şimdi diğer sekmelere birlikte bakalım."),
    TurAdim(5, "halka", "Zikirhane", "Ekranın her yerine dokun, sayar; her dokunuşta hafif titrer. Hedefe varınca uzun titrer ve sıradaki zikre geçer."),
    TurAdim(5, "zikirsekme", "Takip ve kütüphane", "Takip, son beş günde hangi zikri tamamladığını gösterir. \"Kütüphaneden ekle\" ile sahih hadislerdeki zikirleri ve Esmâ-ül Hüsnâ'yı ekler, kendi zikrini oluşturursun."),
    TurAdim(2, "raf", "Okuma rafın", "Her sırt bir kitap; Kur'an da burada. Altında bugünkü payın ve tiki var: tike dokununca bugünkü pay okunmuş olur."),
    TurAdim(2, "plan", "Plan ve bitiş tarihi", "Bitirmek istediğin tarihi koy; günlük payın hesaplanır. Öndeysen ya da geride kaldıysan burada söyler."),
    TurAdim(3, "deftersekme", "Defter", "Özet: günün gülü. İstikrar: hafta, ay, yıl ve tüm zamanlar. Kaza: borcun ve gün gün eda."),
    TurAdim(3, "gul3", "Günün gülü", "Beş yaprak: Namaz, Vaktinde, Sayfa, Kaza, Zikir. Gün boyunca yaptıkça yapraklar açılır."),
    TurAdim(0, null, "Hepsi bu kadar", "Ayarlar'da \"Tanıtımı tekrar göster\" ile istediğin zaman yeniden bakabilirsin. Allah kabul etsin."),
)

/** The tour's state: the step on screen (-1 = off) and where each tagged element is on the screen. */
object Tur {
    var adim by mutableIntStateOf(-1)
    val rects = mutableStateMapOf<String, Rect>()
}

/** Marks an element the tour can light up. */
fun Modifier.turHedef(id: String): Modifier = composed {
    DisposableEffect(id) { onDispose { Tur.rects.remove(id) } }
    onGloballyPositioned { Tur.rects[id] = it.boundsInRoot() }
}

/** Dims the screen except the current step's element and shows a bubble above or below it; touches do not reach the app. */
@Composable
fun TurOverlay(onTab: (Int) -> Unit, onEnd: () -> Unit) {
    val k = Tur.adim.coerceIn(0, TUR.size - 1)
    val a = TUR[k]
    LaunchedEffect(k) { onTab(a.tab) }
    val d = LocalDensity.current
    val pad = with(d) { 6.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
    }) {
        val h = constraints.maxHeight.toFloat()
        val w = constraints.maxWidth.toFloat()
        val r = a.hedef?.let { Tur.rects[it] }?.takeIf { it.height > 4f && it.width > 4f }
        val spec = tween<Float>(450, easing = FastOutSlowInEasing)
        val l by animateFloatAsState(r?.left?.minus(pad) ?: (w / 2), spec, label = "l")
        val t by animateFloatAsState(r?.top?.minus(pad) ?: (h / 2), spec, label = "t")
        val rr by animateFloatAsState(r?.right?.plus(pad) ?: (w / 2), spec, label = "r")
        val b by animateFloatAsState(r?.bottom?.plus(pad) ?: (h / 2), spec, label = "b")
        val pulse by rememberInfiniteTransition(label = "p").animateFloat(.3f, 1f, infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pa")
        Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
            drawRect(Color(0xC8030610))
            if (rr - l > 1f) {
                val cr = CornerRadius(16.dp.toPx())
                drawRoundRect(Color.Black, Offset(l, t), Size(rr - l, b - t), cr, blendMode = BlendMode.Clear)
                val g = 4.dp.toPx()
                drawRoundRect(Pal.Gold.copy(alpha = .8f * pulse), Offset(l - g, t - g), Size(rr - l + 2 * g, b - t + 2 * g),
                    CornerRadius(20.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            }
        }
        var bh by remember { mutableIntStateOf(0) }
        val gap = with(d) { 14.dp.toPx() }
        val top = with(d) { 40.dp.toPx() }
        val below = r != null && (r.top + r.bottom) / 2 < h / 2
        val y = when {
            r == null -> (h - bh) / 2
            below -> b + gap
            else -> t - gap - bh
        }.coerceIn(top, (h - bh - top).coerceAtLeast(top))
        Column(
            Modifier.offset { IntOffset(0, y.toInt()) }.padding(horizontal = Space.l).fillMaxWidth()
                .onSizeChanged { bh = it.height }
                .clip(RoundedCornerShape(18.dp)).background(Pal.Text).padding(Space.l),
        ) {
            Text(a.baslik, color = Pal.Ground, fontSize = Type.body, fontWeight = FontWeight.Bold)
            Text(a.metin, Modifier.padding(top = Space.xs), color = Color(0xFF2A3346), fontSize = Type.body2, lineHeight = 19.sp)
            Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
                Text("${k + 1} / ${TUR.size}", Modifier.weight(1f), color = Color(0xFF6F7B96), fontSize = Type.label)
                val last = k == TUR.size - 1
                if (!last) TextButton(onClick = onEnd) { Text("Atla", color = Color(0xFF6F7B96), fontSize = Type.body2) }
                Button(
                    onClick = { if (last) onEnd() else Tur.adim = k + 1 }, shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = Pal.Ground, contentColor = Pal.Text),
                ) { Text(if (last) "Başla" else "İleri", fontSize = Type.body2, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}
