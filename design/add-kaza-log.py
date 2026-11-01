"""Demo dosyasına günlük kaza grafiği için kazaLog ekler."""
import datetime
import json
import random
import re

random.seed(23)
path = r"design\prefs-demo.xml"
src = open(path, encoding="utf-8").read()
today = datetime.date(2026, 11, 1)
klog = {}
for i in range(60):
    if random.random() < 0.45:
        klog[(today - datetime.timedelta(days=i)).isoformat()] = random.randint(1, 6)
src = re.sub(r'<string name="kazaLog">.*?</string>', "", src, flags=re.S)
src = src.replace("</map>", '    <string name="kazaLog">' + json.dumps(klog, ensure_ascii=False) + "</string>\n</map>")
open(path, "w", encoding="utf-8", newline="\n").write(src)
print("kazaLog gün:", len(klog))
