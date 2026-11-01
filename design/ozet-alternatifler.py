"""Özet ekranındaki eşmerkezli halkalara alternatifler — tek sayfalık karşılaştırma.

Çıktı: vakit-ekran/ozet-alternatifler.png
"""
from PIL import Image, ImageDraw, ImageFont

W, H = 1120, 1960
BG = (11, 18, 32)
CARD = (20, 28, 46)
LINE = (38, 48, 70)
TEXT = (244, 242, 236)
MUTED = (140, 149, 168)
GOLD = (230, 180, 94)
TEAL = (107, 197, 176)
BLUE = (138, 166, 216)
WARN = (224, 138, 91)


def font(size, bold=False, serif=False):
    path = r"C:\Windows\Fonts\georgia.ttf" if serif else r"C:\Windows\Fonts\segoeui.ttf"
    if bold:
        path = r"C:\Windows\Fonts\georgiab.ttf" if serif else r"C:\Windows\Fonts\segoeuib.ttf"
    return ImageFont.truetype(path, size)


img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

TITLE = font(46, serif=True)
SUB = font(20)
H2 = font(26, bold=True)
BODY = font(19)
BIG = font(64, serif=True)
MID = font(34, serif=True)


def card(y, h, title, note):
    d.rounded_rectangle([40, y, W - 40, y + h], 26, fill=CARD)
    d.text((72, y + 26), title, font=H2, fill=TEXT)
    d.text((72, y + 62), note, font=BODY, fill=MUTED)


d.text((40, 34), "Özet · halka yerine ne kullanabiliriz?", font=TITLE, fill=TEXT)
d.text((42, 104), "Dördü de aynı veriyi gösteriyor; farklı okuma biçimleri.", font=SUB, fill=MUTED)

# ---------------------------------------------------------------- A: yatay çubuklar
y = 160
card(y, 400, "A · Hedef çubukları", "Sakin, tek bakışta okunur; uzun liste gibi durmaz.")
rows = [("Namaz", "3 / 5", 0.60, GOLD), ("Okuma", "2 / 30 sayfa", 0.07, TEAL),
        ("Hatim", "2 / 604 sayfa", 0.02, BLUE), ("Kaza", "18 vakit kaldı", 0.25, WARN)]
ry = y + 120
for name, val, frac, col in rows:
    d.text((72, ry), name, font=BODY, fill=TEXT)
    bx0, bx1 = 240, 790
    d.rounded_rectangle([bx0, ry + 6, bx1, ry + 20], 7, fill=LINE)
    d.rounded_rectangle([bx0, ry + 6, bx0 + int((bx1 - bx0) * max(frac, 0.02)), ry + 20], 7, fill=col)
    d.text((bx1 + 24, ry), val, font=BODY, fill=MUTED)
    ry += 62

# ---------------------------------------------------------------- B: büyük sayı
y = 590
card(y, 400, "B · Büyük sayı + noktalar", "Bugünün özü tek sayı; diğerleri ikinci planda.")
d.text((72, y + 120), "3", font=font(92, serif=True), fill=GOLD)
d.text((150, y + 176), "/ 5", font=MID, fill=MUTED)
d.text((72, y + 232), "bugün kılınan vakit", font=BODY, fill=MUTED)
for i in range(5):
    cx, cy = 96 + i * 54, y + 300
    if i < 3:
        d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], fill=GOLD)
    else:
        d.ellipse([cx - 11, cy - 11, cx + 11, cy + 11], outline=LINE, width=3)
mx = 470
for name, val, col in [("Sayfa", "2 / 30", TEAL), ("Hatim", "2 / 604", BLUE), ("Kaza", "18 vakit", WARN)]:
    d.text((mx, y + 150), name, font=SUB, fill=MUTED)
    d.text((mx, y + 180), val, font=font(34, serif=True), fill=col)
    mx += 210

# ---------------------------------------------------------------- C: 2x2 kutular
y = 1020
card(y, 400, "C · Dört kutu", "En sade hâli; her metrik kendi kutusunda.")
boxes = [("3/5", "namaz", GOLD), ("2/30", "sayfa", TEAL), ("2/604", "hatim", BLUE), ("18", "kaza", WARN)]
for i, (big, label, col) in enumerate(boxes):
    bx = 72 + (i % 2) * 500
    by = y + 116 + (i // 2) * 132
    d.rounded_rectangle([bx, by, bx + 460, by + 116], 18, fill=(26, 36, 56))
    d.text((bx + 24, by + 20), big, font=font(44, serif=True), fill=col)
    d.text((bx + 24, by + 76), label, font=BODY, fill=MUTED)

# ---------------------------------------------------------------- D: hilal
y = 1450
card(y, 400, "D · Hilal dolgusu", "Temaya en yakını: hilal, kılınan oranla doluyor.")
cx, cy, r = 210, y + 236, 96
d.arc([cx - r, cy - r, cx + r, cy + r], 130, 410, fill=LINE, width=16)
d.arc([cx - r, cy - r, cx + r, cy + r], 130, 130 + int(280 * 0.6), fill=GOLD, width=16)
d.text((cx - 26, cy - 34), "3/5", font=font(30, serif=True), fill=TEXT)
d.text((340, y + 150), "Hilal içindeki dolu kısım", font=BODY, fill=TEXT)
d.text((340, y + 182), "bugünün oranını gösterir.", font=BODY, fill=MUTED)
d.text((340, y + 232), "Kaza ve hatim ayrı satırda kalır.", font=BODY, fill=MUTED)
d.text((340, y + 286), "Not: hilal, logonun diliyle aynı.", font=SUB, fill=GOLD)

img.save(r"vakit-ekran\ozet-alternatifler.png")
print("ok")
