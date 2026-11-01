# Namazım — project guide for coding agents

Android prayer app (Kotlin + Jetpack Compose). Goal: get the user to actually pray. Reminders before each
prayer time, then "kıldın mı?" nags until the user taps **Kıldım**. Missed prayers go to kaza automatically.
All UI text is Turkish. The owner cares a lot about visuals and motion.

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
  `adb shell am start -n com.namazim/.MainActivity --ei tab N` (0 Namaz, 1 Kıble, 2 Okuma, 3 Muhasebe, 4 Ayarlar),
  then `adb exec-out screencap -p > shot.png`.

## Code map (`app/src/main/java/com/namazim/`)

| File | What |
|---|---|
| `Prayers.kt` | Prayer times via `adhan2` (TURKEY method) + per-vakit second offsets fitted to Diyanet tables; `slots()` (one prayer per day: start/end/nagEnd), extra times (kerahat, işrak, kuşluk, evvâbin, teheccüd), hijri date (ICU Umm al-Qura), mübarek günler, sûre start pages. |
| `Store.kt` | Everything persisted, in one SharedPreferences file: location, prayed set (`"yyyy-mm-dd|VAKIT"`), kaza counts (+VITIR), reading log, books, all settings, snooze. `settleKaza()` turns ended unprayed prayers into kaza. |
| `Reminders.kt` | Alarm chain: always exactly one `setAlarmClock` alarm for the next reminder. `reminders(slot, cfg)` is the cadence; `fire()` shows due notifications; Kıldım / snooze receivers; `test()` sends a sample nag. |
| `MainActivity.kt` | Activity, bottom nav, Namaz (home) screen, full-screen "Kıldın mı?" (`ReminderScreen`, opened from the nag notification), permission card, location fetch. |
| `Screens.kt` | Kıble (compass with magnetic declination fix, kıble saati), Kaza, Okuma (Kur'an pages/cüz/hatim, daily goal, books), Muhasebe (Özet + Kaza tabs, heatmap, streaks). |
| `Ayarlar.kt` | Settings screen (theme, reminder cadence, snooze, extras, goals, location, phone permissions). |
| `Theme.kt` | Two palettes, `SeherPalette` (navy + gold, Cormorant) and `OdakPalette` (black + lime, Space Grotesk). `Pal.current` is Compose state, so switching theme recomposes everything. Use `Pal.X` colors and `NumStyle` for big numbers. |
| `Sky.kt` | Seher home header: physically based sky as an AGSL `RuntimeShader` (Rayleigh/Mie scattering, clouds, stars, real sun/moon position and phase, planes, birds, hills + mosque). Long-press = fast preview. Falls back to a gradient below API 33 or if the shader fails. ~20 ms GPU/frame, capped at ~12 fps. |
| `Odak.kt` | Odak home header: ring of the five prayers. |

Tests: `app/src/test/.../LogicTest.kt` (reminder cadence, cüz), `DiyanetMatchTest.kt` (our times vs Diyanet Nov 2026, Ankara & İstanbul, ≤1 min).

## Design

- `design/canvas/*.dc.html` — the design mockups (open in a browser; they were made on a claude.ai design canvas, private link
  `https://claude.ai/artifact/5Sti4FGu2sFyQBBg8v3XMT`). `Main.dc.html` is the WebGL version of the sky shader.
- `design/sky-test/` — standalone WebGL test page for the sky shader (serve the folder with `python -m http.server`).
- Decisions so far: two themes (Seher default, Odak); Çini rejected. Logo "Hilal saat" (crescent as a clock face, 5 dots = 5 vakit,
  hand points to the next one) — already the adaptive + themed launcher icon (`res/drawable/ic_logo.xml`).
- The owner rejected illustrated/"machine-drawn" skies; it has to look photographic and move.

## Open work (in rough priority)

1. **Live icon widget**: Android doesn't allow live launcher icons for third‑party apps, so build a 1×1 home-screen widget that
   looks like the logo: background = sky color for the time of day, hand turns to the next prayer, dots fill as prayers are marked,
   label like "İkindi 15:27". Update it from `Reminders.scheduleNext`. Design: `design/canvas/Canli-Ikon.dc.html`.
2. Check on the phone: new softer clouds, 2× sun/moon, Odak theme, Ayarlar, Muhasebe › Kaza.
3. **Diyanet Awqat Salah API** (owner is applying for an account at awqatsalah.diyanet.gov.tr): district picker + yearly times,
   via a tiny server so credentials are not in the APK (the terms forbid sharing them; quota is ~5 requests/day/endpoint).
   Until then times are computed (`Prayers.kt`).
4. Other phones: lower minSdk (now 31), brand-specific battery/autostart guidance, Play Store vs APK distribution.
   `USE_EXACT_ALARM` may not pass Play review for a non-alarm app.
5. Odak versions of Kıble/Okuma/Muhasebe are only recolored, not redesigned.

## Conventions

- Keep changes small; match the surrounding style (Compose, one `Store`, no DI, no extra libraries).
- `shortcut:` comments mark known limits.
- Test on the phone after visual changes; screenshots go to `vakit-ekran/`.
