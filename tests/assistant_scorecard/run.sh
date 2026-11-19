#!/bin/bash
# Karne (İP1): masaüstü (JVM) birim testleri + bağlantıların statik denetimi.
# Android derlemesi ya da cihaz testi değildir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -d "$T" \
  "$DIR/stubs/android/content/Context.java" "$DIR/stubs/android/content/SharedPreferences.java" "$DIR/stubs/android/os/SystemClock.java" \
  "$A/ElekVoiceEntry.java" "$A/AssistantMisses.java" "$A/AssistantScorecard.java" "$A/AssistantMeter.java" \
  "$DIR/AssistantScorecardTest.java" "$DIR/AssistantMeterTest.java" 2>&1 | grep -v "^Note:" || true
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.AssistantScorecardTest
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.AssistantMeterTest
python3 "$DIR/check_wiring.py"
