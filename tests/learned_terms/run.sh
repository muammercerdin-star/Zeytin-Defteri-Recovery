#!/bin/bash
# Öğrenilen sözler (İP4): birim testleri + bağlantıların statik denetimi. Masaüstü (JVM) testidir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$A/ActionGate.java" "$A/Approval.java" "$A/OperationVoiceDialog.java" "$DIR/LearnedTermsTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.LearnedTermsTest
python3 "$DIR/check_wiring.py"
