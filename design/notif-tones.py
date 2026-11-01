"""Namaz uygulaması için 10 bildirim sesi — İslami motifler, birbirinden belirgin.

Fikir: her ses farklı bir "enstrüman" ve farklı bir makam/ritim üzerine kurulu;
böylece listede karışmıyorlar.

1 Ezan · Hicaz   — ney tınısıyla kısa, melismatik bir ezan cümlesi
2 Sala · Rast    — daha derin, ağır iki cümle
3 Ney            — tek uzun nefes, vibrato ve kayma
4 Kudüm          — yumuşak iki davul vuruşu
5 Def            — zilli tef şıngırtısı
6 Ud             — iki telli, parmakla çalınmış akor
7 Rebap          — yaylı, kabaran uzun ton
8 Zikir          — alçak, ritmik nefes/davul nabzı
9 Hilal          — çanak (singing bowl), uzun ve metalik
10 Sabah         — Saba makamında parlak, yükselen cümle

Tüm sesler 22.05 kHz mono; tepe ~0.5, yumuşak giriş/çıkış.
"""
import math
import os
import struct
import wave

import numpy as np

FS = 22050
OUT = os.path.join("app", "src", "main", "res", "raw")
os.makedirs(OUT, exist_ok=True)
rng = np.random.default_rng(7)


# ----------------------------------------------------------------- yardımcılar
def t_axis(dur):
    return np.arange(int(FS * dur)) / FS


def env(n, attack=0.01, release=0.3, curve=1.0):
    a = max(1, int(n * attack))
    r = max(1, int(n * release))
    e = np.ones(n)
    e[:a] = np.linspace(0, 1, a) ** curve
    e[n - r:] = np.linspace(1, 0, r) ** curve
    return e


def onepole_lp(x, cutoff):
    a = 1 - math.exp(-2 * math.pi * cutoff / FS)
    y = np.empty_like(x)
    acc = 0.0
    for i in range(len(x)):
        acc += a * (x[i] - acc)
        y[i] = acc
    return y


def bandpass(x, lo, hi):
    return onepole_lp(x, hi) - onepole_lp(x, lo)


def vibrato(t, depth_cents=12.0, rate=5.2):
    return 2 ** (depth_cents / 1200.0 * np.sin(2 * math.pi * rate * t))


def glide(freqs, times):
    """Zaman noktaları arasında üstel/uniform geçişli frekans eğrisi."""
    return np.interp(times, np.linspace(times[0], times[-1], len(freqs)), freqs)


def ney(dur, freq, amp=1.0, vib=10.0, breath=0.05):
    t = t_axis(dur)
    f = freq * vibrato(t, vib)
    ph = 2 * math.pi * np.cumsum(f) / FS
    body = np.sin(ph) + 0.35 * np.sin(2 * ph) + 0.12 * np.sin(3 * ph) + 0.05 * np.sin(4 * ph)
    air = bandpass(rng.normal(0, 1, len(t)), 1200, 5000) * breath * (0.6 + 0.4 * np.sin(2 * math.pi * 1.3 * t))
    return (body + air) * env(len(t), 0.08, 0.35) * amp


def pluck(dur, freq, amp=1.0, damp=0.996, bright=0.5):
    n = int(FS * dur)
    period = max(2, int(FS / freq))
    buf = rng.normal(0, 1, period)
    buf = buf - bright * onepole_lp(buf, 600)
    buf = onepole_lp(buf, 3200)
    out = np.empty(n)
    idx = 0
    last = 0.0
    for i in range(n):
        cur = buf[idx]
        out[i] = cur
        nxt = damp * 0.5 * (cur + last)
        last = cur
        buf[idx] = nxt
        idx = (idx + 1) % period
    return out * env(n, 0.002, 0.5) * amp


def drum(dur, f0, f1, amp=1.0, noise=0.25):
    t = t_axis(dur)
    f = f1 + (f0 - f1) * np.exp(-t * 18)
    body = np.sin(2 * math.pi * np.cumsum(f) / FS) * np.exp(-t * 9)
    skin = bandpass(rng.normal(0, 1, len(t)), 200, 2500) * np.exp(-t * 40) * noise
    return (body + skin) * amp


def jingle(dur=1.1, amp=1.0):
    t = t_axis(dur)
    out = np.zeros(len(t))
    for centre in (4200, 5600, 7200, 9100):
        out += bandpass(rng.normal(0, 1, len(t)), centre - 900, centre + 900) * np.exp(-t * 14)
    out += np.sin(2 * math.pi * 2400 * t) * np.exp(-t * 22) * 0.3
    return out * env(len(t), 0.002, 0.6) * 0.5 * amp


def bowl(dur, freq, amp=1.0):
    t = t_axis(dur)
    partials = [(1.0, 1.0, 1.1), (2.76, 0.5, 1.6), (5.4, 0.28, 2.3), (8.2, 0.14, 3.2), (11.9, 0.06, 4.5)]
    out = np.zeros(len(t))
    for ratio, gain, decay in partials:
        out += np.sin(2 * math.pi * freq * ratio * t) * gain * np.exp(-t * decay)
    return out * amp


def bowed(dur, freq, amp=1.0):
    t = t_axis(dur)
    f = freq * vibrato(t, 18, 4.6)
    ph = 2 * math.pi * np.cumsum(f) / FS
    saw = sum(np.sin(k * ph) / k for k in range(1, 7))
    return onepole_lp(saw, 2400) * env(len(t), 0.25, 0.4) * amp


def phrase(notes, timbre="ney", gap=0.0, amp=1.0):
    """[(freq, süre), ...] dizisini üst üste binmeli çalar."""
    total = sum(d for _, d in notes) - gap * (len(notes) - 1)
    out = np.zeros(int(FS * total))
    pos = 0.0
    for f, d in notes:
        seg = ney(d, f, amp) if timbre == "ney" else pluck(d, f, amp)
        s = int(pos * FS)
        e = min(len(out), s + len(seg))
        out[s:e] += seg[: e - s]
        pos += d - gap
    return out


# makamlar (yaklaşık, Batı eşdeğeri)
def scale(root, steps):
    return [root * (2 ** (s / 12)) for s in steps]


HICAZ = scale(330.0, [0, 1, 4, 5, 7, 8, 11, 12])      # E hicaz
RAST = scale(261.6, [0, 2, 3.5, 5, 7, 9, 10.5, 12])   # C rast (3.5/10.5 komalık)
SABA = scale(293.7, [0, 1.5, 3, 4, 6, 8, 10, 12])     # D saba


def normalise(x, target_rms=0.15, ceiling=0.9):
    """Algılanan ses düzeyini eşitle: tepeye göre değil RMS'e göre ölçekle."""
    rms = math.sqrt(float(np.mean(np.square(x)))) if len(x) else 0.0
    gain = target_rms / max(rms, 1e-6)
    y = x * gain
    peak = float(np.max(np.abs(y))) if len(y) else 0.0
    if peak > ceiling:
        y *= ceiling / peak
    return y


def write(name, x):
    n = len(x)
    fade = max(1, int(FS * 0.01))
    x = x.copy()
    x[:fade] *= np.linspace(0, 1, fade)
    x[-fade:] *= np.linspace(1, 0, fade)
    x = np.clip(x, -1, 1)
    data = (x * 32767).astype("<i2")
    with wave.open(os.path.join(OUT, name), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(FS)
        w.writeframes(data.tobytes())
    print(f"{name:26} {n / FS:.2f} sn")


# ----------------------------------------------------------------- sesler
def ezan():
    f = HICAZ
    notes = [(f[4], 0.55), (f[5], 0.35), (f[4], 0.30), (f[3], 0.45), (f[2], 0.35), (f[1], 0.55), (f[0], 0.95)]
    return normalise(phrase(notes, "ney", gap=0.10))


def sala():
    f = RAST
    a = phrase([(f[3], 0.7), (f[4], 0.5), (f[5], 0.7), (f[4], 0.45)], "ney", gap=0.12)
    b = phrase([(f[2], 0.6), (f[1], 0.5), (f[0], 1.2)], "ney", gap=0.12)
    out = np.concatenate([a, np.zeros(int(FS * 0.12)), b])
    out = out[: int(FS * 3.2)]
    return normalise(out)


def ney_solo():
    t = t_axis(2.8)
    f = 392 * vibrato(t, 14, 5.0) * (1 + 0.006 * np.sin(2 * math.pi * 0.7 * t))
    ph = 2 * math.pi * np.cumsum(f) / FS
    body = np.sin(ph) + 0.3 * np.sin(2 * ph) + 0.1 * np.sin(3 * ph)
    air = bandpass(rng.normal(0, 1, len(t)), 900, 4500) * 0.07
    return normalise((body + air) * env(len(t), 0.18, 0.45))


def kudum():
    a = drum(0.5, 130, 78)
    b = drum(0.4, 118, 72) * 0.7
    out = np.concatenate([a, np.zeros(int(FS * 0.12)), b])
    return normalise(out)


def def_():
    a = jingle(0.9)
    b = jingle(0.55) * 0.6
    out = np.concatenate([a, np.zeros(int(FS * 0.08)), b])
    return normalise(out)


def ud():
    a = pluck(1.8, 196.0, damp=0.997)
    b = pluck(1.6, 246.94, damp=0.996)
    out = a.copy()
    s = int(FS * 0.18)
    out[s:s + len(b)] += b[: len(out) - s] * 0.8
    return normalise(out)


def rebap():
    a = bowed(2.2, 174.6)
    b = bowed(1.4, 196.0) * 0.6
    out = a.copy()
    s = int(FS * 0.55)
    out[s:s + len(b)] += b[: len(out) - s]
    return normalise(out)


def zikir():
    n = int(FS * 2.0)
    out = np.zeros(n)
    for i, off in enumerate((0.0, 0.42, 0.84, 1.26)):
        hit = drum(0.45, 110, 70, amp=0.9 if i % 2 == 0 else 0.6)
        s = int(off * FS)
        e = min(n, s + len(hit))
        out[s:e] += hit[: e - s]
    hum = np.sin(2 * math.pi * 98 * t_axis(1.9)) * env(int(FS * 1.9), 0.3, 0.5) * 0.25
    out[: len(hum)] += hum
    return normalise(out)


def hilal_bowl():
    return normalise(bowl(3.2, 220.0))


def sabah():
    f = SABA
    return normalise(phrase([(f[2], 0.32), (f[3], 0.30), (f[4], 0.34), (f[5], 0.5), (f[6], 0.7)], "pluck", gap=0.06))


write("notif_ezan.wav", ezan())
write("notif_sala.wav", sala())
write("notif_ney.wav", ney_solo())
write("notif_kudum.wav", kudum())
write("notif_def.wav", def_())
write("notif_ud.wav", ud())
write("notif_rebap.wav", rebap())
write("notif_zikir.wav", zikir())
write("notif_hilal.wav", hilal_bowl())
write("notif_sabah.wav", sabah())

