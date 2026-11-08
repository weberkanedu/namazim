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
  (`--es skymode` is gone: the header is always Harem.)
  `--ez resetentries true` (debug) runs Ayarlar › Kayıtları sıfırla (`Store.resetEntries`: prayers, kaza, reading, zikir counts go; location, settings, zikir list, `lastFired` stay) with tracking restarting at the next imsak.
  `--ez testnotif true` (debug) sends the sample "Kıldın mı?" notification (same as Ayarlar › Deneme bildirimi).
  `--ez backuptest true` (debug) exports a backup, parses it back and logs `Backup: round trip … mismatched=[]` (writes nothing).
- The screen sleeps after 60 s; without a wake key the screenshots come out black.

## Code map (`app/src/main/java/com/namazim/`)

| File | What |
|---|---|
| `Prayers.kt` | Prayer times via `adhan2` (TURKEY method) + per-vakit second offsets fitted to Diyanet tables; `slots()` (one prayer per day: start/end/nagEnd), extra times (kerahat, işrak, kuşluk, evvâbin, teheccüd), hijri date (ICU Umm al-Qura), mübarek günler, sûre start pages. |
| `Store.kt` | Everything persisted, in one SharedPreferences file: location, prayed set (`"yyyy-mm-dd|VAKIT"`), kaza counts (+VITIR), reading log, books, all settings, snooze. `settleKaza()` turns ended unprayed prayers into kaza. Backup: `exportBackup()` / `readBackup()` / `restore()` write the whole prefs file as one typed JSON (`{app:"namazim",v:1,prefs}`), restore is one `commit()`; Ayarlar › Yedekleme saves/opens it via the system file picker, and the home screen shows a quiet band after 30 days without a backup (`backupDue`). Android Auto Backup is also on (no `allowBackup=false`). |
| `Reminders.kt` | Alarm chain: always exactly one `setAlarmClock` alarm for the next reminder. `reminders(slot, cfg)` is the cadence; `fire()` shows due notifications; Kıldım / snooze receivers; `test()` sends a sample nag. |
| `MainActivity.kt` | Activity, bottom nav, Namaz (home) screen, full-screen "Kıldın mı?" (`ReminderScreen`, opened from the nag notification), permission card, location fetch. The home body under the header is `VakitlerBody` in `Vakitler.kt`. |
| `Vakitler.kt` | Home "Vakitler", design V7 (`design/design-system/vakitler-secenekler.html`). The logo rose (outer petal = prayed, inner petal = prayed in the first third, i.e. the gül's Vaktinde petal; a gold arc over the current petal shrinks with the time left for vaktinde). Then the countdown to the next threshold (vaktinde → vakit çıkmadan → kerahat in peach). Then the current vakit as a three-part strip (vaktinde · vakit · İkindi's sarı kerahat hatched). One big "…kıldım" (or "geri al"). Then "Bugün": a bead per vakit with the ek vakitler as chips (Güneş, işrak, kuşluk under İmsak). Tapping another petal or a row opens a sheet (times, status, Kıldım / Geri al). Pure `vakitState` / `thirdEnd` are tested in `VakitlerTest.kt`. **Namaz sonrası tesbihat** (design `design/design-system/tesbihat.html`): once the current vakit is marked, the Kıldım button turns into a tesbihat card. "Yaptım" marks it as done by hand or with the cemaat (`Store.setTesbihat(key, "el")`). "Zikirhanede çek" sets `TesbihatNav.key` and opens `TesbihatMode` in `Zikir.kt`, which counts 33·33·33 (`tesbihStep`, progress kept in `Store.tesbihProgress`) and marks "app" at 99. "Sonra" hides the card for this session. Prayed rows carry a tesbih icon, and the vakit sheet has a tesbihat section. Ayarlar › Hedefler: `askTesbihat` (ask at all) and `tesbihatCounts`: the tesbihat is never written into `zikirLog`; `Store.zikirCounts` adds it on read (`tesbihatOn`) while the setting is on, so flipping it changes Takip, Zikirhane and the rose at once. With it off, `dailyZikirs` also drops the three default 33s from the day's list (Zikirhane chips, Takip, rose); a re-targeted one, e.g. Sübhânallah 100, stays. The pencil editor always shows the whole list. Undoing the prayer also undoes its tesbihat. There is no undo button under the rose after Kıldım; undo lives in the vakit sheet (petal or row). Debug: `--ei tab 5 --es tesbihat "2026-11-05|SABAH"`. |
| `Screens.kt` | Kıble (compass with magnetic declination fix, kıble saati; opened from the compass button in the home header, back arrow like Ayarlar — its dock slot is reserved for the coming Zikir tab), Kaza (design K2: one total + six columns with a tick under each, "Kaza ekle" menu, "Geçmişten kalan borcunu hesapla" sheet: `kazaDebtDays` (tests in `KazaTest.kt`) adds the estimate to every vakit; above the columns a band (bugün · son 7 gün · seri, `kazaStreak`) and "gün gün eda": what is left in days of six and the day in progress as six beads (`kazaRound`, from `Store.kazaDone_*` lifetime counters that start Nov 2026), a long buzz when a day of six completes; under the columns `KazaCalendar` (22 weeks, gold = plan kept)), Okuma (design M1 "Raf üstte", `design/design-system/okuma-secenekler.html`: today's total over the shelf, book spines with today's share + tick, the chosen reading with plan lead / finish date, 14 days; `ReadingSheet` sets a dated plan. `Store.readings()` = Kur'an (`quranPlan*` keys) + `books` (optional `startPage/from/until`); per-reading pages in `readLogBy` (pages logged before it existed count as Kur'an); plan math `todayShare` / `planLead` / `readingShare` tested in `OkumaTest.kt`; the rose's Sayfa petal = `readingToday()`), Muhasebe — shown to the user as **Defter** with sections **Özet / İstikrar / Kaza** (renamed 2026-11-05; code still says Muhasebe/Gidişat). Gidişat has one visual per period (`SubTabs`): Hafta = vakit map 5×7 (`WeekMap`), Ay = 5-day pulse curve + vakit bars (`PulseCurve`), Yıl = heatmap (`YearHeat`), Tümü = cumulative curve + milestones + "ne zaman kılıyorsun". Periods never count days before `Store.firstDay`. Mockups: `design/design-system/gidisat-donemler.html`, `gidisat-kaza-secenekler.html`. |
| `Ayarlar.kt` | Settings, design A3 "Kutucuklar" (`design/design-system/ayarlar-secenekler.html`): six `SettingTile`s (Hatırlatmalar, Ses, Hedefler, Konum, İzinler — peach when a permission is missing — and Veri: backup, restore, reset) each saying where things stand; a tap opens that topic's rows in a `SettingSheet`. Opened from the gear in the home header (tab 4), back arrow in the title. A seventh, full-width tile opens Geri bildirim (`Feedback.kt`). |
| `GulLogic.kt` / `GulView.kt` | Muhasebe › Özet 3D rose. Pure petal progress (Namaz, Vaktinde, Sayfa, Kaza, Zikir), prayer day = imsak to imsak; tests in `GulTest.kt`. `GulCard` hosts `assets/gul/gul.html` (three.js 0.170.0, vendored) in a WebView served from `https://gul.local/` via `shouldInterceptRequest`, and drives it through `gul.set` / `gul.spin`. Özet layout is design A "Sahne" (`design/design-system/muhasebe-secenekler.html`): the rose stands full width on the page with no card; the page clears to `#0B1322` (Pal.Ground) and renders without a post pass so no box edge shows. A transparent canvas is NOT composited by the Xiaomi WebView (rose vanishes), and bloom is replaced by a CSS `#halo` screened over the canvas. `design/gul/gul-embed.html` is a generated design copy (URL options `labels`, `shadow`, `p`). Spec `docs/superpowers/specs/2026-11-04-gul-design.md`, approved look `design/gul/gul-3d.html`. |
| `Zikir.kt` | Zikir tab, two sections (`PageTabs`): **Zikirhane** (counter) and **Takip** (habit grid: zikirs × last 5 prayer days, tick / count / +extra; `zikirCell`). A free counter "Serbest" (log key `FREE_ZIKIR`, no target, never in the goal or the rose) is the first chip; counting past a target is logged as extra. `--ei zsection 1` opens Takip. The user's own list (`Store.zikirs`, defaults to tesbihat 33×3) as chips, a shaded tesbih ring, the whole body is the tap target (tick haptic, long buzz at the target, then the next unfinished zikir). Counts per prayer day in `Store.zikirLog`. The rose's 5th petal = average completion (`zikirPetal` in `GulLogic.kt`). Spec `docs/superpowers/specs/2026-11-04-zikir-design.md`, mockup `design/design-system/zikir.html`. |
| `ZikirNur.kt` | Zikirhane look Z1 "Nur" (`design/design-system/zikirhane-secenekler.html`, chosen 2026-11-08): `NurHead` (Arabic + meaning for known zikirs via `zikirArabic`, loose spelling match, system Arabic font), `NurRing` (gold arc filling once to the target, no turns; the free counter turns every 33), `NurBursts` (light motes rising from the touch point, no ring; frame loop only while something is on screen), `ayetOfDay`. Pure `zikirProgress` / `zikirArabic` tested in `ZikirNurTest.kt`. Takip: tapping a zikir name opens Zikirhane on it (`pick`). Zikirhane has no chips since 2026-11-08: the zikir is picked in Takip, and reaching a target moves on to the next one. Arabic for user zikirs (design C, `design/design-system/zikir-arapca-secenekler.html`): `ZIKIR_LIST` = 17 known zikirs + the 99 names; the pencil sheet suggests matches as the name is typed (`suggestZikir`); the two optional fields (Arapça yazı, Anlamı) are always open, a pick fills them. A zikir keeps its own `ar` / `mean` (saved in `Store.zikirs` JSON); `zikirArabic(z)` prefers it over the built-in entry. |
| `Ogutler.kt` | 26 ayet/hadis about namaz, each with its source (`OGUTLER`). `nextOgut(store)` hands out the next one in turn (`Store.ogutIndex`), so every "Kıldın mı?" notification (BigTextStyle, text + "— source") and every opening of `ReminderScreen` shows a different one. The prep and "vakti çıkıyor" notifications keep their own short text. Status-bar icon `ic_notif.xml` = the gül's outer petals and bud, drawn thick. |
| `Feedback.kt` | Ayarlar › Geri bildirim: a message (plus phone model and app version) goes to the owner's Telegram via Bot API `sendMessage`. Bot token and chat id are read from `local.properties` (`telegram.token=…`, `telegram.chat=…`) into `BuildConfig` at build time; they are never in git. Without them the form says it is not set up. The token can be pulled out of the APK (`shortcut:` note): use a bot for nothing else, and put it behind a server before a public release. Per-phone anti-spam: one message a minute, at most 5 a day (`feedbackBlock`, `Store.feedbackLog`, tests in `OgutTest.kt`). |
| `Theme.kt` | One palette, `SeherPalette` (navy + gold, Cormorant). Use `Pal.X` colors and `NumStyle` for big numbers. |
| `Sky.kt` | Only the astronomy now: `skyBodies` (sun and moon alt/az and moon phase, used by the widget and Kıble) and `SkyDebug` (test extras). The AGSL shader sky was removed in Nov 2026. |
| `NamazimWidget.kt` | 1×1 home-screen "canlı ikon": sky gradient for the time of day, the logo rose (petal per vakit, gold when prayed, bright edge on the next one), label "İkindi 15:27". Redrawn from `Reminders.scheduleNext` and on widget updates. |
| `SkyLayers.kt` | **The home header (Harem, the only one)**: 240 dp and plays photoreal Mekke/Medine loops (`assets/sky/mekke-gece|mekke-gun|medine-safak|medine-aksam.mp4`, made with Veo, see `design/namaz-vitrin-brief.md`) picked by `haremClip` (imsak–güneş Medine dawn, güneş–ikindi Mekke day, ikindi–akşam Medine sunset, else Mekke night; `HaremTest.kt`), cross-fading 1.5 s when the vakit turns; the date line moves under the picture. `SkyVideo` center-crops (TextureView matrix) and pauses in the background. `--el skytime` previews the other clips. Photo plates, the single-clip video mode and the shader were removed in Nov 2026. |

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
  `design/logo-gen.py` (edit that, not the XML; it also writes `design/icon-preview.html`). Launcher look since 2026-11-08 is **L1** (`design/logo-parlak-secenekler.html`): gold gradient background (`res/drawable/ic_bg_gold.xml`, also generated) with navy-filled petals and light gold edges, because the navy icon got lost among bright launcher icons; `ic_logo_mono.xml` (themed icon) is unchanged. The old "Hilal saat" lace crescent is
  replaced; the home-screen widget (`NamazimWidget.kt`) draws the same rose on the time-of-day sky: petal i = vakit i, prayed petals filled gold, the next/owed vakit's petal drawn last with a bright edge.
- The owner rejected illustrated/"machine-drawn" skies; it has to look photographic and move.

## Open work (in rough priority)

0. **Özet rose not visible on the phone.** WebGL draws (pixel readback confirms), but the WebView does not show the canvas. Full investigation log and next steps are in `docs/superpowers/notes/2026-11-04-devir.md`. After that: rose left with vertical Hatim / 30-day bars on the right, the Muhasebe top menu redesign, the "%200" rate bug. Zikir module landed 2026-11-04 (drag-to-reorder still open).
1. **Live icon widget**: built in `NamazimWidget.kt` (1×1, sky colour for the time of day, hand to the next prayer, dots fill,
   "İkindi 15:27"). Still to do: add it once to the home screen and judge it at real size; a widget picker label / preview image
   would be nice. Design: `design/canvas/Canli-Ikon.dc.html`.
2. Check on the phone: softer clouds (the old shards came from the `fract(p*123.34)` hash collapsing in half precision — replaced
   with Hoskins' hash), new mosque + treeline, lace logo, gear/location menu in the header, Ayarlar back arrow.
3. **Seher sky** (obsolete since Nov 2026, kept for history): the shader remained the default. Photo (A) and video (C) renderers are wired in `SkyLayers.kt` and switchable
   from Ayarlar; `assets/sky/*` now hold the owner's Kling art: three photographic plates (day/dusk/night) and a 10 s looping `scene.mp4`. To replace: `day/dusk/night.webp`
   plates with the same framing and **no sun/moon drawn in** (photo mode draws the arc and the discs itself), an optional looping
   `wheat.webp`, or a `scene.mp4` clip. Shader open ends: the wheat reads a bit like corduroy, the horizon has a light band, and the
   sun blows out at midday. `design/sky-test/sky.js` is one revision behind `Sky.kt`.
4a. **Feedback via a small server (before any public release, owner asked 2026-11-05):** today the Telegram bot token ships in the APK (`Feedback.kt`). Move `sendMessage` behind the same tiny server planned for Diyanet: the app posts the text there, the server holds the token and rate-limits per device/IP. Then drop `telegram.token` from `local.properties`/`BuildConfig` and revoke the old token in BotFather.
4. **Diyanet Awqat Salah API** (owner is applying for an account at awqatsalah.diyanet.gov.tr): district picker + yearly times,
   via a tiny server so credentials are not in the APK (the terms forbid sharing them; quota is ~5 requests/day/endpoint).
   Until then times are computed (`Prayers.kt`).
5. Other phones: lower minSdk (now 31), brand-specific battery/autostart guidance, Play Store vs APK distribution.
   `USE_EXACT_ALARM` may not pass Play review for a non-alarm app.

## Conventions

- The dock floats over the pages (`App` overlays `SeherNav`). Every tab page ends its scroll with `padding(bottom = LocalDockPad.current)` so its last row clears the island.

- Keep changes small; match the surrounding style (Compose, one `Store`, no DI, no extra libraries).
- `shortcut:` comments mark known limits.
- Test on the phone after visual changes; screenshots go to `vakit-ekran/`.

