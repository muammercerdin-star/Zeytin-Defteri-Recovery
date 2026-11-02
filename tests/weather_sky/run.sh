#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -d "$T" "$A/WeatherSky.java" "$DIR/WeatherSkyTest.java"
java -Dfile.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.WeatherSkyTest
python3 - "$A" <<'P'
import sys, os
A = sys.argv[1]
f = open(os.path.join(A, "ProducerFeedActivity.java"), encoding="utf-8").read()
v = open(os.path.join(A, "WeatherSkyView.java"), encoding="utf-8").read()
checks = [
    ("güncel hava gelince animasyon güncellenir", "sky.setWeather(w.weatherCode,w.isDay,w.windKmh);" in f),
    ("animasyon kartın arkasında, köşeler kırpılır", "weatherHolder.addView(sky" in f and "setClipToOutline(true)" in f),
    ("kaydırma konumu kartın kendisine göre", "state.weatherCard=weatherHolder;" in f),
    ("ekranda değilken durur", "getGlobalVisibleRect(visible)" in v),
    ("animasyonlar kapalıysa durgun", "ANIMATOR_DURATION_SCALE" in v and "if (!animate) return;" in v),
    ("dokunuşu karta bırakır", "setClickable(false)" in v),
]
bad = 0
for n, ok in checks:
    print(("PASS " if ok else "FAIL ") + n); bad += 0 if ok else 1
sys.exit(1 if bad else 0)
P
