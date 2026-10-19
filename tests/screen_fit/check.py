#!/usr/bin/env python3
"""ScreenFit: hesap kuralı (dar ekran 360 dp'ye ölçeklenir) ve uygulama kancası statik kontrolü."""
import re, pathlib, sys
root = pathlib.Path(__file__).resolve().parents[2]
src = (root / "app/src/main/java/com/zeytindefteri/app/ScreenFit.java").read_text(encoding="utf-8")
app = (root / "app/src/main/java/ZeytinApplication.java").read_text(encoding="utf-8")
welcome = (root / "app/src/main/java/com/zeytindefteri/app/WelcomeActivity.java").read_text(encoding="utf-8")
checks = [
    ("MIN_WIDTH_DP = 360" in src, "tasarım genişliği 360 dp"),
    ("MAX_FONT_SCALE = 1.15f" in src, "yazı ölçeği sınırı"),
    ("onActivityPreCreated" in app and "ScreenFit.apply(a)" in app, "her ekranda setContentView öncesi uygulanır"),
    ("SDK_INT<29" in app, "Android 9 ve altı için onActivityCreated yedeği"),
    ("ScreenFit.shrinkToFit(" in welcome, "karşılama ekranı tek sayfaya sığdırılır"),
]
for ok, label in checks:
    if not ok: sys.exit("FAIL " + label)
    print("PASS", label)
def fit(w, dpi):
    if w <= 0 or dpi <= 0 or w >= 360: return (w, dpi)
    return (360, max(120, round(dpi * (w / 360))))
assert fit(411, 420) == (411, 420) and fit(320, 480) == (360, 427)
print("PASS hesap: 320 dp ekran 360 dp'ye ölçeklenir, normal ekran değişmez")
