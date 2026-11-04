# Zikir: kendi listesi olan tesbih sayacı

Tarih: 2026-11-04 · Durum: onaylandı, uygulandı · Maket: `design/design-system/zikir.html`

## Niyet

Dock'taki Zikir sekmesi bugün boş durum gösteriyor; burası gerçek bir sayaca dönüşüyor. Kullanıcı kendi zikir listesini kurar, telefona bakmadan sayar ve tamamladıkça Özet'teki gülün beşinci yaprağı açılır.

## Kararlar (sahibiyle konuşuldu)

| Konu | Karar |
|---|---|
| Sayma | Sekmenin gövdesinin tamamı dokunma alanı. Ortada tesbih halkası, içinde Cormorant büyük sayı. |
| Sıfırlama | Her gün imsakta; gülle aynı gün (`Store.prayerDay()`). |
| İlk liste | Tesbihat hazır gelir: Sübhânallah 33, Elhamdülillah 33, Allâhu ekber 33. Düzenlenir, silinir, yenisi eklenir. |
| Hedefte | Her dokunuşta kısa tık titreşimi. Hedefte uzun titreşim ve altın parıltı, ardından listedeki tamamlanmamış sıradaki zikre geçilir. Hepsi bittiyse yerinde kalır. |

## Ekran

- Başlık "Zikir", sağda düzenle düğmesi.
- Başlığın altında yatay kaydırılan çipler: her zikir adı ve "21/33". Seçili olan altın; tamamlananın önünde tik var. Bir çipe dokunmak o zikre geçirir.
- Ortada halka: hedef 33 ya da daha azsa her sayıya bir boncuk düşer. Hedef 33'ten büyükse halka 33'lük turlarla dolar ve altında "2. tur" yazar. Dolan boncuk altın, kalan boncuk çizgi rengindedir.
- Halkanın altında küçük "−1" düğmesi var (yanlış dokunuş için). Bugünün özeti: "Bugün %63".
- Düzenleme bir alt sayfada yapılır: liste satırları (ad, hedef, sil) ve "Zikir ekle" (ad + hedef alanı).

## Veri

- `Store.zikirs`: `[{name, target}]` JSON. Anahtar yoksa varsayılan tesbihat gelir; boş liste geçerli bir seçimdir.
- `Store.zikirLog`: `{ "2026-11-04": { "Sübhânallah": 21 } }`. Gün, `prayerDay()` ile belirlenir. Ad listede benzersizdir.
- Gülün beşinci yaprağı: ilerleme her zikrin `ratio(count, target)` değerinin ortalamasıdır, etiket "tamamlanan/toplam". `counts = true` olur; böylece "hepsi tamam" kuralı 5 yaprağa döner. Liste boşsa yaprak açık sayılır (`ratio` kuralı: hedefi olmayan cezalandırılmaz).

## Hedef olmayanlar

- Sıralamayı sürükleyerek değiştirme (shortcut, sonra).
- Ses tuşuyla sayma, sesli sayma, geçmiş grafiği.
- Her namazdan sonra sıfırlanan tesbihat (yalnız günlük).

## Test

`ZikirTest`: ortalama ilerleme, boş liste, hedefi aşan sayının 1'de kırpılması, "sıradaki tamamlanmamış zikir" seçimi (sarma ve hepsi bittiğinde yerinde kalma).
