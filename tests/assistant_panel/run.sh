#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
P=app/src/main/java/com/zeytindefteri/app
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
mkdir -p "$D/src/com/zeytindefteri/app"
# ZeytinAssistantPeriod yalnızca ZeytinAssistantData.date'i kullanır; Android'e bağlı veri sınıfı yerine küçük bir taklit.
cat > "$D/src/com/zeytindefteri/app/ZeytinAssistantData.java" <<'J'
package com.zeytindefteri.app;
public final class ZeytinAssistantData { public static Long date(String v){ try{return java.time.LocalDate.parse(v).toEpochDay();}catch(Exception e){return null;} } }
J
javac -nowarn -encoding UTF-8 -d "$D/out" $P/ZeytinAssistantText.java $P/ZeytinAssistantCommand.java $P/ZeytinAssistantPeriod.java \
  $P/ElekVoiceEntry.java $P/ElekVoiceDialog.java $P/ElekVoiceBuyer.java $P/OperationKinds.java $P/OperationVoiceEntry.java $P/HarvestVoiceEntry.java $P/AssistantReceivables.java $P/VoiceRecordDialog.java \
  "$D/src/com/zeytindefteri/app/ZeytinAssistantData.java" tests/assistant_panel/AssistantTextTest.java
java -cp "$D/out" com.zeytindefteri.app.AssistantTextTest
python3 tests/assistant_panel/check_wiring.py
