#!/bin/bash
# Tahsilat cümlesi (eğitim turu 1): CollectionEntry birim testleri + bağlantı denetimi. Masaüstü (JVM) testidir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$DIR/CollectionEntryTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.CollectionEntryTest
python3 "$DIR/check_wiring.py"
