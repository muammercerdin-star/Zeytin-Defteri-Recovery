#!/bin/bash
# Ekrandaki sonuç aynı mı (İP5): sözlük ve alışkanlık boşken asistanın her cümlede yaptığı iş İP4 sonundakiyle
# (baseline.tsv) aynı olmalı; bilinçli değişiklikler intended.tsv'de. Masaüstü (JVM) testidir.
# Taban üretmek: run.sh --write (yalnız taban commit'inde).
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$A/AssistantOutcome.java" \
  "$ROOT/tests/brain_equivalence/LegacyRouter.java" "$ROOT/tests/brain_equivalence/BrainEquivalenceTest.java" "$DIR/OutcomeTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.OutcomeTest "$ROOT" "$@"
python3 "$DIR/check_wiring.py"
