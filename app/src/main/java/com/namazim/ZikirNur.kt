package com.namazim

import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.drawText
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
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
fun suggestZikir(typed: String, max: Int = 3): List<HazirZikir> {
    val k = norm(typed)
    if (k.length < 2) return emptyList()
    return ZIKIR_LIST.filter { norm(it.name).contains(k) }.sortedBy { norm(it.name).indexOf(k) }.take(max)
}

// loose spelling: "ya latif" finds "Yâ Latîf"
private fun norm(s: String) = s.lowercase(Locale("tr")).map {
    when (it) { 'â' -> 'a'; 'î', 'ı' -> 'i'; 'û' -> 'u'; 'ş' -> 's'; 'ç' -> 'c'; 'ğ' -> 'g'; 'ö' -> 'o'; 'ü' -> 'u'; else -> it }
}.filter { it.isLetter() }.joinToString("")

/** A built-in zikir: name, Arabic, short meaning, the count a hadith gives (null = none), and the hadith source. */
data class HazirZikir(val name: String, val ar: String, val mean: String, val adet: Int? = null, val kaynak: String? = null)

/** The well-known zikirs, the owner's picks from sahih hadis (design/design-system/zikir-kutuphane.html, 2026-11-08),
 *  then the 99 names (Tirmizî order, "Allah" is in the first part). */
val ZIKIR_LIST = listOf(
    HazirZikir("Sübhânallah", "سُبْحَانَ اللّٰهِ", "Allah her türlü noksanlıktan uzaktır."),
    HazirZikir("Elhamdülillah", "اَلْحَمْدُ لِلّٰهِ", "Hamd Allah içindir."),
    HazirZikir("Allâhu ekber", "اَللّٰهُ اَكْبَرُ", "Allah en büyüktür."),
    HazirZikir("Lâ ilâhe illallah", "لَا إِلٰهَ إِلَّا اللّٰهُ", "Allah'tan başka ilah yoktur.", 100, "Tirmizî, Daavât 9"),
    HazirZikir("Estağfirullah", "أَسْتَغْفِرُ اللّٰهَ", "Allah'tan bağışlanma dilerim."),
    HazirZikir("Estağfirullâhe'l-azîm ve etûbu ileyh", "أَسْتَغْفِرُ اللّٰهَ الْعَظِيمَ وَأَتُوبُ إِلَيْهِ", "Yüce Allah'tan bağışlanma diler, O'na tövbe ederim."),
    HazirZikir("Salavat", "اَللّٰهُمَّ صَلِّ عَلٰى مُحَمَّدٍ", "Allah'ım, Muhammed'e salât eyle."),
    HazirZikir("Sübhânallahi ve bihamdihî", "سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ", "Allah'ı hamdiyle tesbih ederim.", 100, "Buhârî, Daavât 65; Müslim, Zikir 28"),
    HazirZikir("Sübhânallâhi'l-azîm", "سُبْحَانَ اللّٰهِ الْعَظِيمِ", "Yüce Allah her türlü noksanlıktan uzaktır."),
    HazirZikir("Lâ havle ve lâ kuvvete illâ billâh", "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰهِ", "Güç ve kuvvet ancak Allah'tandır.", null, "Buhârî, Daavât 50; Müslim, Zikir 44"),
    HazirZikir("Hasbünallah", "حَسْبُنَا اللّٰهُ وَنِعْمَ الْوَكِيلُ", "Allah bize yeter, O ne güzel vekildir."),
    HazirZikir("Hasbiyallah", "حَسْبِيَ اللّٰهُ", "Allah bana yeter."),
    HazirZikir("Yunus duası", "لَا إِلٰهَ إِلَّا أَنْتَ سُبْحَانَكَ إِنِّي كُنْتُ مِنَ الظَّالِمِينَ", "Senden başka ilah yoktur, Seni tenzih ederim; ben zalimlerden oldum.", null, "Tirmizî, Daavât 81"),
    HazirZikir("Bismillâh", "بِسْمِ اللّٰهِ", "Allah'ın adıyla."),
    HazirZikir("Mâşâallah", "مَا شَاءَ اللّٰهُ", "Allah ne güzel dilemiş."),
    HazirZikir("Yâ Hayyu yâ Kayyûm", "يَا حَيُّ يَا قَيُّومُ", "Ey diri olan, her şeyi ayakta tutan."),
    HazirZikir("Allah", "اَللّٰهُ", "Bütün güzel isimlerin sahibi."),
    HazirZikir("Sübhânallâhi ve bihamdihî, sübhânallâhi'l-azîm", "سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ سُبْحَانَ اللّٰهِ الْعَظِيمِ", "Dile hafif, mizanda ağır, Rahmân'a sevimli iki kelime.", 100, "Buhârî, Daavât 65; Müslim, Zikir 31"),
    HazirZikir("Sübhânallâhi velhamdülillâhi ve lâ ilâhe illallâhu vallâhu ekber", "سُبْحَانَ اللّٰهِ وَالْحَمْدُ لِلّٰهِ وَلَا إِلٰهَ إِلَّا اللّٰهُ وَاللّٰهُ أَكْبَرُ", "Allah'a en sevimli dört söz.", 100, "Müslim, Âdâb 12; Zikir 32"),
    HazirZikir("Sübhânallâhi ve bihamdihî adede halkıhî", "سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ عَدَدَ خَلْقِهِ وَرِضَا نَفْسِهِ وَزِنَةَ عَرْشِهِ وَمِدَادَ كَلِمَاتِهِ", "Mahlukatı sayısınca, rızası kadar, arşının ağırlığınca ve kelimelerinin mürekkebi kadar.", 3, "Müslim, Zikir 79"),
    HazirZikir("Yatarken tesbihat (33·33·34)", "سُبْحَانَ اللّٰهِ · اَلْحَمْدُ لِلّٰهِ · اَللّٰهُ أَكْبَرُ", "33 Sübhânallâh, 33 Elhamdülillâh, 34 Allâhu ekber; bir hizmetçiden daha hayırlı.", 100, "Buhârî, Fezâilü'l-ashâb 9; Müslim, Zikir 80"),
    HazirZikir("Lâ ilâhe illallâhu vahdehû lâ şerîke leh", "لَا إِلٰهَ إِلَّا اللّٰهُ وَحْدَهُ لَا شَرِيكَ لَهُ، لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ وَهُوَ عَلٰى كُلِّ شَيْءٍ قَدِيرٌ", "Allah'tan başka ilah yoktur, tektir; mülk ve hamd O'nundur, O her şeye kadirdir.", 100, "Buhârî, Daavât 64; Müslim, Zikir 28"),
    HazirZikir("Seyyidü'l-istiğfâr", "اَللّٰهُمَّ أَنْتَ رَبِّي لَا إِلٰهَ إِلَّا أَنْتَ، خَلَقْتَنِي وَأَنَا عَبْدُكَ، وَأَنَا عَلٰى عَهْدِكَ وَوَعْدِكَ مَا اسْتَطَعْتُ، أَعُوذُ بِكَ مِنْ شَرِّ مَا صَنَعْتُ، أَبُوءُ لَكَ بِنِعْمَتِكَ عَلَيَّ، وَأَبُوءُ بِذَنْبِي فَاغْفِرْ لِي، فَإِنَّهُ لَا يَغْفِرُ الذُّنُوبَ إِلَّا أَنْتَ", "İstiğfarın efendisi.", 1, "Buhârî, Daavât 2"),
    HazirZikir("Estağfirullâhe ve etûbu ileyh", "أَسْتَغْفِرُ اللّٰهَ وَأَتُوبُ إِلَيْهِ", "Allah'tan bağışlanma diler, O'na tövbe ederim.", 100, "Buhârî, Daavât 3; Müslim, Zikir 41"),
    HazirZikir("Rabbiğfir lî ve tüb aleyye", "رَبِّ اغْفِرْ لِي وَتُبْ عَلَيَّ إِنَّكَ أَنْتَ التَّوَّابُ الرَّحِيمُ", "Rabbim, beni bağışla, tövbemi kabul et.", 100, "Ebû Dâvûd, Vitr 26; Tirmizî, Daavât 38"),
    HazirZikir("Salli-bârik", "اَللّٰهُمَّ صَلِّ عَلٰى مُحَمَّدٍ وَعَلٰى آلِ مُحَمَّدٍ كَمَا صَلَّيْتَ عَلٰى إِبْرَاهِيمَ وَعَلٰى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ، اَللّٰهُمَّ بَارِكْ عَلٰى مُحَمَّدٍ وَعَلٰى آلِ مُحَمَّدٍ كَمَا بَارَكْتَ عَلٰى إِبْرَاهِيمَ وَعَلٰى آلِ إِبْرَاهِيمَ إِنَّكَ حَمِيدٌ مَجِيدٌ", "Bir salavata on rahmet.", 10, "Buhârî, Enbiyâ 10; Müslim, Salât 66, 70"),
    HazirZikir("Âyetü'l-kürsî", "اَللّٰهُ لَا إِلٰهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ", "Bakara 255; yatarken okuyan sabaha kadar korunur.", 1, "Buhârî, Vekâle 10"),
    HazirZikir("İhlâs, Felak, Nâs", "قُلْ هُوَ اللّٰهُ أَحَدٌ · قُلْ أَعُوذُ بِرَبِّ الْفَلَقِ · قُلْ أَعُوذُ بِرَبِّ النَّاسِ", "Sabah ve akşam üçer kere; her şeye karşı yeter.", 3, "Ebû Dâvûd, Edeb 101; Tirmizî, Daavât 117"),
    HazirZikir("Bismillâhillezî lâ yedurru", "بِسْمِ اللّٰهِ الَّذِي لَا يَضُرُّ مَعَ اسْمِهِ شَيْءٌ فِي الْأَرْضِ وَلَا فِي السَّمَاءِ وَهُوَ السَّمِيعُ الْعَلِيمُ", "Sabah ve akşam üçer kere söyleyene hiçbir şey zarar vermez.", 3, "Ebû Dâvûd, Edeb 101; Tirmizî, Daavât 13"),
    HazirZikir("Eûzü bi kelimâtillâhi't-tâmmât", "أَعُوذُ بِكَلِمَاتِ اللّٰهِ التَّامَّاتِ مِنْ شَرِّ مَا خَلَقَ", "Yarattıklarının şerrinden Allah'ın tam kelimelerine sığınırım.", 3, "Müslim, Zikir 54"),
    HazirZikir("Rabbenâ âtinâ", "رَبَّنَا آتِنَا فِي الدُّنْيَا حَسَنَةً وَفِي الْآخِرَةِ حَسَنَةً وَقِنَا عَذَابَ النَّارِ", "Peygamberimizin en çok yaptığı dua.", null, "Buhârî, Daavât 55; Müslim, Zikir 26"),
    HazirZikir("Allâhümme innî es'elüke'l-hüdâ", "اَللّٰهُمَّ إِنِّي أَسْأَلُكَ الْهُدٰى وَالتُّقٰى وَالْعَفَافَ وَالْغِنٰى", "Senden hidayet, takva, iffet ve gönül zenginliği isterim.", null, "Müslim, Zikir 72"),
    HazirZikir("Yâ mukallibe'l-kulûb", "يَا مُقَلِّبَ الْقُلُوبِ ثَبِّتْ قَلْبِي عَلٰى دِينِكَ", "Ey kalpleri çeviren, kalbimi dinin üzere sabit kıl.", null, "Tirmizî, Kader 7"),
    HazirZikir("Yâ Rahmân", "يَا رَحْمٰنُ", "Dünyada bütün yaratılmışlara merhamet eden."),
    HazirZikir("Yâ Rahîm", "يَا رَحِيمُ", "Ahirette müminlere merhamet eden."),
    HazirZikir("Yâ Melik", "يَا مَلِكُ", "Mülkün, kâinatın sahibi."),
    HazirZikir("Yâ Kuddûs", "يَا قُدُّوسُ", "Her eksiklikten uzak olan."),
    HazirZikir("Yâ Selâm", "يَا سَلَامُ", "Esenlik veren."),
    HazirZikir("Yâ Mü'min", "يَا مُؤْمِنُ", "Güven veren."),
    HazirZikir("Yâ Müheymin", "يَا مُهَيْمِنُ", "Gözetip koruyan."),
    HazirZikir("Yâ Azîz", "يَا عَزِيزُ", "İzzet sahibi, mağlup edilmeyen."),
    HazirZikir("Yâ Cebbâr", "يَا جَبَّارُ", "Dilediğini yaptıran, kırıkları onaran."),
    HazirZikir("Yâ Mütekebbir", "يَا مُتَكَبِّرُ", "Büyüklükte eşi olmayan."),
    HazirZikir("Yâ Hâlık", "يَا خَالِقُ", "Yaratan."),
    HazirZikir("Yâ Bâri'", "يَا بَارِئُ", "Kusursuz yaratan."),
    HazirZikir("Yâ Musavvir", "يَا مُصَوِّرُ", "Her şeye şekil veren."),
    HazirZikir("Yâ Gaffâr", "يَا غَفَّارُ", "Günahları çokça bağışlayan."),
    HazirZikir("Yâ Kahhâr", "يَا قَهَّارُ", "Her şeye galip gelen."),
    HazirZikir("Yâ Vehhâb", "يَا وَهَّابُ", "Karşılıksız veren."),
    HazirZikir("Yâ Rezzâk", "يَا رَزَّاقُ", "Rızık veren."),
    HazirZikir("Yâ Fettâh", "يَا فَتَّاحُ", "Hayır kapılarını açan."),
    HazirZikir("Yâ Alîm", "يَا عَلِيمُ", "Her şeyi bilen."),
    HazirZikir("Yâ Kâbıd", "يَا قَابِضُ", "Dilediğine daraltan."),
    HazirZikir("Yâ Bâsıt", "يَا بَاسِطُ", "Dilediğine genişleten."),
    HazirZikir("Yâ Hâfıd", "يَا خَافِضُ", "Dilediğini alçaltan."),
    HazirZikir("Yâ Râfi'", "يَا رَافِعُ", "Dilediğini yükselten."),
    HazirZikir("Yâ Muiz", "يَا مُعِزُّ", "İzzet veren."),
    HazirZikir("Yâ Müzil", "يَا مُذِلُّ", "Dilediğini zillete düşüren."),
    HazirZikir("Yâ Semî'", "يَا سَمِيعُ", "Her şeyi işiten."),
    HazirZikir("Yâ Basîr", "يَا بَصِيرُ", "Her şeyi gören."),
    HazirZikir("Yâ Hakem", "يَا حَكَمُ", "Hükmeden."),
    HazirZikir("Yâ Adl", "يَا عَدْلُ", "Mutlak adalet sahibi."),
    HazirZikir("Yâ Latîf", "يَا لَطِيفُ", "Lütfu sonsuz, en ince işleri bilen."),
    HazirZikir("Yâ Habîr", "يَا خَبِيرُ", "Her şeyden haberdar olan."),
    HazirZikir("Yâ Halîm", "يَا حَلِيمُ", "Yumuşak davranan, cezada acele etmeyen."),
    HazirZikir("Yâ Azîm", "يَا عَظِيمُ", "Pek yüce."),
    HazirZikir("Yâ Gafûr", "يَا غَفُورُ", "Çok bağışlayan."),
    HazirZikir("Yâ Şekûr", "يَا شَكُورُ", "Az amele çok sevap veren."),
    HazirZikir("Yâ Aliyy", "يَا عَلِيُّ", "Yüceler yücesi."),
    HazirZikir("Yâ Kebîr", "يَا كَبِيرُ", "Pek büyük."),
    HazirZikir("Yâ Hafîz", "يَا حَفِيظُ", "Koruyan."),
    HazirZikir("Yâ Mukît", "يَا مُقِيتُ", "Her canlının gıdasını veren."),
    HazirZikir("Yâ Hasîb", "يَا حَسِيبُ", "Hesaba çeken."),
    HazirZikir("Yâ Celîl", "يَا جَلِيلُ", "Celal sahibi."),
    HazirZikir("Yâ Kerîm", "يَا كَرِيمُ", "Çok cömert."),
    HazirZikir("Yâ Rakîb", "يَا رَقِيبُ", "Her şeyi gözeten."),
    HazirZikir("Yâ Mücîb", "يَا مُجِيبُ", "Duaları kabul eden."),
    HazirZikir("Yâ Vâsi'", "يَا وَاسِعُ", "İlmi ve rahmeti her şeyi kuşatan."),
    HazirZikir("Yâ Hakîm", "يَا حَكِيمُ", "Hikmet sahibi."),
    HazirZikir("Yâ Vedûd", "يَا وَدُودُ", "Kullarını seven ve sevilen."),
    HazirZikir("Yâ Mecîd", "يَا مَجِيدُ", "Şanı yüce."),
    HazirZikir("Yâ Bâis", "يَا بَاعِثُ", "Ölüleri dirilten."),
    HazirZikir("Yâ Şehîd", "يَا شَهِيدُ", "Her şeye şahit olan."),
    HazirZikir("Yâ Hakk", "يَا حَقُّ", "Varlığı gerçek olan."),
    HazirZikir("Yâ Vekîl", "يَا وَكِيلُ", "Güvenilip dayanılan."),
    HazirZikir("Yâ Kaviyy", "يَا قَوِيُّ", "Çok güçlü."),
    HazirZikir("Yâ Metîn", "يَا مَتِينُ", "Çok sağlam."),
    HazirZikir("Yâ Veliyy", "يَا وَلِيُّ", "Müminlerin dostu, yardımcısı."),
    HazirZikir("Yâ Hamîd", "يَا حَمِيدُ", "Övülmeye layık."),
    HazirZikir("Yâ Muhsî", "يَا مُحْصِي", "Her şeyi tek tek sayan."),
    HazirZikir("Yâ Mübdi'", "يَا مُبْدِئُ", "İlk kez yaratan."),
    HazirZikir("Yâ Muîd", "يَا مُعِيدُ", "Tekrar yaratan."),
    HazirZikir("Yâ Muhyî", "يَا مُحْيِي", "Hayat veren."),
    HazirZikir("Yâ Mümît", "يَا مُمِيتُ", "Ölümü yaratan."),
    HazirZikir("Yâ Hayy", "يَا حَيُّ", "Diri, ölümsüz."),
    HazirZikir("Yâ Kayyûm", "يَا قَيُّومُ", "Her şeyi ayakta tutan."),
    HazirZikir("Yâ Vâcid", "يَا وَاجِدُ", "İstediğini bulan."),
    HazirZikir("Yâ Mâcid", "يَا مَاجِدُ", "Şanı büyük."),
    HazirZikir("Yâ Vâhid", "يَا وَاحِدُ", "Tek."),
    HazirZikir("Yâ Samed", "يَا صَمَدُ", "Hiçbir şeye muhtaç olmayan."),
    HazirZikir("Yâ Kâdir", "يَا قَادِرُ", "Gücü yeten."),
    HazirZikir("Yâ Muktedir", "يَا مُقْتَدِرُ", "Her şeye kudreti yeten."),
    HazirZikir("Yâ Mukaddim", "يَا مُقَدِّمُ", "Dilediğini öne alan."),
    HazirZikir("Yâ Muahhir", "يَا مُؤَخِّرُ", "Dilediğini geriye bırakan."),
    HazirZikir("Yâ Evvel", "يَا أَوَّلُ", "Başlangıcı olmayan."),
    HazirZikir("Yâ Âhir", "يَا آخِرُ", "Sonu olmayan."),
    HazirZikir("Yâ Zâhir", "يَا ظَاهِرُ", "Varlığı apaçık olan."),
    HazirZikir("Yâ Bâtın", "يَا بَاطِنُ", "Mahiyeti gizli olan."),
    HazirZikir("Yâ Vâlî", "يَا وَالِي", "Her şeyi yöneten."),
    HazirZikir("Yâ Müteâlî", "يَا مُتَعَالِي", "Pek yüce."),
    HazirZikir("Yâ Berr", "يَا بَرُّ", "İyilik eden."),
    HazirZikir("Yâ Tevvâb", "يَا تَوَّابُ", "Tövbeleri kabul eden."),
    HazirZikir("Yâ Müntakim", "يَا مُنْتَقِمُ", "Suçluları cezalandıran."),
    HazirZikir("Yâ Afüvv", "يَا عَفُوُّ", "Affeden."),
    HazirZikir("Yâ Raûf", "يَا رَؤُوفُ", "Çok şefkatli."),
    HazirZikir("Yâ Mâlikü'l-mülk", "يَا مَالِكَ الْمُلْكِ", "Mülkün ebedî sahibi."),
    HazirZikir("Yâ Zü'l-celâli ve'l-ikrâm", "يَا ذَا الْجَلَالِ وَالْإِكْرَامِ", "Celal ve ikram sahibi."),
    HazirZikir("Yâ Muksit", "يَا مُقْسِطُ", "Adaletle hükmeden."),
    HazirZikir("Yâ Câmi'", "يَا جَامِعُ", "İstediğini bir araya toplayan."),
    HazirZikir("Yâ Ganiyy", "يَا غَنِيُّ", "Hiçbir şeye muhtaç olmayan."),
    HazirZikir("Yâ Mugnî", "يَا مُغْنِي", "Zengin kılan."),
    HazirZikir("Yâ Mâni'", "يَا مَانِعُ", "Dilemediğine engel olan."),
    HazirZikir("Yâ Dârr", "يَا ضَارُّ", "Zararı yaratan."),
    HazirZikir("Yâ Nâfi'", "يَا نَافِعُ", "Faydayı yaratan."),
    HazirZikir("Yâ Nûr", "يَا نُورُ", "Âlemleri nurlandıran."),
    HazirZikir("Yâ Hâdî", "يَا هَادِي", "Hidayet veren."),
    HazirZikir("Yâ Bedî'", "يَا بَدِيعُ", "Eşsiz yaratan."),
    HazirZikir("Yâ Bâkî", "يَا بَاقِي", "Varlığı sonsuz olan."),
    HazirZikir("Yâ Vâris", "يَا وَارِثُ", "Her şeyin asıl sahibi."),
    HazirZikir("Yâ Reşîd", "يَا رَشِيدُ", "Doğru yolu gösteren."),
    HazirZikir("Yâ Sabûr", "يَا صَبُورُ", "Çok sabırlı."),
)
private val BY_NAME = ZIKIR_LIST.associate { norm(it.name) to (it.ar to it.mean) }

/** The library's sections (Takip › Kütüphaneden ekle). */
val ZIKIR_TURLERI = listOf("Tümü", "Tesbihat", "Tevhid", "İstiğfar", "Salavat", "Dua", "Esmâ")

fun turOf(h: HazirZikir): String {
    val n = h.name
    return when {
        n == "Yâ mukallibe'l-kulûb" -> "Dua"
        n == "Allah" || n.startsWith("Yâ ") -> "Esmâ"
        n.contains("istiğf", ignoreCase = true) || n.startsWith("Estağfirullah") || n.startsWith("Rabbiğfir") -> "İstiğfar"
        n == "Salavat" || n == "Salli-bârik" -> "Salavat"
        n.startsWith("Sübhân") || n == "Elhamdülillah" || n == "Allâhu ekber" || n.startsWith("Yatarken") -> "Tesbihat"
        n.startsWith("Lâ ") -> "Tevhid"
        else -> "Dua"
    }
}

/** Library entries of [tur] ("Tümü" = all) whose name or meaning holds [query] (loose spelling). */
fun kutuphane(tur: String, query: String): List<HazirZikir> {
    val q = norm(query)
    return ZIKIR_LIST.filter { (tur == "Tümü" || turOf(it) == tur) && (q.isEmpty() || norm(it.name).contains(q) || norm(it.mean).contains(q)) }
}

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

/** A meaning shown as a sentence: ends with a period (zikirs saved before 2026-11-09 kept the library text without one). */
fun cumle(t: String): String = t.trim().let { if (it.isEmpty() || it.last() in ".!?…") it else "$it." }

/** Arabic (when known), the name, the meaning. */
@Composable
fun NurHead(name: String, own: Pair<String, String>? = null, okunus: String? = null) {
    val ar = own ?: zikirArabic(name)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (ar != null) Text(ar.first, style = TextStyle(fontSize = 32.sp, lineHeight = 44.sp, color = Color(0xFFF2D79B),
            textAlign = TextAlign.Center, shadow = Shadow(Pal.Gold.copy(alpha = .55f), blurRadius = 22f)))
        if (!okunus.isNullOrBlank() && okunus != name)
            Text(okunus, color = Pal.Text.copy(alpha = .8f), fontSize = Type.body2, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, textAlign = TextAlign.Center)
        Text(name, style = NumStyle, fontSize = Type.h2, color = Pal.Text, textAlign = TextAlign.Center)
        if (ar != null && ar.second.isNotBlank()) Text(cumle(ar.second), color = Pal.Muted, fontSize = Type.label, textAlign = TextAlign.Center)
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

/** A mote of light flying from [from] to [to] (page coordinates), leaving at [t0] for [ms]. */
class Mote(val from: Offset, val to: Offset, val t0: Long, val ms: Int)

/** Where the motes may land: points inside the letters (root coordinates), in the order they light up. */
class NurHedef { var noktalar: List<Offset> = emptyList() }

/** Tesbihat, design D1 "Zerre zerre" (design/design-system/tesbihat-dolum-secenekler.html, 2026-11-12): the zikir's Arabic
 *  barely there, and every count lights the next share of points inside the letters (never the gaps between words) in gold,
 *  in a random order, where the motes land. [f] 0 → 1 = how much of the word is lit, [flash] = the glow at a full word. */
@Composable
fun NurYazi(ar: String, f: Float, flash: Float, hedef: NurHedef) {
    // the gold follows the motes: each count lights its share as they land (~0.7 s later). Not a delayed animation:
    // quick taps kept restarting the delay, and nothing lit until the counting stopped (owner, 2026-11-12)
    var goal by remember(ar) { mutableStateOf(f.coerceIn(0f, 1f)) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(ar, f) { val v = f.coerceIn(0f, 1f); if (v < goal) goal = v else scope.launch { kotlinx.coroutines.delay(700); if (v > goal) goal = v } }
    val shown by androidx.compose.animation.core.animateFloatAsState(goal, tween(380), label = "yazi")
    val style = TextStyle(fontSize = 54.sp, lineHeight = 70.sp, textAlign = TextAlign.Center)
    val olcer = androidx.compose.ui.text.rememberTextMeasurer()
    val density = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.padding(horizontal = Space.l), contentAlignment = Alignment.Center) {
        val maxW = constraints.maxWidth
        val yerlesim = remember(ar, maxW) { olcer.measure(ar, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxW)) }
        val w = yerlesim.size.width; val h = yerlesim.size.height
        val adim = with(density) { 3.dp.toPx() }
        // the ink cells of the glyphs, shuffled once per word
        val hucre = remember(yerlesim) {
            val yol = yerlesim.getPathForRange(0, ar.length).asAndroidPath()
            val bolge = android.graphics.Region().apply { setPath(yol, android.graphics.Region(0, 0, w, h)) }
            buildList {
                var y = adim / 2
                while (y < h) { var x = adim / 2; while (x < w) { if (bolge.contains(x.toInt(), y.toInt())) add(Offset(x, y)); x += adim }; y += adim }
            }.shuffled(kotlin.random.Random(ar.hashCode()))
        }
        val altin = Brush.verticalGradient(listOf(Color(0xFFFFF1C9), Color(0xFFE2A64E)))
        val yaricap = adim * .8f
        val isik = remember(adim) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = android.graphics.BlurMaskFilter(adim * 2.5f, android.graphics.BlurMaskFilter.Blur.NORMAL) } }
        val kivilcim = remember(adim) { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = android.graphics.BlurMaskFilter(adim * .8f, android.graphics.BlurMaskFilter.Blur.NORMAL) } }
        val boy = with(density) { androidx.compose.ui.unit.DpSize(w.toDp(), h.toDp()) }
        Canvas(Modifier.size(boy).onGloballyPositioned { c -> val o = c.positionInRoot(); hedef.noktalar = hucre.map { it + o } }) {
            if (flash > 0f) drawCircle(Brush.radialGradient(listOf(Color(0xFFFFE9B0).copy(alpha = .45f * flash), Color.Transparent)),
                radius = size.maxDimension * .75f, center = center)
            drawText(yerlesim, color = Pal.Text.copy(alpha = .03f))   // barely there until it is counted
            val n = (shown * hucre.size).toInt()
            if (n > 0) {
                // a warm blurred light under the lit points (a BlurMaskFilter, so it glows instead of greying the navy),
                // the points that just lit brighter, then the gold letters only where they are lit
                val nc = drawContext.canvas.nativeCanvas
                isik.color = android.graphics.Color.argb(26, 255, 214, 140)
                for (i in 0 until n step 6) nc.drawCircle(hucre[i].x, hucre[i].y, adim * 1.6f, isik)
                val taze = ((shown - .025f) * hucre.size).toInt().coerceAtLeast(0)
                kivilcim.color = android.graphics.Color.argb(170, 255, 244, 220)
                for (i in taze until n) nc.drawCircle(hucre[i].x, hucre[i].y, adim * .9f, kivilcim)
                val maske = androidx.compose.ui.graphics.Path().apply { for (i in 0 until n) addOval(androidx.compose.ui.geometry.Rect(hucre[i], yaricap)) }
                clipPath(maske) { drawText(yerlesim, brush = altin, alpha = 1f) }
            }
        }
    }
}

/** The motes in flight: a curve from the touch up to the pen tip; the frame loop runs only while one is flying. */
@Composable
fun NurMotes(motes: SnapshotStateList<Mote>, modifier: Modifier) {
    var now by remember { mutableLongStateOf(frameClock()) }
    LaunchedEffect(motes.isNotEmpty()) {
        while (motes.isNotEmpty()) {
            withFrameMillis { now = frameClock() }
            motes.removeAll { now - it.t0 > it.ms }
        }
    }
    Canvas(modifier) {
        motes.forEach { m ->
            val t = ((now - m.t0).toFloat() / m.ms).coerceIn(0f, 1f)
            if (now < m.t0) return@forEach
            val e = t * t * (3 - 2 * t)
            // a quadratic curve that swings out sideways on the way up
            val c = Offset((m.from.x + m.to.x) / 2 + (if (m.from.x < m.to.x) -60.dp.toPx() else 60.dp.toPx()), minOf(m.from.y, m.to.y) + 40.dp.toPx())
            val p = m.from * ((1 - e) * (1 - e)) + c * (2 * (1 - e) * e) + m.to * (e * e)
            drawCircle(Color(0xFFFFE7B0).copy(alpha = .35f * (1 - .5f * t)), 6.dp.toPx(), p)
            drawCircle(Color(0xFFFFF4DC).copy(alpha = 1 - .3f * t), (2.6f - 1.2f * t).dp.toPx(), p)
        }
    }
}
