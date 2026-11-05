package com.namazim

import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.VibrationEffect
import android.os.VibratorManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.draw.alpha
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
fun NeedLocation() = Box(Modifier.fillMaxSize().padding(Space.xxl), contentAlignment = Alignment.Center) {
    Text("Önce Namaz sekmesinden konumunu belirle.", color = Pal.Muted, textAlign = TextAlign.Center)
}

@Composable
private fun InfoCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) =
    AppCard(modifier, content = content)
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
fun KibleScreen(onBack: () -> Unit) {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (level) "Telefon düz" else "Telefonu yatay tut", color = if (level) okGreen else Pal.Muted,
                    fontSize = Type.label, textAlign = TextAlign.End)
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Geri", tint = Pal.Text) }
            }
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
                        val l = measurer.measure(t, TextStyle(color = if (t == "K") Pal.Warn else Pal.Muted, fontSize = Type.body2, fontWeight = FontWeight.SemiBold))
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
                Text("${hdg.roundToInt()}°", style = NumStyle, fontSize = Type.title,
                    color = if (aligned) okGreen else Pal.Text)
                Text("yönün", fontSize = Type.micro, color = Pal.Muted)
                // little spirit level: bubble centres when the phone lies flat
                Canvas(Modifier.padding(top = Space.s).size(96.dp, 14.dp)) {
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
                color = if (aligned) okGreen else Pal.Text, fontSize = Type.h2, fontWeight = FontWeight.SemiBold,
            )
            Text(if (aligned) "Telefon hafifçe titreşti" else "Altın çizgi kıbleye kalan açıyı gösterir", color = Pal.Muted, fontSize = Type.body2)
        }
        Row(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
            InfoCard(Modifier.weight(1f)) {
                Text("Kıble açısı", color = Pal.Muted, fontSize = Type.label)
                Text("${qibla.roundToInt()}°", style = NumStyle, fontSize = Type.num, color = Pal.Text)
                Text("Pusulada ${((qibla - declination + 360) % 360).roundToInt()}°", color = Pal.Muted, fontSize = Type.micro)
            }
            InfoCard(Modifier.weight(1f)) {
                Text("Kâbe'ye uzaklık", color = Pal.Muted, fontSize = Type.label)
                Text("≈ %,d km".format(java.util.Locale("tr"), (km / 10).roundToInt() * 10), style = NumStyle, fontSize = Type.num, color = Pal.Text)
            }
        }
        InfoCard(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.s, bottom = Space.l).fillMaxWidth()) {
            if (qTime != null) {
                Text("Kıble saati ${hm(qTime)}", color = Pal.Gold, fontWeight = FontWeight.SemiBold, fontSize = Type.body)
                Text("Bugün bu saatte güneş tam kıble yönünde. Pusulasız da bulabilirsin.", color = Pal.Muted, fontSize = Type.body2)
            } else {
                Text("Bugün güneş kıble yönünden geçmiyor.", color = Pal.Muted, fontSize = Type.body2)
            }
        }
    }
}

// ---------------------------------------------------------------- Kaza

private val KAZA_KEYS = listOf("SABAH" to "Sabah", "OGLE" to "Öğle", "IKINDI" to "İkindi", "AKSAM" to "Akşam", "YATSI" to "Yatsı", "VITIR" to "Vitir")

/**
 * Estimated kaza owed, in days (each day = one of every vakit, vitir included): the years between the age the debt
 * started and the age regular prayer began, minus excused days each month (özel hal) and what was already made up.
 */
fun kazaDebtDays(fromAge: Int, toAge: Int, excusedPerMonth: Int, alreadyDays: Int): Int {
    if (toAge <= fromAge) return 0
    val years = toAge - fromAge
    return (years * 365 - years * 12 * excusedPerMonth - alreadyDays).coerceAtLeast(0)
}

/**
 * "Gün gün eda": one day of kaza is one of every vakit. From what was made up ([done]) and what is still owed ([owed]),
 * per vakit, returns the full days made up and which vakit are already in for the day in progress. A vakit that was
 * never owed (no kaza either way) does not hold the day back.
 */
fun kazaRound(done: List<Int>, owed: List<Int>): Pair<Int, List<Boolean>> {
    val active = done.indices.filter { done[it] > 0 || owed[it] > 0 }
    if (active.isEmpty()) return 0 to done.map { false }
    val days = active.minOf { done[it] }
    return days to done.indices.map { it !in active || done[it] > days }
}

/** Days in a row with at least one kaza, newest first; an empty today does not break yesterday's run. */
fun kazaStreak(newestFirst: List<Int>): Int =
    newestFirst.drop(if (newestFirst.firstOrNull() == 0) 1 else 0).takeWhile { it > 0 }.size

/** Kaza takvimi: the last 22 weeks, Monday first; gold where the plan was kept, soft gold for less, empty before first use. */
@Composable
private fun KazaCalendar(today: LocalDate, first: LocalDate, newestFirst: List<Int>, rate: Int) {
    val weeks = 22
    val start = today.minusDays((today.dayOfWeek.value - 1 + (weeks - 1) * 7).toLong())
    val tr = java.util.Locale("tr")
    val monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMM", tr)
    val noData = Pal.Track.copy(alpha = .35f)
    Column(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.xl)) {
        SectionLabel("KAZA TAKVİMİ", Modifier.padding(bottom = Space.s))
        BoxWithConstraints {
            val gap = 3.dp
            val cell = (maxWidth - gap * (weeks - 1)) / weeks
            Column {
                Row(Modifier.height(14.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (w in 0 until weeks) {
                        val d = start.plusDays(w * 7L)
                        Box(Modifier.width(cell)) {
                            if (w == 0 || d.monthValue != d.minusDays(7).monthValue) Text(
                                d.format(monthFmt), Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                                color = Pal.Muted, fontSize = 9.sp, maxLines = 1, softWrap = false,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
                for (row in 0 until 7) Row(Modifier.padding(top = if (row == 0) 0.dp else gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
                    for (w in 0 until weeks) {
                        val d = start.plusDays(w * 7L + row)
                        val ago = (today.toEpochDay() - d.toEpochDay()).toInt()
                        val n = newestFirst.getOrElse(ago) { 0 }
                        val color = when {
                            ago < 0 -> Color.Transparent
                            d.isBefore(first) -> noData
                            n == 0 -> Pal.Track
                            n >= rate -> Pal.Gold
                            else -> Pal.Gold.copy(alpha = .4f)
                        }
                        Box(
                            Modifier.size(cell).clip(RoundedCornerShape(2.dp)).background(color)
                                .then(if (ago == 0) Modifier.border(1.dp, Pal.Text.copy(alpha = .8f), RoundedCornerShape(2.dp)) else Modifier),
                        )
                    }
                }
            }
        }
        Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("hiç", color = Pal.Muted, fontSize = Type.micro)
            listOf(Pal.Track, Pal.Gold.copy(alpha = .4f), Pal.Gold).forEach { Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
            Text("plan tuttu (günde $rate)", color = Pal.Muted, fontSize = Type.micro)
        }
        val kept30 = newestFirst.take(30).count { it >= rate }
        var run = 0; var longest = 0
        newestFirst.forEach { if (it > 0) { run++; longest = max(longest, run) } else run = 0 }
        Text(
            if (newestFirst.none { it > 0 }) "Kaza kıldıkça günler burada altınla dolar."
            else "Son 30 günde planı $kept30 gün tuttun; en uzun seri $longest gün.",
            Modifier.padding(top = Space.m), color = Pal.Text, fontSize = Type.body2,
        )
    }
}

/** Kaza › "Geçmişten kalan borcunu hesapla": four numbers in, a live estimate out; [onAdd] gets the days to add per vakit. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KazaDebtSheet(rate: Int, onDismiss: () -> Unit, onAdd: (Int) -> Unit) {
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var excused by remember { mutableStateOf("0") }
    var already by remember { mutableStateOf("0") }
    var unit by remember { mutableIntStateOf(0) } // 0 gün, 1 ay, 2 yıl
    val f = from.toIntOrNull(); val t = to.toIntOrNull()
    val ex = excused.toIntOrNull() ?: 0
    val al = (already.toIntOrNull() ?: 0) * listOf(1, 30, 365)[unit]
    val error = when {
        f == null || t == null -> null
        f !in 7..60 -> "Borcun başladığı yaş 7 ile 60 arasında olmalı"
        t !in 8..100 -> "Düzenli kılmaya başladığın yaş 8 ile 100 arasında olmalı"
        t <= f -> "Düzenli kılmaya başladığın yaş, borcun başladığı yaştan büyük olmalı"
        ex > 15 -> "Ayda en fazla 15 gün düşülebilir"
        else -> null
    }
    val days = if (f != null && t != null && error == null) kazaDebtDays(f, t, ex, al) else null
    val tr = java.util.Locale("tr")
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Border, focusedLabelColor = Pal.Gold,
        cursorColor = Pal.Gold, focusedTextColor = Pal.Text, unfocusedTextColor = Pal.Text, unfocusedLabelColor = Pal.Muted,
    )
    val num = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
    @Composable fun field(value: String, set: (String) -> Unit, label: String, m: Modifier) =
        OutlinedTextField(value, { s -> set(s.filter(Char::isDigit).take(4)) }, m, label = { Text(label, fontSize = Type.label) },
            singleLine = true, keyboardOptions = num, colors = colors)

    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.xl).navigationBarsPadding().imePadding().padding(bottom = Space.l).verticalScroll(rememberScrollState())) {
            Text("Geçmişten kalan kaza", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
            Text("Borcun başladığı yaştan düzenli kılmaya başladığın yaşa kadar her gün, vitir dahil 6 vakit sayılır.",
                Modifier.padding(top = Space.xs), color = Pal.Muted, fontSize = Type.body2, lineHeight = 19.sp)
            Row(Modifier.padding(top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                field(from, { from = it }, "Borcun başladığı yaş", Modifier.weight(1f))
                field(to, { to = it }, "Düzenli kılmaya başladığın yaş", Modifier.weight(1f))
            }
            field(excused, { excused = it }, "Ayda düşülecek gün (özel hal)", Modifier.padding(top = Space.s).fillMaxWidth())
            Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                field(already, { already = it }, "Daha önce kıldığın kaza", Modifier.weight(1f))
                Segmented(listOf("gün", "ay", "yıl"), unit, Modifier.padding(top = Space.s)) { unit = it }
            }
            if (error != null) Text(error, Modifier.padding(top = Space.s), color = Pal.Warn, fontSize = Type.label)
            Column(Modifier.fillMaxWidth().padding(vertical = Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
                if (days == null) {
                    Text("İki yaşı yazınca tahmin burada görünür.", color = Pal.Muted, fontSize = Type.body2)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("%,d".format(tr, days * 6), style = NumStyle, fontSize = Type.display, lineHeight = Type.display, color = Pal.Text)
                        Text("vakit", Modifier.padding(start = Space.xs, bottom = 7.dp), color = Pal.Muted, fontSize = Type.body)
                    }
                    val yearsAtRate = if (rate <= 0) null else days * 6f / rate / 365f
                    Text(
                        "≈ ${"%.1f".format(tr, days / 365f)} yıl · her vakitten ${"%,d".format(tr, days)}" +
                            (yearsAtRate?.let { " · günde $rate ile ~${"%.1f".format(tr, it)} yıl" } ?: ""),
                        color = Pal.Muted, fontSize = Type.body2, textAlign = TextAlign.Center,
                    )
                    Text("Tahmindir; sonra vakit vakit düzeltebilirsin.", Modifier.padding(top = Space.xs), color = Pal.Muted.copy(alpha = .7f), fontSize = Type.label)
                }
            }
            GoldButton("Kaza listeme ekle", Modifier.fillMaxWidth().alpha(if ((days ?: 0) > 0) 1f else .4f), Icons.Default.Add) {
                if ((days ?: 0) > 0) onAdd(days!!)
            }
        }
    }
}

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
    var openKazaRate by remember { mutableStateOf(false) }
    var openDebt by remember { mutableStateOf(false) }

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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockPad.current)) {
        if (!embedded) ScreenTitle("Kaza")
        // design K2: one total, then six columns whose heights are what is left for each vakit
        val counts = KAZA_KEYS.map { v.let { _ -> store.kaza(it.first) } }
        val maxC = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
        val days = if (rate <= 0) 0 else (total + rate - 1) / rate
        val eta = LocalDate.now().plusDays(days.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", java.util.Locale("tr")))
        // daily rhythm: today (and yesterday), last 7 days, days in a row with kaza
        val log = v.let { store.kazaLog() }
        val today = store.prayerDay()
        val daily = (0 until 160).map { log[today.minusDays(it.toLong()).toString()] ?: 0 }   // newest first
        Row(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.l).fillMaxWidth().height(IntrinsicSize.Min)) {
            BigStat("${daily[0]}", "bugün · dün ${daily[1]}")
            Box(Modifier.width(1.dp).fillMaxHeight().background(Pal.Line))
            BigStat("${daily.take(7).sum()}", "son 7 gün")
            Box(Modifier.width(1.dp).fillMaxHeight().background(Pal.Line))
            BigStat("${kazaStreak(daily)}", "gün seri")
        }
        // "gün gün eda": what is left in days of six, and the day in progress
        val done = KAZA_KEYS.map { v.let { _ -> store.kazaDone(it.first) } }
        val (edaDays, inNow) = kazaRound(done, counts)
        val dayLeft = (total + KAZA_KEYS.size - 1) / KAZA_KEYS.size
        Column(Modifier.fillMaxWidth().padding(top = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedContent(if (total == 0) 0 else dayLeft, transitionSpec = { (scaleIn(initialScale = 1.3f) + fadeIn()) togetherWith fadeOut() }, label = "total") {
                    Text("$it", style = NumStyle, fontSize = Type.display, lineHeight = Type.display, color = Pal.Text)
                }
                Text(if (total == 0) "vakit kaldı" else "günlük kaza", Modifier.padding(start = Space.xs, bottom = 7.dp), color = Pal.Muted, fontSize = Type.body)
            }
            Text(
                if (total == 0) "Kaza borcun yok · Allah kabul etsin."
                else "$total vakit · günde $rate ile ~$eta" + if (edaDays > 0) " · $edaDays gün eda edildi" else "",
                Modifier.padding(horizontal = Space.xl), color = Pal.Muted, fontSize = Type.body2, textAlign = TextAlign.Center,
            )
            // with nothing owed yet, the likely reason is that the past was never entered
            if (total == 0) Box(
                Modifier.padding(top = Space.m).heightIn(min = TouchMin).clip(CircleShape).border(1.dp, Pal.Gold.copy(alpha = .55f), CircleShape)
                    .clickable { openDebt = true }.padding(horizontal = Space.l),
                contentAlignment = Alignment.Center,
            ) { Text("Geçmişten kalan borcunu hesapla", color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold) }
            if (total > 0) {
                SectionLabel("SIRADAKİ GÜN · ${edaDays + 1}. GÜN", Modifier.padding(start = Space.xl, end = Space.xl, top = Space.l, bottom = Space.s))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.m)) {
                    KAZA_KEYS.forEachIndexed { i, (_, name) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // a bead, shaded like the zikir ring: gold once this vakit is in for the day
                            Box(Modifier.size(28.dp).clip(CircleShape).background(Brush.radialGradient(
                                if (inNow[i]) listOf(Color(0xFFFFF1CF), Pal.Gold, Color(0xFF8A6420)) else listOf(Color(0xFF3A4A68), Pal.Track),
                            )))
                            Text(name, Modifier.padding(top = Space.xs), color = Pal.Muted, fontSize = Type.micro)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = Space.m, end = Space.m, top = Space.xl), verticalAlignment = Alignment.Bottom) {
            KAZA_KEYS.forEachIndexed { i, (key, name) ->
                val c = counts[i]
                val top = c == maxC && c > 0   // the longest column is the one to work on
                val h by animateFloatAsState(c.toFloat() / maxC, tween(600), label = "kaza$i")
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$c", style = NumStyle, fontSize = Type.h2, color = if (top) Pal.Gold else Pal.Text)
                    Spacer(Modifier.height(Space.xs))
                    Box(Modifier.width(30.dp).height(200.dp).clip(CircleShape).background(Pal.Track.copy(alpha = .5f)), contentAlignment = Alignment.BottomCenter) {
                        if (c > 0) Box(
                            Modifier.fillMaxWidth().fillMaxHeight(h).clip(CircleShape).background(
                                Brush.verticalGradient(listOf(if (top) Pal.Gold else Pal.Gold.copy(alpha = .75f), Pal.Gold.copy(alpha = .3f))),
                            ),
                        )
                    }
                    Text(name, Modifier.padding(top = Space.s), color = Pal.Muted, fontSize = Type.label)
                    // one tap = one kaza of this vakit prayed; 48dp target around a 40dp circle
                    Box(
                        Modifier.padding(top = Space.xs).size(TouchMin).clip(CircleShape)
                            .clickable(enabled = c > 0) {
                                store.payKaza(key); v++
                                val after = kazaRound(KAZA_KEYS.map { store.kazaDone(it.first) }, KAZA_KEYS.map { store.kaza(it.first) }).first
                                if (after > edaDays) {
                                    ctx.getSystemService(android.os.VibratorManager::class.java)?.defaultVibrator
                                        ?.vibrate(android.os.VibrationEffect.createOneShot(320, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                    undoable("Bir günlük kaza tamamlandı, Allah kabul etsin") { store.unpayKaza(key) }
                                } else undoable("$name kazası kılındı") { store.unpayKaza(key) }
                            }
                            .semantics { contentDescription = "$name kazası kıldım" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(40.dp).clip(CircleShape).background(if (top) Pal.Gold else Pal.Track.copy(alpha = if (c > 0) 1f else .4f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Check, null, tint = if (top) Pal.Ground else Pal.Gold.copy(alpha = if (c > 0) 1f else .35f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
        Text("sütunun altındaki tik: o vakitten bir kaza kıldım", Modifier.fillMaxWidth().padding(top = Space.s),
            color = Pal.Muted.copy(alpha = .8f), fontSize = Type.label, textAlign = TextAlign.Center)
        KazaCalendar(today, store.firstDay, daily, rate)

        Column(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Bu ay kılınan", Modifier.weight(1f), color = Pal.Muted, fontSize = Type.body2)
                Text("$paid", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
            }
            Row(Modifier.padding(top = Space.m), verticalAlignment = Alignment.CenterVertically) {
                // adding a kaza by hand (one not tracked by the app): pick the vakit
                var addOpen by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.heightIn(min = TouchMin).clip(CircleShape).border(1.dp, Pal.Border, CircleShape)
                            .clickable { addOpen = true }.padding(horizontal = Space.l),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Default.Add, null, tint = Pal.Gold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text("Kaza ekle", color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
                    }
                    DropdownMenu(addOpen, { addOpen = false }, containerColor = Pal.Card, shape = RoundedCornerShape(Radius.m)) {
                        KAZA_KEYS.forEach { (key, name) ->
                            DropdownMenuItem(text = { Text(name, color = Pal.Text, fontSize = Type.body) }, onClick = {
                                addOpen = false
                                store.addKaza(key, 1); v++
                                undoable("$name kazası eklendi") { store.addKaza(key, -1) }
                            })
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.heightIn(min = TouchMin).clip(CircleShape).clickable { openKazaRate = true }.padding(horizontal = Space.m),
                    contentAlignment = Alignment.Center,
                ) { Text("Planı değiştir", color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold) }
            }
            // kaza from before the app: the app only adds what it saw, so the past has to be estimated once
            Row(
                Modifier.padding(top = Space.m).fillMaxWidth().heightIn(min = TouchMin).clip(RoundedCornerShape(Radius.s))
                    .clickable { openDebt = true }.padding(horizontal = Space.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Geçmişten kalan borcunu hesapla", color = Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.Medium)
                    Text("Yaşlarına göre tahmini kaza, listene eklenir", color = Pal.Muted, fontSize = Type.label)
                }
                Icon(Icons.Default.KeyboardArrowRight, null, tint = Pal.Muted, modifier = Modifier.size(20.dp))
            }
            Text("Vaktinde kılınmayan namazlar buraya kendiliğinden eklenir.", Modifier.padding(top = Space.l, bottom = Space.xl),
                color = Pal.Muted.copy(alpha = .8f), fontSize = Type.label)
        }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = Space.m)) { data ->
            Snackbar(snackbarData = data, containerColor = Pal.Card, contentColor = Pal.Text, actionColor = Pal.Gold)
        }
    }

    if (openDebt) KazaDebtSheet(rate, onDismiss = { openDebt = false }) { days ->
        openDebt = false
        KAZA_KEYS.forEach { store.addKaza(it.first, days) }; v++
        undoable("%,d vakit kaza eklendi".format(java.util.Locale("tr"), days * KAZA_KEYS.size)) {
            KAZA_KEYS.forEach { store.addKaza(it.first, -days) }
        }
    }

    if (openKazaRate) {
        var draft by remember { mutableStateOf("$rate") }
        AlertDialog(
            onDismissRequest = { openKazaRate = false },
            containerColor = Pal.Card,
            title = { Text("Günde kaç kaza?", color = Pal.Text) },
            text = {
                Column {
                    Text("Kaza planını günde kaç vakit kılacağını yazarak belirle.", color = Pal.Muted, fontSize = Type.body2, lineHeight = 18.sp)
                    Spacer(Modifier.height(Space.s))
                    OutlinedTextField(
                        draft, { draft = it }, singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Border),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    draft.trim().toIntOrNull()?.let { if (it in 1..50) store.kazaRate = it }
                    openKazaRate = false; v++
                }) { Text("Kaydet", color = Pal.Gold) }
            },
            dismissButton = { TextButton(onClick = { openKazaRate = false }) { Text("Vazgeç", color = Pal.Muted) } },
        )
    }
}

// ---------------------------------------------------------------- Okuma

const val QURAN_PAGES = 604

/** Cüz of a page in the standard 604-page mushaf: cüz 1 is pages 1–21, then 20 pages each. */
fun cuz(page: Int) = if (page < 22) 1 else minOf(30, (page - 2) / 20 + 1)

private val TR_DAYS = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

/** Today's share of a dated plan: what was left at the start of today, spread evenly over the days left (today included). */
fun todayShare(total: Int, pageNow: Int, readToday: Int, today: LocalDate, until: LocalDate): Int {
    val left = (total - (pageNow - readToday)).coerceAtLeast(0)
    val days = (until.toEpochDay() - today.toEpochDay() + 1).coerceAtLeast(1)
    return ((left + days - 1) / days).toInt()
}

/** Days ahead (+) or behind (−) of an even plan from page [startPage] on [from] to [total] on [until], counted at the end of today. */
fun planLead(startPage: Int, total: Int, from: LocalDate, until: LocalDate, today: LocalDate, pageNow: Int): Int {
    val span = (until.toEpochDay() - from.toEpochDay() + 1).coerceAtLeast(1)
    val perDay = (total - startPage).toFloat() / span
    if (perDay <= 0f) return 0
    val expected = startPage + perDay * (today.toEpochDay() - from.toEpochDay() + 1).coerceIn(0, span)
    return ((pageNow - expected) / perDay).toInt()   // toward zero: half a day is not a day yet
}

/** Today's share of one reading: the plan's share when dated, the fixed daily goal for an undated Kur'an, nothing for an undated book. */
fun readingShare(r: Reading, readToday: Int, today: LocalDate, readGoal: Int): Int = when {
    r.until != null -> if (r.page >= r.total) 0 else todayShare(r.total, r.page, readToday, today, r.until)
    r.quran -> readGoal
    else -> 0
}

/** Pages read today and today's target over every reading on the shelf (the rose's Sayfa petal uses the same pair). */
fun readingToday(store: Store): Pair<Int, Int> {
    val day = store.prayerDay()
    val by = store.readLogBy(day)
    var done = 0; var target = 0
    store.readings().forEach { r ->
        val read = by[r.id] ?: 0
        val share = readingShare(r, read, day, store.readGoal)
        target += share; done += minOf(read, share)
    }
    return done to target
}

private val SHORT_DATE = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale("tr"))

/** Okuma, design M1 "Raf üstte": today's total, the shelf (each spine carries its own share and tick), the chosen reading, 14 days. */
@Composable
fun OkumaScreen() {
    StoreRev.n.let { } // refresh when any setting or count changes
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var v by remember { mutableIntStateOf(0) }
    val todayDate = store.prayerDay()   // logs write to the prayer day (imsak to imsak)
    val readings = v.let { store.readings() }
    val byToday = v.let { store.readLogBy(todayDate) }
    val goal = v.let { store.readGoal }
    val shares = readings.map { readingShare(it, byToday[it.id] ?: 0, todayDate, goal) }
    val target = shares.sum()
    val done = readings.indices.sumOf { minOf(byToday[readings[it].id] ?: 0, shares[it]) }
    var sel by remember { mutableIntStateOf(0) }
    val i = sel.coerceIn(0, readings.size - 1)
    var sheet by remember { mutableStateOf<String?>(null) }   // null closed, "" new reading, else the id whose plan is edited
    val heroBar by animateFloatAsState(if (target == 0) 0f else done.toFloat() / target, tween(500), label = "today")

    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    fun undoable(message: String, undo: () -> Unit) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            if (snackbar.showSnackbar(message, actionLabel = "Geri al", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed) { undo(); v++ }
        }
    }
    /** Moves a reading by [n] pages and logs it; the Kur'an wraps into a new hatim (and its dated plan ends there). */
    fun read(r: Reading, n: Int) {
        if (r.quran) {
            if (n < 0 && store.quranPage == 0) return
            var p = store.quranPage + n
            if (p > QURAN_PAGES) { store.hatim += 1; p -= QURAN_PAGES; store.setQuranPlan("", 0, null, null) }
            store.quranPage = p.coerceAtLeast(0)
            store.logPagesFor("quran", n)
        } else {
            val np = (r.page + n).coerceIn(0, r.total)
            store.saveBooks(store.books().map { if (it.name == r.id) it.copy(page = np) else it })
            store.logPagesFor(r.id, np - r.page)
        }
        v++
    }

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockPad.current)) {
        ScreenTitle("Okuma") {
            if (store.hatim > 0) Text("${store.hatim} hatim", Modifier.clip(CircleShape).background(Pal.Gold.copy(alpha = .14f))
                .padding(horizontal = Space.m, vertical = Space.xs), color = Pal.Gold, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
        }

        // ---- today, across every reading
        Column(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.s)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$target", style = NumStyle, fontSize = Type.display, lineHeight = Type.display, color = Pal.Text)
                Text("sayfa bugün", Modifier.padding(start = Space.xs, bottom = 7.dp), color = Pal.Muted, fontSize = Type.body)
            }
            Text(
                when { target == 0 -> "Bugün için hedef yok; bir okumaya bitiş tarihi ver."; done >= target -> "Bugünkü okuma tamam, elhamdülillah."; else -> "$done / $target okundu" },
                color = Pal.Muted, fontSize = Type.body2,
            )
            Box(Modifier.padding(top = Space.m).fillMaxWidth().height(6.dp).clip(CircleShape).background(Pal.Track)) {
                Box(Modifier.fillMaxWidth(heroBar.coerceIn(0f, 1f)).fillMaxHeight().clip(CircleShape).background(Pal.Gold))
            }
        }

        // ---- the shelf: fill = how far the reading is; the foot carries today's share and its tick
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(start = Space.xl, end = Space.xl, top = Space.xl, bottom = Space.s),
            horizontalArrangement = Arrangement.spacedBy(Space.m)) {
            readings.forEachIndexed { k, r ->
                val read = byToday[r.id] ?: 0
                Spine(r, read, shares[k], store.hatim, k == i, onClick = { sel = k }) {
                    // tick: finish today's share in one go
                    val add = shares[k] - read
                    if (add > 0) { read(r, add); undoable("${r.title}: bugünkü $add sayfa okundu") { read(r.copy(page = r.page + add), -add) } }
                }
            }
            Box(
                Modifier.size(72.dp, 168.dp).clip(RoundedCornerShape(14.dp)).border(1.dp, Pal.Border, RoundedCornerShape(14.dp))
                    .clickable { sheet = "" }.semantics { contentDescription = "Yeni okuma" },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Add, null, tint = Pal.Gold)
                    Text("Yeni", color = Pal.Muted, fontSize = Type.label)
                }
            }
        }

        // ---- the chosen reading
        val r = readings[i]
        val read = byToday[r.id] ?: 0
        val share = shares[i]
        Column(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.l)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${r.page}", style = NumStyle, fontSize = Type.title, lineHeight = Type.title, color = Pal.Text)
                Text("/ ${r.total} sayfa", Modifier.padding(start = Space.xs, bottom = 5.dp), color = Pal.Muted, fontSize = Type.body2)
            }
            Text(
                listOfNotNull(
                    if (r.quran && r.page > 0) "${cuz(r.page)}. cüz · ${sureOf(r.page)}" else null,
                    if (!r.quran) r.title else null,
                    r.until?.let { "bitiş ${it.format(SHORT_DATE)}" },
                ).joinToString(" · ").ifEmpty { "henüz başlamadın" },
                color = Pal.Muted, fontSize = Type.body2,
            )
            // pace of the last 14 days for this reading → when it would end
            val pace = (0 until 14).sumOf { store.readLogBy(todayDate.minusDays(it.toLong()))[r.id] ?: 0 } / 14f
            val finish = if (pace < .05f || r.page >= r.total) null else todayDate.plusDays(kotlin.math.ceil((r.total - r.page) / pace).toLong())
            Row(Modifier.padding(top = Space.m).fillMaxWidth().height(IntrinsicSize.Min)) {
                BigStat(if (share > 0) "$read/$share" else "$read", "bugün")
                Box(Modifier.width(1.dp).fillMaxHeight().background(Pal.Line))
                if (r.from != null && r.until != null) {
                    val lead = planLead(r.startPage, r.total, r.from, r.until, todayDate, r.page)
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (lead > 0) "+$lead" else "$lead", style = NumStyle, fontSize = Type.num, lineHeight = Type.num,
                            color = when { lead > 0 -> Pal.Good; lead < 0 -> Pal.Warn; else -> Pal.Text })
                        Text(when { lead > 0 -> "gün önde"; lead < 0 -> "gün geride"; else -> "planda" }, color = Pal.Muted, fontSize = Type.label)
                    }
                } else if (r.quran) {
                    // undated Kur'an: the fixed daily goal, tap to change it
                    Column(Modifier.weight(1f).clickable {
                        val goals = listOf(5, 10, 15, 20, 30)
                        store.readGoal = goals[(goals.indexOf(goal) + 1) % goals.size]; v++
                    }, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$goal", style = NumStyle, fontSize = Type.num, lineHeight = Type.num, color = Pal.Gold)
                        Text("günlük hedef", color = Pal.Muted, fontSize = Type.label)
                    }
                } else BigStat("–", "plan yok")
                Box(Modifier.width(1.dp).fillMaxHeight().background(Pal.Line))
                BigStat(finish?.format(SHORT_DATE) ?: "–", "bu hızla biter")
            }
            Row(Modifier.padding(top = Space.l), horizontalArrangement = Arrangement.spacedBy(Space.s), verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = { read(r, -1) }, Modifier.size(TouchMin), border = androidx.compose.foundation.BorderStroke(1.dp, Pal.Border)) {
                    Text("−1", color = Pal.Text, fontSize = Type.body2)
                }
                if (share > read) GoldButton("Bugünkünü tamamla", Modifier.weight(1f), Icons.Default.Check) {
                    val add = share - read
                    read(r, add); undoable("${r.title}: bugünkü $add sayfa okundu") { read(r.copy(page = r.page + add), -add) }
                } else GoldButton("+1 sayfa", Modifier.weight(1f)) { read(r, 1) }
                OutlinedIconButton(onClick = { read(r, 1) }, Modifier.size(TouchMin), border = androidx.compose.foundation.BorderStroke(1.dp, Pal.Border)) {
                    Text("+1", color = Pal.Text, fontSize = Type.body2)
                }
            }
            Row(Modifier.padding(top = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                if (r.quran) TextButton(onClick = { read(r, 20) }) { Text("+1 cüz", color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
                TextButton(onClick = { sheet = r.id }) { Text(if (r.until == null) "Plan kur" else "Planı değiştir", color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
                Spacer(Modifier.weight(1f))
                if (!r.quran) TextButton(onClick = {
                    val before = store.books()
                    store.saveBooks(before.filter { it.name != r.id }); sel = 0; v++
                    undoable("${r.title} silindi") { store.saveBooks(before) }
                }) { Text("Sil", color = Pal.Muted) }
            }
        }

        // ---- 14 days, every reading together
        val log = v.let { store.readLog() }
        val days = (13 downTo 0).map { todayDate.minusDays(it.toLong()) }
        val counts = days.map { log[it.toString()] ?: 0 }
        Column(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.l, bottom = Space.xl)) {
            SectionLabel("SON 14 GÜN · ${counts.sum()} SAYFA", Modifier.padding(bottom = Space.s))
            val maxN = counts.maxOrNull()?.coerceAtLeast(1) ?: 1
            Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
                counts.forEachIndexed { k, n ->
                    val h by animateFloatAsState(n / maxN.toFloat(), tween(500), label = "bar$k")
                    Box(Modifier.weight(1f).fillMaxHeight(h.coerceAtLeast(.04f)).clip(RoundedCornerShape(4.dp))
                        .background(if (k == 13) Pal.Gold else Pal.Gold.copy(alpha = if (n > 0) .45f else .15f)))
                }
            }
            Row(Modifier.padding(top = Space.xs)) {
                Text("14 gün önce", Modifier.weight(1f), color = Pal.Muted.copy(alpha = .7f), fontSize = Type.micro)
                Text("bugün", color = Pal.Muted.copy(alpha = .7f), fontSize = Type.micro)
            }
        }
    }
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = Space.m)) { data ->
        Snackbar(snackbarData = data, containerColor = Pal.Card, contentColor = Pal.Text, actionColor = Pal.Gold)
    }
    }

    sheet?.let { id -> ReadingSheet(store, readings.firstOrNull { it.id == id }) { sheet = null; v++ } }
}

/** A book spine on the shelf: the fill rises with progress; the foot shows today's share and a tick that completes it. */
@Composable
private fun Spine(r: Reading, read: Int, share: Int, hatim: Int, selected: Boolean, onClick: () -> Unit, onTick: () -> Unit) {
    val progress by animateFloatAsState(if (r.total == 0) 0f else r.page.toFloat() / r.total, tween(600), label = "spine")
    val finished = !r.quran && r.page >= r.total
    val shape = RoundedCornerShape(14.dp)
    Box(
        Modifier.size(104.dp, 168.dp).clip(shape).background(Pal.Card)
            .border(if (selected) 2.dp else 1.dp, if (selected) Pal.Gold else Color.White.copy(alpha = .06f), shape)
            .clickable(onClick = onClick).semantics { contentDescription = "${r.title}, ${r.page} / ${r.total}" },
    ) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().fillMaxHeight(progress.coerceIn(0f, 1f))
            .background(Brush.verticalGradient(listOf(Pal.Gold.copy(alpha = .12f), Pal.Gold.copy(alpha = .55f)))))
        Column(Modifier.fillMaxSize().padding(Space.s + 2.dp)) {
            Text(
                when { r.quran -> "HATİM ${hatim + 1}"; finished -> "BİTTİ"; r.until != null -> "PLANLI"; else -> "KİTAP" },
                color = Pal.Gold, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp,
            )
            Text(if (r.quran) "Kur'an-ı Kerîm" else r.title, style = NumStyle, fontSize = 17.sp, lineHeight = 19.sp, color = Pal.Text, maxLines = 3)
            if (r.quran && r.title != "Kur'an-ı Kerîm") Text(r.title, color = Pal.Muted, fontSize = Type.micro, maxLines = 1)
            Spacer(Modifier.weight(1f))
            if (share > 0) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$read", style = NumStyle, fontSize = 18.sp, color = Pal.Text)
                Text("/$share", Modifier.weight(1f), color = Pal.Muted, fontSize = Type.micro)
                val ok = read >= share
                Box(
                    Modifier.size(32.dp).clip(CircleShape).then(if (ok) Modifier.background(Pal.Gold) else Modifier.border(1.5.dp, Pal.Gold, CircleShape))
                        .clickable(enabled = !ok, onClick = onTick).semantics { contentDescription = "${r.title}: bugünkünü tamamla" },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Default.Check, null, tint = if (ok) Pal.Ground else Pal.Gold, modifier = Modifier.size(16.dp)) }
            }
        }
    }
}

/** New reading, or the plan of an existing one ([edit]): presets, pages, dates; the daily pages are worked out live. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadingSheet(store: Store, edit: Reading?, onClose: () -> Unit) {
    val today = store.prayerDay()
    var kind by remember { mutableIntStateOf(if (edit?.quran == true) 0 else if (edit != null) 2 else 0) }   // 0 hatim, 1 cüz, 2 kitap
    val quran = kind == 0
    var name by remember { mutableStateOf(edit?.let { if (it.quran) store.quranPlanName else it.title } ?: "") }
    var total by remember { mutableStateOf(edit?.total?.toString() ?: "$QURAN_PAGES") }
    var already by remember { mutableStateOf((edit?.page ?: store.quranPage).toString()) }
    var until by remember { mutableStateOf(edit?.until) }
    var picking by remember { mutableStateOf(false) }
    val t = if (quran) QURAN_PAGES else total.toIntOrNull() ?: 0
    val a = already.toIntOrNull() ?: 0
    val clash = !quran && edit == null && (name.trim() == "quran" || store.books().any { it.name.equals(name.trim(), ignoreCase = true) })
    val error = when {
        !quran && edit == null && name.isBlank() -> "Ne okuyacağını yaz"
        clash -> "Bu adla bir okuma zaten var"
        t <= 0 -> "Toplam sayfa en az 1 olmalı"
        a > t -> "Okunan, toplamdan fazla olamaz"
        else -> null
    }
    val colors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = Pal.Gold, unfocusedBorderColor = Pal.Border, focusedLabelColor = Pal.Gold,
        cursorColor = Pal.Gold, focusedTextColor = Pal.Text, unfocusedTextColor = Pal.Text, unfocusedLabelColor = Pal.Muted,
        disabledTextColor = Pal.Muted, disabledBorderColor = Pal.Border, disabledLabelColor = Pal.Muted,
    )
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)

    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.xl).navigationBarsPadding().imePadding().padding(bottom = Space.l).verticalScroll(rememberScrollState())) {
            Text(if (edit == null) "Yeni okuma" else "Plan · ${edit.title}", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
            if (edit == null) Row(Modifier.padding(top = Space.m), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                listOf("Hatim · 604", "1 cüz · 20", "Kitap").forEachIndexed { k, label ->
                    val on = kind == k
                    Box(Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(Radius.s)).background(if (on) Pal.Track else Color.Transparent)
                        .border(1.dp, if (on) Color.Transparent else Pal.Border, RoundedCornerShape(Radius.s))
                        .clickable {
                            kind = k
                            when (k) {
                                0 -> { total = "$QURAN_PAGES"; already = "${store.quranPage}" }
                                1 -> { total = "20"; already = "0"; if (name.isBlank()) name = "Cüz okuması" }
                                else -> { total = ""; already = "0" }
                            }
                        }.padding(horizontal = Space.m, vertical = Space.s), contentAlignment = Alignment.Center) {
                        Text(label, color = if (on) Pal.Gold else Pal.Muted, fontSize = Type.body2, fontWeight = FontWeight.Medium)
                    }
                }
            }
            OutlinedTextField(name, { name = it.take(40) }, Modifier.padding(top = Space.m).fillMaxWidth(), singleLine = true, colors = colors,
                enabled = quran || edit == null,
                label = { Text(if (quran) "Planın adı (isteğe bağlı, ör. Ramazan hatmi)" else "Ne okuyacaksın?", fontSize = Type.label) })
            Row(Modifier.padding(top = Space.s), horizontalArrangement = Arrangement.spacedBy(Space.s)) {
                OutlinedTextField(if (quran) "$QURAN_PAGES" else total, { total = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f),
                    enabled = !quran, singleLine = true, keyboardOptions = num, colors = colors, label = { Text("Toplam sayfa", fontSize = Type.label) })
                OutlinedTextField(already, { already = it.filter(Char::isDigit).take(5) }, Modifier.weight(1f),
                    singleLine = true, keyboardOptions = num, colors = colors, label = { Text("Okunan sayfa", fontSize = Type.label) })
            }
            // the end date is what turns a reading into a plan
            Row(Modifier.padding(top = Space.m).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Bitiş", color = Pal.Muted, fontSize = Type.label)
                    Text(until?.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale("tr"))) ?: "Tarih yok",
                        color = Pal.Text, fontSize = Type.body)
                }
                if (until != null) TextButton(onClick = { until = null }) { Text("Kaldır", color = Pal.Muted) }
                TextButton(onClick = { picking = true }) { Text("Tarih seç", color = Pal.Gold, fontWeight = FontWeight.SemiBold) }
            }
            if (error != null) Text(error, Modifier.padding(top = Space.s), color = Pal.Warn, fontSize = Type.label)
            Column(Modifier.fillMaxWidth().padding(vertical = Space.l), horizontalAlignment = Alignment.CenterHorizontally) {
                val u = until
                if (u != null && error == null) {
                    val per = todayShare(t, a, 0, today, u)
                    val days = u.toEpochDay() - today.toEpochDay() + 1
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$per", style = NumStyle, fontSize = Type.display, lineHeight = Type.display, color = Pal.Text)
                        Text("sayfa / gün", Modifier.padding(start = Space.xs, bottom = 7.dp), color = Pal.Muted, fontSize = Type.body)
                    }
                    Text("${t - a} sayfa · ${days.coerceAtLeast(1)} gün", color = Pal.Muted, fontSize = Type.body2)
                } else Text(
                    if (quran) "Tarih vermezsen günlük hedef (${store.readGoal} sayfa) kullanılır." else "Tarih vermezsen günlük hedefi olmaz; ilerlemesi yine tutulur.",
                    color = Pal.Muted, fontSize = Type.body2, textAlign = TextAlign.Center,
                )
            }
            GoldButton(if (edit == null) "Okumayı başlat" else "Kaydet", Modifier.fillMaxWidth().alpha(if (error == null) 1f else .4f), Icons.Default.Check) {
                if (error != null) return@GoldButton
                val from = if (until != null) today else null
                if (quran) {
                    store.quranPage = a
                    store.setQuranPlan(name.trim(), a, from, until)
                } else if (edit != null) {
                    store.saveBooks(store.books().map { if (it.name == edit.id) it.copy(page = a, total = t, startPage = a, from = from, until = until) else it })
                } else {
                    store.saveBooks(store.books() + Book(name.trim(), a, t, a, from, until))
                }
                onClose()
            }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (until ?: today.plusDays(30)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis >= today.atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { until = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("Tamam", color = Pal.Gold) }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Vazgeç", color = Pal.Muted) } },
            colors = DatePickerDefaults.colors(containerColor = Pal.Card),
        ) {
            DatePicker(state, colors = DatePickerDefaults.colors(
                containerColor = Pal.Card, selectedDayContainerColor = Pal.Gold, selectedDayContentColor = Pal.Ground,
                todayDateBorderColor = Pal.Gold, todayContentColor = Pal.Gold, dayContentColor = Pal.Text,
                titleContentColor = Pal.Muted, headlineContentColor = Pal.Text, weekdayContentColor = Pal.Muted,
                navigationContentColor = Pal.Text, yearContentColor = Pal.Text, selectedYearContainerColor = Pal.Gold,
            ))
        }
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
                Text(label, color = if (selected == i) Pal.Ground else Pal.Muted, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
/** Primary tabs for a page's sections: gold label + short gold bar under the selected one, hairline baseline. */
@Composable
fun PageTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.s)) {
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val on = selected == i
                val color by animateColorAsState(if (on) Pal.Gold else Pal.Muted, tween(150), label = "tab")
                val bar by animateFloatAsState(if (on) 1f else 0f, tween(250), label = "bar")
                Box(
                    Modifier.weight(1f).height(TouchMin).clip(RoundedCornerShape(Radius.s))
                        .clickable {
                            if (!on) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        }
                        .semantics { this.selected = on; role = Role.Tab },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, color = color, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
                    Box(
                        Modifier.align(Alignment.BottomCenter).width(32.dp * bar).height(3.dp)
                            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(Pal.Gold),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Line))
    }
}

/** Muhasebe holds the overview (like Vakit's "Özet"), the history and the kaza list. */
@Composable
fun MuhasebeScreen() {
    var section by remember { mutableIntStateOf(SkyDebug.mSection) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Defter", rule = false)
        PageTabs(listOf("Özet", "İstikrar", "Kaza"), section) { section = it }
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
/** (vakti çıkmış, bunlardan kılınmış): a prayer marked before its time ends counts once its time is out, so the rate stays ≤ 100%. */
fun dueKept(list: List<Slot>, prayed: Set<String>, now: Long): Pair<Int, Int> {
    val ended = list.filter { it.end <= now }
    return ended.size to ended.count { it.key in prayed }
}

@Composable
private fun MuhasebeOzet() {
    StoreRev.n.let { } // refresh when any setting or count changes
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    // also refresh when the prayer day turns (imsak) and when the app comes back to the foreground
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(tick) { kotlinx.coroutines.delay((store.nextImsak() - System.currentTimeMillis()).coerceAtLeast(1_000)); tick++ }
    val owner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    tick.let { }
    if (!store.hasLocation) return NeedLocation()
    val today = LocalDate.now()
    val now = System.currentTimeMillis()
    val first = store.firstDay
    val log = store.readLog()
    val prayed = store.prayed()
    val page = store.quranPage
    val totalKaza = Vakit.entries.sumOf { store.kaza(it) } + store.kaza("VITIR")
    val day = store.prayerDay()   // the rose's day runs imsak to imsak
    val readToday = readingToday(store)   // the Sayfa petal: today's pages against today's target on the shelf
    val petals = gulPetals(
        slots(store.lat, store.lng, day), prayed, store::prayedAt,
        pagesToday = readToday.first, readGoal = readToday.second,
        kazaToday = store.kazaLog()[day.toString()] ?: 0, kazaRate = store.kazaRate, kazaOwed = totalKaza,
        zikirs = dailyZikirs(store.zikirs, store.tesbihatCounts), zikirCounts = store.zikirCounts(day),
    )

    val slotCache = remember { mutableMapOf<LocalDate, List<Slot>>() }
    fun daySlots(d: LocalDate) = slotCache.getOrPut(d) { slots(store.lat, store.lng, d) }

    // son 30 gün ve tüm zamanlar: kaç vakit geldi, kaçı kılındı, kaç gün tamdı
    fun stats(days: Int): Triple<Int, Int, Int> {
        var due = 0; var kept = 0; var full = 0
        for (i in 0 until days) {
            val d = today.minusDays(i.toLong())
            if (d.isBefore(first)) break
            val list = daySlots(d)
            val (dd, kk) = dueKept(list, prayed, now)
            due += dd; kept += kk
            if (list.all { it.key in prayed || it.end > now }) full++
        }
        return Triple(due, kept, full)
    }
    val (due30, kept30, full30) = stats(30)
    val totalDays = (today.toEpochDay() - first.toEpochDay()).toInt() + 1
    val (dueAll, keptAll, _) = stats(totalDays.coerceAtMost(400))
    val rate30 = if (due30 == 0) 0f else kept30.toFloat() / due30
    val rateAll = if (dueAll == 0) 0f else keptAll.toFloat() / dueAll
    // seri: full days in a row back from today (today counts while nothing in it has been missed)
    var streak = 0
    for (i in 0 until totalDays.coerceAtMost(400)) {
        if (daySlots(today.minusDays(i.toLong())).all { it.key in prayed || it.end > now }) streak++ else break
    }

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

    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val anim by animateFloatAsState(if (shown) 1f else 0f, tween(900), label = "radar")

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockPad.current)) {
        // design A "Sahne": the rose stands on the page itself, full width; long-term numbers sit in one row under it
        GulCard(petals, Modifier.fillMaxWidth().height(360.dp))
        val open = petals.count { it.progress >= 1f }
        var roseInfo by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (open == petals.size) "Bugün · gül tam açtı" else "Bugün · $open yaprak tam açık").uppercase(java.util.Locale("tr")),
                color = Pal.Muted, fontSize = Type.label, letterSpacing = 1.sp, fontWeight = FontWeight.Medium,
            )
            IconButton(onClick = { roseInfo = true }) { Icon(Icons.Default.Info, "Gül nasıl açar?", tint = Pal.Gold, modifier = Modifier.size(18.dp)) }
        }
        if (roseInfo) RoseInfoSheet(petals, store.kazaRate) { roseInfo = false }
        Row(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.l).fillMaxWidth().height(IntrinsicSize.Min)) {
            StatColumn("$page", "/604", page / 604f, "Hatim")
            Box(Modifier.width(1.dp).fillMaxHeight().background(Pal.Line))
            StatColumn("$full30", "/30", full30 / 30f, "Son 30 gün")
            Box(Modifier.width(1.dp).fillMaxHeight().background(Pal.Line))
            StatColumn("$streak", "", null, "gün seri")
        }

        InfoCard(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.m).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(22.dp).clip(CircleShape).background(Pal.Gold.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = Pal.Gold, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(Space.s))
                Text(insight, Modifier.weight(1f), color = Pal.Text, fontSize = Type.body2, lineHeight = 19.sp)
            }
            Spacer(Modifier.height(Space.m))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("%${(rate30 * 100).roundToInt()}", style = NumStyle, fontSize = Type.num, color = Pal.Text)
                    Text("Son 30 gün", color = Pal.Muted, fontSize = Type.micro)
                }
                Column(Modifier.weight(1f)) {
                    Text("%${(rateAll * 100).roundToInt()}", style = NumStyle, fontSize = Type.num, color = Pal.Text)
                    Text("Tüm zamanlar", color = Pal.Muted, fontSize = Type.micro)
                }
            }
        }

        InfoCard(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.m).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HATIM TAKİBİ", Modifier.weight(1f), color = Pal.Muted, fontSize = Type.micro, letterSpacing = 1.sp)
                Text(if (page >= 604) "BİTTİ" else "DEVAM EDİYOR", color = Color(0xFF6BC5B0), fontSize = Type.micro, letterSpacing = 1.sp)
            }
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 6.dp.toPx()
                        drawArc(Pal.Track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                        drawArc(Color(0xFF8AA6D8), -90f, 360f * (page / 604f) * anim, false, Offset(w / 2, w / 2),
                            Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Text("%${(page * 100 / 604)}", color = Pal.Text, fontSize = Type.label, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("$page / 604 sayfa · ${sureOf(page)}", color = Pal.Text, fontSize = Type.body2)
                    Text("${store.hatim} hatim tamam", color = Pal.Muted, fontSize = Type.label)
                    Text(
                        if (eta != null) "Bu hızla ~${eta.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale("tr")))}"
                        else "Günde birkaç sayfa okuyunca tarih burada görünecek",
                        color = Pal.Muted, fontSize = Type.micro,
                    )
                }
            }
        }

        InfoCard(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.m, bottom = Space.l).fillMaxWidth()) {
            Text("KAZA NAMAZI", color = Pal.Muted, fontSize = Type.micro, letterSpacing = 1.sp)
            Spacer(Modifier.height(Space.s))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize()) {
                        val w = 6.dp.toPx()
                        drawArc(Pal.Track, 0f, 360f, false, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), style = Stroke(w))
                        val prog = if (totalKaza == 0) 1f else (store.kazaPaidThisMonth().toFloat() / (store.kazaPaidThisMonth() + totalKaza)).coerceIn(0f, 1f)
                        drawArc(Pal.Warn, -90f, 360f * prog * anim, false, Offset(w / 2, w / 2),
                            Size(size.width - w, size.height - w), style = Stroke(w, cap = StrokeCap.Round))
                    }
                    Text("${totalKaza}", color = Pal.Text, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(Space.m))
                Column(Modifier.weight(1f)) {
                    Text("$totalKaza vakit kaldı", style = NumStyle, fontSize = Type.num, color = Pal.Text)
                    Text("Bu ay ${store.kazaPaidThisMonth()} kaza kıldın · plan günde ${store.kazaRate}", color = Pal.Muted, fontSize = Type.label)
                    Text(
                        if (monthsLeft != null) "Bu hızla ~${monthsLeft.roundToInt()} ay içinde tamamlanır, inşallah"
                        else "Kaza kalmadı, elhamdülillah",
                        color = Pal.Muted, fontSize = Type.micro,
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

    /** One period. Day i is start+i; days before [from] predate the app and count for nothing.
     *  marks[i][k] for vakit k: 0 not due yet, 1 prayed, 2 missed. */
    data class Bucket(
        val start: LocalDate, val days: Int, val from: Int, val elapsed: Int,
        val counts: IntArray, val marks: Array<IntArray>, val pages: IntArray, val kazaDaily: IntArray,
        val kept: Int, val due: Int, val full: Int, val prevRate: Float?,
        val vKept: IntArray, val vDue: IntArray, val fracs: Map<String, List<Float>>, val mins: Map<String, List<Int>>,
    )

    // bir yıllık vakit hesabı birkaç yüz ms sürüyor: arka planda hesapla, bu arada spinner göster
    var bucket by remember(period) { mutableStateOf<Bucket?>(null) }
    LaunchedEffect(period, prayed.size, log.size) {
        bucket = withContext(Dispatchers.Default) { run {
            val now = System.currentTimeMillis()
            val start = when (period) {
                0 -> today.minusDays(6); 1 -> today.minusDays(29); 2 -> LocalDate.of(today.year, 1, 1); else -> first
            }
            val lastDay = if (period == 2) LocalDate.of(today.year, 12, 31) else today
            val n = (lastDay.toEpochDay() - start.toEpochDay()).toInt() + 1
            val elapsed = ((today.toEpochDay() - start.toEpochDay()).toInt() + 1).coerceAtMost(n)
            val from = (first.toEpochDay() - start.toEpochDay()).toInt().coerceIn(0, elapsed)

            val counts = IntArray(n); val pages = IntArray(n); val marks = Array(n) { IntArray(5) }
            val vKept = IntArray(5); val vDue = IntArray(5)
            var kept = 0; var due = 0; var full = 0
            val fracs = mutableMapOf<String, MutableList<Float>>()
            val mins = mutableMapOf<String, MutableList<Int>>()
            for (i in from until elapsed) {
                val d = start.plusDays(i.toLong())
                pages[i] = log[d.toString()] ?: 0
                for (sl in slots(store.lat, store.lng, d)) {
                    val k = sl.vakit.ordinal
                    val m = when { sl.key in prayed -> 1; sl.end <= now -> 2; else -> 0 }
                    marks[i][k] = m
                    if (m != 0) { due++; vDue[k]++ }
                    if (m == 1) { kept++; vKept[k]++; counts[i]++ }
                    // namazları ne zaman kılıyoruz: her işaretin vakit penceresindeki yeri
                    val at = store.prayedAt(sl.key) ?: continue
                    if (at < sl.start || sl.end <= sl.start) continue
                    fracs.getOrPut(sl.vakit.name) { mutableListOf() }.add(((at - sl.start).toFloat() / (sl.end - sl.start)).coerceIn(0f, 1f))
                    mins.getOrPut(sl.vakit.name) { mutableListOf() }.add(((at - sl.start) / 60_000L).toInt())
                }
                if (counts[i] >= 5) full++
            }
            // önceki dönem (hafta/ay): aynı uzunlukta, hemen öncesi; uygulamadan önceki günler yok sayılır
            val prevRate = if (period > 1) null else {
                var pk = 0; var pd = 0
                for (j in 1..elapsed) {
                    val d = start.minusDays(j.toLong())
                    if (d.isBefore(first)) break
                    slots(store.lat, store.lng, d).forEach { pd++; if (it.key in prayed) pk++ }
                }
                if (pd == 0) null else pk.toFloat() / pd
            }
            val kLog = store.kazaLog()
            val kazaDaily = IntArray(n) { i -> if (i < from) 0 else kLog[start.plusDays(i.toLong()).toString()] ?: 0 }
            Bucket(start, n, from, elapsed, counts, marks, pages, kazaDaily, kept, due, full, prevRate, vKept, vDue, fracs, mins)
        } }
    }
    val data = bucket
    if (data == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Pal.Gold) }
        return
    }

    val rate = if (data.due == 0) 0f else data.kept.toFloat() / data.due
    val pagesSum = data.pages.sum()
    val kazaSum = data.kazaDaily.sum()
    val dayFmt = java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", tr)
    fun pct(f: Float) = "%${(f * 100).roundToInt()}"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockPad.current)) {
        SubTabs(listOf("Hafta", "Ay", "Yıl", "Tümü"), period) { period = it }

        // ---- hero: one big number, what it means under it
        Column(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.xl)) {
            val (big, unit) = when (period) {
                0 -> "${data.kept}" to "/ ${data.due} vakit"
                1 -> pct(rate) to "vakit kılındı"
                2 -> "${data.full}" to "gün tam · ${today.year}"
                else -> "${data.kept}" to "vakit namaz"
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(big, style = NumStyle, fontSize = Type.display, lineHeight = Type.display, color = Pal.Text)
                Text(unit, Modifier.padding(start = Space.xs, bottom = 7.dp), color = Pal.Muted, fontSize = Type.body)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (period) {
                        0 -> "Son 7 gün"; 1 -> "Son 30 gün"
                        2 -> "${data.elapsed - data.from} gün · vakitlerin ${pct(rate)}'i kılındı"
                        else -> "${data.elapsed} gündür seninle · ${first.format(dayFmt)}'ten beri"
                    },
                    color = Pal.Muted, fontSize = Type.body2,
                )
                data.prevRate?.let { prev ->
                    val d = ((rate - prev) * 100).roundToInt()
                    Text(" · ${if (d >= 0) "▲" else "▼"} ${abs(d)} puan", color = if (d >= 0) Pal.Good else Pal.Warn, fontSize = Type.body2)
                    Text(" önceki döneme göre", color = Pal.Muted, fontSize = Type.body2)
                }
            }
        }

        when (period) {
            0 -> WeekMap(data.start, data.from, data.marks, data.pages, pagesSum, tr)
            1 -> {
                // a month of single days is noisy: 5-day running average of each day's share of due prayers
                val daily = (data.from until data.elapsed).map { i ->
                    val due = data.marks[i].count { it != 0 }
                    if (due == 0) null else data.counts[i].toFloat() / due
                }.filterNotNull()
                val smooth = daily.indices.map { i -> daily.subList(max(0, i - 4), i + 1).average().toFloat() }
                PulseCurve(smooth, cumulative = false, Modifier.padding(top = Space.l).fillMaxWidth().height(170.dp))
                CurveAxis(data.start.plusDays(data.from.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM", tr)), "bugün")
                Column(Modifier.padding(horizontal = Space.xl)) {
                    SectionLabel("VAKİT VAKİT", Modifier.padding(top = Space.xl, bottom = Space.s))
                    Vakit.entries.forEach { vk ->
                        val f = if (data.vDue[vk.ordinal] == 0) 0f else data.vKept[vk.ordinal].toFloat() / data.vDue[vk.ordinal]
                        Row(Modifier.height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(vk.ad, Modifier.width(64.dp), color = Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.Medium)
                            Box(Modifier.weight(1f).height(4.dp).clip(CircleShape).background(Pal.Track)) {
                                // a weak vakit (under 80 %) shows in peach, the one to work on
                                Box(Modifier.fillMaxHeight().fillMaxWidth(f).clip(CircleShape).background(if (f < .8f) Pal.Warn else Pal.Gold))
                            }
                            Text(pct(f), Modifier.width(44.dp), color = Pal.Muted, fontSize = Type.label, textAlign = TextAlign.End)
                        }
                    }
                }
            }
            2 -> YearHeat(data.start, data.days, data.from, data.elapsed, data.counts, tr)
            else -> {
                var acc = 0
                PulseCurve(data.counts.take(data.elapsed).map { acc += it; acc.toFloat() }, cumulative = true,
                    Modifier.padding(top = Space.l).fillMaxWidth().height(150.dp))
                CurveAxis("başlangıç", "bugün")
            }
        }

        // ---- under the main visual
        when (period) {
            0, 1 -> Row(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.xl).fillMaxWidth().height(IntrinsicSize.Min)) {
                BigStat("${data.kept}", "vakit kılındı")
                Box(Modifier.width(1.dp).fillMaxHeight().background(Pal.Line))
                BigStat("$pagesSum", "sayfa okundu")
                Box(Modifier.width(1.dp).fillMaxHeight().background(Pal.Line))
                BigStat("$kazaSum", "kaza kılındı")
            }
            2 -> Column(Modifier.padding(horizontal = Space.xl)) {
                // best month of the year by share of due prayers kept
                val best = (1..12).mapNotNull { m ->
                    var k = 0; var d = 0
                    for (i in data.from until data.elapsed) if (data.start.plusDays(i.toLong()).monthValue == m) {
                        data.marks[i].forEach { if (it != 0) d++; if (it == 1) k++ }
                    }
                    if (d == 0) null else m to k.toFloat() / d
                }.maxByOrNull { it.second }
                if (best != null) Row(Modifier.padding(top = Space.m)) {
                    Text("En iyi ay ", color = Pal.Muted, fontSize = Type.body2)
                    Text(java.time.Month.of(best.first).getDisplayName(java.time.format.TextStyle.FULL, tr), color = Pal.Gold, fontSize = Type.body2, fontWeight = FontWeight.SemiBold)
                    Text(" · ${pct(best.second)}", color = Pal.Muted, fontSize = Type.body2)
                }
                SectionLabel("BU YIL", Modifier.padding(top = Space.xl, bottom = Space.s))
                Row { BigStat("${data.kept}", "vakit namaz", start = true); BigStat("$pagesSum", "sayfa okundu", start = true) }
                Spacer(Modifier.height(Space.l))
                Row { BigStat("${store.hatim}", "hatim", start = true); BigStat("$kazaSum", "kaza kılındı", start = true) }
            }
            else -> Column(Modifier.padding(horizontal = Space.xl)) {
                SectionLabel("KİLOMETRE TAŞLARI", Modifier.padding(top = Space.xl, bottom = Space.xs))
                val firstFull = (0 until data.elapsed).firstOrNull { data.counts[it] >= 5 }?.let { data.start.plusDays(it.toLong()) }
                var run = 0; var longest = 0
                for (i in 0 until data.elapsed) { if (data.counts[i] >= 5) { run++; longest = max(longest, run) } else run = 0 }
                Milestone(Icons.Default.Star, "İlk tam gün", firstFull?.format(dayFmt) ?: "henüz yok", if (firstFull != null) "✓" else "–")
                Milestone(Icons.Default.DateRange, "En uzun seri", "üst üste beş vakit", "$longest gün")
                Milestone(Icons.Default.Done, "Okunan", "Kur'an ve kitaplar", "$pagesSum s.")
                Milestone(Icons.Default.CheckCircle, "Kılınan kaza", "plan: günde ${store.kazaRate}", "$kazaSum")
            }
        }

        // ---- Namazları ne zaman kılıyoruz: yalnızca "Tümü"de (kısa dönemde ortalama yanıltır)
        if (period == 3) {
            InfoCard(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.xl, bottom = Space.l).fillMaxWidth()) {
                Text("NAMAZLARINI NE ZAMAN KILIYORSUN", color = Pal.Muted, fontSize = Type.micro, letterSpacing = .8.sp)
                Spacer(Modifier.height(Space.s))
                Vakit.entries.forEach { vk ->
                    val fs = data.fracs[vk.name].orEmpty()
                    val avg = if (fs.isEmpty()) null else fs.average().toFloat()
                    Row(Modifier.padding(top = Space.s), verticalAlignment = Alignment.CenterVertically) {
                        Text(vk.ad, Modifier.width(58.dp), color = Pal.Text, fontSize = Type.body2)
                        Box(Modifier.weight(1f).height(8.dp).clip(CircleShape).background(Pal.Track)) {
                            // the first third of the window is "vaktinde"
                            Box(Modifier.fillMaxWidth(1f / 3).fillMaxHeight().background(Pal.Gold.copy(alpha = .18f)))
                            if (avg != null) Box(Modifier.fillMaxWidth(avg).fillMaxHeight().clip(CircleShape).background(Pal.Gold))
                        }
                        Spacer(Modifier.width(Space.s))
                        val ms = data.mins[vk.name].orEmpty()
                        val avgMin = if (ms.isEmpty()) null else ms.average().toInt()
                        Text(
                            when { avgMin == null -> "—"; avgMin >= 60 -> "${avgMin / 60}sa ${avgMin % 60}dk"; else -> "${avgMin}dk" },
                            Modifier.width(64.dp), color = if (avgMin == null) Pal.Muted else Pal.Text, fontSize = Type.label, textAlign = TextAlign.End,
                        )
                    }
                }
                Spacer(Modifier.height(Space.xs))
                Text("vaktin başı → sonraki vakit · açık bölge: ilk üçte bir · sağda: girişten kaç dakika sonra", color = Pal.Muted, fontSize = Type.micro, lineHeight = 15.sp)
                Spacer(Modifier.height(Space.s))
                Text(
                    "Not: Bu sayı bir fazilet ölçüsü değil; kendi düzenini görmen için. " +
                        "Hanefi'de her vakitte erken kılmak daha faziletli sayılsa da, niyet ve istikrar öndedir.",
                    color = Pal.Muted, fontSize = Type.micro, lineHeight = 16.sp,
                )
            }
        }
        Spacer(Modifier.height(Space.xl))
    }
}

/** Secondary tabs for a section's periods: plain labels, a full-width gold line under the selected one. */
@Composable
private fun SubTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.s).fillMaxWidth()) {
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val on = i == selected
                Box(Modifier.weight(1f).height(44.dp).clickable { onSelect(i) }, contentAlignment = Alignment.Center) {
                    Text(label, color = if (on) Pal.Text else Pal.Muted, fontSize = Type.body2, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
                    if (on) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(2.dp).background(Pal.Gold))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.Line))
    }
}

@Composable
private fun RowScope.BigStat(value: String, label: String, start: Boolean = false) {
    Column(Modifier.weight(1f), horizontalAlignment = if (start) Alignment.Start else Alignment.CenterHorizontally) {
        Text(value, style = NumStyle, fontSize = Type.num, lineHeight = Type.num, color = Pal.Text)
        Text(label, color = Pal.Muted, fontSize = Type.label)
    }
}

@Composable
private fun CurveAxis(left: String, right: String) {
    Row(Modifier.padding(horizontal = Space.xl, vertical = Space.xs).fillMaxWidth()) {
        Text(left, Modifier.weight(1f), color = Pal.Muted.copy(alpha = .7f), fontSize = Type.micro)
        Text(right, color = Pal.Muted.copy(alpha = .7f), fontSize = Type.micro)
    }
}

/** Smooth gold curve with a fading fill; [cumulative] scales to its own max, otherwise 0..1 with a "beş vakit" line. */
@Composable
private fun PulseCurve(values: List<Float>, cumulative: Boolean, modifier: Modifier) {
    val grow by animateFloatAsState(1f, tween(900), label = "pulse")
    Box(modifier) {
    // one day of data is just a dot: say why
    if (values.size < 2) Text("Eğri birkaç gün sonra belirecek.", Modifier.align(Alignment.BottomCenter).padding(bottom = Space.l),
        color = Pal.Muted.copy(alpha = .7f), fontSize = Type.label)
    Canvas(Modifier.fillMaxSize()) {
        if (values.isEmpty()) return@Canvas
        val top = 16.dp.toPx(); val bottom = 8.dp.toPx()
        val maxV = if (cumulative) values.max().coerceAtLeast(1f) else 1f
        val x = { i: Int -> if (values.size < 2) size.width / 2 else size.width * i / (values.size - 1) }
        val y = { v: Float -> size.height - bottom - (v / maxV) * (size.height - top - bottom) * grow }
        if (!cumulative) drawLine(Pal.Line, Offset(0f, y(1f)), Offset(size.width, y(1f)), 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 10f)))
        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) {
                val xm = (x(i - 1) + x(i)) / 2
                cubicTo(xm, y(values[i - 1]), xm, y(values[i]), x(i), y(values[i]))
            }
        }
        val fill = Path().apply { addPath(line); lineTo(x(values.size - 1), size.height); lineTo(x(0), size.height); close() }
        drawPath(fill, Brush.verticalGradient(listOf(Pal.Gold.copy(alpha = .38f), Color.Transparent)))
        drawPath(line, Pal.Gold, style = Stroke(2.2.dp.toPx(), cap = StrokeCap.Round))
        val end = Offset(x(values.size - 1), y(values.last()))
        drawCircle(Pal.Gold.copy(alpha = .2f), 10.dp.toPx(), end)
        drawCircle(Pal.Gold, 5.dp.toPx(), end)
    }
    }
}

/** Hafta: 5 vakit × 7 gün, every prayer on its own; reading pages per day under it. */
@Composable
private fun WeekMap(start: LocalDate, from: Int, marks: Array<IntArray>, pages: IntArray, pagesSum: Int, tr: java.util.Locale) {
    val nameW = 64.dp
    Column(Modifier.padding(start = Space.xl, end = Space.xl, top = Space.xl)) {
        Row {
            Spacer(Modifier.width(nameW))
            for (i in 0 until 7) {
                val d = start.plusDays(i.toLong()); val t = i == 6
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, tr), color = if (t) Pal.Gold else Pal.Muted, fontSize = Type.micro, fontWeight = FontWeight.SemiBold)
                    Text("${d.dayOfMonth}", style = NumStyle, color = if (t) Pal.Gold else Pal.Text, fontSize = Type.body)
                }
            }
        }
        Vakit.entries.forEach { vk ->
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(vk.ad, Modifier.width(nameW), color = Pal.Text, fontSize = Type.body2)
                for (i in 0 until 7) Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    val m = if (i < from) -1 else marks[i][vk.ordinal]
                    when (m) {
                        1 -> Box(Modifier.size(22.dp).clip(CircleShape).background(Pal.Gold), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, null, tint = Pal.Ground, modifier = Modifier.size(14.dp))
                        }
                        2 -> Box(Modifier.size(20.dp).border(1.5.dp, Pal.Warn, CircleShape))
                        0 -> Box(Modifier.size(20.dp).border(1.dp, Pal.Border, CircleShape))
                        else -> Box(Modifier.size(4.dp).clip(CircleShape).background(Pal.Border))   // before the app was installed
                    }
                }
            }
        }
        // the vakit missed most often this week
        val miss = Vakit.entries.map { vk -> vk to (from until 7).count { marks[it][vk.ordinal] == 2 } }.maxByOrNull { it.second }
        Text(
            if (miss == null || miss.second == 0) "Bu hafta hiç vakit kaçmadı, elhamdülillah." else "Bu hafta en çok ${miss.first.ad} kaçtı (${miss.second} gün).",
            Modifier.padding(top = Space.s), color = Pal.Muted, fontSize = Type.body2,
        )
        SectionLabel("OKUMA · $pagesSum SAYFA", Modifier.padding(top = Space.xl, bottom = Space.s))
        val maxP = pages.take(7).maxOrNull()?.coerceAtLeast(1) ?: 1
        Row(verticalAlignment = Alignment.Bottom) {
            Spacer(Modifier.width(nameW))
            for (i in 0 until 7) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.width(12.dp).height((44f * pages[i] / maxP).coerceAtLeast(2f).dp).clip(CircleShape).background(Pal.Gold.copy(alpha = .7f)))
                Text("${pages[i]}", Modifier.padding(top = 4.dp), color = Pal.Muted, fontSize = Type.micro)
            }
        }
    }
}

/** Yıl: the year as a heatmap, 53 weeks × 7 days; days before the app and after today stay empty. */
@Composable
private fun YearHeat(start: LocalDate, days: Int, from: Int, elapsed: Int, counts: IntArray, tr: java.util.Locale) {
    val heatRamp = listOf(Pal.Track, Color(0xFF4A3D27), Color(0xFF6E5A35), Color(0xFF977A45), Color(0xFFC29B5A), Pal.Gold)
    val noData = Pal.Track.copy(alpha = .35f)
    val missed = Color(0xFF55657F) // "hiç" ile karışmasın ama 3:1 kontrastı geçsin
    val monthFmt = java.time.format.DateTimeFormatter.ofPattern("MMM", tr)
    Column(Modifier.padding(top = Space.xl)) {
        BoxWithConstraints(Modifier.padding(horizontal = Space.xl)) {
            val gap = 2.dp
            val firstDow = start.dayOfWeek.value - 1
            val cols = (firstDow + days + 6) / 7
            val cell = 9.dp
            val totalW = cell * cols + gap * (cols - 1)
            val hScroll = rememberScrollState()
            LaunchedEffect(totalW) { hScroll.scrollTo(hScroll.maxValue) }   // open on today's end of the year
            Box(Modifier.fillMaxWidth().horizontalScroll(hScroll)) {
                Column(Modifier.width(totalW)) {
                    Row(Modifier.height(16.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (w in 0 until cols) {
                            val d = start.plusDays((w * 7 - firstDow).toLong())
                            val prev = start.plusDays(((w - 1) * 7 - firstDow).toLong())
                            Box(Modifier.width(cell)) {
                                if (w == 0 || d.monthValue != prev.monthValue) Text(
                                    d.format(monthFmt), Modifier.wrapContentWidth(Alignment.Start, unbounded = true),
                                    color = Pal.Muted, fontSize = 9.sp, maxLines = 1, softWrap = false,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    for (row in 0 until 7) Row(Modifier.padding(top = if (row == 0) 0.dp else gap), horizontalArrangement = Arrangement.spacedBy(gap)) {
                        for (w in 0 until cols) {
                            val idx = w * 7 - firstDow + row
                            val color = when {
                                idx < 0 || idx >= days -> Color.Transparent
                                idx < from || idx >= elapsed -> noData
                                counts[idx] == 0 -> missed
                                else -> heatRamp[counts[idx].coerceIn(0, 5)]
                            }
                            Box(
                                Modifier.size(cell).clip(RoundedCornerShape(2.dp)).background(color)
                                    .then(if (idx == elapsed - 1) Modifier.border(1.dp, Pal.Text.copy(alpha = .8f), RoundedCornerShape(2.dp)) else Modifier),
                            )
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(start = Space.xl, top = Space.s), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text("hiç", color = Pal.Muted, fontSize = Type.micro)
            (listOf(missed) + heatRamp.drop(1)).forEach { Box(Modifier.size(9.dp).clip(RoundedCornerShape(2.dp)).background(it)) }
            Text("beş vakit", color = Pal.Muted, fontSize = Type.micro)
        }
    }
}

@Composable
private fun Milestone(icon: ImageVector, title: String, sub: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(Pal.Gold.copy(alpha = .15f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Pal.Gold, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f).padding(start = Space.m)) {
            Text(title, color = Pal.Text, fontSize = Type.body2, fontWeight = FontWeight.Medium)
            Text(sub, color = Pal.Muted, fontSize = Type.label)
        }
        Text(value, style = NumStyle, fontSize = Type.h2, color = Pal.Text)
    }
    HorizontalDivider(color = Pal.Line)
}

/** Özet › ⓘ: how each petal of the rose opens, with today's numbers beside each one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoseInfoSheet(petals: List<Petal>, kazaRate: Int, onClose: () -> Unit) {
    val how = mapOf(
        "Namaz" to "Beş vaktin her biri için \"Kıldım\" dedikçe açılır. Beşi de kılınınca tam açık.",
        "Vaktinde" to "\"Kıldım\"a vaktin ilk üçte birinde basılan namazları sayar. Erken kılınan her vakit yaprağı biraz daha açar.",
        "Sayfa" to "Okuma'daki bugünkü hedefe göre açılır: planlı okumaların bugünkü payı ya da Kur'an'ın günlük hedefi.",
        "Kaza" to "Kaza planına göre açılır (günde $kazaRate kaza). Kaza borcun yoksa yaprak hep açıktır.",
        "Zikir" to "Zikir listendeki her zikrin bugünkü tamamlanma oranının ortalamasıyla açılır. Serbest zikir ve hedef sonrası ekstralar sayılmaz.",
    )
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Pal.Card) {
        Column(Modifier.padding(horizontal = Space.xl).navigationBarsPadding().padding(bottom = Space.l).verticalScroll(rememberScrollState())) {
            Text("Gül nasıl açar?", style = NumStyle, fontSize = Type.h2, color = Pal.Text)
            Text("Gülün beş yaprağı bugünün beş işidir. Gün imsakta başlar ve gül her imsakta yeniden gonca olur.",
                Modifier.padding(top = Space.xs, bottom = Space.s), color = Pal.Muted, fontSize = Type.body2, lineHeight = 19.sp)
            petals.forEach { p ->
                Row(Modifier.padding(top = Space.m)) {
                    Column(Modifier.weight(1f)) {
                        Text(p.name, color = Pal.Text, fontSize = Type.body, fontWeight = FontWeight.SemiBold)
                        Text(how[p.name] ?: "", color = Pal.Muted, fontSize = Type.body2, lineHeight = 19.sp)
                    }
                    Text(if (p.progress >= 1f) "✓" else p.label, Modifier.padding(start = Space.m),
                        style = NumStyle, fontSize = Type.h2, color = if (p.progress >= 1f) Pal.Gold else Pal.Text)
                }
            }
            HorizontalDivider(Modifier.padding(vertical = Space.l), color = Pal.Line)
            Text("Beş yaprağın hepsi açılınca gonca da çözülür; gülün kalbinde altın bir ışık yanar ve tozlar yükselir. Tamamlanan yaprağın etiketi kaybolur, yalnız eksik işler etiketli kalır. Gülü parmağınla çevirebilirsin.",
                color = Pal.Muted, fontSize = Type.body2, lineHeight = 19.sp)
        }
    }
}

/** One number under the Özet rose: big Cormorant value, small "/of", a thin bar (null = no bar), name. */
@Composable
private fun RowScope.StatColumn(value: String, of: String, progress: Float?, name: String) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = NumStyle, color = Pal.Text, fontSize = Type.num, lineHeight = Type.num)
            if (of.isNotEmpty()) Text(of, Modifier.padding(start = 2.dp, bottom = 3.dp), color = Pal.Muted, fontSize = Type.label)
        }
        Box(Modifier.padding(horizontal = Space.l, vertical = Space.s).fillMaxWidth().height(3.dp).clip(CircleShape)
            .background(if (progress == null) Color.Transparent else Pal.Track)) {
            if (progress != null) Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(CircleShape).background(Pal.Gold))
        }
        Text(name, color = Pal.Muted, fontSize = Type.body2)
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








