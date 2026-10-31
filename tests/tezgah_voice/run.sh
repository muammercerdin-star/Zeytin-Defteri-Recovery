#!/bin/bash
set -e
DIR=$(cd "$(dirname "$0")" && pwd); ROOT=$(cd "$DIR/../.." && pwd); A="$ROOT/app/src/main/java/com/zeytindefteri/app"
T=$(mktemp -d); trap 'rm -rf "$T"' EXIT
javac -encoding UTF-8 -d "$T" "$A/ElekVoiceEntry.java" "$A/ElekVoiceBuyer.java" "$A/TezgahModel.java" "$A/TezgahWizardFlow.java" "$A/ElekSaleModel.java" "$A/TezgahVoiceDialog.java" "$DIR/TezgahVoiceDialogTest.java"
java -cp "$T" com.zeytindefteri.app.TezgahVoiceDialogTest
python3 "$DIR/check_wiring.py"
