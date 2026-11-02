#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -d "$T" "$A/PrayerTimes.java" "$DIR/PrayerTimesTest.java"
java -Dfile.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.PrayerTimesTest
python3 - "$A" <<'P'
import sys, os
r = lambda f: open(os.path.join(sys.argv[1], f), encoding="utf-8").read()
f, v, st = r("ProducerFeedActivity.java"), r("PrayerStripView.java"), r("SettingsView.java")
checks = [
    ("şerit bahçe seçicinin altında, bahçe konumundan", "hero.addView(prayer,homeCardParams(12));" in f and "weatherCoordinates(garden.id)" in f),
    ("konum yoksa hava gelince doldurulur", "prayer.setLocation(w.latitude,w.longitude,prayerPlace);" in f),
    ("Ayarlar'dan kapatılabilir", "prayerRow(activity)" in st and "PrayerStripView.enabled(this)" in f),
    ("yatsıdan sonra yarının imsakı gösterilir", "tomorrowImsak : today[i]" in v),
    ("ekranda değilken çizim yavaşlar", "getGlobalVisibleRect(visible)" in v),
]
bad = 0
for n, ok in checks:
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
sys.exit(1 if bad else 0)
P
