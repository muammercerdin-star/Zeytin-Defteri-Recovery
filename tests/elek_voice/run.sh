#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd)
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -d "$T" "$ROOT/app/src/main/java/com/zeytindefteri/app/ElekVoiceEntry.java" "$DIR/ElekVoiceEntryTest.java"
java -cp "$T" com.zeytindefteri.app.ElekVoiceEntryTest
python3 "$DIR/check_wiring.py"
