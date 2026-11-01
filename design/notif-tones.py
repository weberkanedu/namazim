"""Bildirim sesleri: 10 kısa, kulağa hoş gelen ton üretir (res/raw/notif_*.wav).

Tasarım ölçütleri: 2-3 sn, yumuşak giriş/çıkış (klik yok), tepe ~0.5, telefon
hoparlöründe bile net duyulan orta frekanslar, keskin yüksek harmonik yok.
"""
import math
import os
import random
import struct
import wave

RATE = 44100
OUT = os.path.join("app", "src", "main", "res", "raw")
os.makedirs(OUT, exist_ok=True)
random.seed(4)


def write(name, samples):
    peak = max(1e-6, max(abs(s) for s in samples))
    scale = 0.5 / peak
    n = len(samples)
    fade = int(RATE * 0.012)
    out = []
    for i, s in enumerate(samples):
        if i < fade:
            s *= i / fade
        elif i > n - fade:
            s *= max(0.0, (n - i) / fade)
        out.append(int(max(-1.0, min(1.0, s * scale)) * 32767))
    path = os.path.join(OUT, name)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", v) for v in out))
    print(name, round(n / RATE, 2), "sn")


def tone(dur, freqs, decay=1.5, attack=0.01, amp=None):
    """freqs: [(oran, genlik), ...] — temel frekansın harmonikleri."""
    n = int(RATE * dur)
    amp = amp or [1.0 / (i + 1) for i in range(len(freqs))]
    out = []
    a = max(1, int(RATE * attack))
    for i in range(n):
        t = i / RATE
        e = math.exp(-t * decay)
        if i < a:
            e *= i / a
        s = sum(math.sin(2 * math.pi * f * t) * g for (f, g) in freqs) * e
        out.append(s)
    return out


def mix(a, b, offset=0.0, gain=1.0):
    s0 = int(offset * RATE)
    n = max(len(a), s0 + len(b))
    out = [0.0] * n
    for i, v in enumerate(a):
        out[i] += v
    for i, v in enumerate(b):
        out[s0 + i] += v * gain
    return out


def sakin():
    return tone(3.0, [(396, .55), (528, .35), (264, .2), (792, .05)], decay=0.9, attack=0.12)


def can():
    return tone(2.6, [(523.25, .6), (1046.5, .28), (1568, .12), (2093, .06), (261.6, .2)], decay=1.4)


def zil():
    return tone(2.0, [(659.25, .55), (1318.5, .3), (1977.75, .18), (2637, .1)], decay=2.2)


def odak():
    a = tone(0.5, [(880, .7), (1760, .2)], decay=7)
    b = tone(0.5, [(1174.66, .7), (2349, .2)], decay=7)
    return mix(a, b, 0.34)


def damla():
    n = int(RATE * 1.6)
    out = []
    for i in range(n):
        t = i / RATE
        f = 1250 * math.exp(-t * 6) + 420
        e = math.exp(-t * 3.2) * min(1.0, t * 400)
        out.append(math.sin(2 * math.pi * f * t) * e)
    return out


def nefes():
    n = int(RATE * 1.8)
    out = []
    buf = 0.0
    for i in range(n):
        t = i / RATE
        buf = buf * 0.92 + random.uniform(-1, 1) * 0.08
        e = math.sin(math.pi * min(1.0, t / 1.8)) ** 1.5
        out.append(buf * e * 1.6 + math.sin(2 * math.pi * 220 * t) * e * 0.15)
    return out


def hilal():
    a = tone(1.4, [(659.25, .6), (1318.5, .15)], decay=1.8)
    b = tone(1.6, [(880, .6), (1760, .15)], decay=1.4)
    return mix(a, b, 0.30, 0.9)


def sabah():
    notes = [(523.25, 0.0), (659.25, 0.22), (783.99, 0.44)]
    out = []
    for f, off in notes:
        out = mix(out, tone(1.2, [(f, .6), (f * 2, .18)], decay=2.2), off, 0.85)
    return out


def yanki():
    a = tone(1.2, [(587.33, .55), (1174.66, .22), (1761.99, .1)], decay=2.6)
    b = [v * 0.45 for v in a]
    c = [v * 0.2 for v in a]
    return mix(mix(a, b, 0.24), c, 0.48)


def nagme():
    # yumuşak "tel" tınısı: kısa, sönümlü harmonikler + hafif detone ikinci katman
    a = tone(2.2, [(440, .5), (880, .22), (1320, .12), (1760, .06)], decay=1.9)
    b = tone(2.2, [(443, .3), (886, .12)], decay=2.1)
    return mix(a, b)


for name, fn in [("sakin", sakin), ("can", can), ("zil", zil), ("odak", odak), ("damla", damla),
                 ("nefes", nefes), ("hilal", hilal), ("sabah", sabah), ("yanki", yanki), ("nagme", nagme)]:
    write(f"notif_{name}.wav", fn())
