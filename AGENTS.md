# Namazım — project guide for coding agents

Android prayer app (Kotlin + Jetpack Compose). Goal: get the user to actually pray. Reminders before each
prayer time, then "kıldın mı?" nags until the user taps **Kıldım**. Missed prayers go to kaza automatically.
All UI text is Turkish. The owner cares a lot about visuals and motion.

**Handoff:** read `docs/superpowers/notes/2026-11-04-devir.md` first. It covers the open "rose invisible on the phone" bug, the owner's pending requests and the owner's working preferences.

## Build

- JDK 17, Android SDK 34 (`C:/Users/lejyo/AppData/Local/Android/Sdk`, see `local.properties`), Gradle 8.7 wrapper, AGP 8.5.2, Kotlin 2.0.20.
- The folder name contains `ı`, which breaks AGP on Windows. Either rename the folder to `Namazim`, or build from a
  substituted drive: `subst N: "C:\Users\lejyo\Desktop\Namazım"` then `N:\gradlew.bat testDebugUnitTest assembleDebug`
  (the subst is lost on reboot). `android.overridePathCheck=true` is set in `gradle.properties`.
- APK: `app/build/outputs/apk/debug/app-debug.apk` (a copy is kept as `Namazim.apk` in the root).

## Phone (Xiaomi, Android 16)

- Over Wi‑Fi adb. Wireless debugging keeps switching itself off, so it was put on a fixed port:
  enable *Kablosuz hata ayıklama* once, `adb connect <ip>:<port>`, then `adb tcpip 5555` → `adb connect 192.168.1.193:5555`.
  Holds until the phone reboots.
- `adb install -r` shows a Xiaomi confirmation on the phone; the user must tap *Yükle*.
- Tap/key injection is not allowed. To open a tab for a screenshot:
  `adb shell am start -n com.namazim/.MainActivity --ei tab N` (0 Namaz, 1 Kıble, 2 Okuma, 3 Muhasebe, 4 Ayarlar, 5 Zikir;
  1 and 4 are not in the dock, they open from the home header),
  then `adb exec-out screencap -p > shot.png`. The tab extra also works on a running instance.
- More test extras: `--el skytime <ms>` shifts the sky clock (e.g. `-19800000` shows noon at 18:30),
  `--ez keepawake true` pins the screen on, `--ez widgetpreview true` shows the widget art big,
  `--es skymode shader|photo|video` switches the header renderer.
  `--ez backuptest true` (debug) exports a backup, parses it back and logs `Backup: round trip … mismatched=[]` (writes nothing).
- The screen sleeps after 60 s; without a wake key the screenshots come out black.

## Code map (`app/src/main/java/com/namazim/`)

| File | What |
|---|---|
| `Prayers.kt` | Prayer times via `adhan2` (TURKEY method) + per-vakit second offsets fitted to Diyanet tables; `slots()` (one prayer per day: start/end/nagEnd), extra times (kerahat, işrak, kuşluk, evvâbin, teheccüd), hijri date (ICU Umm al-Qura), mübarek günler, sûre start pages. |
| `Store.kt` | Everything persisted, in one SharedPreferences file: location, prayed set (`"yyyy-mm-dd|VAKIT"`), kaza counts (+VITIR), reading log, books, all settings, snooze. `settleKaza()` turns ended unprayed prayers into kaza. Backup: `exportBackup()` / `readBackup()` / `restore()` write the whole prefs file as one typed JSON (`{app:"namazim",v:1,prefs}`), restore is one `commit()`; Ayarlar › Yedekleme saves/opens it via the system file picker, and the home screen shows a quiet band after 30 days without a backup (`backupDue`). Android Auto Backup is also on (no `allowBackup=false`). |
| `Reminders.kt` | Alarm chain: always exactly one `setAlarmClock` alarm for the next reminder. `reminders(slot, cfg)` is the cadence; `fire()` shows due notifications; Kıldım / snooze receivers; `test()` sends a sample nag. |
| `MainActivity.kt` | Activity, bottom nav, Namaz (home) screen, full-screen "Kıldın mı?" (`ReminderScreen`, opened from the nag notification), permission card, location fetch. The home screen is a countdown plus a vertical **timeline**: one node per vakit with a tick that marks it prayed (tap again to undo, and the mark time shows under the name), ek vakitler hang under their vakit and open with the chevron. |
| `Screens.kt` | Kıble (compass with magnetic declination fix, kıble saati; opened from the compass button in the home header, back arrow like Ayarlar — its dock slot is reserved for the coming Zikir tab), Kaza (design K2: one total + six columns with a tick under each, "Kaza ekle" menu, "Geçmişten kalan borcunu hesapla" sheet: `kazaDebtDays` (tests in `KazaTest.kt`) adds the estimate to every vakit; above the columns a band (bugün · son 7 gün · seri, `kazaStreak`) and "gün gün eda": what is left in days of six and the day in progress as six beads (`kazaRound`, from `Store.kazaDone_*` lifetime counters that start Nov 2026), a long buzz when a day of six completes; under the columns `KazaCalendar` (22 weeks, gold = plan kept)), Okuma (design M1 "Raf üstte", `design/design-system/okuma-secenekler.html`: today's total over the shelf, book spines with today's share + tick, the chosen reading with plan lead / finish date, 14 days; `ReadingSheet` sets a dated plan. `Store.readings()` = Kur'an (`quranPlan*` keys) + `books` (optional `startPage/from/until`); per-reading pages in `readLogBy` (pages logged before it existed count as Kur'an); plan math `todayShare` / `planLead` / `readingShare` tested in `OkumaTest.kt`; the rose's Sayfa petal = `readingToday()`), Muhasebe (Özet / Gidişat / Kaza). Gidişat has one visual per period (`SubTabs`): Hafta = vakit map 5×7 (`WeekMap`), Ay = 5-day pulse curve + vakit bars (`PulseCurve`), Yıl = heatmap (`YearHeat`), Tümü = cumulative curve + milestones + "ne zaman kılıyorsun". Periods never count days before `Store.firstDay`. Mockups: `design/design-system/gidisat-donemler.html`, `gidisat-kaza-secenekler.html`. |
| `Ayarlar.kt` | Settings screen (theme, sky renderer, reminder cadence, snooze, goals, location, phone permissions). Opened from the gear in the home header (tab 4), not from the bottom nav; its title has a back arrow. |
| `GulLogic.kt` / `GulView.kt` | Muhasebe › Özet 3D rose. Pure petal progress (Namaz, Vaktinde, Sayfa, Kaza, Zikir), prayer day = imsak to imsak; tests in `GulTest.kt`. `GulCard` hosts `assets/gul/gul.html` (three.js 0.170.0, vendored) in a WebView served from `https://gul.local/` via `shouldInterceptRequest`, and drives it through `gul.set` / `gul.spin`. Özet layout is design A "Sahne" (`design/design-system/muhasebe-secenekler.html`): the rose stands full width on the page with no card; the page clears to `#0B1322` (Pal.Ground) and renders without a post pass so no box edge shows. A transparent canvas is NOT composited by the Xiaomi WebView (rose vanishes), and bloom is replaced by a CSS `#halo` screened over the canvas. `design/gul/gul-embed.html` is a generated design copy (URL options `labels`, `shadow`, `p`). Spec `docs/superpowers/specs/2026-11-04-gul-design.md`, approved look `design/gul/gul-3d.html`. |
| `Zikir.kt` | Zikir tab, two sections (`PageTabs`): **Zikirhane** (counter) and **Takip** (habit grid: zikirs × last 5 prayer days, tick / count / +extra; `zikirCell`). A free counter "Serbest" (log key `FREE_ZIKIR`, no target, never in the goal or the rose) is the first chip; counting past a target is logged as extra. `--ei zsection 1` opens Takip. The user's own list (`Store.zikirs`, defaults to tesbihat 33×3) as chips, a shaded tesbih ring, the whole body is the tap target (tick haptic, long buzz at the target, then the next unfinished zikir). Counts per prayer day in `Store.zikirLog`. The rose's 5th petal = average completion (`zikirPetal` in `GulLogic.kt`). Spec `docs/superpowers/specs/2026-11-04-zikir-design.md`, mockup `design/design-system/zikir.html`. |
| `Theme.kt` | One palette, `SeherPalette` (navy + gold, Cormorant). Use `Pal.X` colors and `NumStyle` for big numbers. |
| `Sky.kt` | Seher home header (340 dp): AGSL `RuntimeShader` sky — Rayleigh/Mie atmosphere, soft clouds, stars, the sun and the moon riding one arc across the sky by azimuth (moon phase is real), hazy hills, a mosque with lit windows, and a wind-blown wheat field that melts into the page. Long-press = fast preview. Falls back to a gradient below API 33 or if the shader fails. ~20 ms GPU/frame, capped at ~12 fps. |
| `NamazimWidget.kt` | 1×1 home-screen "canlı ikon": sky gradient for the time of day, crescent, hand to the next prayer, dots fill as prayers are marked, label "İkindi 15:27". Redrawn from `Reminders.scheduleNext` and on widget updates. |
| `SkyLayers.kt` | Photo/video versions of the Seher header, asset driven from `app/src/main/assets/sky/` (`day/dusk/night.webp` plates, optional `wheat.webp` loop, optional `scene.mp4`). Photo mode crossfades the plates by sun altitude and draws the arc + sun + moon (real phase) itself; video mode plays the clip on a TextureView. Selected in Ayarlar › Görünüm › Gökyüzü (`Store.skyMode`) or with `--es skymode`. |

Tests: `app/src/test/.../LogicTest.kt` (reminder cadence, cüz), `DiyanetMatchTest.kt` (our times vs Diyanet Nov 2026, Ankara & İstanbul, ≤1 min).

## Design

- `design/design-system/` — **the Seher design system (approved 2026-11-04)**: tokens (`styles.css`, each with its Compose twin), every M3 component + Namazım extensions (`index.html`, Dev Mode + Copy as prompt), concept of home + Muhasebe › Özet (`concept.html`). Serve the repo root (`python -m http.server 8765`) and open `/design/design-system/index.html`. Body font Inter (`res/font/inter.ttf`, OFL), display/numbers Cormorant; cards are tonal (no shadow); dock = Namaz, Zikir, Okuma, Muhasebe (Zikir counter in `Zikir.kt`).
- `design/canvas/*.dc.html` — the design mockups (open in a browser; they were made on a claude.ai design canvas, private link
  `https://claude.ai/artifact/5Sti4FGu2sFyQBBg8v3XMT`). `Main.dc.html` is the WebGL version of the sky shader.
- `design/sky-test/` — standalone WebGL test page for the sky shader (serve the folder with `python -m http.server`).
- Decisions so far: one theme, Seher (Odak removed Nov 2026); Çini rejected. Logo **"Beş vakit gülü"** (chosen 2026-11-04 from
  `design/logo-secenekler.html`, design B): a rose seen from above like the Ottoman gül motif, five overlapping outer petals = five
  vakit (one tinted = the next vakit), five inner petals, a spiral bud, a faint gold ring, gold lines on navy. `res/drawable/ic_logo.xml`
  (foreground, petals filled with `@color/icon_bg` so they overlap) and `ic_logo_mono.xml` (themed icon, lines only) are generated by
  `design/logo-gen.py` (edit that, not the XML; it also writes `design/icon-preview.html`). The old "Hilal saat" lace crescent is
  replaced; the home-screen widget still draws the crescent outline (`NamazimWidget.kt`).
- The owner rejected illustrated/"machine-drawn" skies; it has to look photographic and move.

## Open work (in rough priority)

0. **Özet rose not visible on the phone.** WebGL draws (pixel readback confirms), but the WebView does not show the canvas. Full investigation log and next steps are in `docs/superpowers/notes/2026-11-04-devir.md`. After that: rose left with vertical Hatim / 30-day bars on the right, the Muhasebe top menu redesign, the "%200" rate bug. Zikir module landed 2026-11-04 (drag-to-reorder still open).
1. **Live icon widget**: built in `NamazimWidget.kt` (1×1, sky colour for the time of day, hand to the next prayer, dots fill,
   "İkindi 15:27"). Still to do: add it once to the home screen and judge it at real size; a widget picker label / preview image
   would be nice. Design: `design/canvas/Canli-Ikon.dc.html`.
2. Check on the phone: softer clouds (the old shards came from the `fract(p*123.34)` hash collapsing in half precision — replaced
   with Hoskins' hash), new mosque + treeline, lace logo, gear/location menu in the header, Ayarlar back arrow.
3. **Seher sky**: the shader remains the default. Photo (A) and video (C) renderers are wired in `SkyLayers.kt` and switchable
   from Ayarlar; `assets/sky/*` now hold the owner's Kling art: three photographic plates (day/dusk/night) and a 10 s looping `scene.mp4`. To replace: `day/dusk/night.webp`
   plates with the same framing and **no sun/moon drawn in** (photo mode draws the arc and the discs itself), an optional looping
   `wheat.webp`, or a `scene.mp4` clip. Shader open ends: the wheat reads a bit like corduroy, the horizon has a light band, and the
   sun blows out at midday. `design/sky-test/sky.js` is one revision behind `Sky.kt`.
4. **Diyanet Awqat Salah API** (owner is applying for an account at awqatsalah.diyanet.gov.tr): district picker + yearly times,
   via a tiny server so credentials are not in the APK (the terms forbid sharing them; quota is ~5 requests/day/endpoint).
   Until then times are computed (`Prayers.kt`).
5. Other phones: lower minSdk (now 31), brand-specific battery/autostart guidance, Play Store vs APK distribution.
   `USE_EXACT_ALARM` may not pass Play review for a non-alarm app.

## Conventions

- Keep changes small; match the surrounding style (Compose, one `Store`, no DI, no extra libraries).
- `shortcut:` comments mark known limits.
- Test on the phone after visual changes; screenshots go to `vakit-ekran/`.

