# Gül — Muhasebe › Özet için 3B günlük gül

Tarih: 2026-11-04 · Durum: tasarım, onay bekliyor

## Niyet

Muhasebe › Özet'in üst kartındaki beş eksenli radar grafiği (`RadarChart`, `Screens.kt`) kalkar; yerine 3 boyutlu, canlı bir gül gelir. Gül Hz. Peygamber'le özdeşleştiği için Namazım'a yakışır. Amaç ödül: kullanıcı gün içinde hedeflerini tamamladıkça gülün yaprakları açılsın, **açık hâl kapalı hâlden çok daha güzel** olsun ki insan gülü açtırmak için uğraşsın.

## Hedefler

- Gülün dış halkasında 5 büyük yaprak var; her biri bir günlük hedefe bağlı ve o hedefte alınan yol kadar açılıyor.
- Hiçbir şey yapılmadığında gül kapalı, soluk, mat bir gonca. İlerledikçe renk canlanıyor; tamamlanan yaprağın içi hafifçe ışıyor.
- Bütün hedefler tamamlanınca iç gonca çözülüyor, gülün kalbinde sıcak altın ışık yanıyor, yukarı doğru altın tozlar süzülüyor.
- Kullanıcı gülü parmakla çevirebiliyor; dokunulmadığında gül kendi kendine yavaşça dönüyor.
- Görünüm, onaylanan prototipin aynısı: `design/gul/gul-3d.html` (tarayıcıda açılır; three.js CDN üzerinden yüklenir) (gece sahnesi, kızıl gül). **Referans bu dosyadır**; uygulamadaki gül bununla yan yana karşılaştırılarak doğrulanır.

## Hedef olmayanlar

- Zikir modülü bu projede yok (ayrı proje, sonra). Zikir yaprağı o gelene kadar kapalı bekler.
- Renk/sahne seçici, ayarlar, paylaşma yok.
- Radar dışındaki Özet kartlarına dokunulmuyor.

## Anti-hedefler (çalışsa bile başarısızlık sayılır)

- Gül prototipten belirgin şekilde daha kaba, daha düz ya da "çizilmiş" görünüyorsa. Sahibi çizimsi/makine işi görselleri reddediyor (bkz. `AGENTS.md`).
- Açık hâl kapalı hâlden daha az çekiciyse ya da fark zor seçiliyorsa.
- Yapraklar prototipteki "tam açık" hâlinden daha fazla kıvrılıyorsa (sahibi bunu özellikle istemedi).
- Kullanıcıdan hedefler için yeni veri girişi isteniyorsa (Vaktinde tamamen otomatik olmalı).
- Ekran açılırken takılma/donma, ya da kaydırmayı kilitleyen bir 3B yüzey.

## Beş yaprak ve bağlantıları

Yapraklar prototipteki beş açıya bu tablonun sırasıyla yerleşir: Namaz ile Vaktinde yan yana, Zikir prototipteki eski "Hedef" yerinde (benim kararım). Etiket adları aynen bunlar.

| Yaprak | İlerleme (0..1) | Veri kaynağı | Aşama |
|---|---|---|---|
| **Namaz** | bugünkü `slots()` içinden kılınan / 5 | `store.prayed()` | 5 aşama, her vakit 1 |
| **Vaktinde** | bugün **ilk vakitte** kılınan namaz / 5 | `store.prayedAt(slot.key)` | 5 aşama |
| **Sayfa** | bugün okunan sayfa / `store.readGoal` (1'de kırpılır) | `store.readLog()[bugün]` | hedef kadar aşama |
| **Kaza** | bugün kılınan kaza / `store.kazaRate` (1'de kırpılır) | `store.kazaLog()[bugün]` | `kazaRate` kadar aşama |
| **Zikir** | şimdilik hep 0 | — (zikir modülü gelince) | — |

**Vaktinde kuralı (sahibinin kararı):** Her namazın vakti `slot.start`..`slot.end` arası üçe bölünür: ilk / orta / son vakit. `prayedAt` ilk üçte birin içindeyse o namaz vaktinde sayılır. Ayrı veri girişi yok; "Kıldım"a basılma saati esas. Kıldım'a geç basılırsa namaz geç sayılır; bu bilerek kabul edildi. İşareti geri alınan namaz sayılmaz.

**Kaza yaprağı:** Bugünkü kaza sayısı `kazaLog` ile tutuluyor (`payKaza` +1, `unpayKaza` -1). `kazaRate` 0 ya da kaza borcu hiç yoksa yaprak tam açık sayılır (benim kararım: borcu olmayanı cezalandırmayalım).

**Sayfa yaprağı:** `readGoal` 0 ise yaprak tam açık sayılır (benim kararım, aynı gerekçe).

**Kalıcı ilerlemeler:** Hatim (`quranPage/604`) ve 30 günlük tam gün serisi (`full30/30`) gülden çıkar, gül kartının altında iki ince ilerleme çizgisi olarak kalır. Mevcut lejant satırı ve "Hedefe ulaşılan gün" metni bu çizgilere dönüşür.

## Gülün davranışı

- **Açıklık sınırı:** Prototipteki `OPEN_MAX = .6` sınırı zaten son hâli veriyor: ilerleme 1 olduğunda yaprak, sahibinin seçtiği açıklıkta durur. Prototipi olduğu gibi al; sınırı ikinci kez uygulama.
- **Renk:** kapalı yaprak doygunluğu ve parlaklığı düşük (prototipte `muted`), ilerledikçe gerçek renge döner; ilerleme 1'de yaprak hafif ışır (emissive).
- **İç gonca:** bütün yaprakların en küçüğü kadar çözülür (`budC = min`).
- **Kutlama:** "hepsi tamam" olduğunda kalpte altın ışık, yükselen altın tozlar, hafif bloom.
- **Zikir modülü gelene kadar "hepsi" = ilk dört yaprak** (benim kararım). Bu kural üç yerde birden geçerli: iç goncanın çözülmesi (`budC` = dört yaprağın en küçüğü), etiketlerin kaybolması ve kutlama. Yoksa mükemmel bir günde bile gonca kapalı, etiketler ekranda kalır. Prototipteki `Math.min(...cur)` kontrolleri buna göre değişir. Zikir yaprağı bu sürede kapalı gonca olarak durur; modül gelince kural beş yaprağa döner.
- **Etiketler:** her yaprağın etiketi (ad + "3/5", "%40" gibi değer) ince bir çizgiyle yaprağa bağlı. **Bütün yapraklar açılana kadar görünür; hepsi açılınca etiketler kaybolur** (sahibinin kararı) ve gül tek başına kalır.
- **Etkileşim:** sürükleyerek çevirme, dokunulmadığında yavaş otomatik dönüş (prototipteki hız). Yakınlaştırma yok (benim kararım: sayfa kaydırmasıyla çakışmasın).
- **Gün = namaz günü, imsaktan imsaka (sahibinin kararı).** Yatsı bir sonraki imsaka kadar işaretlenebildiği için gülün günü de imsakta biter: imsaktan önceki saatlerde gül hâlâ önceki günü gösterir, imsak girince yeniden goncadan başlar. Bunun için tek bir yardımcı (örneğin `prayerDay(now)`: şimdi bugünün imsakından önceyse dün, değilse bugün) tanımlanır ve gülün bütün hesapları onu kullanır.
- Aynı yardımcı `logPages` ve `logKaza`'da da kullanılır (benim kararım). Yoksa gece 00:30'da okunan sayfa takvimde ertesi güne yazılır, gül ise hâlâ önceki günü gösterdiği için o sayfayı görmez. Bu değişiklik okuma ve kaza geçmişindeki gün sınırını da imsaka taşır; geçmiş kayıtlara dokunulmaz.
- **Sahne:** gece sahnesi (uygulamanın laciverti), kızıl gül. Sahne/renk seçici yok.

## Kısıtlar

- Kotlin + Compose, tek `Store`, DI yok, **Gradle'a yeni kütüphane yok** (`AGENTS.md` kuralı).
- Telefon: Xiaomi, Android 16; minSdk 31. Görsel değişiklikler telefonda denenir, ekran görüntüleri `vakit-ekran/` klasörüne.
- Uygulama internetsiz çalışmalı; gül de.
- Özet sayfası dikey kaydırılıyor; gül kartı yatay sürüklemeyi yakalamalı, dikey kaydırmayı engellememeli.

## Nasıl yapılacak (benim kararım, sahibi itiraz edebilir)

Prototip three.js ile yazılmış bir web sayfası. Uygulamaya **Android WebView** ile gömülür:

- Not: WebView, `file:///android_asset` adresinden ES modüllerini yüklemeyi engeller. `androidx.webkit`'teki `WebViewAssetLoader` yeni kütüphane kuralına takılıyor. Ya `shouldInterceptRequest` ile elle sunulur ya da betikler tek dosyada birleştirilir; seçim kurucunun.
- `three.module.js` (ve kullanılan eklentiler: OrbitControls ya da onun yerine basit bir sürükle-çevir, EffectComposer/RenderPass/UnrealBloomPass/OutputPass) `app/src/main/assets/gul/` altına dosya olarak girer, internet gerekmez. Bu bir Gradle bağımlılığı değil, varlık dosyası.
- Sayfa `gul.html` prototipten türetilir. Panel, kaydırıcılar, sahne/renk düğmeleri çıkar; arka plan Özet kartıyla kaynaşacak şekilde ayarlanır.
- Kotlin tarafı her `StoreRev` değişiminde beş ilerleme değerini (ve aşama/etiket metinlerini) sayfaya `evaluateJavascript` ile verir; sayfa animasyonla oraya gider.
- İlerleme hesapları Kotlin'de, test edilebilir saf fonksiyonlarda olur (özellikle Vaktinde'nin üçe bölme kuralı ve kırpmalar). `LogicTest.kt`'ye testleri eklenir.

Alternatif (seçilmedi): gülü OpenGL ES ile doğrudan yeniden yazmak. Daha hafif ama birkaç kat daha uzun ve prototiple birebir aynı görüntüyü tutturmak zor.

## Kurucuya bırakılanlar

- WebView'in Compose içine yerleşimi, kart yüksekliği (gül prototipteki gibi kartın ~%60–70'ini doldurmalı).
- Telefonda performans kademeleri (pixel ratio, gölge haritası boyutu, MSAA örnek sayısı); hedef akıcı dönüş. Gerekirse kutlama dışında bloom kapatılabilir.
- Sayfa yüklenmeden önce kartta ne görüneceği (boş kart ya da sessiz yer tutucu; sıçrama olmasın).
- Prototipteki hata ayıklama kancalarının (`__s`, `__fps`) temizlenmesi.
