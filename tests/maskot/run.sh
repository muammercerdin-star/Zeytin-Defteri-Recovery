#!/bin/bash
# Tanecik: sözler, günlük hafıza ve önerdiği soruların asistanın gerçek çözümleyicisinde anlaşılması.
set -e
here="$(cd "$(dirname "$0")" && pwd)"; root="$(cd "$here/../.." && pwd)"
A="$root/app/src/main/java/com/zeytindefteri/app"
out="$(mktemp -d)"; trap 'rm -rf "$out"' EXIT
javac -encoding UTF-8 -nowarn -d "$out" "$A/TanecikLines.java" "$A/ZeytinAssistantCommand.java" "$A/ZeytinAssistantPeriod.java" \
  "$here/stubs/ZeytinAssistantData.java" "$A/ElekVoiceEntry.java" "$A/ElekVoiceDialog.java" "$A/ElekVoiceBuyer.java" \
  "$A/HarvestVoiceEntry.java" "$A/AssistantReceivables.java" "$here/TanecikLinesTest.java" 2>&1 | grep -v "^Note:" || true
java -Dstdout.encoding=UTF-8 -cp "$out" com.zeytindefteri.app.TanecikLinesTest
