# Namaz ana sayfası · vitrin videoları (çekim listesi ve promptlar)

Amaç: ana sayfanın 240 dp başlığı fotoğraf gerçekliğinde ve sürekli hareketli olsun. Mekke ve Medine, vakte göre değişsin.
Çizim yok: sahneler AI video modeliyle (Kling 2.x / Veo 3 / Seedance) üretilir, uygulama bunları döngü olarak oynatır
ve üstüne kendi canlı katmanlarını koyar (geri sayım, gerçek güneş/ay yayı, hafif ışık parçacıkları).

## Teknik kurallar (her çekim için)

- **Süre:** 8–10 sn. Kusursuz döngü: ilk ve son kare aynı ışıkta ve konumda olmalı. Kamera ya sabit kalır ya da çok yavaş kayar; zoom ya da kesme yok.
- **Kadraj:** 16:9 üretilip 3:2'ye kırpılır. Ana konu alt üçte birde ve ortada olmalı. Üst 20 % gökyüzü kalmalı, çünkü konum çipi ve düğmeler orada duracak. Alt 30 % karanlığa iniyor (geri sayım orada).
- **Çözünürlük:** 1080p. Çıktı H.264 mp4, 3–6 MB, sessiz.
- **Yazı, logo, filigran yok.** İnsanlar küçük ve uzakta; yüz yakın çekimi yok. Saygılı, sakin, belgesel tonu.
- **Işık:** gerçek saat dilimine uygun olmalı (aşağıdaki eşleme).

## Vakit → sahne eşlemesi

| Vakit aralığı | Sahne | Dosya |
|---|---|---|
| İmsak → Güneş | Medine, şafak | `assets/sky/medine-safak.mp4` |
| Güneş → İkindi | Mekke, gündüz | `assets/sky/mekke-gun.mp4` |
| İkindi → Akşam | Medine, gün batımı | `assets/sky/medine-aksam.mp4` |
| Akşam → İmsak | Mekke, gece | `assets/sky/mekke-gece.mp4` |

Geçiş: vakit değişince 1,5 sn çapraz geçiş. Uygulama açıkken saat ilerledikçe sahne kendiliğinden değişir.

## Promptlar (İngilizce; modeller İngilizce prompta daha iyi uyuyor)

**1 · mekke-gece.mp4**
> Photorealistic documentary footage, locked-off wide shot from a high balcony of Masjid al-Haram at night. The Kaaba draped in black kiswa
> with the gold calligraphy band, centered in the lower third. Thousands of pilgrims in white perform tawaf, flowing counter-clockwise
> around it as soft motion blur, a slow continuous river of white. Marble courtyard glowing warm under floodlights, minarets lit at the
> edges of frame, deep navy sky with a few stars at the top. Gentle real-time motion, no camera movement, no zoom, no text, 10 second
> seamless loop, cinematic, natural colors, 35mm, high detail.

**2 · mekke-gun.mp4**
> Photorealistic documentary footage, static wide shot of Masjid al-Haram in bright afternoon light. The Kaaba centered in the lower third,
> pilgrims in white circling it counter-clockwise in a slow, continuous flow, sharp shadows on white marble, pigeons occasionally
> crossing the frame, clear pale-blue sky at the top with thin high clouds drifting slowly. No camera movement, no text, seamless 10 second loop.

**3 · medine-aksam.mp4**
> Photorealistic documentary footage of Al-Masjid an-Nabawi in Medina at golden hour. The Green Dome and the minarets in the lower third,
> the giant white courtyard umbrellas slowly folding closed in unison, palm trees swaying gently, warm orange sunset sky with soft clouds
> drifting, a few worshippers walking far away. Very slow lateral dolly, no zoom, no text, seamless 10 second loop, natural colors.

**4 · medine-safak.mp4**
> Photorealistic footage of Al-Masjid an-Nabawi at dawn, blue hour turning to first light. The Green Dome and minarets softly lit,
> courtyard lamps still glowing, the umbrellas slowly opening, palms barely moving, a thin crescent moon fading in a deep blue to
> rose sky. Static camera, no text, seamless 10 second loop, calm and quiet.

Kontrol listesi: döngü dikişi görünmüyor mu, Kâbe ve kubbe doğru oranda mı (yapay zekâ bazen kubbe rengini ya da minare sayısını bozuyor),
kadrajın üstünde gökyüzü boşluğu var mı.

## Uygulamada (videolar gelince)

- Başlık 240 dp olacak. `SkyLayers.kt`'deki video modu genişletilecek: vakte göre klip seçilecek, iki `TextureView` arasında çapraz geçiş yapılacak, döngü kesintisiz oynayacak.
- Üstte canlı katmanlar: geri sayım ve tarih, istenirse gerçek güneş/ay yayı (bugünkü fotoğraf modu gibi), çok hafif altın ışık parçacıkları.
- Pil: video yalnızca ekran açık ve sekme görünürken oynayacak; başka sekmeye geçince durup kaldığı kareden devam edecek.
- Ayarlar › Gökyüzü'ne bir seçenek gelecek: "Harem (video)". Bugünkü shader ve fotoğraf modları seçenek olarak kalacak.

## Üretim (2026-11-05)

Kaynak: sahibin Google AI Pro hesabı, Flow / Veo. Dört ham klip `Desktop/vitrin-ham/` klasöründe (repo dışında): 1920×1080, 24 fps, 9,96 sn, sesli.
Brief'teki İngilizce promptlar olduğu gibi kullanıldı. Görünür filigran ya da yazı yok.

| Dosya | Durum | Not |
|---|---|---|
| `mekke-gece.mp4` | ✅ kullanıldı | Kâbe, kuşak ve Makam-ı İbrahim doğru, kamera sabit. Arkada inşaat vinçleri var (gerçekçi ama dikkat çekebilir). |
| `mekke-gun.mp4` | ✅ kullanıldı | Kamera sabit. Güvercinler 3. ve 6. saniyelerde geçiyor. Tavaf akışı daha çok yürüyüş gibi duruyor. |
| `medine-aksam.mp4` | ✅ v2 kullanıldı | v1 reddedildi: şemsiyeler kapanırken eriyip direğe dönüşüyordu, başı ve sonu farklıydı. v2'de şemsiyeler açık ve kıpırdamıyor, bulutlar yavaşça kayıyor, ışık sabit. Kubbe yeşil, minareler doğru. |
| `medine-safak.mp4` | ✅ v2 kullanıldı | v1 reddedildi: ışık gündüze dönüyor, kamera yaklaşıyordu. v2 mavi saatte kalıyor, minare ışıkları yanık, hilal sabit, şemsiyeler kapalı. Ortada bir kuş sürüsü geçiyor, son karede birkaç kuş var; çapraz geçiş bunları yumuşatıyor. |

Döngü: ilk 1 sn, klibin son 1 sn'siyle çapraz geçişle karıştırıldı (ffmpeg `xfade`, 9,0 sn). Çıktı H.264 1080p, yuv420p, sessiz, faststart.
gece CRF 25 → 5,0 MB, gün CRF 23 → 5,6 MB, şafak CRF 21 → 5,2 MB, akşam CRF 21 → 3,5 MB. Dikişte, ardışık kareler arasındaki SSIM farkından biraz daha büyük bir fark kalıyor (gece 0,89 / 0,94, gün 0,93 / 0,97, şafak 0,98 / 1,00, akşam 0,98 / 1,00). İnsanlar karışırken bir an yumuşuyor, sıçrama yok.

Medine v2 promptları (değişen şey sabit ışık ve kıpırdamayan şemsiyeler; Veo zaman akışını ve dönüşen nesneleri döngüye uygun üretmiyor):

**medine-aksam (v2)**
> Photorealistic documentary footage of Al-Masjid an-Nabawi in Medina at golden hour. The Green Dome and the minarets in the lower third,
> the giant white courtyard umbrellas fully open and completely still, palm trees swaying gently, warm orange sunset sky with soft clouds
> drifting slowly, a few worshippers walking far away. Locked-off static camera, constant light, nothing changes over time, no zoom, no text,
> seamless loop, natural colors.

**medine-safak (v2)**
> Photorealistic footage of Al-Masjid an-Nabawi at blue hour before dawn. The Green Dome and minarets softly lit, minaret lights on,
> courtyard lamps glowing, the umbrellas closed and still, palms barely moving, a thin crescent moon in a deep blue sky with a faint rose
> glow at the horizon. Locked-off static camera, constant light for the whole clip, no change in brightness, no text, seamless loop, calm and quiet.
