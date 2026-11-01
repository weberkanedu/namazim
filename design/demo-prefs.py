"""Demo verisi: son 60 gün için gerçekçi işaretler + kılınma saatleri üretir.

Kullanım: python design/demo-prefs.py   (design/prefs-real.xml -> design/prefs-demo.xml)
"""
import datetime
import random
import re

SRC = r"design\prefs-real.xml"
OUT = r"design\prefs-demo.xml"

random.seed(11)
TODAY = datetime.date(2026, 11, 1)
DAYS = 60

# vakit pencereleri (başlangıç dakikası, uzunluk dakika) — Telefonun gerçek vakitlerine yakın
WINDOWS = {
    "SABAH": (5 * 60 + 46, 84),
    "OGLE": (12 * 60 + 37, 170),
    "IKINDI": (15 * 60 + 27, 147),
    "AKSAM": (17 * 60 + 54, 79),
    "YATSI": (19 * 60 + 13, 300),
}
# vaktin ne kadarında kılındığı (ortalama oran, sapma)
CENTER = {"SABAH": 0.35, "OGLE": 0.45, "IKINDI": 0.55, "AKSAM": 0.30, "YATSI": 0.40}
SPREAD = {"SABAH": 0.18, "OGLE": 0.22, "IKINDI": 0.25, "AKSAM": 0.15, "YATSI": 0.30}

keys, ats, log = [], [], {}
for i in range(DAYS):
    d = TODAY - datetime.timedelta(days=i)
    ds = d.isoformat()
    p_keep = 0.40 + 0.5 * (i / DAYS)  # gün geçtikçe istikrar artıyor
    for v, (start, length) in WINDOWS.items():
        if random.random() > p_keep:
            continue
        frac = min(0.95, max(0.02, random.gauss(CENTER[v], SPREAD[v])))
        minute = start + int(frac * length)
        base = datetime.datetime(d.year, d.month, d.day, (minute // 60) % 24, minute % 60)
        keys.append(f"{ds}|{v}")
        ats.append((f"prayedAt_{ds}|{v}", int(base.timestamp() * 1000)))
    log[ds] = random.randint(2, 14)

src = open(SRC, encoding="utf-8").read()

prayed_set = "\n".join(f"        <string>{k}</string>" for k in keys)
src = re.sub(r'<set name="prayed">.*?</set>', '<set name="prayed">\n' + prayed_set + "\n    </set>", src, flags=re.S)

since = int(datetime.datetime(2026, 9, 2, 21, 0).timestamp() * 1000)
src = re.sub(r'<long name="since" value="\d+" />', f'<long name="since" value="{since}" />', src)
src = re.sub(r'<string name="firstDay">[^<]*</string>', '<string name="firstDay">2026-09-02</string>', src)

extra = "\n".join(f'    <long name="{n}" value="{v}" />' for n, v in ats)
src = src.replace("</map>", extra + "\n</map>")

log_json = "{" + ", ".join(f'"{k}":{v}' for k, v in log.items()) + "}"
src = re.sub(r'<string name="readLog">.*?</string>',
             '<string name="readLog">' + log_json.replace("<", "&lt;") + "</string>", src, flags=re.S)

open(OUT, "w", encoding="utf-8", newline="\n").write(src)
print("isaret:", len(keys), "gun:", DAYS, "log:", len(log))

