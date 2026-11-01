#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -nowarn -d "$T" "$A/ElekVoiceEntry.java" "$A/ElekVoiceDialog.java" "$A/ElekVoiceBuyer.java" "$A/OperationKinds.java" "$A/OperationVoiceEntry.java" "$A/HarvestVoiceEntry.java" "$A/VoiceRecordDialog.java" "$DIR/VoiceRecordDialogTest.java" 2>&1 | grep -v "^Note:" || true
java -Dfile.encoding=UTF-8 -cp "$T" com.zeytindefteri.app.VoiceRecordDialogTest
python3 "$DIR/check_wiring.py"
