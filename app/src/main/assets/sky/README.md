# Seher gökyüzü katmanları

Bu klasördeki dosyalar "Canlı" shader'ın yerine fotoğraf/video gökyüzü kullanmak içindir.
Ayarlar › Görünüm › **Gökyüzü** ile telefonda Canlı / Fotoğraf / Video arasında geçiş yapılır.

Şu an buradakiler **yer tutucu**: mevcut shader'ın ekran görüntülerinden üretildiler,
sadece boru hattını denemek için. Gerçek fotoğraflarla değiştirilecekler.

## Dosyalar

| Dosya | Ne | Notlar |
|---|---|---|
| `day.webp` | Gündüz plakası | Dikey, aynı kadraj, **güneş diski ve yazı yok** |
| `dusk.webp` | Gün batımı plakası | aynı kadraj |
| `night.webp` | Gece plakası | aynı kadraj |
| `wheat.webp` | (opsiyonel) tarla döngüsü | saydam animasyonlu WebP, 8–12 kare, kusursuz döngü |
| `scene.mp4` | (opsiyonel) tüm sahne videosu | ses yok, kısa döngü, H.264, 720×684 ya da daha büyük |

Fotoğraf modunda yay, güneş ve ay uygulama tarafından çizilir (ay evresi gerçek tarihten);
bu yüzden plakalarda güneş/ay **olmamalı**. Video modunda her şey videonun içindedir.

## Yeni plaka koyunca

1. Dosyaları bu klasöre aynı adla koy (WebP ya da JPEG/PNG; `SkyLayers.kt` adları arıyor).
2. `./gradlew assembleDebug` + `adb install -r`.
3. Ayarlar › Gökyüzü › Fotoğraf.

Plakalar 720×684 (≈ 0.95 en-boy) hedefe göre kırpılır; daha büyük verirsen sadece kalite artar.
