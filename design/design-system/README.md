# Seher tasarım sistemi

Namazım'ın tek teması: gece laciverdi üstünde tek altın vurgu. Material 3'ün token ve bileşen ayrımını izler.
**Henüz uygulama koduna aktarılmadı.** Onaydan sonra `index.html`'deki "Tasarım sistemini kur" istemi `Theme.kt` ve `UiKit.kt`'yi günceller.

## Dosyalar

- `styles.css`: tokenlar (`:root`) ve bileşen sınıfları. Tek kaynak bu dosya. Her token yorumunda Compose karşılığı yazılı (`Pal.X`, `Type`, `Space`, `Radius`).
- `index.html`: yaşayan stil rehberi. Temeller, M3'ün zorunlu bileşenlerinin tamamı ve Namazım eklentileri (gökyüzü başlığı, geri sayım, vakit zaman çizgisi, gül kartı). Dev Mode ile incelenir; "Copy as prompt" Compose istemi üretir.
- `concept.html`: iki ekran, ana sayfa ile Muhasebe › Özet.
- `icons.html`: ikon sprite'ı. `index.html` ve `concept.html` bu sprite'ın bir kopyasını taşır. İkon eklersen kopyayı da güncelle.

Sayfalar `app/src/main/assets/sky/dusk.webp` ve `design/gul/gul-3d.html` dosyalarını göreli yolla kullanır. Bu yüzden repo kökünden sunulmalıdır:
`python -m http.server 8765 --directory .` ile başlat, sonra `http://localhost:8765/design/design-system/index.html` adresini aç.
`file://` ile açılınca gül görünmez.

## Genişletme

- **Token eklemek:** `styles.css`'te doğru gruba bir değişken ekle. Rengi, `--on-*` eşiyle birlikte ekle ve kontrastı hesapla (gövde metni için ≥ 4.5:1). Rengi bileşende elle yazma.
- **Bileşen eklemek:** üç parça gerekir:
  1. `styles.css`'te yalnızca token kullanan bir sınıf;
  2. `index.html`'de `<section id="anahtar">`;
  3. `SPECS.anahtar` girdisi.

  Bunlar hazır olunca Dev Mode ve "Copy as prompt" bileşeni kendiliğinden kapsar.
- **Kurallar:**
  - Altın, ekranda bir tek "sıradaki iş" içindir.
  - Şeftali yalnız kaza ve kerahat anlamı taşır.
  - Kartlar gölgesizdir (tonal yüzey, üst parıltı ve 1 px kenar). Gölge yalnız gökyüzünün üstünde yüzen öğelere verilir.
  - Rakamlar Cormorant ile `lnum` kullanır. Okuma metni Inter'dir.
