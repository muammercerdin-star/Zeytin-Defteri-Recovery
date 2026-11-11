"""Kullanıcıya görünen metinlerde (Java metin sabitleri, res/ XML) gerçek kişi ya da bahçe adı olmamalı.
Yorumlar ve testler serbest; yasal metindeki veri sorumlusu adı (LegalDocuments) hariç."""
import re
from pathlib import Path

FORBIDDEN = ["Girelli", "Muammer Çerdin", "Volkan Afşar", "Yusuf Soyaslan", "Hakan Yıldırım", "Emre Karataş", "Çerdin"]
ALLOW_FILES = {"LegalDocuments.java"}
bad = []
for p in Path("app/src/main/java").rglob("*.java"):
    if p.name in ALLOW_FILES:
        continue
    text = p.read_text(encoding="utf-8")
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    text = re.sub(r"//[^\n]*", "", text)
    for lit in re.findall(r'"(?:\\.|[^"\\])*"', text):
        for name in FORBIDDEN:
            if name in lit:
                bad.append(f"{p}: {lit[:80]}")
for p in Path("app/src/main/res").rglob("*.xml"):
    text = re.sub(r"<!--.*?-->", "", p.read_text(encoding="utf-8"), flags=re.S)
    for name in FORBIDDEN:
        if name in text:
            bad.append(f"{p}: {name}")
for b in bad:
    print("FAIL", b)
print("OK gerçek ad yok" if not bad else f"{len(bad)} sorun")
raise SystemExit(1 if bad else 0)
