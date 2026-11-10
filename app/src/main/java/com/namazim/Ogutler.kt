package com.namazim

/** An ayet or hadis about namaz shown in the "Kıldın mı?" reminders: a short Turkish meaning and its source. */
data class Ogut(val text: String, val source: String)

// Meanings follow the common Diyanet wording, shortened where a reminder needs it; every one names its source.
// Ayet and hadis alternate so two reminders in a row never feel the same.
val OGUTLER = listOf(
    Ogut("Allah katında amellerin en sevimlisi, vaktinde kılınan namazdır.", "Hadis · Buhârî, Mevâkît 5"),
    Ogut("Şüphesiz namaz, müminler üzerine vakitleri belirlenmiş bir farzdır.", "Nisâ 4:103"),
    Ogut("Kapısının önünden akan nehirde günde beş kez yıkanan kişide kir kalır mı? Beş vakit namaz da böyledir; Allah onunla günahları siler.", "Hadis · Buhârî, Mevâkît 6; Müslim, Mesâcid 283"),
    Ogut("Sabır ve namazla Allah'tan yardım dileyin.", "Bakara 2:45"),
    Ogut("Kul, Rabbine en çok secdedeyken yakındır. Secdede duayı çoğaltın.", "Hadis · Müslim, Salât 215"),
    Ogut("Namaz, insanı hayâsızlıktan ve kötülükten alıkoyar.", "Ankebût 29:45"),
    Ogut("Kıyamet günü kulun ilk hesaba çekileceği ameli namazdır.", "Hadis · Tirmizî, Salât 188"),
    Ogut("Beni anmak için namaz kıl.", "Tâhâ 20:14"),
    Ogut("Ey Bilâl, kalk da namazla bizi rahatlat.", "Hadis · Ebû Dâvûd, Edeb 78"),
    Ogut("Müminler kurtuluşa ermiştir; onlar namazlarında huşû içindedirler.", "Mü'minûn 23:1-2"),
    Ogut("Sabah namazını kılan kimse Allah'ın himayesindedir.", "Hadis · Müslim, Mesâcid 261"),
    Ogut("Namazlara ve orta namaza devam edin; Allah'a gönülden boyun eğerek durun.", "Bakara 2:238"),
    Ogut("İki serin vakit namazını (sabah ve ikindi) kılan cennete girer.", "Hadis · Buhârî, Mevâkît 26"),
    Ogut("Gündüzün iki ucunda ve gecenin ilk saatlerinde namaz kıl. İyilikler kötülükleri giderir.", "Hûd 11:114"),
    Ogut("Beş vakit namaz, büyük günahlardan kaçınıldıkça, aralarında işlenen günahlara kefarettir.", "Hadis · Müslim, Tahâret 16"),
    Ogut("Kalpler ancak Allah'ı anmakla huzur bulur.", "Ra'd 13:28"),
    Ogut("Amellerin Allah'a en sevimlisi, az da olsa devamlı olanıdır.", "Hadis · Buhârî, Rikâk 18"),
    Ogut("Beni anın ki ben de sizi anayım.", "Bakara 2:152"),
    Ogut("İkindi namazını kaçıran, ailesini ve malını kaybetmiş gibidir.", "Hadis · Buhârî, Mevâkît 14"),
    Ogut("Ailene namazı emret, kendin de ona sabırla devam et.", "Tâhâ 20:132"),
    Ogut("Yatsıyı cemaatle kılan gecenin yarısını, sabahı da cemaatle kılan bütün geceyi ibadetle geçirmiş gibidir.", "Hadis · Müslim, Mesâcid 260"),
    Ogut("Secde et ve Rabbine yaklaş.", "Alak 96:19"),
    Ogut("Cemaatle kılınan namaz, tek başına kılınandan yirmi yedi derece üstündür.", "Hadis · Buhârî, Ezân 30"),
    Ogut("Namazı kıl; sabah namazını da kıl, çünkü sabah namazı şahitlidir.", "İsrâ 17:78"),
    Ogut("Gözümün nuru namazda kılındı.", "Hadis · Nesâî, İşretü'n-nisâ 1"),
    Ogut("Namazı dosdoğru kılın, zekâtı verin ve rükû edenlerle birlikte rükû edin.", "Bakara 2:43"),
)

/** The next öğüt in turn; the position is kept in the store so every reminder shows a different one. */
fun nextOgut(store: Store): Ogut {
    val i = store.ogutIndex
    store.ogutIndex = (i + 1) % OGUTLER.size
    return OGUTLER[i % OGUTLER.size]
}

/** One ayet or hadis about Cuma, shown once on home after the Cuma vakti enters; a different one each Friday. */
val CUMA_AYETLERI = listOf(
    Ogut("Ey iman edenler! Cuma günü namaz için çağrı yapıldığında Allah'ı anmaya koşun, alışverişi bırakın.", "Cuma 62:9"),
    Ogut("Güneşin doğduğu en hayırlı gün Cuma günüdür.", "Hadis · Müslim, Cum'a 17"),
    Ogut("Namaz kılınınca yeryüzüne dağılın, Allah'ın lütfundan rızık arayın ve Allah'ı çok anın ki kurtuluşa eresiniz.", "Cuma 62:10"),
    Ogut("Cuma günü bana çokça salavat getirin; sizin salavatınız bana sunulur.", "Hadis · Ebû Dâvûd, Salât 207"),
    Ogut("Cuma gününde bir saat vardır ki, Müslüman kul o saate denk gelip Allah'tan hayır dilerse Allah onu ona verir.", "Hadis · Buhârî, Cum'a 37"),
    Ogut("Şüphesiz Allah ve melekleri Peygamber'e salât eder. Ey iman edenler, siz de ona salât edin ve tam bir teslimiyetle selam verin.", "Ahzâb 33:56"),
    Ogut("Cuma günü gusledip temizlenen, güzel koku sürünüp namaza giden ve imam hutbe okurken susan kişinin, iki Cuma arasındaki günahları bağışlanır.", "Hadis · Buhârî, Cum'a 6"),
    Ogut("Cuma günü Kehf sûresini okuyana, iki Cuma arası bir nur verilir.", "Hadis · Hâkim, Müstedrek 2/399"),
)

/** The Cuma ayet of the week that holds [day]: weeks counted from the epoch, so every Friday brings the next one. */
fun cumaAyeti(day: java.time.LocalDate): Ogut = CUMA_AYETLERI[Math.floorMod(day.toEpochDay() / 7, CUMA_AYETLERI.size.toLong()).toInt()]
