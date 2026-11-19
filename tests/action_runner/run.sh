#!/bin/bash
# Görünür uygulayıcı ve çok adımlı eylem (İP7): ayrıştırma, onay/geri al kuralı (RunnerFlow), izin, karne + bağlantı
# denetimi. Masaüstü (JVM) testidir; Tanecik'in ekranda uçması telefonda denenir.
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$A/RunnerFlow.java" "$A/HarmlessPermission.java" "$A/AssistantOutcome.java" "$A/ActionGate.java" \
  "$A/Approval.java" "$A/OperationVoiceDialog.java" "$DIR/ActionRunnerTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.ActionRunnerTest
python3 "$DIR/check_wiring.py"
