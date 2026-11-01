"""Bildirim sesleri: üç kısa, yumuşak ton üretir (res/raw/*.wav).

- sakin : alçak, yavaş sönümlenen çift sinüs (gece için)
- zil   : çan benzeri harmonikler
- odak  : iki kısa net bip (gündüz için)
"""
import math
import os
import struct
import wave

RATE = 44100
OUT = os.path.join("app", "src", "main", "res", "raw")
os.makedirs(OUT, exist_ok=True)


def write(name, samples):
    peak = max(1e-6, max(abs(s) for s in samples))
    scale = 0.55 / peak
    path = os.path.join(OUT, name)
    with wave.open(path, "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s * scale)) * 32767)) for s in samples))
    print(name, len(samples) / RATE, "sn")


def env(i, n, attack=0.02, release=0.5):
    a = int(n * attack) or 1
    r = int(n * release) or 1
    if i < a:
        return i / a
    if i > n - r:
        return max(0.0, (n - i) / r)
    return 1.0


def sakin():
    dur = 2.6
    n = int(RATE * dur)
    out = []
    for i in range(n):
        t = i / RATE
        e = env(i, n, 0.06, 0.75)
        s = (math.sin(2 * math.pi * 396 * t) * 0.6 + math.sin(2 * math.pi * 528 * t) * 0.4
             + math.sin(2 * math.pi * 792 * t) * 0.08)
        out.append(s * e)
    return out


def zil():
    dur = 2.8
    n = int(RATE * dur)
    out = []
    for i in range(n):
        t = i / RATE
        e = math.exp(-t * 1.6)
        s = (math.sin(2 * math.pi * 660 * t) * 0.55
             + math.sin(2 * math.pi * 1320 * t) * 0.25
             + math.sin(2 * math.pi * 1980 * t) * 0.12
             + math.sin(2 * math.pi * 2640 * t) * 0.06)
        out.append(s * e * min(1.0, t * 200))
    return out


def odak():
    n = int(RATE * 0.9)
    out = [0.0] * n
    for start, freq in ((0.0, 880.0), (0.42, 1174.0)):
        s0 = int(start * RATE)
        for i in range(int(RATE * 0.28)):
            t = i / RATE
            e = math.exp(-t * 9)
            if s0 + i < n:
                out[s0 + i] += math.sin(2 * math.pi * freq * t) * e
    return out


write("notif_sakin.wav", sakin())
write("notif_zil.wav", zil())
write("notif_odak.wav", odak())
