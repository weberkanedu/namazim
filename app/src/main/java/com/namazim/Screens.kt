package com.namazim

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

@Composable
fun NeedLocation() = Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Text("Önce Namaz sekmesinden konumunu belirle.", color = Pal.Muted, textAlign = TextAlign.Center)
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Pal.Card).padding(horizontal = 16.dp, vertical = 14.dp), content = content)

// ---------------------------------------------------------------- Kıble

private const val KAABA_LAT = 21.4225
private const val KAABA_LNG = 39.8262

private fun distanceKm(lat: Double, lng: Double): Double {
    val r = 6371.0
    val dLat = Math.toRadians(KAABA_LAT - lat); val dLng = Math.toRadians(KAABA_LNG - lng)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat)) * cos(Math.toRadians(KAABA_LAT)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

/** Minute of today when the sun stands in the qibla direction, or null if it never does. */
private fun qiblaTime(lat: Double, lng: Double, qibla: Double): Long? {
    val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    var best: Long? = null; var bestDiff = 99.0
    for (m in 0 until 1440) {
        val t = start + m * 60_000L
        val sun = skyBodies(lat, lng, t).sun
        if (sun.alt <= 0) continue
        val bearing = (Math.toDegrees(sun.az) + 180 + 360) % 360
        val diff = abs(((bearing - qibla + 540) % 360) - 180)
        if (diff < bestDiff) { bestDiff = diff; best = t }
    }
    return if (bestDiff < 1.0) best else null
}

@Composable
fun KibleScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    if (!store.hasLocation) return NeedLocation()
    val qibla = remember { qiblaDegrees(store.lat, store.lng) }
    val km = remember { distanceKm(store.lat, store.lng) }
    val qTime = remember { qiblaTime(store.lat, store.lng, qibla) }
    var heading by remember { mutableFloatStateOf(0f) }
    // The compass reads magnetic north; qibla is a true bearing. Ankara's declination is ~6°, which is
    // also why Diyanet lists 155° (magnetic) where the true bearing is 160°.
    val declination = remember {
        android.hardware.GeomagneticField(store.lat.toFloat(), store.lng.toFloat(), 0f, System.currentTimeMillis()).declination
    }

    DisposableEffect(Unit) {
        val sm = ctx.getSystemService(SensorManager::class.java)
        val rot = FloatArray(9); val ori = FloatArray(3)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rot, e.values)
                SensorManager.getOrientation(rot, ori)
                val az = ((Math.toDegrees(ori[0].toDouble()) + declination + 360) % 360).toFloat()
                val d = ((az - heading + 540) % 360) - 180
                heading = (heading + d * 0.15f + 360) % 360 // smooth out sensor jitter
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    val diff = (((qibla - heading + 540) % 360) - 180).toFloat() // + means turn right
    val aligned = abs(diff) < 4
    LaunchedEffect(aligned) {
        if (aligned) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }
    val glow by animateFloatAsState(if (aligned) 1f else 0.15f, tween(400), label = "glow")
    val measurer = rememberTextMeasurer()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Kıble", store.city.ifEmpty { null }) {
            Text("Telefonu yere\nparalel tut", color = Pal.Muted, fontSize = 12.sp, textAlign = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(340.dp).background(Brush.radialGradient(listOf(Pal.Gold.copy(alpha = 0.32f * glow), Color.Transparent))))
            Canvas(Modifier.size(300.dp)) {
                val c = center; val r = size.minDimension / 2
                drawCircle(Color(0xFF101B30), r)
                drawCircle(Pal.Line, r, style = Stroke(1f))
                rotate(-heading, c) {
                    for (i in 0 until 72) {
                        val major = i % 6 == 0
                        rotate(i * 5f, c) {
                            drawLine(if (major) Pal.Text.copy(alpha = .8f) else Pal.Text.copy(alpha = .35f),
                                Offset(c.x, c.y - r + 10), Offset(c.x, c.y - r + if (major) 24 else 16), if (major) 3f else 2f)
                        }
                    }
                    listOf("K" to 0f, "D" to 90f, "G" to 180f, "B" to 270f).forEach { (t, a) ->
                        val l = measurer.measure(t, TextStyle(color = if (t == "K") Pal.Warn else Pal.Muted, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                        val rr = r - 44
                        val p = Offset(c.x + rr * sin(Math.toRadians(a.toDouble())).toFloat(), c.y - rr * cos(Math.toRadians(a.toDouble())).toFloat())
                        drawText(l, topLeft = Offset(p.x - l.size.width / 2, p.y - l.size.height / 2))
                    }
                    rotate(qibla.toFloat(), c) {
                        val s = 22.dp.toPx()
                        drawRoundRect(Color(0xFF141414), Offset(c.x - s / 2, c.y - r + 26), Size(s, s), androidx.compose.ui.geometry.CornerRadius(4f))
                        drawRect(Pal.Gold, Offset(c.x - s / 2, c.y - r + 26 + s * 0.22f), Size(s, s * 0.16f))
                    }
                }
                if (!aligned) {
                    val inset = 26.dp.toPx()
                    drawArc(Pal.Gold, -90f, diff, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                }
                val needle = Path().apply {
                    moveTo(c.x, c.y - r + 34.dp.toPx()); lineTo(c.x + 10.dp.toPx(), c.y - r + 62.dp.toPx())
                    lineTo(c.x, c.y - r + 56.dp.toPx()); lineTo(c.x - 10.dp.toPx(), c.y - r + 62.dp.toPx()); close()
                }
                drawPath(needle, if (aligned) Pal.Gold else Pal.Text)
                drawCircle(Pal.Ground, r * 0.36f)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${heading.roundToInt()}°", style = NumStyle, fontSize = 38.sp, color = Pal.Text)
                Text("yönün", fontSize = 11.sp, color = Pal.Muted)
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (aligned) "Kıbleye döndün" else if (diff > 0) "Sağa ${diff.roundToInt()}° dön" else "Sola ${(-diff).roundToInt()}° dön",
                color = if (aligned) Pal.Gold else Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
            )
            Text(if (aligned) "Telefon hafifçe titreşti" else "Altın çizgi kıbleye kalan açıyı gösterir", color = Pal.Muted, fontSize = 13.sp)
        }
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard(Modifier.weight(1f)) {
                Text("Kıble açısı", color = Pal.Muted, fontSize = 12.sp)
                Text("${qibla.roundToInt()}°", style = NumStyle, fontSize = 28.sp, color = Pal.Text)
                Text("Pusulada ${((qibla - declination + 360) % 360).roundToInt()}°", color = Pal.Muted, fontSize = 11.sp)
            }
            InfoCard(Modifier.weight(1f)) {
                Text("Kâbe'ye uzaklık", color = Pal.Muted, fontSize = 12.sp)
                Text("≈ %,d km".format(java.util.Locale("tr"), (km / 10).roundToInt() * 10), style = NumStyle, fontSize = 28.sp, color = Pal.Text)
            }
        }
        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 10.dp, bottom = 20.dp).fillMaxWidth()) {
            if (qTime != null) {
                Text("Kıble saati ${hm(qTime)}", color = Pal.Gold, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                Text("Bugün bu saatte güneş tam kıble yönünde. Pusulasız da bulabilirsin.", color = Pal.Muted, fontSize = 13.sp)
            } else {
                Text("Bugün güneş kıble yönünden geçmiyor.", color = Pal.Muted, fontSize = 13.sp)
            }
        }
    }
}

// ---------------------------------------------------------------- Kaza

private val KAZA_KEYS = listOf("SABAH" to "Sabah", "OGLE" to "Öğle", "IKINDI" to "İkindi", "AKSAM" to "Akşam", "YATSI" to "Yatsı", "VITIR" to "Vitir")

@Composable
fun KazaScreen(embedded: Boolean = false) {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var v by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { store.settleKaza(); v++ }
    // reading v makes every value below refresh after a change
    val total = v.let { KAZA_KEYS.sumOf { store.kaza(it.first) } }
    val paid = v.let { store.kazaPaidThisMonth() }
    val rate = v.let { store.kazaRate }
    val ring by animateFloatAsState(if (paid + total == 0) 1f else paid.toFloat() / (paid + total), tween(700), label = "ring")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        if (!embedded) ScreenTitle("Kaza")
        Text("Vaktinde kılınmayan namazlar buraya kendiliğinden eklenir", Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
            color = Pal.Muted, fontSize = 13.sp)
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = 10.dp.toPx(); val inset = w / 2
                    drawArc(Pal.Track, 0f, 360f, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w))
                    drawArc(Pal.Gold, -90f, 360f * ring, false, Offset(inset, inset), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AnimatedContent(total, transitionSpec = { (scaleIn(initialScale = 1.3f) + fadeIn()) togetherWith fadeOut() }, label = "total") {
                        Text("$it", style = NumStyle, fontSize = 48.sp, color = Pal.Text)
                    }
                    Text("kaldı", color = Pal.Muted, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column {
                Text("Bu ay kılınan", color = Pal.Muted, fontSize = 13.sp)
                Text("$paid", style = NumStyle, fontSize = 30.sp, color = Pal.Gold)
            }
        }

        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Kaza planı", Modifier.weight(1f), color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                listOf(1, 2, 3, 5).forEach { n ->
                    val on = n == rate
                    Box(
                        Modifier.padding(start = 6.dp).size(44.dp, 32.dp).clip(CircleShape)
                            .background(if (on) Pal.Gold else Color.Transparent)
                            .border(1.dp, if (on) Pal.Gold else Pal.Border, CircleShape)
                            .clickable { store.kazaRate = n; v++ },
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", color = if (on) Pal.Ground else Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(8.dp))
            val days = (total + rate - 1) / rate
            Text(
                if (total == 0) "Kaza borcun yok. Allah kabul etsin." else "Günde $rate kaza kılarsan $days günde bitirirsin.",
                color = Pal.Text, fontSize = 14.sp,
            )
        }

        Column(Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
            KAZA_KEYS.forEach { (key, name) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 58.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(name, Modifier.weight(1f), color = Pal.Text, fontSize = 16.sp)
                    Text("${v.let { store.kaza(key) }}", Modifier.widthIn(min = 32.dp), style = NumStyle, fontSize = 26.sp, color = Pal.Text, textAlign = TextAlign.End)
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = { store.payKaza(key); v++ }, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pal.Gold, contentColor = Pal.Ground),
                    ) { Text("Kıldım", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedIconButton(onClick = { store.addKaza(key, 1); v++ }, modifier = Modifier.size(36.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Pal.Border)) {
                        Icon(Icons.Default.Add, "$name kazası ekle", tint = Pal.Text, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = Pal.Line.copy(alpha = .5f))
            }
        }
    }
}

// ---------------------------------------------------------------- Okuma

const val QURAN_PAGES = 604

/** Cüz of a page in the standard 604-page mushaf: cüz 1 is pages 1–21, then 20 pages each. */
fun cuz(page: Int) = if (page < 22) 1 else minOf(30, (page - 2) / 20 + 1)

private val TR_DAYS = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

@Composable
fun OkumaScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var v by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    val page = v.let { store.quranPage }
    val log = v.let { store.readLog() }
    val todayDate = LocalDate.now()
    val today = log[todayDate.toString()] ?: 0
    val goal = v.let { store.readGoal }
    val gauge by animateFloatAsState(page / QURAN_PAGES.toFloat(), tween(600), label = "gauge")
    val goalBar by animateFloatAsState((today.toFloat() / goal).coerceAtMost(1f), tween(500), label = "goal")

    fun readQuran(n: Int) {
        var p = store.quranPage + n
        if (p > QURAN_PAGES) { store.hatim += 1; p -= QURAN_PAGES } // finished: count a hatim, carry over
        store.quranPage = p.coerceAtLeast(0)
        store.logPages(n)
        v++
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Okuma") {
            if (store.hatim > 0) Text("${store.hatim} hatim tamamlandı", Modifier.clip(CircleShape).background(Pal.Gold.copy(alpha = .14f))
                .padding(horizontal = 12.dp, vertical = 6.dp), color = Pal.Gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp).fillMaxWidth()) {
            Text("Kur'an-ı Kerîm", color = Pal.Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.BottomCenter) {
                Canvas(Modifier.size(240.dp, 130.dp)) {
                    val w = 12.dp.toPx()
                    val d = size.width - w
                    drawArc(Pal.Track, 180f, 180f, false, Offset(w / 2, w / 2), Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
                    drawArc(Pal.Gold, 180f, 180f * gauge, false, Offset(w / 2, w / 2), Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (page == 0) "—" else "$page", style = NumStyle, fontSize = 44.sp, color = Pal.Text)
                    Text("/ $QURAN_PAGES sayfa", color = Pal.Muted, fontSize = 12.sp)
                }
            }
            Text(if (page == 0) "Henüz başlamadın" else "${cuz(page)}. cüz · ${sureOf(page)} Sûresi",
                Modifier.fillMaxWidth().padding(top = 8.dp), color = Pal.Text, fontSize = 14.sp, textAlign = TextAlign.Center)
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("−", Modifier.width(52.dp)) { if (store.quranPage > 0) readQuran(-1) }
                GoldButton("+1 sayfa okudum", Modifier.weight(1f)) { readQuran(1) }
                GhostButton("+1 cüz", color = Pal.Gold) { readQuran(20) }
            }
        }

        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp).fillMaxWidth()) {
            Row {
                Text(if (today >= goal) "Bugünkü hedef tamam ✓" else "Bugün $today sayfa", Modifier.weight(1f), color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                // Tap to change the daily goal.
                Text("Hedef $goal sayfa", Modifier.clickable {
                    val goals = listOf(5, 10, 15, 20, 30)
                    store.readGoal = goals[(goals.indexOf(goal) + 1) % goals.size]; v++
                }, color = Pal.Muted, fontSize = 13.sp)
            }
            Box(Modifier.padding(top = 10.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(Pal.Track)) {
                Box(Modifier.fillMaxWidth(goalBar).fillMaxHeight().clip(CircleShape).background(Pal.Gold))
            }
            val week = (6 downTo 0).map { todayDate.minusDays(it.toLong()) }
            val max = maxOf(goal, week.maxOf { log[it.toString()] ?: 0 }).toFloat()
            Row(Modifier.padding(top = 14.dp).fillMaxWidth().height(80.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                week.forEach { d ->
                    val n = log[d.toString()] ?: 0
                    val h by animateFloatAsState(n / max, tween(500), label = "bar")
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().height((56 * h).dp.coerceAtLeast(3.dp)).clip(RoundedCornerShape(4.dp))
                            .background(if (d == todayDate) Pal.Gold else Color(0xFF3A4A66)))
                        Text(if (d == todayDate) "Bugün" else TR_DAYS[d.dayOfWeek.value - 1], color = Pal.Muted, fontSize = 10.sp)
                    }
                }
            }
        }

        Row(Modifier.padding(start = 24.dp, end = 12.dp, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Kitaplarım", Modifier.weight(1f), color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            TextButton(onClick = { adding = true }) { Text("+ Kitap ekle", color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
        }
        v.let { store.books() }.forEachIndexed { i, b ->
            InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp).fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(b.name, Modifier.weight(1f), color = Pal.Text, fontSize = 14.sp)
                    Text("${b.page} / ${b.total}", color = Pal.Muted, fontSize = 12.sp)
                    IconButton(onClick = { store.saveBooks(store.books().filterIndexed { j, _ -> j != i }); v++ }, Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, "${b.name} kitabını sil", tint = Pal.Muted, modifier = Modifier.size(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val pct by animateFloatAsState(b.page / b.total.toFloat(), tween(500), label = "book")
                    Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(Pal.Track)) {
                        Box(Modifier.fillMaxWidth(pct).fillMaxHeight().background(if (b.page >= b.total) Pal.Gold else Pal.Muted))
                    }
                    listOf(1, 10).forEach { n ->
                        TextButton(onClick = {
                            val p = minOf(b.total, b.page + n)
                            store.saveBooks(store.books().mapIndexed { j, x -> if (j == i) x.copy(page = p) else x })
                            store.logPages(p - b.page); v++
                        }, enabled = b.page < b.total) { Text("+$n", color = if (b.page < b.total) Pal.Text else Pal.Muted) }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (adding) {
        var name by remember { mutableStateOf("") }
        var total by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { adding = false }, containerColor = Pal.Card,
            title = { Text("Kitap ekle", color = Pal.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Kitap adı") }, singleLine = true)
                    OutlinedTextField(total, { total = it.filter(Char::isDigit) }, label = { Text("Sayfa sayısı") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = {
                TextButton(enabled = name.isNotBlank() && (total.toIntOrNull() ?: 0) > 0, onClick = {
                    store.saveBooks(store.books() + Book(name.trim(), 0, total.toInt()))
                    adding = false; v++
                }) { Text("Ekle", color = Pal.Gold) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Vazgeç", color = Pal.Muted) } },
        )
    }
}

// ---------------------------------------------------------------- Muhasebe

@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.clip(CircleShape).background(Pal.Card).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            Box(Modifier.clip(CircleShape).background(if (selected == i) Pal.Gold else Color.Transparent)
                .clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 7.dp)) {
                Text(label, color = if (selected == i) Pal.Ground else Pal.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Muhasebe holds the overview and, like Vakit, the kaza list. */
@Composable
fun MuhasebeScreen() {
    var section by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Muhasebe") { Segmented(listOf("Özet", "Kaza"), section) { section = it } }
        Box(Modifier.weight(1f)) { if (section == 0) MuhasebeOzet() else KazaScreen(embedded = true) }
    }
}

@Composable
private fun MuhasebeOzet() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    if (!store.hasLocation) return NeedLocation()
    var month by remember { mutableStateOf(true) }
    val now = System.currentTimeMillis()
    val today = LocalDate.now()
    val prayed = store.prayed()
    val log = store.readLog()
    val first = store.firstDay
    val days = if (month) 30L else 7L

    // Per-day prayer slots are needed several times; compute each day once.
    val slotCache = remember { mutableMapOf<LocalDate, List<Slot>>() }
    fun daySlots(d: LocalDate) = slotCache.getOrPut(d) { slots(store.lat, store.lng, d) }
    fun counted(s: Slot) = s.key in prayed || (s.end <= now && store.tracked(s))

    val perVakit = Vakit.entries.associateWith { intArrayOf(0, 0) } // [due, kept]
    for (i in 0 until days) {
        val d = today.minusDays(i)
        if (d.isBefore(first)) break
        for (s in daySlots(d)) if (counted(s)) {
            perVakit[s.vakit]!![0]++
            if (s.key in prayed) perVakit[s.vakit]!![1]++
        }
    }
    val due = perVakit.values.sumOf { it[0] }; val kept = perVakit.values.sumOf { it[1] }
    fun fullDay(d: LocalDate) = daySlots(d).all { it.key in prayed || !store.tracked(it) }
    var streak = 0
    // today counts once all five are prayed; until then the streak runs up to yesterday
    var day = if (fullDay(today)) today else today.minusDays(1)
    while (!day.isBefore(first) && fullDay(day)) { streak++; day = day.minusDays(1) }
    var longest = 0; var run = 0; var d0 = first
    while (!d0.isAfter(today)) { if (fullDay(d0)) { run++; longest = max(longest, run) } else run = 0; d0 = d0.plusDays(1) }
    val pages = (0 until days).sumOf { log[today.minusDays(it).toString()] ?: 0 }
    val rate = if (due == 0) 0f else kept.toFloat() / due
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val ringAnim by animateFloatAsState(if (shown) rate else 0f, tween(1000), label = "rate")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (month) "Son 30 gün" else "Son 7 gün", Modifier.weight(1f), color = Pal.Muted, fontSize = 13.sp)
            Segmented(listOf("Hafta", "Ay"), if (month) 1 else 0) { month = it == 1 }
        }
        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            val w = 6.dp.toPx()
                            drawArc(Pal.Track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                            drawArc(Pal.Gold, -90f, 360f * ringAnim, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                        }
                        Text(if (due == 0) "—" else "%${(rate * 100).roundToInt()}", color = Pal.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text("Vaktinde", color = Pal.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        Text("$kept / $due vakit", color = Pal.Muted, fontSize = 12.sp)
                    }
                }
            }
            InfoCard(Modifier.weight(1f)) {
                Text("Seri", color = Pal.Muted, fontSize = 12.sp)
                Text("$streak gün", style = NumStyle, fontSize = 32.sp, color = Pal.Text)
                Text("En uzun: $longest gün", color = Pal.Muted, fontSize = 12.sp)
            }
        }

        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp).fillMaxWidth()) {
            Row { Text("Son 5 hafta", Modifier.weight(1f), color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp); Text("günde kılınan vakit", color = Pal.Muted, fontSize = 13.sp) }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz").forEach { Text(it, Modifier.weight(1f), color = Pal.Muted, fontSize = 10.sp, textAlign = TextAlign.Center) } }
            val start = today.minusDays((today.dayOfWeek.value - 1 + 28).toLong())
            for (w in 0 until 5) {
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (dow in 0 until 7) {
                        val d = start.plusDays((w * 7 + dow).toLong())
                        val color = if (d.isAfter(today) || d.isBefore(first)) Pal.Track.copy(alpha = .4f) else {
                            val n = daySlots(d).count { it.key in prayed }
                            // bronze to gold: mixing navy with gold directly gives grey
                            listOf(Pal.Track, Color(0xFF4A3D27), Color(0xFF6E5A35), Color(0xFF977A45), Color(0xFFC29B5A), Pal.Gold)[n]
                        }
                        Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(color))
                    }
                }
            }
        }

        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp).fillMaxWidth()) {
            Text("Vakit vakit", color = Pal.Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            perVakit.forEach { (vakit, c) ->
                val r = if (c[0] == 0) 0f else c[1].toFloat() / c[0]
                val anim by animateFloatAsState(if (shown) r else 0f, tween(900), label = "vakit")
                Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(vakit.ad, Modifier.width(56.dp), color = Pal.Text, fontSize = 13.sp)
                    Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Pal.Track)) {
                        Box(Modifier.fillMaxWidth(anim).fillMaxHeight().clip(CircleShape).background(if (r < 0.8f) Pal.Warn else Pal.Gold))
                    }
                    Text(if (c[0] == 0) "—" else "%${(r * 100).roundToInt()}", Modifier.width(44.dp), color = Pal.Muted, fontSize = 13.sp, textAlign = TextAlign.End)
                }
            }
        }

        val worst = perVakit.filter { it.value[0] >= 3 }.minByOrNull { it.value[1].toFloat() / it.value[0] }
        val worstRate = worst?.let { it.value[1].toFloat() / it.value[0] } ?: 1f
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(Pal.Gold.copy(alpha = .1f)).border(1.dp, Pal.Gold.copy(alpha = .3f), RoundedCornerShape(18.dp)).padding(16.dp)) {
            Text(
                when {
                    due < 5 -> "Birkaç gün kullandıktan sonra burada sana özel bir değerlendirme göreceksin."
                    worst != null && worstRate < 0.85f -> "En çok ${worst.key.ad.lowercase(java.util.Locale("tr"))} namazını kaçırıyorsun. O vakitte biraz daha dikkatli ol, hatırlatmalara kulak ver."
                    else -> "Maşallah, bu dönem vakitlerin çoğunu vaktinde kıldın. Böyle devam."
                }, color = Pal.Text, fontSize = 14.sp, lineHeight = 20.sp,
            )
        }

        Row(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp, bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoCard(Modifier.weight(1f)) { Text("Okunan sayfa", color = Pal.Muted, fontSize = 12.sp); Text("$pages", style = NumStyle, fontSize = 28.sp, color = Pal.Text) }
            InfoCard(Modifier.weight(1f)) { Text("Bu ay kılınan kaza", color = Pal.Muted, fontSize = 12.sp); Text("${store.kazaPaidThisMonth()}", style = NumStyle, fontSize = 28.sp, color = Pal.Text) }
        }
    }
}
