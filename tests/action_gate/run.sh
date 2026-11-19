#!/bin/bash
# Onay kapısı (İP3): onay kuralının delinemezliği + bağlantıların statik denetimi. Masaüstü (JVM) testidir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$A/OperationVoiceDialog.java" "$A/Approval.java" "$A/ActionGate.java" \
  "$ROOT/tests/brain_equivalence/LegacyRouter.java" "$ROOT/tests/brain_equivalence/BrainEquivalenceTest.java" "$DIR/ActionGateTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.ActionGateTest "$ROOT"
python3 "$DIR/check_wiring.py"
