"""Kompakt ana sayfa önerileri: üç varyantı 390x844 olarak çizer (Pillow + uygulamanın fontları)."""
from PIL import Image, ImageDraw, ImageFont

W, H = 390, 844
BG = (11, 19, 34, 255)
TEXT = (243, 238, 227, 255)
MUTED = (201, 209, 220, 255)
GOLD = (232, 190, 116, 255)
LINE = (36, 50, 72, 255)
CARD = (18, 29, 51, 255)
OFF = (66, 82, 106, 255)

F_DISPLAY = r"app\src\main\res\font\cormorant.ttf"
F_SANS = r"C:\Windows\Fonts\segoeui.ttf"
F_SANSB = r"C:\Windows\Fonts\segoeuib.ttf"


def f(path, size):
    return ImageFont.truetype(path, size)


def rrect(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def circle(d, cx, cy, r, fill=None, outline=None, width=1):
    d.ellipse((cx - r, cy - r, cx + r, cy + r), fill=fill, outline=outline, width=width)


def check(d, cx, cy, s, color):
    d.line((cx - s * 0.55, cy + s * 0.05, cx - s * 0.10, cy + s * 0.48, cx + s * 0.58, cy - s * 0.45),
           fill=color, width=max(2, int(s * 0.36)), joint="curve")


def text(d, xy, s, font, fill, anchor="la"):
    d.text(xy, s, font=font, fill=fill, anchor=anchor)


def sky_strip(d, h=92):
    for y in range(h):
        t = y / h
        d.line((0, y, W, y), fill=(int(40 - 28 * t), int(48 - 34 * t), int(70 - 48 * t), 255))
    text(d, (22, h - 26), "✦ Üç Ayların Başlangıcı · 5 hafta kaldı", f(F_SANS, 11), GOLD)


PRAYERS = [("İmsak", "05:46", True), ("Güneş", "07:10", None), ("Öğle", "12:37", True),
           ("İkindi", "15:27", True), ("Akşam", "17:54", True), ("Yatsı", "18:59", False)]


def tick(d, cx, cy, r, state, active=False):
    if state is True:
        circle(d, cx, cy, r, fill=GOLD)
        check(d, cx, cy, r * 0.62, BG)
    elif state is None:      # namaz değil (Güneş): boş yer
        return
    else:
        circle(d, cx, cy, r, fill=None, outline=GOLD if active else OFF, width=2)


def variant_1(d):
    """A · Vaktin yanında tik, üstte ince geri sayım."""
    sky_strip(d)
    text(d, (22, 110), "YATSI VAKTİ", f(F_SANSB, 11), GOLD)
    text(d, (22, 124), "9:02", f(F_DISPLAY, 58), TEXT)
    text(d, (132, 170), "kaldı · 18:59", f(F_SANS, 13), MUTED)

    text(d, (22, 214), "Bugünün vakitleri", f(F_SANS, 12), MUTED)
    text(d, (250, 214), "Ek vakitler", f(F_SANS, 12), MUTED)
    rrect(d, (330, 209, 368, 229), 10, fill=None, outline=LINE, width=2)
    circle(d, 339, 219, 7, fill=MUTED)

    ry = 246
    for name, hm, done in PRAYERS:
        active = name == "Yatsı"
        text(d, (22, ry), name, f(F_SANSB if active else F_SANS, 15), GOLD if active else TEXT)
        text(d, (280, ry), hm, f(F_SANSB if active else F_SANS, 15), GOLD if active else TEXT, anchor="ra")
        tick(d, 344, ry + 9, 11, done, active)
        d.line((22, ry + 36, 368, ry + 36), fill=LINE)
        ry += 50


def variant_2(d):
    """B · Üstte tıklanabilir çip şeridi, altta sade saat listesi."""
    sky_strip(d)
    text(d, (22, 110), "YATSI", f(F_SANSB, 11), GOLD)
    text(d, (22, 122), "9:02", f(F_DISPLAY, 52), TEXT)
    text(d, (118, 158), "kaldı", f(F_SANS, 13), MUTED)
    text(d, (368, 128), "18:59", f(F_SANSB, 13), MUTED, anchor="ra")

    names = [p for p in PRAYERS if p[2] is not None]
    cx = 22
    for name, hm, done in names:
        active = name == "Yatsı"
        w = 62
        rrect(d, (cx, 186, cx + w, 226), 20, fill=GOLD if done else (None if active else CARD),
              outline=GOLD if active else None, width=2)
        text(d, (cx + w / 2, 196), name, f(F_SANSB if (done or active) else F_SANS, 11),
             BG if done else (GOLD if active else TEXT), anchor="ma")
        if done:
            check(d, cx + w / 2, 211, 6, BG)
        else:
            text(d, (cx + w / 2, 209), hm, f(F_SANS, 9), GOLD if active else MUTED, anchor="ma")
        cx += w + 6

    ry = 248
    for name, hm, done in PRAYERS:
        active = name == "Yatsı"
        col = GOLD if active else (MUTED if done else TEXT)
        text(d, (22, ry), name, f(F_SANSB if active else F_SANS, 15), col)
        text(d, (300, ry), hm, f(F_SANSB if active else F_SANS, 15), col, anchor="ra")
        if done:
            text(d, (356, ry), "✓", f(F_SANS, 14), GOLD, anchor="ra")
        d.line((22, ry + 30, 368, ry + 30), fill=LINE)
        ry += 44


def variant_3(d):
    """C · Ultra minimal: kalan süre kartı + sadece tikli liste."""
    sky_strip(d)
    rrect(d, (22, 108, 368, 156), 24, fill=CARD)
    text(d, (38, 116), "Yatsı", f(F_SANSB, 14), GOLD)
    text(d, (38, 134), "18:59", f(F_SANS, 11), MUTED)
    text(d, (352, 124), "9:02 kaldı", f(F_SANSB, 13), TEXT, anchor="ra")

    ry = 178
    for name, hm, done in PRAYERS:
        active = name == "Yatsı"
        text(d, (22, ry), name, f(F_SANSB if active else F_SANS, 15), GOLD if active else TEXT)
        text(d, (280, ry), hm, f(F_SANSB if active else F_SANS, 15), GOLD if active else TEXT, anchor="ra")
        tick(d, 344, ry + 9, 10, done, active)
        d.line((22, ry + 32, 368, ry + 32), fill=LINE)
        ry += 46


if __name__ == "__main__":
    labels = ["A · vaktin yanında tik", "B · çip şerit + liste", "C · ultra minimal"]
    sheet = Image.new("RGB", (W * 3 + 40, H + 44), (24, 30, 42))
    sd = ImageDraw.Draw(sheet)
    for i, (fn, lab) in enumerate(zip((variant_1, variant_2, variant_3), labels)):
        img = Image.new("RGBA", (W, H), BG)
        d = ImageDraw.Draw(img)
        fn(d)
        x = 10 + i * (W + 10)
        sheet.paste(img.convert("RGB"), (x, 34))
        sd.text((x + 12, 10), lab, font=f(F_SANSB, 13), fill=(160, 175, 200))
    sheet.save("vakit-ekran/ana-kompakt-oneriler.png")
    print("ok")
