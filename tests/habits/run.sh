#!/bin/bash
# Sezilen alışkanlıklar (İP5): HabitModel, düzeltmeler, karne sayıları + bağlantı denetimi. Masaüstü (JVM) testidir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$A/HabitModel.java" "$A/GradeFormModel.java" \
  "$DIR/HabitModelTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.HabitModelTest
python3 "$DIR/check_wiring.py"
