#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -d "$T" "$A/PrayerTimes.java" "$DIR/PrayerTimesTest.java"
java -Dfile.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.PrayerTimesTest
python3 - "$A" <<'P'
import sys, os
r = lambda f: open(os.path.join(sys.argv[1], f), encoding="utf-8").read()
f, v, st = r("ProducerFeedActivity.java"), r("PrayerLineView.java"), r("SettingsView.java")
checks = [
    ("selamlamanın altında tek satır, bahçe konumundan", f.index("hero.addView(top);") < f.index("hero.addView(prayer,") and "weatherCoordinates(garden.id)" in f),
    ("konum yoksa hava gelince doldurulur", "prayer.setLocation(w.latitude,w.longitude,prayerPlace);" in f),
    ("Ayarlar'dan kapatılabilir", "prayerRow(activity)" in st and "PrayerLineView.enabled(this)" in f),
    ("yatsıdan sonra sıradaki yarının imsakı", "? tomorrowImsak : today[n.next]" in v),
    ("ekrandan çıkınca güncelleme durur", "removeCallbacks(tick); super.onDetachedFromWindow();" in v),
]
bad = 0
for n, ok in checks:
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
sys.exit(1 if bad else 0)
P
