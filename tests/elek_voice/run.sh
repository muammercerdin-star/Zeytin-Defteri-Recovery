#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -d "$T" "$ROOT/app/src/main/java/com/zeytindefteri/app/ElekVoiceEntry.java" "$ROOT/app/src/main/java/com/zeytindefteri/app/ElekVoiceDialog.java" "$ROOT/app/src/main/java/com/zeytindefteri/app/ElekVoiceBuyer.java" "$DIR/ElekVoiceEntryTest.java" "$DIR/ElekVoiceDialogTest.java" "$DIR/ElekVoiceBuyerTest.java"
java -cp "$T" com.zeytindefteri.app.ElekVoiceEntryTest
java -cp "$T" com.zeytindefteri.app.ElekVoiceDialogTest
java -cp "$T" com.zeytindefteri.app.ElekVoiceBuyerTest
python3 "$DIR/check_wiring.py"
