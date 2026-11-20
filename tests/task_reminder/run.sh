#!/bin/bash
# Gelecek zamanlı iş → hatırlatma (eğitim turu 1): birim testleri ve bağlantı denetimi (JVM).
set -e
DIR=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$DIR/../.." && pwd)
T=$(mktemp -d)
trap 'rm -rf "$T"' EXIT
source "$ROOT/tests/sentence_bank/brain_build.sh"
brain_build "$T" "$DIR/TaskReminderTest.java"
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$BRAIN_CP" com.zeytindefteri.app.TaskReminderTest
python3 "$DIR/check_wiring.py"
