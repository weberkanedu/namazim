package com.namazim

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

// Zikirhane design Z1 "Nur" (design/design-system/zikirhane-secenekler.html): the zikir in Arabic with its meaning,
// a gold arc that fills once to the target (no turns), a ripple and rising light motes where the screen is touched,
// and a glow that grows with the progress.

/** How far the ring is filled, 0..1: once to the target; the free counter (no target) turns every 33 like a tesbih. */
fun zikirProgress(count: Int, target: Int?): Float = when {
    target == null -> if (count > 0 && count % 33 == 0) 1f else (count % 33) / 33f
    else -> (count.toFloat() / target).coerceIn(0f, 1f)
}

/** Arabic text and a short Turkish meaning for well-known zikirs; null for the user's own wording. */
fun zikirArabic(name: String): Pair<String, String>? = ARABIC[norm(name)]

private fun norm(s: String) = s.lowercase(Locale("tr")).replace('â', 'a').replace('î', 'i').replace('û', 'u')
    .filter { it.isLetter() }

private val ARABIC = listOf(
    "Sübhânallah" to ("سُبْحَانَ اللّٰهِ" to "Allah her türlü noksanlıktan uzaktır"),
    "Elhamdülillah" to ("اَلْحَمْدُ لِلّٰهِ" to "Hamd Allah içindir"),
    "Allâhu ekber" to ("اَللّٰهُ اَكْبَرُ" to "Allah en büyüktür"),
    "Lâ ilâhe illallah" to ("لَا إِلٰهَ إِلَّا اللّٰهُ" to "Allah'tan başka ilah yoktur"),
    "Estağfirullah" to ("أَسْتَغْفِرُ اللّٰهَ" to "Allah'tan bağışlanma dilerim"),
    "Salavat" to ("اَللّٰهُمَّ صَلِّ عَلٰى مُحَمَّدٍ" to "Allah'ım, Muhammed'e salât eyle"),
    "Sübhânallahi ve bihamdihî" to ("سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ" to "Allah'ı hamdiyle tesbih ederim"),
    "Lâ havle ve lâ kuvvete illâ billâh" to ("لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰهِ" to "Güç ve kuvvet ancak Allah'tandır"),
    "Hasbünallah" to ("حَسْبُنَا اللّٰهُ وَنِعْمَ الْوَكِيلُ" to "Allah bize yeter, O ne güzel vekildir"),
    "Yâ Latîf" to ("يَا لَطِيفُ" to "Ey lütfu sonsuz, en ince işleri bilen Allah"),
    "Yâ Hayyu yâ Kayyûm" to ("يَا حَيُّ يَا قَيُّومُ" to "Ey diri olan, her şeyi ayakta tutan"),
    "Yâ Rahmân" to ("يَا رَحْمٰنُ" to "Ey rahmeti her şeyi kuşatan"),
    "Yâ Rahîm" to ("يَا رَحِيمُ" to "Ey merhametli olan"),
).associate { (k, v) -> norm(k) to v }

/** One line under the counter, a different one each day. */
private val ZIKIR_AYET = listOf(
    "Kalpler ancak Allah'ı anmakla huzur bulur." to "Ra'd 13:28",
    "Beni anın ki ben de sizi anayım." to "Bakara 2:152",
    "Ey iman edenler! Allah'ı çokça anın." to "Ahzâb 33:41",
    "Allah'ı çok anın ki kurtuluşa eresiniz." to "Cuma 62:10",
    "Rabbinin adını an ve bütün varlığınla O'na yönel." to "Müzzemmil 73:8",
    "Allah'ı anmak elbette en büyük ibadettir." to "Ankebût 29:45",
    "Rabbini anan ile anmayanın durumu, diri ile ölü gibidir." to "Hadis · Buhârî, Daavât 66",
)
fun ayetOfDay(day: LocalDate = LocalDate.now()) = ZIKIR_AYET[day.dayOfYear % ZIKIR_AYET.size]

/** Arabic (when known), the name, the meaning. */
@Composable
fun NurHead(name: String) {
    val ar = zikirArabic(name)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (ar != null) Text(ar.first, style = TextStyle(fontSize = 32.sp, lineHeight = 44.sp, color = Color(0xFFF2D79B),
            textAlign = TextAlign.Center, shadow = Shadow(Pal.Gold.copy(alpha = .55f), blurRadius = 22f)))
        Text(name, style = NumStyle, fontSize = Type.h2, color = Pal.Text, textAlign = TextAlign.Center)
        if (ar != null) Text(ar.second, color = Pal.Muted, fontSize = Type.label, textAlign = TextAlign.Center)
    }
}

/** The gold arc on a dark track, a bright bead at its head; [glow] flashes when a target is reached. */
@Composable
fun NurRing(count: Int, target: Int?, glow: Float, modifier: Modifier) {
    val f = zikirProgress(count, target)
    val shown = remember { Animatable(f) }
    // forward it eases, a new turn (free counter) or a chip change snaps
    LaunchedEffect(f) { if (f < shown.value) shown.snapTo(f) else shown.animateTo(f, tween(250)) }
    Canvas(modifier) {
        val s = 6.dp.toPx()
        val r = size.minDimension / 2 - 14.dp.toPx()
        val box = Size(2 * r, 2 * r)
        val tl = Offset(center.x - r, center.y - r)
        if (glow > 0f) drawCircle(Pal.Gold.copy(alpha = .18f * glow), r + 18.dp.toPx())
        drawCircle(Pal.Track, r, style = Stroke(s))
        val v = shown.value
        if (v > 0f) {
            drawArc(Pal.Gold.copy(alpha = .20f), -90f, 360f * v, false, tl, box, style = Stroke(s * 3.2f, cap = StrokeCap.Round))
            drawArc(Pal.Gold, -90f, 360f * v, false, tl, box, style = Stroke(s, cap = StrokeCap.Round))
            val a = Math.toRadians(-90.0 + 360.0 * v)
            val p = Offset(center.x + r * cos(a).toFloat(), center.y + r * sin(a).toFloat())
            drawCircle(Color(0xFFFFE7B0).copy(alpha = .30f), 15.dp.toPx(), p)
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF1CF), Pal.Gold, Color(0xFF8A6420)),
                center = p + Offset(-3.dp.toPx(), -3.dp.toPx()), radius = 13.dp.toPx()), 8.5.dp.toPx(), p)
        }
    }
}

/** A touch on the counter: a ring spreading from [at] and six motes of light rising. Times in ms of [frameClock]. */
class Burst(val at: Offset, val t0: Long, val drift: List<Float>)

fun frameClock() = System.nanoTime() / 1_000_000

@Composable
fun NurBursts(bursts: SnapshotStateList<Burst>, modifier: Modifier) {
    var now by remember { mutableLongStateOf(frameClock()) }
    // runs only while something is on screen, then the canvas is idle
    LaunchedEffect(bursts.isNotEmpty()) {
        while (bursts.isNotEmpty()) {
            withFrameMillis { now = frameClock() }
            bursts.removeAll { now - it.t0 > 1400 }
        }
    }
    Canvas(modifier) {
        bursts.forEach { b ->
            val age = (now - b.t0).coerceAtLeast(0).toFloat()
            val ring = (age / 800f).coerceAtMost(1f)
            if (ring < 1f) drawCircle(Pal.Gold.copy(alpha = .7f * (1 - ring)), 110.dp.toPx() * ring, b.at, style = Stroke(2.dp.toPx()))
            val m = (age / 1400f).coerceAtMost(1f)
            val rise = 1 - (1 - m) * (1 - m)
            b.drift.forEachIndexed { i, dx ->
                val p = Offset(b.at.x + dx * rise, b.at.y - 140.dp.toPx() * rise + i * 3.dp.toPx())
                drawCircle(Color(0xFFFFE7B0).copy(alpha = .35f * (1 - m)), 6.dp.toPx(), p)
                drawCircle(Color(0xFFFFF4DC).copy(alpha = 1 - m), 2.5.dp.toPx(), p)
            }
        }
    }
}
