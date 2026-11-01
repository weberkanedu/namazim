"""Araştırmadaki kalıpları tek panoda gösteren wireframe tahtası (Pillow)."""
from PIL import Image, ImageDraw, ImageFont

W, H = 1220, 900
BG = (24, 30, 42)
CARD = (16, 24, 40)
TEXT = (243, 238, 227)
MUTED = (201, 209, 220)
GOLD = (232, 190, 116)
LINE = (44, 58, 80)
DIM = (120, 136, 160)

F_DISPLAY = r"app\src\main\res\font\cormorant.ttf"
F_SANS = r"C:\Windows\Fonts\segoeui.ttf"
F_SANSB = r"C:\Windows\Fonts\segoeuib.ttf"


def f(p, s):
    return ImageFont.truetype(p, s)


def rr(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def cir(d, cx, cy, r, fill=None, outline=None, width=1):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=outline, width=width)


def tick(d, cx, cy, r, on, active=False):
    if on:
        cir(d, cx, cy, r, fill=GOLD)
        d.line((cx - r * .45, cy, cx - r * .05, cy + r * .4, cx + r * .5, cy - r * .45),
               fill=CARD, width=max(2, int(r * .34)), joint="curve")
    else:
        cir(d, cx, cy, r, outline=GOLD if active else DIM, width=2)


def rows(d, x, y, w, n=5, step=34, ticks=True, done=2, active=4):
    for i in range(n):
        d.line((x, y + i * step + step - 8, x + w, y + i * step + step - 8), fill=LINE)
        d.text((x, y + i * step), f"vakit {i+1}", font=f(F_SANS, 12), fill=GOLD if i == active else TEXT)
        d.text((x + w - 46, y + i * step), "05:46", font=f(F_SANS, 12),
               fill=GOLD if i == active else TEXT, anchor="ra")
        if ticks:
            tick(d, x + w - 18, y + i * step + 7, 8, i < done, i == active)


def card(sheet, sd, x, y, w, h, kicker, name, note):
    rr(sd, (x, y, x + w, y + h), 14, fill=CARD)
    sd.text((x + 16, y + 12), kicker, font=f(F_SANSB, 10), fill=GOLD)
    sd.text((x + 16, y + 26), name, font=f(F_SANSB, 14), fill=TEXT)
    sd.text((x + 16, y + h - 30), note, font=f(F_SANS, 10), fill=MUTED)


def hero_ring(d, x, y):
    cir(d, x + 74, y + 74, 56, outline=(70, 86, 110), width=8)
    d.arc((x + 18, y + 18, x + 130, y + 130), -90, 160, fill=GOLD, width=8)
    d.text((x + 74, y + 58), "9:02", font=f(F_DISPLAY, 34), fill=TEXT, anchor="ma")
    d.text((x + 74, y + 92), "yatsı", font=f(F_SANS, 10), fill=MUTED, anchor="ma")
    for i in range(5):
        cir(d, x + 20 + i * 12, y + 150, 3, fill=GOLD if i < 3 else DIM)


def checklist(d, x, y):
    d.text((x, y - 18), "BUGÜN", font=f(F_SANSB, 9), fill=GOLD)
    d.text((x + 150, y - 18), "3/5", font=f(F_SANSB, 11), fill=TEXT, anchor="ra")
    rows(d, x, y, 168, n=5, step=30, ticks=True, done=3, active=4)


def compact(d, x, y):
    d.text((x, y - 18), "YATSI · 9:02", font=f(F_SANSB, 10), fill=GOLD)
    rows(d, x, y, 168, n=5, step=30, ticks=True, done=2, active=4)


def chips(d, x, y):
    for i in range(5):
        on = i < 2
        act = i == 4
        rr(d, (x + i * 34, y - 18, x + i * 34 + 30, y + 6), 12,
           fill=GOLD if on else None, outline=GOLD if act else LINE, width=2)
    rows(d, x, y + 30, 168, n=4, step=30, ticks=False, done=0, active=3)


def card_today(d, x, y):
    rr(d, (x, y - 20, x + 168, y + 16), 18, fill=(26, 38, 60))
    d.text((x + 12, y - 12), "Yatsı 18:59", font=f(F_SANSB, 11), fill=TEXT)
    d.text((x + 156, y - 12), "9:02", font=f(F_SANSB, 11), fill=GOLD, anchor="ra")
    rows(d, x, y + 34, 168, n=5, step=30, ticks=True, done=2, active=4)


def timeline(d, x, y):
    d.line((x + 14, y - 10, x + 14, y + 150), fill=LINE, width=2)
    for i in range(5):
        cy = y + i * 34
        pos = i == 4
        cir(d, x + 14, cy, 5, fill=GOLD if pos else CARD, outline=GOLD if pos else DIM, width=2)
        d.text((x + 30, cy - 7), f"vakit {i+1}", font=f(F_SANSB if pos else F_SANS, 12),
               fill=GOLD if pos else TEXT)
        d.text((x + 168, cy - 7), "05:46", font=f(F_SANS, 12), fill=GOLD if pos else TEXT, anchor="ra")
        if i < 3:
            tick(d, x + 190, cy, 8, True)


if __name__ == "__main__":
    sheet = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((24, 18), "Namaz uygulamalarında tutan ana sayfa kalıpları", font=f(F_SANSB, 18), fill=TEXT)
    d.text((24, 44), "Wireframe: ekran görüntüleri indirilemediği için yapılar çizildi; kaynaklar altta.", font=f(F_SANS, 11), fill=MUTED)

    cards = [
        ("MUSLIM PRO / ATHAN", "Hero + halka", hero_ring, "geri sayım + halka, tek bakışta 'ne kadar kaldı'"),
        ("PILLARS / STREAKS", "Bugün checklist", checklist, "tik satırları + 3/5 sayacı, seri hissi"),
        ("VAKIT", "Kompakt satır + tik", compact, "en sade: saat + yanında tik"),
        ("ATHAN / VAKIT", "Çip şerit + liste", chips, "5 vakit çipi, hızlı işaretleme"),
        ("MUSLIM PRO", "'Bugün' kartı", card_today, "tek satır özet kartı + liste"),
        ("YENİ TREND", "Zaman çizelgesi", timeline, "dikey hat: şimdi nerede, ne geçti"),
    ]
    cols, cw, ch, gap = 3, 380, 300, 14
    for i, (kick, name, fn, note) in enumerate(cards):
        cx = 24 + (i % cols) * (cw + gap)
        cy = 76 + (i // cols) * (ch + gap)
        card(sheet, d, cx, cy, cw, ch, kick, name, note)
        fn(d, cx + 20, cy + 64)

    d.text((24, H - 42), "Kaynaklar: screensdesign (Muslim Pro), dribbble (Prayer Time App, Ummati), "
                         "apps.apple.com (Vakit), Pillars/Streaks desenleri, journalofhadith (Muslim Pro vs Sajda).",
           font=f(F_SANS, 10), fill=DIM)
    sheet.save("vakit-ekran/kalip-tahtasi.png")
    print("ok")
