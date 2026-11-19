#!/bin/bash
# Öğrendiklerim ekranı (İP6): ekran içeriği (LearnedScreen) + öğret → gör → sil → anlaşılmıyor + bağlantı denetimi.
# Masaüstü (JVM) testidir; Android ekranı burada çizilmez.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$A/LearnedScreen.java" "$DIR/LearnedScreenTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.LearnedScreenTest
python3 "$DIR/check_wiring.py"
