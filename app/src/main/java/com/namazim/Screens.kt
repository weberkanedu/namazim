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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
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
import androidx.compose.ui.graphics.StrokeJoin
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.*

@Composable
fun NeedLocation() = Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
    Text("Önce Namaz sekmesinden konumunu belirle.", color = Pal.Muted, textAlign = TextAlign.Center)
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    Column(
        modifier
            .shadow(14.dp, RoundedCornerShape(22.dp), clip = false, ambientColor = Color.Black.copy(alpha = .35f), spotColor = Color.Black.copy(alpha = .45f))
            .clip(RoundedCornerShape(22.dp))
            .background(Pal.Card)
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = .05f), Color.White.copy(alpha = .012f), Color.Transparent),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = .055f), RoundedCornerShape(22.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        content = content,
    )

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
    StoreRev.n.let { } // refresh when any setting or count changes
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    if (!store.hasLocation) return NeedLocation()
    val qibla = remember { qiblaDegrees(store.lat, store.lng) }
    val km = remember { distanceKm(store.lat, store.lng) }
    val qTime = remember { qiblaTime(store.lat, store.lng, qibla) }
    var heading by remember { mutableFloatStateOf(0f) }
    var pitchDeg by remember { mutableFloatStateOf(0f) }
    var rollDeg by remember { mutableFloatStateOf(0f) }
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
                pitchDeg = Math.toDegrees(ori[1].toDouble()).toFloat()
                rollDeg = Math.toDegrees(ori[2].toDouble()).toFloat()
            }
            override fun onAccuracyChanged(s: Sensor?, a: Int) {}
        }
        sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    val hdg = SkyDebug.kible ?: heading
    val diff = (((qibla - hdg + 540) % 360) - 180).toFloat() // + means turn right
    val aligned = abs(diff) < 4
    // face-up on a table is pitch -90°, roll 0; anything else means the dial is tilted
    val level = abs(pitchDeg + 90f) < 7f && abs(rollDeg) < 7f
    val okGreen = Color(0xFF6BD6A0)
    LaunchedEffect(aligned, level) {
        if (aligned && level) ctx.getSystemService(VibratorManager::class.java).defaultVibrator
            .vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK))
    }
    val glow by animateFloatAsState(if (aligned) 1f else 0.15f, tween(400), label = "glow")
    val measurer = rememberTextMeasurer()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScreenTitle("Kıble", store.city.ifEmpty { null }) {
            Text(if (level) "Telefon düz" else "Telefonu yatay tut", color = if (level) okGreen else Pal.Muted,
                fontSize = 12.sp, textAlign = TextAlign.End)
        }
        Box(Modifier.fillMaxWidth().height(340.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(340.dp).background(Brush.radialGradient(
                listOf((if (aligned) okGreen else Pal.Gold).copy(alpha = 0.30f * glow), Color.Transparent))))
            Canvas(Modifier.size(300.dp)) {
                val c = center; val r = size.minDimension / 2
                // 3-D dial: soft shadow under it, a bezel with a light edge, then the face
                drawOval(Color(0x66000000), topLeft = Offset(c.x - r * 1.02f, c.y + r * 0.55f),
                    size = Size(r * 2.04f, r * 0.62f))
                drawCircle(Brush.verticalGradient(listOf(Color(0xFF243550), Color(0xFF0A1220)), startY = c.y - r, endY = c.y + r), r)
                drawCircle(Color(0x33FFFFFF), r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
                drawCircle(Color(0xFF0B1424), r - 9.dp.toPx())
                drawCircle(Pal.Text.copy(alpha = .07f), r - 9.dp.toPx(), style = Stroke(1.dp.toPx()))

                rotate(-hdg, c) {
                    for (i in 0 until 72) {
                        val major = i % 6 == 0
                        rotate(i * 5f, c) {
                            drawLine(
                                if (major) Pal.Text.copy(alpha = .75f) else Pal.Text.copy(alpha = .28f),
                                Offset(c.x, c.y - r + 18.dp.toPx()),
                                Offset(c.x, c.y - r + (if (major) 31.dp.toPx() else 24.dp.toPx())),
                                if (major) 2.5f else 1.5f,
                            )
                        }
                    }
                    listOf("K" to 0f, "D" to 90f, "G" to 180f, "B" to 270f).forEach { (t, a) ->
                        val l = measurer.measure(t, TextStyle(color = if (t == "K") Pal.Warn else Pal.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
                        val rr = r - 82.dp.toPx()
                        val p = Offset(c.x + rr * sin(Math.toRadians(a.toDouble())).toFloat(), c.y - rr * cos(Math.toRadians(a.toDouble())).toFloat())
                        drawText(l, topLeft = Offset(p.x - l.size.width / 2, p.y - l.size.height / 2))
                    }
                    // the Kaaba marker on the rim
                    rotate(qibla.toFloat(), c) {
                        val s = 20.dp.toPx()
                        drawRoundRect(if (aligned) okGreen else Color(0xFF141414), Offset(c.x - s / 2, c.y - r + 34.dp.toPx()), Size(s, s * 0.9f), androidx.compose.ui.geometry.CornerRadius(4f))
                        drawRect(if (aligned) Color.White.copy(alpha = .85f) else Pal.Gold,
                            Offset(c.x - s / 2, c.y - r + 34.dp.toPx() + s * 0.22f), Size(s, s * 0.16f))
                    }
                }
                if (!aligned) {
                    val inset = 22.dp.toPx()
                    drawArc(Pal.Gold, -90f, diff, false, Offset(inset, inset), Size(size.width - 2 * inset, size.height - 2 * inset),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                } else {
                    drawCircle(okGreen, r - 3.dp.toPx(), style = Stroke(3.dp.toPx()))
                }
                // needle from the centre to the rim, with a small tail
                val nw = 6.dp.toPx()
                val needle = Path().apply {
                    moveTo(c.x, c.y - r + 38.dp.toPx())
                    lineTo(c.x + nw, c.y + 6.dp.toPx())
                    lineTo(c.x, c.y + 14.dp.toPx())
                    lineTo(c.x - nw, c.y + 6.dp.toPx())
                    close()
                }
                drawPath(needle, if (aligned) okGreen else Pal.Text)
                drawCircle(if (aligned) okGreen else Pal.Text, 3.dp.toPx(), c)
                drawCircle(Color(0xFF0B1424), r * 0.36f)
                drawCircle(Pal.Text.copy(alpha = .05f), r * 0.36f, style = Stroke(1.dp.toPx()))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${hdg.roundToInt()}°", style = NumStyle, fontSize = 38.sp,
                    color = if (aligned) okGreen else Pal.Text)
                Text("yönün", fontSize = 11.sp, color = Pal.Muted)
                // little spirit level: bubble centres when the phone lies flat
                Canvas(Modifier.padding(top = 8.dp).size(96.dp, 14.dp)) {
                    drawRoundRect(Pal.Text.copy(alpha = .10f), size = size, cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                    val bx = (size.width / 2) + (rollDeg.coerceIn(-25f, 25f) / 25f) * (size.width / 2 - 9.dp.toPx())
                    drawCircle(if (level) okGreen else Pal.Muted, 5.dp.toPx(), Offset(bx, size.height / 2))
                }
            }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                when {
                    aligned && level -> "Kıbleye döndün"
                    aligned -> "Yön doğru · telefonu düz tut"
                    diff > 0 -> "Sağa ${diff.roundToInt()}° dön"
                    else -> "Sola ${(-diff).roundToInt()}° dön"
                },
                color = if (aligned) okGreen else Pal.Text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
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
    StoreRev.n.let { } // refresh when any setting or count changes
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var v by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) { store.settleKaza(); v++ }
    // reading v makes every value below refresh after a change
    val total = v.let { KAZA_KEYS.sumOf { store.kaza(it.first) } }
    val paid = v.let { store.kazaPaidThisMonth() }
    val rate = v.let { store.kazaRate }
    val ring by animateFloatAsState(if (paid + total == 0) 1f else paid.toFloat() / (paid + total), tween(700), label = "ring")

    // yanlış dokunuşu geri almak için: her işlemden sonra kısa bir "Geri al" fırsatı
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun undoable(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val res = snackbar.showSnackbar(message, actionLabel = "Geri al", duration = SnackbarDuration.Long)
            if (res == SnackbarResult.ActionPerformed) { undo(); v++ }
        }
    }
    Box(Modifier.fillMaxSize()) {
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
                        onClick = {
                            store.payKaza(key); v++
                            undoable("$name kazası kılındı") { store.unpayKaza(key) }
                        }, contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Pal.Gold, contentColor = Pal.Ground),
                    ) { Text("Kıldım", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.width(6.dp))
                    OutlinedIconButton(onClick = {
                            store.addKaza(key, 1); v++
                            undoable("$name kazası eklendi") { store.addKaza(key, -1) }
                        }, modifier = Modifier.size(36.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Pal.Border)) {
                        Icon(Icons.Default.Add, "$name kazası ekle", tint = Pal.Text, modifier = Modifier.size(18.dp))
                    }
                }
                HorizontalDivider(color = Pal.Line.copy(alpha = .5f))
            }
        }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) { data ->
            Snackbar(snackbarData = data, containerColor = Pal.Card, contentColor = Pal.Text, actionColor = Pal.Gold)
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
    StoreRev.n.let { } // refresh when any setting or count changes
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
fun Segmented(options: List<String>, selected: Int, modifier: Modifier = Modifier, stretch: Boolean = false, onSelect: (Int) -> Unit) {
    Row(modifier.clip(RoundedCornerShape(24.dp)).background(Pal.Card).padding(3.dp)) {
        options.forEachIndexed { i, label ->
            Box((if (stretch) Modifier.weight(1f) else Modifier).clip(RoundedCornerShape(20.dp))
                .background(if (selected == i) Pal.Gold else Color.Transparent)
                .pressable { onSelect(i) }.padding(horizontal = if (stretch) 0.dp else 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center) {
                Text(label, color = if (selected == i) Pal.Ground else Pal.Muted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
/** Muhasebe holds the overview (like Vakit's "Özet"), the history and the kaza list. */
@Composable
fun MuhasebeScreen() {
    var section by remember { mutableIntStateOf(SkyDebug.mSection) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Muhasebe")
        Segmented(listOf("Özet", "Gidişat", "Kaza"), section,
            Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp).fillMaxWidth(), stretch = true) { section = it }
        Box(Modifier.weight(1f).clipToBounds()) {
            when (section) {
                0 -> MuhasebeOzet()
                1 -> MuhasebeGidisat()
                else -> KazaScreen(embedded = true)
            }
        }
    }
}

/** "Bugün ne durumdayım": dört halka (namaz, okuma, hatim, kaza) + tek cümlelik değerlendirme. */
@Composable
private fun MuhasebeOzet() {
    StoreRev.n.let { } // refresh when any setting or count changes
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    if (!store.hasLocation) return NeedLocation()
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    val first = store.firstDay
    val log = store.readLog()
    val prayed = store.prayed()
    val todaySlots = slots(store.lat, store.lng, today)
    val prayedToday = todaySlots.count { it.key in prayed }
    val pagesToday = log[today.toString()] ?: 0
    val page = store.quranPage
    val totalKaza = Vakit.entries.sumOf { store.kaza(it) } + store.kaza("VITIR")

    val slotCache = remember { mutableMapOf<LocalDate, List<Slot>>() }
    fun daySlots(d: LocalDate) = slotCache.getOrPut(d) { slots(store.lat, store.lng, d) }

    // son 30 gün ve tüm zamanlar: kaç vakit geldi, kaçı kılındı, kaç gün tamdı
    fun stats(days: Int): Triple<Int, Int, Int> {
        var due = 0; var kept = 0; var full = 0
        for (i in 0 until days) {
            val d = today.minusDays(i.toLong())
            if (d.isBefore(first)) break
            val list = daySlots(d)
            due += list.count { it.end <= now }
            kept += list.count { it.key in prayed }
            if (list.all { it.key in prayed || it.end > now }) full++
        }
        return Triple(due, kept, full)
    }
    val (due30, kept30, full30) = stats(30)
    val totalDays = (today.toEpochDay() - first.toEpochDay()).toInt() + 1
    val (dueAll, keptAll, _) = stats(totalDays.coerceAtMost(400))
    val rate30 = if (due30 == 0) 0f else kept30.toFloat() / due30
    val rateAll = if (dueAll == 0) 0f else keptAll.toFloat() / dueAll

    // ne zaman kılıyorsun: vaktin başı / ortası / sonu
    var sumPos = 0f; var nPos = 0
    for (d in listOf(today, today.minusDays(1), today.minusDays(2))) {
        for (s in daySlots(d)) {
            val at = store.prayedAt(s.key) ?: continue
            if (s.end <= s.start) continue
            sumPos += ((at - s.start).toFloat() / (s.end - s.start)).coerceIn(0f, 1f); nPos++
        }
    }
    val insight = when {
        nPos == 0 -> "Birkaç vakit işaretledikçe burada nasıl kıldığını göreceksin."
        sumPos / nPos < 0.34f -> "Namazlarını çoğunlukla vaktin başında kılıyorsun."
        sumPos / nPos < 0.67f -> "Namazlarını çoğunlukla vaktin ortasında kılıyorsun."
        else -> "Namazlarını çoğunlukla vaktin sonuna doğru kılıyorsun."
    }

    // okuma hızı → mushaf ne zaman biter
    val perDay = (0 until 7).sumOf { log[today.minusDays(it.toLong()).toString()] ?: 0 } / 7f
    val eta = if (perDay <= 0.05f || page == 0) null else today.plusDays(((604 - page) / perDay).toLong().coerceAtLeast(1))
    val monthsLeft = if (store.kazaRate <= 0 || totalKaza == 0) null else totalKaza / (store.kazaRate * 30f)

    // radar: beş eksen (namaz, sayfa, hatim, kaza, hedef) — halkaların yerine
    val kazaProg = run {
        val paid = store.kazaPaidThisMonth(); val t = totalKaza
        if (paid + t == 0) 0.5f else (paid.toFloat() / (paid + t)).coerceIn(0f, 1f)
    }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val anim by animateFloatAsState(if (shown) 1f else 0f, tween(900), label = "radar")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp).fillMaxWidth()) {
            RadarChart(
                axes = listOf(
                    "Namaz" to (prayedToday / 5f),
                    "Sayfa" to (if (store.readGoal == 0) 0f else pagesToday.toFloat() / store.readGoal).coerceIn(0f, 1f),
                    "Hatim" to (page / 604f).coerceIn(0f, 1f),
                    "Kaza" to kazaProg,
                    "Hedef" to (full30 / 30f).coerceIn(0f, 1f),
                ),
                colors = listOf(Pal.Gold, Color(0xFF6BC5B0), Color(0xFF8AA6D8), Pal.Warn, Color(0xFFC29B5A)),
                anim = anim,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(
                    "$prayedToday/5 namaz" to Pal.Gold,
                    "$pagesToday/${store.readGoal} sayfa" to Color(0xFF6BC5B0),
                    "${page}/604 hatim" to Color(0xFF8AA6D8),
                    "$totalKaza kaza" to Pal.Warn,
                ).forEach { (label, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(5.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(4.dp))
                        Text(label, color = Pal.Muted, fontSize = 10.5.sp)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text("Hedefe ulaşılan gün: $full30/30", color = Pal.Muted, fontSize = 12.sp)
        }

        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Pal.Gold.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Pal.Gold, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(insight, Modifier.weight(1f), color = Pal.Text, fontSize = 13.5.sp, lineHeight = 19.sp)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("%${(rate30 * 100).roundToInt()}", style = NumStyle, fontSize = 30.sp, color = Pal.Text)
                    Text("Son 30 gün", color = Pal.Muted, fontSize = 11.sp)
                }
                Column(Modifier.weight(1f)) {
                    Text("%${(rateAll * 100).roundToInt()}", style = NumStyle, fontSize = 30.sp, color = Pal.Text)
                    Text("Tüm zamanlar", color = Pal.Muted, fontSize = 11.sp)
                }
            }
        }

        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HATIM TAKİBİ", Modifier.weight(1f), color = Pal.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                Text(if (page >= 604) "BİTTİ" else "DEVAM EDİYOR", color = Color(0xFF6BC5B0), fontSize = 11.sp, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 6.dp.toPx()
                        drawArc(Pal.Track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                        drawArc(Color(0xFF8AA6D8), -90f, 360f * (page / 604f) * anim, false, Offset(w / 2, w / 2),
                            Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Text("%${(page * 100 / 604)}", color = Pal.Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("$page / 604 sayfa · ${sureOf(page)}", color = Pal.Text, fontSize = 13.sp)
                    Text("${store.hatim} hatim tamam", color = Pal.Muted, fontSize = 12.sp)
                    Text(
                        if (eta != null) "Bu hızla ~${eta.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("tr")))}"
                        else "Günde birkaç sayfa okuyunca tarih burada görünecek",
                        color = Pal.Muted, fontSize = 11.5.sp,
                    )
                }
            }
        }

        InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp).fillMaxWidth()) {
            Text("KAZA NAMAZI", color = Pal.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 6.dp.toPx()
                        drawArc(Pal.Track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                        val prog = if (totalKaza == 0) 1f else (store.kazaPaidThisMonth().toFloat() / (store.kazaPaidThisMonth() + totalKaza)).coerceIn(0f, 1f)
                        drawArc(Pal.Warn, -90f, 360f * prog * anim, false, Offset(w / 2, w / 2),
                            Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Text("${totalKaza}", color = Pal.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("$totalKaza vakit kaldı", style = NumStyle, fontSize = 26.sp, color = Pal.Text)
                    Text("Bu ay ${store.kazaPaidThisMonth()} kaza kıldın", color = Pal.Muted, fontSize = 12.sp)
                    Text(
                        if (monthsLeft != null) "Bu hızla ~${monthsLeft.roundToInt()} ay içinde tamamlanır, inşallah"
                        else "Kaza kalmadı, elhamdülillah",
                        color = Pal.Muted, fontSize = 11.5.sp,
                    )
                }
            }
        }
    }
}
/** Gidişat: dönem seçmeli ısı haritası, bugüne kadarki toplamlar ve türlere göre özet. */
@Composable
private fun MuhasebeGidisat() {
    val ctx = LocalContext.current
    StoreRev.n.let { }
    val store = remember { Store(ctx) }
    if (!store.hasLocation) return NeedLocation()
    var period by remember { mutableIntStateOf(SkyDebug.gPeriod) } // 0 hafta, 1 ay, 2 yıl, 3 tümü
    val today = LocalDate.now()
    val first = store.firstDay
    val prayed = store.prayed()
    val log = store.readLog()
    val tr = java.util.Locale("tr")

    data class Bucket(
        val start: LocalDate, val days: Int, val elapsed: Int, val counts: IntArray, val pages: IntArray,
        val any: Int, val full: Int, val total: Int, val prevAny: Int,
        val fracs: Map<String, List<Float>>, val mins: Map<String, List<Int>>,
        val allNamaz: Int, val allSayfa: Int, val allHatim: Int, val allKaza: Int, val fullYear: Boolean,
        val kazaDaily: IntArray,
    )

    // bir yıllık vakit hesabı birkaç yüz ms sürüyor: arka planda hesapla, bu arada spinner göster
    var bucket by remember(period) { mutableStateOf<Bucket?>(null) }
    LaunchedEffect(period, prayed.size, log.size) {
        bucket = withContext(Dispatchers.Default) { run {
            val fullYear = period == 2
            val start = if (fullYear) LocalDate.of(today.year, 1, 1) else {
                val len = when (period) { 0 -> 7; 1 -> 30; else -> (today.toEpochDay() - first.toEpochDay()).toInt() + 1 }
                today.minusDays((len - 1).toLong()).let { if (it.isBefore(first)) first else it }
            }
            val lastDay = if (fullYear) LocalDate.of(today.year, 12, 31) else today
            val n = (lastDay.toEpochDay() - start.toEpochDay()).toInt() + 1
            val elapsed = ((today.toEpochDay() - start.toEpochDay()).toInt() + 1).coerceAtMost(n)

            val counts = IntArray(n); val pages = IntArray(n)
            var any = 0; var full = 0; var total = 0
            for (i in 0 until elapsed) {
                val d = start.plusDays(i.toLong())
                val c = slots(store.lat, store.lng, d).count { it.key in prayed }
                counts[i] = c; pages[i] = log[d.toString()] ?: 0
                if (c > 0) any++
                if (c >= 5) full++
                total += c
            }
            // önceki dönem: aynı sayıda gün, bu dönemin hemen öncesi
            var prevAny = 0
            for (i in 0 until elapsed) {
                val d = start.minusDays((elapsed - i).toLong())
                if (d.isBefore(first)) break
                if (slots(store.lat, store.lng, d).any { it.key in prayed }) prevAny++
            }
            // namazları ne zaman kılıyoruz: her işaretin vakit penceresindeki yeri
            val fracs = mutableMapOf<String, MutableList<Float>>()
            val mins = mutableMapOf<String, MutableList<Int>>()
            for (i in 0 until elapsed) {
                val d = start.plusDays(i.toLong())
                for (sl in slots(store.lat, store.lng, d)) {
                    val at = store.prayedAt(sl.key) ?: continue
                    if (at < sl.start || sl.end <= sl.start) continue
                    fracs.getOrPut(sl.vakit.name) { mutableListOf() }
                        .add(((at - sl.start).toFloat() / (sl.end - sl.start)).coerceIn(0f, 1f))
                    mins.getOrPut(sl.vakit.name) { mutableListOf() }.add(((at - sl.start) / 60_000L).toInt())
                }
            }
            // bugüne kadar (tüm zamanlar) toplamlar
            var allNamaz = 0; var allSayfa = 0
            var d0 = first
            while (!d0.isAfter(today)) {
                allNamaz += slots(store.lat, store.lng, d0).count { it.key in prayed }
                allSayfa += log[d0.toString()] ?: 0
                d0 = d0.plusDays(1)
            }
            val allKaza = Vakit.entries.sumOf { store.kaza(it) } + store.kaza("VITIR")
            val kLog = store.kazaLog()
            val kazaDaily = IntArray(n) { i -> kLog[start.plusDays(i.toLong()).toString()] ?: 0 }
            Bucket(start, n, elapsed, counts, pages, any, full, total, prevAny, fracs, mins,
                allNamaz, allSayfa, store.hatim, allKaza, fullYear, kazaDaily)
        } }
    }
    val data = bucket
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pal.Gold) }
        return
    }

    val monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMM", tr)
    val endOfRange = if (data.fullYear) LocalDate.of(today.year, 12, 31) else today
    val range = "${data.start.format(java.time.format.DateTimeFormatter.ofPattern("d MMM", tr))} – " +
        endOfRange.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", tr))
    val heatRamp = listOf(Pal.Track, Color(0xFF4A3D27), Color(0xFF6E5A35), Color(0xFF977A45), Color(0xFFC29B5A), Pal.Gold)
    val futureCell = Pal.Track.copy(alpha = .45f)
    val emptyCell = Color(0xFF39465F)

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Segmented(
            listOf("Hafta", "Ay", "Yıl", "Tümü"), period,
            Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp).fillMaxWidth(), stretch = true,
        ) { period = it }
        Text(range, Modifier.padding(start = 24.dp, top = 10.dp), color = Pal.Muted, fontSize = 12.sp)

        // ---- Bugüne kadar: yalnızca "Tümü"de (tüm zamanların toplamı)
        if (period == 3) {
            InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp).fillMaxWidth()) {
                Text("BUGÜNE KADAR", color = Pal.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf(
                        "${data.allNamaz}" to "namaz" to Color(0xFF6BC5B0),
                        "${data.allSayfa}" to "sayfa" to Color(0xFF8AA6D8),
                        "${data.allHatim}" to "hatim" to Pal.Gold,
                        "${data.allKaza}" to "kaza" to Pal.Warn,
                    ).forEach { spec ->
                        val (pair, color) = spec
                        val (value, unit) = pair
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(value, style = NumStyle, fontSize = 26.sp, color = color)
                            Text(unit, color = Pal.Muted, fontSize = 10.sp, letterSpacing = .5.sp)
                        }
                    }
                }
            }
        }

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp)) {
            Text(
                "${data.elapsed} günün ${data.any}'inde ibadet işaretledin",
                style = NumStyle, fontSize = 26.sp, lineHeight = 30.sp, color = Pal.Text,
            )
            Spacer(Modifier.height(4.dp))
            Text("önceki dönemde ${data.prevAny} gün", color = Pal.Muted, fontSize = 12.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                when {
                    data.any == 0 -> "İlk işareti koyunca burada birikmeye başlayacak."
                    data.any > data.prevAny -> "Bu dönem öncekinden daha istikrarlısın — Allah kabul etsin."
                    data.any < data.prevAny -> "Bu dönem biraz aksadı. Bir vakit bile sayılır, bugün başla."
                    else -> "Geçen dönemle aynı tempodasın. Bir vakit daha ekleyelim."
                },
                color = Pal.Text.copy(alpha = .85f), fontSize = 14.sp, lineHeight = 20.sp,
            )
        }

        // ---- harita: Hafta/Ay takvim ızgarası, Yıl tam takvim. Tümü'de harita yok.
        if (period != 3) {
            InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp).fillMaxWidth()) {
                Text(
                    if (period == 2) "NAMAZ HARİTASI · ${today.year}" else "NAMAZ HARİTASI",
                    color = Pal.Muted, fontSize = 11.sp, letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(10.dp))
                if (period == 2) {
                    // yıl: 53 hafta × 7 gün, ay etiketleriyle
                    BoxWithConstraints {
                        val gap = 1.dp
                        val cols = (data.days + 6) / 7
                        // %50 daha büyük kutular; sığmazsa sağa sola kaydırılır
                        val fitW = (maxWidth - gap * (cols - 1).coerceAtLeast(0)) / cols.coerceAtLeast(1)
                        val cellW = fitW * 1.5f
                        val cellH = cellW * 1.25f
                        val totalW = cellW * cols + gap * (cols - 1)
                        val hScroll = rememberScrollState()
                        LaunchedEffect(totalW) { hScroll.scrollTo(hScroll.maxValue) }
                        val firstDow = data.start.dayOfWeek.value - 1
                        Box(Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
                        Column(Modifier.width(totalW)) {
                        Row(Modifier.fillMaxWidth().height(16.dp)) {
                            for (w in 0 until cols) {
                                val d = data.start.plusDays((w * 7 - firstDow).toLong())
                                val prev = data.start.plusDays(((w - 1) * 7 - firstDow).toLong())
                                Box(Modifier.weight(1f)) {
                                    if (d.monthValue != prev.monthValue && (d.monthValue % 2 == 1)) {
                                        Text(
                                            d.format(monthFmt),
                                            Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                                            color = Pal.Muted, fontSize = 8.sp, maxLines = 1, softWrap = false,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Column {
                            for (row in 0 until 7) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = if (row == 0) 0.dp else gap),
                                    horizontalArrangement = Arrangement.spacedBy(gap, Alignment.CenterHorizontally),
                                ) {
                                    for (w in 0 until cols) {
                                        val idx = w * 7 - firstDow + row
                                        val c = if (idx in data.counts.indices) data.counts[idx] else -1
                                        val future = idx >= data.elapsed || idx < 0
                                        val todayCell = idx == data.elapsed - 1
                                        Box(
                                            Modifier.size(cellW, cellH).clip(RoundedCornerShape(2.dp))
                                                .background(if (future) futureCell else if (c == 0) emptyCell else heatRamp[c.coerceIn(0, 5)])
                                                .then(
                                                    if (todayCell) Modifier.border(1.dp, Pal.Text.copy(alpha = .7f), RoundedCornerShape(2.dp))
                                                    else Modifier,
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                        }
                        }
                    }
                } else {
                    // hafta / ay: gerçek takvim ızgarası (7 sütun), gün numaralarıyla
                    BoxWithConstraints {
                        val gap = 4.dp
                        val cellW = (maxWidth - gap * 6) / 7
                        val cellH = cellW * 0.5f
                        val offset = data.start.dayOfWeek.value - 1
                        val rows = (offset + data.days + 6) / 7
                        Column(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                                TR_DAYS.forEach {
                                    Text(it, Modifier.weight(1f), color = Pal.Muted, fontSize = 9.sp, textAlign = TextAlign.Center)
                                }
                            }
                            for (r in 0 until rows) {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = gap),
                                    horizontalArrangement = Arrangement.spacedBy(gap),
                                ) {
                                    for (c in 0 until 7) {
                                        val idx = r * 7 + c - offset
                                        val inRange = idx in 0 until data.days
                                        val n = if (inRange && idx < data.elapsed) data.counts[idx] else -1
                                        val todayCell = inRange && idx == data.elapsed - 1
                                        Box(
                                            Modifier.size(cellW, cellH).clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    when {
                                                        !inRange -> Color.Transparent
                                                        idx >= data.elapsed -> futureCell
                                                        else -> heatRamp[n.coerceIn(0, 5)]
                                                    },
                                                )
                                                .then(
                                                    if (todayCell) Modifier.border(1.dp, Pal.Text.copy(alpha = .7f), RoundedCornerShape(8.dp))
                                                    else Modifier,
                                                ),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            if (inRange) Text(
                                                "${data.start.plusDays(idx.toLong()).dayOfMonth}",
                                                color = if (n >= 3) Pal.Ground else Pal.Muted,
                                                fontSize = 10.sp,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("hiç", color = Pal.Muted, fontSize = 10.sp)
                    heatRamp.forEach { Box(Modifier.size(9.dp).clip(CircleShape).background(it)) }
                    Text("beş vakit", color = Pal.Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (data.full == 0) "${data.elapsed} günün hiçbirinde beş vaktin tamamını işaretlemedin." else "${data.elapsed} günün ${data.full}'inde beş vaktin tamamını işaretledin.",
                    color = Pal.Muted, fontSize = 12.sp,
                )
            }
        }

        // Tümü sekmesinde tür tablosu yok (yalnızca toplamlar ve zamanlama)
        if (period != 3) InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp).fillMaxWidth()) {
            Text("TÜRLERE GÖRE", color = Pal.Muted, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(8.dp))
            val types = listOf(
                Triple("Namaz", data.total to "vakit", Color(0xFF6BC5B0)),
                Triple("Okuma", data.pages.sum() to "sayfa", Color(0xFF8AA6D8)),
                Triple("Hatim", store.hatim to "hatim", Pal.Gold),
                Triple("Kaza", data.allKaza to "vakit", Pal.Warn),
            )
            types.forEach { (name, pair, color) ->
                val (value, unit) = pair
                Row(Modifier.padding(top = 12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(color))
                    Spacer(Modifier.width(10.dp))
                    Text(name, Modifier.weight(1f), color = Pal.Text, fontSize = 15.sp)
                    // mini grafik: son 21 günün 3 günlük ortalaması
                    Canvas(Modifier.size(64.dp, 24.dp)) {
                        val n = minOf(21, data.elapsed)
                        if (n > 1) {
                            val raw = when (name) {
                                "Namaz" -> IntArray(n) { data.counts[data.elapsed - n + it] }
                                "Okuma" -> IntArray(n) { data.pages[data.elapsed - n + it] }
                                // hatim: son 21 günün kümülatif okuma eğrisi
                                "Hatim" -> {
                                    var acc = 0
                                    IntArray(n) { acc += data.pages[data.elapsed - n + it]; acc }
                                }
                                else -> IntArray(n) { data.kazaDaily[data.elapsed - n + it] }
                            }
                            val vals = if (name == "Hatim") FloatArray(n) { raw[it].toFloat() } else FloatArray(n) { i ->
                                val a = raw[max(0, i - 1)]; val b = raw[i]; val c = raw[min(n - 1, i + 1)]
                                (a + b + c) / 3f
                            }
                            val maxV = (vals.maxOrNull() ?: 0f).coerceAtLeast(1f)
                            val path = Path()
                            vals.forEachIndexed { i, v ->
                                val x = size.width * i / (n - 1).toFloat()
                                val y = size.height - (size.height * v / maxV) * 0.9f
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, color, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("$value", style = NumStyle, fontSize = 22.sp, color = Pal.Text)
                    Spacer(Modifier.width(3.dp))
                    Text(unit, color = Pal.Muted, fontSize = 10.sp)
                }
            }
        }

        // ---- Namazları ne zaman kılıyoruz: yalnızca "Tümü"de (kısa dönemde ortalama yanıltır)
        if (period == 3) {
            InfoCard(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp).fillMaxWidth()) {
                Text("NAMAZLARINI NE ZAMAN KILIYORSUN", color = Pal.Muted, fontSize = 11.sp, letterSpacing = .8.sp)
                Spacer(Modifier.height(10.dp))
                val vakitColors = mapOf(
                    "SABAH" to Color(0xFF8E7CC3), "OGLE" to Color(0xFF5B9BD5), "IKINDI" to Color(0xFF9AA5B1),
                    "AKSAM" to Color(0xFFE07A4F), "YATSI" to Color(0xFF5B9BD5),
                )
                Vakit.entries.forEach { vk ->
                    val fs = data.fracs[vk.name].orEmpty()
                    val avg = if (fs.isEmpty()) null else fs.average().toFloat()
                    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vk.ad, Modifier.width(58.dp), color = Pal.Text, fontSize = 13.sp)
                        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Pal.Track)) {
                            if (avg != null) Box(
                                Modifier.fillMaxWidth(avg).fillMaxHeight().clip(CircleShape)
                                    .background(vakitColors[vk.name] ?: Pal.Gold),
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (avg == null) "—" else "${(avg * 7).coerceIn(1f, 7f).roundToInt()}/7",
                            color = if (avg == null) Pal.Muted else Pal.Text, fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text("vaktin başı → sonraki vakit", color = Pal.Muted, fontSize = 10.sp)

                HorizontalDivider(Modifier.padding(vertical = 12.dp), color = Pal.Line)
                Text("Vakit girdikten kaç dakika sonra işaretliyorsun", color = Pal.Text, fontSize = 13.sp)
                Vakit.entries.forEach { vk ->
                    val ms = data.mins[vk.name].orEmpty()
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(vk.ad, Modifier.width(58.dp), color = Pal.Text, fontSize = 13.sp)
                        val avgMin = if (ms.isEmpty()) null else ms.average().toInt()
                        Text(
                            when {
                                avgMin == null -> "—"
                                avgMin >= 60 -> "${avgMin / 60}sa ${avgMin % 60}dk"
                                else -> "${avgMin}dk"
                            },
                            Modifier.weight(1f), color = if (avgMin == null) Pal.Muted else Pal.Gold, fontSize = 15.sp,
                        )
                        Text(if (ms.isEmpty()) "" else "${ms.size} namaz", color = Pal.Muted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Not: Bu sayı bir fazilet ölçüsü değil; kendi düzenini görmen için. " +
                        "Hanefi'de her vakitte erken kılmak daha faziletli sayılsa da, niyet ve istikrar öndedir.",
                    color = Pal.Muted, fontSize = 11.sp, lineHeight = 16.sp,
                )
            }
        }
    }
}

/** Beş eksenli radar: Özet'in üst kartı (halkaların yerine). */
@Composable
private fun RadarChart(axes: List<Pair<String, Float>>, colors: List<Color>, anim: Float) {
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = Pal.Muted)
    Canvas(Modifier.fillMaxWidth().height(250.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f + 2.dp.toPx())
        val r = minOf(size.width, size.height) / 2f - 34.dp.toPx()
        val n = axes.size
        fun pt(i: Int, f: Float): Offset {
            val a = (-Math.PI / 2 + 2 * Math.PI * i / n).toFloat()
            return Offset(c.x + r * f * cos(a), c.y + r * f * sin(a))
        }
        for (ring in 1..4) {
            val path = Path()
            for (i in 0 until n) {
                val o = pt(i, ring / 4f)
                if (i == 0) path.moveTo(o.x, o.y) else path.lineTo(o.x, o.y)
            }
            path.close()
            drawPath(path, Pal.Line, style = Stroke(1.dp.toPx()))
        }
        for (i in 0 until n) drawLine(Pal.Line, c, pt(i, 1f), 1.dp.toPx())
        val poly = Path()
        axes.forEachIndexed { i, axis ->
            val o = pt(i, (axis.second * anim).coerceIn(0.03f, 1f))
            if (i == 0) poly.moveTo(o.x, o.y) else poly.lineTo(o.x, o.y)
        }
        poly.close()
        drawPath(poly, Pal.Gold.copy(alpha = .18f))
        drawPath(poly, Pal.Gold, style = Stroke(2.dp.toPx(), join = StrokeJoin.Round))
        axes.forEachIndexed { i, axis ->
            val o = pt(i, (axis.second * anim).coerceIn(0.03f, 1f))
            drawCircle(colors[i % colors.size], 4.dp.toPx(), o)
            val lp = pt(i, 1.22f)
            val layout = measurer.measure(axis.first, labelStyle)
            drawText(layout, topLeft = Offset(lp.x - layout.size.width / 2f, lp.y - layout.size.height / 2f))
        }
    }
}
/** Dokununca hafifçe küçülen tıklanabilir yüzey — hareket hissi. */
@Composable
fun Modifier.pressable(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .96f else 1f, spring(stiffness = Spring.StiffnessMedium), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = src, indication = null, onClick = onClick)
}

