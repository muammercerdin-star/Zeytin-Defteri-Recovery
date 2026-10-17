#!/bin/sh
set -e
cd "$(dirname "$0")/../.."
P=app/src/main/java/com/zeytindefteri/app
D=$(mktemp -d); trap 'rm -rf "$D"' EXIT
mkdir -p "$D/src/com/zeytindefteri/app"
cat > "$D/src/com/zeytindefteri/app/ZeytinAssistantData.java" <<'J'
package com.zeytindefteri.app;
public final class ZeytinAssistantData { public static Long date(String v){ try{return java.time.LocalDate.parse(v).toEpochDay();}catch(Exception e){return null;} } }
J
javac -nowarn -encoding UTF-8 -d "$D/out" $P/ZeytinAssistantMarket.java $P/RegionGradePrice.java $P/MarketBackendConfig.java $P/MeydanMarketStrip.java \
  $P/ZeytinAssistantCommand.java $P/ZeytinAssistantPeriod.java "$D/src/com/zeytindefteri/app/ZeytinAssistantData.java" tests/assistant_market/AssistantMarketTest.java
java -cp "$D/out" com.zeytindefteri.app.AssistantMarketTest
python3 tests/assistant_market/check_wiring.py
