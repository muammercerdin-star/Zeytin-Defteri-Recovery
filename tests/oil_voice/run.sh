#!/bin/bash
# Yağhane cümlesi (eğitim turu 1): OilVoiceEntry + hatırlatma zamanı birim testleri ve bağlantı denetimi (JVM).
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$DIR/OilVoiceEntryTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.OilVoiceEntryTest
python3 "$DIR/check_wiring.py"
