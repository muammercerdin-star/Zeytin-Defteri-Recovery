#!/bin/sh
# Asistanın anlama katmanı: masaüstü (JVM) testi + panel bağlantılarının statik denetimi.
# Android derlemesi ya da cihaz/emülatör testi değildir.
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
javac -nowarn -encoding UTF-8 -d "$D/out" $P/ZeytinAssistantHints.java $P/ZeytinAssistantText.java $P/ZeytinAssistantCommand.java \
  $P/ZeytinAssistantPeriod.java "$D/src/com/zeytindefteri/app/ZeytinAssistantData.java" tests/assistant_hints/HintsTest.java
java -Dstdout.encoding=UTF-8 -cp "$D/out" com.zeytindefteri.app.HintsTest
if [ -f tests/assistant_hints/check_wiring.py ]; then python3 tests/assistant_hints/check_wiring.py; fi
