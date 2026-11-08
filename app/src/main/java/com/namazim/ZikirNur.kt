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
// a gold arc that fills once to the target (no turns), light motes rising where the screen is touched,
// and a glow that grows with the progress.

/** How far the ring is filled, 0..1: once to the target; the free counter (no target) turns every 33 like a tesbih. */
fun zikirProgress(count: Int, target: Int?): Float = when {
    target == null -> if (count > 0 && count % 33 == 0) 1f else (count % 33) / 33f
    else -> (count.toFloat() / target).coerceIn(0f, 1f)
}

/** Arabic text and a short Turkish meaning for well-known zikirs; null for the user's own wording. */
fun zikirArabic(name: String): Pair<String, String>? = BY_NAME[norm(name)]

/** The zikir's own Arabic (typed or picked when it was added), else the built-in one. */
fun zikirArabic(z: Zikir): Pair<String, String>? = z.ar?.let { it to (z.mean ?: "") } ?: zikirArabic(z.name)

/** Built-in entries whose name contains what was typed (loose: no accents, "ya" optional); best first, at most [max]. */
fun suggestZikir(typed: String, max: Int = 3): List<Triple<String, String, String>> {
    val k = norm(typed)
    if (k.length < 2) return emptyList()
    return ZIKIR_LIST.filter { norm(it.first).contains(k) }.sortedBy { norm(it.first).indexOf(k) }.take(max)
}

// loose spelling: "ya latif" finds "Yâ Latîf"
private fun norm(s: String) = s.lowercase(Locale("tr")).map {
    when (it) { 'â' -> 'a'; 'î', 'ı' -> 'i'; 'û' -> 'u'; 'ş' -> 's'; 'ç' -> 'c'; 'ğ' -> 'g'; 'ö' -> 'o'; 'ü' -> 'u'; else -> it }
}.filter { it.isLetter() }.joinToString("")

/** Name, Arabic, short meaning: the well-known zikirs, then the 99 names (Tirmizî order, "Allah" is in the first part). */
val ZIKIR_LIST = listOf(
    Triple("Sübhânallah", "سُبْحَانَ اللّٰهِ", "Allah her türlü noksanlıktan uzaktır"),
    Triple("Elhamdülillah", "اَلْحَمْدُ لِلّٰهِ", "Hamd Allah içindir"),
    Triple("Allâhu ekber", "اَللّٰهُ اَكْبَرُ", "Allah en büyüktür"),
    Triple("Lâ ilâhe illallah", "لَا إِلٰهَ إِلَّا اللّٰهُ", "Allah'tan başka ilah yoktur"),
    Triple("Estağfirullah", "أَسْتَغْفِرُ اللّٰهَ", "Allah'tan bağışlanma dilerim"),
    Triple("Estağfirullâhe'l-azîm ve etûbu ileyh", "أَسْتَغْفِرُ اللّٰهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ", "Yüce Allah'tan bağışlanma diler, O'na tövbe ederim"),
    Triple("Salavat", "اَللّٰهُمَّ صَلِّ عَلٰى مُحَمَّدٍ", "Allah'ım, Muhammed'e salât eyle"),
    Triple("Sübhânallahi ve bihamdihî", "سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ", "Allah'ı hamdiyle tesbih ederim"),
    Triple("Sübhânallâhi'l-azîm", "سُبْحَانَ اللّٰهِ الْعَظِيمِ", "Yüce Allah her türlü noksanlıktan uzaktır"),
    Triple("Lâ havle ve lâ kuvvete illâ billâh", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰهِ", "Güç ve kuvvet ancak Allah'tandır"),
    Triple("Hasbünallah", "حَسْبُنَا اللّٰهُ وَنِعْمَ الْوَكِيلُ", "Allah bize yeter, O ne güzel vekildir"),
    Triple("Hasbiyallah", "حَسْبِيَ اللّٰهُ", "Allah bana yeter"),
    Triple("Yunus duası", "لَا إِلٰهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ", "Senden başka ilah yoktur, seni tenzih ederim; ben zalimlerden oldum"),
    Triple("Bismillâh", "بِسْمِ اللّٰهِ", "Allah'ın adıyla"),
    Triple("Mâşâallah", "مَا شَاءَ اللّٰهُ", "Allah ne güzel dilemiş"),
    Triple("Yâ Hayyu yâ Kayyûm", "يَا حَيُّ يَا قَيُّومُ", "Ey diri olan, her şeyi ayakta tutan"),
    Triple("Allah", "اَللّٰهُ", "Bütün güzel isimlerin sahibi"),
    Triple("Yâ Rahmân", "يَا رَحْمٰنُ", "Dünyada bütün yaratılmışlara merhamet eden"),
    Triple("Yâ Rahîm", "يَا رَحِيمُ", "Ahirette müminlere merhamet eden"),
    Triple("Yâ Melik", "يَا مَلِكُ", "Mülkün, kâinatın sahibi"),
    Triple("Yâ Kuddûs", "يَا قُدُّوسُ", "Her eksiklikten uzak olan"),
    Triple("Yâ Selâm", "يَا سَلَامُ", "Esenlik veren"),
    Triple("Yâ Mü'min", "يَا مُؤْمِنُ", "Güven veren"),
    Triple("Yâ Müheymin", "يَا مُهَيْمِنُ", "Gözetip koruyan"),
    Triple("Yâ Azîz", "يَا عَزِيزُ", "İzzet sahibi, mağlup edilmeyen"),
    Triple("Yâ Cebbâr", "يَا جَبَّارُ", "Dilediğini yaptıran, kırıkları onaran"),
    Triple("Yâ Mütekebbir", "يَا مُتَكَبِّرُ", "Büyüklükte eşi olmayan"),
    Triple("Yâ Hâlık", "يَا خَالِقُ", "Yaratan"),
    Triple("Yâ Bâri'", "يَا بَارِئُ", "Kusursuz yaratan"),
    Triple("Yâ Musavvir", "يَا مُصَوِّرُ", "Her şeye şekil veren"),
    Triple("Yâ Gaffâr", "يَا غَفَّارُ", "Günahları çokça bağışlayan"),
    Triple("Yâ Kahhâr", "يَا قَهَّارُ", "Her şeye galip gelen"),
    Triple("Yâ Vehhâb", "يَا وَهَّابُ", "Karşılıksız veren"),
    Triple("Yâ Rezzâk", "يَا رَزَّاقُ", "Rızık veren"),
    Triple("Yâ Fettâh", "يَا فَتَّاحُ", "Hayır kapılarını açan"),
    Triple("Yâ Alîm", "يَا عَلِيمُ", "Her şeyi bilen"),
    Triple("Yâ Kâbıd", "يَا قَابِضُ", "Dilediğine daraltan"),
    Triple("Yâ Bâsıt", "يَا بَاسِطُ", "Dilediğine genişleten"),
    Triple("Yâ Hâfıd", "يَا خَافِضُ", "Dilediğini alçaltan"),
    Triple("Yâ Râfi'", "يَا رَافِعُ", "Dilediğini yükselten"),
    Triple("Yâ Muiz", "يَا مُعِزُّ", "İzzet veren"),
    Triple("Yâ Müzil", "يَا مُذِلُّ", "Dilediğini zillete düşüren"),
    Triple("Yâ Semî'", "يَا سَمِيعُ", "Her şeyi işiten"),
    Triple("Yâ Basîr", "يَا بَصِيرُ", "Her şeyi gören"),
    Triple("Yâ Hakem", "يَا حَكَمُ", "Hükmeden"),
    Triple("Yâ Adl", "يَا عَدْلُ", "Mutlak adalet sahibi"),
    Triple("Yâ Latîf", "يَا لَطِيفُ", "Lütfu sonsuz, en ince işleri bilen"),
    Triple("Yâ Habîr", "يَا خَبِيرُ", "Her şeyden haberdar olan"),
    Triple("Yâ Halîm", "يَا حَلِيمُ", "Yumuşak davranan, cezada acele etmeyen"),
    Triple("Yâ Azîm", "يَا عَظِيمُ", "Pek yüce"),
    Triple("Yâ Gafûr", "يَا غَفُورُ", "Çok bağışlayan"),
    Triple("Yâ Şekûr", "يَا شَكُورُ", "Az amele çok sevap veren"),
    Triple("Yâ Aliyy", "يَا عَلِيُّ", "Yüceler yücesi"),
    Triple("Yâ Kebîr", "يَا كَبِيرُ", "Pek büyük"),
    Triple("Yâ Hafîz", "يَا حَفِيظُ", "Koruyan"),
    Triple("Yâ Mukît", "يَا مُقِيتُ", "Her canlının gıdasını veren"),
    Triple("Yâ Hasîb", "يَا حَسِيبُ", "Hesaba çeken"),
    Triple("Yâ Celîl", "يَا جَلِيلُ", "Celal sahibi"),
    Triple("Yâ Kerîm", "يَا كَرِيمُ", "Çok cömert"),
    Triple("Yâ Rakîb", "يَا رَقِيبُ", "Her şeyi gözeten"),
    Triple("Yâ Mücîb", "يَا مُجِيبُ", "Duaları kabul eden"),
    Triple("Yâ Vâsi'", "يَا وَاسِعُ", "İlmi ve rahmeti her şeyi kuşatan"),
    Triple("Yâ Hakîm", "يَا حَكِيمُ", "Hikmet sahibi"),
    Triple("Yâ Vedûd", "يَا وَدُودُ", "Kullarını seven ve sevilen"),
    Triple("Yâ Mecîd", "يَا مَجِيدُ", "Şanı yüce"),
    Triple("Yâ Bâis", "يَا بَاعِثُ", "Ölüleri dirilten"),
    Triple("Yâ Şehîd", "يَا شَهِيدُ", "Her şeye şahit olan"),
    Triple("Yâ Hakk", "يَا حَقُّ", "Varlığı gerçek olan"),
    Triple("Yâ Vekîl", "يَا وَكِيلُ", "Güvenilip dayanılan"),
    Triple("Yâ Kaviyy", "يَا قَوِيُّ", "Çok güçlü"),
    Triple("Yâ Metîn", "يَا مَتِينُ", "Çok sağlam"),
    Triple("Yâ Veliyy", "يَا وَلِيُّ", "Müminlerin dostu, yardımcısı"),
    Triple("Yâ Hamîd", "يَا حَمِيدُ", "Övülmeye layık"),
    Triple("Yâ Muhsî", "يَا مُحْصِي", "Her şeyi tek tek sayan"),
    Triple("Yâ Mübdi'", "يَا مُبْدِئُ", "İlk kez yaratan"),
    Triple("Yâ Muîd", "يَا مُعِيدُ", "Tekrar yaratan"),
    Triple("Yâ Muhyî", "يَا مُحْيِي", "Hayat veren"),
    Triple("Yâ Mümît", "يَا مُمِيتُ", "Ölümü yaratan"),
    Triple("Yâ Hayy", "يَا حَيُّ", "Diri, ölümsüz"),
    Triple("Yâ Kayyûm", "يَا قَيُّومُ", "Her şeyi ayakta tutan"),
    Triple("Yâ Vâcid", "يَا وَاجِدُ", "İstediğini bulan"),
    Triple("Yâ Mâcid", "يَا مَاجِدُ", "Şanı büyük"),
    Triple("Yâ Vâhid", "يَا وَاحِدُ", "Tek"),
    Triple("Yâ Samed", "يَا صَمَدُ", "Hiçbir şeye muhtaç olmayan"),
    Triple("Yâ Kâdir", "يَا قَادِرُ", "Gücü yeten"),
    Triple("Yâ Muktedir", "يَا مُقْتَدِرُ", "Her şeye kudreti yeten"),
    Triple("Yâ Mukaddim", "يَا مُقَدِّمُ", "Dilediğini öne alan"),
    Triple("Yâ Muahhir", "يَا مُؤَخِّرُ", "Dilediğini geriye bırakan"),
    Triple("Yâ Evvel", "يَا أَوَّلُ", "Başlangıcı olmayan"),
    Triple("Yâ Âhir", "يَا آخِرُ", "Sonu olmayan"),
    Triple("Yâ Zâhir", "يَا ظَاهِرُ", "Varlığı apaçık olan"),
    Triple("Yâ Bâtın", "يَا بَاطِنُ", "Mahiyeti gizli olan"),
    Triple("Yâ Vâlî", "يَا وَالِي", "Her şeyi yöneten"),
    Triple("Yâ Müteâlî", "يَا مُتَعَالِي", "Pek yüce"),
    Triple("Yâ Berr", "يَا بَرُّ", "İyilik eden"),
    Triple("Yâ Tevvâb", "يَا تَوَّابُ", "Tövbeleri kabul eden"),
    Triple("Yâ Müntakim", "يَا مُنْتَقِمُ", "Suçluları cezalandıran"),
    Triple("Yâ Afüvv", "يَا عَفُوُّ", "Affeden"),
    Triple("Yâ Raûf", "يَا رَؤُوفُ", "Çok şefkatli"),
    Triple("Yâ Mâlikü'l-mülk", "يَا مَالِكَ الْمُلْكِ", "Mülkün ebedî sahibi"),
    Triple("Yâ Zü'l-celâli ve'l-ikrâm", "يَا ذَا الْجَلَالِ وَالْإِكْرَامِ", "Celal ve ikram sahibi"),
    Triple("Yâ Muksit", "يَا مُقْسِطُ", "Adaletle hükmeden"),
    Triple("Yâ Câmi'", "يَا جَامِعُ", "İstediğini bir araya toplayan"),
    Triple("Yâ Ganiyy", "يَا غَنِيُّ", "Hiçbir şeye muhtaç olmayan"),
    Triple("Yâ Mugnî", "يَا مُغْنِي", "Zengin kılan"),
    Triple("Yâ Mâni'", "يَا مَانِعُ", "Dilemediğine engel olan"),
    Triple("Yâ Dârr", "يَا ضَارُّ", "Zararı yaratan"),
    Triple("Yâ Nâfi'", "يَا نَافِعُ", "Faydayı yaratan"),
    Triple("Yâ Nûr", "يَا نُورُ", "Âlemleri nurlandıran"),
    Triple("Yâ Hâdî", "يَا هَادِي", "Hidayet veren"),
    Triple("Yâ Bedî'", "يَا بَدِيعُ", "Eşsiz yaratan"),
    Triple("Yâ Bâkî", "يَا بَاقِي", "Varlığı sonsuz olan"),
    Triple("Yâ Vâris", "يَا وَارِثُ", "Her şeyin asıl sahibi"),
    Triple("Yâ Reşîd", "يَا رَشِيدُ", "Doğru yolu gösteren"),
    Triple("Yâ Sabûr", "يَا صَبُورُ", "Çok sabırlı"),
)
private val BY_NAME = ZIKIR_LIST.associate { norm(it.first) to (it.second to it.third) }

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
fun NurHead(name: String, own: Pair<String, String>? = null) {
    val ar = own ?: zikirArabic(name)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (ar != null) Text(ar.first, style = TextStyle(fontSize = 32.sp, lineHeight = 44.sp, color = Color(0xFFF2D79B),
            textAlign = TextAlign.Center, shadow = Shadow(Pal.Gold.copy(alpha = .55f), blurRadius = 22f)))
        Text(name, style = NumStyle, fontSize = Type.h2, color = Pal.Text, textAlign = TextAlign.Center)
        if (ar != null && ar.second.isNotBlank()) Text(ar.second, color = Pal.Muted, fontSize = Type.label, textAlign = TextAlign.Center)
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

/** A touch on the counter: six motes of light rising from [at]. Times in ms of [frameClock]. */
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
