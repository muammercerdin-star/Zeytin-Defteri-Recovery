#!/bin/bash
# TurkishText (İP2): tek sadeleştirici ve sayı okuyucu. Masaüstü (JVM) testi; Android derlemesi değildir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -d "$T" "$A/TurkishText.java" "$DIR/TurkishTextTest.java" 2>&1 | grep -v "^Note:" || true
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.TurkishTextTest
python3 "$DIR/check_single.py"
