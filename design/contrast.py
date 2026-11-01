"""Seher/Odak paletleri için WCAG kontrast oranlarını hesaplar."""
def lum(hexstr):
    h = hexstr.lstrip("#")
    r, g, b = (int(h[i:i + 2], 16) / 255 for i in (0, 2, 4))
    def f(c):
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    return 0.2126 * f(r) + 0.7152 * f(g) + 0.0722 * f(b)


def ratio(a, b):
    la, lb = lum(a), lum(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


pairs = [
    ("Seher", "metin", "#F3EEE3", "zemin", "#0B1322"),
    ("Seher", "metin", "#F3EEE3", "kart", "#121D33"),
    ("Seher", "soluk", "#C9D1DC", "kart", "#121D33"),
    ("Seher", "soluk", "#C9D1DC", "zemin", "#0B1322"),
    ("Seher", "altın", "#E8BE74", "kart", "#121D33"),
    ("Seher", "uyarı", "#F4B08F", "kart", "#121D33"),
    ("Seher", "teal", "#6BC5B0", "kart", "#121D33"),
    ("Seher", "mavi", "#8AA6D8", "kart", "#121D33"),
    ("Seher", "boş kutu", "#39465F", "kart", "#121D33"),
    ("Odak", "metin", "#F5F5F2", "zemin", "#0B0C0E"),
    ("Odak", "soluk", "#A0A4AB", "kart", "#16181C"),
    ("Odak", "lime", "#C6F45E", "kart", "#16181C"),
    ("Odak", "uyarı", "#FF8A4C", "kart", "#16181C"),
]
for theme, n1, c1, n2, c2 in pairs:
    r = ratio(c1, c2)
    flag = "AA" if r >= 4.5 else ("AA-büyük" if r >= 3 else "yetersiz")
    print(f"{theme:6} {n1:9} {c1} / {n2:5} {c2}  = {r:5.2f}  {flag}")
